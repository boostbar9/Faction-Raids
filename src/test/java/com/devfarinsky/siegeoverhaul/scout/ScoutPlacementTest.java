package com.devfarinsky.siegeoverhaul.scout;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntBiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScoutPlacementTest extends MinecraftTestSupport {
    final ServerLevel level = mock(ServerLevel.class);
    final WorldBorder border = new WorldBorder();
    final Map<BlockPos, BlockState> edits = new HashMap<>();
    ToIntBiFunction<Integer, Integer> heights = (x, z) -> 64;

    @BeforeEach void terrain() {
        border.setSize(2000);
        when(level.getWorldBorder()).thenReturn(border);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getHeight(any(), anyInt(), anyInt()))
                .thenAnswer(call -> heights.applyAsInt(call.getArgument(1), call.getArgument(2)));
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            BlockState edit = edits.get(pos);
            if (edit != null) return edit;
            return pos.getY() < heights.applyAsInt(pos.getX(), pos.getZ())
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
    }

    @Test void partyMembersResampleTheirOwnSurfaceHeight() {
        heights = (x, z) -> x > 0 ? 65 : 64;
        var party = ScoutPlacement.partyPositions(level, new BlockPos(0, 64, 0), 5);
        assertEquals(5, party.size());
        assertTrue(party.contains(new BlockPos(1, 65, 0)));
        assertTrue(party.stream().allMatch(pos -> pos.getY() == heights.applyAsInt(pos.getX(), pos.getZ())));
    }

    @Test void waterTreesAndIsolatedPillarsAreRejected() {
        edits.put(new BlockPos(5, 63, 0), Blocks.WATER.defaultBlockState());
        assertNull(ScoutPlacement.surface(level, 5, 0));
        edits.put(new BlockPos(5, 63, 0), Blocks.OAK_LOG.defaultBlockState());
        assertNull(ScoutPlacement.surface(level, 5, 0));
        edits.clear();
        heights = (x, z) -> x == 20 && z == 0 ? 70 : 64;
        BlockPos fallback = ScoutPlacement.findSpawn(level, new BlockPos(0, 64, 0), 20, 0.0);
        assertNotNull(fallback);
        assertNotEquals(new BlockPos(20, 70, 0), fallback);
    }

    @Test void unloadedPreferredColumnFallsBackWithoutReadingItsTerrain() {
        when(level.hasChunkAt(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getX() != 100);
        BlockPos spawn = ScoutPlacement.findSpawn(level, new BlockPos(0, 64, 0), 100, 0.0);
        assertNotNull(spawn);
        assertNotEquals(100, spawn.getX());
        verify(level, never()).getHeight(any(), eq(100), anyInt());
    }

    @Test void lookoutStaysOnIncomingSideInsteadOfChoosingAnUnrelatedHighPoint() {
        heights = (x, z) -> z >= 40 ? 90 : 64;
        BlockPos lookout = ScoutPlacement.findLookout(level, new BlockPos(0, 64, 0),
                new BlockPos(100, 64, 0), 40);
        assertEquals(new BlockPos(40, 64, 0), lookout);
    }

    @Test void entireScoutBodyMustFitInsideWorldBorder() {
        border.setCenter(0.5, 0.5);
        border.setSize(0.5);
        assertTrue(border.isWithinBounds(new BlockPos(0, 64, 0)));
        assertNull(ScoutPlacement.surface(level, 0, 0));
    }
}
