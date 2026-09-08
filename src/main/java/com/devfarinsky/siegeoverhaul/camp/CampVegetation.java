package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.*;

/** Clear only small vegetation in camp jobs; capture paired plants before neighbor updates. */
public final class CampVegetation {
    private CampVegetation() {}
    public static boolean plant(BlockState state) {
        return state.getBlock() instanceof FlowerBlock || state.getBlock() instanceof TallFlowerBlock
                || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH);
    }
    public static boolean replaceable(BlockState state) { return state.canBeReplaced() || plant(state); }
    public static boolean clear(ServerLevel level,RaidSavedData.RaidState raid,BlockPos pos) {
        BlockState state=level.getBlockState(pos);if(!plant(state))return state.isAir();
        List<BlockPos> cells=new ArrayList<>();cells.add(pos);
        if(state.getBlock() instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)) {
            BlockPos pair=state.getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below();
            if(!level.hasChunkAt(pair))return false;
            if(level.getBlockState(pair).is(state.getBlock()))cells.add(pair);
        }
        for(BlockPos cell:cells) {
            if(!level.hasChunkAt(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || level.getBlockEntity(cell)!=null || !level.getFluidState(cell).isEmpty())return false;
        }
        for(BlockPos cell:cells)raid.recordCampBlock(cell.asLong(),raid.pendingCampBlocks.getOrDefault(cell.asLong(),"minecraft:air"),BlockRestoration.serialize(level,cell));
        RaidSavedData.get(level.getServer()).setDirty();
        cells.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        for(BlockPos cell:cells) {
            level.levelEvent(2001,cell,Block.getId(level.getBlockState(cell)));
            level.setBlock(cell,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return level.getBlockState(pos).isAir();
    }
}
