package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Camp decorations may never be placed at the defender's objective or edit an existing banner. */
public final class CampBannerPlacement {
    private CampBannerPlacement() {}
    public static boolean canPlace(ServerLevel level, RaidSavedData.RaidState raid, BlockPos pos) {
        if (raid.campPos == null || Math.abs((long) pos.getX()-raid.campPos.getX()) > 9
                || Math.abs((long) pos.getZ()-raid.campPos.getZ()) > 9
                || pos.getY() < raid.campPos.getY()-3 || pos.getY() > raid.campPos.getY()+12) return false;
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos).canBeReplaced() && level.getBlockEntity(pos) == null
                && level.getFluidState(pos).isEmpty();
    }
}
