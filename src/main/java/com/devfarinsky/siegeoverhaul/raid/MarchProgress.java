package com.devfarinsky.siegeoverhaul.raid;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Keep useful paths alive; retry a failed or stationary route without teleporting. */
public final class MarchProgress {
    private MarchProgress() {}
    public static boolean shouldRepath(Mob mob, Vec3 destination, long now) {
        var data=mob.getPersistentData();
        Vec3 previous=new Vec3(data.getDouble("MarchX"),data.getDouble("MarchY"),data.getDouble("MarchZ"));
        Vec3 goal=new Vec3(data.getDouble("MarchGoalX"),data.getDouble("MarchGoalY"),data.getDouble("MarchGoalZ"));
        boolean fresh=!data.contains("MarchProgressAt") || now<data.getLong("MarchProgressAt");
        boolean moved=mob.position().distanceToSqr(previous)>=0.25;
        boolean changed=destination.distanceToSqr(goal)>4;
        boolean stalled=!fresh && !moved && now-data.getLong("MarchProgressAt")>=40;
        if(fresh || moved || changed || stalled) {
            data.putDouble("MarchX",mob.getX());data.putDouble("MarchY",mob.getY());data.putDouble("MarchZ",mob.getZ());
            data.putLong("MarchProgressAt",now);
        }
        data.putDouble("MarchGoalX",destination.x);data.putDouble("MarchGoalY",destination.y);data.putDouble("MarchGoalZ",destination.z);
        return fresh || changed || stalled || mob.getNavigation().isDone();
    }
}
