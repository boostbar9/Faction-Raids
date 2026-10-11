package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PlayerFortificationJobs;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.devfarinsky.siegeoverhaul.core.WallBuilderAccess;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Synthetic saved admission prefixes with real resume, snapshot validation, project store, ledger,
 * selectors and hand lifecycle/mirror code. Native installation, assignment, storage cleanup and
 * world/entity access are mocked. Reload round-trips the persistent records, not Forge entity
 * construction or native AI. Cancellation checks helper-stage detachment, not terminal compaction.
 * No native placement, gameplay, physical disk ordering or restart coverage is claimed.
 */
class PerimeterPendingCrewReplayTest extends MinecraftTestSupport {
    private static final String AREA = "SiegeProtectedAreaReceipt", GENERATION = "SiegeProtectedLedgerGeneration",
            SNAPSHOT = "SiegeProtectedConstructionV1";

    private enum Prefix {
        PENDING, WHOLE_SELECTOR, LEDGER_GENERATION, CHILD_RECEIPT_AND_MIRROR,
        HAND_PROOF, NATIVE_METADATA, NATIVE_ASSIGNMENT, ACTIVE;
        boolean includes(Prefix other) { return ordinal() >= other.ordinal(); }
    }

    @Test void everyPersistedPrefixReplaysRealAdmissionOnceAndThenBecomesAnExactNoOp() throws Exception {
        for (Prefix prefix : Prefix.values()) try (var f = new Fixture()) {
            f.admitThrough(prefix); f.reload();
            var coreBefore = f.core.copy(); var markerBefore = f.areaData.copy();
            var handBefore = f.ledger.handLifecycle(f.helperId);
            assertEquals(prefix == Prefix.ACTIVE, f.ledger.crewMember(f.project, f.helperId), prefix.name());
            assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project), prefix.name());
            f.assertActive();
            assertEquals(1, f.handBindings, "Exactly one value-preserving mirror rebind: " + prefix);
            assertEquals(prefix.includes(Prefix.NATIVE_ASSIGNMENT) ? 0 : 1, f.assignments, prefix.name());
            if (handBefore != null) assertEquals(handBefore, f.ledger.handLifecycle(f.helperId));
            assertEquals(coreBefore, f.core); assertEquals(markerBefore, f.areaData); f.assertResourcesUnchanged();

