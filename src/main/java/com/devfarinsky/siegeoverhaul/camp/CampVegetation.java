package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.*;

/** Clear only small vegetation in camp jobs; capture paired plants before neighbor updates. */
public final class CampVegetation {
    private static final ResourceLocation VERY_SHORT_GRASS = new ResourceLocation("sizeable_foliage", "very_short_grass");
    private CampVegetation() {}
    public static boolean plant(BlockState state) {
        return state.getBlock() instanceof FlowerBlock || state.getBlock() instanceof TallFlowerBlock
                || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)
                || reviewedOptionalPlant(state, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** Shared admission for work that only authorizes changing one snapshotted cell. */
    public static boolean singleCellPlant(BlockState state) {
        if (state == null || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.getBlock() instanceof DoublePlantBlock || state.is(Blocks.WITHER_ROSE)) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && ("minecraft".equals(id.getNamespace()) ? plant(state) : reviewedOptionalPlant(state, id));
    }

    /**
     * Reviewed optional compatibility, not a general modded/replaceable-block exemption.
     * Sizeable Foliage's very_short_grass is a one-cell BushBlock copying vanilla grass.
     * Its other grasses/bushes can remove partners from onRemove and are not admitted.
     * See docs/sizeable-foliage-compatibility.md for the upstream source and binary audit.
     */
    static boolean reviewedOptionalPlant(BlockState state, ResourceLocation id) {
        return reviewedOptionalType(id, state.getBlock().getClass().getName()) && singleCellGrassShape(state);
    }

    static boolean reviewedOptionalType(ResourceLocation id, String className) {
        return VERY_SHORT_GRASS.equals(id)
                && "com.craisinlord.sizeablefoliage.content.block.VeryShortGrassBlock".equals(className);
    }

    static boolean singleCellGrassShape(BlockState state) {
        return state.getBlock() instanceof BushBlock
                && !(state.getBlock() instanceof CropBlock) && !(state.getBlock() instanceof DoublePlantBlock)
                && !(state.getBlock() instanceof FlowerBlock)
                && state.getValues().isEmpty() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && state.canBeReplaced() && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0.0F
                && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
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
