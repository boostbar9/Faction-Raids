package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterGateProjectFixture;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
 * Controller boundary regressions with mocked world, ledger and cleanup results. The request
 * predicate and one-cell resume snapshot are synthetic fixtures, not native-runtime evidence.
 * ProtectedStorageCleanupTest separately checks the admitted callback/cleanup implementation.
 */
class NativeCrewContinuationTest extends MinecraftTestSupport {
    private static final String AREA = "SiegeProtectedAreaReceipt", GENERATION = "SiegeProtectedLedgerGeneration";

    private static final class Fixture implements AutoCloseable {
        final PerimeterProject project = PerimeterGateProjectFixture.paidRunning();
        final UUID builder = UUID.randomUUID(), generation = UUID.randomUUID();
        final BuilderEntity worker = mock(BuilderEntity.class);
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer owner = mock(ServerPlayer.class);
        final ProtectedBuildArea area = mock(ProtectedBuildArea.class);
        final ConstructionEditLedger ledger = mock(ConstructionEditLedger.class);
        final CompoundTag data = new CompoundTag(), areaData = new CompoundTag();
        final SimpleContainer inventory = new SimpleContainer(12);
        final NeededItem request = new NeededItem(stack -> stack.is(Items.COBBLESTONE), 17, true);
        final List<NeededItem> requests = new ArrayList<>(List.of(request));
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class);
        final MockedStatic<WorkersConstructionRuntime> runtime = mockStatic(WorkersConstructionRuntime.class);
        final MockedStatic<ProtectedStorageAccess> storage = mockStatic(ProtectedStorageAccess.class);
        final MockedStatic<WorkersBridge> workers = mockStatic(WorkersBridge.class);

