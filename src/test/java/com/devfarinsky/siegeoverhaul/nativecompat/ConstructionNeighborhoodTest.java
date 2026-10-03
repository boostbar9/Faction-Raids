package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConstructionNeighborhoodTest extends MinecraftTestSupport {
    @Test void foreignBoundaryCactusIsProtectedWithoutReadingOrWritingUnloadedTerrain() {
        ServerLevel level = mock(ServerLevel.class);
        BlockPos planned = new BlockPos(15, 64, 0), foreignCactus = planned.east();
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(foreignCactus)).thenReturn(Blocks.CACTUS.defaultBlockState());
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, planned));
        verify(level, never()).setBlock(any(), any(), anyInt());
        verify(level, never()).destroyBlock(any(), anyBoolean(), any());
        clearInvocations(level);
        when(level.hasChunkAt(foreignCactus)).thenReturn(false);
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, planned));
        verify(level, never()).getBlockState(foreignCactus);
    }

    @Test void aSecondRingAcrossAChunkBoundaryIsCheckedBeforePowerOrBlockReads() {
        ServerLevel level = mock(ServerLevel.class);
        BlockPos target = new BlockPos(14, 64, 5);
        when(level.hasChunkAt(any())).thenAnswer(call -> ((BlockPos)call.getArgument(0)).getX() < 16);
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, target));
        verify(level, never()).hasNeighborSignal(any());
        verify(level, never()).getBlockState(any());
    }

    @Test void gravityFluidsInventoriesAndRedstoneAreNotStableNeighbors() {
        for (var block : java.util.List.of(Blocks.SAND, Blocks.GRAVEL, Blocks.ANVIL, Blocks.WATER,
                Blocks.LAVA, Blocks.CHEST, Blocks.OBSERVER, Blocks.REDSTONE_WIRE, Blocks.TNT,
                Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.CACTUS))
            assertFalse(NativeConstructionGuard.stableNeighbor(block.defaultBlockState()), block.toString());
        for (var block : java.util.List.of(Blocks.AIR, Blocks.COBBLESTONE, Blocks.STONE_BRICKS,
                Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.GRASS_BLOCK))
            assertTrue(NativeConstructionGuard.stableNeighbor(block.defaultBlockState()), block.toString());
    }

    @Test void poweredPlacementAndUnknownNeighborhoodAreBlockedBeforeMutation() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.hasNeighborSignal(BlockPos.ZERO)).thenReturn(true);
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, BlockPos.ZERO));
        verify(level).hasNeighborSignal(BlockPos.ZERO);
        clearInvocations(level);
        when(level.hasNeighborSignal(BlockPos.ZERO)).thenReturn(false);
        when(level.hasChunkAt(any())).thenReturn(false);
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, BlockPos.ZERO));
        verify(level, never()).getBlockState(any());
    }
}
