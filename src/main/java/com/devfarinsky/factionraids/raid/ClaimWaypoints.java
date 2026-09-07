package com.devfarinsky.factionraids.raid;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import com.devfarinsky.factionraids.compat.RecruitsClaimsBridge;
import com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.ClaimSnapshot;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v2.35.0 &mdash; visualize the defending team's Recruits claim boundary
 * during a raid so defenders can see the line they're holding.
 *
 * <p>Two view distances, one visual grammar:
 * <ul>
 *   <li><b>Near</b> (within {@code claimEdgeRadius}): sampled particle
 *       fence along the exposed edges of the claim's chunk set. Reads as
 *       a shimmering palisade at ground level.</li>
 *   <li><b>Far</b> (beyond {@code claimEdgeRadius}): vertical particle
 *       columns at the four corners of the claim's chunk bounding box.
 *       Reads from across a valley &mdash; same visual grammar as the
 *       {@code broadcastObjectiveBeacon} column.</li>
 * </ul>
 *
 * <p>Boundaries come from Recruits via {@link RecruitsClaimsBridge}. No
 * Faction Raids data is duplicated; if the player resizes their claim in
 * Recruits, the new shape is picked up on the next cache refresh (default
 * 20 seconds).
 *
 * <p>Restraint: defenders-only, active-raid-only. Attackers do not see
 * the boundary. When Recruits is absent or the anchor has no matching
 * claim, this service quietly no-ops.
 */
public final class ClaimWaypoints {

    private ClaimWaypoints() {}

    /**
     * How often we re-query Recruits for the defending claim's shape.
     * Cheap query, but there's no reason to hit reflection every raid
     * tick when the claim's chunk set rarely changes mid-siege.
     */
    private static final int CACHE_TTL_TICKS = 20 * 20; // 20 seconds

    /** Per-team edge sample cache keyed by team. Recomputed on TTL expiry or claim id change. */
    private static final Map<String, CacheEntry> CACHE = new HashMap<>();

    /** Cache entry: precomputed edge samples + corner columns + the source claim id. */
    private record CacheEntry(UUID claimId, long expiresAtGameTime,
                              List<Vec3> nearSamples,
                              List<int[]> farCorners /* [chunkX, chunkZ] pairs */) {}

    /**
     * Emit the waypoint pass for one raid tick. Called from {@code processRaid}
     * right after the objective beacon.
     *
     * @param level    the raid's ServerLevel
     * @param anchor   the defending anchor (used to resolve the claim)
     * @param members  online members of the defending team (viewer set)
     */
    public static void tick(ServerLevel level, RaidSavedData.Anchor anchor,
                            Collection<ServerPlayer> members) {
        if (!RaidConfig.CLAIM_WAYPOINTS_ENABLED.get()) return;
        if (level == null || anchor == null || members == null || members.isEmpty()) return;
        if (!RecruitsClaimsBridge.available()) return;

        CacheEntry entry = getOrRefreshCache(level, anchor);
        if (entry == null) return;

        int nearRadius = RaidConfig.CLAIM_WAYPOINTS_NEAR_RADIUS.get();
        double nearRadiusSq = (double) nearRadius * nearRadius;

        for (ServerPlayer viewer : members) {
            if (viewer.isSpectator() || viewer.level() != level) continue;
            emitFor(level, viewer, entry, nearRadiusSq);
        }
    }

    /**
     * Force-drop any cached edge geometry for this team. Called from the
     * raid teardown so a subsequent raid on the same team recomputes the
     * boundary against whatever Recruits reports next.
     */
    public static void invalidate(String teamKey) {
        if (teamKey != null) CACHE.remove(teamKey);
    }

    /** Drop everything. Called from server-stopping. */
    public static void shutdown() {
        CACHE.clear();
    }

    // ---- particle emission ----

    private static void emitFor(ServerLevel level, ServerPlayer viewer,
                                CacheEntry entry, double nearRadiusSq) {
        // Split into near/far based on the viewer's closest sample. This
        // avoids double-drawing the fence AND the corners for one viewer.
        double bestSq = Double.POSITIVE_INFINITY;
        for (Vec3 s : entry.nearSamples) {
            double dx = viewer.getX() - s.x;
            double dz = viewer.getZ() - s.z;
            double d = dx * dx + dz * dz;
            if (d < bestSq) bestSq = d;
        }
        if (bestSq <= nearRadiusSq) {
            emitNear(level, viewer, entry);
        } else {
            emitFar(level, viewer, entry);
        }
    }

