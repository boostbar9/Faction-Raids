package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.world.Container;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProtectedInventoryGoalTest extends com.devfarinsky.siegeoverhaul.MinecraftTestSupport {
    private static final class NativeGoal extends Goal {
        int starts,ticks,stops;boolean throwStop;
        public boolean canUse(){return true;}
        public boolean canContinueToUse(){return true;}
        public void start(){starts++;}
        public void tick(){ticks++;}
        public void stop(){stops++;if(throwStop)throw new IllegalStateException("partial native stop");}
    }
    private static final class Adapter extends ProtectedInventoryGoal {
        String cleanupProblem;int cleanups;boolean upkeep;boolean obligation=true;
        ProtectedStorageAccess.Kind kind(){return upkeep?ProtectedStorageAccess.Kind.POSITION_UPKEEP:ProtectedStorageAccess.Kind.NEEDED;}
        boolean cleanupObligation(){return obligation;}
        Adapter(BuilderEntity worker,NativeGoal nativeGoal,Session session){super(worker,nativeGoal,session);}
        boolean upkeep(){return upkeep;}
        String beforeStart(){return null;}String beforeTick(){return null;}
        String cleanup(){cleanups++;return cleanupProblem;}
    }
    private static BuilderEntity worker(CompoundTag data) {
        var worker=mock(BuilderEntity.class);when(worker.getPersistentData()).thenReturn(data);return worker;
    }

    @Test void canceledReceiptDoesNotReleaseDeferredUnloadedCleanup() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            goal.start();goal.cleanupProblem="load old source";goal.stop();
            assertEquals(0,nativeGoal.stops);assertEquals(1,session.pending.size());
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            assertFalse(session.ready());assertEquals(0,nativeGoal.stops);
            goal.cleanupProblem=null;assertTrue(session.ready());assertEquals(1,nativeGoal.stops);
            assertTrue(session.pending.isEmpty());assertTrue(session.ready());assertEquals(1,nativeGoal.stops);
        }
    }
    @Test void currentLifecycleKeepsCleanupObligationWhenCancellationPrecedesStop() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            goal.cleanupProblem="unloaded";goal.stop();assertEquals(0,nativeGoal.stops);assertFalse(session.ready());
        }
    }
    @Test void receiptLossDuringRunningLifecycleStopsWithoutAnyLegacyTransferTick() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            goal.tick();assertEquals(0,nativeGoal.ticks);assertEquals(1,nativeGoal.stops);assertEquals(1,goal.cleanups);
            assertFalse(goal.canContinueToUse());goal.tick();goal.stop();assertEquals(1,nativeGoal.stops);
            // After that protected obligation closes, a genuinely new legacy
            // lifecycle still behaves like the original native goal.
            goal.start();goal.tick();assertEquals(1,nativeGoal.ticks);goal.stop();assertEquals(2,nativeGoal.stops);
        }
    }
    @Test void receiptLossRetainsUnloadedCleanupUntilItsSafeStopCanDrain() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();
            goal.cleanupProblem="old chest unloaded";
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            goal.tick();assertEquals(0,nativeGoal.ticks);assertEquals(0,nativeGoal.stops);assertTrue(session.pending.contains(goal));
            assertFalse(goal.canContinueToUse());goal.tick();assertFalse(session.ready());assertFalse(goal.canUse());
            assertEquals(0,nativeGoal.ticks);assertEquals(0,nativeGoal.stops);
            goal.cleanupProblem=null;assertTrue(session.ready());assertEquals(1,nativeGoal.stops);assertTrue(session.pending.isEmpty());
            goal.tick();assertEquals(0,nativeGoal.ticks);
        }
    }
    @Test void partialCleanupAfterReceiptLossNeverRetriesStopOrFallsBackToTransfer() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();nativeGoal.throwStop=true;
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            assertDoesNotThrow(goal::tick);assertEquals(1,nativeGoal.stops);assertEquals(0,nativeGoal.ticks);
            assertTrue(session.pending.contains(goal));assertFalse(session.ready());assertFalse(session.ready());
            goal.tick();goal.start();goal.tick();assertEquals(1,nativeGoal.starts);assertEquals(1,nativeGoal.stops);assertEquals(0,nativeGoal.ticks);
        }
    }
    @Test void lateOldPositionStopCannotOverwriteANewEntityUpkeepLifecycle() {
        BuilderEntity worker=worker(new CompoundTag());var oldNative=new NativeGoal();var newNative=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);
        var oldGoal=new Adapter(worker,oldNative,session);var newGoal=new Adapter(worker,newNative,session);
        oldGoal.upkeep=true;newGoal.upkeep=true;
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            oldGoal.start();oldGoal.cleanupProblem="old target unloaded after owner/target change";oldGoal.stop();
            assertFalse(newGoal.canUse());newGoal.start();newGoal.tick();assertEquals(0,newNative.starts);assertEquals(0,newNative.ticks);
            oldGoal.cleanupProblem=null;assertTrue(newGoal.canUse());assertEquals(1,oldNative.stops);
            newGoal.start();newGoal.tick();assertEquals(1,newNative.starts);assertEquals(1,newNative.ticks);
            assertTrue(session.ready());assertEquals(1,oldNative.stops);
        }
    }
    @Test void protectedUpkeepObligationBlocksOnlySiblingUpkeepEvenAfterCancellation() {
        BuilderEntity worker=worker(new CompoundTag());var oldNative=new NativeGoal();var nextNative=new NativeGoal();var supplyNative=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);
        var oldGoal=new Adapter(worker,oldNative,session);oldGoal.upkeep=true;
        var nextGoal=new Adapter(worker,nextNative,session);nextGoal.upkeep=true;
        var supply=new Adapter(worker,supplyNative,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            oldGoal.start();assertFalse(nextGoal.canUse()); // Even before cleanup becomes deferred.
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            assertFalse(nextGoal.canUse());oldGoal.cleanupProblem="unloaded old chest";oldGoal.stop();
            assertFalse(nextGoal.canUse());assertTrue(supply.canUse());supply.start();supply.tick();assertEquals(1,supplyNative.ticks);
            oldGoal.cleanupProblem=null;assertTrue(nextGoal.canUse());assertEquals(1,oldNative.stops);
            nextGoal.start();assertEquals(1,nextNative.starts);
        }
    }

    @Test void partialStopFailureIsCaughtAndNeverReplayed() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();nativeGoal.throwStop=true;
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            goal.start();assertDoesNotThrow(goal::stop);assertEquals(1,nativeGoal.stops);
            assertFalse(session.ready());assertFalse(session.ready());assertEquals(1,nativeGoal.stops);
        }
    }
    @Test void legacyLifecycleDelegatesWithoutProtectedReadChecks() {
        BuilderEntity worker=worker(new CompoundTag());var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);goal.cleanupProblem="unloaded";
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            assertTrue(goal.canUse());goal.start();goal.tick();goal.stop();
            assertEquals(1,nativeGoal.starts);assertEquals(1,nativeGoal.ticks);assertEquals(1,nativeGoal.stops);assertEquals(0,goal.cleanups);
        }
    }

    @Test void deferredCancellationCleanupSurvivesFreshWorkerAndSessionWithoutReplaying() {
        var worker=worker(new CompoundTag());var original=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,original,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            goal.start();goal.cleanupProblem="unloaded source";goal.stop();
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            var saved=worker.getPersistentData().copy();var reopened=worker(saved);
            var freshNative=new NativeGoal();var freshSession=new ProtectedInventoryGoal.Session(reopened);
            var fresh=new Adapter(reopened,freshNative,freshSession);
            assertFalse(freshSession.ready());assertFalse(freshSession.cleanupComplete());assertFalse(fresh.canUse());
            fresh.start();fresh.tick();fresh.stop();
            assertEquals(0,original.stops);assertEquals(0,freshNative.starts);assertEquals(0,freshNative.stops);
            assertEquals(saved, reopened.getPersistentData());
            assertEquals(ProtectedInventoryCleanup.CLEANUP,ProtectedInventoryCleanup.read(saved).get(goal.kind()));
        }
    }

    @Test void partialStopRemainsReviewAfterCancelSaveAndFreshGoal() {
        var worker=worker(new CompoundTag());var original=new NativeGoal();original.throwStop=true;
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,original,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();goal.stop();
            var saved=worker.getPersistentData().copy();var reopened=worker(saved);
            var freshNative=new NativeGoal();var freshSession=new ProtectedInventoryGoal.Session(reopened);
            var fresh=new Adapter(reopened,freshNative,freshSession);
            fresh.start();fresh.stop();assertFalse(freshSession.ready());assertFalse(freshSession.cleanupComplete());
            assertEquals(1,original.stops);assertEquals(0,freshNative.stops);
            assertEquals(ProtectedInventoryCleanup.REVIEW,ProtectedInventoryCleanup.read(saved).get(goal.kind()));
        }
    }

    @Test void safeCheckpointAndCompletedStopPermitNormalSaveReopen() {
        var worker=worker(new CompoundTag());var original=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,original,session);goal.obligation=false;
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();goal.tick();
            assertFalse(ProtectedInventoryCleanup.outstanding(worker.getPersistentData()));
            var reopened=worker(worker.getPersistentData().copy());var freshSession=new ProtectedInventoryGoal.Session(reopened);
            assertTrue(freshSession.ready());assertTrue(freshSession.cleanupComplete());
            goal.obligation=true;goal.tick();assertTrue(ProtectedInventoryCleanup.outstanding(worker.getPersistentData()));
            goal.stop();assertFalse(ProtectedInventoryCleanup.outstanding(worker.getPersistentData()));
            assertTrue(new ProtectedInventoryGoal.Session(worker(worker.getPersistentData().copy())).ready());
        }
    }

    @Test void callbackFenceSurvivesSnapshotTakenInsideNativeCallback() {
        var worker=worker(new CompoundTag());var snapshots=new java.util.ArrayList<CompoundTag>();
        Goal nativeGoal=new Goal() {
            public boolean canUse(){return true;}
            public void start(){snapshots.add(worker.getPersistentData().copy());}
        };
        var session=new ProtectedInventoryGoal.Session(worker);
        var goal=new ProtectedInventoryGoal(worker,nativeGoal,session) {
            ProtectedStorageAccess.Kind kind(){return ProtectedStorageAccess.Kind.NEEDED;}
            String beforeStart(){return null;}String beforeTick(){return null;}String cleanup(){return null;}
            boolean cleanupObligation(){return false;}
        };
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();
            assertFalse(ProtectedInventoryCleanup.outstanding(worker.getPersistentData()));
            assertFalse(new ProtectedInventoryGoal.Session(worker(snapshots.get(0))).ready());
            assertEquals(ProtectedInventoryCleanup.REVIEW,ProtectedInventoryCleanup.read(snapshots.get(0)).get(goal.kind()));
        }
    }

    @Test void nativeSourceIsDirtiedEvenWhenOriginalTransferThrows() {
        Container source=mock(Container.class);
        assertThrows(IllegalStateException.class,()->ProtectedStorageAccess.notifyAfterNativeTransfer(source,()->{throw new IllegalStateException();}));
        verify(source).setChanged();
    }
}
