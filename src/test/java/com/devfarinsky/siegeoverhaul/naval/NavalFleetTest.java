package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NavalFleetTest extends MinecraftTestSupport {
    private static final BlockPos WATER = new BlockPos(0, 64, 0);

    @Test void diagonalObstructionAndDiagonalUnloadedChunkRejectSpawn() {
        ServerLevel level = water();
        assertTrue(NavalFleet.isClearWaterFootprint(level, WATER, 3));
        when(level.getFluidState(WATER.offset(2, 0, 2))).thenReturn(Fluids.EMPTY.defaultFluidState());
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
        when(level.getFluidState(WATER.offset(2, 0, 2))).thenReturn(Fluids.WATER.defaultFluidState());
        when(level.hasChunkAt(WATER.offset(-3, 0, 3))).thenReturn(false);
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
    }

    @Test void diagonalCeilingAndNearbyVesselRejectSpawn() {
        ServerLevel level = water();
        when(level.getBlockState(WATER.offset(2, 2, -2))).thenReturn(Blocks.STONE.defaultBlockState());
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
        when(level.getBlockState(WATER.offset(2, 2, -2))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getEntities(isNull(), any(AABB.class))).thenReturn(List.of(mock(Entity.class)));
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
    }

    @Test void waterloggedBlocksAndSubmergedColumnsAreNotOpenWater() {
        ServerLevel level = water();
        BlockPos diagonal = WATER.offset(2, 0, 2);
        when(level.getBlockState(diagonal)).thenReturn(Blocks.OAK_FENCE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
        when(level.getBlockState(diagonal)).thenReturn(Blocks.WATER.defaultBlockState());
        when(level.getBlockState(diagonal.above())).thenReturn(Blocks.WATER.defaultBlockState());
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
    }

    @Test void wholeFootprintMustRemainInsideBorderAndBuildHeight() {
        ServerLevel level = water();
        level.getWorldBorder().setCenter(.5, .5);
        level.getWorldBorder().setSize(5);
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
        level.getWorldBorder().setSize(100);
        when(level.getMaxBuildHeight()).thenReturn(66);
        assertFalse(NavalFleet.isClearWaterFootprint(level, WATER, 3));
    }

    @Test void rejectedNativeBoardingNeverRetriesWithForce() {
        Entity vessel = mock(Entity.class);
        Mob rejected = mock(Mob.class), accepted = mock(Mob.class);
        when(rejected.isAlive()).thenReturn(true);
        when(accepted.isAlive()).thenReturn(true);
        when(accepted.startRiding(vessel, false)).thenReturn(true);
        assertEquals(1, NavalFleet.mountCrew(vessel, List.of(rejected, accepted), 6));
        verify(rejected).startRiding(vessel, false);
        verify(rejected, never()).startRiding(vessel, true);
        verify(accepted, never()).startRiding(vessel, true);
    }

    @Test void existingPassengerIsNotTakenFromAnotherVehicle() {
        Entity vessel = mock(Entity.class);
        Mob mob = mock(Mob.class);
        when(mob.isAlive()).thenReturn(true);
        when(mob.isPassenger()).thenReturn(true);
        assertFalse(NavalFleet.board(vessel, mob));
        verify(mob, never()).startRiding(any(), anyBoolean());
    }

    private static ServerLevel water() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getFluidState(any())).thenReturn(Fluids.WATER.defaultFluidState());
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        return level;
    }
}
