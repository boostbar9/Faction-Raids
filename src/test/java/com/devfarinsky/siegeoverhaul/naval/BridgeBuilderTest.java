package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.formations.RecruitsFormationBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BridgeBuilderTest extends MinecraftTestSupport {
    private final BlockPos start = new BlockPos(0, 64, 0), objective = new BlockPos(80, 64, 0);
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final RaidSavedData.RaidState state = new RaidSavedData.RaidState("team:test", "home", 0);
    private final RaidSavedData data = new RaidSavedData();
    private final RaidSavedData.Anchor anchor = new RaidSavedData.Anchor("team:test", "Test", UUID.randomUUID(),
            Set.of(), false, false, Map.of(), 0);
    private ServerLevel level;
    private Mob mob;
    private PathNavigation navigation;

    @BeforeEach void setup() throws ReflectiveOperationException {
        level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        var border = mock(WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call -> blocks.getOrDefault(call.getArgument(0), Blocks.AIR.defaultBlockState()));
        when(level.noCollision(isNull(), any())).thenReturn(true);
        when(level.setBlockAndUpdate(any(), any())).thenAnswer(call -> {
            blocks.put(call.getArgument(0), call.getArgument(1));
            return true;
        });
        mob = mock(Mob.class);
        navigation = mock(PathNavigation.class);
        when(mob.getNavigation()).thenReturn(navigation);
        when(mob.getLookControl()).thenReturn(mock(LookControl.class));
        when(mob.getUUID()).thenReturn(UUID.randomUUID());
        when(mob.isAlive()).thenReturn(true);
        when(mob.onGround()).thenReturn(true);
        when(mob.blockPosition()).thenReturn(start);
        when(mob.getBbWidth()).thenReturn(.6F);
        when(mob.distanceToSqr(any(Vec3.class))).thenReturn(0.0);
        var tags = new CompoundTag();
        tags.putString(ModConstants.Tags.RAID_TEAM, state.teamKey);
        tags.putString(ModConstants.Tags.RAID_ROLE, "shieldman");
        when(mob.getPersistentData()).thenReturn(tags);
        var goals = Mob.class.getField("goalSelector");
        goals.setAccessible(true);
        goals.set(mob, new GoalSelector(() -> null));
        when(level.getEntity(mob.getUUID())).thenReturn(mob);
        state.raiders.add(mob.getUUID());
        data.anchors.put(state.teamKey, anchor);
        data.raids.put(state.teamKey, state);
        blocks.put(start.below(), Blocks.STONE.defaultBlockState());
    }

    private void gap(int span, boolean water) {
        blocks.put(start.east(span + 1).below(), Blocks.STONE.defaultBlockState());
        if (water) for (int i = 1; i <= span; i++) blocks.put(start.east(i).below(), Blocks.WATER.defaultBlockState());
    }

    private int scan(int limit) {
        return BridgeBuilder.scanGap(level, start, Direction.EAST, limit, anchor, new HashMap<>());
    }

    @Test void acceptsAirAndWaterAtFloorLevelAndEnforcesInclusiveGapBounds() {
        try (var claims = mockStatic(ClaimBridge.class)) {
            for (boolean water : new boolean[]{false, true}) {
                blocks.clear(); blocks.put(start.below(), Blocks.STONE.defaultBlockState());
                gap(24, water);
                assertEquals(24, scan(24));
                assertEquals(0, scan(23));
            }
            blocks.clear(); blocks.put(start.below(), Blocks.STONE.defaultBlockState()); gap(64, false);
            assertEquals(64, scan(64));
            assertEquals(0, scan(65));
            blocks.remove(start.east(65).below()); gap(65, false);
            assertEquals(0, scan(64));
        }
        assertEquals(24, RaidConfig.MAX_BRIDGE_SPAN.get());
    }

    @Test void rejectsSolidWaterloggedLavaLowCeilingsAndHazardousShores() {
        try (var claims = mockStatic(ClaimBridge.class)) {
            gap(3, false);
            for (BlockState forbidden : List.of(Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true), Blocks.LAVA.defaultBlockState(),
                    Blocks.OAK_FENCE.defaultBlockState())) {
                blocks.put(start.east().below(), forbidden);
                assertEquals(0, scan(24));
            }
            blocks.remove(start.east().below());
            blocks.put(start.east().above(), Blocks.STONE.defaultBlockState());
            assertEquals(0, scan(24));
            blocks.remove(start.east().above());
            blocks.put(start.east(4).below(), Blocks.MAGMA_BLOCK.defaultBlockState());
            assertEquals(0, scan(24));
            blocks.put(start.east(4).below(), Blocks.STONE.defaultBlockState());
            blocks.put(start.east().below(2), Blocks.LAVA.defaultBlockState());
            assertEquals(0, scan(24));
        }
    }

    @Test void rejectsUnloadedBorderHeightAndCollisionWithoutMutatingTerrain() {
        try (var claims = mockStatic(ClaimBridge.class)) {
            gap(3, false);
            when(level.hasChunkAt(start.east(2))).thenReturn(false);
            assertEquals(0, scan(24));
            when(level.hasChunkAt(start.east(2))).thenReturn(true);
            when(level.getWorldBorder().isWithinBounds(start.east(2))).thenReturn(false);
            assertEquals(0, scan(24));
            when(level.getWorldBorder().isWithinBounds(start.east(2))).thenReturn(true);
            when(level.isOutsideBuildHeight(start.east(2))).thenReturn(true);
            assertEquals(0, scan(24));
            when(level.isOutsideBuildHeight(start.east(2))).thenReturn(false);
            when(level.noCollision(isNull(), any())).thenReturn(false);
            assertEquals(0, scan(24));
            when(level.noCollision(isNull(), any())).thenReturn(true);
            when(level.getEntities(isNull(Entity.class), any(), any())).thenReturn(List.of(mob));
            assertEquals(0, scan(24));
            verify(level, never()).setBlockAndUpdate(any(), any());
        }
    }

    @Test void claimChecksAreDeduplicatedPerChunkAndForeignClaimsBlockThePlan() {
        try (var claims = mockStatic(ClaimBridge.class)) {
            gap(24, false);
            assertEquals(24, scan(24));
            claims.verify(() -> ClaimBridge.isForeignClaim(level, new ChunkPos(0, 0), anchor), times(1));
            claims.verify(() -> ClaimBridge.isForeignClaim(level, new ChunkPos(1, 0), anchor), times(1));
            claims.when(() -> ClaimBridge.isForeignClaim(level, new ChunkPos(1, 0), anchor)).thenReturn(true);
            assertEquals(0, scan(24));
        }
    }

    @Test void existingReachableObjectiveRouteNeverPromotesOrSpends() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            gap(3, true);
            Path path = mock(Path.class);
            when(path.canReach()).thenReturn(true);
            when(navigation.createPath(objective, 1)).thenReturn(path);
            BridgeBuilder.tick(level, state, objective);
            assertNull(state.bridgePlan);
            assertEquals(0, state.bridgeAttempts);
            verify(mob, never()).setCustomName(any());
            verify(level, never()).setBlockAndUpdate(any(), any());
        }
    }

    @Test void promotionConservesPopulationCombatRoleAndReservesAttemptBeforeConstruction() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class);
             var formations = mockStatic(RecruitsFormationBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            gap(3, false);
            Path approach = mock(Path.class);
            when(approach.canReach()).thenReturn(true);
            when(navigation.createPath(start, 0)).thenReturn(approach);
            BridgeBuilder.tick(level, state, objective);
            assertNotNull(state.bridgePlan);
            assertEquals(1, state.bridgeAttempts);
            assertEquals(Set.of(mob.getUUID()), state.raiders);
            assertEquals("shieldman", mob.getPersistentData().getString(ModConstants.Tags.RAID_ROLE));
            assertTrue(mob.getPersistentData().getBoolean(BridgeBuilder.SPECIALIST_TAG));
            assertTrue(BridgeBuilder.assigned(mob));
            verify(mob).setCustomName(argThat(name -> "Enemy Bridge Builder".equals(name.getString())));
            verify(level, never()).setBlockAndUpdate(any(), any());
        }
    }

    @Test void excludesCaptainsHeroesOperatorsGuardsScoutsAndNonMembers() {
        assertTrue(BridgeBuilder.eligible(mob, state));
        var tags = mob.getPersistentData();
        for (String role : List.of("captain", "commander", "hero", "warcaster", "cavalry", "scout", "flanker")) {
            tags.putString(ModConstants.Tags.RAID_ROLE, role);
            assertFalse(BridgeBuilder.eligible(mob, state));
        }
        // The roles RaidEvents actually writes for rank-and-file troops must
        // qualify, otherwise no wave ever produces a bridge builder.
        for (String role : List.of("marksman", "breacher", "shieldman", "bowman", "crossbowman")) {
            tags.putString(ModConstants.Tags.RAID_ROLE, role);
            assertTrue(BridgeBuilder.eligible(mob, state), role);
        }
        tags.putString(ModConstants.Tags.RAID_ROLE, "shieldman");
        for (String marker : List.of("SiegeEnemyHero", "SiegeHeroRole", "SiegeCampGuardTeam",
                "SiegeOperatorAssigned", "FactionRaidsSiegeTeam", ModConstants.Tags.CAMP_WORKER_TEAM, ModConstants.Tags.SCOUT)) {
            tags.putBoolean(marker, true);
            assertFalse(BridgeBuilder.eligible(mob, state), marker);
            tags.remove(marker);
        }
        state.campGuards.add(mob.getUUID()); assertFalse(BridgeBuilder.eligible(mob, state));
        state.campGuards.clear(); state.raiders.clear(); assertFalse(BridgeBuilder.eligible(mob, state));
    }

    private BridgeBuilder.BuilderGoal job() {
        gap(3, true);
        state.bridgeAttempts = 1;
        state.bridgePlan = new BridgePlan(mob.getUUID(), start, objective, Direction.EAST, 3, 1000);
        return new BridgeBuilder.BuilderGoal(level, state, mob);
    }

    private void step(BridgeBuilder.BuilderGoal goal, long time) {
        when(level.getGameTime()).thenReturn(time);
        for (int i = 0; i < 10; i++) goal.tick();
    }

    @Test void builderMustReachEachEdgeAndPlacesOneRestorableFloorBlockAtATime() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job();
            Path approach = mock(Path.class); when(approach.canReach()).thenReturn(true);
            when(navigation.createPath(start, 0)).thenReturn(approach);
            when(mob.distanceToSqr(any(Vec3.class))).thenReturn(9.0);
            step(goal, 0);
            assertEquals(0, state.bridgeBlocksSpent);
            verify(navigation).moveTo(approach, 1.0);
            when(mob.distanceToSqr(any(Vec3.class))).thenReturn(0.0);
            step(goal, 20);
            assertEquals(1, state.bridgeBlocksSpent);
            assertEquals(Blocks.OAK_PLANKS.defaultBlockState(), blocks.get(start.east().below()));
            assertFalse(blocks.containsKey(start.east()));
            assertEquals("minecraft:water", state.campBlocks.get(start.east().below().asLong())
                    .getCompound("Original").getString("Name"));
            step(goal, 20);
            assertEquals(1, state.bridgeBlocksSpent);
            when(mob.distanceToSqr(any(Vec3.class))).thenReturn(9.0);
            step(goal, 40);
            assertEquals(1, state.bridgeBlocksSpent);
            when(mob.distanceToSqr(any(Vec3.class))).thenReturn(0.0);
            step(goal, 60);
            assertEquals(2, state.bridgeBlocksSpent);
            assertEquals(3, state.bridgePlan.next);
        }
    }

    @Test void failedPlacementAndEntitiesNeverConsumeMaterialsOrRecordCleanup() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job();
            when(level.setBlockAndUpdate(any(), any())).thenReturn(false);
            step(goal, 0);
            assertEquals(0, state.bridgeBlocksSpent); assertTrue(state.campBlocks.isEmpty());
            when(level.getEntities(isNull(Entity.class), any(), any())).thenReturn(List.of(mob));
            clearInvocations(level);
            step(goal, 20);
            verify(level, never()).setBlockAndUpdate(any(), any());
            assertEquals(1, state.bridgePlan.next);
        }
    }

    @Test void changedTerrainCancelsWithoutReplacingPlayersBlocksOrRefunding() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job();
            step(goal, 0);
            blocks.put(start.east(2).below(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            step(goal, 20);
            assertNull(state.bridgePlan);
            assertEquals(1, state.bridgeAttempts); assertEquals(1, state.bridgeBlocksSpent);
            assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), blocks.get(start.east(2).below()));
        }
    }

    @Test void saveLoadRetainsPlanBudgetsAndCancellationCannotRefillThem() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job(); step(goal, 0);
            var loaded = RaidSavedData.RaidState.load(state.save());
            assertEquals(1, loaded.bridgeAttempts); assertEquals(1, loaded.bridgeBlocksSpent);
            assertEquals(2, loaded.bridgePlan.next);
            assertEquals(start, loaded.bridgePlan.start); assertEquals(objective, loaded.bridgePlan.objective);
            assertEquals(mob.getUUID(), loaded.bridgePlan.builder);
            assertEquals(1000, loaded.bridgePlan.deadline); assertEquals(20, loaded.bridgePlan.nextPlacement);
            BridgeBuilder.cancel(level, loaded);
            var cancelled = RaidSavedData.RaidState.load(loaded.save());
            assertNull(cancelled.bridgePlan);
            assertEquals(1, cancelled.bridgeAttempts); assertEquals(1, cancelled.bridgeBlocksSpent);
            assertEquals(400, cancelled.bridgeNextAttempt);
            var old = RaidSavedData.RaidState.load(new CompoundTag());
            assertNull(old.bridgePlan); assertEquals(0, old.bridgeAttempts); assertEquals(0, old.bridgeBlocksSpent);
        }
    }

    @Test void unloadedBuilderWaitsWithoutBuildingAndDeadOrExpiredBuilderCancels() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            job(); when(level.getEntity(mob.getUUID())).thenReturn(null);
            BridgeBuilder.tick(level, state, objective);
            assertNotNull(state.bridgePlan);
            verify(level, never()).setBlockAndUpdate(any(), any());
            when(level.getGameTime()).thenReturn(1000L);
            BridgeBuilder.tick(level, state, objective);
            assertNull(state.bridgePlan); assertEquals(1, state.bridgeAttempts);
            job(); when(level.getGameTime()).thenReturn(0L);
            when(level.getEntity(mob.getUUID())).thenReturn(mob); when(mob.isAlive()).thenReturn(false);
            BridgeBuilder.tick(level, state, objective);
            assertNull(state.bridgePlan); assertEquals(1, state.bridgeAttempts);
        }
    }

    @Test void invalidSavedPlansAreDiscardedButSpentBudgetsRemain() {
        job();
        for (String direction : List.of("up", "down", "invalid")) {
            var tag = state.save(); tag.getCompound("BridgePlan").putString("Direction", direction);
            assertNull(RaidSavedData.RaidState.load(tag).bridgePlan);
            assertEquals(1, RaidSavedData.RaidState.load(tag).bridgeAttempts);
        }
        var tag = state.save(); tag.getCompound("BridgePlan").putInt("Span", 65);
        assertNull(RaidSavedData.RaidState.load(tag).bridgePlan);
    }

    @Test void discoveryRequiresReachableEdgeAndMovementTowardObjective() {
        try (var claims = mockStatic(ClaimBridge.class)) {
            gap(3, false);
            assertNull(BridgeBuilder.discover(level, mob, objective, anchor, new HashMap<>(), 24, 96));
            Path approach = mock(Path.class); when(approach.canReach()).thenReturn(true);
            when(navigation.createPath(start, 0)).thenReturn(approach);
            assertNotNull(BridgeBuilder.discover(level, mob, objective, anchor, new HashMap<>(), 24, 96));
            assertNull(BridgeBuilder.discover(level, mob, start.west(80), anchor, new HashMap<>(), 24, 96));
            assertNull(BridgeBuilder.discover(level, mob, objective, anchor, new HashMap<>(), 24, 2));
        }
    }

    @Test void exhaustedBudgetsAndNewlyReachableRouteStopConstruction() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            state.bridgeAttempts = RaidConfig.MAX_BRIDGES_PER_RAID.get();
            BridgeBuilder.tick(level, state, objective);
            assertNull(state.bridgePlan);
            verifyNoInteractions(navigation);
            var goal = job();
            Path route = mock(Path.class); when(route.canReach()).thenReturn(true);
            when(navigation.createPath(objective, 1)).thenReturn(route);
            step(goal, 0);
            assertNull(state.bridgePlan); assertEquals(0, state.bridgeBlocksSpent);
            when(navigation.createPath(objective, 1)).thenReturn(null);
            goal = job(); state.bridgeBlocksSpent = RaidConfig.MAX_BRIDGE_BLOCKS_PER_RAID.get();
            step(goal, 20);
            assertNull(state.bridgePlan);
            verify(level, never()).setBlockAndUpdate(any(), any());
        }
    }

    @Test void restoredJobRegainsMovementLockAndContinuesAtSavedIndex() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class);
             var formations = mockStatic(RecruitsFormationBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job(); step(goal, 0);
            var loaded = RaidSavedData.RaidState.load(state.save());
            data.raids.put(state.teamKey, loaded);
            assertFalse(BridgeBuilder.assigned(mob));
            BridgeBuilder.tick(level, loaded, objective);
            assertTrue(BridgeBuilder.assigned(mob));
            var restored = (BridgeBuilder.BuilderGoal) mob.goalSelector.getAvailableGoals().iterator().next().getGoal();
            step(restored, 20);
            assertEquals(2, loaded.bridgeBlocksSpent); assertEquals(3, loaded.bridgePlan.next);
            assertEquals(1, loaded.bridgeAttempts);
        }
    }

    @Test void completionAnnouncementOccursOnlyAfterLastPlacementAndOnlyOnce() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class);
             var formations = mockStatic(RecruitsFormationBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job(); step(goal, 0); step(goal, 20);
            assertFalse(state.bridgeCompletionPending);
            step(goal, 40);
            assertTrue(state.bridgeCompletionPending);
            var loaded = RaidSavedData.RaidState.load(state.save());
            data.raids.put(state.teamKey, loaded);
            assertTrue(BridgeBuilder.tick(level, loaded, objective));
            assertFalse(BridgeBuilder.tick(level, loaded, objective));
            assertEquals(3, loaded.bridgeBlocksSpent);
        }
    }

    @Test void pausedDisabledNoAiAndEndedRaidsCannotBuildOrKeepMovementExclusions() {
        try (var saves = mockStatic(RaidSavedData.class); var claims = mockStatic(ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var goal = job();
            mob.goalSelector.addGoal(0, goal);
            assertTrue(BridgeBuilder.assigned(mob));
            for (int condition = 0; condition < 5; condition++) {
                switch (condition) {
                    case 0 -> state.offlinePauseAnnounced = true;
                    case 1 -> state.preparationTicks = 100;
                    case 2 -> when(mob.isNoAi()).thenReturn(true);
                    case 3 -> RaidConfig.ENABLED.set(false);
                    case 4 -> data.raids.remove(state.teamKey);
                }
                assertFalse(BridgeBuilder.assigned(mob));
                assertFalse(goal.canUse());
                step(goal, 0);
                assertEquals(0, state.bridgeBlocksSpent);
                assertEquals(1, state.bridgeAttempts);
                assertNotNull(state.bridgePlan);
                state.offlinePauseAnnounced = false;
                state.preparationTicks = 0;
                when(mob.isNoAi()).thenReturn(false);
                RaidConfig.ENABLED.set(true);
                data.raids.put(state.teamKey, state);
            }
            step(goal, 20);
            assertEquals(1, state.bridgeBlocksSpent);
            state.raiders.clear();
            assertFalse(BridgeBuilder.assigned(mob));
            step(goal, 40);
            assertEquals(1, state.bridgeBlocksSpent);
        }
    }
}