            var workerBefore = f.workerData.copy(); var ledgerBefore = f.ledger.save(new CompoundTag());
            int assignments = f.assignments, handBindings = f.handBindings;
            f.reload(); f.ledger.setDirty(false);
            // An already active exact association must not rerun strict admission or native setup.
            f.storage.when(() -> ProtectedStorageAccess.runningProblem(f.worker)).thenReturn("Synthetic native resupply in progress");
            f.storage.clearInvocations(); f.hooks.clearInvocations();
            assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project), prefix.name());
            assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project), prefix.name());
            assertEquals(workerBefore, f.workerData); assertEquals(ledgerBefore, f.ledger.save(new CompoundTag()));
            assertFalse(f.ledger.isDirty()); assertEquals(assignments, f.assignments); assertEquals(handBindings, f.handBindings);
            f.storage.verifyNoInteractions(); f.hooks.verifyNoInteractions();
            assertEquals(coreBefore, f.core); assertEquals(markerBefore, f.areaData); f.assertResourcesUnchanged();
        }
    }

    @Test void controllerKeepsUnloadedPendingPrefixesAndReplaysTheSameAdmissionWhenTheActorReturns() throws Exception {
        for (Prefix prefix : Prefix.values()) if (prefix != Prefix.ACTIVE) try (var f = new Fixture()) {
            f.admitThrough(prefix); f.reload(); f.helperLoaded = false;
            var ledgerBefore = f.ledger.save(new CompoundTag()); var workerBefore = f.workerData.copy();
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(workerBefore, f.workerData);
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId));
            assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId));
            f.helperLoaded = true;
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            f.assertActive(); assertEquals(1, f.ledger.reservedCrewMembers(f.project).size());
            f.assertResourcesUnchanged();
        }
    }

    @Test void cancellationDetachesEveryPrefixWithoutPaymentRefundManifestRewriteOrMaterialLoss() throws Exception {
        for (Prefix prefix : Prefix.values()) try (var f = new Fixture()) {
            f.admitThrough(prefix); f.cancel(); f.reload();
            var coreBefore = f.core.copy(); var markerBefore = f.areaData.copy();
            var handBefore = f.ledger.handLifecycle(f.helperId);
            assertTrue(NativePerimeterCrew.retireStage(f.level, f.project, f.area.getUUID()), prefix.name());
            assertNull(NativeConstructionGuard.currentArea(f.worker));
            assertFalse(f.workerData.contains(AREA)); assertFalse(f.workerData.contains(GENERATION));
            assertFalse(f.workerData.contains(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            assertFalse(f.workerData.contains(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER));
            assertFalse(f.ledger.pendingCrewMember(f.project, f.helperId));
            assertTrue(f.ledger.knownCrewMember(f.project, f.helperId));
            assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId));
            if (prefix == Prefix.ACTIVE) {
                // Stage detachment retains the active helper's whole-project identity for the outer
                // terminal cleanup controller. This test does not pretend it compacted the project.
                assertTrue(PerimeterProjectLink.reserved(f.worker));
                assertTrue(f.ledger.crewMember(f.project, f.helperId));
            } else {
                assertFalse(PerimeterProjectLink.reserved(f.worker));
                assertTrue(f.ledger.reservedCrewMembers(f.project).isEmpty());
                assertFalse(f.savedMember().getBoolean("Ready")); assertTrue(f.savedMember().getBoolean("Withdrawn"));
                assertFalse(f.ledger.activateCrewMember(f.project, f.helperId));
            }
            if (prefix.includes(Prefix.CHILD_RECEIPT_AND_MIRROR)) {
                assertTrue(ProtectedBuilderHandLifecycle.matches(f.workerData, f.helperId, f.ledger));
                assertEquals(1, f.handBindings);
            } else {
                assertNull(f.ledger.handLifecycle(f.helperId)); assertEquals(0, f.handBindings);
            }
            if (handBefore != null) assertEquals(handBefore, f.ledger.handLifecycle(f.helperId));
            assertTrue(f.ledger.matchesProjectIdentity(f.project)); assertTrue(f.ledger.contains(f.project.header().projectId()));
            assertEquals(coreBefore, f.core); assertEquals(markerBefore, f.areaData); f.assertResourcesUnchanged();

            var ledgerBefore = f.ledger.save(new CompoundTag()); var workerBefore = f.workerData.copy();
            int detachments = f.detachments, handBindings = f.handBindings;
            f.reload(); f.ledger.setDirty(false);
            assertTrue(NativePerimeterCrew.retireStage(f.level, f.project, f.area.getUUID()), prefix.name());
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area); // Canceled projects cannot admit helpers.
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(workerBefore, f.workerData);
            assertFalse(f.ledger.isDirty()); assertEquals(detachments, f.detachments); assertEquals(handBindings, f.handBindings);
            assertEquals(coreBefore, f.core); assertEquals(markerBefore, f.areaData); f.assertResourcesUnchanged();
        }
    }

    @Test void wrongOwnerAreaProjectOrLedgerGenerationCannotReplayPendingOrActiveIdentity() throws Exception {
        List<Consumer<Fixture>> conflicts = List.of(
                f -> when(f.owner.getUUID()).thenReturn(UUID.randomUUID()),
                f -> f.workers.when(() -> WorkersBridge.readWorkerOwner(f.worker)).thenReturn(UUID.randomUUID()),
                f -> when(f.area.getUUID()).thenReturn(UUID.randomUUID()),
                f -> f.workerData.putUUID(AREA, f.project.stages().get(1).areaId()),
                f -> f.workerData.putUUID(GENERATION, UUID.randomUUID()),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration", UUID.randomUUID()),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putLong("Generation", f.project.header().generation() + 1),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("Project", UUID.randomUUID()),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putString("Hash", "b".repeat(64)),
                f -> f.workerData.getCompound(ProtectedBuilderHandLifecycle.KEY).putUUID("Generation", UUID.randomUUID()),
                f -> f.areaData.getCompound(SNAPSHOT).putUUID("Owner", UUID.randomUUID()),
                f -> {
                    var foreign = mock(ProtectedBuildArea.class); when(foreign.getUUID()).thenReturn(UUID.randomUUID());
                    f.worker.currentBuildArea = foreign;
                });
        for (Prefix prefix : List.of(Prefix.NATIVE_ASSIGNMENT, Prefix.ACTIVE))
            for (var conflict : conflicts) try (var f = new Fixture()) {
                f.admitThrough(prefix); conflict.accept(f); f.reload();
                var ledgerBefore = f.ledger.save(new CompoundTag()); var workerBefore = f.workerData.copy();
                var coreBefore = f.core.copy(); var markerBefore = f.areaData.copy(); f.ledger.setDirty(false);
                assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
                assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(workerBefore, f.workerData);
                assertFalse(f.ledger.isDirty()); assertEquals(0, f.assignments); assertEquals(1, f.handBindings);
                assertEquals(coreBefore, f.core); assertEquals(markerBefore, f.areaData); f.assertResourcesUnchanged();
            }
    }

    @Test void failedNativeAssignmentPersistsOneHandProofAndReplaysWithoutAnotherRebindOrCharge() throws Exception {
        try (var f = new Fixture()) {
            f.admitThrough(Prefix.PENDING); f.assignmentAccepted = false;
            assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId)); assertFalse(f.ledger.crewMember(f.project, f.helperId));
            assertNull(NativeConstructionGuard.currentArea(f.worker));
            assertEquals(1, f.handBindings); var receipt = f.ledger.handLifecycle(f.helperId); assertNotNull(receipt);
            f.reload(); f.assignmentAccepted = true;
            assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
            f.assertActive(); assertEquals(receipt, f.ledger.handLifecycle(f.helperId));
            assertEquals(1, f.handBindings); assertEquals(2, f.assignments); f.assertResourcesUnchanged();
        }
    }

    @Test void unequalHandMirrorCannotFinishAdmissionOrCancellationAndNeverChoosesAnItemAmount() throws Exception {
        try (var f = new Fixture()) {
            f.admitThrough(Prefix.CHILD_RECEIPT_AND_MIRROR); f.mainHand.setCount(22); f.reload();
            var ledgerBefore = f.ledger.save(new CompoundTag()); var inventoryBefore = f.inventorySnapshot();
            var handBefore = f.mainHand.save(new CompoundTag()); var coreBefore = f.core.copy();
            assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(f.workerData));
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(coreBefore, f.core);
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId)); assertNull(f.ledger.handLifecycle(f.helperId));
            assertEquals(0, f.handBindings); assertEquals(0, f.assignments);
            f.cancel(); f.reload(); var canceledCore = f.core.copy();
            assertFalse(NativePerimeterCrew.retireStage(f.level, f.project, f.area.getUUID()));
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(canceledCore, f.core);
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(f.workerData));
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId)); assertEquals(0, f.detachments);
            assertEquals(inventoryBefore, f.inventorySnapshot()); assertEquals(handBefore, f.mainHand.save(new CompoundTag()));
            assertEquals(936, f.core.getLong("BankEmeralds")); assertEquals(0, f.handBindings);
            verify(f.worker, never()).setItemInHand(any(), any()); verify(f.worker, never()).setItemSlot(any(), any());
        }
    }

    @Test void unfinishedCleanupRetainsThePendingReservationAndRetriesCancellationAfterReload() throws Exception {
        try (var f = new Fixture()) {
            f.admitThrough(Prefix.NATIVE_ASSIGNMENT); f.cancel(); f.reload();
            var ledgerBefore = f.ledger.save(new CompoundTag()); var coreBefore = f.core.copy();
            f.storage.when(() -> ProtectedStorageAccess.drainCleanup(f.worker)).thenReturn(false);
            assertFalse(NativePerimeterCrew.retireStage(f.level, f.project, f.area.getUUID()));
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(coreBefore, f.core);
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId)); assertEquals(0, f.detachments);
            assertTrue(PerimeterProjectLink.matches(f.worker, f.project, f.helperId));
            assertEquals(f.area.getUUID(), NativeConstructionGuard.protectedReceiptArea(f.worker));
            assertSame(f.area, NativeConstructionGuard.currentArea(f.worker)); f.assertResourcesUnchanged();
            f.reload(); f.storage.when(() -> ProtectedStorageAccess.drainCleanup(f.worker)).thenReturn(true);
            assertTrue(NativePerimeterCrew.retireStage(f.level, f.project, f.area.getUUID()));
            assertTrue(f.ledger.reservedCrewMembers(f.project).isEmpty()); assertNull(NativeConstructionGuard.currentArea(f.worker));
            assertEquals(1, f.handBindings); assertEquals(coreBefore, f.core); f.assertResourcesUnchanged();
        }
    }

    @Test void conflictingPartialCancellationSelectorsKeepTheExactPendingRecordAndDoNotDetach() throws Exception {
        List<Consumer<Fixture>> conflicts = List.of(
                f -> f.workerData.putUUID(AREA, UUID.randomUUID()),
                f -> f.workerData.putUUID(GENERATION, UUID.randomUUID()),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration", UUID.randomUUID()),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putString("Hash", "b".repeat(64)),
                f -> f.workerData.getCompound(ProtectedBuilderHandLifecycle.KEY).putUUID("Generation", UUID.randomUUID()),
                f -> {
                    var foreign = mock(ProtectedBuildArea.class); when(foreign.getUUID()).thenReturn(UUID.randomUUID());
                    f.worker.currentBuildArea = foreign;
                });
        for (var conflict : conflicts) try (var f = new Fixture()) {
            f.admitThrough(Prefix.NATIVE_ASSIGNMENT); f.cancel(); conflict.accept(f); f.reload();
            var ledgerBefore = f.ledger.save(new CompoundTag()); var coreBefore = f.core.copy();
            assertFalse(NativePerimeterCrew.retireStage(f.level, f.project, f.area.getUUID()));
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(coreBefore, f.core);
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId)); assertEquals(0, f.detachments);
            assertTrue(PerimeterProjectLink.reserved(f.worker)); assertEquals(1, f.handBindings); f.assertResourcesUnchanged();
        }
    }

    private static final class Fixture implements AutoCloseable {
        PerimeterProject project = ConstructionProjectLedgerTest.project();
        CompoundTag core = new CompoundTag(), workerData = new CompoundTag(), areaData = new CompoundTag();
        ConstructionEditLedger ledger = new ConstructionEditLedger();
        final RaidSavedData data = new RaidSavedData();
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer owner = mock(ServerPlayer.class);
        final BuilderEntity worker = mock(BuilderEntity.class);
        final ProtectedBuildArea area = mock(ProtectedBuildArea.class);
        final UUID helperId = UUID.randomUUID();
        final SimpleContainer inventory = new SimpleContainer(12);
        final List<CompoundTag> materialsBefore;
        final CompoundTag paidProjectBefore;
        ItemStack mainHand;
        boolean helperLoaded = true, assignmentAccepted = true;
        int handBindings, assignments, detachments;
        final MockedStatic<RaidSavedData> saves = mockStatic(RaidSavedData.class);
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class,
                call -> call.getMethod().getName().equals("get") ? ledger : CALLS_REAL_METHODS.answer(call));
        final MockedStatic<WorkersBridge> workers = mockStatic(WorkersBridge.class);
        final MockedStatic<ProtectedStorageAccess> storage = mockStatic(ProtectedStorageAccess.class);
        final MockedStatic<WallBuilderAccess> hooks = mockStatic(WallBuilderAccess.class);
        final MockedStatic<WorkersConstructionRuntime> runtime = mockStatic(WorkersConstructionRuntime.class);

        Fixture() throws ReflectiveOperationException {
            core.putLong("BankEmeralds", 1000); data.siegeCores.put(project.header().coreKey(), core);
            PerimeterProjectStore.prepare(core, project, () -> {});
            assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
            project = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {}).project();
            project = PerimeterProjectStore.replace(core, project.check(), project.activate(project.check()), () -> {});
            paidProjectBefore = project.save();
            when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level); when(server.isSameThread()).thenReturn(true);
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            when(level.hasChunkAt(any())).thenReturn(true); when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
            when(level.getEntity(any(UUID.class))).thenAnswer(call -> {
                UUID id = call.getArgument(0);
                if (id.equals(helperId)) return helperLoaded ? worker : null;
                return id.equals(project.active().areaId()) ? area : null;
            });
            when(level.getEntitiesOfClass(eq(Mob.class), any(), any())).thenReturn(List.of());
            when(owner.getUUID()).thenReturn(project.header().owner());
            when(worker.getUUID()).thenReturn(helperId); when(worker.level()).thenReturn(level); when(worker.isAlive()).thenReturn(true);
            when(worker.getPersistentData()).thenAnswer(call -> workerData); when(worker.getUseItem()).thenReturn(ItemStack.EMPTY);
            when(worker.getInventory()).thenReturn(inventory); when(worker.getMainHandItem()).thenAnswer(call -> mainHand);
            var selector = mock(GoalSelector.class); when(selector.getAvailableGoals()).thenReturn(Set.of());
            var field = Mob.class.getDeclaredField("goalSelector"); field.setAccessible(true); field.set(worker, selector);
            worker.neededItems = new ArrayList<>();
            inventory.setItem(5, new ItemStack(Items.COBBLESTONE, 23));
            inventory.getItem(5).getOrCreateTag().putString("Origin", "pending-admission-fixture");
            inventory.setItem(6, new ItemStack(Items.BREAD, 7));
            mainHand = inventory.getItem(5).copy(); materialsBefore = inventorySnapshot();
            doAnswer(call -> { assertEquals(InteractionHand.MAIN_HAND, call.getArgument(0));
                mainHand = call.getArgument(1); handBindings++; return null;
            }).when(worker).setItemInHand(any(), any());
            workers.when(() -> WorkersBridge.isBuilder(worker)).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(worker)).thenAnswer(call -> project.header().owner());
            workers.when(() -> WorkersBridge.readOwner(area)).thenAnswer(call -> project.header().owner());
            workers.when(() -> WorkersBridge.assignBuildAreaDirectly(worker, area)).thenAnswer(call -> {
                assignments++; if (assignmentAccepted) worker.currentBuildArea = area; return assignmentAccepted;
            });
            workers.when(() -> WorkersBridge.detachBuildAreaReference(eq(worker), any())).thenAnswer(call -> {
                UUID id = call.getArgument(1); Entity current = worker.currentBuildArea;
                if (current != null && current.getUUID().equals(id)) worker.currentBuildArea = null;
                detachments++; return true;
            });
            hooks.when(() -> WallBuilderAccess.install(worker)).thenReturn(true);
            storage.when(() -> ProtectedStorageAccess.install(worker)).thenReturn(true);
            storage.when(() -> ProtectedStorageAccess.runningProblem(worker)).thenCallRealMethod();
            storage.when(() -> ProtectedStorageAccess.drainCleanup(worker)).thenReturn(true);
            runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
            prepareExactSnapshot();
        }

        void prepareExactSnapshot() {
            var header = project.header(); var layout = project.active().layout();
            var recipe = TerritoryFortification.blueprint(layout.targets(), layout.min(), layout.max());
            var plan = AcceptedConstructionPlan.decode(layout.origin(), Direction.SOUTH, layout.width(), layout.depth(), layout.height(), recipe);
            var saved = plan.save(); saved.putUUID("Owner", header.owner()); saved.putUUID("Builder", header.builder());
            saved.putString("CoreKey", header.coreKey()); saved.putLong("CorePos", header.originalCore().asLong());
            var before = new ListTag(); layout.targets().keySet().forEach(pos -> {
                var cell = new CompoundTag(); cell.putLong("Pos", pos);
                cell.put("State", NbtUtils.writeBlockState(project.before().get(pos))); before.add(cell);
            });
            saved.put("Before", before); saved.putLongArray("Completed", new long[0]); saved.putLongArray("Cleared", new long[0]);
            saved.put("Reservation", AcceptedConstructionReservation.capture(level, plan,
                    layout.reservation().stream().map(BlockPos::of).toList()).save());
            areaData.put(SNAPSHOT, saved); areaData.putBoolean("SiegeConstructionCommissionPaid", true);
            when(area.getUUID()).thenReturn(project.active().areaId()); when(area.level()).thenReturn(level);
            when(area.getPersistentData()).thenAnswer(call -> areaData); when(area.blockPosition()).thenReturn(header.originalCore());
            when(area.reservedBuilderId()).thenReturn(header.builder()); when(area.getOriginPos()).thenReturn(layout.origin());
            when(area.getFacing()).thenReturn(Direction.SOUTH); when(area.getWidthSize()).thenReturn(layout.width());
            when(area.getDepthSize()).thenReturn(layout.depth()); when(area.getHeightSize()).thenReturn(layout.height());
            when(area.getStructureNBT()).thenAnswer(call -> recipe.copy());
            areaData.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER, header.builder());
            areaData.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, header.owner());
            PerimeterProjectAuthority.stamp(area, project);
            assertTrue(NativeConstructionGuard.matchesProjectSnapshot(area, project), "Fixture must pass the real immutable stage gate");
        }

        void admitThrough(Prefix prefix) {
            assertTrue(ledger.enlistCrew(project, helperId));
            if (prefix.includes(Prefix.WHOLE_SELECTOR)) PerimeterProjectLink.set(worker, project, helperId);
            if (prefix.includes(Prefix.LEDGER_GENERATION)) assertTrue(PerimeterProjectLink.bindLedgerGeneration(worker, ledger.generation()));
            if (prefix.includes(Prefix.CHILD_RECEIPT_AND_MIRROR)) {
                workerData.putUUID(AREA, area.getUUID()); workerData.putUUID(GENERATION, ledger.generation());
                ProtectedBuilderHandMirror.arm(workerData);
            }
            if (prefix.includes(Prefix.HAND_PROOF)) {
                assertNull(ProtectedBuilderHandMirror.restore(worker));
                assertTrue(ledger.retainHandLifecycle(workerData, helperId, project.header().owner(), area.getUUID()));
            }
            if (prefix.includes(Prefix.NATIVE_METADATA)) PlayerFortificationJobs.linkWorker(worker, area, project.header().owner());
            if (prefix.includes(Prefix.NATIVE_ASSIGNMENT)) worker.currentBuildArea = area;
            if (prefix == Prefix.ACTIVE) assertTrue(ledger.activateCrewMember(project, helperId));
        }
        void cancel() {
            project = PerimeterProjectStore.replace(core, project.check(),
                    project.cancel(project.check(), "Owner canceled interrupted helper admission"), () -> {});
        }
        void reload() {
            core = core.copy(); data.siegeCores.put(project.header().coreKey(), core);
            project = PerimeterProjectStore.get(core, project.header().projectId());
            ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            workerData = workerData.copy(); areaData = areaData.copy();
        }
        CompoundTag savedMember() {
            return ledger.save(new CompoundTag()).getCompound("BuilderCrew").getList("Projects", Tag.TAG_COMPOUND)
                    .getCompound(0).getList("Members", Tag.TAG_COMPOUND).getCompound(0);
        }
        void assertActive() {
            assertTrue(ledger.crewMember(project, helperId)); assertFalse(ledger.pendingCrewMember(project, helperId));
            assertTrue(savedMember().getBoolean("Ready")); assertFalse(savedMember().getBoolean("Withdrawn"));
            assertEquals(Set.of(helperId), ledger.crewMembers(project)); assertEquals(project.header().builder(), ledger.assignedBuilder(project));
            assertTrue(PerimeterProjectLink.matches(worker, project, helperId));
            assertEquals(ledger.generation(), PerimeterProjectLink.ledgerGeneration(workerData));
            assertTrue(NativeConstructionGuard.validHandReceipt(workerData, ledger));
            assertTrue(ProtectedBuilderHandLifecycle.matches(workerData, helperId, ledger));
            assertFalse(ProtectedBuilderHandMirror.pending(workerData)); assertFalse(ProtectedBuilderHandMirror.reviewNeeded(workerData));
            assertEquals(area.getUUID(), workerData.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            assertEquals(project.header().owner(), workerData.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER));
            assertSame(area, NativeConstructionGuard.currentArea(worker)); assertSame(inventory.getItem(5), mainHand);
        }
        List<CompoundTag> inventorySnapshot() {
            var stacks = new ArrayList<CompoundTag>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) stacks.add(inventory.getItem(slot).save(new CompoundTag()));
            return stacks;
        }
        void assertResourcesUnchanged() {
            assertEquals(936, core.getLong("BankEmeralds"));
            var original = PerimeterProject.load(paidProjectBefore);
            assertEquals(original.payment(), project.payment()); assertEquals(original.manifestHash(), project.manifestHash());
            assertEquals(original.targets(), project.targets()); assertEquals(original.before(), project.before());
            assertEquals(original.clearanceBefore(), project.clearanceBefore()); assertEquals(original.reservation(), project.reservation());
            assertEquals(original.receipts(), project.receipts()); assertEquals(original.activeStage(), project.activeStage());
            assertEquals(materialsBefore, inventorySnapshot()); assertEquals(materialsBefore.get(5), mainHand.save(new CompoundTag()));
            assertTrue(worker.neededItems.isEmpty());
            verify(worker, never()).setItemSlot(any(), any()); verify(worker, never()).stopUsingItem();
            verify(worker, never()).remove(any()); verify(area, never()).removeAuthorized();
            verify(level, never()).setBlock(any(), any(), anyInt()); verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
            verify(level, never()).destroyBlock(any(), anyBoolean());
        }
        @Override public void close() {
            PerimeterProjectAuthority.stopped(server);
            runtime.close(); hooks.close(); storage.close(); workers.close(); ledgers.close(); saves.close();
        }
    }
}
