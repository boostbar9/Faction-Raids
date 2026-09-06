package com.devfarinsky.factionraids.compat;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.OptionalCompatBridge;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v2.27.0 compile-time-free bridge to Recruits' claim system. Reads Recruits
 * data structures via reflection so Faction Raids still loads (and this
 * bridge silently no-ops) when Recruits is absent or its internal shape
 * changes between versions.
 *
 * <p>What we read:
 * <ul>
 *     <li>The static {@code com.talhanation.recruits.ClaimEvents.recruitsClaimManager}
 *         field \u2014 the world-scoped claim registry.</li>
 *     <li>{@code RecruitsClaimManager#getClaim(ChunkPos)} \u2014 O(1) lookup of
 *         the claim covering a chunk.</li>
 *     <li>{@code RecruitsClaim} getters ({@code getUUID}, {@code getName},
 *         {@code getOwnerFactionStringID}, {@code getCenter},
 *         {@code getClaimedChunks}, {@code getHealth}, {@code getMaxHealth},
 *         and the {@code isUnderSiege} field).</li>
 * </ul>
 *
 * <p>What we do NOT touch:
 * <ul>
 *     <li>Anything mutating (setters, siege lifecycle, event posting).
 *         v2.27 is read-only. Mutation lands in v2.28 via public events.</li>
 *     <li>Recruits' internal factions or diplomacy. We only look up claims.</li>
 * </ul>
 *
 * <p>Threading: called from the server tick and command paths on the server
 * thread. Reflection resolution is cached in an {@link AtomicBoolean}-gated
 * field so repeated calls are cheap after the first successful lookup.
 */
public final class RecruitsClaimsBridge {

    private static final String CLAIM_EVENTS_CLASS = "com.talhanation.recruits.ClaimEvents";
    private static final String CLAIM_MANAGER_FIELD = "recruitsClaimManager";
    private static final String CLAIM_MANAGER_CLASS = "com.talhanation.recruits.world.RecruitsClaimManager";
    private static final String CLAIM_CLASS = "com.talhanation.recruits.world.RecruitsClaim";

    private static final AtomicBoolean RESOLVED = new AtomicBoolean(false);
    private static final AtomicBoolean RESOLUTION_FAILED = new AtomicBoolean(false);
    private static volatile Field claimManagerField;
    private static volatile Method getClaimByChunkPos;
    private static volatile Method getAllClaims;
    private static volatile Method claimGetUUID;
    private static volatile Method claimGetName;
    private static volatile Method claimGetOwnerFactionStringID;
    private static volatile Method claimGetCenter;
    private static volatile Method claimGetClaimedChunks;
    private static volatile Method claimGetHealth;
    private static volatile Method claimGetMaxHealth;
    private static volatile Field claimIsUnderSiegeField;

    private RecruitsClaimsBridge() {}

    // ------------------------------------------------------------------ availability

    /**
     * Master toggle: is Recruits loaded AND has reflection resolution
     * succeeded at least once? Cheap to call every raid tick.
     */
    public static boolean available() {
        if (!OptionalCompatBridge.isLoaded(OptionalCompatBridge.RECRUITS)) return false;
        if (RESOLUTION_FAILED.get()) return false;
        if (!RESOLVED.get()) resolveReflection();
        return RESOLVED.get() && !RESOLUTION_FAILED.get();
    }

    private static synchronized void resolveReflection() {
        if (RESOLVED.get() || RESOLUTION_FAILED.get()) return;
        try {
            Class<?> claimEvents = Class.forName(CLAIM_EVENTS_CLASS);
            claimManagerField = claimEvents.getField(CLAIM_MANAGER_FIELD);

            Class<?> manager = Class.forName(CLAIM_MANAGER_CLASS);
            getClaimByChunkPos = manager.getMethod("getClaim", ChunkPos.class);
            getAllClaims = manager.getMethod("getAllClaims");

            Class<?> claim = Class.forName(CLAIM_CLASS);
            claimGetUUID = claim.getMethod("getUUID");
            claimGetName = claim.getMethod("getName");
            claimGetOwnerFactionStringID = claim.getMethod("getOwnerFactionStringID");
            claimGetCenter = claim.getMethod("getCenter");
            claimGetClaimedChunks = claim.getMethod("getClaimedChunks");
            claimGetHealth = claim.getMethod("getHealth");
            claimGetMaxHealth = claim.getMethod("getMaxHealth");
            claimIsUnderSiegeField = claim.getField("isUnderSiege");

            RESOLVED.set(true);
            FactionLogger.LOG.info("[FactionRaids] Recruits claims bridge ready.");
        } catch (Throwable t) {
            RESOLUTION_FAILED.set(true);
            FactionLogger.LOG.warn("[FactionRaids] Recruits claims bridge unavailable: {} {} - claim-awareness disabled this session.",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    // ------------------------------------------------------------------ core lookups

    /** Snapshot of the claim covering this chunk, or empty. */
    public static Optional<ClaimSnapshot> getClaimAt(ServerLevel level, ChunkPos chunk) {
        if (!available() || level == null || chunk == null) return Optional.empty();
        try {
            Object manager = claimManagerField.get(null);
            if (manager == null) return Optional.empty();
            Object claim = getClaimByChunkPos.invoke(manager, chunk);
            return snapshot(claim);
        } catch (Throwable t) {
            // Recruits changed shape mid-session. Kill the bridge for this run.
            RESOLUTION_FAILED.set(true);
            FactionLogger.LOG.warn("[FactionRaids] Recruits claim lookup failed: {} {} - bridge disabled for the rest of this session.",
                    t.getClass().getSimpleName(), t.getMessage());
            return Optional.empty();
        }
    }

    /** Convenience overload keyed by a block position. */
    public static Optional<ClaimSnapshot> getClaimAt(ServerLevel level, BlockPos pos) {
        return pos == null ? Optional.empty() : getClaimAt(level, new ChunkPos(pos));
    }

    // ------------------------------------------------------------------ anchor-aware helpers

    /**
     * Resolves the claim that "belongs" to this Faction Raids anchor.
     *
     * <p>Walks the anchor's defense points and returns the first claim whose
     * {@code ownerFactionStringID} matches {@code anchor.teamKey()}. Returns
     * empty when no defense point sits inside a friendly claim (either the
     * player hasn't claimed anything with Recruits, they claimed with a
     * different faction, or Recruits is absent).
     *
     * <p>Called fresh per raid, so if the player deletes or resizes their
     * claim between raids the new state is honored on the next lookup.
     */
    public static Optional<ClaimSnapshot> resolveDefendingClaim(ServerLevel level, RaidSavedData.Anchor anchor) {
        if (!available() || level == null || anchor == null) return Optional.empty();
        String teamKey = anchor.teamKey();
        if (teamKey == null || teamKey.isBlank()) return Optional.empty();
        for (RaidSavedData.DefensePoint point : anchor.defensePoints().values()) {
            Optional<ClaimSnapshot> snap = getClaimAt(level, point.pos());
            if (snap.isPresent() && teamKey.equals(snap.get().ownerFactionStringId())) {
                return snap;
            }
        }
        return Optional.empty();
    }

    /** True when the chunk is claimed by the given faction (defender). */
    public static boolean isChunkOwnedBy(ServerLevel level, ChunkPos chunk, String factionStringId) {
        if (factionStringId == null || factionStringId.isBlank()) return false;
        return getClaimAt(level, chunk)
                .map(s -> factionStringId.equals(s.ownerFactionStringId()))
                .orElse(false);
    }

    // ------------------------------------------------------------------ internals

    @SuppressWarnings("unchecked")
    private static Optional<ClaimSnapshot> snapshot(Object claim) throws ReflectiveOperationException {
        if (claim == null) return Optional.empty();
        UUID uuid = (UUID) claimGetUUID.invoke(claim);
        String name = (String) claimGetName.invoke(claim);
        String owner = (String) claimGetOwnerFactionStringID.invoke(claim);
        ChunkPos center = (ChunkPos) claimGetCenter.invoke(claim);
        Collection<ChunkPos> chunks = (Collection<ChunkPos>) claimGetClaimedChunks.invoke(claim);
        int health = (int) claimGetHealth.invoke(claim);
        int maxHealth = (int) claimGetMaxHealth.invoke(claim);
        boolean underSiege = claimIsUnderSiegeField.getBoolean(claim);
        Set<ChunkPos> chunkSet = chunks == null ? Collections.emptySet() : new HashSet<>(chunks);
        return Optional.of(new ClaimSnapshot(uuid, name, owner, center, chunkSet, underSiege, health, maxHealth));
    }

    /**
     * Immutable snapshot of a Recruits claim taken at the moment of query.
     * Safe to hold across ticks; will simply be stale if the source claim
     * changes. Re-query for fresh data.
     */
    public record ClaimSnapshot(
            UUID claimId,
            String claimName,
            String ownerFactionStringId,
            ChunkPos center,
            Set<ChunkPos> chunks,
            boolean isUnderSiege,
            int health,
            int maxHealth
    ) {
        public ClaimSnapshot {
            chunks = chunks == null ? Set.of() : Set.copyOf(chunks);
        }

        /** Chunk-set membership check without exposing the internal collection type. */
        public boolean contains(ChunkPos chunk) {
            return chunk != null && chunks.contains(chunk);
        }
    }
}
