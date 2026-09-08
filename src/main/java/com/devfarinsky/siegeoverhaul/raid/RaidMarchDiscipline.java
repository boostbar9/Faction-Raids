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
    }
    public static boolean retainTarget(Mob mob,LivingEntity target,Vec3 objective,double range) {
        return target!=null && target.isAlive() && target.level()==mob.level() && !mob.isAlliedTo(target)
                && (!(target instanceof net.minecraft.world.entity.player.Player p) || !p.isCreative() && !p.isSpectator())
                && mob.distanceToSqr(target)<=range*range
                && target.distanceToSqr(objective)<=Math.max(48*48,mob.distanceToSqr(objective)+32*32);
    }
}
