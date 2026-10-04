package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.DepositItemsToStorage;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real cancellation, journal, ledger and removal-event code; native world/inventory boundaries are mocked. */
class PerimeterDestroyedBuilderCleanupTest extends MinecraftTestSupport {
    private static final String AREA="SiegeProtectedAreaReceipt", GENERATION="SiegeProtectedLedgerGeneration";

    @Test void authenticatedDeathReleasesOnlyExactMarkersAndReservationWithoutRefundOrInventoryCleanup() throws Exception {
        try(var f=new Fixture()) {
            f.remove(Entity.RemovalReason.KILLED);
            String destruction=f.ledger.projectBuilderDestructionReceipt(f.project);assertNotNull(destruction);
            CompoundTag workerBefore=f.workerData.copy();UUID ledgerGeneration=f.ledger.generation();
            assertTrue(f.cancel());var terminal=PerimeterProjectStore.terminal(f.core,f.project.header().projectId());
            assertNotNull(terminal);assertNull(PerimeterProjectStore.get(f.core,f.project.header().projectId()));
            assertEquals(PerimeterProject.State.CANCELED,terminal.state());assertEquals(64,terminal.payment().debited());
            assertEquals(936,FactionBank.balance(f.core));assertArrayEquals(new int[]{-64},FactionBank.ledgerDeltas(f.core));
            assertFalse(f.ledger.reserves(f.cells()));assertFalse(f.ledger.retired(f.project.active().areaId()));
            assertEquals(workerBefore,f.workerData,"Death cleanup must leave removed worker/corpse data untouched");
            String prefix=f.project.header().projectId()+":"+f.project.header().generation()+":"+f.project.manifestHash()+":"
                    +ledgerGeneration+":"+f.project.stages().stream().map(PerimeterProject.Stage::areaId).toList();
            assertEquals(hash("exact-worker-destroyed:"+prefix+":"+f.project.header().builder()+":"+destruction),
                    terminal.cleanup().builderDetachmentReceipt());
            assertNotEquals(hash("exact-worker-detached:"+prefix+":"+f.project.header().builder()),
                    terminal.cleanup().builderDetachmentReceipt());
            verify(f.area).removeAuthorized();verify(f.worker,never()).remove(any());verify(f.worker,never()).setItemSlot(any(),any());
            verify(f.level,never()).setBlock(any(),any(),anyInt());verify(f.level,never()).destroyBlock(any(),anyBoolean());
            f.storage.verifyNoInteractions();f.bridge.verify(()->WorkersBridge.detachBuildAreaReference(any(),any()),never());
            assertTrue(f.entities.containsKey(f.corpse.getUUID()));verify(f.corpse,never()).remove(any());
        }
    }

