package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Read-only access to the production standing-site predicate in an initialized QA world. */
public final class NativeQaBuilderStanding {
    private NativeQaBuilderStanding() {}
    public static boolean admits(ServerLevel level, Mob builder, BlockPos feet) {
        return WallBuilderAccess.standingSites(level, builder, feet).contains(feet);
    }
}
