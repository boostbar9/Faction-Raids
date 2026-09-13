package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.siege.SiegeIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiegeYardTest extends MinecraftTestSupport {
    @Test
    void purchaseGuidanceUsesSelectedVehicleFootprint() {
        String guidance = SiegeYard.deploymentAreaGuidance(new SiegeIntegration.Footprint(2, 5));
        assertEquals(5, SiegeYard.deploymentDiameter(new SiegeIntegration.Footprint(2, 5)));
        assertTrue(guidance.contains("5x5"));
        assertTrue(guidance.contains("5 blocks of headroom"));
    }

    @Test
    void clearanceChecksTopLayerIntersectedByCenteredSpawn() {
        BlockPos center = new BlockPos(0, 64, 0);
        ServerLevel level = clearDeploymentLevel(center);
        BlockPos obstruction = center.above(4);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (pos.equals(obstruction)) return Blocks.STONE.defaultBlockState();
            return pos.getY() < center.getY()
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        });

        String issue = SiegeYard.describeClearance(level, center, 2, 5);
        assertTrue(issue.contains("0, 68, 0"));
    }

    @Test
    void validVehicleSizedVolumePassesClearance() {
        BlockPos center = new BlockPos(0, 64, 0);
        ServerLevel level = clearDeploymentLevel(center);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() < center.getY()
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        });

        assertNull(SiegeYard.describeClearance(level, center, 2, 5));
    }

    private static ServerLevel clearDeploymentLevel(BlockPos center) {
        ServerLevel level = mock(ServerLevel.class);
        WorldBorder border = mock(WorldBorder.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        return level;
    }
}
