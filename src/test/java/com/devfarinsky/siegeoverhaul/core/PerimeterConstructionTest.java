package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerimeterConstructionTest extends MinecraftTestSupport {
    @Test void prepareCachesPermittedChunksInTheProductionPermissionCallback() {
        assertPreparePermissionLookups(false, false);
    }

    @Test void prepareStillRejectsADifferentDeniedChunkAfterPermittedCells() {
        assertPreparePermissionLookups(true, false);
    }

    @Test void prepareStillRejectsAProviderFailureAfterPermittedCells() {
        assertPreparePermissionLookups(false, true);
    }

    private void assertPreparePermissionLookups(boolean denySecondChunk, boolean failSecondChunk) {
        var site = new BlockPos(8, 64, 8);
        var chunks = Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0));
        var level = level();
        when(level.getHeight(eq(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES),
                anyInt(), anyInt())).thenReturn(64);
        assertTrue(chunks.size() > 1, "The fixture must exercise separate chunks");
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        var player = mock(ServerPlayer.class);
        when(player.serverLevel()).thenReturn(level);
        when(player.isAlive()).thenReturn(true);
        when(player.mayBuild()).thenReturn(true);
        when(level.mayInteract(eq(player), any(BlockPos.class))).thenReturn(true);
        var anchor = new RaidSavedData.Anchor("team:a", "A", UUID.randomUUID(),
                Set.of(), false, false, Map.of(), 0);
        var data = new RaidSavedData();
        data.anchors.put("team:a", anchor);
        var claim = new RecruitsClaimsBridge.ClaimSnapshot(UUID.randomUUID(), "Home", "faction",
                new ChunkPos(site), chunks, false, 10, 10);
        var permissionLookups = new HashMap<ChunkPos, Integer>();
        var ownershipLookups = new HashMap<ChunkPos, Integer>();
        var broken = new AtomicBoolean();
        // Stop only after the real plan/site/permission checks, before unrelated resource lookup.
        String siteAccepted = "test: site validation completed";
        try (var cores = mockStatic(SiegeCore.class);
             var saves = mockStatic(RaidSavedData.class);
             var workers = mockStatic(WorkersBridge.class);
             var recruits = mockStatic(RecruitsClaimsBridge.class);
             var claims = mockStatic(ClaimBridge.class);
             var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            cores.when(() -> SiegeCore.key(player)).thenReturn("team:a");
            cores.when(() -> SiegeCore.point(player.server, "team:a"))
                    .thenReturn(new RaidSavedData.DefensePoint("siege_core", Level.OVERWORLD.location(), site));
            saves.when(() -> RaidSavedData.get(player.server)).thenReturn(data);
            workers.when(WorkersBridge::available).thenReturn(true);
            recruits.when(RecruitsClaimsBridge::available).thenReturn(true);
            recruits.when(() -> RecruitsClaimsBridge.resolveDefendingClaim(level, anchor)).thenReturn(Optional.of(claim));
            recruits.when(() -> RecruitsClaimsBridge.getFactionTerritory(level, "faction", PerimeterTerritory.MAX_CHUNKS))
                    .thenReturn(new RecruitsClaimsBridge.TerritorySnapshot("faction", chunks, null));
            recruits.when(() -> RecruitsClaimsBridge.isChunkOwnedBy(eq(level), any(ChunkPos.class), eq("faction")))
                    .thenAnswer(call -> { ownershipLookups.merge(call.getArgument(1), 1, Integer::sum); return true; });
            claims.when(ClaimBridge::providerBroken).thenAnswer(call -> broken.get());
            claims.when(() -> ClaimBridge.isForeignClaim(eq(level), any(ChunkPos.class), any(RaidSavedData.Anchor.class)))
                    .thenAnswer(call -> {
                        ChunkPos chunk = call.getArgument(1);
                        permissionLookups.merge(chunk, 1, Integer::sum);
                        if (permissionLookups.size() == 2) {
                            if (failSecondChunk) broken.set(true);
                            return denySecondChunk || failSecondChunk;
                        }
                        return false;
                    });
            guard.when(NativeConstructionGuard::availabilityProblem).thenReturn(siteAccepted);
            var result = PerimeterConstruction.prepare(player, site, 1);
            if (failSecondChunk) assertEquals(DefenseStructures.PROVIDER_FAILURE, result.problem());
            else if (denySecondChunk) {
                assertNotNull(result.problem());
                assertTrue(result.problem().contains("also claimed by another faction"), result.problem());
            } else assertEquals(siteAccepted, result.problem());
            assertNull(result.builder(), "Read-only preparation must stop before resource selection");
            assertEquals(denySecondChunk || failSecondChunk ? 2 : chunks.size(), permissionLookups.size());
            if (!denySecondChunk && !failSecondChunk) assertEquals(chunks, permissionLookups.keySet());
            assertTrue(permissionLookups.values().stream().allMatch(count -> count == 1), permissionLookups.toString());
            assertEquals(permissionLookups, ownershipLookups, "Both production bridge lookups must be cached per chunk");
            verify(level, atLeast(2)).mayInteract(eq(player), any(BlockPos.class));
            guard.verify(NativeConstructionGuard::availabilityProblem,
                    denySecondChunk || failSecondChunk ? never() : times(1));
            verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
            verify(level, never()).destroyBlock(any(), anyBoolean(), any());
        }
    }

    @Test void territoryDenialReportsRecruitsFailureInsteadOfLostTerritory() {
        var chunk = new ChunkPos(3, -2);
        var territory = new com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.TerritorySnapshot("faction", Set.of(chunk), null);
        try (var recruits = mockStatic(com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.class, CALLS_REAL_METHODS)) {
            recruits.when(() -> com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.isChunkOwnedBy(any(), any(), any())).thenReturn(true);
            assertNull(PerimeterConstruction.territoryDenial(mock(ServerLevel.class), chunk, territory, null));
            var disabled = new java.util.concurrent.atomic.AtomicBoolean();
            recruits.when(() -> com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.isChunkOwnedBy(any(), any(), any()))
                    .thenAnswer(call -> { disabled.set(true); return false; });
            recruits.when(com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge::failed).thenAnswer(call -> disabled.get());
            String message = PerimeterConstruction.territoryDenial(mock(ServerLevel.class), chunk, territory, null);
            assertEquals(DefenseStructures.PROVIDER_FAILURE, message);
            assertFalse(message.contains("no longer part"), message);
        }
        // Without a failure, a chunk the faction lost is still reported as lost territory.
        try (var recruits = mockStatic(com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.class, CALLS_REAL_METHODS)) {
            recruits.when(() -> com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.isChunkOwnedBy(any(), any(), any())).thenReturn(false);
            String lost = PerimeterConstruction.territoryDenial(mock(ServerLevel.class), chunk, territory, null);
            assertTrue(lost.contains("no longer part of your faction territory"), lost);
        }
    }
    private PerimeterBlueprint.Plan plan() { return PerimeterBlueprint.create(Set.of(new ChunkPos(0,0)),
            (x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE); }
    private ServerLevel level() {
        var level=mock(ServerLevel.class);when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getAllEntities()).thenReturn(List.of());
        var storage=mock(net.minecraft.world.level.storage.DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any(),any(),anyString())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        when(level.getMaxBuildHeight()).thenReturn(320);when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getBlockState(any())).thenAnswer(call->((BlockPos)call.getArgument(0)).getY()<64
                ?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        return level;
    }
    @Test void completeDryOwnedFootprintIsAccepted() {assertNull(PerimeterConstruction.siteProblem(level(),plan(),p->true));}
    @Test void wholePerimeterReviewReportsTheSameOutsideNeighborAsNativeAdmission() {
        var level = level(); var plan = plan();
        BlockPos sand = new BlockPos(-1, 64, 8);
        assertFalse(PerimeterConstruction.reservedCells(plan).contains(sand));
        when(level.getBlockState(sand)).thenReturn(Blocks.SAND.defaultBlockState());
        String problem = PerimeterConstruction.siteProblem(level, plan, p -> true);
        assertNotNull(problem);
        assertTrue(problem.contains("minecraft:sand") && problem.contains(sand.toShortString()), problem);
        String nativeProblem = com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.placementNeighborhoodProblem(
                level, plan.blocks().keySet().stream().map(BlockPos::of).toList());
        assertEquals(nativeProblem, problem);
        verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
        verify(level, never()).destroyBlock(any(), anyBoolean(), any());
    }
    @Test void wholePerimeterReviewChecksNeighborChunksWithoutLoadingThem() {
        var level = level(); var plan = plan();
        when(level.hasChunkAt(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getX() >= 0);
        assertNotNull(PerimeterConstruction.siteProblem(level, plan, p -> true));
        verify(level, never()).getBlockState(argThat(pos -> pos.getX() < 0));
        verify(level, never()).hasNeighborSignal(any());
    }
    @Test void pairedPlantsInUnderDeckClearanceAreRejectedDuringTheFreeReview() {
        var plan = plan(); BlockPos cell = new BlockPos(2, 64, 8);
        assertTrue(plan.clearance().contains(cell.asLong()));
        assertFalse(plan.blocks().containsKey(cell.asLong()));
        for (var block : List.of(Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.SUNFLOWER, Blocks.WITHER_ROSE)) {
            var level = level(); when(level.getBlockState(cell)).thenReturn(block.defaultBlockState());
            String problem = PerimeterConstruction.siteProblem(level, plan, p -> true);
            assertNotNull(problem);
            assertTrue(problem.contains(cell.toShortString()), problem);
            verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
        }
        var level = level(); when(level.getBlockState(cell)).thenReturn(Blocks.DANDELION.defaultBlockState());
        assertNull(PerimeterConstruction.siteProblem(level, plan, p -> true));
    }
    @Test void completedCellsDoNotRequirePermissionToMutateTheirNeighborsAgain() {
        var plan = plan(); var level = level();
        BlockPos pending = new BlockPos(15, 64, 8), sand = new BlockPos(-1, 64, 8);
        assertTrue(plan.blocks().containsKey(pending.asLong()));
        // Replacing the existing terrain answer must not execute it with Mockito's null matcher.
        doAnswer(call -> {
            BlockPos p = call.getArgument(0);
            if (p.equals(sand)) return Blocks.SAND.defaultBlockState();
            if (p.equals(pending)) return Blocks.AIR.defaultBlockState();
            String id = plan.blocks().get(p.asLong());
            if (id != null) return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(new net.minecraft.resources.ResourceLocation(id)).defaultBlockState();
            return p.getY() < 64 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }).when(level).getBlockState(any());
        assertNull(PerimeterConstruction.siteProblem(level, plan, p -> true));
        verify(level, never()).hasNeighborSignal(new BlockPos(0, 64, 8));
    }
    @Test void reservationRetainsWalkwayHeadroomWithoutClaimingTheHollowCourtyard() {
        var plan = plan();
        var reserved = PerimeterConstruction.reservedCells(plan);
        var headroom = new BlockPos(2, 69, 8);
        assertTrue(plan.clearance().contains(headroom.asLong()));
        assertFalse(plan.blocks().containsKey(headroom.asLong()));
        assertTrue(reserved.contains(headroom));
        assertTrue(plan.blocks().keySet().stream().allMatch(cell -> reserved.contains(BlockPos.of(cell))));
        for (var column : plan.columns()) if (!column.parapet()) for (int y = 0; y < 3; y++) {
            BlockPos cavity = column.base().above(y);
            assertTrue(plan.clearance().contains(cavity.asLong())); assertTrue(reserved.contains(cavity));
            assertFalse(plan.blocks().containsKey(cavity.asLong()));
        }
        assertEquals(1320, reserved.size());
        assertFalse(reserved.contains(new BlockPos(8, 64, 8)));
        assertFalse(reserved.contains(new BlockPos(8, 69, 8)));
        assertThrows(UnsupportedOperationException.class, reserved::clear);
    }
    @Test void unclaimedInnerFootprintOrHeadroomFailsEntirePlan() {
        assertNotNull(PerimeterConstruction.siteProblem(level(),plan(),p->p.getX()!=4));
        assertNotNull(PerimeterConstruction.siteProblem(level(),plan(),p->p.getY()!=69));
    }
    @Test void allChunksAreCheckedBeforeReadingTerrain() {
        var level=level();when(level.hasChunkAt(any())).thenReturn(false);
        assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));verify(level,never()).getBlockState(any());
    }
    @Test void housesInventoriesFluidAndHazardsAreNeverSilentlyOmitted() {
        for(var block:List.of(Blocks.OAK_PLANKS,Blocks.CHEST,Blocks.WATER,Blocks.LAVA,Blocks.NETHER_PORTAL)) {
            var level=level();when(level.getBlockState(new BlockPos(2,68,2))).thenReturn(block.defaultBlockState());
            assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true),block.toString());
        }
    }
    @Test void existingBlocksInsideEveryBodyLayerRejectRatherThanExcavate() {
        for (int inward = 1; inward <= 3; inward++) for (int y = 64; y < 67; y++) {
            BlockPos cavity = new BlockPos(inward, y, 7);
            assertTrue(plan().clearance().contains(cavity.asLong()));
            for (var block : List.of(Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.CHEST, Blocks.WATER)) {
                var level = level(); when(level.getBlockState(cavity)).thenReturn(block.defaultBlockState());
                assertNotNull(PerimeterConstruction.siteProblem(level, plan(), p -> true));
                verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
            }
        }
    }
    @Test void occupiedBuildCellsAndUnsafeGroundRejectCommission() {
        var level=level();when(level.getEntities(isNull(Entity.class),any(AABB.class),any())).thenReturn(List.of(mock(Entity.class)));
        assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));
        level=level();when(level.getBlockState(new BlockPos(0,63,0))).thenReturn(Blocks.WATER.defaultBlockState());
        assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));
    }
    @Test void anotherReservedNativeAreaBlocksTheWholeOverlappingPlan() {
        var level=level();var area=mock(Entity.class);var tag=new CompoundTag();
        tag.putLong(PerimeterConstruction.SITE_MIN,new BlockPos(4,64,4).asLong());
        tag.putLong(PerimeterConstruction.SITE_MAX,new BlockPos(8,69,8).asLong());
        when(area.getPersistentData()).thenReturn(tag);when(level.getEntitiesOfClass(eq(Entity.class),any(),any())).thenReturn(List.of(area));
        when(level.getAllEntities()).thenReturn(List.of(area)); when(area.isAlive()).thenReturn(true);
        try(var bridge=mockStatic(WorkersBridge.class)) {bridge.when(()->WorkersBridge.isBuildArea(area)).thenReturn(true);
            assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));}
    }
    @Test void pendingIndexMakesNativeScanLinearButKeepsItsEnvelopeBounded() {
        assertTrue(PerimeterConstruction.nativeScanWithinBudget(plan()));
        var chunks=new java.util.HashSet<ChunkPos>();
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)chunks.add(new ChunkPos(x,z));
        var large=PerimeterBlueprint.create(chunks,(x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE);
        assertTrue(large.valid());assertTrue(PerimeterConstruction.nativeScanWithinBudget(large));
        var oversized = new PerimeterBlueprint.Plan(large.blocks(), large.columns(), large.clearance(),
                BlockPos.ZERO, new BlockPos(511, 32, 511), large.runs(), large.connections(), large.materialCounts(), List.of());
        assertFalse(PerimeterConstruction.nativeScanWithinBudget(oversized));
    }
    @Test void materialsCountSharedCornerCellsOnlyOnce() {
        assertEquals(572,plan().blocks().size());assertTrue(PerimeterConstruction.materials(plan()).contains("352 cobblestone"));
        assertTrue(PerimeterConstruction.materials(plan()).contains("220 oak planks"));
    }
}
