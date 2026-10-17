package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import org.mockito.Answers;
import org.mockito.MockSettings;
import org.mockito.stubbing.Answer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mocked live-level reads only; not evidence of actual Workers construction or playtesting. */
class PerimeterGateAccessTest extends MinecraftTestSupport {
    private static final Set<ChunkPos> ONE = Set.of(new ChunkPos(0, 0));
    private static final BlockPos APPROACH = new BlockPos(7, 64, -1);
    private static final BlockPos SKIN = new BlockPos(7, 64, 0);

    @Test void freshValidatorReadsExactlyThreeLiteralAirCellsAndFullFloorWithoutNeighborsOrMutations() {
        var site = new Site();
        var validator = PerimeterGateAccess.validator(site.level, site.player, site.wall, site.permitted);
        assertNull(validator.problem(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        assertEquals(Set.of(APPROACH.asLong(), APPROACH.above().asLong(), APPROACH.above(2).asLong(), APPROACH.below().asLong()),
                new HashSet<>(site.reads));
        assertEquals(4, site.reads.size());
        verify(site.level, times(4)).getBlockEntity(any());
        site.noMutation();
        // The validator retains only plan metadata, never a previous world state or decision.
        site.put(APPROACH, Blocks.DANDELION);
        assertNotNull(validator.problem(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        site.put(APPROACH, Blocks.AIR);
        assertNull(validator.problem(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
    }

    @Test void everyHeadroomLayerRejectsFluidsSolidsPlantsHazardsAndBlockEntities() {
        for (int y = 0; y < 3; y++) for (Block block : List.of(Blocks.WATER, Blocks.LAVA, Blocks.STONE,
                Blocks.GRASS, Blocks.DANDELION, Blocks.TALL_GRASS, Blocks.VINE, Blocks.TORCH,
                Blocks.OAK_SAPLING, Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.CHEST,
                Blocks.MAGMA_BLOCK, Blocks.FIRE, Blocks.CACTUS, Blocks.POWDER_SNOW, Blocks.WITHER_ROSE)) {
            var site = new Site(); BlockPos blocked = APPROACH.above(y); site.put(blocked, block);
            String problem = site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH);
            assertNotNull(problem, block + " at layer " + y);
            assertTrue(problem.contains(blocked.toShortString()), problem);
            site.noMutation();
        }
    }

    @Test void literalAirVariantsPassButAnActualBlockEntityInApparentlyAirDoesNot() {
        var site = new Site(); site.put(APPROACH, Blocks.CAVE_AIR); site.put(APPROACH.above(), Blocks.VOID_AIR);
        assertNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        when(site.level.getBlockEntity(APPROACH)).thenReturn(mock(BlockEntity.class));
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        site.noMutation();
    }

    @Test void footingRejectsCliffsFluidsFallingBlocksAndDependentOrIncompleteShapes() {
        for (Block block : List.of(Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.SAND, Blocks.RED_SAND,
                Blocks.GRAVEL, Blocks.ANVIL, Blocks.DRAGON_EGG, Blocks.POINTED_DRIPSTONE, Blocks.SCAFFOLDING,
                Blocks.MUD, Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.SNOW, Blocks.OAK_SLAB, Blocks.OAK_STAIRS,
                Blocks.OAK_FENCE, Blocks.COBBLESTONE_WALL, Blocks.OAK_TRAPDOOR, Blocks.PISTON,
                Blocks.STICKY_PISTON, Blocks.HONEY_BLOCK, Blocks.SLIME_BLOCK, Blocks.CHEST,
                Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.SOUL_SAND, Blocks.ICE, Blocks.OAK_LEAVES)) {
            var site = new Site(); site.put(APPROACH.below(), block);
            assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH), block.toString());
            assertTrue(site.reads.stream().allMatch(cell -> BlockPos.of(cell).getX() == APPROACH.getX()
                    && BlockPos.of(cell).getZ() == APPROACH.getZ()), "No shape dependency may read a neighbor");
        }
        var site = new Site();
        // Forge's registry is frozen. Model an unknown block without invoking Block's constructor or registering it.
        Block moddedFullCube = mock(Block.class);
        BlockState unknown = mock(BlockState.class);
        when(unknown.getBlock()).thenReturn(moddedFullCube);
        when(unknown.hasBlockEntity()).thenReturn(false);
        when(unknown.getFluidState()).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(unknown.is(any(Block.class))).thenReturn(false);
        doThrow(new AssertionError("An unaudited shape must be refused before querying collision"))
                .when(unknown).isCollisionShapeFullBlock(any(), any());
        doThrow(new AssertionError("An unaudited shape must not query collision or neighbors"))
                .when(unknown).getCollisionShape(any(), any());
        site.states.put(APPROACH.below().asLong(), unknown);
        String refusal = site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH);
        assertNotNull(refusal);
        assertTrue(refusal.contains("existing stable, full-block"), refusal);
        assertTrue(refusal.contains(APPROACH.below().toShortString()), refusal);
        verify(unknown, never()).isCollisionShapeFullBlock(any(), any());
        verify(unknown, never()).getCollisionShape(any(), any());
        site.noMutation();
    }

    @Test void naturalStableFullSoilStoneAndExistingWallMaterialsAreAccepted() {
        for (Block block : List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
                Blocks.PODZOL, Blocks.MYCELIUM, Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
                Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.BEDROCK, Blocks.SNOW_BLOCK,
                Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS)) {
            var site = new Site(); site.put(APPROACH.below(), block);
            assertNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH), block.toString());
        }
    }

    @Test void gateSkinsMustBeEmptyEvenWhenTheyMatchTheOriginalPlannedWallMaterial() {
        var site = new Site(); assertEquals("minecraft:cobblestone", site.wall.blocks().get(SKIN.asLong()));
        assertNull(site.column(SKIN, PerimeterGateLayout.Region.WALL));
        for (Block block : List.of(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.DANDELION)) {
            site.put(SKIN, block);
            assertNotNull(site.column(SKIN, PerimeterGateLayout.Region.WALL));
        }
        site.noMutation();
    }

    @Test void onlyTheExactPlannedDirtFoundationCanSupplyAnAbsentWallFloor() {
        var site = new Site(ONE, true);
        assertEquals("minecraft:dirt", site.wall.blocks().get(SKIN.below().asLong()));
        site.put(SKIN.below(), Blocks.AIR);
        assertNull(site.column(SKIN, PerimeterGateLayout.Region.WALL));
        assertNotNull(site.column(SKIN, PerimeterGateLayout.Region.INSIDE_APPROACH));
        site.put(APPROACH.below(), Blocks.AIR);
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        for (Block block : List.of(Blocks.STONE, Blocks.DANDELION, Blocks.WATER, Blocks.CHEST)) {
            site.put(SKIN.below(), block);
            assertNotNull(site.column(SKIN, PerimeterGateLayout.Region.WALL));
        }
        site.put(SKIN.below(), Blocks.DIRT);
        assertNull(site.column(SKIN, PerimeterGateLayout.Region.WALL));
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.WALL));
    }

    @Test void everyReadChecksLoadedHeightBorderAndCurrentPermission() {
        for (int layer = -1; layer < 3; layer++) for (int mode = 0; mode < 4; mode++) {
            var site = new Site(); BlockPos rejected = APPROACH.above(layer);
            switch (mode) {
                case 0 -> when(site.level.hasChunkAt(rejected)).thenReturn(false);
                case 1 -> {
                    var border = mock(WorldBorder.class); when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
                    when(border.isWithinBounds(rejected)).thenReturn(false); when(site.level.getWorldBorder()).thenReturn(border);
                }
                case 2 -> when(site.level.mayInteract(site.player, rejected)).thenReturn(false);
                case 3 -> site.denied.add(rejected.asLong());
            }
            assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
            assertFalse(site.reads.contains(rejected.asLong()));
            verify(site.level, never()).getBlockEntity(rejected);
        }
        var tooLow = new Site(); when(tooLow.level.getMinBuildHeight()).thenReturn(64);
        assertNotNull(tooLow.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        assertFalse(tooLow.reads.contains(APPROACH.below().asLong()));
        var tooHigh = new Site(); when(tooHigh.level.getMaxBuildHeight()).thenReturn(66);
        assertNotNull(tooHigh.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        assertFalse(tooHigh.reads.contains(APPROACH.above(2).asLong()));
    }

    @Test void unloadBetweenStateAndBlockEntityReadFailsBeforeTheSecondReadAndLaterReloadIsFresh() {
        var site = new Site(); when(site.level.hasChunkAt(APPROACH)).thenReturn(true, false);
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        assertEquals(List.of(APPROACH.asLong()), site.reads);
        verify(site.level, never()).getBlockEntity(APPROACH);
        when(site.level.hasChunkAt(APPROACH)).thenReturn(true);
        assertNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        site.noMutation();
    }

    @Test void missingActorWrongWorldPermissionLossAndReadExceptionsFailClosed() {
        var site = new Site();
        assertNotNull(PerimeterGateAccess.columnProblem(site.level, null, site.wall, APPROACH,
                PerimeterGateLayout.Region.OUTSIDE_APPROACH, site.permitted));
        when(site.player.serverLevel()).thenReturn(mock(ServerLevel.class));
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        when(site.player.serverLevel()).thenReturn(site.level); when(site.player.mayBuild()).thenReturn(false);
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
        assertTrue(site.reads.isEmpty());
        when(site.player.mayBuild()).thenReturn(true);
        assertNotNull(PerimeterGateAccess.columnProblem(site.level, site.player, site.wall, APPROACH,
                PerimeterGateLayout.Region.OUTSIDE_APPROACH, p -> { throw new IllegalStateException("unavailable"); }));
        doThrow(new IllegalStateException("unavailable")).when(site.level).getBlockState(APPROACH);
        assertNotNull(site.column(APPROACH, PerimeterGateLayout.Region.OUTSIDE_APPROACH));
    }

    @Test void snapshotCapturesExactApproachesAndNaturalPassageFloorsWithOneReadPerCell() {
        var site = new Site();
        long firstAir = site.layout.approachClearance().iterator().next();
        long firstFloor = site.layout.approachFooting().iterator().next();
        site.states.put(firstAir, Blocks.CAVE_AIR.defaultBlockState());
        var snowy = Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true);
        site.states.put(firstFloor, snowy);
        var snapshot = site.snapshot(); assertTrue(snapshot.ready(), snapshot.problem());
        var contract = snapshot.contract();
        assertEquals(Blocks.CAVE_AIR.defaultBlockState(), contract.observations().get(firstAir));
        assertEquals(snowy, contract.observations().get(firstFloor));
        assertEquals(site.layout.approachClearance(), contract.airObservations());
        for (var column : site.wall.columns()) if (site.layout.passageClearance().contains(column.base().asLong()))
            assertTrue(contract.floorObservations().contains(column.base().below().asLong()));
        Set<Long> allChecked = new HashSet<>(contract.observations().keySet());
        allChecked.addAll(site.layout.passageClearance());
        assertEquals(allChecked, new HashSet<>(site.reads));
        assertEquals(allChecked.size(), site.reads.size(), "Snapshot reads each selected live cell once");
        assertTrue(contract.componentObservations(0).size() <= 348);
        assertDoesNotThrow(() -> contract.validateAgainst(ONE, contract.applyOpenings(site.wall)));
        site.noMutation();
    }

    @Test void snapshotKeepsPlannedFloorsOutOfObservationsAndDoesNotIgnoreBlockedSkin() {
        var site = new Site(ONE, true);
        for (var column : site.wall.columns()) if (column.supportDepth() > 0) site.put(column.base().below(), Blocks.AIR);
        var snapshot = site.snapshot(); assertTrue(snapshot.ready(), snapshot.problem());
        int supported = 0;
        for (var column : site.wall.columns()) if (column.supportDepth() > 0
                && site.layout.passageClearance().contains(column.base().asLong())) {
            supported++; assertFalse(snapshot.contract().observations().containsKey(column.base().below().asLong()));
        }
        assertTrue(supported > 0);
        long skin = site.layout.wallOpenings().iterator().next(); site.states.put(skin, Blocks.COBBLESTONE.defaultBlockState());
        var blocked = site.snapshot(); assertFalse(blocked.ready()); assertNull(blocked.contract());
        assertTrue(blocked.problem().contains(BlockPos.of(skin).toShortString()));
        site.noMutation();
    }

    @Test void malformedLayoutFailsBeforeAnyWorldReadAndCannotLeavePartialContract() {
        var site = new Site(); var missing = new HashSet<>(site.layout.approachClearance()); missing.remove(missing.iterator().next());
        var forged = new PerimeterGateLayout.Layout(site.layout.gates(), site.layout.wallOpenings(),
                site.layout.passageClearance(), missing, site.layout.approachFooting(), List.of());
        var result = PerimeterGateAccess.snapshot(site.level, site.player, ONE, site.wall, forged, site.permitted);
        assertFalse(result.ready()); assertNull(result.contract()); assertTrue(site.reads.isEmpty());
        var duplicates = new ArrayList<>(site.layout.gates()); duplicates.set(3, duplicates.get(0));
        forged = new PerimeterGateLayout.Layout(duplicates, site.layout.wallOpenings(), site.layout.passageClearance(),
                site.layout.approachClearance(), site.layout.approachFooting(), List.of());
        assertFalse(PerimeterGateAccess.snapshot(site.level, site.player, ONE, site.wall, forged, site.permitted).ready());
        assertTrue(site.reads.isEmpty());
    }

    @Test void exactStateAndPermissionChangesAreDetectedOnEveryObservationCheck() {
        var site = new Site(); var contract = site.snapshot().contract(); assertNotNull(contract);
        long cell = contract.floorObservations().iterator().next();
        site.states.put(cell, Blocks.GRASS_BLOCK.defaultBlockState());
        assertNotNull(site.full(contract));
        site.states.remove(cell); assertNull(site.full(contract));
        long air = contract.airObservations().iterator().next(); site.states.put(air, Blocks.CAVE_AIR.defaultBlockState());
        assertNotNull(site.full(contract), "Even another safe air state differs from the exact review");
        site.states.remove(air); site.denied.add(cell); assertNotNull(site.full(contract));
        site.denied.clear(); assertNull(site.full(contract));
        when(site.level.hasChunkAt(BlockPos.of(cell))).thenReturn(false); assertNotNull(site.full(contract));
        when(site.level.hasChunkAt(BlockPos.of(cell))).thenReturn(true); assertNull(site.full(contract));
        site.noMutation();
    }

    @Test void changingOnlyAStableFloorPropertyStillRequiresReview() {
        var site = new Site(); long floor = site.layout.approachFooting().iterator().next();
        site.states.put(floor, Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, false));
        var contract = site.snapshot().contract(); assertNotNull(contract); assertNull(site.full(contract));
        site.states.put(floor, Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true));
        assertNotNull(site.full(contract));
    }

    @Test void initiallyUnsafeSavedObservationsDoNotBecomeSafeMerelyByMatchingTheWorld() {
        var site = new Site(); var valid = site.snapshot().contract();
        var states = new LinkedHashMap<>(valid.observations()); long cell = valid.floorObservations().iterator().next();
        states.put(cell, Blocks.SAND.defaultBlockState()); site.states.put(cell, Blocks.SAND.defaultBlockState());
        var unsafe = PerimeterGateContract.create(ONE, site.wall, site.layout, states);
        assertNotNull(site.full(unsafe));
        site.states.clear();
        states = new LinkedHashMap<>(valid.observations()); cell = valid.airObservations().iterator().next();
        states.put(cell, Blocks.DANDELION.defaultBlockState()); site.states.put(cell, Blocks.DANDELION.defaultBlockState());
        unsafe = PerimeterGateContract.create(ONE, site.wall, site.layout, states);
        assertNotNull(site.full(unsafe));
    }

    @Test void activeComponentChecksAreBoundedFreshAndCatchFutureDamageOnActivationWhileFullChecksAlwaysCatchIt() {
        var site = new Site(Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)), false);
        var snapshot = site.snapshot(); assertTrue(snapshot.ready(), snapshot.problem()); var contract = snapshot.contract();
        site.reads.clear(); assertNull(site.full(contract));
        assertEquals(contract.observations().size(), site.reads.size());
        site.reads.clear(); assertNull(site.component(contract, 0));
        assertEquals(contract.componentObservations(0).keySet(), new HashSet<>(site.reads));
        assertEquals(contract.componentObservations(0).size(), site.reads.size());
        assertTrue(site.reads.size() <= PerimeterGateContract.MAX_COMPONENT_OBSERVATIONS);
        long future = contract.componentObservations(1).keySet().stream()
                .filter(cell -> contract.airObservations().contains(cell) && !contract.componentObservations(0).containsKey(cell))
                .findFirst().orElseThrow();
        site.states.put(future, Blocks.STONE.defaultBlockState());
        site.reads.clear(); assertNull(site.component(contract, 0)); assertFalse(site.reads.contains(future));
        assertNotNull(site.component(contract, 1)); assertNotNull(site.full(contract));
        site.states.remove(future); assertNull(site.component(contract, 1)); assertNull(site.full(contract));
        site.reads.clear(); assertNotNull(site.component(contract, -1)); assertNotNull(site.component(contract, 2));
        assertTrue(site.reads.isEmpty(), "Unknown component is rejected without world reads");
        site.noMutation();
    }

    @Test void nearManifestLimitFortyComponentEnvelopeStillReadsOnlyTheActiveComponent() {
        Set<ChunkPos> territory = new HashSet<>();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 5; z++) territory.add(new ChunkPos(2 * x, 2 * z));
        var site = new Site(territory, false, true); var snapshot = site.snapshot();
        assertTrue(snapshot.ready(), snapshot.problem()); var contract = snapshot.contract();
        assertEquals(160, contract.gates().size());
        assertEquals(40 * 312, contract.observations().size());
        assertEquals(65_280, site.wall.blocks().size() + site.wall.clearance().size() + contract.observations().size());
        site.reads.clear(); assertNull(site.full(contract));
        assertEquals(12_480, site.reads.size());
        for (int component : List.of(0, 20, 39)) {
            site.reads.clear(); assertNull(site.component(contract, component));
            assertEquals(312, site.reads.size());
            assertTrue(site.reads.size() <= PerimeterGateContract.MAX_COMPONENT_OBSERVATIONS);
            assertEquals(contract.componentObservations(component).keySet(), new HashSet<>(site.reads));
        }
        long future = contract.componentObservations(39).keySet().iterator().next();
        site.states.put(future, Blocks.WATER.defaultBlockState());
        site.reads.clear(); assertNull(site.component(contract, 0)); assertFalse(site.reads.contains(future));
        assertNotNull(site.component(contract, 39)); assertNotNull(site.full(contract));
        site.noMutation();
    }

    @Test void boundedScaleHarnessFailsFastForMutationAndChunkLoadingWithoutRecordedHistory() {
        var site = new Site(ONE, false, true);
        assertThrows(AssertionError.class, () -> site.level.setBlock(APPROACH, Blocks.STONE.defaultBlockState(), 3, 512));
        assertThrows(AssertionError.class, () -> site.level.getChunkAt(APPROACH));
        assertEquals(2, site.forbiddenCalls);
        assertTrue(mockingDetails(site.level).getInvocations().isEmpty());
    }

    private static final class Site {
        final ServerLevel level;
        final ServerPlayer player;
        final boolean boundedHistory;
        int forbiddenCalls;
        final Map<Long, BlockState> states = new HashMap<>();
        final List<Long> reads = new ArrayList<>();
        final Set<Long> denied = new HashSet<>();
        final Predicate<BlockPos> permitted = p -> !denied.contains(p.asLong());
        final Set<ChunkPos> territory;
        final PerimeterBlueprint.Plan wall;
        final PerimeterGateLayout.Layout layout;
        Site() { this(ONE, false); }
        Site(Set<ChunkPos> territory, boolean raised) { this(territory, raised, false); }
        Site(Set<ChunkPos> territory, boolean raised, boolean boundedHistory) {
            this.boundedHistory = boundedHistory;
            MockSettings worldSettings = withSettings(), playerSettings = withSettings();
            if (boundedHistory) {
                // Keep the exact scale/read checks without retaining hundreds of thousands of Mockito stack records.
                // Any unexpected world/player API fails immediately, including every mutation/loading overload.
                worldSettings.stubOnly().defaultAnswer(readsOnly(Set.of("getMinBuildHeight", "getMaxBuildHeight",
                        "getWorldBorder", "hasChunkAt", "mayInteract", "getBlockState", "getBlockEntity")));
                playerSettings.stubOnly().defaultAnswer(readsOnly(Set.of("serverLevel", "isAlive", "isSpectator", "mayBuild")));
            }
            level = mock(ServerLevel.class, worldSettings); player = mock(ServerPlayer.class, playerSettings);
            this.territory = territory;
            wall = PerimeterBlueprint.create(territory, (x, z) -> PerimeterBlueprint.Surface.ready(raised && x < 8 ? 62 : 64),
                    PerimeterBlueprint.Palette.COBBLESTONE);
            layout = PerimeterGateLayout.create(territory, wall, (feet, region) -> null);
            assertTrue(layout.valid(), layout.problemSummary());
            when(player.serverLevel()).thenReturn(level); when(player.isAlive()).thenReturn(true); when(player.mayBuild()).thenReturn(true);
            when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
            when(level.getWorldBorder()).thenReturn(new WorldBorder()); when(level.hasChunkAt(any())).thenReturn(true);
            when(level.mayInteract(eq(player), any())).thenReturn(true);
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0); reads.add(pos.asLong());
                return states.getOrDefault(pos.asLong(), pos.getY() < 64 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }).when(level).getBlockState(any());
        }
        void put(BlockPos pos, Block block) { states.put(pos.asLong(), block.defaultBlockState()); }
        String column(BlockPos feet, PerimeterGateLayout.Region region) {
            return PerimeterGateAccess.columnProblem(level, player, wall, feet, region, permitted);
        }
        PerimeterGateAccess.Snapshot snapshot() { return PerimeterGateAccess.snapshot(level, player, territory, wall, layout, permitted); }
        String full(PerimeterGateContract contract) { return PerimeterGateAccess.observationProblem(level, player, contract, permitted); }
        String component(PerimeterGateContract contract, int component) {
            return PerimeterGateAccess.componentObservationProblem(level, player, contract, component, permitted);
        }
        private Answer<Object> readsOnly(Set<String> methods) {
            return call -> {
                String method = call.getMethod().getName();
                if (!methods.contains(method) && !Set.of("toString", "hashCode", "equals").contains(method)) {
                    forbiddenCalls++;
                    throw new AssertionError("Unexpected API in read-only scale fixture: " + method);
                }
                return Answers.RETURNS_DEFAULTS.answer(call);
            };
        }
        void noMutation() {
            if (boundedHistory) {
                assertEquals(0, forbiddenCalls, "Every unauthorized API is fail-fast, even without invocation history");
                assertTrue(mockingDetails(level).getInvocations().isEmpty());
                assertTrue(mockingDetails(player).getInvocations().isEmpty());
                return;
            }
            verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
            verify(level, never()).destroyBlock(any(), anyBoolean(), any());
            verify(level, never()).getChunkAt(any());
            verify(level, never()).getHeight(any(), anyInt(), anyInt());
            verify(level, never()).hasNeighborSignal(any());
        }
    }
}
