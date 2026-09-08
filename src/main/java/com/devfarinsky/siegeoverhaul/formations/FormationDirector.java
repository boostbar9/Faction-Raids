package com.devfarinsky.siegeoverhaul.formations;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Issues native walking orders; combat and objective navigation release them explicitly. */
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
        raiders.removeIf(mob -> !shouldMarch(level, state.teamKey, mob, objective));
        // Stable input order keeps surviving soldiers in their existing slots.
        raiders.sort(java.util.Comparator.comparing(Mob::getUUID));
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
        Vec3 direction = objVec.subtract(centroid).multiply(1, 0, 1);
        double len = direction.length();
        Vec3 forward = len < 1.0E-3 ? new Vec3(1, 0, 0) : direction.scale(1.0D / len);
        Vec3 waypoint = centroid.add(forward.scale(Math.min(WAYPOINT_LEAD, len - 1.0D)));

        boolean dispatched = RecruitsFormationBridge.apply(formation, forward, waypoint, raiders, false);
        LAST_APPLIED.put(state.teamKey, now);
        return dispatched;
    }

    public static boolean shouldMarch(ServerLevel level, String defendingTeam, Mob mob, BlockPos objective) {
        return shouldMarch(mob, objective)
                && com.devfarinsky.siegeoverhaul.core.SiegeCore.claimed(level, mob.blockPosition(), defendingTeam);
    }

    public static boolean shouldMarch(Mob mob, BlockPos objective) {
        return RaidConfig.ENABLE_FORMATIONS.get() && !mob.isPassenger()
                && !com.devfarinsky.siegeoverhaul.siege.RaiderLadderGoal.assigned(mob)
                && !mob.horizontalCollision && !mob.onClimbable()
                && (mob.getTarget() == null || !mob.getTarget().isAlive())
                && mob.distanceToSqr(Vec3.atCenterOf(objective)) > DISSOLVE_DISTANCE * DISSOLVE_DISTANCE;
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
