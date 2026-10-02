package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded fallback survey. A bare timber pillar is never treated as a tree. */
final class CampGround {
    record Column(int ground, int top, boolean tree) {}
    static boolean leaves(BlockState s) {
        return s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)
                && !s.getValue(LeavesBlock.WATERLOGGED) && s.getFluidState().isEmpty();
    }
    static boolean trunk(BlockState s) {
        boolean log=s.is(Blocks.OAK_LOG)||s.is(Blocks.SPRUCE_LOG)||s.is(Blocks.BIRCH_LOG)
                ||s.is(Blocks.JUNGLE_LOG)||s.is(Blocks.ACACIA_LOG)||s.is(Blocks.DARK_OAK_LOG)
                ||s.is(Blocks.MANGROVE_LOG)||s.is(Blocks.CHERRY_LOG);
        return log && s.getValue(RotatedPillarBlock.AXIS)==Direction.Axis.Y;
    }
    static Column survey(ServerLevel level, BlockPos column, boolean fallback) {
        int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
        if(!fallback)return new Column(top,top,false);
        int ground=top;
        while(ground>level.getMinBuildHeight() && top-ground<32
                && trunk(level.getBlockState(new BlockPos(column.getX(),ground-1,column.getZ()))))ground--;
        if(top-ground<2 || top-ground>=32
                ||!CampRoad.soil(level.getBlockState(new BlockPos(column.getX(),ground-1,column.getZ()))))
            return new Column(top,top,false);
        // Require an ordinary non-persistent canopy near the top of the supported trunk.
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=2;y++) {
            BlockPos p=new BlockPos(column.getX()+x,top+y,column.getZ()+z);
            if(level.hasChunkAt(p)&&level.getWorldBorder().isWithinBounds(p)&&leaves(level.getBlockState(p)))
                return new Column(ground,top,true);
        }
        return new Column(top,top,false);
    }
    static boolean water(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
        // Only water and natural aquatic vegetation; never lava or waterlogged player blocks.
    }
    private CampGround() {}
}
