package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerritoryFortificationTest extends MinecraftTestSupport {
    private final BlockPos base = new BlockPos(8, 70, 12);

    @Test
    void onlyPerimeterWithinStorageReachIsQueued() {
        BlockPos storage = new BlockPos(0, 64, 0);
        BlockPos near = new BlockPos(40, 90, 40);
        BlockPos edge = new BlockPos(TerritoryFortification.STORAGE_SEARCH_RADIUS, 64, 0);
        BlockPos far = new BlockPos(TerritoryFortification.STORAGE_SEARCH_RADIUS + 1, 64, 0);
        assertEquals(java.util.List.of(near, edge),
                TerritoryFortification.withinStorageRange(java.util.List.of(near, edge, far), storage));
        assertEquals(java.util.List.of(),
                TerritoryFortification.withinStorageRange(java.util.List.of(far), storage));
    }

    @Test
    void foundationFillsContiguousAirUntilSturdyGround() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() <= base.getY() - 4
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        });

        assertEquals(3, TerritoryFortification.foundationDepth(level, base));
    }

    @Test
    void nonReplaceableThinBlockStopsDisconnectedFoundation() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (pos.equals(base.below(2))) return Blocks.TORCH.defaultBlockState();
            if (pos.getY() <= base.getY() - 4) return Blocks.STONE.defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        });

        assertEquals(1, TerritoryFortification.foundationDepth(level, base));
    }

    @Test
    void unloadedChunkBoundsTheFoundationScan() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenAnswer(call -> !base.below(3).equals(call.getArgument(0)));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());

        assertEquals(2, TerritoryFortification.foundationDepth(level, base));
    }

    @Test
    void configuredWallHeightFollowsEachTerrainAdjustedBase() {
        assertEquals(72, TerritoryFortification.columnTopY(base, false));
        assertEquals(74, TerritoryFortification.columnTopY(base, true));

        BlockPos uphill = base.above(4);
        assertEquals(76, TerritoryFortification.columnTopY(uphill, false));
        assertEquals(78, TerritoryFortification.columnTopY(uphill, true));
    }
}
