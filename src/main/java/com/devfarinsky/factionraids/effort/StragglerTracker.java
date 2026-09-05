package com.devfarinsky.factionraids.effort;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detects raiders that stall out on the way to the objective, teleports
 * them forward once, and drops them from the wave count if they stall
 * again. Stops raids from grinding to a halt because a handful of raiders
 * are stuck behind a fence, in a tree, or on a hillside.
 *
 * <p>Tracks two ints per raider (in memory, not persisted):
 * <ul>
 *   <li>{@code lastDist}   -- last sampled distance to objective, in blocks</li>
 *   <li>{@code strikes}    -- how many stall-and-rescue cycles they've hit</li>
 * </ul>
 *
 * <p>Sample cadence: {@link #SAMPLE_INTERVAL_TICKS} ticks. Progress test:
 * distance to objective must decrease by at least {@link #PROGRESS_EPSILON}
 * blocks between samples, otherwise a strike lands. First strike -> teleport
 * near the raider centroid, one block toward the objective. Second strike
 * -> remove from {@code state.raiders} so the wave can advance without
 * them. The entity keeps living and can still fight.
 *
 * <p>v2.18.0 audit fixes:
 * <ul>
 *   <li>Progress check compares real block distance (not squared distance)
 *       so the threshold is consistent regardless of how far the raider
 *       is from the objective. The old {@code delta >= EPSILON * EPSILON}
 *       compared a squared-distance delta against 4, which meant a raider
 *       100 blocks out only had to change squared-distance by 4 (roughly
 *       0.02 real blocks) to pass, while one 3 blocks out needed a much
 *       larger jump.</li>
 *   <li>{@link #forget(String)} now drains all per-team UUID entries
 *       from {@code TRACKS} instead of leaking them. Wave-end
 *       {@code .discard()} does not fire death events, so the earlier
 *       "TRACKS entries drain themselves" assumption was optimistic and
 *       let the map grow unbounded over long uptimes.</li>
 *   <li>Rescue teleport snaps to the surface Y at the rescue XZ instead
 *       of using the centroid Y, so a raider stuck on a plateau does not
 *       get warped down into a valley (or a raider in a valley up onto
 *       a plateau).</li>
 * </ul>
 */
public final class StragglerTracker {

    public static final int SAMPLE_INTERVAL_TICKS = 15 * 20; // 15 s
    public static final double PROGRESS_EPSILON = 2.0D;      // blocks

    private static final Map<UUID, int[]> TRACKS = new HashMap<>();
    private static final Map<String, Long> LAST_SAMPLE = new HashMap<>();
    // v2.18.0: track which raider UUIDs belong to which team so forget()
    // can drain the entries when a raid ends, instead of leaking them
    // whenever a raider is removed via .discard() (which never fires a
    // death event and so never triggers the old passive cleanup).
    private static final Map<String, Set<UUID>> TEAM_RAIDERS = new HashMap<>();

    private StragglerTracker() {}

    /**
     * Run one sampling pass for a raid. Cheap to call every server tick --
     * internally rate-limited to SAMPLE_INTERVAL_TICKS per raid.
     *
     * @return number of raiders dropped from the wave this call
     */
    public static int tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (!RaidConfig.ENABLE_STRAGGLER_RESCUE.get()) return 0;
        if (state == null || state.raiders.isEmpty() || objective == null) return 0;

        long now = level.getGameTime();
        Long last = LAST_SAMPLE.get(state.teamKey);
        if (last != null && now - last < SAMPLE_INTERVAL_TICKS) return 0;
        LAST_SAMPLE.put(state.teamKey, now);

        Vec3 objVec = Vec3.atCenterOf(objective);
        // Centroid of currently-alive raiders -- rescue target when we teleport.
        Vec3 centroid = centroidOfLiveRaiders(level, state, objVec);
        Vec3 toObjective = objVec.subtract(centroid).normalize();
        Vec3 rescueTarget = centroid.add(toObjective.scale(2.0D));

        Set<UUID> teamSet = TEAM_RAIDERS.computeIfAbsent(state.teamKey, k -> new HashSet<>());

        int dropped = 0;
        Iterator<UUID> it = state.raiders.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                TRACKS.remove(id);
                teamSet.remove(id);
                continue;
            }
            // v2.18.0: compare real block distances, not squared. Old code
            // stored distSq and compared delta against EPSILON*EPSILON,
            // which made the threshold effectively vanish for raiders far
            // from the objective (squared-distance changes a lot per meter
            // when you're far out).
            int distBlocks = (int) Math.min(Integer.MAX_VALUE, Math.sqrt(mob.distanceToSqr(objVec)));
            int[] track = TRACKS.get(id);
            if (track == null) {
                TRACKS.put(id, new int[]{distBlocks, 0});
                teamSet.add(id);
                continue;
            }
            int delta = track[0] - distBlocks;
            if (delta >= PROGRESS_EPSILON) {
                // Made progress toward objective -- reset strikes.
                track[0] = distBlocks;
                track[1] = 0;
                continue;
            }
            // No meaningful progress in the sample window.
            track[1] += 1;
            if (track[1] == 1) {
                rescueByTeleport(level, mob, rescueTarget);
                track[0] = (int) Math.min(Integer.MAX_VALUE, Math.sqrt(mob.distanceToSqr(objVec)));
            } else if (track[1] >= 2) {
                FactionLogger.LOG.debug("Dropping stuck raider {} from wave for team {}",
                        id, state.teamKey);
                it.remove();
                TRACKS.remove(id);
                teamSet.remove(id);
                dropped++;
            }
        }
        return dropped;
    }

    /**
     * Forget a raid's stall tracks -- call on raid end.
     *
     * <p>v2.18.0: actually drains {@code TRACKS}. The old comment claimed
     * entries "drain themselves as raiders die/despawn," but raiders
     * removed with {@code Entity.discard()} on wave/raid end do not fire
     * a death event, so their tracks used to leak permanently. Over long
     * server uptime with many raids, the map grew unbounded.
     */
    public static void forget(String teamKey) {
        LAST_SAMPLE.remove(teamKey);
        Set<UUID> teamSet = TEAM_RAIDERS.remove(teamKey);
        if (teamSet != null) {
            for (UUID id : teamSet) TRACKS.remove(id);
        }
    }

    private static Vec3 centroidOfLiveRaiders(ServerLevel level,
                                              RaidSavedData.RaidState state,
                                              Vec3 fallback) {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (UUID id : state.raiders) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob && mob.isAlive()) {
                x += mob.getX();
                y += mob.getY();
                z += mob.getZ();
                n++;
            }
        }
        if (n == 0) return fallback;
        return new Vec3(x / n, y / n, z / n);
    }

    /**
     * v2.18.0: snap the rescue Y to the surface height at the target XZ
     * so a raider stuck on a plateau is not dumped into a valley (and
     * vice versa). The old code used the centroid Y, which meant one
     * outlier's rescue placed them anywhere from underwater to floating.
     */
    private static void rescueByTeleport(ServerLevel level, Mob mob, Vec3 target) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(target.x), (int) Math.floor(target.z));
        mob.teleportTo(target.x, surfaceY, target.z);
    }
}
