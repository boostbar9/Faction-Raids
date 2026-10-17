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
        String problem = NativeConstructionGuard.neighborhoodProblem(level, planned);
        assertNotNull(problem);
        assertTrue(problem.contains("minecraft:cactus"));
        assertTrue(problem.contains(foreignCactus.toShortString()));
        assertEquals(problem, NativeConstructionGuard.placementNeighborhoodProblem(level, java.util.List.of(planned)));
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

    @Test void looseGroundIsStableOnlyWhileItRestsOnStableSturdySupport() {
        ServerLevel level = mock(ServerLevel.class);
        BlockPos planned = new BlockPos(5, 64, 0), sand = planned.east();
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> world = new java.util.HashMap<>();
        when(level.getBlockState(any())).thenAnswer(call -> world.getOrDefault(call.<BlockPos>getArgument(0),
                ((BlockPos) call.getArgument(0)).getY() < 64 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState()));
        world.put(sand, Blocks.SAND.defaultBlockState());
        world.put(sand.below(), Blocks.GRAVEL.defaultBlockState());
        assertNull(NativeConstructionGuard.neighborhoodProblem(level, planned), "sand on gravel on stone");
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, planned, sand.below()::equals),
                "support the builder clears first does not count");
        world.put(sand.below(), Blocks.AIR.defaultBlockState());
        String problem = NativeConstructionGuard.neighborhoodProblem(level, planned);
        assertNotNull(problem, "floating sand can fall into the site");
        assertTrue(problem.contains("minecraft:sand"));
        world.put(sand.below(), Blocks.CHEST.defaultBlockState());
        assertNotNull(NativeConstructionGuard.neighborhoodProblem(level, planned), "a container is not stable support");
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test void freshSnowIsClearedButOtherBlocksAreLeftForTheGuard() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        BlockPos snow = new BlockPos(1, 65, 1), stone = new BlockPos(2, 65, 1);
        when(level.getBlockState(snow)).thenReturn(Blocks.SNOW.defaultBlockState());
        when(level.getBlockState(stone)).thenReturn(Blocks.COBBLESTONE.defaultBlockState());
        assertEquals(1, NativeConstructionGuard.clearSnow(level, snow));
        verify(level).setBlock(eq(snow), eq(Blocks.AIR.defaultBlockState()), anyInt());
        assertEquals(0, NativeConstructionGuard.clearSnow(level, stone));
        verify(level, never()).setBlock(eq(stone), any(), anyInt());
        when(level.hasChunkAt(snow)).thenReturn(false);
        clearInvocations(level);
        assertEquals(0, NativeConstructionGuard.clearSnow(level, snow));
        verify(level, never()).getBlockState(snow);
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
