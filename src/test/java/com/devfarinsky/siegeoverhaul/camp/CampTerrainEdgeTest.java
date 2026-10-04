package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real planner fixtures; these do not claim generated-world or companion-mod playtesting. */
class CampTerrainEdgeTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final BlockPos center = new BlockPos(0, 64, 0);
    private final Map<BlockPos, Integer> heights = new HashMap<>();
    private final Set<BlockPos> rock = new HashSet<>();
    private final Map<BlockPos, BlockState> edits = new HashMap<>();

    @BeforeEach void terrain() {
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(Heightmap.Types.class), anyInt(), anyInt()))
                .thenAnswer(call -> height(call.getArgument(1), call.getArgument(2)));
        when(level.getBlockState(any())).thenAnswer(call -> state(call.getArgument(0)));
        when(level.setBlock(any(), any(), anyInt())).thenAnswer(call -> {
            edits.put(((BlockPos) call.getArgument(0)).immutable(), call.getArgument(1));
            return true;
        });
    }

    @AfterEach void releaseReads() { clearInvocations(level); }

    private int height(int x, int z) { return heights.getOrDefault(new BlockPos(x, 0, z), 64); }

    private void terrainHeight(int x, int z, int y, boolean naturalRock) {
        BlockPos column = new BlockPos(x, 0, z);
        heights.put(column, y);
        if (naturalRock) rock.add(column); else rock.remove(column);
    }

    private BlockState state(BlockPos pos) {
        if (edits.containsKey(pos)) return edits.get(pos);
        int y = height(pos.getX(), pos.getZ());
        if (pos.getY() >= y) return Blocks.AIR.defaultBlockState();
        if (rock.contains(new BlockPos(pos.getX(), 0, pos.getZ()))) return Blocks.STONE.defaultBlockState();
        return (pos.getY() == y - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState();
    }

    private void remoteRockSpike() {
        terrainHeight(15, 0, 68, true);
        terrainHeight(16, 0, 68, true);
    }

    private CampTerrain.Plan fallback(Direction entrance) {
        var reasons = new ArrayList<CampTerrain.Rejection>();
        var plan = CampTerrain.plan(level, center, pos -> false, reasons::add, true, entrance)
                .orElseThrow(() -> new AssertionError("Unexpected rejection: " + reasons));
        assertTrue(reasons.isEmpty());
        return plan;
    }

    private void rejected(Predicate<BlockPos> excluded, CampTerrain.Rejection expected) {
        var reasons = new ArrayList<CampTerrain.Rejection>();
        assertTrue(CampTerrain.plan(level, center, excluded, reasons::add, true, Direction.NORTH).isEmpty());
        assertEquals(List.of(expected), reasons);
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test void existingOuterRockSpikeDoesNotRequireExcavatingAFlatSafeCamp() {
        remoteRockSpike();
        assertEquals(center, CampTerrain.earthworksCenter(level, center, true));
        var plan = fallback(Direction.NORTH);
        assertTrue(plan.preservedSteepEdges(), "The old all-boundary constraints reject this four-block edge");
        assertTrue(plan.changes().isEmpty(), "An unrelated natural rock spike must remain completely untouched");
        assertEquals(Direction.NORTH, plan.entrance());
        assertTrue(state(new BlockPos(15, 67, 0)).is(Blocks.STONE));
        assertTrue(state(new BlockPos(16, 67, 0)).is(Blocks.STONE));
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test void recoveredEarthworksKeepAllAdjacentSlopesAndTheWholeEntranceSafe() {
        remoteRockSpike();
        terrainHeight(1, 0, 66, false);
        terrainHeight(-1, 0, 62, false);
        var plan = fallback(Direction.NORTH);
        assertTrue(plan.preservedSteepEdges());
        assertEquals(4, plan.changes().size());
        assertTrue(plan.changes().stream().noneMatch(c -> c.pos().getX() >= 15));
        assertTrue(plan.changes().stream().allMatch(c -> Math.abs(c.pos().getX()) <= 15
                && Math.abs(c.pos().getZ()) <= 15));
        var raid = new RaidState("team:edge", "siege_core", 0);
        assertTrue(CampTerrain.apply(level, raid, plan));
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
            int actual = actualHeight(x, z);
            if (Math.max(Math.abs(x), Math.abs(z)) <= 9) assertEquals(64, actual);
            assertTrue(Math.abs(actual - height(x, z)) <= CampTerrain.FALLBACK_MAX_CHANGE);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = x + direction.getStepX(), nz = z + direction.getStepZ();
                assertTrue(Math.abs(actual - actualHeight(nx, nz))
                        <= Math.max(1, Math.abs(height(x, z) - height(nx, nz))), "Worsened existing slope");
                if (Math.max(Math.abs(x), Math.abs(z)) <= 10)
                    assertTrue(Math.abs(actual - actualHeight(nx, nz)) <= 1, "Unsafe camp shoulder");
            }
        }
        for (int distance = 9; distance <= 18; distance++) for (int side = -1; side <= 1; side++)
            assertEquals(64, actualHeight(side, -distance), "The full three-wide exit must connect to the core");
        var saved = RaidState.load(raid.save());
        assertEquals(plan.changes().size(), saved.campBlocks.size());
        assertTrue(saved.campBlocks.values().stream().allMatch(tag -> !tag.getCompound("Original").isEmpty()));
        assertEquals(Direction.NORTH, CampPerimeter.mainGateSide(saved));
    }

    private int actualHeight(int x, int z) {
        for (int y = 100; y >= 40; y--) if (!state(new BlockPos(x, y, z)).isAir()) return y + 1;
        throw new AssertionError("Unsupported column at " + x + ", " + z);
    }

    @Test void dryRowsBeyondACliffDoNotCountAsAConnectedEntrance() {
        for (int x = 16; x <= 18; x++) for (int z = -1; z <= 1; z++) terrainHeight(x, z, 72, true);
        var plan = fallback(Direction.EAST);
        assertTrue(plan.preservedSteepEdges());
        assertEquals(Direction.SOUTH, plan.entrance(), "Rotate away from an eight-block cliff across the east approach");
        assertTrue(plan.changes().isEmpty());
    }

    @Test void cliffsAcrossEveryExitStillRejectInsteadOfMakingAnIsolatedPad() {
        for (int x = -26; x <= 26; x++) for (int z = -26; z <= 26; z++)
            if (Math.max(Math.abs(x), Math.abs(z)) >= 16) terrainHeight(x, z, 72, true);
        rejected(pos -> false, CampTerrain.Rejection.EDGE);
    }

    @Test void blockedDryExitsRemainRejectedBeforeReliefRecovery() {
        remoteRockSpike();
        for (Direction side : Direction.Plane.HORIZONTAL) edits.put(center.relative(side, 18), Blocks.CHEST.defaultBlockState());
        rejected(pos -> false, CampTerrain.Rejection.NO_LAND_EXIT);
    }

    @Test void recoveryDoesNotBypassClaimsOrLoadedChunkChecks() {
        remoteRockSpike();
        rejected(pos -> pos.getX() == 15 && pos.getZ() == 0, CampTerrain.Rejection.CLAIM);
        when(level.hasChunkAt(new BlockPos(15, 64, 0))).thenReturn(false);
        rejected(pos -> false, CampTerrain.Rejection.UNLOADED);
    }

    @Test void recoveryDoesNotBypassWorldBorders() {
        remoteRockSpike();
        level.getWorldBorder().setSize(30);
        rejected(pos -> false, CampTerrain.Rejection.BORDER);
    }

    @Test void recoveryDoesNotExcavatePlayerBuildsOrNaturalRock() {
        remoteRockSpike();
        edits.put(new BlockPos(2, 64, 2), Blocks.CHEST.defaultBlockState());
        rejected(pos -> false, CampTerrain.Rejection.BLOCK_ENTITY);
        edits.put(new BlockPos(2, 64, 2), Blocks.OAK_PLANKS.defaultBlockState());
        rejected(pos -> false, CampTerrain.Rejection.CLEARANCE);
        edits.clear();
        terrainHeight(2, 2, 65, true);
        rejected(pos -> false, CampTerrain.Rejection.SOIL);
    }

    @Test void unsupportedWaterAndOverBudgetWorkStillReject() {
        remoteRockSpike();
        for (int y = 57; y <= 63; y++) edits.put(new BlockPos(2, y, 2), Blocks.WATER.defaultBlockState());
        rejected(pos -> false, CampTerrain.Rejection.FLUID);
        edits.clear();
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) terrainHeight(x, z, 52, false);
        rejected(pos -> false, CampTerrain.Rejection.BUDGET);
    }

    @Test void ordinaryGradingKeepsItsStrictBoundaryRulesAndPlanConstructorsRemainCompatible() {
        terrainHeight(13, 0, 70, false);
        var reasons = new ArrayList<CampTerrain.Rejection>();
        assertTrue(CampTerrain.plan(level, center, pos -> false, reasons::add).isEmpty());
        assertEquals(List.of(CampTerrain.Rejection.EDGE), reasons);
        assertFalse(new CampTerrain.Plan(List.of()).preservedSteepEdges());
        assertFalse(new CampTerrain.Plan(List.of(), Direction.SOUTH).preservedSteepEdges());
        heights.clear();
        assertFalse(fallback(Direction.NORTH).preservedSteepEdges());
    }
}
