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
                limitNativeArmyController(mob, mob::isPassenger);
                break;
            }
        }
    }

    /** CaptainEntity ticks this controller even with patrol state IDLE and KEEP_PATROLLING set. */
    static boolean limitNativeArmyController(Object leader, java.util.function.BooleanSupplier passenger) {
        try {
            var field = leader.getClass().getField("attackController");
            Object controller = field.get(leader);
            if (controller == null || !field.getType().isInterface()) return false;
            if (java.lang.reflect.Proxy.isProxyClass(controller.getClass())
                    && java.lang.reflect.Proxy.getInvocationHandler(controller) instanceof LandArmyOrders) return true;
            field.set(leader, java.lang.reflect.Proxy.newProxyInstance(field.getType().getClassLoader(),
                    new Class<?>[]{field.getType()}, new LandArmyOrders(controller, passenger)));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private record LandArmyOrders(Object controller, java.util.function.BooleanSupplier passenger)
            implements java.lang.reflect.InvocationHandler {
        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            // Siege owns land army movement. Keep native ship orders and all individual combat goals.
            if (!passenger.getAsBoolean() && method.getParameterCount() == 0
                    && method.getReturnType() == void.class
                    && method.getName().equals("tick")) return null;
            try {
                return method.invoke(controller, args);
            } catch (java.lang.reflect.InvocationTargetException ex) {
                throw ex.getCause();
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
            // Recruits 1.15.2 EnemyAction.KEEP_PATROLLING: skip its patrol attack state,
            // while leaving individual melee/target goals and Siege Overhaul orders active.
            action.invoke(leader, (byte) 2);
            patrol.invoke(leader, idle);
            leader.getClass().getMethod("setFollowState", int.class).invoke(leader, 0);
            for (String name : new String[]{"isInFormation", "holdFormation"}) {
                try {
                    leader.getClass().getField(name).setBoolean(leader, false);
                } catch (NoSuchFieldException ignored) { }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false; // Ordinary mobs do not expose the optional leader API.
        }
    }
    /** Keep assault specialists on approach, but permit combat once they reach the objective area. */
    public static boolean pushPastDefenders(String role, boolean enabled, boolean atObjective) {
        return enabled && !atObjective && ("breacher".equals(role) || "commander".equals(role));
    }
    public static boolean isLeader(String role) {
        return com.devfarinsky.siegeoverhaul.formations.FormationTactics.isLeader(role);
    }
    /** Captains intercept nearby defenders, rather than chasing distractions across the approach. */
    public static double approachAggroRangeSq(double rangeSq, String role, boolean atObjective, double scale) {
        if (atObjective) return rangeSq;
        String normalized = com.devfarinsky.siegeoverhaul.formations.FormationTactics.normalizeRole(role);
        if ("breacher".equals(normalized) || "commander".equals(normalized)) return rangeSq * scale * scale;
        if (isLeader(normalized)) return Math.min(rangeSq, Math.max(8 * 8, rangeSq * scale * scale));
        return rangeSq;
    }
    public static boolean retainTarget(Mob mob,LivingEntity target,Vec3 objective,double range) {
        return target!=null && target.isAlive() && target.level()==mob.level() && !mob.isAlliedTo(target)
                && (!(target instanceof net.minecraft.world.entity.player.Player p) || !p.isCreative() && !p.isSpectator())
                && mob.distanceToSqr(target)<=range*range
                && target.distanceToSqr(objective)<=Math.max(48*48,mob.distanceToSqr(objective)+32*32);
    }
}
