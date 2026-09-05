package com.devfarinsky.factionraids.effort;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detects raiders that stall out on the way to the objective, teleports
 * them forward once, and drops them from the wave count if they stall
 * again. Stops raids from grinding to a halt because a handful of raiders
 * are stuck behind a fence, in a tree, or on a hillside.
 *
 * <p>Tracks two ints per raider (in memory, not persisted):
 * <ul>
 *   <li>{@code lastDistSq}   \u2014 last sampled squared distance to objective</li>
 *   <li>{@code strikes}      \u2014 how many stall-and-rescue cycles they've hit</li>
 * </ul>
 *
 * <p>Sample cadence: {@link #SAMPLE_INTERVAL_TICKS} ticks. Progress test:
 * distance to objective must decrease by at least
 * {@link #PROGRESS_EPSILON} blocks between samples, otherwise a strike lands.
 * First strike \u2192 teleport near the raider centroid, one block toward the
 * objective. Second strike \u2192 remove from {@code state.raiders} so the wave
 * can advance without them. The entity keeps living and can still fight.
 */
public final class StragglerTracker {

    public static final int SAMPLE_INTERVAL_TICKS = 15 * 20; // 15 s
    public static final double PROGRESS_EPSILON = 2.0D;      // blocks

    private static final Map<UUID, int[]> TRACKS = new HashMap<>();
    private static final Map<String, Long> LAST_SAMPLE = new HashMap<>();

    private StragglerTracker() {}

    /**
     * Run one sampling pass for a raid. Cheap to call every server tick \u2014
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
        // Centroid of currently-alive raiders \u2014 rescue target when we teleport.
        Vec3 centroid = centroidOfLiveRaiders(level, state, objVec);
        Vec3 toObjective = objVec.subtract(centroid).normalize();
        Vec3 rescueTarget = centroid.add(toObjective.scale(2.0D));

        int dropped = 0;
        Iterator<UUID> it = state.raiders.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                TRACKS.remove(id);
                continue;
            }
            double distSq = mob.distanceToSqr(objVec);
            int[] track = TRACKS.get(id);
            if (track == null) {
                TRACKS.put(id, new int[]{(int) Math.min(Integer.MAX_VALUE, distSq), 0});
                continue;
            }
            double delta = track[0] - distSq;
            if (delta >= PROGRESS_EPSILON * PROGRESS_EPSILON) {
                // Made progress toward objective \u2014 reset strikes.
                track[0] = (int) Math.min(Integer.MAX_VALUE, distSq);
                track[1] = 0;
                continue;
            }
            // No meaningful progress in the sample window.
            track[1] += 1;
            if (track[1] == 1) {
                rescueByTeleport(mob, rescueTarget);
                track[0] = (int) Math.min(Integer.MAX_VALUE, mob.distanceToSqr(objVec));
            } else if (track[1] >= 2) {
                FactionLogger.LOG.debug("Dropping stuck raider {} from wave for team {}",
                        id, state.teamKey);
                it.remove();
                TRACKS.remove(id);
                dropped++;
            }
        }
        return dropped;
    }

    /** Forget a raid's stall tracks \u2014 call on raid end. */
    public static void forget(String teamKey) {
        LAST_SAMPLE.remove(teamKey);
        // TRACKS entries drain themselves as raiders die/despawn; nothing to
        // key on teamKey here without a heavier lookup. They're bounded by
        // the total raider UUID space per raid, which is small.
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

    private static void rescueByTeleport(Mob mob, Vec3 target) {
        // Use teleportTo so the client updates cleanly.
        mob.teleportTo(target.x, target.y, target.z);
    }
}
