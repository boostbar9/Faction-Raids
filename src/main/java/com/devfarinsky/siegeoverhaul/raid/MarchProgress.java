package com.devfarinsky.siegeoverhaul.raid;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Keep useful paths alive; retry a failed or stationary route without teleporting. */
public final class MarchProgress {
    private static final String ADVANCE_AT="MarchAdvanceAt", BEST_DISTANCE="MarchBestDistance";
    private MarchProgress() {}
    public static boolean shouldRepath(Mob mob, Vec3 destination, long now) {
        var data=mob.getPersistentData();
        Vec3 previous=new Vec3(data.getDouble("MarchX"),data.getDouble("MarchY"),data.getDouble("MarchZ"));
        Vec3 goal=new Vec3(data.getDouble("MarchGoalX"),data.getDouble("MarchGoalY"),data.getDouble("MarchGoalZ"));
        boolean fresh=!data.contains("MarchProgressAt") || now<data.getLong("MarchProgressAt");
        boolean moved=mob.position().distanceToSqr(previous)>=0.25;
        boolean changed=destination.distanceToSqr(goal)>4;
        double remaining=mob.position().distanceTo(destination);
        boolean advanceFresh=fresh || changed || !data.contains(ADVANCE_AT);
        boolean advancing=advanceFresh || remaining < data.getDouble(BEST_DISTANCE)-.5;
        boolean circling=!advanceFresh && now-data.getLong(ADVANCE_AT)>=200;
        if(advancing || circling) {
            data.putDouble(BEST_DISTANCE,remaining);data.putLong(ADVANCE_AT,now);
        }
        boolean stalled=!fresh && !moved && now-data.getLong("MarchProgressAt")>=40;
        if(fresh || moved || changed || stalled) {
            data.putDouble("MarchX",mob.getX());data.putDouble("MarchY",mob.getY());data.putDouble("MarchZ",mob.getZ());
            data.putLong("MarchProgressAt",now);
        }
        data.putDouble("MarchGoalX",destination.x);data.putDouble("MarchGoalY",destination.y);data.putDouble("MarchGoalZ",destination.z);
        return fresh || changed || stalled || circling || mob.getNavigation().isDone();
    }
}