    private static void emitNear(ServerLevel level, ServerPlayer viewer, CacheEntry entry) {
        // Dust particle so the color reads clearly at range without being
        // as loud as flame. Faction-red hue matches the alert palette used
        // elsewhere in the mod. Scale 1.2 keeps individual dots legible.
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(0.95F, 0.35F, 0.20F), 1.2F);
        for (Vec3 s : entry.nearSamples) {
            // Cull per-sample against a viewer-local horizon so we don't
            // ship particles the client will never render. Radius chosen
            // to be generous \u2014 fog fade in Minecraft eats them past ~128m.
            double dx = viewer.getX() - s.x;
            double dz = viewer.getZ() - s.z;
            if (dx * dx + dz * dz > 128.0 * 128.0) continue;
            level.sendParticles(viewer, dust, true, s.x, s.y + 0.5, s.z,
                    1, 0.05D, 0.02D, 0.05D, 0.0D);
        }
    }

    private static void emitFar(ServerLevel level, ServerPlayer viewer, CacheEntry entry) {
        // Corner columns \u2014 same visual grammar as the objective beacon.
        // Flame is deliberate: at range you want a torchlight silhouette,
        // not a subtle dust shimmer.
        for (int[] c : entry.farCorners) {
            double cx = (c[0] << 4) + 8.0;
            double cz = (c[1] << 4) + 8.0;
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) cx, (int) cz);
            for (int dy = 0; dy < 14; dy++) {
                level.sendParticles(viewer, ParticleTypes.FLAME, true,
                        cx, surfaceY + dy, cz, 1, 0.10D, 0.0D, 0.10D, 0.0D);
            }
        }
    }

    // ---- cache management ----

    private static CacheEntry getOrRefreshCache(ServerLevel level, RaidSavedData.Anchor anchor) {
        String teamKey = anchor.teamKey();
        long now = level.getGameTime();
        CacheEntry cached = CACHE.get(teamKey);

        // Fast path: cache is fresh AND we've already resolved a claim.
        if (cached != null && now < cached.expiresAtGameTime) return cached;

        // Slow path: refresh from Recruits.
        var snap = RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
        if (snap.isEmpty()) {
            // No matching claim - drop any stale cache and quietly no-op.
            CACHE.remove(teamKey);
            return null;
        }
        ClaimSnapshot claim = snap.get();
        if (claim.chunks().isEmpty()) {
            CACHE.remove(teamKey);
            return null;
        }

        // Recompute edges + corners.
        List<Vec3> nearSamples = sampleEdges(level, claim.chunks());
        List<int[]> farCorners = computeCorners(claim.chunks());
        CacheEntry fresh = new CacheEntry(claim.claimId(), now + CACHE_TTL_TICKS,
                nearSamples, farCorners);
        CACHE.put(teamKey, fresh);
        return fresh;
    }

    /**
     * Walk every chunk in the claim; for each, check its four chunk
     * neighbors. Every neighbor NOT in the set contributes 16 blocks of
     * exposed edge on that side. Sample along that edge at
     * {@code claimEdgeSamplePitch} intervals so the fence reads as a
     * continuous line without shipping a particle per block.
     */
    private static List<Vec3> sampleEdges(ServerLevel level, Set<ChunkPos> chunks) {
        int pitch = Math.max(1, RaidConfig.CLAIM_WAYPOINTS_SAMPLE_PITCH.get());
        List<Vec3> samples = new ArrayList<>();
        for (ChunkPos c : chunks) {
            int minX = c.x << 4;
            int minZ = c.z << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            // North edge (z = minZ) exposed?
            if (!chunks.contains(new ChunkPos(c.x, c.z - 1))) {
                for (int x = minX; x <= maxX; x += pitch) {
                    addSample(level, samples, x + 0.5, minZ + 0.05);
                }
            }
            // South edge (z = maxZ + 1) exposed?
            if (!chunks.contains(new ChunkPos(c.x, c.z + 1))) {
                for (int x = minX; x <= maxX; x += pitch) {
                    addSample(level, samples, x + 0.5, maxZ + 0.95);
                }
            }
            // West edge (x = minX) exposed?
            if (!chunks.contains(new ChunkPos(c.x - 1, c.z))) {
                for (int z = minZ; z <= maxZ; z += pitch) {
                    addSample(level, samples, minX + 0.05, z + 0.5);
                }
            }
            // East edge (x = maxX + 1) exposed?
            if (!chunks.contains(new ChunkPos(c.x + 1, c.z))) {
                for (int z = minZ; z <= maxZ; z += pitch) {
                    addSample(level, samples, maxX + 0.95, z + 0.5);
                }
            }
        }
        return samples;
    }

    private static void addSample(Level level, List<Vec3> out, double x, double z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) x, (int) z);
        out.add(new Vec3(x, y, z));
    }

    /**
     * Axis-aligned bounding box of the claim's chunk set gives us four
     * corner chunks. We render a vertical particle column above each. If
     * the claim is a single chunk all four corners collapse onto it,
     * which is fine (still a legible beacon at range).
     */
    private static List<int[]> computeCorners(Set<ChunkPos> chunks) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos c : chunks) {
            if (c.x < minX) minX = c.x;
            if (c.x > maxX) maxX = c.x;
            if (c.z < minZ) minZ = c.z;
            if (c.z > maxZ) maxZ = c.z;
        }
        List<int[]> corners = new ArrayList<>(4);
        corners.add(new int[]{minX, minZ});
        corners.add(new int[]{maxX, minZ});
        corners.add(new int[]{minX, maxZ});
        corners.add(new int[]{maxX, maxZ});
        return corners;
    }
}
