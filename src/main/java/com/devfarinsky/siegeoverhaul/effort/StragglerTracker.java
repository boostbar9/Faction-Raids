package com.devfarinsky.siegeoverhaul.effort;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
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

/** Retry stalled paths without teleporting soldiers; retire only persistently stationary stragglers. */
public final class StragglerTracker {

    public static final int SAMPLE_INTERVAL_TICKS = 15 * 20; // 15 s
    public static final double PROGRESS_EPSILON = 2.0D;      // blocks

    private static final Map<UUID, Vec3> POSITIONS = new HashMap<>();
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
        Set<UUID> teamSet = TEAM_RAIDERS.computeIfAbsent(state.teamKey, k -> new HashSet<>());

        int dropped = 0;
        Iterator<UUID> it = state.raiders.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                TRACKS.remove(id);
                POSITIONS.remove(id);
                teamSet.remove(id);
                continue;
            }
            // Holding the active objective, fighting a visible defender, and
            // operating/riding a vehicle are useful stationary states. Never
            // teleport or retire those mobs as if they were lost en route.
            int objectiveRadius = !RaidConfig.ENABLE_BREACH_PHASE.get() || state.breached
                    ? RaidConfig.CAPTURE_RADIUS.get() : RaidConfig.BREACH_OBJECTIVE_RADIUS.get();
            var target = mob.getTarget();
            if (mob.distanceToSqr(objVec) <= (double) objectiveRadius * objectiveRadius
                    || mob.isPassenger()
                    || com.devfarinsky.siegeoverhaul.siege.RaiderLadderGoal.assigned(mob)
                    || (target != null && target.isAlive() && mob.getSensing().hasLineOfSight(target))) {
                TRACKS.remove(id);
                POSITIONS.remove(id);
                teamSet.remove(id);
                continue;
            }
            // v2.18.0: compare real block distances, not squared. Old code
            // stored distSq and compared delta against EPSILON*EPSILON,
            // which made the threshold effectively vanish for raiders far
            // from the objective (squared-distance changes a lot per meter
            // when you're far out).
            int distBlocks = (int) Math.min(Integer.MAX_VALUE, Math.sqrt(mob.distanceToSqr(objVec)));
            Vec3 previous = POSITIONS.put(id, mob.position());
            int[] track = TRACKS.get(id);
            if (track == null) {
                TRACKS.put(id, new int[]{distBlocks, 0});
                teamSet.add(id);
                continue;
            }
            int delta = track[0] - distBlocks;
            if (delta >= PROGRESS_EPSILON || (previous != null
                    && previous.distanceToSqr(mob.position()) >= PROGRESS_EPSILON * PROGRESS_EPSILON)) {
                // Made progress toward objective -- reset strikes.
                track[0] = distBlocks;
                track[1] = 0;
                continue;
            }
            // No meaningful progress in the sample window.
            track[1] += 1;
            if (track[1] == 1) {
                com.devfarinsky.siegeoverhaul.formations.RecruitsFormationBridge.release(mob);
                mob.getNavigation().stop();
                mob.getNavigation().moveTo(objVec.x, objVec.y, objVec.z, RaidConfig.RAIDER_ADVANCE_SPEED.get());
                track[0] = (int) Math.min(Integer.MAX_VALUE, Math.sqrt(mob.distanceToSqr(objVec)));
            } else if (track[1] >= 4) {
                FactionLogger.LOG.debug("Dropping stuck raider {} from wave for team {}",
                        id, state.teamKey);
                mob.discard();
                it.remove();
                state.missingTicks.remove(id);
                state.lastKnownChunks.remove(id);
                state.totalEscaped++;
                TRACKS.remove(id);
                POSITIONS.remove(id);
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
            for (UUID id : teamSet) { TRACKS.remove(id); POSITIONS.remove(id); }
        }
    }

}
