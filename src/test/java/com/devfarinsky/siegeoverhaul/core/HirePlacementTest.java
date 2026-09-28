package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HirePlacementTest extends MinecraftTestSupport {
    final ServerLevel level = mock(ServerLevel.class);
    final Mob recruit = mock(Mob.class);
    final BlockPos feet = new BlockPos(0, 64, 0);
    final WorldBorder border = new WorldBorder();

    @BeforeEach void terrain() {
        border.setSize(1000);
        when(level.getWorldBorder()).thenReturn(border);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(recruit.position()).thenReturn(Vec3.ZERO);
        when(recruit.getBoundingBox()).thenReturn(new AABB(-.3,0,-.3,.3,1.95,.3));
        when(level.noCollision(eq(recruit),any(AABB.class))).thenReturn(true);
        when(level.getEntities(eq(recruit),any(AABB.class))).thenReturn(List.of());
        when(level.getBlockState(any())).thenAnswer(i -> ((BlockPos)i.getArgument(0)).getY() < 64
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    @Test void acceptsSafeGroundWithoutMovingUnitOrChangingWorld() {
        assertTrue(HirePlacement.safe(level,recruit,feet,p -> true));
        verify(recruit,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void hazardousSupportAndNonCollidingBodyHazardsAreRejected() {
        for (Block hazard : new Block[]{Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
                Blocks.CACTUS, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.SWEET_BERRY_BUSH,
                Blocks.WITHER_ROSE, Blocks.POWDER_SNOW, Blocks.WATER, Blocks.LAVA,
                Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY}) {
            when(level.getBlockState(feet.below())).thenReturn(hazard.defaultBlockState());
            assertFalse(HirePlacement.safe(level,recruit,feet,p -> true), "Floor: " + hazard);
            when(level.getBlockState(feet.below())).thenReturn(Blocks.STONE.defaultBlockState());
            when(level.getBlockState(feet)).thenReturn(hazard.defaultBlockState());
            assertFalse(HirePlacement.safe(level,recruit,feet,p -> true), "Body: " + hazard);
            when(level.getBlockState(feet)).thenReturn(Blocks.AIR.defaultBlockState());
        }
    }

    @Test void fullBodyMustBeInsideBorderEvenWhenItsFeetAreInside() {
        border.setCenter(.5,.5); border.setSize(.5);
        assertTrue(border.isWithinBounds(feet));
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
    }

    @Test void entireBodyAndSupportMustFitBuildHeight() {
        when(level.getMinBuildHeight()).thenReturn(64);
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(65);
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
        when(recruit.getBoundingBox()).thenReturn(new AABB(-.3,0,-.3,.3,3.1,.3));
        when(level.getMaxBuildHeight()).thenReturn(67);
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
    }

    @Test void wideUnitCannotCrossUnloadedChunkOrClaimBoundary() {
        when(recruit.getBoundingBox()).thenReturn(new AABB(-.8,0,-.8,.8,1.95,.8));
        when(level.hasChunkAt(any())).thenAnswer(i -> ((BlockPos)i.getArgument(0)).getX() >= 0);
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
        verify(level,never()).getBlockState(any());
        doReturn(true).when(level).hasChunkAt(any());
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> p.getX() >= 0));
        assertTrue(HirePlacement.safe(level,recruit,feet,p -> true));
    }

    @Test void missingFootingBlocksAndEntitiesPreventPlacement() {
        when(level.getBlockState(feet.below())).thenReturn(Blocks.AIR.defaultBlockState());
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
        when(level.getBlockState(feet.below())).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.noCollision(eq(recruit),any(AABB.class))).thenReturn(false);
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
        when(level.noCollision(eq(recruit),any(AABB.class))).thenReturn(true);
        when(level.getEntities(eq(recruit),any(AABB.class))).thenReturn(List.of(mock(Mob.class)));
        assertFalse(HirePlacement.safe(level,recruit,feet,p -> true));
    }
}
