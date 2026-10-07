package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.WallBuilderAccess;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeHandLifecycleBoundaryTest extends MinecraftTestSupport {
    private static final String AREA = "SiegeProtectedAreaReceipt", GENERATION = "SiegeProtectedLedgerGeneration";

    private static final class Fixture implements AutoCloseable {
        final BuilderEntity worker = mock(BuilderEntity.class);
        final ServerLevel level = mock(ServerLevel.class);
        final CompoundTag data = new CompoundTag();
        final SimpleContainer inventory = new SimpleContainer(12);
        final AtomicReference<ItemStack> hand = new AtomicReference<>();
        final UUID builder = UUID.randomUUID(), owner = UUID.randomUUID(), area = UUID.randomUUID();
        ConstructionEditLedger ledger = new ConstructionEditLedger();
        final MockedStatic<WorkersBridge> workers = mockStatic(WorkersBridge.class);
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class,
                invocation -> invocation.getMethod().getName().equals("get") ? ledger : CALLS_REAL_METHODS.answer(invocation));
        final MockedStatic<WorkersConstructionRuntime> runtime = mockStatic(WorkersConstructionRuntime.class);
        final MockedStatic<ProtectedStorageAccess> storage = mockStatic(ProtectedStorageAccess.class);
        final MockedStatic<WallBuilderAccess> goals = mockStatic(WallBuilderAccess.class);
        Fixture(boolean provenance) {
            // Existing hand-only fixtures have positively known empty new-job authority, not an unreadable world store.
            var earthworksStorage=mock(net.minecraft.world.level.storage.DimensionDataStorage.class);
            when(level.getDataStorage()).thenReturn(earthworksStorage);
            when(earthworksStorage.<EarthworksJobLedger>computeIfAbsent(any(),any(),eq("siege_earthworks_jobs_v1"))).thenReturn(new EarthworksJobLedger());
            var server = mock(MinecraftServer.class); when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
            when(worker.level()).thenReturn(level); when(worker.getUUID()).thenReturn(builder); when(worker.getPersistentData()).thenReturn(data);
            when(worker.getInventory()).thenReturn(inventory); when(worker.getMainHandItem()).thenAnswer(i -> hand.get());
            when(worker.getUseItem()).thenReturn(ItemStack.EMPTY);
            inventory.setItem(5, new ItemStack(Items.COBBLESTONE, 57));
            inventory.getItem(5).getOrCreateTag().putString("origin", "untouched");
            inventory.setItem(6, new ItemStack(Items.BREAD, 7)); hand.set(inventory.getItem(5));
            doAnswer(i -> { var stack = i.getArgument(1, ItemStack.class); hand.set(stack); inventory.setItem(5, stack); return null; })
                    .when(worker).setItemInHand(eq(InteractionHand.MAIN_HAND), any());
            workers.when(() -> WorkersBridge.isBuilder(worker)).thenReturn(true);
            workers.when(() -> WorkersBridge.detachBuildAreaReference(eq(worker), any())).thenReturn(true);
            ledgers.when(() -> ConstructionEditLedger.get(level)).thenAnswer(i -> ledger);
            runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
            storage.when(() -> ProtectedStorageAccess.install(worker)).thenReturn(true);
            storage.when(() -> ProtectedStorageAccess.drainCleanup(worker)).thenReturn(true);
            goals.when(() -> WallBuilderAccess.install(worker)).thenReturn(true);
            assertTrue(ledger.register(area, Set.of(BlockPos.ZERO)));
            if (provenance) assertTrue(ledger.retainHandLifecycle(data, builder, owner, area));
        }
        void terminal(boolean cleaned) { ledger.retire(area, cleaned); ledger.acknowledgeRetirement(area); }
        void reload(boolean disk) {
            ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            hand.set(ItemStack.of(inventory.getItem(5).save(new CompoundTag())));
            NativeConstructionGuard.areaJoined(new EntityJoinLevelEvent(worker, level, disk));
        }
        void oldManualProof() {
            var marker = mock(ProtectedBuildArea.class); var markerData = new CompoundTag();
            when(marker.getUUID()).thenReturn(area); when(marker.getPersistentData()).thenReturn(markerData);
            when(marker.reservedBuilderId()).thenReturn(builder); when(level.getEntity(area)).thenReturn(marker);
            workers.when(() -> WorkersBridge.readOwner(marker)).thenReturn(owner);
            var blueprint = AcceptedConstructionPlanTest.blueprint(1, 0, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
            var plan = AcceptedConstructionPlan.decode(BlockPos.ZERO, Direction.SOUTH, 1, 1, 1, blueprint);
            when(level.hasChunkAt(any())).thenReturn(true);
            var saved = plan.save(); saved.putUUID("Builder", builder); saved.putUUID("Owner", owner);
            saved.putString("CoreKey", "team:test"); saved.putLong("CorePos", 0);
            saved.putLongArray("Completed", new long[0]); saved.putLongArray("Cleared", new long[0]);
            var cell = new CompoundTag(); cell.putLong("Pos", 0); cell.put("State", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
            var before = new ListTag(); before.add(cell); saved.put("Before", before);
            saved.put("Reservation", AcceptedConstructionReservation.capture(level, plan, plan.cells.keySet()).save());
            markerData.put("SiegeProtectedConstructionV1", saved);
            data.putUUID(AREA, area); data.putUUID(GENERATION, ledger.generation());
        }
        @Override public void close() { goals.close(); storage.close(); runtime.close(); ledgers.close(); workers.close(); }
    }

    @Test void canceledAndCompletedManualJobsRebindAcrossDiskAndPortalJoinsWithoutRestoringWorkAuthority() {
        for (boolean completed : new boolean[]{false, true}) for (boolean disk : new boolean[]{false, true}) {
            try (var f = new Fixture(true)) {
                f.data.putUUID(AREA, f.area); f.data.putUUID(GENERATION, f.ledger.generation());
                assertTrue(NativeConstructionGuard.retireBuilderAssociation(f.worker, f.area));
                assertFalse(f.data.contains(AREA)); assertFalse(f.data.contains(GENERATION));
                f.terminal(completed); var food = f.inventory.getItem(6); var material = f.inventory.getItem(5).save(new CompoundTag());
                f.reload(disk); assertNotSame(f.inventory.getItem(5), f.hand.get());
                assertTrue(ProtectedBuilderHandMirror.pending(f.data));
                assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker));
                assertSame(f.inventory.getItem(5), f.hand.get()); assertEquals(material, f.hand.get().save(new CompoundTag()));
                assertSame(food, f.inventory.getItem(6)); assertEquals(57, f.inventory.countItem(Items.COBBLESTONE));
                assertFalse(NativeConstructionGuard.hasProtectedReceipt(f.worker));
                assertNotNull(NativeConstructionGuard.storageProblem(f.worker, Set.of()));
                assertFalse(f.ledger.reserves(Set.of(BlockPos.ZERO))); assertFalse(f.ledger.completeReservation(f.area));
                assertEquals(1, ProtectedBuilderHandMirror.rebindCount(f.data));
                assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker));
                verify(f.worker, times(1)).setItemInHand(eq(InteractionHand.MAIN_HAND), any());
            }
        }
    }

    @Test void transferredNativeOwnerDoesNotInvalidateImmutableHandOnlyProvenance() {
        try (var f = new Fixture(true)) {
            f.terminal(true); f.workers.when(() -> WorkersBridge.readWorkerOwner(f.worker)).thenReturn(UUID.randomUUID());
            f.reload(true); assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker));
            assertSame(f.inventory.getItem(5), f.hand.get()); assertEquals(f.owner, f.ledger.handLifecycle(f.builder).owner());
            f.workers.verify(() -> WorkersBridge.readWorkerOwner(f.worker), never());
        }
    }

    @Test void terminalUseOrUnsupportedRuntimeNeverRebindsOrLeaksLegacyCallbacks() {
        for (boolean using : new boolean[]{false, true}) try (var f = new Fixture(true)) {
            f.terminal(true); f.reload(true);
            if (using) when(f.worker.isUsingItem()).thenReturn(true);
            else f.runtime.when(WorkersConstructionRuntime::problem).thenReturn("Unsupported pinned runtime");
            assertFalse(NativeConstructionGuard.beforeWorkerTick(f.worker));
            assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker, null));
            assertTrue(ProtectedBuilderHandMirror.pending(f.data)); assertFalse(ProtectedBuilderHandMirror.reviewNeeded(f.data));
            verify(f.worker, never()).setItemInHand(any(), any()); verify(f.worker, never()).stopUsingItem();
            assertEquals(57, f.inventory.getItem(5).getCount()); assertEquals(57, f.hand.get().getCount());
        }
    }

    @Test void unequalTerminalCopiesRemainReviewBlockedAcrossASecondLoad() {
        try (var f = new Fixture(true)) {
            f.terminal(false); f.reload(true); f.hand.get().shrink(1);
            assertFalse(NativeConstructionGuard.beforeWorkerTick(f.worker)); assertTrue(ProtectedBuilderHandMirror.reviewNeeded(f.data));
            assertEquals(57, f.inventory.getItem(5).getCount()); assertEquals(56, f.hand.get().getCount());
            f.reload(true); assertFalse(NativeConstructionGuard.beforeWorkerTick(f.worker));
            verify(f.worker, never()).setItemInHand(any(), any());
        }
    }

    @Test void missingEntityHalfAndArbitraryEntityTagsCannotBecomeAliasAuthority() {
        for (boolean remove : new boolean[]{false, true}) try (var f = new Fixture(remove)) {
            f.terminal(false);
            if (remove) f.data.remove(ProtectedBuilderHandLifecycle.KEY);
            else f.data.put(ProtectedBuilderHandLifecycle.KEY, new ProtectedBuilderHandLifecycle.Receipt(
                    UUID.randomUUID(), f.builder, f.owner, f.area, f.ledger.generation()).save());
            f.reload(true); assertTrue(ProtectedBuilderHandMirror.pending(f.data));
            assertFalse(NativeConstructionGuard.beforeWorkerTick(f.worker));
            verify(f.worker, never()).setItemInHand(any(), any());
        }
    }

    @Test void ordinaryAndAlreadyCleanedUnmarkedLegacyWorkersAreNeverMigrated() {
        try (var f = new Fixture(false)) {
            f.terminal(true); f.reload(true);
            assertFalse(ProtectedBuilderHandMirror.pending(f.data));
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker));
            assertFalse(ProtectedBuilderHandLifecycle.selected(f.data));
            verify(f.worker, never()).setItemInHand(any(), any());
        }
    }

    @Test void fullHandHistoryRejectsNewCommissionBeforeReservationReceiptOrNativeSetterChanges() {
        try (var f = new Fixture(false)) {
            for (int i=0;i<ProtectedBuilderHandLifecycle.MAX_BUILDERS;i++)
                assertTrue(f.ledger.retainHandLifecycle(new CompoundTag(),UUID.randomUUID(),f.owner,f.area));
            var sender=mock(net.minecraft.server.level.ServerPlayer.class);var marker=mock(ProtectedBuildArea.class);
            var markerData=new CompoundTag();when(marker.level()).thenReturn(f.level);when(marker.getPersistentData()).thenReturn(markerData);
            assertFalse(NativeConstructionGuard.protect(sender,f.worker,marker,Set.of(BlockPos.ZERO)));
            assertTrue(NativeConstructionGuard.status(marker).contains("history is full"));
            assertFalse(f.data.contains(AREA));assertFalse(f.data.contains(GENERATION));
            assertFalse(ProtectedBuilderHandLifecycle.selected(f.data));assertFalse(ProtectedBuilderHandMirror.pending(f.data));
            assertEquals(1,f.ledger.save(new CompoundTag()).getList("Sites",10).size());
            verify(f.worker,never()).setItemInHand(any(),any());
            f.storage.verify(()->ProtectedStorageAccess.install(f.worker),never());
        }
    }

    @Test void loadedExactOldManualSnapshotMigratesBeforeImmediateCancellationThenSurvivesReload() {
        try (var f = new Fixture(false)) {
            f.oldManualProof(); f.hand.set(f.inventory.getItem(5).copy());
            assertTrue(NativeConstructionGuard.retireBuilderAssociation(f.worker, f.area));
            assertTrue(ProtectedBuilderHandLifecycle.matches(f.data, f.builder, f.ledger));
            assertFalse(f.data.contains(AREA)); f.terminal(false); f.reload(true);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker));
            assertSame(f.inventory.getItem(5), f.hand.get());
        }
    }

    @Test void missingOrForeignOldSnapshotAndAdminRemovedHistoryCannotBeGuessedFromEqualStacks() {
        for (int invalid = 0; invalid < 4; invalid++) try (var f = new Fixture(false)) {
            f.oldManualProof();
            if (invalid == 0) when(f.level.getEntity(f.area)).thenReturn(null);
            if (invalid == 1) f.data.putUUID(GENERATION, UUID.randomUUID());
            if (invalid == 2) when(f.worker.getUUID()).thenReturn(UUID.randomUUID());
            if (invalid == 3) {
                var marker=f.level.getEntity(f.area);when(marker.getRemovalReason()).thenReturn(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                NativeConstructionGuard.areaRemoved(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(marker,f.level));
                assertTrue(f.ledger.retired(f.area)); // Forced/admin removal cannot mint replacement hand provenance.
            }
            assertFalse(NativeConstructionGuard.retireBuilderAssociation(f.worker, f.area));
            assertTrue(f.data.hasUUID(AREA)); assertFalse(ProtectedBuilderHandLifecycle.selected(f.data));
            verify(f.worker, never()).setItemInHand(any(), any());
            f.workers.verify(() -> WorkersBridge.detachBuildAreaReference(any(), any()), never());
        }
    }
}
