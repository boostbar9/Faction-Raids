package com.devfarinsky.factionraids.formations;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-raid ticker that keeps raiders in the formation their
 * {@link com.devfarinsky.factionraids.waves.WaveComposition} asked for.
 *
 * <p>Recruits' formation methods place recruits at fixed positions each call.
 * If we called every tick the recruits would freeze in place, so
 * {@code FormationDirector} rate-limits reapplication to one call per
 * {@link #REAPPLY_TICKS} ticks. That's slow enough to let the pathfinder
 * actually move between calls, and fast enough that the formation reforms
 * as members die or fall behind.
 *
 * <p>Target selection uses the raid's approach angle: raiders form up at an
 * intermediate waypoint short of the objective, then advance as a bloc.
 * Once close enough, the formation dissolves (returned as
 * {@link Formation#NONE}) so raiders can freely close and attack.
 */
public final class FormationDirector {

    /** Minimum ticks between formation reapplication per raid. 4 seconds. */
    public static final int REAPPLY_TICKS = 80;

    /** Distance from objective at which the formation dissolves and raiders swarm. */
    public static final double DISSOLVE_DISTANCE = 12.0D;

    /** Distance ahead of the raiders (toward the objective) to place the formation waypoint. */
    public static final double WAYPOINT_LEAD = 6.0D;

    /** Per-team-key last-tick timestamps so all raids share one lightweight ticker. */
    private static final Map<String, Long> LAST_APPLIED = new HashMap<>();

    private FormationDirector() {}

    /**
     * Attempt to hold or reapply the formation for one raid.
     *
     * @param level      raid level
     * @param state      raid state
     * @param objective  point the raiders are attacking
     * @param formation  formation the current wave should hold
     * @return true when a formation call was actually dispatched (for logs)
     */
    public static boolean tick(ServerLevel level, RaidSavedData.RaidState state,
                                BlockPos objective, Formation formation) {
        if (!RaidConfig.ENABLE_FORMATIONS.get()) return false;
        if (formation == null || formation == Formation.NONE) return false;
        if (state == null || state.raiders.isEmpty()) return false;
        if (!RecruitsFormationBridge.available()) return false;

        long now = level.getGameTime();
        Long last = LAST_APPLIED.get(state.teamKey);
        if (last != null && now - last < REAPPLY_TICKS) return false;

        List<Mob> raiders = collectLiveRaiders(level, state);
        if (raiders.isEmpty()) return false;

        Vec3 centroid = centroidOf(raiders);
        Vec3 objVec = Vec3.atCenterOf(objective);
        double distance = centroid.distanceTo(objVec);
        // Close enough: let raiders swarm instead of clumping in a shape.
        if (distance <= DISSOLVE_DISTANCE) {
            LAST_APPLIED.put(state.teamKey, now);
            return false;
        }

        // Advance vector = normalized centroid -> objective. Waypoint sits
        // WAYPOINT_LEAD blocks ahead of the centroid, on the same vector.
        Vec3 direction = objVec.subtract(centroid);
        double len = direction.length();
        Vec3 forward = len < 1.0E-3 ? new Vec3(1, 0, 0) : direction.scale(1.0D / len);
        Vec3 waypoint = centroid.add(forward.scale(Math.min(WAYPOINT_LEAD, len - 1.0D)));

        boolean dispatched = RecruitsFormationBridge.apply(formation, forward, waypoint, raiders, false);
        LAST_APPLIED.put(state.teamKey, now);
        return dispatched;
    }

    /** Forget a raid — call when the raid ends so the map stays bounded. */
    public static void forget(String teamKey) {
        LAST_APPLIED.remove(teamKey);
    }

    private static List<Mob> collectLiveRaiders(ServerLevel level,
                                                 RaidSavedData.RaidState state) {
        List<Mob> out = new ArrayList<>();
        for (var id : state.raiders) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob && mob.isAlive()) out.add(mob);
        }
        return out;
    }

    private static Vec3 centroidOf(List<Mob> raiders) {
        double x = 0, y = 0, z = 0;
        for (Mob mob : raiders) {
            x += mob.getX();
            y += mob.getY();
            z += mob.getZ();
        }
        int n = raiders.size();
        return new Vec3(x / n, y / n, z / n);
    }
}
