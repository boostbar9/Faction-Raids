package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PlayerFortificationJobs;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real destructive-removal callback, saved project and ledger; world/native entities remain mocks. */
class PerimeterPendingCrewDestructionTest extends MinecraftTestSupport {
    private static final String AREA = "SiegeProtectedAreaReceipt", GENERATION = "SiegeProtectedLedgerGeneration";

    @Test void destructiveRemovalRecoversEveryInterruptedAdmissionWithoutInventingActivationOrInventoryCleanup() {
        for (Entity.RemovalReason reason : List.of(Entity.RemovalReason.KILLED, Entity.RemovalReason.DISCARDED))
            for (int boundary = 0; boundary <= 6; boundary++) try (var f = new Fixture()) {
                f.admitThrough(boundary); f.reload();
                assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId));
                assertFalse(f.ledger.crewMember(f.project, f.helperId));
                CompoundTag workerBefore = f.workerData.copy(), coreBefore = f.core.copy();
                var handBefore = f.ledger.handLifecycle(f.helperId);
                f.remove(reason);
                String receipt = f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId);
                assertNotNull(receipt, "Missing destructive-removal proof at admission boundary " + boundary);
                f.reload();
                assertEquals(receipt, f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId));
                assertTrue(f.ledger.reservedCrewMembers(f.project).isEmpty());
                assertFalse(f.ledger.crewMember(f.project, f.helperId));
                assertFalse(f.ledger.pendingCrewMember(f.project, f.helperId));
                assertNull(f.ledger.pendingCrewProject(f.helperId));
                assertTrue(f.ledger.knownCrewMember(f.project, f.helperId));
                assertFalse(f.ledger.activateCrewMember(f.project, f.helperId));
                assertFalse(savedMember(f.ledger).getBoolean("Ready"));
                assertTrue(f.ledger.matchesProjectLease(f.project)); assertTrue(f.ledger.matchesProjectReservation(f.project));
                assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));
                assertEquals(f.project.header().builder(), f.ledger.assignedBuilder(f.project));
                // The missing destroyed helper must no longer block stage retirement or cancellation cleanup.
                var verified = f.project.verifyStage(f.project.check(), f.project.expectedStageReceipt());
                assertTrue(NativePerimeterCrew.retireStage(f.level, verified, f.project.active().areaId()));
                var canceled = f.project.cancel(f.project.check(), "Owner canceled interrupted admission");
                assertTrue(NativePerimeterCrew.retireStage(f.level, canceled, f.project.active().areaId()));
                assertEquals(workerBefore, f.workerData); assertEquals(coreBefore, f.core);
                assertEquals(handBefore, f.ledger.handLifecycle(f.helperId));
                f.assertNoCleanupMutations();
            }
    }

    @Test void absentUnloadedAndDimensionTransferredPendingHelpersRemainRecoverableAtEveryBoundary() {
        for (Entity.RemovalReason reason : new Entity.RemovalReason[]{null, Entity.RemovalReason.UNLOADED_TO_CHUNK,
                Entity.RemovalReason.UNLOADED_WITH_PLAYER, Entity.RemovalReason.CHANGED_DIMENSION})
            for (int boundary = 0; boundary <= 6; boundary++) try (var f = new Fixture()) {
                f.admitThrough(boundary); f.reload();
                CompoundTag before = f.ledger.save(new CompoundTag()), workerBefore = f.workerData.copy();
                f.ledger.setDirty(false); f.remove(reason);
                assertEquals(before, f.ledger.save(new CompoundTag())); assertFalse(f.ledger.isDirty());
                assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId));
                assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId));
                assertEquals(f.project.header().projectId(), f.ledger.pendingCrewProject(f.helperId));
                assertFalse(NativePerimeterCrew.retireStage(f.level, f.project, f.project.active().areaId()));
                f.reload(); assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId));
                assertEquals(workerBefore, f.workerData); f.assertNoCleanupMutations();
            }
    }

    @Test void copiedWorkerWrongOwnerAndConflictingPartialSelectorsCannotRetireThePendingIdentity() {
        List<Consumer<Fixture>> conflicts = List.of(
                f -> when(f.worker.getUUID()).thenReturn(UUID.randomUUID()),
                f -> f.bridge.when(() -> WorkersBridge.readWorkerOwner(f.worker)).thenReturn(UUID.randomUUID()),
                f -> f.bridge.when(() -> WorkersBridge.isBuilder(f.worker)).thenReturn(false),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putLong("Generation", f.project.header().generation() + 1),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("Project", UUID.randomUUID()),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putString("Hash", "b".repeat(64)),
                f -> f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration", UUID.randomUUID()),
                f -> f.workerData.putUUID(GENERATION, UUID.randomUUID()),
                f -> f.workerData.putString(GENERATION, "malformed generation"),
                f -> f.workerData.putUUID(AREA, f.project.stages().get(1).areaId()),
                f -> f.workerData.putString(AREA, "malformed child selector"),
                f -> f.workerData.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID, UUID.randomUUID()),
                f -> f.workerData.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, UUID.randomUUID()),
                f -> { var other = mock(ProtectedBuildArea.class); when(other.getUUID()).thenReturn(UUID.randomUUID());
                    when(other.level()).thenReturn(f.level); f.worker.currentBuildArea = other; });
        for (Consumer<Fixture> conflict : conflicts) try (var f = new Fixture()) {
            f.admitThrough(6); conflict.accept(f);
            CompoundTag before = f.ledger.save(new CompoundTag()), workerBefore = f.workerData.copy();
            f.ledger.setDirty(false); f.remove(Entity.RemovalReason.KILLED);
            assertEquals(before, f.ledger.save(new CompoundTag())); assertFalse(f.ledger.isDirty());
            assertTrue(f.ledger.pendingCrewMember(f.project, f.helperId));
            assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId));
            assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            assertEquals(workerBefore, f.workerData); f.assertNoCleanupMutations();
        }
    }

    @Test void noSelectorLookupStillRequiresTheFullAuthoritativeProjectAndValidLedgerGeneration() {
        for (String conflict : List.of("missing core", "wrong core", "foreign membership generation", "invalid ledger"))
            try (var f = new Fixture()) {
                f.admitThrough(0);
                if (conflict.equals("missing core")) f.data.siegeCores.clear();
                else if (conflict.equals("wrong core")) { f.data.siegeCores.clear(); f.data.siegeCores.put("team:foreign", f.core); }
                else {
                    var saved = f.ledger.save(new CompoundTag());
                    if (conflict.equals("foreign membership generation"))
                        saved.getCompound("BuilderCrew").getList("Projects", Tag.TAG_COMPOUND).getCompound(0)
                                .putLong("Generation", f.project.header().generation() + 1);
                    else saved.getCompound("BuilderCrew").putUUID("LedgerGeneration", UUID.randomUUID());
                    f.ledger = ConstructionEditLedger.load(saved);
                }
                CompoundTag before = f.ledger.save(new CompoundTag()); f.ledger.setDirty(false);
                f.remove(Entity.RemovalReason.KILLED);
                assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helperId));
                assertEquals(before, f.ledger.save(new CompoundTag())); assertFalse(f.ledger.isDirty());
                f.assertNoCleanupMutations();
            }
    }

    @Test void retainedPriorHandProvenanceAndLaterPlayerEditsSurvivePendingDestruction() {
        try (var f = new Fixture()) {
            UUID oldArea = UUID.randomUUID(), oldOwner = UUID.randomUUID();
            assertTrue(f.ledger.register(oldArea, java.util.Set.of(new BlockPos(1000, 65, 1000))));
            assertTrue(f.ledger.retainHandLifecycle(f.workerData, f.helperId, oldOwner, oldArea));
            var hand = f.ledger.handLifecycle(f.helperId); assertNotNull(hand);
            f.ledger.retire(oldArea, true);
            f.admitThrough(5);
            f.ledger.record(BlockPos.of(f.project.reservation().iterator().next()));
            CompoundTag workerBefore = f.workerData.copy(), coreBefore = f.core.copy();
            f.remove(Entity.RemovalReason.KILLED); f.reload();
            assertTrue(f.ledger.crewBuilderDestroyed(f.project, f.helperId));
            assertTrue(f.ledger.edited(f.project.header().projectId()));
            assertEquals(hand, f.ledger.handLifecycle(f.helperId));
            assertEquals(workerBefore, f.workerData); assertEquals(coreBefore, f.core);
            f.assertNoCleanupMutations();
        }
    }

    private static CompoundTag savedMember(ConstructionEditLedger ledger) {
        return ledger.save(new CompoundTag()).getCompound("BuilderCrew").getList("Projects", Tag.TAG_COMPOUND)
                .getCompound(0).getList("Members", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static final class Fixture implements AutoCloseable {
        PerimeterProject project = ConstructionProjectLedgerTest.project();
        final CompoundTag core = new CompoundTag(), workerData = new CompoundTag();
        ConstructionEditLedger ledger = new ConstructionEditLedger();
        final RaidSavedData data = new RaidSavedData();
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final BuilderEntity worker = mock(BuilderEntity.class);
        final ProtectedBuildArea area = mock(ProtectedBuildArea.class);
        final UUID helperId = UUID.randomUUID();
        final MockedStatic<RaidSavedData> saved = mockStatic(RaidSavedData.class);
        final MockedStatic<WorkersBridge> bridge = mockStatic(WorkersBridge.class);
        final MockedStatic<ProtectedStorageAccess> storage = mockStatic(ProtectedStorageAccess.class);
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class,
                call -> call.getMethod().getName().equals("get") ? ledger : CALLS_REAL_METHODS.answer(call));

        Fixture() {
            core.putLong("BankEmeralds", 1000); data.siegeCores.put(project.header().coreKey(), core);
            PerimeterProjectStore.prepare(core, project, () -> {});
            assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
            project = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {}).project();
            project = PerimeterProjectStore.replace(core, project.check(), project.activate(project.check()), () -> {});
            saved.when(() -> RaidSavedData.get(server)).thenReturn(data);
            when(server.overworld()).thenReturn(level); when(server.isSameThread()).thenReturn(true);
            when(level.getServer()).thenReturn(server);
            when(worker.getUUID()).thenReturn(helperId); when(worker.getPersistentData()).thenReturn(workerData);
            when(worker.level()).thenReturn(level); when(worker.isAlive()).thenReturn(false);
            when(area.getUUID()).thenReturn(project.active().areaId()); when(area.level()).thenReturn(level);
            when(area.blockPosition()).thenReturn(new BlockPos(8, 64, 8));
            bridge.when(() -> WorkersBridge.isBuilder(worker)).thenReturn(true);
            bridge.when(() -> WorkersBridge.readWorkerOwner(worker)).thenReturn(project.header().owner());
        }
        void admitThrough(int boundary) {
            assertTrue(ledger.enlistCrew(project, helperId));
            if (boundary >= 1) PerimeterProjectLink.set(worker, project, helperId);
            if (boundary >= 2) assertTrue(PerimeterProjectLink.bindLedgerGeneration(worker, ledger.generation()));
            if (boundary >= 3) { workerData.putUUID(AREA, project.active().areaId()); workerData.putUUID(GENERATION, ledger.generation()); }
            if (boundary >= 4) assertTrue(ledger.retainHandLifecycle(workerData, helperId, project.header().owner(), project.active().areaId()));
            if (boundary >= 5) PlayerFortificationJobs.linkWorker(worker, area, project.header().owner());
            if (boundary >= 6) worker.currentBuildArea = area;
        }
        void remove(Entity.RemovalReason reason) {
            when(worker.getRemovalReason()).thenReturn(reason);
            NativeConstructionGuard.areaRemoved(new EntityLeaveLevelEvent(worker, level));
        }
        void reload() { ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag())); }
        void assertNoCleanupMutations() {
            verify(worker, never()).setItemSlot(any(), any()); verify(worker, never()).remove(any());
            verify(area, never()).removeAuthorized(); verify(level, never()).setBlock(any(), any(), anyInt());
            verify(level, never()).destroyBlock(any(), anyBoolean()); storage.verifyNoInteractions();
            bridge.verify(() -> WorkersBridge.detachBuildAreaReference(any(), any()), never());
        }
        @Override public void close() { ledgers.close(); storage.close(); bridge.close(); saved.close(); }
    }
}
