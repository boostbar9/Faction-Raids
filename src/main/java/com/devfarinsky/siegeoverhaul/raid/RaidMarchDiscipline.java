package com.devfarinsky.siegeoverhaul.raid;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.phys.Vec3;

/** Siege-owned troops follow siege orders instead of starting ambient patrols. */
public final class RaidMarchDiscipline {
    private RaidMarchDiscipline() {}
    public static void install(Mob mob) {
        for (var wrapped : new java.util.ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            var goal=wrapped.getGoal();String name=goal.getClass().getName();
            if (goal instanceof RandomStrollGoal || name.endsWith(".RecruitWanderGoal")
                    || name.endsWith("$LongDistancePatrolGoal")) mob.goalSelector.removeGoal(goal);
        }
        mob.clearRestriction();
        for (Class<?> type = mob.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.talhanation.recruits.entities.AbstractLeaderEntity")) {
                releaseNativePatrol(mob);
                break;
            }
        }
    }

    /** Native leaders otherwise start a second army controller that can regroup/hold/retreat. */
    static boolean releaseNativePatrol(Object leader) {
        try {
            var action = leader.getClass().getMethod("setEnemyAction", byte.class);
            java.lang.reflect.Method patrol = null;
            Object idle = null;
            for (var method : leader.getClass().getMethods()) {
                if (!method.getName().equals("setPatrolState") || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isEnum()) continue;
                for (Object value : method.getParameterTypes()[0].getEnumConstants()) {
                    if (((Enum<?>) value).name().equals("IDLE")) { patrol = method; idle = value; }
                }
            }
            if (patrol == null) return false;
            // Recruits 1.15.2 EnemyAction.KEEP_PATROLLING: skip its army attack controller,
            // while leaving individual melee/target goals and Siege Overhaul orders active.
            action.invoke(leader, (byte) 2);
            patrol.invoke(leader, idle);
            leader.getClass().getMethod("setFollowState", int.class).invoke(leader, 0);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false; // Ordinary mobs do not expose the optional leader API.
        }
    }
    /** Keep assault specialists on approach, but permit combat once they reach the objective area. */
    public static boolean pushPastDefenders(String role, boolean enabled, boolean atObjective) {
        return enabled && !atObjective && ("breacher".equals(role) || "commander".equals(role));
    }
    public static boolean retainTarget(Mob mob,LivingEntity target,Vec3 objective,double range) {
        return target!=null && target.isAlive() && target.level()==mob.level() && !mob.isAlliedTo(target)
                && (!(target instanceof net.minecraft.world.entity.player.Player p) || !p.isCreative() && !p.isSpectator())
                && mob.distanceToSqr(target)<=range*range
                && target.distanceToSqr(objective)<=Math.max(48*48,mob.distanceToSqr(objective)+32*32);
    }
}
