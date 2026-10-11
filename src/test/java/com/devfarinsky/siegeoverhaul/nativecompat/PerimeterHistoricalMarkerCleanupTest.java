package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real controller tick, project store, journal, ledger and historical snapshot gate. The completed
 * section/receipt and split-save marker are synthetic fixtures; world entities and native storage
 * cleanup are mocked. These tests do not claim native placement, save ordering or gameplay coverage.
 */
class PerimeterHistoricalMarkerCleanupTest extends MinecraftTestSupport {
    private static final String SNAPSHOT = "SiegeProtectedConstructionV1";

    @Test void canceledEditedHistoricalMarkerRetiresOnceAfterCoreAndLedgerReloadWithoutRestoringTerrain() {
        try (var f = new Fixture()) {
            assertNotEquals(f.area.reservedBuilderId(), f.ledger.builderForArea(f.project, f.area.getUUID()),
                    "The historical fallback, rather than the current coordinator shortcut, must be exercised");
            assertFalse(NativeConstructionGuard.matchesVerifiedCleanupSnapshot(f.area, f.beforeCancellation),
                    "A later player edit must still deny the noncanceled completed-stage path");
            assertNotNull(PerimeterProjectAuthority.problem(f.level, f.worker, f.area, true, true),
                    "Cancellation never restores native construction authority");
            f.reload();
            var snapshotBefore = f.areaData.copy();
            var handBefore = f.workerData.getCompound(ProtectedBuilderHandLifecycle.KEY).copy();
            var assignmentsBefore = f.ledger.save(new CompoundTag()).getCompound("BuilderAssignments").copy();
            var generation = f.ledger.generation();

            f.tick();

            var terminal = PerimeterProjectStore.terminal(f.core, f.project.header().projectId());
            assertNotNull(terminal, "Exact canceled historical marker must not remain permanently reserved after a player edit");
            assertNull(PerimeterProjectStore.get(f.core, f.project.header().projectId()));
            assertEquals(PerimeterProject.State.CANCELED, terminal.state());
            assertEquals(f.project.header().owner(), terminal.owner());
            assertEquals(f.project.header().builder(), terminal.builder());
            assertEquals(f.project.manifestHash(), terminal.manifestHash());
            assertEquals(1, terminal.verifiedStages());
            assertEquals(f.project.payment(), terminal.payment());
            assertEquals(generation, terminal.cleanup().ledgerGeneration());
            assertEquals(snapshotBefore, f.areaData, "Historical owner, coordinator and accepted baseline must not be rewritten");
            assertEquals(assignmentsBefore, f.ledger.save(new CompoundTag()).getCompound("BuilderAssignments"));
            assertEquals(f.promoted, f.ledger.assignedTerminalBuilder(terminal));
            assertTrue(f.ledger.terminalBuilderAreaKnown(terminal, f.project.header().builder(), f.area.getUUID()));
            assertFalse(f.ledger.contains(f.project.header().projectId()));
            assertFalse(f.ledger.reserves(f.reservedCells()));
            assertFalse(PerimeterProjectLink.reserved(f.worker));
            assertEquals(handBefore, f.workerData.getCompound(ProtectedBuilderHandLifecycle.KEY));
            assertTrue(ProtectedBuilderHandLifecycle.matches(f.workerData, f.promoted, f.ledger));
            assertFalse(f.entities.containsKey(f.area.getUUID()));
            assertEquals(1, f.removalChecks, "Edit and ownership evidence must still exist when marker removal is authorized");
            f.assertWorldInventoryAndPaymentUnchanged();

            var compactCore = f.core.copy(); var compactLedger = f.ledger.save(new CompoundTag());
            f.reload(); f.tick(); f.tick();
            assertEquals(compactCore, f.core); assertEquals(compactLedger, f.ledger.save(new CompoundTag()));
            verify(f.area, times(1)).removeAuthorized();
            f.storage.verify(() -> ProtectedStorageAccess.drainCleanup(f.worker), times(1));
            f.assertWorldInventoryAndPaymentUnchanged();
        }
    }

