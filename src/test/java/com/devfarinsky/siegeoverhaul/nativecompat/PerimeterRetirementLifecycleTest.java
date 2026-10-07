package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerimeterRetirementLifecycleTest extends MinecraftTestSupport {
    private static final String AREA="SiegeProtectedAreaReceipt", GENERATION="SiegeProtectedLedgerGeneration";
    private record Fixture(CompoundTag core,PerimeterProject project,ConstructionEditLedger ledger,
                           BuilderEntity worker,CompoundTag data,ServerLevel level) {}

    private Fixture fixture() {
        var project=ConstructionProjectLedgerTest.project();var core=new CompoundTag();var ledger=new ConstructionEditLedger();
        PerimeterProjectStore.prepare(core,project,()->{});assertTrue(ledger.registerProject(project));assertTrue(ledger.leaseProjectStage(project));
        var paid=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,true,()->{}).project();
        project=PerimeterProjectStore.replace(core,paid.check(),paid.activate(paid.check()),()->{});
        var worker=mock(BuilderEntity.class);var data=new CompoundTag();var level=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
        // This v1 fixture has a known healthy, empty NEW-job ledger. An unavailable world ledger is intentionally fail-closed.
        var earthworksStorage=mock(net.minecraft.world.level.storage.DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(earthworksStorage);
        when(earthworksStorage.<EarthworksJobLedger>computeIfAbsent(any(),any(),eq("siege_earthworks_jobs_v1"))).thenReturn(new EarthworksJobLedger());
        when(level.getServer()).thenReturn(server);when(server.overworld()).thenReturn(level);when(worker.level()).thenReturn(level);
        when(worker.getUUID()).thenReturn(project.header().builder());when(worker.getPersistentData()).thenReturn(data);
        when(worker.getUseItem()).thenReturn(ItemStack.EMPTY);when(worker.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(worker.getInventory()).thenReturn(new SimpleContainer(12));worker.neededItems=new ArrayList<>();
        PerimeterProjectLink.set(worker,project);data.putUUID(AREA,project.active().areaId());data.putUUID(GENERATION,ledger.generation());
        assertTrue(ledger.retainHandLifecycle(data, worker.getUUID(), project.header().owner(), project.active().areaId()));
        return new Fixture(core,project,ledger,worker,data,level);
    }
    private PerimeterProject verified(Fixture f) {
        var p=f.project();return PerimeterProjectStore.replace(f.core(),p.check(),p.verifyStage(p.check(),p.expectedStageReceipt()),()->{});
    }
    private void configure(Fixture f,MockedStatic<WorkersBridge> workers,MockedStatic<ConstructionEditLedger> ledgers,
                           MockedStatic<PerimeterProjectAuthority> authority,MockedStatic<ProtectedStorageAccess> storage,
                           MockedStatic<WallBuilderAccess> goals,MockedStatic<WorkersConstructionRuntime> runtime) {
        workers.when(()->WorkersBridge.isBuilder(f.worker())).thenReturn(true);
        workers.when(()->WorkersBridge.detachBuildAreaReference(eq(f.worker()),any(UUID.class))).thenReturn(true);
        ledgers.when(()->ConstructionEditLedger.get(f.level())).thenReturn(f.ledger());
        authority.when(()->PerimeterProjectAuthority.snapshot(f.level(),f.project().header().coreKey()))
                .thenAnswer(invocation->PerimeterProjectStore.snapshot(f.core()));
        storage.when(()->ProtectedStorageAccess.install(f.worker())).thenReturn(true);
        storage.when(()->ProtectedStorageAccess.drainCleanup(f.worker())).thenReturn(true);
        goals.when(()->WallBuilderAccess.install(f.worker())).thenReturn(true);
        runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
    }

    @Test void retiredVerifiedReceiptOnlyAuthorizesValuePreservingHandRestore() {
        var f=fixture();var p=verified(f);f.ledger().retire(p.active().areaId(),true);
        var authority=PerimeterProjectStore.snapshot(f.core());
        assertFalse(NativeConstructionGuard.validHandReceipt(f.data(),f.ledger()));
        assertTrue(NativeConstructionGuard.validRetirementHandReceipt(f.data(),f.worker().getUUID(),f.ledger(),authority));
        var reloaded=ConstructionEditLedger.load(f.ledger().save(new CompoundTag()));
        assertTrue(NativeConstructionGuard.validRetirementHandReceipt(f.data(),f.worker().getUUID(),reloaded,authority));
        assertFalse(NativeConstructionGuard.validRetirementHandReceipt(f.data(),UUID.randomUUID(),f.ledger(),authority));
        var foreign=f.data().copy();foreign.putUUID(AREA,UUID.randomUUID());
        assertFalse(NativeConstructionGuard.validRetirementHandReceipt(foreign,f.worker().getUUID(),f.ledger(),authority));
        var generation=f.data().copy();generation.putUUID(GENERATION,UUID.randomUUID());
        assertFalse(NativeConstructionGuard.validRetirementHandReceipt(generation,f.worker().getUUID(),f.ledger(),authority));
        var unverified=fixture();unverified.ledger().retire(unverified.project().active().areaId(),true);
        assertFalse(NativeConstructionGuard.validRetirementHandReceipt(unverified.data(),unverified.worker().getUUID(),unverified.ledger(),
                PerimeterProjectStore.snapshot(unverified.core())));
    }

    @Test void loadedVerifiedWorkerKeepsItsAssociationUntilRuntimeRetiresTheStage() {
        var f=fixture();var p=verified(f);f.ledger().retire(p.active().areaId(),true);ProtectedBuilderHandMirror.arm(f.data());
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker()));assertFalse(ProtectedBuilderHandMirror.pending(f.data()));
            assertEquals(p.active().areaId(),f.data().getUUID(AREA));assertTrue(PerimeterProjectLink.matches(f.worker(),p));
            workers.verify(()->WorkersBridge.detachBuildAreaReference(any(),any()),never());
            assertNotNull(NativeConstructionGuard.storageProblem(f.worker(),Set.of()),"Hand cleanup proof never grants storage authority");
        }
    }

    @Test void activeUseCanFinishWhilePendingHandsIndependentlyDenyWorkAndTransfers() {
        var f=fixture();var p=verified(f);f.ledger().retire(p.active().areaId(),true);ProtectedBuilderHandMirror.arm(f.data());
        when(f.worker().isUsingItem()).thenReturn(true);
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);clearInvocations(f.worker());
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker()));
            assertTrue(ProtectedBuilderHandMirror.pending(f.data()));assertFalse(ProtectedBuilderHandMirror.reviewNeeded(f.data()));
            assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker(),null));
            assertNotNull(NativeConstructionGuard.storageProblem(f.worker(),Set.of()));
            verify(f.worker(),never()).getInventory();verify(f.worker(),never()).stopUsingItem();
            workers.verify(()->WorkersBridge.detachBuildAreaReference(any(),any()),never());
        }
    }

    @Test void canceledWorkerRetainsScopeWhenCleanupIsPendingWithoutClearingRequestsOrReview() {
        var f=fixture();var p=f.project();PerimeterProjectStore.replace(f.core(),p.check(),p.cancel(p.check(),"Owner canceled"),()->{});
        var requests=f.worker().neededItems;
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);
            storage.when(()->ProtectedStorageAccess.drainCleanup(f.worker())).thenReturn(false);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker()));assertTrue(f.data().hasUUID(AREA));
            assertTrue(PerimeterProjectLink.reserved(f.worker()));assertTrue(NativeConstructionGuard.hasProtectedReceipt(f.worker()));
            assertSame(requests,f.worker().neededItems);workers.verify(()->WorkersBridge.detachBuildAreaReference(any(),any()),never());
            ProtectedBuilderHandMirror.requireInventoryReview(f.data());assertFalse(NativeConstructionGuard.readyForRetirement(f.worker()));
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(f.data()));
        }
    }

    @Test void compactTerminalReconcilesExactStaleWorkerAfterHandRestoreAndGuardedDrain() {
        var f=fixture();var p=f.project();var canceled=p.cancel(p.check(),"Canceled");
        PerimeterProjectStore.replace(f.core(),p.check(),canceled,()->{});UUID generation=f.ledger().generation();
        f.ledger().retire(p.header().projectId(),true);
        var proof=new PerimeterTerminalReceipt.CleanupProof(p.header().projectId(),p.header().generation(),p.manifestHash(),canceled.revision(),
                canceled.state(),p.header().owner(),p.header().builder(),generation,p.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                "1".repeat(64),"2".repeat(64),"3".repeat(64));
        PerimeterProjectStore.compact(f.core(),canceled.check(),proof,()->{});ProtectedBuilderHandMirror.arm(f.data());
        assertTrue(NativeConstructionGuard.validRetirementHandReceipt(f.data(),f.worker().getUUID(),f.ledger(),PerimeterProjectStore.snapshot(f.core())));
        assertFalse(NativeConstructionGuard.validHandReceipt(f.data(),f.ledger()));
        assertFalse(NativeConstructionGuard.validRetirementHandReceipt(f.data(),f.worker().getUUID(),new ConstructionEditLedger(),PerimeterProjectStore.snapshot(f.core())));
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);
            storage.when(()->ProtectedStorageAccess.drainCleanup(f.worker())).thenReturn(false);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker()));assertTrue(f.data().hasUUID(AREA));assertTrue(PerimeterProjectLink.reserved(f.worker()));
            assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker(),null));
            storage.when(()->ProtectedStorageAccess.drainCleanup(f.worker())).thenReturn(true);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker()));
            assertFalse(f.data().contains(AREA));assertFalse(f.data().contains(GENERATION));assertFalse(PerimeterProjectLink.reserved(f.worker()));
        }
    }

    @Test void wholeProjectScopeWithoutAChildReceiptNeverBecomesAnUnguardedNativeJob() {
        var f=fixture();f.data().remove(AREA);f.data().remove(GENERATION);
        assertTrue(NativeConstructionGuard.hasProtectedReceipt(f.worker()));assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker(),null));
        assertFalse(NativeConstructionGuard.validHandReceipt(f.data(),f.ledger()));
    }

    @Test void exactOldCompactTerminalMigratesBeforeItsLastProjectAndChildSelectorsAreCleared() {
        var original=fixture();var ledgerTag=original.ledger().save(new CompoundTag());ledgerTag.remove("HandLifecycles");
        original.data().remove(ProtectedBuilderHandLifecycle.KEY);
        var f=new Fixture(original.core(),original.project(),ConstructionEditLedger.load(ledgerTag),original.worker(),original.data(),original.level());
        var p=f.project();var canceled=p.cancel(p.check(),"Canceled");
        PerimeterProjectStore.replace(f.core(),p.check(),canceled,()->{});f.ledger().retire(p.header().projectId(),true);
        var proof=new PerimeterTerminalReceipt.CleanupProof(p.header().projectId(),p.header().generation(),p.manifestHash(),canceled.revision(),
                canceled.state(),p.header().owner(),p.header().builder(),f.ledger().generation(),p.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                "1".repeat(64),"2".repeat(64),"3".repeat(64));
        PerimeterProjectStore.compact(f.core(),canceled.check(),proof,()->{});
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.worker()));
            assertTrue(ProtectedBuilderHandLifecycle.matches(f.data(),f.worker().getUUID(),f.ledger()));
            assertFalse(f.data().contains(AREA));assertFalse(f.data().contains(GENERATION));assertFalse(PerimeterProjectLink.reserved(f.worker()));
            assertFalse(NativeConstructionGuard.hasProtectedReceipt(f.worker()));
            assertNotNull(NativeConstructionGuard.storageProblem(f.worker(),Set.of()));
        }
    }

    @Test void canceledUnpaidPreparationWithoutAnyProtectedHandoffCanDrainWithoutFabricatingProvenance() {
        var original=fixture();var p=ConstructionProjectLedgerTest.project();var core=new CompoundTag();var ledger=new ConstructionEditLedger();
        PerimeterProjectStore.prepare(core,p,()->{});assertTrue(ledger.registerProject(p));assertTrue(ledger.leaseProjectStage(p));
        PerimeterProjectStore.replace(core,p.check(),p.cancel(p.check(),"Native handoff never accepted"),()->{});
        var data=new CompoundTag();when(original.worker().getPersistentData()).thenReturn(data);when(original.worker().getUUID()).thenReturn(p.header().builder());
        PerimeterProjectLink.set(original.worker(),p);
        var f=new Fixture(core,p,ledger,original.worker(),data,original.level());
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);
            assertTrue(NativeConstructionGuard.readyForRetirement(f.worker()));
            assertNull(ledger.handLifecycle(f.worker().getUUID()));assertFalse(ProtectedBuilderHandLifecycle.selected(data));
            assertFalse(ProtectedBuilderHandMirror.pending(data));verify(f.worker(),never()).setItemInHand(any(),any());
            data.putUUID(GENERATION,ledger.generation());assertFalse(NativeConstructionGuard.readyForRetirement(f.worker()));
        }
    }

    @Test void canceledOldProjectCannotDrainAnUnrelatedCurrentNativeAssignment() {
        var f=fixture();var p=f.project();PerimeterProjectStore.replace(f.core(),p.check(),p.cancel(p.check(),"Canceled"),()->{});
        var unrelated=mock(com.talhanation.workers.entities.workarea.BuildArea.class);
        when(unrelated.getUUID()).thenReturn(UUID.randomUUID());f.worker().currentBuildArea=unrelated;
        try(var workers=mockStatic(WorkersBridge.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var authority=mockStatic(PerimeterProjectAuthority.class);var storage=mockStatic(ProtectedStorageAccess.class);
            var goals=mockStatic(WallBuilderAccess.class);var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            configure(f,workers,ledgers,authority,storage,goals,runtime);
            assertTrue(NativePerimeterProjects.reconcileWorker(f.worker()));
            assertSame(unrelated,f.worker().currentBuildArea);assertEquals(p.active().areaId(),f.data().getUUID(AREA));
            assertTrue(PerimeterProjectLink.reserved(f.worker()));
            storage.verify(()->ProtectedStorageAccess.drainCleanup(any()),never());
            workers.verify(()->WorkersBridge.detachBuildAreaReference(any(),any()),never());
            assertFalse(NativeConstructionGuard.retireBuilderAssociation(f.worker(),p.active().areaId()));
            storage.verify(()->ProtectedStorageAccess.drainCleanup(any()),never());
        }
    }

    @Test void retirementBindsExactMarkerPositionScopeAndReservedBuilderWithoutTrustingDone() {
        var f=fixture();var p=f.project();var area=mock(ProtectedBuildArea.class);var data=new CompoundTag();var marker=new BlockPos(8,64,8);
        when(area.getUUID()).thenReturn(p.active().areaId());when(area.getPersistentData()).thenReturn(data);
        when(area.blockPosition()).thenReturn(marker);when(area.reservedBuilderId()).thenReturn(p.header().builder());
        PerimeterProjectAuthority.stamp(area,p);
        var attempt=new PerimeterStageJournal.Attempt(0,p.active().areaId(),marker,PerimeterStageJournal.State.LIVE);
        try(var workers=mockStatic(WorkersBridge.class)) {
            workers.when(()->WorkersBridge.readOwner(area)).thenReturn(p.header().owner());
            assertTrue(NativePerimeterProjects.retirementMarkerMatches(area,p,attempt,marker));
            when(area.blockPosition()).thenReturn(marker.east());assertFalse(NativePerimeterProjects.retirementMarkerMatches(area,p,attempt,marker));
            when(area.blockPosition()).thenReturn(marker);data.getCompound(PerimeterProjectAuthority.KEY).putLong("Generation",2);
            assertFalse(NativePerimeterProjects.retirementMarkerMatches(area,p,attempt,marker));
            data.getCompound(PerimeterProjectAuthority.KEY).putLong("Generation",p.header().generation());
            when(area.reservedBuilderId()).thenReturn(UUID.randomUUID());assertFalse(NativePerimeterProjects.retirementMarkerMatches(area,p,attempt,marker));
        }
    }
}