        Fixture() throws ReflectiveOperationException {
            var server = mock(MinecraftServer.class);
            when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
            when(worker.level()).thenReturn(level); when(worker.getUUID()).thenReturn(builder);
            when(worker.isAlive()).thenReturn(true); when(worker.getPersistentData()).thenReturn(data);
            when(worker.getUseItem()).thenReturn(ItemStack.EMPTY); when(worker.getInventory()).thenReturn(inventory);
            worker.neededItems = requests;
            inventory.setItem(5, new ItemStack(Items.COBBLESTONE, 23));
            inventory.getItem(5).getOrCreateTag().putString("origin", "continuation-fixture");
            inventory.setItem(6, new ItemStack(Items.BREAD, 7));
            // A real selector presence makes runningProblem inspect dormant requests. It never
            // dispatches goals here; drainCleanup's success/failure is the boundary under test.
            var selector = mock(GoalSelector.class);
            var field = Mob.class.getDeclaredField("goalSelector"); field.setAccessible(true); field.set(worker, selector);
            when(selector.getAvailableGoals()).thenReturn(Set.of());
            when(ledger.generation()).thenReturn(generation);
            when(ledger.sameGeneration(generation)).thenReturn(true);
            when(ledger.knownCrewMember(project, builder)).thenReturn(true);
            when(ledger.crewMember(project, builder)).thenReturn(true);
            var receipt = new ProtectedBuilderHandLifecycle.Receipt(UUID.randomUUID(), builder,
                    project.header().owner(), project.active().areaId(), generation);
            data.put(ProtectedBuilderHandLifecycle.KEY, receipt.save());
            when(ledger.handLifecycle(builder)).thenReturn(receipt);
            PerimeterProjectLink.set(worker, project, builder);
            assertTrue(PerimeterProjectLink.bindLedgerGeneration(worker, generation));
            ledgers.when(() -> ConstructionEditLedger.get(level)).thenReturn(ledger);
            runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
            storage.when(() -> ProtectedStorageAccess.runningProblem(worker)).thenCallRealMethod();
            storage.when(() -> ProtectedStorageAccess.drainCleanup(worker)).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(worker)).thenReturn(project.header().owner());
        }

        /** Only the outer snapshot-match boundary is stubbed by resume tests, not resume itself. */
        void prepareResume() {
            when(ledger.crewStageGranted(project, builder)).thenReturn(true);
            when(ledger.workerCleanupAreas(project, builder)).thenReturn(Set.of(project.active().areaId()));
            when(owner.getUUID()).thenReturn(project.header().owner());
            when(area.level()).thenReturn(level); when(area.getUUID()).thenReturn(project.active().areaId());
            when(area.getPersistentData()).thenReturn(areaData);
            when(ledger.crewMember(project, builder)).thenReturn(true);
            when(ledger.matchesProjectLease(project)).thenReturn(true);
            when(ledger.completeReservation(project.active().areaId())).thenReturn(true);
            data.putUUID(AREA, project.active().areaId()); data.putUUID(GENERATION, generation);
            areaData.putBoolean("SiegeConstructionCommissionPaid", true);
            var plan = AcceptedConstructionPlan.decode(BlockPos.ZERO, Direction.SOUTH, 1, 1, 1,
                    AcceptedConstructionPlanTest.blueprint(1, 0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));
            var saved = plan.save(); saved.putUUID("Owner", project.header().owner());
            saved.putUUID("Builder", project.header().builder()); saved.putString("CoreKey", project.header().coreKey());
            saved.putLong("CorePos", project.header().originalCore().asLong());
            saved.putLongArray("Completed", new long[0]); saved.putLongArray("Cleared", new long[0]);
            var cell = new CompoundTag(); cell.putLong("Pos", 0);
            cell.put("State", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
            var before = new ListTag(); before.add(cell); saved.put("Before", before);
            when(level.hasChunkAt(any())).thenReturn(true);
            saved.put("Reservation", AcceptedConstructionReservation.capture(level, plan, plan.cells.keySet()).save());
            areaData.put("SiegeProtectedConstructionV1", saved);
            worker.currentBuildArea = area;
        }

        void assertRequestsAndInventoryUntouched() {
            assertSame(requests, worker.neededItems); assertEquals(1, requests.size());
            assertSame(request, requests.get(0)); assertEquals(17, request.count); assertTrue(request.required);
            assertNull(request.sourceKey);
            assertEquals(23, inventory.getItem(5).getCount());
            assertEquals("continuation-fixture", inventory.getItem(5).getTag().getString("origin"));
            assertEquals(7, inventory.getItem(6).getCount());
            verify(worker, never()).getInventory(); verify(worker, never()).setItemInHand(any(), any());
            verify(worker, never()).setItemSlot(any(), any()); verify(worker, never()).stopUsingItem();
        }

        void assertNoDrain() { storage.verify(() -> ProtectedStorageAccess.drainCleanup(worker), never()); }

        @Override public void close() { workers.close(); storage.close(); runtime.close(); ledgers.close(); }
    }

    @Test void authenticatedContinuationKeepsOriginalNativeRequestsAndAllInventoryValues() throws Exception {
        try (var f = new Fixture()) {
            var before = f.data.copy(); var paid = f.project.save();
            var requestMatcher = f.request.matcher; var hand = f.inventory.getItem(5); var food = f.inventory.getItem(6);
            assertNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            assertNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            f.storage.verify(() -> ProtectedStorageAccess.drainCleanup(f.worker), times(2));
            f.storage.verify(() -> ProtectedStorageAccess.runningProblem(f.worker), never());
            assertEquals(before, f.data); assertEquals(paid, f.project.save());
            assertSame(requestMatcher, f.request.matcher); assertSame(hand, f.inventory.getItem(5)); assertSame(food, f.inventory.getItem(6));
            f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void savedCoordinatorIdentityAlsoAllowsTheSameBoundedContinuation() throws Exception {
        try (var f = new Fixture()) {
            when(f.ledger.knownCrewMember(f.project, f.builder)).thenReturn(false);
            when(f.ledger.crewMember(f.project, f.builder)).thenReturn(false);
            when(f.ledger.knownProjectBuilder(f.project, f.builder)).thenReturn(true);
            when(f.ledger.assignedBuilder(f.project)).thenReturn(f.builder);
            assertNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            f.storage.verify(() -> ProtectedStorageAccess.drainCleanup(f.worker));
            f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void freshWorkerRequestsStillFailStrictCommissioningWithoutDrain() throws Exception {
        try (var f = new Fixture()) {
            f.data.remove(PerimeterProjectLink.KEY); f.data.remove(ProtectedBuilderHandLifecycle.KEY);
            when(f.ledger.knownCrewMember(f.project, f.builder)).thenReturn(false);
            when(f.ledger.crewMember(f.project, f.builder)).thenReturn(false);
            assertNotNull(NativeConstructionGuard.commissionProblem(f.worker));
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            f.assertNoDrain(); f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void missingOrConflictingContinuationIdentityCannotDrainOrCarryRequests() throws Exception {
        var damage = List.<Consumer<Fixture>>of(
                f -> f.data.remove(PerimeterProjectLink.KEY),
                f -> f.data.getCompound(PerimeterProjectLink.KEY).remove("LedgerGeneration"),
                f -> f.data.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration", UUID.randomUUID()),
                f -> f.data.getCompound(PerimeterProjectLink.KEY).putString("Hash", "b".repeat(64)),
                f -> f.data.remove(ProtectedBuilderHandLifecycle.KEY),
                f -> f.data.getCompound(ProtectedBuilderHandLifecycle.KEY).putUUID("Generation", UUID.randomUUID()),
                f -> when(f.ledger.handLifecycle(f.builder)).thenReturn(null),
                f -> when(f.ledger.crewMember(f.project, f.builder)).thenReturn(false));
        for (var corrupt : damage) try (var f = new Fixture()) {
            corrupt.accept(f); var before = f.data.copy();
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            assertEquals(before, f.data); f.assertNoDrain(); f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void pendingHandReviewAndEveryActiveUseSignalBlockBeforeCleanup() throws Exception {
        var block = List.<Consumer<Fixture>>of(
                f -> ProtectedBuilderHandMirror.arm(f.data),
                f -> ProtectedBuilderHandMirror.requireInventoryReview(f.data),
                f -> when(f.worker.isUsingItem()).thenReturn(true),
                f -> when(f.worker.getUseItem()).thenReturn(new ItemStack(Items.BREAD)),
                f -> when(f.worker.getUseItemRemainingTicks()).thenReturn(1),
                f -> when(f.worker.getUseItem()).thenReturn(null));
        for (var obstruct : block) try (var f = new Fixture()) {
            obstruct.accept(f); var before = f.data.copy();
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            assertEquals(before, f.data); f.assertNoDrain(); f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void reportedOrphanedCleanupRetainsItsJournalAndNeverBecomesContinuationPermission() throws Exception {
        try (var f = new Fixture()) {
            ProtectedInventoryCleanup.record(f.data, ProtectedStorageAccess.Kind.NEEDED, ProtectedInventoryCleanup.CLEANUP);
            f.storage.when(() -> ProtectedStorageAccess.drainCleanup(f.worker)).thenReturn(false);
            var before = f.data.copy();
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            assertEquals(before, f.data); assertTrue(ProtectedInventoryCleanup.outstanding(f.data));
            f.storage.verify(() -> ProtectedStorageAccess.drainCleanup(f.worker), times(2));
            f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void unavailableRuntimeOrExceptionalCleanupFailsClosed() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.when(WorkersConstructionRuntime::problem).thenReturn("Fixture runtime unavailable");
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project)); f.assertNoDrain();
            f.runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
            f.storage.when(() -> ProtectedStorageAccess.drainCleanup(f.worker)).thenThrow(new IllegalStateException("Fixture cleanup failure"));
            assertNotNull(NativeConstructionGuard.continuationProblem(f.worker, f.project));
            f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void pendingHelperCannotUseOldHandProvenanceToBypassStrictRequestAdmission() throws Exception {
        try (var f = new Fixture(); var guards = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            f.prepareResume(); when(f.ledger.crewMember(f.project, f.builder)).thenReturn(false);
            when(f.ledger.pendingCrewMember(f.project, f.builder)).thenReturn(true);
            guards.when(() -> NativeConstructionGuard.matchesProjectSnapshot(f.area, f.project)).thenReturn(true);
            var before = f.data.copy();
            assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
            assertEquals(before, f.data); f.assertNoDrain();
            verify(f.ledger, never()).activateCrewMember(any(), any());
            f.workers.verify(() -> WorkersBridge.assignBuildAreaDirectly(any(), any()), never());
            f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void exactActiveHelperResumeDoesNotRepeatedlyStopNativeResupply() throws Exception {
        try (var f = new Fixture(); var guards = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            f.prepareResume();
            guards.when(() -> NativeConstructionGuard.matchesProjectSnapshot(f.area, f.project)).thenReturn(true);
            // A live storage lifecycle would reject strict commissioning. Exact current association
            // must return before either that predicate or its cleanup is invoked again.
            f.storage.when(() -> ProtectedStorageAccess.runningProblem(f.worker)).thenReturn("Fixture running resupply");
            var before = f.data.copy(); var areaBefore = f.areaData.copy();
            assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
            assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
            assertEquals(before, f.data); assertEquals(areaBefore, f.areaData); assertSame(f.area, f.worker.currentBuildArea);
            f.assertNoDrain(); f.storage.verify(() -> ProtectedStorageAccess.runningProblem(f.worker), never());
            f.storage.verify(() -> ProtectedStorageAccess.install(f.worker), never());
            f.workers.verify(() -> WorkersBridge.assignBuildAreaDirectly(any(), any()), never());
            verify(f.worker, never()).getNavigation(); f.assertRequestsAndInventoryUntouched();
        }
    }

    @Test void activeAssociationRepairsMissingSelectorButNeverOverwritesWrongGeneration() throws Exception {
        for (boolean missing : new boolean[]{true, false}) {
            try (var f = new Fixture(); var guards = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                f.prepareResume();
                guards.when(() -> NativeConstructionGuard.matchesProjectSnapshot(f.area, f.project)).thenReturn(true);
                if (missing) f.data.remove(PerimeterProjectLink.KEY);
                else f.data.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration", UUID.randomUUID());
                var before = f.data.copy();
                assertEquals(missing, NativeConstructionGuard.resumeCrewMember(f.owner, f.worker, f.area, f.project));
                if (missing) {
                    assertTrue(PerimeterProjectLink.matches(f.worker, f.project, f.builder));
                    assertEquals(f.generation, PerimeterProjectLink.ledgerGeneration(f.data));
                } else assertEquals(before, f.data);
                f.assertNoDrain(); f.assertRequestsAndInventoryUntouched();
            }
        }
    }
}
