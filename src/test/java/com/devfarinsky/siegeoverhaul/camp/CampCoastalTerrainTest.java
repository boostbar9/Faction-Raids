package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Combined coastal fixtures, not a generated-world or companion-mod playtest. */
class CampCoastalTerrainTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final Map<BlockPos, BlockState> edits = new HashMap<>();
    private BlockPos scout;
    private Direction ocean;
    private ChunkPos loadedCenter;

    private BlockPos coast(BlockPos searchCenter, Direction oceanSide) {
        scout = searchCenter;
        ocean = oceanSide;
        loadedCenter = new ChunkPos(scout);
        edits.clear();
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        doAnswer(call -> loaded(call.getArgument(0))).when(level).hasChunkAt(any());
        doAnswer(call -> {
            BlockPos column = new BlockPos(call.getArgument(1), 64, call.getArgument(2));
            assertTrue(loaded(column), "Height query outside the ready 3x3 chunks: " + column);
            return 64;
        }).when(level).getHeight(any(Heightmap.Types.class), anyInt(), anyInt());
        doAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            assertTrue(loaded(pos), "Block query outside the ready 3x3 chunks: " + pos);
            return state(pos);
        }).when(level).getBlockState(any());
        doAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            assertTrue(loaded(pos), "Mutation outside the ready 3x3 chunks: " + pos);
            edits.put(pos.immutable(), call.getArgument(1));
            return true;
        }).when(level).setBlock(any(), any(), anyInt());
        // Use the real local-search grid, choosing its inland-most centerline site.
        BlockPos inland = scout.relative(ocean.getOpposite(), 7);
        for (int attempt = 0; attempt < 25; attempt++) {
            BlockPos site = CampLoading.localCandidate(scout, attempt, true);
            if (site.equals(inland)) return site;
        }
        throw new AssertionError("Expanded search missed inland centerline site " + inland);
    }

    private boolean loaded(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return Math.abs(chunk.x - loadedCenter.x) <= 1 && Math.abs(chunk.z - loadedCenter.z) <= 1;
    }

    private boolean wet(BlockPos pos) {
        int outward = (pos.getX() - scout.getX()) * ocean.getStepX()
                + (pos.getZ() - scout.getZ()) * ocean.getStepZ();
        return outward > 5;
    }

    private BlockState state(BlockPos pos) {
        if (edits.containsKey(pos)) return edits.get(pos);
        if (pos.getY() >= 64) return Blocks.AIR.defaultBlockState();
        if (wet(pos)) return (pos.getY() >= 61 ? Blocks.WATER : Blocks.STONE).defaultBlockState();
        return (Math.floorMod(pos.getX() + pos.getZ(), 2) == 0 ? Blocks.SAND : Blocks.STONE)
                .defaultBlockState();
    }

    @AfterEach void releaseRecordedTerrainQueries() {
        clearInvocations(level);
    }

    @Test void mixedCoastsFindLoadedDryExitsInEveryDirectionAndRetainWaterSnapshots() {
        for (BlockPos search : List.of(new BlockPos(8, 64, 8), new BlockPos(-24, 64, -40))) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos site = coast(search, side);
                assertEquals(site, CampTerrain.earthworksCenter(level, site, true));
                assertTrue(CampTerraforming.acceptableForTerraforming(level, site, site, 12));
                RaidState raid = new RaidState("team:coast", "home", 0);
                var reasons = new ArrayList<CampTerrain.Rejection>();
                var plan = CampTerrain.plan(level, site, pos -> false, reasons::add, true, side)
                        .orElseThrow(() -> new AssertionError(search + " / " + side + ": " + reasons));

                assertTrue(reasons.isEmpty());
                assertEquals(side.getClockWise(), plan.entrance(), "Water-facing exit must rotate onto land");
                assertEquals(3 * 31 * 3, plan.changes().size(), "Only the three-block-wide shallow-water strip needs filling");
                assertEquals(plan.changes().size(), plan.changes().stream().map(CampTerrain.Change::pos).distinct().count());
                assertTrue(plan.changes().stream().allMatch(change -> wet(change.pos())
                        && change.before().is(Blocks.WATER) && change.after().is(Blocks.DIRT)));
                assertTrue(plan.changes().stream().allMatch(change ->
                        Math.abs(change.pos().getX() - site.getX()) <= 15
                                && Math.abs(change.pos().getZ() - site.getZ()) <= 15),
                        "Rejected ocean-exit work must not leak beyond the accepted camp footprint");
                verify(level, never()).setBlock(any(), any(), anyInt());
                assertFalse(raid.campaign.contains("CampEntranceFacing"));

                assertTrue(CampTerrain.apply(level, raid, plan));
                RaidState saved = RaidState.load(raid.save());
                assertEquals(plan.entrance(), CampPerimeter.mainGateSide(saved));
                assertEquals(plan.changes().size(), saved.campBlocks.size());
                for (var change : plan.changes()) {
                    assertTrue(state(change.pos()).is(Blocks.DIRT));
                    assertEquals("minecraft:water", saved.campBlocks.get(change.pos().asLong())
                            .getCompound("Original").getString("Name"));
                    BlockPos support = change.pos().atY(60);
                    assertTrue(state(support).is(Blocks.STONE));
                    assertFalse(saved.campBlocks.containsKey(support.asLong()));
                }
                clearInvocations(level);
            }
        }
    }

    @Test void coastalFallbackStillRejectsAProtectedWaterColumnWithoutMutation() {
        BlockPos site = coast(new BlockPos(-24, 64, -40), Direction.WEST);
        BlockPos protectedColumn = site.relative(ocean, 15);
        assertTrue(wet(protectedColumn));
        var reasons = new ArrayList<CampTerrain.Rejection>();
        assertTrue(CampTerrain.plan(level, site, pos -> pos.getX() == protectedColumn.getX()
                && pos.getZ() == protectedColumn.getZ(), reasons::add, true, ocean).isEmpty());
        assertEquals(List.of(CampTerrain.Rejection.CLAIM), reasons);
        verify(level, never()).setBlock(any(), any(), anyInt());
        assertTrue(edits.isEmpty());
    }

    @Test void playerBlockAddedToPlannedCoastalFillPreventsAllEarthworksAndEntrancePersistence() {
        BlockPos site = coast(new BlockPos(8, 64, 8), Direction.SOUTH);
        var plan = CampTerrain.plan(level, site, pos -> false, reason -> {}, true, ocean).orElseThrow();
        BlockPos changed = plan.changes().get(plan.changes().size() - 1).pos();
        edits.put(changed, Blocks.CHEST.defaultBlockState());
        RaidState raid = new RaidState("team:coast", "home", 0);

        assertFalse(CampTerrain.apply(level, raid, plan));
        verify(level, never()).setBlock(any(), any(), anyInt());
        assertTrue(state(changed).is(Blocks.CHEST));
        assertTrue(raid.campBlocks.isEmpty());
        assertFalse(raid.campaign.contains("CampEntranceFacing"));
    }
}
