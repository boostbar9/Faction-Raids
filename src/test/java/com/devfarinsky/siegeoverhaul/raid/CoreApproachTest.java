package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreApproachTest extends MinecraftTestSupport {
    private final BlockPos core=new BlockPos(0,64,0);
    private ServerLevel level() {
        var level=mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        var border=mock(net.minecraft.world.level.border.WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(any())).thenAnswer(c->((BlockPos)c.getArgument(0)).getY()<64?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        when(level.noCollision(any(),any(AABB.class))).thenAnswer(c->((AABB)c.getArgument(1)).minY>=64);
        return level;
    }
    private Mob mob(PathNavigation nav) {
        var mob=mock(Mob.class);when(mob.position()).thenReturn(new Vec3(0.5,64,6.5));
        when(mob.getBoundingBox()).thenReturn(new AABB(.2,64,6.2,.8,65.95,6.8));
        when(mob.getNavigation()).thenReturn(nav);return mob;
    }

    @Test void navigationSelectsReachableFloorBesideCoreRatherThanTheSolidCoreCell() {
        var level=level();var nav=mock(PathNavigation.class);var mob=mob(nav);var path=mock(Path.class);
        Set<BlockPos> expected=Set.of(core.north(),core.south(),core.east(),core.west());
        assertEquals(expected,CoreApproach.targets(level,mob,core));
        when(nav.createPath(expected,0)).thenReturn(path);when(path.canReach()).thenReturn(true);
        when(nav.moveTo(path,1.2)).thenReturn(true);
        assertTrue(CoreApproach.moveTo(level,mob,Vec3.atCenterOf(core),1.2));
        verify(nav).moveTo(path,1.2);assertFalse(expected.contains(core));
    }

    @Test void unreachableOrDistantApproachFallsBackWithoutReplacingNavigation() {
        var level=level();var nav=mock(PathNavigation.class);var mob=mob(nav);var path=mock(Path.class);
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);when(path.canReach()).thenReturn(false);
        assertFalse(CoreApproach.moveTo(level,mob,Vec3.atCenterOf(core),1));
        verify(nav,never()).moveTo(any(Path.class),anyDouble());
        clearInvocations(nav);when(mob.distanceToSqr(any(Vec3.class))).thenReturn(1000.0);
        assertFalse(CoreApproach.moveTo(level,mob,Vec3.atCenterOf(core),1));
        verify(nav,never()).createPath(anySet(),anyInt());
    }

    @Test void candidatesRespectLoadedChunksHazardsAndActualUnitClearance() {
        var level=level();var mob=mob(mock(PathNavigation.class));
        when(level.hasChunkAt(core.north())).thenReturn(false);
        when(level.getBlockState(core.south().below())).thenReturn(Blocks.MAGMA_BLOCK.defaultBlockState());
        when(level.getFluidState(core.east())).thenReturn(net.minecraft.world.level.material.Fluids.WATER.defaultFluidState());
        assertEquals(Set.of(core.west()),CoreApproach.targets(level,mob,core));
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(false);
        assertTrue(CoreApproach.targets(level,mob,core).isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
}