    @Test void unverifiedCanceledMarkerCannotUseSavedCompletedCellsAsCompletionProof() {
        try (var f = new Fixture(false)) {
            assertTrue(f.project.receipts().isEmpty());
            assertTrue(f.areaData.getCompound(SNAPSHOT).getLongArray("Completed").length > 0,
                    "Marker progress alone must not authorize historical cleanup");
            f.assertRejectedWithoutCleanup();
        }
    }

    @Test void changedNativeOwnerOrSavedOwnerCannotRemoveTheHistoricalMarker() {
        assertRejected(List.of(
                f -> f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(UUID.randomUUID()),
                f -> f.editSnapshot(tag -> tag.putUUID("Owner", UUID.randomUUID()))));
    }

    @Test void changedNativeRecipeOrCoherentlyChangedSavedRecipeCannotReplaceThePaidManifest() {
        assertRejected(List.of(
                f -> f.nativeRecipe = changedRecipe(f.nativeRecipe),
                f -> {
                    f.nativeRecipe = changedRecipe(f.nativeRecipe);
                    f.editSnapshot(tag -> tag.put("Structure", f.nativeRecipe.copy()));
                },
                f -> f.editSnapshot(tag -> {
                    var cell = tag.getList("Before", Tag.TAG_COMPOUND).getCompound(0);
                    cell.put("State", NbtUtils.writeBlockState(f.project.targets().get(cell.getLong("Pos"))));
                })));

    }

    @Test void wrongHistoricalCoordinatorOrProjectScopeCannotRemoveTheMarker() {
        assertRejected(List.of(
                f -> when(f.area.reservedBuilderId()).thenReturn(UUID.randomUUID()),
                f -> f.editSnapshot(tag -> tag.putUUID("Builder", UUID.randomUUID())),
                f -> f.areaData.getCompound(PerimeterProjectAuthority.KEY)
                        .putLong("Generation", f.project.header().generation() + 1),
                f -> f.areaData.getCompound(PerimeterProjectAuthority.KEY).putString("StageHash", "b".repeat(64))));
    }

    @Test void movedMarkerChangedOriginOrChangedEnvelopeCannotRemoveTheMarker() {
        assertRejected(List.of(
                f -> when(f.area.blockPosition()).thenReturn(f.marker.east()),
                f -> when(f.area.getOriginPos()).thenReturn(f.project.active().layout().origin().east()),
                f -> when(f.area.getWidthSize()).thenReturn(f.project.active().layout().width() + 1)));
    }

    @Test void missingWrongIdentityOrOutOfOrderJournalCannotAuthorizeCleanup() {
        assertRejected(List.of(
                f -> f.core.remove(PerimeterStageJournal.KEY),
                f -> f.journalEntry().putUUID("Id", UUID.randomUUID()),
                f -> f.journalEntry().putLong("Generation", f.project.header().generation() + 1),
                f -> f.journalEntry().putString("Hash", "b".repeat(64)),
                f -> f.journalAttempt().putUUID("Area", f.project.stages().get(1).areaId()),
                f -> f.journalAttempt().putLong("Marker", f.marker.east().asLong()),
                f -> f.journalAttempt().putInt("Index", 1),
                f -> {
                    f.journalAttempt().putString("State", "RETIRED");
                    var ahead = new CompoundTag(); ahead.putInt("Index", 1);
                    ahead.putUUID("Area", f.project.stages().get(1).areaId()); ahead.putLong("Marker", f.marker.asLong());
                    ahead.putString("State", "LIVE"); f.journalEntry().getList("Attempts", Tag.TAG_COMPOUND).add(ahead);
                },
                f -> f.journalEntry().put("Attempts", new ListTag())));
    }

