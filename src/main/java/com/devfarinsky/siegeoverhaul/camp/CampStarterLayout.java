package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Shared local coordinates keep the sanctuary and gate avenue clear in every orientation. */
public final class CampStarterLayout {
    private CampStarterLayout() {}

    public static BlockPos campfire(BlockPos camp, Direction front) { return at(camp,front,-6,1); }
    public static BlockPos supplies(BlockPos camp, Direction front) { return at(camp,front,-6,3); }
    public static BlockPos banner(BlockPos camp, Direction front) { return at(camp,front,-6,5); }
    public static BlockPos forge(BlockPos camp, Direction front) { return at(camp,front,6,4); }
    public static BlockPos pavilion(BlockPos camp, Direction front, int side) { return at(camp,front,side*6,-6); }

    public static boolean gateCell(BlockPos camp, BlockPos cell, Direction front) {
        int dx=cell.getX()-camp.getX(),dz=cell.getZ()-camp.getZ();
        return dx*front.getStepX()+dz*front.getStepZ()==9
                && Math.abs(dx*front.getClockWise().getStepX()+dz*front.getClockWise().getStepZ())<=2;
    }
    private static BlockPos at(BlockPos camp, Direction front, int side, int depth) {
        if (front.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal camp facing required");
        return camp.relative(front,depth).relative(front.getClockWise(),side);
    }
}
