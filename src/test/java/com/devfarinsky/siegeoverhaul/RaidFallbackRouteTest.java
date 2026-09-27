package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.raid.FlankRoutes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaidFallbackRouteTest extends MinecraftTestSupport {
    private final ServerLevel level=mock(ServerLevel.class);
    private final PathfinderMob mob=mock(PathfinderMob.class);
    private final PathNavigation nav=mock(PathNavigation.class);
    private final Vec3 objective=new Vec3(100,64,0), narrow=new Vec3(8.5,64,0.5), wide=new Vec3(4.5,64,4.5);
    private void setup() {
        when(mob.level()).thenReturn(level);when(mob.getNavigation()).thenReturn(nav);
        when(mob.position()).thenReturn(new Vec3(0.5,64,0.5));
        when(mob.getBoundingBox()).thenReturn(new AABB(0.2,64,0.2,0.8,65.8,0.8));
        when(level.hasChunkAt(any())).thenReturn(true);when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        doAnswer(i -> ((BlockPos)i.getArgument(0)).getY()<64 ? Blocks.STONE.defaultBlockState()
                : Blocks.AIR.defaultBlockState()).when(level).getBlockState(any());
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(true);
    }
    @Test void unreachableNarrowCandidateDoesNotHideReachableWideRoute() {
        setup();var blocked=mock(Path.class);var reachable=mock(Path.class);when(reachable.canReach()).thenReturn(true);
        when(nav.createPath(BlockPos.containing(narrow),0)).thenReturn(blocked);
        when(nav.createPath(BlockPos.containing(wide),0)).thenReturn(reachable);
        try(var random=mockStatic(DefaultRandomPos.class)) {
            random.when(()->DefaultRandomPos.getPosTowards(eq(mob),anyInt(),anyInt(),eq(objective),anyDouble()))
                    .thenReturn(narrow,wide);
            assertEquals(wide,RaidEvents.coneFallbackTarget(mob,objective));
            verify(nav,times(2)).createPath(any(BlockPos.class),eq(0));
            verify(nav,never()).moveTo(any(Path.class),anyDouble());
        }
    }
    @Test void unsafeCandidatesDoNotTriggerPathSearchOrGetCached() {
        setup();when(level.hasChunkAt(any())).thenReturn(false);
        try(var random=mockStatic(DefaultRandomPos.class)) {
            random.when(()->DefaultRandomPos.getPosTowards(eq(mob),anyInt(),anyInt(),eq(objective),anyDouble()))
                    .thenReturn(narrow,wide);
            assertNull(RaidEvents.coneFallbackTarget(mob,objective));verifyNoInteractions(nav);
        }
    }
    @Test void cachedDestinationIsDiscardedWhenTerrainChangesWithoutIgnoringSearchCooldown() {
        setup();var stuck=new RaidEvents.StuckEntry(1000,100);stuck.escalationLevel=1;
        stuck.cachedFallbackTarget=narrow;stuck.cachedFallbackObjective=objective;
        stuck.fallbackCacheUntilGameTime=200;stuck.nextFallbackSearchGameTime=150;
        var telemetry=new RaidEvents.PathingTelemetry();when(mob.distanceToSqr(narrow)).thenReturn(64.0);
        assertEquals(narrow,RaidEvents.fallbackRouteTarget(level,mob,objective,stuck,100,telemetry));
        when(level.getFluidState(BlockPos.containing(narrow))).thenReturn(Fluids.WATER.defaultFluidState());
        assertNull(RaidEvents.fallbackRouteTarget(level,mob,objective,stuck,101,telemetry));
        assertNull(stuck.cachedFallbackTarget);assertEquals(1,telemetry.fallbackCacheHits);
        verifyNoInteractions(nav);
    }
    @Test void actualBodyMustFitInsideLoadedDryBorderedSpace() {
        setup();assertTrue(FlankRoutes.safeDestination(level,mob,narrow));
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(false);
        assertFalse(FlankRoutes.safeDestination(level,mob,narrow));
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(true);
        level.getWorldBorder().setSize(2);assertFalse(FlankRoutes.safeDestination(level,mob,narrow));
    }
}