    @Test void unloadedOrNontickingMarkerChunkRetainsEvidenceThenRetriesSuccessfully() {
        for (int unavailable = 0; unavailable < 3; unavailable++) try (var f = new Fixture()) {
            switch (unavailable) {
                case 0 -> when(f.level.hasChunkAt(f.marker)).thenReturn(false);
                case 1 -> when(f.level.areEntitiesLoaded(anyLong())).thenReturn(false);
                default -> when(f.chunks.isPositionTicking(anyLong())).thenReturn(false);
            }
            f.assertRejectedWithoutCleanup();
            when(f.level.hasChunkAt(f.marker)).thenReturn(true);
            when(f.level.areEntitiesLoaded(anyLong())).thenReturn(true);
            when(f.chunks.isPositionTicking(anyLong())).thenReturn(true);
            f.tick();
            assertNotNull(PerimeterProjectStore.terminal(f.core, f.project.header().projectId()));
            verify(f.area, times(1)).removeAuthorized(); f.assertWorldInventoryAndPaymentUnchanged();
        }
    }

    @Test void unfinishedWorkerCleanupCannotRemoveMarkerOrEraseEditEvidenceAndCanRetry() {
        try (var f = new Fixture()) {
            f.storage.when(() -> ProtectedStorageAccess.drainCleanup(f.worker)).thenReturn(false);
            var coreBefore = f.core.copy(); var ledgerBefore = f.ledger.save(new CompoundTag());
            var markerBefore = f.areaData.copy();
            f.tick(); f.tick();
            assertEquals(coreBefore, f.core); assertEquals(ledgerBefore, f.ledger.save(new CompoundTag()));
            assertEquals(markerBefore, f.areaData); assertTrue(f.ledger.edited(f.project.header().projectId()));
            assertTrue(PerimeterProjectLink.matches(f.worker, f.project, f.promoted));
            assertNull(PerimeterProjectStore.terminal(f.core, f.project.header().projectId()));
            verify(f.area, never()).removeAuthorized();
            f.bridge.verify(() -> WorkersBridge.detachBuildAreaReference(any(), any()), never());
            f.assertWorldInventoryAndPaymentUnchanged();
            f.storage.when(() -> ProtectedStorageAccess.drainCleanup(f.worker)).thenReturn(true);
            f.tick();
            assertNotNull(PerimeterProjectStore.terminal(f.core, f.project.header().projectId()));
            verify(f.area, times(1)).removeAuthorized(); f.assertWorldInventoryAndPaymentUnchanged();
        }
    }

    private static void assertRejected(List<Consumer<Fixture>> alterations) {
        for (var alter : alterations) try (var f = new Fixture()) {
            alter.accept(f); f.assertRejectedWithoutCleanup();
        }
    }

    private static CompoundTag changedRecipe(CompoundTag original) {
        var changed = original.copy();
        changed.getList("blocks", Tag.TAG_COMPOUND).getCompound(0)
                .put("state", NbtUtils.writeBlockState(Blocks.STONE_BRICKS.defaultBlockState()));
        return changed;
    }

    private static final class Fixture implements AutoCloseable {
        final PerimeterProject beforeCancellation, project;
        final UUID promoted = UUID.randomUUID();
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final ServerChunkCache chunks = mock(ServerChunkCache.class);
        final BuilderEntity worker = mock(BuilderEntity.class);
        final ProtectedBuildArea area = mock(ProtectedBuildArea.class);
        final CompoundTag workerData = new CompoundTag(), areaData = new CompoundTag();
        final BlockPos marker = new BlockPos(8, 64, 8), playerEdit;
        final Map<UUID, Entity> entities = new HashMap<>();
        final Map<Long, BlockState> world = new HashMap<>(), worldBefore;
        final SimpleContainer inventory = new SimpleContainer(12);
        final NeededItem request = new NeededItem(stack -> stack.is(Items.COBBLESTONE), 11, true);
        final List<NeededItem> requests = new ArrayList<>(List.of(request));
        final List<CompoundTag> inventoryBefore;
        final RaidSavedData data = new RaidSavedData();
        CompoundTag core = new CompoundTag(), nativeRecipe;
        ConstructionEditLedger ledger = new ConstructionEditLedger();
        int removalChecks;
        final MockedStatic<RaidSavedData> saves = mockStatic(RaidSavedData.class);
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class,
                invocation -> invocation.getMethod().getName().equals("get") ? ledger : CALLS_REAL_METHODS.answer(invocation));
        final MockedStatic<WorkersBridge> bridge = mockStatic(WorkersBridge.class);
        final MockedStatic<ProtectedStorageAccess> storage = mockStatic(ProtectedStorageAccess.class);

