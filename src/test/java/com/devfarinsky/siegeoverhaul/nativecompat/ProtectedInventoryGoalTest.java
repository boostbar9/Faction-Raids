package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.world.Container;
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
        String cleanupProblem;int cleanups;boolean upkeep;
        Adapter(BuilderEntity worker,NativeGoal nativeGoal,Session session){super(worker,nativeGoal,session);}
        boolean upkeep(){return upkeep;}
        String beforeStart(){return null;}String beforeTick(){return null;}
        String cleanup(){cleanups++;return cleanupProblem;}
    }
    @Test void canceledReceiptDoesNotReleaseDeferredUnloadedCleanup() {
        BuilderEntity worker=mock(BuilderEntity.class);var nativeGoal=new NativeGoal();
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
        BuilderEntity worker=mock(BuilderEntity.class);var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);goal.start();
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            goal.cleanupProblem="unloaded";goal.stop();assertEquals(0,nativeGoal.stops);assertFalse(session.ready());
        }
    }
    @Test void lateOldPositionStopCannotOverwriteANewEntityUpkeepLifecycle() {
        BuilderEntity worker=mock(BuilderEntity.class);var oldNative=new NativeGoal();var newNative=new NativeGoal();
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
        BuilderEntity worker=mock(BuilderEntity.class);var oldNative=new NativeGoal();var nextNative=new NativeGoal();var supplyNative=new NativeGoal();
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
        BuilderEntity worker=mock(BuilderEntity.class);var nativeGoal=new NativeGoal();nativeGoal.throwStop=true;
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            goal.start();assertDoesNotThrow(goal::stop);assertEquals(1,nativeGoal.stops);
            assertFalse(session.ready());assertFalse(session.ready());assertEquals(1,nativeGoal.stops);
        }
    }
    @Test void legacyLifecycleDelegatesWithoutProtectedReadChecks() {
        BuilderEntity worker=mock(BuilderEntity.class);var nativeGoal=new NativeGoal();
        var session=new ProtectedInventoryGoal.Session(worker);var goal=new Adapter(worker,nativeGoal,session);goal.cleanupProblem="unloaded";
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            assertTrue(goal.canUse());goal.start();goal.tick();goal.stop();
            assertEquals(1,nativeGoal.starts);assertEquals(1,nativeGoal.ticks);assertEquals(1,nativeGoal.stops);assertEquals(0,goal.cleanups);
        }
    }
    @Test void nativeSourceIsDirtiedEvenWhenOriginalTransferThrows() {
        Container source=mock(Container.class);
        assertThrows(IllegalStateException.class,()->ProtectedStorageAccess.notifyAfterNativeTransfer(source,()->{throw new IllegalStateException();}));
        verify(source).setChanged();
    }
}
