package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SiteClearanceTest extends MinecraftTestSupport {
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final BlockGetter level = mock(BlockGetter.class);
    {
        when(level.getBlockState(any())).thenAnswer(call -> blocks.getOrDefault(call.<BlockPos>getArgument(0), Blocks.AIR.defaultBlockState()));
    }

    @Test void naturalCoverIncludingPairedPlantsIsClearedVegetation() {
        for (var block : List.of(Blocks.GRASS, Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.FERN, Blocks.SUNFLOWER,
                Blocks.POPPY, Blocks.SNOW, Blocks.VINE, Blocks.OAK_SAPLING, Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH,
                Blocks.BROWN_MUSHROOM, Blocks.SUGAR_CANE, Blocks.MOSS_CARPET))
            assertTrue(SiteClearance.vegetation(level, BlockPos.ZERO, block.defaultBlockState()), block.toString());
    }

    @Test void playerBlocksContainersFluidsAndCropsStayProtected() {
        for (var block : List.of(Blocks.CHEST, Blocks.WATER, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.WHEAT,
                Blocks.TORCH, Blocks.WHITE_WOOL, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_LOG, Blocks.GLASS)) {
            assertFalse(SiteClearance.vegetation(level, BlockPos.ZERO, block.defaultBlockState()), block.toString());
            assertFalse(SiteClearance.terrain(block.defaultBlockState()), block.toString());
        }
        assertFalse(SiteClearance.vegetation(level, BlockPos.ZERO,
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)), "placed leaves");
        assertFalse(SiteClearance.vegetation(level, BlockPos.ZERO,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true)), "waterlogged");
    }

    @Test void logsAreClearedOnlyWhenTheTrunkCarriesNaturalLeaves() {
        BlockPos base = new BlockPos(0, 64, 0);
        for (int y = 0; y < 5; y++) blocks.put(base.above(y), Blocks.OAK_LOG.defaultBlockState());
        assertFalse(SiteClearance.vegetation(level, base, Blocks.OAK_LOG.defaultBlockState()), "bare log pillar");
        blocks.put(base.above(5).east(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        assertFalse(SiteClearance.vegetation(level, base, Blocks.OAK_LOG.defaultBlockState()), "decorated pillar");
        blocks.put(base.above(5), Blocks.OAK_LEAVES.defaultBlockState());
        assertTrue(SiteClearance.vegetation(level, base, Blocks.OAK_LOG.defaultBlockState()), "tree trunk");
    }

    @Test void reviewTreatsClearableCellsAsEmptyButKeepsMatchingTargetsAndProtectedBlocks() {
        BlockState air = Blocks.AIR.defaultBlockState();
        assertEquals(air, SiteClearance.reviewed(level, BlockPos.ZERO, Blocks.LARGE_FERN.defaultBlockState(), null));
        assertEquals(air, SiteClearance.reviewed(level, BlockPos.ZERO, Blocks.GRASS_BLOCK.defaultBlockState(), "minecraft:cobblestone"));
        assertEquals(Blocks.DIRT.defaultBlockState(), SiteClearance.reviewed(level, BlockPos.ZERO, Blocks.DIRT.defaultBlockState(), "minecraft:dirt"));
        assertEquals(Blocks.CHEST.defaultBlockState(), SiteClearance.reviewed(level, BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), null));
        assertEquals(Blocks.SAND.defaultBlockState(), SiteClearance.reviewed(level, BlockPos.ZERO, Blocks.SAND.defaultBlockState(), null));
    }

    @Test void neighbourReviewIgnoresVegetationEverywhereButTerrainOnlyInsideTheReservation() {
        BlockPos fern = new BlockPos(1, 64, 0), dirt = new BlockPos(2, 64, 0);
        blocks.put(fern, Blocks.LARGE_FERN.defaultBlockState());
        blocks.put(dirt, Blocks.DIRT.defaultBlockState());
        var outside = SiteClearance.clearedNeighbours(level, Set.of());
        assertTrue(outside.test(fern));
        assertFalse(outside.test(dirt));
        assertTrue(SiteClearance.clearedNeighbours(level, Set.of(dirt)).test(dirt));
    }
}