        Fixture() { this(true); }
        Fixture(boolean verifiedCompletion) {
            var prepared = ConstructionProjectLedgerTest.project(); var header = prepared.header();
            core.putLong("BankEmeralds", 1000); data.siegeCores.put(header.coreKey(), core);
            PerimeterProjectStore.prepare(core, prepared, () -> {});
            PerimeterStageJournal.prepare(core, prepared, () -> {});
            assertTrue(ledger.registerProject(prepared)); assertTrue(ledger.leaseProjectStage(prepared));
            assertNotNull(PerimeterStageJournal.begin(core, prepared, marker, () -> {}));
            PerimeterStageJournal.live(core, prepared, () -> {});
            var paid = PerimeterProjectStore.consumeOnce(core, header.projectId(), prepared.manifestHash(), 64, false, () -> {}).project();
            var running = PerimeterProjectStore.replace(core, paid.check(), paid.activate(paid.check()), () -> {});
            assertTrue(ledger.enlistCrew(running, promoted)); assertTrue(ledger.activateCrewMember(running, promoted));
            beforeCancellation = verifiedCompletion ? PerimeterProjectStore.replace(core, running.check(),
                    running.verifyStage(running.check(), running.expectedStageReceipt()), () -> {}) : running;
            // The missing-receipt negative transfers while the lease is still active, then retires
            // it; an unverified retired lease cannot itself authorize coordinator promotion.
            if (verifiedCompletion) ledger.retire(beforeCancellation.active().areaId(), true);
            assertTrue(ledger.transferCrewCoordinator(beforeCancellation, header.builder(), promoted, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
            if (!verifiedCompletion) ledger.retire(beforeCancellation.active().areaId(), true);

            when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
            when(server.getAllLevels()).thenReturn(List.of(level)); when(server.isSameThread()).thenReturn(true);
            when(server.getTickCount()).thenReturn(20);
            when(level.hasChunkAt(any())).thenReturn(true); when(level.areEntitiesLoaded(anyLong())).thenReturn(true);
            when(level.getChunkSource()).thenReturn(chunks); when(chunks.isPositionTicking(anyLong())).thenReturn(true);
            when(level.getEntity(any(UUID.class))).thenAnswer(i -> entities.get(i.getArgument(0)));
            world.putAll(prepared.before()); world.putAll(prepared.clearanceBefore());
            when(level.getBlockState(any())).thenAnswer(i -> world.getOrDefault(((BlockPos) i.getArgument(0)).asLong(), Blocks.AIR.defaultBlockState()));
            doAnswer(i -> { world.put(((BlockPos) i.getArgument(0)).asLong(), i.getArgument(1)); return true; })
                    .when(level).setBlock(any(), any(), anyInt());
            doAnswer(i -> { world.put(((BlockPos) i.getArgument(0)).asLong(), i.getArgument(1)); return true; })
                    .when(level).setBlock(any(), any(), anyInt(), anyInt());

            var layout = beforeCancellation.active().layout();
            nativeRecipe = TerritoryFortification.blueprint(layout.targets(), layout.min(), layout.max());
            var plan = AcceptedConstructionPlan.decode(layout.origin(), Direction.SOUTH, layout.width(), layout.depth(), layout.height(), nativeRecipe);
            var snapshot = plan.save(); snapshot.putUUID("Owner", header.owner()); snapshot.putUUID("Builder", header.builder());
            snapshot.putString("CoreKey", header.coreKey()); snapshot.putLong("CorePos", header.originalCore().asLong());
            var before = new ListTag();
            layout.targets().keySet().forEach(pos -> {
                var cell = new CompoundTag(); cell.putLong("Pos", pos);
                cell.put("State", NbtUtils.writeBlockState(prepared.before().get(pos))); before.add(cell);
            });
            snapshot.put("Before", before);
            snapshot.put("Reservation", AcceptedConstructionReservation.capture(level, plan,
                    layout.reservation().stream().map(BlockPos::of).toList()).save());
            snapshot.putLongArray("Completed", layout.targets().keySet().stream().mapToLong(Long::longValue).toArray());
            snapshot.putLongArray("Cleared", new long[0]); areaData.put(SNAPSHOT, snapshot);
            when(area.level()).thenReturn(level); when(area.getUUID()).thenReturn(beforeCancellation.active().areaId());
            when(area.getPersistentData()).thenReturn(areaData); when(area.blockPosition()).thenReturn(marker);
            when(area.reservedBuilderId()).thenReturn(header.builder()); when(area.getOriginPos()).thenReturn(layout.origin());
            when(area.getFacing()).thenReturn(Direction.SOUTH); when(area.getWidthSize()).thenReturn(layout.width());
            when(area.getDepthSize()).thenReturn(layout.depth()); when(area.getHeightSize()).thenReturn(layout.height());
            when(area.getStructureNBT()).thenAnswer(i -> nativeRecipe.copy());
            PerimeterProjectAuthority.stamp(area, beforeCancellation);
            bridge.when(() -> WorkersBridge.readOwner(area)).thenReturn(header.owner());
            entities.put(area.getUUID(), area);

            layout.targets().keySet().forEach(pos -> world.put(pos, prepared.targets().get(pos)));
            playerEdit = BlockPos.of(layout.targets().keySet().iterator().next());
            world.put(playerEdit.asLong(), Blocks.GOLD_BLOCK.defaultBlockState()); ledger.record(playerEdit);
            project = PerimeterProjectStore.replace(core, beforeCancellation.check(),
                    beforeCancellation.cancel(beforeCancellation.check(), "Owner canceled after editing a completed section"), () -> {});
            assertTrue(ledger.edited(header.projectId())); assertFalse(ledger.matchesProjectLease(project));
            assertFalse(ledger.verifiedRetiredProjectStage(project, area.getUUID()));

            when(worker.level()).thenReturn(level); when(worker.getUUID()).thenReturn(promoted);
            when(worker.isAlive()).thenReturn(true); when(worker.getPersistentData()).thenReturn(workerData);
            when(worker.getUseItem()).thenReturn(ItemStack.EMPTY); when(worker.getInventory()).thenReturn(inventory);
            // The promoted worker already detached at DONE. Its saved whole-project link still
            // requires ordinary live cleanup before the controller may remove the returned marker.
            PerimeterProjectLink.set(worker, project, promoted);
            assertTrue(PerimeterProjectLink.bindLedgerGeneration(worker, ledger.generation()));
            assertTrue(ledger.retainHandLifecycle(workerData, promoted, header.owner(), area.getUUID()));
            worker.neededItems = requests; inventory.setItem(5, new ItemStack(Items.COBBLESTONE, 23));
            inventory.getItem(5).getOrCreateTag().putString("Origin", "historical-cleanup-fixture");
            inventory.setItem(6, new ItemStack(Items.BREAD, 7)); entities.put(promoted, worker);
            bridge.when(WorkersBridge::available).thenReturn(true);
            bridge.when(() -> WorkersBridge.isBuilder(worker)).thenReturn(true);
            bridge.when(() -> WorkersBridge.readWorkerOwner(worker)).thenReturn(header.owner());
            bridge.when(() -> WorkersBridge.detachBuildAreaReference(eq(worker), any())).thenReturn(true);
            storage.when(() -> ProtectedStorageAccess.drainCleanup(worker)).thenReturn(true);
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            doAnswer(i -> {
                assertTrue(ledger.edited(header.projectId()), "Marker cleanup must not clear later player-edit evidence");
                assertEquals(header.owner(), areaData.getCompound(SNAPSHOT).getUUID("Owner"));
                assertEquals(header.builder(), areaData.getCompound(SNAPSHOT).getUUID("Builder"));
                assertTrue(ledger.projectBuilderAreaKnown(project, header.builder(), area.getUUID()));
                removalChecks++; entities.remove(area.getUUID()); return null;
            }).when(area).removeAuthorized();
            worldBefore = Map.copyOf(world); inventoryBefore = inventorySnapshot(); reload();
        }

        void tick() { NativePerimeterProjects.tick(server); }
        void reload() { core = core.copy(); data.siegeCores.put(project.header().coreKey(), core); ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag())); }
        Set<BlockPos> reservedCells() { return project.reservation().stream().map(BlockPos::of).collect(java.util.stream.Collectors.toSet()); }
        CompoundTag journalEntry() { return core.getCompound(PerimeterStageJournal.KEY).getList("Entries", Tag.TAG_COMPOUND).getCompound(0); }
        CompoundTag journalAttempt() { return journalEntry().getList("Attempts", Tag.TAG_COMPOUND).getCompound(0); }
        void editSnapshot(Consumer<CompoundTag> edit) { var copy = areaData.getCompound(SNAPSHOT).copy(); edit.accept(copy); areaData.put(SNAPSHOT, copy); }
        List<CompoundTag> inventorySnapshot() {
            var result = new ArrayList<CompoundTag>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) result.add(inventory.getItem(slot).save(new CompoundTag()));
            return result;
        }
        void assertRejectedWithoutCleanup() {
            var coreBefore = core.copy(); var ledgerBefore = ledger.save(new CompoundTag());
            var markerBefore = areaData.copy(); var workerBefore = workerData.copy(); ledger.setDirty(false);
            tick(); tick();
            assertEquals(coreBefore, core); assertEquals(ledgerBefore, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty());
            assertEquals(markerBefore, areaData); assertEquals(workerBefore, workerData);
            assertTrue(ledger.edited(project.header().projectId())); assertTrue(ledger.retired(area.getUUID()));
            assertTrue(ledger.reserves(reservedCells())); assertEquals(promoted, ledger.assignedBuilder(project));
            assertNull(PerimeterProjectStore.terminal(core, project.header().projectId()));
            assertSame(area, entities.get(area.getUUID())); verify(area, never()).removeAuthorized();
            storage.verifyNoInteractions(); bridge.verify(() -> WorkersBridge.detachBuildAreaReference(any(), any()), never());
            assertWorldInventoryAndPaymentUnchanged();
        }
        void assertWorldInventoryAndPaymentUnchanged() {
            assertEquals(worldBefore, world, "Cleanup must leave completed blocks, player edits, clearance and future-stage baseline unchanged");
            assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), world.get(playerEdit.asLong()));
            assertTrue(mockingDetails(level).getInvocations().stream().noneMatch(i -> Set.of(
                    "setBlock", "setBlockAndUpdate", "removeBlock", "destroyBlock", "setBlockEntity", "removeBlockEntity")
                    .contains(i.getMethod().getName())), "No terrain/block-entity write of any overload is permitted");
            assertEquals(inventoryBefore, inventorySnapshot()); assertSame(requests, worker.neededItems);
            assertEquals(List.of(request), worker.neededItems); assertEquals(11, request.count); assertTrue(request.required);
            assertNull(request.sourceKey); verify(level, never()).addFreshEntity(any());
            verify(worker, never()).getInventory(); verify(worker, never()).setItemInHand(any(), any());
            verify(worker, never()).setItemSlot(any(), any()); verify(worker, never()).stopUsingItem();
            verify(area, never()).rebindBuilder(any(), any()); verify(area, never()).setStartBuild(anyBoolean());
            verify(area, never()).setStructureNBT(any()); verify(worker, never()).remove(any());
            assertEquals(936, FactionBank.balance(core)); assertArrayEquals(new int[]{-64}, FactionBank.ledgerDeltas(core));
        }
        @Override public void close() {
            PerimeterProjectAuthority.stopped(server); storage.close(); bridge.close(); ledgers.close(); saves.close();
        }
    }
}
