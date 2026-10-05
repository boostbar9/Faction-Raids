package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaptureBoundaryTest extends MinecraftTestSupport {
    private static final BlockPos CORE = new BlockPos(0, 64, 0);
    @Test void sampledBoundaryMatchesServerRadiusAndUsesAFixedWorkBudget() {
        for (int radius : new int[]{2, 6, 32}) {
            var samples = new AtomicInteger();
            var segments = CaptureBoundary.sample(CORE, radius, (x, z) -> { samples.incrementAndGet(); return 64; });
            assertEquals(CaptureBoundary.SEGMENTS, samples.get());
            assertEquals(CaptureBoundary.SEGMENTS, segments.size());
            for (var segment : segments) {
                assertEquals(radius, Math.hypot(segment.from().x - .5, segment.from().z - .5), 1e-9);
                assertEquals(64.045, segment.from().y, 1e-9);
            }
            assertEquals(16, segments.stream().filter(CaptureBoundary.Segment::marker).count());
        }
    }
    @Test void projectionNeverBridgesCliffsOrMissingSamples() {
        var segments = CaptureBoundary.sample(CORE, 6, (x, z) -> x > 2 ? Double.NaN : z > 0 ? 64 : 68);
        assertTrue(segments.size() < CaptureBoundary.SEGMENTS);
        for (var segment : segments) {
            assertTrue(segment.from().x <= 2 && segment.to().x <= 2);
            assertTrue(Math.abs(segment.from().y - segment.to().y) <= 1.05);
        }
        assertTrue(CaptureBoundary.sample(CORE, 33, (x, z) -> fail("Invalid radius must not sample")).isEmpty());
    }
    private LevelReader flatWorld() {
        var level = mock(LevelReader.class);
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getBlockState(any())).thenAnswer(c -> ((BlockPos)c.getArgument(0)).getY() == 63
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        return level;
    }
    @Test void terrainUsesCollisionSurfaceNotHeightmapOrUnloadedChunks() {
        var level = flatWorld();
        assertEquals(64, CaptureBoundary.surface(level, CORE, 2, 6.5, .5));
        when(level.getBlockState(new BlockPos(6, 63, 0))).thenReturn(Blocks.STONE_SLAB.defaultBlockState());
        assertEquals(63.5, CaptureBoundary.surface(level, CORE, 2, 6.5, .5));
        when(level.hasChunk(0, 0)).thenReturn(false);
        clearInvocations(level);
        assertTrue(Double.isNaN(CaptureBoundary.surface(level, CORE, 2, 6.5, .5)));
        verify(level, never()).getBlockState(any());
        verify(level, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
    }
    @Test void roofsOutsideVerticalToleranceDoNotReceiveAFalseGroundRing() {
        var level = flatWorld();
        when(level.getBlockState(new BlockPos(6, 66, 0))).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(64, CaptureBoundary.surface(level, CORE, 2, 6.5, .5),
                "A ceiling above the feet tolerance must not hide the eligible floor underneath");
        // A floor beneath the allowed cylinder is equally invalid.
        doReturn(Blocks.AIR.defaultBlockState()).when(level).getBlockState(any());
        assertTrue(Double.isNaN(CaptureBoundary.surface(level, CORE, 2, 6.5, .5)));
    }
    @Test void sightPreflightChecksIntermediateColumnsAndNeverLoadsThem() {
        var level = flatWorld();
        assertTrue(CaptureBoundary.loadedSight(level, CORE, new Vec3(32.5, 64, .5)));
        when(level.hasChunk(1, 0)).thenReturn(false);
        assertFalse(CaptureBoundary.loadedSight(level, CORE, new Vec3(32.5, 64, .5)));
        assertFalse(CaptureBoundary.loadedSight(level, CORE, new Vec3(192, 64, .5)));
        verify(level, never()).getBlockState(any());
        verify(level, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
    }
}
