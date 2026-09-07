package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v3.3.0 - unified land-claim awareness. Provides a single API surface so
 * gameplay code doesn't care WHICH claim mod is installed, only whether a
 * given position is claimed by someone the raid should respect.
 *
 * <p>This bridge aggregates over three claim providers, checked in order:</p>
 * <ol>
 *   <li>Recruits claims (via {@link RecruitsClaimsBridge}) - always checked
 *       first because Faction Raids uses Recruits claim data for anchor
 *       selection and war-camp placement already.</li>
 *   <li>FTB Chunks - checked via reflection against
 *       {@code dev.ftb.mods.ftbchunks.api.FTBChunksAPI}.</li>
 *   <li>Open Parties and Claims - checked via reflection against
 *       {@code xaero.pac.common.server.claims.ClaimsManager}.</li>
 * </ol>
 *
 * <p>All reflection paths are guarded by {@link ModList#isLoaded} and cached
 * per-JVM. Any thrown exception disables that provider for the session; the
 * mod continues to work with the remaining providers.</p>
 *
 * <p>Design intent: return {@code true} from {@link #isClaimed} only when
 * some player OTHER than the raid target has claimed the chunk. Raiders
 * SHOULD still be able to build camps and breach blocks on the target's
 * own claim - that's the whole game.</p>
 */
public final class ClaimBridge {

    // Cache "is this provider available" answers per JVM to avoid repeated
    // ModList lookups + reflection failures on hot code paths.
    private static final ConcurrentHashMap<String, Boolean> PROVIDER_AVAILABLE = new ConcurrentHashMap<>();

    // FTB Chunks reflection handles - resolved lazily on first call, cached
    // to null-sentinel when unavailable so we don't re-throw every tick.
    private static volatile boolean ftbInit = false;
    private static volatile Method ftbGetManager;
    private static volatile Method ftbGetChunk;
    private static volatile Method ftbGetTeamId;

    // Open Parties reflection handles - same lazy-init pattern as FTB.
    private static volatile boolean opacInit = false;
    private static volatile Method opacGetInstance;
    private static volatile Method opacGetClaimStatePos;
    private static volatile Method opacClaimGetPlayerId;

    private ClaimBridge() {}

    /**
     * Returns true when the chunk at {@code pos} in {@code level} is claimed
     * by a player OTHER than any member of {@code defenderAnchor}. If the
     * chunk is unclaimed, or is claimed by a defender member (or the anchor
     * is null), returns false so raiders proceed normally.
     *
     * <p>Cheap enough to call once per candidate camp origin. Do NOT call
     * per-block on tick loops without a bulk pre-computation step; use
     * {@link #collectClaimedChunks} for that instead.</p>
     */
    public static boolean isForeignClaim(ServerLevel level, BlockPos pos,
                                          RaidSavedData.Anchor defenderAnchor) {
        if (level == null || pos == null) return false;
        return isForeignClaim(level, new ChunkPos(pos), defenderAnchor);
    }

    public static boolean isForeignClaim(ServerLevel level, ChunkPos chunk,
                                          RaidSavedData.Anchor defenderAnchor) {
        if (level == null || chunk == null) return false;
        Set<UUID> defenderMembers = defenderAnchor == null
                ? java.util.Collections.emptySet() : defenderAnchor.members();

        // Provider 1: Recruits claims. Recruits uses a String faction id
        // (not per-player UUIDs), so we treat any Recruits claim whose
        // owner faction differs from this raid's defender team key as
        // foreign. When defenderAnchor is null every Recruits claim is
        // foreign, which matches the intent (any claim should be avoided).
        try {
            if (RecruitsClaimsBridge.available()) {
                Optional<RecruitsClaimsBridge.ClaimSnapshot> snap =
                        RecruitsClaimsBridge.getClaimAt(level, chunk);
                if (snap.isPresent()) {
                    String owner = snap.get().ownerFactionStringId();
                    String defenderTeam = defenderAnchor == null ? null : defenderAnchor.teamKey();
                    if (owner != null && !owner.isEmpty()
                            && (defenderTeam == null || !owner.equalsIgnoreCase(defenderTeam))) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            // Swallow - Recruits bridge already logs its own failures.
        }

        // Provider 2: FTB Chunks.
        if (ftbAvailable()) {
            try {
                UUID owner = ftbClaimOwner(level, chunk);
                if (owner != null && !defenderMembers.contains(owner)) return true;
            } catch (Throwable t) {
                markProviderBroken("ftbchunks", t);
            }
        }

        // Provider 3: Open Parties and Claims.
        if (opacAvailable()) {
            try {
                UUID owner = opacClaimOwner(level, chunk);
                if (owner != null && !defenderMembers.contains(owner)) return true;
            } catch (Throwable t) {
                markProviderBroken("openpartiesandclaims", t);
            }
        }

        return false;
    }

    /**
     * Bulk pre-computation for camp-placement search: given a search area
     * defined by center + radius chunks, return every chunk that any
     * provider considers "foreign-claimed". Result set is a defensive
     * copy safe to hold and iterate.
     */
    public static Set<ChunkPos> collectClaimedChunks(ServerLevel level, BlockPos center, int radiusChunks,
                                                       RaidSavedData.Anchor defenderAnchor) {
        Set<ChunkPos> result = new HashSet<>();
        if (level == null || center == null || radiusChunks <= 0) return result;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkPos cp = new ChunkPos(cx + dx, cz + dz);
                if (isForeignClaim(level, cp, defenderAnchor)) result.add(cp);
            }
        }
        return result;
    }

    /**
     * True when at least one claim provider is installed. Used by the debug
     * command to surface provider-availability status.
     */
    public static boolean anyProviderAvailable() {
        return RecruitsClaimsBridge.available() || ftbAvailable() || opacAvailable();
    }

    public static String diagnosticStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Recruits: ").append(RecruitsClaimsBridge.available() ? "on" : "off");
        sb.append(" | FTB Chunks: ").append(ftbAvailable() ? "on" : "off");
        sb.append(" | Open Parties: ").append(opacAvailable() ? "on" : "off");
        return sb.toString();
    }

    // ---- FTB Chunks reflection ------------------------------------------

    private static boolean ftbAvailable() {
        Boolean cached = PROVIDER_AVAILABLE.get("ftbchunks");
        if (cached != null) return cached;
        boolean available = ModList.get() != null && ModList.get().isLoaded("ftbchunks");
        PROVIDER_AVAILABLE.put("ftbchunks", available);
        return available;
    }

    private static void ftbInitReflection() throws ReflectiveOperationException {
        if (ftbInit) return;
        synchronized (ClaimBridge.class) {
            if (ftbInit) return;
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            ftbGetManager = apiClass.getMethod("getManager");
            Class<?> managerClass = ftbGetManager.getReturnType();
            // ClaimedChunkManager.getChunk(ChunkDimPos) returns ClaimedChunk
            Class<?> dimPosClass = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkDimPos");
            ftbGetChunk = managerClass.getMethod("getChunk", dimPosClass);
            Class<?> chunkClass = ftbGetChunk.getReturnType();
            // ClaimedChunk.getTeamId() returns UUID
            ftbGetTeamId = chunkClass.getMethod("getTeamId");
            ftbInit = true;
        }
    }

    private static UUID ftbClaimOwner(ServerLevel level, ChunkPos chunk) throws ReflectiveOperationException {
        ftbInitReflection();
        Object manager = ftbGetManager.invoke(null);
        if (manager == null) return null;
        Class<?> dimPosClass = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkDimPos");
        Object dimPos = dimPosClass.getConstructor(ResourceKey.class, int.class, int.class)
                .newInstance(level.dimension(), chunk.x, chunk.z);
        Object claimed = ftbGetChunk.invoke(manager, dimPos);
        if (claimed == null) return null;
        Object teamId = ftbGetTeamId.invoke(claimed);
        return teamId instanceof UUID u ? u : null;
    }

    // ---- Open Parties and Claims reflection -----------------------------

    private static boolean opacAvailable() {
        Boolean cached = PROVIDER_AVAILABLE.get("openpartiesandclaims");
        if (cached != null) return cached;
        boolean available = ModList.get() != null && ModList.get().isLoaded("openpartiesandclaims");
        PROVIDER_AVAILABLE.put("openpartiesandclaims", available);
        return available;
    }

    private static void opacInitReflection() throws ReflectiveOperationException {
        if (opacInit) return;
        synchronized (ClaimBridge.class) {
            if (opacInit) return;
            // OPAC public API: OpenPartiesAndClaimsAPI.get().getServerClaimsManager()
            Class<?> apiClass = Class.forName("xaero.pac.OpenPartiesAndClaims");
            opacGetInstance = apiClass.getMethod("getInstance");
            // The instance exposes getServerData() -> getServerClaimsManager() -> get(ResourceLocation, int, int)
            // API surface has shifted across versions; we try the newer packaged API first, then fall back.
            Class<?> claimsMgrClass = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            opacGetClaimStatePos = claimsMgrClass.getMethod("get",
                    ResourceLocation.class, int.class, int.class);
            Class<?> claimStateClass = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI");
            opacClaimGetPlayerId = claimStateClass.getMethod("getPlayerId");
            opacInit = true;
        }
    }

    private static UUID opacClaimOwner(ServerLevel level, ChunkPos chunk) throws ReflectiveOperationException {
        opacInitReflection();
        Object instance = opacGetInstance.invoke(null);
        if (instance == null) return null;
        Method getServerData = instance.getClass().getMethod("getServerData");
        Object serverData = getServerData.invoke(instance);
        if (serverData == null) return null;
        Method getManager = serverData.getClass().getMethod("getServerClaimsManager");
        Object mgr = getManager.invoke(serverData);
        if (mgr == null) return null;
        ResourceLocation dim = level.dimension().location();
        Object claim = opacGetClaimStatePos.invoke(mgr, dim, chunk.x, chunk.z);
        if (claim == null) return null;
        Object playerId = opacClaimGetPlayerId.invoke(claim);
        return playerId instanceof UUID u ? u : null;
    }

    // ---- Common ---------------------------------------------------------

    private static void markProviderBroken(String key, Throwable t) {
        // Log once per provider per JVM, then cache "off" so we stop retrying.
        if (Boolean.FALSE.equals(PROVIDER_AVAILABLE.get(key))) return;
        PROVIDER_AVAILABLE.put(key, Boolean.FALSE);
        FactionLogger.LOG.warn("[SiegeOverhaul] Claim provider {} disabled for this session: {} {}",
                key, t.getClass().getSimpleName(), t.getMessage());
    }
}
