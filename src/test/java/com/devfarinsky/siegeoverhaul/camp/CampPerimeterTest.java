package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampPerimeterTest extends MinecraftTestSupport {

    private static final int GROUND = 64;

    private ServerLevel flatLevel() {
        var level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(GROUND);
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        when(level.getBlockEntity(any())).thenReturn(null);
        when(level.getBlockState(any())).thenAnswer(call ->
                ((BlockPos) call.getArgument(0)).getY() < GROUND
                        ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        var border = mock(net.minecraft.world.level.border.WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        return level;
    }

    private RaidSavedData.RaidState camp() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.campPos = new BlockPos(0, GROUND, 0);
        raid.campClaimId = UUID.randomUUID();
        raid.warGate.putLong("Center", new BlockPos(0, GROUND, 20).asLong());
        raid.warGate.putInt("Facing", Direction.SOUTH.get2DDataValue());
        return raid;
    }

    private <T> T withClaims(RaidSavedData.RaidState raid, ServerLevel level, java.util.function.Supplier<T> body) {
        var saved = new RaidSavedData();
        saved.anchors.put(raid.teamKey, new RaidSavedData.Anchor(raid.teamKey, "Test", UUID.randomUUID(),
                Set.of(), false, false, Map.of(), 0));
        var claim = mock(RecruitsClaimsBridge.ClaimSnapshot.class);
        when(claim.claimId()).thenReturn(raid.campClaimId);
        when(claim.ownerFactionStringId()).thenReturn("enemy");
        try (var saves = mockStatic(RaidSavedData.class);
             var claims = mockStatic(RecruitsClaimsBridge.class);
             var external = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(null)).thenReturn(saved);
            claims.when(() -> RecruitsClaimsBridge.getClaimAt(eq(level), any(BlockPos.class)))
                    .thenReturn(Optional.of(claim));
            external.when(() -> ClaimBridge.isForeignClaim(eq(level), any(BlockPos.class),
                    any(RaidSavedData.Anchor.class))).thenReturn(false);
            return body.get();
        }
    }

    @Test
    void mainGateSideFollowsTheWarGateSoTheRoadIsNotWalledOff() {
        var raid = camp();
        assertEquals(Direction.SOUTH, CampPerimeter.mainGateSide(raid));
        assertEquals(raid.campPos.relative(Direction.SOUTH, CampPerimeter.RADIUS),
                CampPerimeter.mainGateCenter(raid));
    }

    @Test
    void gateSideWallLeavesAWideOpeningUnderALintel() {
        var level = flatLevel();
        var raid = camp();
        var plan = withClaims(raid, level, () -> CampPerimeter.plan(level, raid, CampPerimeter.FIRST_STAGE));
        assertFalse(plan.isEmpty());
        BlockPos gate = CampPerimeter.mainGateCenter(raid);
        // Nothing blocks the opening below the lintel.
        for (int lateral = -CampPerimeter.GATE_HALF_WIDTH; lateral <= CampPerimeter.GATE_HALF_WIDTH; lateral++) {
            BlockPos column = gate.relative(Direction.WEST, lateral);
            for (int y = 0; y < CampPerimeter.GATE_CLEARANCE; y++) {
                assertNull(plan.get(column.atY(GROUND + y).asLong()),
                        "gate opening must stay clear at lateral " + lateral + " y+" + y);
            }
            assertNotNull(plan.get(column.atY(GROUND + CampPerimeter.GATE_CLEARANCE).asLong()),
                    "gate lintel missing at lateral " + lateral);
        }
        // The wall itself is solid either side of the opening.
        BlockPos solid = gate.relative(Direction.WEST, CampPerimeter.GATE_HALF_WIDTH + 3);
        for (int y = 0; y < CampPerimeter.WALL_HEIGHT; y++) {
            assertEquals(CampPerimeter.WALL, plan.get(solid.atY(GROUND + y).asLong()));
        }
    }

    @Test
    void everyWallSideAndCornerTowerHasItsOwnStage() {
        var level = flatLevel();
        var raid = camp();
        Set<Long> seen = new HashSet<>();
        for (int stage = CampPerimeter.FIRST_STAGE; stage <= CampPerimeter.LAST_STAGE; stage++) {
            final int current = stage;
            var plan = withClaims(raid, level, () -> CampPerimeter.plan(level, raid, current));
            assertFalse(plan.isEmpty(), "stage " + stage + " produced no cells");
            assertTrue(plan.size() < 512, "stage " + stage + " exceeds the native job budget");
            seen.addAll(plan.keySet());
        }
        // A tower sits at each of the four corners of the claimed square.
        for (int sx : new int[]{-CampPerimeter.RADIUS, CampPerimeter.RADIUS}) {
            for (int sz : new int[]{-CampPerimeter.RADIUS, CampPerimeter.RADIUS}) {
                BlockPos corner = raid.campPos.offset(sx, 0, sz);
                assertTrue(seen.contains(corner.atY(GROUND + CampPerimeter.TOWER_HEIGHT - 1).asLong()),
                        "no guard tower at corner " + corner);
            }
        }
    }

    @Test
    void unbuildableColumnsAreSkippedInsteadOfFailingTheWholeSide() {
        var level = flatLevel();
        var raid = camp();
        // A ravine along part of the wall line: no ground within reach.
        when(level.getHeight(any(), anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(1) > 4 ? GROUND - 20 : GROUND);
        var plan = withClaims(raid, level, () -> CampPerimeter.plan(level, raid, CampPerimeter.FIRST_STAGE));
        assertFalse(plan.isEmpty());
        assertTrue(plan.keySet().stream().map(BlockPos::of).noneMatch(p -> p.getX() > 4),
                "columns over the ravine must be skipped");
    }

    @Test
    void perimeterStageRecordsTheMainGateForSentries() {
        var level = flatLevel();
        var raid = camp();
        raid.campUpgradeStage = CampPerimeter.FIRST_STAGE;
        try (var nativeJobs = mockStatic(NativeCampConstruction.class)) {
            nativeJobs.when(() -> NativeCampConstruction.start(level, raid)).thenReturn(true);
            withClaims(raid, level, () -> {
                CampDevelopment.tryPerimeter(level, raid);
                return null;
            });
        }
        assertEquals(CampPerimeter.FIRST_STAGE + 1, raid.campUpgradeStage);
        assertEquals(CampPerimeter.mainGateCenter(raid).asLong(), raid.warGate.getLong("PerimeterGate"));
        assertEquals(Direction.SOUTH.get2DDataValue(), raid.warGate.getInt("PerimeterGateFacing"));
    }

    @Test
    void aStageThatCannotBeBuiltIsAbandonedAfterABoundedNumberOfRetries() {
        var level = flatLevel();
        var raid = camp();
        raid.campUpgradeStage = CampPerimeter.FIRST_STAGE;
        when(level.hasChunkAt(any())).thenReturn(false);
        for (int attempt = 0; attempt < 2; attempt++) {
            withClaims(raid, level, () -> { CampDevelopment.tryPerimeter(level, raid); return null; });
            assertEquals(CampPerimeter.FIRST_STAGE, raid.campUpgradeStage);
        }
        withClaims(raid, level, () -> { CampDevelopment.tryPerimeter(level, raid); return null; });
        assertEquals(CampPerimeter.FIRST_STAGE + 1, raid.campUpgradeStage);
        assertTrue(raid.pendingCampBlocks.isEmpty());
    }

    @Test
    void gateSentriesTakeUpPostsBesideTheMainGate() {
        var raid = camp();
        var left = CampGuards.candidates(raid, 4).get(0);
        var right = CampGuards.candidates(raid, 5).get(0);
        assertNotEquals(left, right);
        for (BlockPos post : List.of(left, right)) {
            int distance = Math.max(Math.abs(post.getX() - raid.campPos.getX()),
                    Math.abs(post.getZ() - raid.campPos.getZ()));
            assertTrue(distance >= CampPerimeter.RADIUS - 2 && distance <= CampPerimeter.RADIUS,
                    "gate sentry should stand at the wall line, not in the middle of camp");
        }
    }

}