    @Test void missingUnloadedAndDimensionTransferredWorkersNeverSupplyDestructionEvidence() throws Exception {
        for(Entity.RemovalReason reason:new Entity.RemovalReason[]{null,Entity.RemovalReason.UNLOADED_TO_CHUNK,Entity.RemovalReason.CHANGED_DIMENSION}) {
            try(var f=new Fixture()) {
                f.remove(reason);assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));
                assertTrue(f.cancel());f.assertRetained();verify(f.area,never()).removeAuthorized();f.storage.verifyNoInteractions();
            }
        }
    }

    @Test void copiedWorkerWrongLedgerGenerationWrongProjectGenerationAndWrongSectionCannotProveDeath() throws Exception {
        List<Consumer<Fixture>> changes=List.of(
                f->when(f.worker.getUUID()).thenReturn(UUID.randomUUID()),
                f->f.workerData.putUUID(GENERATION,UUID.randomUUID()),
                f->f.workerData.getCompound(PerimeterProjectLink.KEY).putLong("Generation",f.project.header().generation()+1),
                f->f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("Project",UUID.randomUUID()),
                f->f.workerData.putUUID(AREA,f.project.stages().get(1).areaId()),
                f->f.core.remove(PerimeterStageJournal.KEY));
        for(var change:changes)try(var f=new Fixture()) {
            change.accept(f);f.remove(Entity.RemovalReason.KILLED);
            assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            assertTrue(f.cancel());f.assertRetained();verify(f.area,never()).removeAuthorized();
        }
    }

    @Test void positiveDeathProofSurvivesReloadAndRepeatedCancellationWithoutResurrectingStaleWorkers() throws Exception {
        try(var f=new Fixture()) {
            f.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);assertTrue(f.cancel());f.assertRetained();
            assertTrue(f.cancel());f.assertRetained();
            f.remove(Entity.RemovalReason.KILLED);String receipt=f.ledger.projectBuilderDestructionReceipt(f.project);assertNotNull(receipt);
            f.reload();assertEquals(receipt,f.ledger.projectBuilderDestructionReceipt(f.project));
            assertNotNull(PerimeterProjectAuthority.problem(f.level,f.worker,f.area,false,false),"Canceled authority rejects a stale native job");
            assertTrue(f.cancel());String terminal=PerimeterProjectStore.terminal(f.core,f.project.header().projectId()).receiptHash();
            assertFalse(f.cancel());f.reload();assertFalse(f.cancel());
            assertEquals(terminal,PerimeterProjectStore.terminal(f.core,f.project.header().projectId()).receiptHash());
            assertFalse(f.ledger.reserves(f.cells()));assertEquals(936,FactionBank.balance(f.core));
            assertNotNull(PerimeterProjectAuthority.problem(f.level,f.worker,f.area,false,false),"Compact terminal still denies stale native work");
            assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker,null));
            assertTrue(PerimeterProjectLink.reserved(f.worker),"Removed worker metadata is left for guarded stale-save reconciliation");
        }
    }

    @Test void persistedCleanupSurvivesDeathCompactionAndBlocksRestoredStorageAndUpkeep() throws Exception {
        assertRestoredInventoryJournalBlocked(ProtectedInventoryCleanup.CLEANUP);
    }

    @Test void persistedReviewSurvivesDeathCompactionAndBlocksRestoredStorageAndUpkeep() throws Exception {
        assertRestoredInventoryJournalBlocked(ProtectedInventoryCleanup.REVIEW);
    }

    private static void assertRestoredInventoryJournalBlocked(int state) throws Exception {
        // The journal is already durable in this worker snapshot. Independently rolled-back
        // worker NBT predating that journal is deliberately not modeled by this regression.
        for(var kind:ProtectedStorageAccess.Kind.values())try(var f=new Fixture(true)) {
            var original=inventoryAdapters(f,(BuilderEntity)f.worker,f.workerData);
            ProtectedInventoryCleanup.record(f.workerData,kind,state);
            CompoundTag persistedWorker=f.workerData.copy();
            var corpseData=new CompoundTag();corpseData.putString("InventoryEvidence","leave corpse untouched");
            when(f.corpse.getPersistentData()).thenReturn(corpseData);var corpseBefore=corpseData.copy();

            f.remove(Entity.RemovalReason.KILLED);
            assertNotNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            f.reload();assertTrue(f.cancel());
            var terminal=PerimeterProjectStore.terminal(f.core,f.project.header().projectId());
            assertNotNull(terminal);assertEquals(PerimeterProject.State.CANCELED,terminal.state());
            assertNull(PerimeterProjectStore.get(f.core,f.project.header().projectId()));
            assertFalse(f.ledger.reserves(f.cells()));assertEquals(persistedWorker,f.workerData);
            original.assertNoInventoryCallbacks();
            f.reload();var compactCore=f.core.copy();var compactLedger=f.ledger.save(new CompoundTag());

            // A distinct entity and fresh adapter session have no live ownership of the old callbacks.
            var restoredData=persistedWorker.copy();var restored=inventoryAdapters(f,mock(BuilderEntity.class),restoredData);
            assertNotSame(f.worker,restored.worker());assertEquals(f.worker.getUUID(),restored.worker().getUUID());
            f.entities.put(restored.worker().getUUID(),restored.worker());
            assertFalse(NativeConstructionGuard.validHandReceipt(restoredData,f.ledger));
            assertTrue(NativeConstructionGuard.validRetirementHandReceipt(restoredData,restored.worker().getUUID(),
                    f.ledger,PerimeterProjectStore.snapshot(f.core)),"Exact terminal proof grants cleanup-only recognition");
            for(int retry=0;retry<2;retry++) {
                assertTrue(ProtectedStorageAccess.recoveryBlocked(restored.worker()));
                assertNotNull(ProtectedStorageAccess.runningProblem(restored.worker()));
                assertNotNull(NativeConstructionGuard.commissionProblem(restored.worker()),"No new commission may reuse the stale worker");
                assertNotNull(PerimeterProjectAuthority.problem(f.level,restored.worker(),f.area,false,false));
                assertFalse(ProtectedStorageAccess.drainCleanup(restored.worker()));
                // Reconciliation may allow ordinary living ticks while protected work stays blocked.
                assertTrue(NativePerimeterProjects.reconcileWorker(restored.worker()));
                assertFalse(NativeConstructionGuard.retireBuilderAssociation(restored.worker(),f.project.active().areaId()));
                assertFalse(NativeConstructionGuard.beforeNativeTick(restored.worker(),mock(Goal.class)));
                for(var wrapped:restored.goals()) {
                    var adapter=(ProtectedInventoryGoal)wrapped.getGoal();
                    assertFalse(adapter.canUse());adapter.start();adapter.tick();adapter.stop();
                    assertFalse(adapter.canContinueToUse());assertFalse(adapter.session.cleanupComplete());
                }
                assertFalse(f.cancel());
                assertTrue(PerimeterProjectLink.reserved(restored.worker()));assertSame(f.area,restored.worker().currentBuildArea);
                assertEquals(Map.of(kind,state),ProtectedInventoryCleanup.read(restoredData));
                var authoritativeData=restoredData.copy();authoritativeData.remove("SiegeConstructionPause");
                assertEquals(persistedWorker,authoritativeData,"Only the display-only pause reason may change");
                assertEquals(persistedWorker,f.workerData,"Removed worker NBT remains untouched");
                assertEquals(compactCore,f.core);assertEquals(compactLedger,f.ledger.save(new CompoundTag()));
            }
            restored.assertNoInventoryCallbacks();original.assertNoInventoryCallbacks();
            assertEquals(terminal.receiptHash(),PerimeterProjectStore.terminal(f.core,f.project.header().projectId()).receiptHash());
            assertEquals(64,terminal.payment().debited());assertEquals(936,FactionBank.balance(f.core));
            assertArrayEquals(new int[]{-64},FactionBank.ledgerDeltas(f.core));
            assertSame(f.corpse,f.entities.get(f.corpse.getUUID()));assertEquals(corpseBefore,corpseData);
            verify(f.corpse,never()).remove(any());verify(f.area,times(1)).removeAuthorized();
            verify(f.level,never()).setBlock(any(),any(),anyInt());verify(f.level,never()).destroyBlock(any(),anyBoolean());
            verify(f.level,never()).addFreshEntity(any());
            f.bridge.verify(()->WorkersBridge.detachBuildAreaReference(any(),any()),never());
        }
    }

    private record InventoryAdapters(BuilderEntity worker,List<Goal> delegates,Set<WrappedGoal> goals,
                                     SimpleContainer inventory,SimpleContainer source,NeededItem request) {
        void assertNoInventoryCallbacks() {
            delegates.forEach(goal->verifyNoInteractions(goal));
            verify(worker,never()).getInventory();verify(worker,never()).setItemSlot(any(),any());
            verify(worker,never()).remove(any());verify(worker,never()).resetPaymentTimer();
            assertEquals(173,worker.paymentTimer);assertEquals(List.of(request),worker.neededItems);
            assertEquals(7,inventory.getItem(0).getCount());assertTrue(inventory.getItem(0).is(Items.EMERALD));
            assertEquals(13,source.getItem(0).getCount());assertTrue(source.getItem(0).is(Items.COBBLESTONE));
            verify(source,never()).setChanged();
        }
    }

    private static InventoryAdapters inventoryAdapters(Fixture f,BuilderEntity worker,CompoundTag data) throws Exception {
        when(worker.getUUID()).thenReturn(f.project.header().builder());when(worker.level()).thenReturn(f.level);
        when(worker.getPersistentData()).thenReturn(data);when(worker.isAlive()).thenReturn(true);
        when(worker.getUseItem()).thenReturn(ItemStack.EMPTY);worker.paymentTimer=173;worker.currentBuildArea=f.area;
        f.bridge.when(()->WorkersBridge.isBuilder(worker)).thenReturn(true);
        f.bridge.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(f.project.header().owner());
        var request=new NeededItem(stack->true,3,true);worker.neededItems=new ArrayList<>(List.of(request));
        var inventory=new SimpleContainer(36);inventory.setItem(0,new ItemStack(Items.EMERALD,7));
        when(worker.getInventory()).thenReturn(inventory);
        var source=spy(new SimpleContainer(9));source.setItem(0,new ItemStack(Items.COBBLESTONE,13));clearInvocations(source);
        var needed=mock(GetNeededItemsFromStorage.class);needed.worker=worker;needed.container=source;needed.chestPos=f.marker;
        needed.state=GetNeededItemsFromStorage.State.TAKE_NEEDED_ITEMS;
        var deposit=mock(DepositItemsToStorage.class);deposit.worker=worker;deposit.container=source;deposit.chestPos=f.marker;
        deposit.state=DepositItemsToStorage.State.DEPOSIT;
        var position=mock(RecruitUpkeepPosGoal.class);position.recruit=worker;position.container=source;position.chestPos=f.marker;
        position.canResetPaymentTimer=true;position.setTimer=true;
        var entity=mock(RecruitUpkeepEntityGoal.class);entity.recruit=worker;entity.container=source;
        List<Goal> delegates=List.of(needed,deposit,position,entity);Set<WrappedGoal> goals=new LinkedHashSet<>();
        for(int i=0;i<delegates.size();i++) {
            var goal=delegates.get(i);when(goal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE));
            when(goal.canUse()).thenReturn(true);goals.add(new WrappedGoal(i+1,goal));
        }
        var selector=mock(GoalSelector.class);var field=Mob.class.getDeclaredField("goalSelector");
        field.setAccessible(true);field.set(worker,selector);when(selector.getAvailableGoals()).thenReturn(goals);
        doAnswer(i->{goals.removeIf(wrapped->wrapped.getGoal()==i.getArgument(0));return null;}).when(selector).removeGoal(any());
        doAnswer(i->{goals.add(new WrappedGoal(i.getArgument(0),i.getArgument(1)));return null;}).when(selector).addGoal(anyInt(),any());
        assertTrue(ProtectedStorageAccess.install(worker));
        assertEquals(2,goals.stream().filter(wrapped->wrapped.getGoal() instanceof ProtectedStorageAccess).count());
        assertEquals(2,goals.stream().filter(wrapped->wrapped.getGoal() instanceof ProtectedUpkeepAccess).count());
        delegates.forEach(goal->clearInvocations(goal));clearInvocations(worker);
        return new InventoryAdapters(worker,delegates,goals,inventory,source,request);
    }

    @Test void deathProofDoesNotBypassLoadedMarkerPositionScopeOrBuilderIdentity() throws Exception {
        List<Consumer<Fixture>> changes=List.of(
                f->when(f.level.hasChunkAt(f.marker)).thenReturn(false),
                f->when(f.area.blockPosition()).thenReturn(f.marker.east()),
                f->when(f.area.reservedBuilderId()).thenReturn(UUID.randomUUID()),
                f->f.areaData.getCompound(PerimeterProjectAuthority.KEY).putLong("Generation",f.project.header().generation()+1));
        for(var change:changes)try(var f=new Fixture()) {
            f.remove(Entity.RemovalReason.KILLED);change.accept(f);assertTrue(f.cancel());f.assertRetained();
            verify(f.area,never()).removeAuthorized();
        }
    }

    @Test void canceledDeadProjectWaitsForMarkerChunkThenCompactsExactlyOnce() {
        try(var f=new Fixture()) {
            f.remove(Entity.RemovalReason.KILLED);when(f.level.hasChunkAt(f.marker)).thenReturn(false);
            assertTrue(f.cancel());assertTrue(f.cancel());f.assertRetained();verify(f.area,never()).removeAuthorized();
            f.reload();when(f.level.hasChunkAt(f.marker)).thenReturn(true);
            assertTrue(f.cancel());assertNotNull(PerimeterProjectStore.terminal(f.core,f.project.header().projectId()));
            assertFalse(f.cancel());verify(f.area,times(1)).removeAuthorized();assertFalse(f.ledger.reserves(f.cells()));
        }
    }

    @Test void restoredLoadedWorkerStillNeedsLiveCleanupEvenWithEarlierDestructionProof() {
        try(var f=new Fixture();var guard=mockStatic(NativeConstructionGuard.class,
                invocation->invocation.getMethod().getName().equals("readyForRetirement")?false:CALLS_REAL_METHODS.answer(invocation))) {
            f.remove(Entity.RemovalReason.KILLED);assertNotNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            f.entities.put(f.worker.getUUID(),f.worker);
            assertTrue(f.cancel());f.assertRetained();guard.verify(()->NativeConstructionGuard.readyForRetirement(f.worker));
            guard.verify(()->NativeConstructionGuard.retireBuilderAssociation(any(),any()),never());
            verify(f.area,never()).removeAuthorized();
        }
    }

    @Test void incompleteBuilderSearchCannotTreatUnknownDimensionsAsAbsent() {
        try(var f=new Fixture()) {
            f.remove(Entity.RemovalReason.KILLED);when(f.server.getAllLevels()).thenReturn(Collections.nCopies(33,f.level));
            assertFalse(f.cancel());f.assertRetained();verify(f.area,never()).removeAuthorized();
        }
    }

    @Test void deathBetweenSectionsUsesRetiredPrefixAndPreservedWholeProjectLedgerGeneration() {
        try(var f=new Fixture()) {
            assertNull(PerimeterProjectLink.ledgerGeneration(f.workerData),"Older active saves have only their child ledger receipt");
            f.retireStage();assertEquals(PerimeterProject.State.WAITING_FOR_NEXT_STAGE,f.project.state());
            assertFalse(f.workerData.contains(AREA));assertFalse(f.workerData.contains(GENERATION));
            assertEquals(f.ledger.generation(),PerimeterProjectLink.ledgerGeneration(f.workerData));
            f.reload();f.storage.clearInvocations();CompoundTag workerBefore=f.workerData.copy();
            f.remove(Entity.RemovalReason.KILLED);assertNotNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            assertTrue(f.cancel());assertNotNull(PerimeterProjectStore.terminal(f.core,f.project.header().projectId()));
            assertFalse(f.ledger.reserves(f.cells()));assertEquals(workerBefore,f.workerData);f.storage.verifyNoInteractions();
            assertFalse(f.cancel());assertEquals(936,FactionBank.balance(f.core));
            assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker,null));
        }
    }

    @Test void deathAfterLastChildDetachesSupportsBothFinalVerificationCancellationAndCompletedCleanup() {
        for(boolean completed:new boolean[]{false,true})try(var f=new Fixture()) {
            while(f.project.active()!=null) {
                if(f.project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE)f.beginNextStage();
                f.retireStage();
            }
            assertEquals(PerimeterProject.State.VERIFYING_COMPLETE,f.project.state());
            if(completed)f.project=PerimeterProjectStore.replace(f.core,f.project.check(),f.project.complete(f.project.check(),f.project.manifestHash()),()->{});
            f.reload();f.storage.clearInvocations();f.remove(Entity.RemovalReason.KILLED);
            assertNotNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            if(completed) {
                f.bridge.when(WorkersBridge::available).thenReturn(true);when(f.server.isSameThread()).thenReturn(true);
                NativePerimeterProjects.tick(f.server);
            } else assertTrue(f.cancel());
            var terminal=PerimeterProjectStore.terminal(f.core,f.project.header().projectId());assertNotNull(terminal);
            assertEquals(completed?PerimeterProject.State.COMPLETE:PerimeterProject.State.CANCELED,terminal.state());
            assertFalse(f.ledger.reserves(f.cells()));assertEquals(936,FactionBank.balance(f.core));f.storage.verifyNoInteractions();
            verify(f.corpse,never()).remove(any());verify(f.worker,never()).setItemSlot(any(),any());
        }
    }

    @Test void wholeProjectOnlyDeathRejectsMissingOrWrongLedgerGenerationAndUnretiredOrAheadHistory() {
        List<Consumer<Fixture>> changes=List.of(
                f->f.workerData.getCompound(PerimeterProjectLink.KEY).remove("LedgerGeneration"),
                f->f.workerData.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration",UUID.randomUUID()),
                f->f.workerData.getCompound(PerimeterProjectLink.KEY).putString("LedgerGeneration","wrong type"),
                f->when(f.worker.getUUID()).thenReturn(UUID.randomUUID()),
                f->f.workerData.putUUID(GENERATION,f.ledger.generation()),
                f->f.core.getCompound(PerimeterStageJournal.KEY).getList("Entries",10).getCompound(0)
                        .getList("Attempts",10).getCompound(0).putString("State","LIVE"),
                f->assertTrue(f.ledger.leaseProjectStage(f.project)),
                f->{var saved=f.ledger.save(new CompoundTag());saved.getCompound("ProjectLeases").getList("Projects",10)
                        .getCompound(0).putBoolean("Retired",false);f.ledger=ConstructionEditLedger.load(saved);});
        for(var change:changes)try(var f=new Fixture()) {
            f.retireStage();change.accept(f);f.remove(Entity.RemovalReason.KILLED);
            assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));assertTrue(f.cancel());f.assertRetained();
        }
    }

    @Test void wholeProjectOnlyUnloadedWorkerRemainsReservedAndBoundGenerationCannotBeRewritten() {
        try(var f=new Fixture()) {
            f.retireStage();UUID accepted=f.ledger.generation();assertFalse(PerimeterProjectLink.bindLedgerGeneration(f.worker,UUID.randomUUID()));
            assertEquals(accepted,PerimeterProjectLink.ledgerGeneration(f.workerData));f.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));assertTrue(f.cancel());f.assertRetained();
            f.reload();assertEquals(accepted,PerimeterProjectLink.ledgerGeneration(f.workerData));assertTrue(f.cancel());f.assertRetained();
        }
    }

    @Test void oldBooleanMissingProofAndChangedLedgerGenerationRemainUncertainAfterReload() throws Exception {
        for(String alteration:List.of("legacy","generation","area","receipt"))try(var f=new Fixture()) {
            f.remove(Entity.RemovalReason.KILLED);CompoundTag saved=f.ledger.save(new CompoundTag());
            CompoundTag site=saved.getList("Sites",10).getCompound(0);
            switch(alteration) {
                case "legacy" -> site.remove("BuilderDestruction");
                case "generation" -> saved.putUUID("Generation",UUID.randomUUID());
                case "area" -> site.getCompound("BuilderDestruction").putUUID("Area",f.project.stages().get(1).areaId());
                case "receipt" -> site.getCompound("BuilderDestruction").putString("Receipt","0".repeat(64));
            }
            f.ledger=ConstructionEditLedger.load(saved);assertNull(f.ledger.projectBuilderDestructionReceipt(f.project));
            assertTrue(f.cancel());f.assertRetained();verify(f.area,never()).removeAuthorized();
        }
    }

    @Test void canceledCleanupRequiresExactJournalGenerationStageOrderAndLeasePrefix() {
        var p=ConstructionProjectLedgerTest.project();var marker=new BlockPos(8,64,8);
        var attempt=new PerimeterStageJournal.Attempt(0,p.active().areaId(),marker,PerimeterStageJournal.State.LIVE);
        var exact=new PerimeterStageJournal.Entry(p.header().projectId(),p.header().generation(),p.manifestHash(),List.of(attempt));
        assertTrue(NativePerimeterProjects.cleanupHistoryMatches(p,exact,0));
        assertFalse(NativePerimeterProjects.cleanupHistoryMatches(p,exact,-1));
        assertFalse(NativePerimeterProjects.cleanupHistoryMatches(p,exact,1));
        assertFalse(NativePerimeterProjects.cleanupHistoryMatches(p,new PerimeterStageJournal.Entry(UUID.randomUUID(),exact.generation(),exact.hash(),exact.attempts()),0));
        assertFalse(NativePerimeterProjects.cleanupHistoryMatches(p,new PerimeterStageJournal.Entry(exact.project(),exact.generation()+1,exact.hash(),exact.attempts()),0));
        var wrong=new PerimeterStageJournal.Attempt(0,p.stages().get(1).areaId(),marker,PerimeterStageJournal.State.LIVE);
        assertFalse(NativePerimeterProjects.cleanupHistoryMatches(p,new PerimeterStageJournal.Entry(exact.project(),exact.generation(),exact.hash(),List.of(wrong)),0));
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class Fixture implements AutoCloseable {
        PerimeterProject project=ConstructionProjectLedgerTest.project();CompoundTag core=new CompoundTag();
        ConstructionEditLedger ledger=new ConstructionEditLedger();final RaidSavedData data=new RaidSavedData();
        final MinecraftServer server=mock(MinecraftServer.class);final ServerLevel level=mock(ServerLevel.class);
        final ServerChunkCache chunks=mock(ServerChunkCache.class);
        final ServerPlayer owner=mock(ServerPlayer.class);final Mob worker;
        final ProtectedBuildArea area=mock(ProtectedBuildArea.class);final Entity corpse=mock(Entity.class);
        final CompoundTag workerData=new CompoundTag(),areaData=new CompoundTag();final BlockPos marker=new BlockPos(8,64,8);
        final Map<UUID,Entity> entities=new HashMap<>();
        final MockedStatic<RaidSavedData> saved=mockStatic(RaidSavedData.class);
        final MockedStatic<SiegeCore> cores=mockStatic(SiegeCore.class);
        final MockedStatic<WorkersBridge> bridge=mockStatic(WorkersBridge.class);
        final MockedStatic<ConstructionEditLedger> ledgers=mockStatic(ConstructionEditLedger.class,
                invocation->invocation.getMethod().getName().equals("get")?ledger:CALLS_REAL_METHODS.answer(invocation));
        final MockedStatic<PerimeterProjectAuthority> authority=mockStatic(PerimeterProjectAuthority.class,
                invocation->invocation.getMethod().getName().equals("snapshot")?PerimeterProjectStore.snapshot(core):CALLS_REAL_METHODS.answer(invocation));
        final MockedStatic<ProtectedStorageAccess> storage;

        Fixture() {this(false);}
        Fixture(boolean realInventoryAdapters) {
            worker=realInventoryAdapters?mock(BuilderEntity.class):mock(Mob.class);
            storage=realInventoryAdapters?null:mockStatic(ProtectedStorageAccess.class);
            var h=project.header();core.putLong("BankEmeralds",1000);core.putLong("Position",h.originalCore().asLong());data.siegeCores.put(h.coreKey(),core);
            PerimeterProjectStore.prepare(core,project,()->{});PerimeterStageJournal.prepare(core,project,()->{});
            assertTrue(ledger.registerProject(project));assertTrue(ledger.leaseProjectStage(project));
            assertNotNull(PerimeterStageJournal.begin(core,project,marker,()->{}));PerimeterStageJournal.live(core,project,()->{});
            project=PerimeterProjectStore.consumeOnce(core,h.projectId(),project.manifestHash(),64,false,()->{}).project();
            project=PerimeterProjectStore.replace(core,project.check(),project.activate(project.check()),()->{});
            saved.when(()->RaidSavedData.get(any())).thenReturn(data);
            when(level.getServer()).thenReturn(server);when(server.overworld()).thenReturn(level);when(server.getAllLevels()).thenReturn(List.of(level));
            when(level.dimension()).thenReturn(Level.OVERWORLD);when(level.hasChunkAt(any())).thenReturn(true);
            when(level.areEntitiesLoaded(anyLong())).thenReturn(true);when(level.getChunkSource()).thenReturn(chunks);
            when(chunks.isPositionTicking(anyLong())).thenReturn(true);
            when(level.getEntity(any(UUID.class))).thenAnswer(i->entities.get(i.getArgument(0)));
            when(owner.getUUID()).thenReturn(h.owner());when(owner.serverLevel()).thenReturn(level);when(owner.isAlive()).thenReturn(true);
            cores.when(()->SiegeCore.key(owner)).thenReturn(h.coreKey());cores.when(()->SiegeCore.canUse(eq(owner),any())).thenReturn(true);
            when(worker.getUUID()).thenReturn(h.builder());when(worker.level()).thenReturn(level);when(worker.isAlive()).thenReturn(true);
            when(worker.getUseItem()).thenReturn(ItemStack.EMPTY);
            when(worker.getPersistentData()).thenReturn(workerData);PerimeterProjectLink.set(worker,project);
            workerData.putUUID(AREA,project.active().areaId());workerData.putUUID(GENERATION,ledger.generation());
            assertTrue(ledger.retainHandLifecycle(workerData,h.builder(),h.owner(),project.active().areaId()));
            bridge.when(()->WorkersBridge.isBuilder(worker)).thenReturn(true);bridge.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(h.owner());
            when(area.getUUID()).thenReturn(project.active().areaId());when(area.getPersistentData()).thenReturn(areaData);
            when(area.blockPosition()).thenReturn(marker);when(area.reservedBuilderId()).thenReturn(h.builder());when(area.level()).thenReturn(level);
            bridge.when(()->WorkersBridge.readOwner(area)).thenReturn(h.owner());PerimeterProjectAuthority.stamp(area,project);
            doAnswer(i->{entities.remove(area.getUUID());return null;}).when(area).removeAuthorized();entities.put(area.getUUID(),area);
            when(corpse.getUUID()).thenReturn(UUID.randomUUID());entities.put(corpse.getUUID(),corpse);
        }
        void remove(Entity.RemovalReason reason) {
            when(worker.getRemovalReason()).thenReturn(reason);
            NativeConstructionGuard.areaRemoved(new EntityLeaveLevelEvent(worker,level));
        }
        boolean cancel() {return NativePerimeterProjects.cancel(owner,project.header().projectId(),project.header().generation());}
        Set<BlockPos> cells() {var cells=new HashSet<BlockPos>();project.reservation().forEach(p->cells.add(BlockPos.of(p)));return cells;}
        void assertRetained() {
            assertNotNull(PerimeterProjectStore.get(core,project.header().projectId()));
            assertNull(PerimeterProjectStore.terminal(core,project.header().projectId()));assertTrue(ledger.reserves(cells()));
        }
        void reload() {
            core=core.copy();data.siegeCores.put(project.header().coreKey(),core);
            ledger=ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        }
        void retireStage() {
            UUID child=project.active().areaId();
            project=PerimeterProjectStore.replace(core,project.check(),project.verifyStage(project.check(),project.expectedStageReceipt()),()->{});
            bridge.when(()->WorkersBridge.detachBuildAreaReference(worker,child)).thenReturn(true);
            storage.when(()->ProtectedStorageAccess.drainCleanup(worker)).thenReturn(true);
            assertTrue(NativeConstructionGuard.retireBuilderAssociation(worker,child));
            ledger.retire(child,true);entities.remove(child);PerimeterStageJournal.retired(core,project,()->{});
            project=PerimeterProjectStore.replace(core,project.check(),project.retireVerifiedStage(project.check()),()->{});
        }
        void beginNextStage() {
            assertTrue(ledger.leaseProjectStage(project));assertNotNull(PerimeterStageJournal.begin(core,project,marker,()->{}));
            PerimeterStageJournal.live(core,project,()->{});workerData.putUUID(AREA,project.active().areaId());workerData.putUUID(GENERATION,ledger.generation());
            project=PerimeterProjectStore.replace(core,project.check(),project.activate(project.check()),()->{});
        }
        public void close() {if(storage!=null)storage.close();authority.close();ledgers.close();bridge.close();cores.close();saved.close();}
    }
}
