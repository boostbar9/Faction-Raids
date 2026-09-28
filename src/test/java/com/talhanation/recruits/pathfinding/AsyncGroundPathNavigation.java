package com.talhanation.recruits.pathfinding;

/** API-shape fixture: native Recruits ground navigation does NOT extend vanilla ground navigation. */
public abstract class AsyncGroundPathNavigation extends net.minecraft.world.entity.ai.navigation.PathNavigation {
    protected AsyncGroundPathNavigation(net.minecraft.world.entity.Mob mob,net.minecraft.world.level.Level level) {
        super(mob,level);
    }
}
