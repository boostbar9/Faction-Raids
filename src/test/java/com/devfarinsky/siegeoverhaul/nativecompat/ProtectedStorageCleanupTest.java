package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.AbstractChestGoal;
import com.talhanation.workers.entities.ai.DepositItemsToStorage;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectedStorageCleanupTest extends MinecraftTestSupport {
    private record Fixture(BuilderEntity worker, GetNeededItemsFromStorage nativeGoal,
                           ProtectedInventoryGoal.Session session, ProtectedStorageAccess adapter,
                           WrappedGoal wrapped, List<WrappedGoal> goals) {}

    private static ProtectedStorageAccess adapter(BuilderEntity worker,AbstractChestGoal nativeGoal,
                                                   ProtectedStorageAccess.Kind kind,ProtectedInventoryGoal.Session session) throws Exception {
        nativeGoal.worker=worker;
        when(nativeGoal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE));
        var constructor=ProtectedStorageAccess.class.getDeclaredConstructor(BuilderEntity.class,AbstractChestGoal.class,
                ProtectedStorageAccess.Kind.class,ProtectedInventoryGoal.Session.class);
        constructor.setAccessible(true);return constructor.newInstance(worker,nativeGoal,kind,session);
    }
    private static Fixture fixture() throws Exception {
        var worker=mock(BuilderEntity.class);when(worker.getPersistentData()).thenReturn(new CompoundTag());
        worker.neededItems=new ArrayList<>();
        var nativeGoal=mock(GetNeededItemsFromStorage.class);
        var session=spy(new ProtectedInventoryGoal.Session(worker));
        var adapter=adapter(worker,nativeGoal,ProtectedStorageAccess.Kind.NEEDED,session);
        var wrapped=new WrappedGoal(1,adapter);
        var goals=new ArrayList<>(List.of(wrapped,new WrappedGoal(2,mock(DepositItemsToStorage.class)),
                new WrappedGoal(3,mock(RecruitUpkeepPosGoal.class)),new WrappedGoal(4,mock(RecruitUpkeepEntityGoal.class))));
        return new Fixture(worker,nativeGoal,session,adapter,wrapped,goals);
    }

    @Test void cleanupStopsProtectedWrapperAndLeavesDormantRequestsInventoryAndReviewUntouched() throws Exception {
        var f=fixture();var requested=new NeededItem(stack->true,1,true);
        f.worker().neededItems=new ArrayList<>(List.of(requested));
        var data=f.worker().getPersistentData();ProtectedBuilderHandMirror.requireInventoryReview(data);var before=data.copy();
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);
            f.wrapped().start();clearInvocations(f.worker(),f.nativeGoal(),f.session());
            assertTrue(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            assertFalse(f.wrapped().isRunning());assertTrue(f.adapter().cleanupComplete());assertTrue(f.session().cleanupComplete());
            verify(f.nativeGoal(),times(1)).stop();verify(f.nativeGoal(),never()).tick();verify(f.session(),times(1)).ready();
            verify(f.worker(),never()).getInventory();assertEquals(before,data);
            assertEquals(List.of(requested),f.worker().neededItems);
            assertTrue(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));verify(f.nativeGoal(),times(1)).stop();
        }
    }

    @Test void pendingUnloadedCleanupBlocksUntilSourcesReturnWithoutTransferring() throws Exception {
        var f=fixture();var level=mock(ServerLevel.class);var source=mock(Container.class);var pos=new BlockPos(4,65,4);
        f.nativeGoal().chestPos=pos;f.nativeGoal().container=source;var blocked=new AtomicBoolean(true);
        try(var guard=mockStatic(NativeConstructionGuard.class);var context=mockStatic(ProtectedStorageContext.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);
            context.when(()->ProtectedStorageContext.level(f.worker())).thenReturn(level);
            context.when(()->ProtectedStorageContext.cleanup(level,source,pos)).thenAnswer(invocation->{
                if(blocked.get())throw new IllegalStateException("source unloaded");return null;
            });
            f.wrapped().start();clearInvocations(f.nativeGoal(),f.session());
            assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            assertFalse(f.wrapped().isRunning());assertTrue(f.session().pending.contains(f.adapter()));
            verify(f.nativeGoal(),never()).stop();verify(f.nativeGoal(),never()).tick();verify(f.session(),times(1)).ready();
            blocked.set(false);assertTrue(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            verify(f.nativeGoal(),times(1)).stop();verify(f.nativeGoal(),never()).tick();assertTrue(f.session().cleanupComplete());
        }
    }

    @Test void partialNativeStopNeverReplaysEvenDuringRepeatedDrains() throws Exception {
        var f=fixture();doThrow(new IllegalStateException("partial stop")).when(f.nativeGoal()).stop();
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);f.wrapped().start();
            assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            verify(f.nativeGoal(),times(1)).stop();verify(f.nativeGoal(),never()).tick();assertTrue(f.session().pending.contains(f.adapter()));
        }
    }

    @Test void runningUnwrappedOrLegacyLifecycleBlocksBeforeAnyOtherGoalIsStopped() throws Exception {
        var f=fixture();
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);f.wrapped().start();
            var raw=f.goals().get(1);raw.start();
            assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            verify(f.nativeGoal(),never()).stop();verify(raw.getGoal(),never()).stop();assertTrue(f.wrapped().isRunning());
            raw.stop();
            var other=fixture();other.wrapped().start(); // A genuine legacy lifecycle, without any receipt.
            assertTrue(other.adapter().legacyLifecycleActive());
            assertFalse(ProtectedStorageAccess.drainCleanup(other.worker(),other.goals()));
            verify(other.nativeGoal(),never()).stop();verify(other.nativeGoal(),never()).tick();assertTrue(other.wrapped().isRunning());
        }
    }

    @Test void unknownAdaptersWrongOwnersDuplicateKindsAndLostPendingMembersFailClosed() throws Exception {
        var f=fixture();var unknown=new UnknownAdapter(f.worker(),mock(Goal.class),f.session());
        var wrong=new ArrayList<>(f.goals());wrong.set(0,new WrappedGoal(1,unknown));
        assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),wrong));
        assertFalse(ProtectedStorageAccess.drainCleanup(mock(BuilderEntity.class),f.goals()));
        f.nativeGoal().worker=mock(BuilderEntity.class);
        assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));f.nativeGoal().worker=f.worker();
        var duplicate=new ArrayList<>(f.goals());duplicate.add(f.wrapped());
        assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),duplicate));
        var missing=new ArrayList<>(f.goals());missing.remove(1);assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),missing));
        f.session().pending.add(unknown);
        assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));verify(f.nativeGoal(),never()).stop();
        assertFalse(ProtectedStorageAccess.drainCleanup(f.worker())); // Missing selector is never quiescence evidence.
    }

    @Test void multipleAdaptersSharingASessionDrainItExactlyOnce() throws Exception {
        var f=fixture();var nativeDeposit=mock(DepositItemsToStorage.class);
        var deposit=adapter(f.worker(),nativeDeposit,ProtectedStorageAccess.Kind.DEPOSIT,f.session());
        var depositWrapped=new WrappedGoal(2,deposit);f.goals().set(1,depositWrapped);
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);
            f.wrapped().start();depositWrapped.start();clearInvocations(f.session());
            assertTrue(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));verify(f.session(),times(1)).ready();
            verify(f.nativeGoal(),times(1)).stop();verify(nativeDeposit,times(1)).stop();
            verify(f.nativeGoal(),never()).tick();verify(nativeDeposit,never()).tick();
        }
    }


    @Test void durableCleanupBlocksFreshWrappersAndRawGoalsAfterCanceledProjectReload() throws Exception {
        var f=fixture();var level=mock(ServerLevel.class);var source=mock(Container.class);var pos=new BlockPos(4,65,4);
        f.nativeGoal().chestPos=pos;f.nativeGoal().container=source;
        try(var guard=mockStatic(NativeConstructionGuard.class);var context=mockStatic(ProtectedStorageContext.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);
            context.when(()->ProtectedStorageContext.level(f.worker())).thenReturn(level);
            context.when(()->ProtectedStorageContext.cleanup(level,source,pos)).thenThrow(new IllegalStateException("source unloaded"));
            f.wrapped().start();assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
            var saved=f.worker().getPersistentData().copy();var reopened=fixture();
            when(reopened.worker().getPersistentData()).thenReturn(saved.copy());
            assertFalse(ProtectedStorageAccess.drainCleanup(reopened.worker(),reopened.goals()));
            assertFalse(ProtectedStorageAccess.drainCleanup(reopened.worker(),reopened.goals()));
            verify(reopened.nativeGoal(),never()).stop();verify(reopened.nativeGoal(),never()).tick();
            var raw=new ArrayList<>(reopened.goals());raw.set(0,new WrappedGoal(1,reopened.nativeGoal()));
            assertFalse(ProtectedStorageAccess.drainCleanup(reopened.worker(),raw));
            assertNotNull(ProtectedStorageAccess.runningProblem(reopened.worker()));
            assertEquals(saved,reopened.worker().getPersistentData());
        }
    }

    @Test void malformedOrUnknownJournalNeverBecomesQuiescentOrGetsOverwritten() throws Exception {
        for(boolean malformed:List.of(false,true)) {
            var f=fixture();var data=f.worker().getPersistentData();
            if(malformed)data.putString(ProtectedInventoryCleanup.KEY,"unknown format");
            else {var tag=new CompoundTag();tag.putInt("Version",2);data.put(ProtectedInventoryCleanup.KEY,tag);}
            var before=data.copy();
            try(var guard=mockStatic(NativeConstructionGuard.class)) {
                assertFalse(f.adapter().canUse());f.adapter().start();
                assertFalse(ProtectedStorageAccess.drainCleanup(f.worker(),f.goals()));
                verify(f.nativeGoal(),never()).start();verify(f.nativeGoal(),never()).stop();assertEquals(before,data);
            }
        }
    }


    private static void selector(Fixture f) throws Exception {
        var field=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
        field.set(f.worker(),mock(GoalSelector.class));
        when(f.worker().goalSelector.getAvailableGoals()).thenReturn(new java.util.LinkedHashSet<>(f.goals()));
    }

    @Test void orphanedOrPartialCallbacksBlockNativeConstructionBeforeAnyAreaReads() throws Exception {
        var f=fixture();selector(f);
        ProtectedInventoryCleanup.record(f.worker().getPersistentData(),ProtectedStorageAccess.Kind.NEEDED,ProtectedInventoryCleanup.CLEANUP);
        assertTrue(ProtectedStorageAccess.recoveryBlocked(f.worker()));
        assertFalse(NativeConstructionGuard.beforeNativeTick(f.worker(),mock(Goal.class)));
        verify(f.nativeGoal(),never()).start();verify(f.nativeGoal(),never()).tick();
        assertEquals(ProtectedInventoryCleanup.CLEANUP,ProtectedInventoryCleanup.read(f.worker().getPersistentData())
                .get(ProtectedStorageAccess.Kind.NEEDED));
    }

    @Test void gracefulShutdownDrainsOnlyLiveProtectedGoalsAndNormalReopenIsClear() throws Exception {
        var f=fixture();selector(f);
        var legacy=fixture();selector(legacy);legacy.wrapped().start();
        var server=mock(MinecraftServer.class);var level=mock(ServerLevel.class);
        when(server.getAllLevels()).thenReturn(List.of(level));
        when(level.getAllEntities()).thenReturn(List.of(f.worker(),legacy.worker()));
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);f.wrapped().start();
            ProtectedStorageAccess.drainOnShutdown(server);
            verify(f.nativeGoal(),times(1)).stop();verify(f.nativeGoal(),never()).tick();
            verify(legacy.nativeGoal(),never()).stop();verify(legacy.nativeGoal(),never()).tick();
            assertFalse(f.wrapped().isRunning());assertTrue(legacy.wrapped().isRunning());
            assertFalse(ProtectedInventoryCleanup.outstanding(f.worker().getPersistentData()));
            var reopened=fixture();var snapshot=f.worker().getPersistentData().copy();
            when(reopened.worker().getPersistentData()).thenReturn(snapshot);
            assertTrue(ProtectedStorageAccess.drainCleanup(reopened.worker(),reopened.goals()));
            verify(reopened.nativeGoal(),never()).stop();
            ProtectedStorageAccess.drainOnShutdown(server);verify(f.nativeGoal(),times(1)).stop();
        }
    }

    @Test void shutdownCannotReplayPartialCallbackOrForgetUnloadedSources() throws Exception {
        var f=fixture();selector(f);var level=mock(ServerLevel.class);var source=mock(Container.class);var pos=new BlockPos(4,65,4);
        var partial=fixture();selector(partial);doThrow(new IllegalStateException("partial stop")).when(partial.nativeGoal()).stop();
        f.nativeGoal().chestPos=pos;f.nativeGoal().container=source;
        var server=mock(MinecraftServer.class);when(server.getAllLevels()).thenReturn(List.of(level));
        when(level.getAllEntities()).thenReturn(List.of(f.worker(),partial.worker()));
        try(var guard=mockStatic(NativeConstructionGuard.class);var context=mockStatic(ProtectedStorageContext.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(partial.worker())).thenReturn(true);
            context.when(()->ProtectedStorageContext.level(f.worker())).thenReturn(level);
            context.when(()->ProtectedStorageContext.cleanup(level,source,pos)).thenThrow(new IllegalStateException("unloaded"));
            f.wrapped().start();partial.wrapped().start();
            ProtectedStorageAccess.drainOnShutdown(server);ProtectedStorageAccess.drainOnShutdown(server);
            verify(f.nativeGoal(),never()).stop();verify(partial.nativeGoal(),times(1)).stop();
            assertEquals(ProtectedInventoryCleanup.CLEANUP,ProtectedInventoryCleanup.read(f.worker().getPersistentData())
                    .get(ProtectedStorageAccess.Kind.NEEDED));
            assertEquals(ProtectedInventoryCleanup.REVIEW,ProtectedInventoryCleanup.read(partial.worker().getPersistentData())
                    .get(ProtectedStorageAccess.Kind.NEEDED));
            assertTrue(ProtectedStorageAccess.recoveryBlocked(partial.worker()));
        }
    }


    @Test void storageSearchAndSuccessfulCloseHaveSafeReloadCheckpointsButOpenChestDoesNot() throws Exception {
        var f=fixture();var level=mock(ServerLevel.class);var pos=new BlockPos(4,65,4);
        var bounds=new net.minecraft.world.phys.AABB(pos,pos);var source=mock(Container.class);
        var area=mock(com.talhanation.workers.entities.workarea.StorageArea.class);
        area.storageMap=new java.util.HashMap<>();f.nativeGoal().storageArea=area;
        when(level.getBlockState(pos.above())).thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        try(var guard=mockStatic(NativeConstructionGuard.class);var context=mockStatic(ProtectedStorageContext.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(f.worker())).thenReturn(true);
            context.when(()->ProtectedStorageContext.level(f.worker())).thenReturn(level);
            context.when(()->ProtectedStorageContext.storage(f.worker(),area,true)).thenReturn(bounds);
            context.when(()->ProtectedStorageContext.contains(bounds,pos)).thenReturn(true);
            context.when(()->ProtectedStorageContext.source(level,source,pos))
                    .thenReturn(new ProtectedStorageContext.Source(source,pos,java.util.Set.of(pos)));
            f.nativeGoal().state=GetNeededItemsFromStorage.State.SELECT_STORAGE;f.wrapped().start();f.adapter().tick();
            assertFalse(ProtectedInventoryCleanup.outstanding(f.worker().getPersistentData()));
            f.nativeGoal().state=GetNeededItemsFromStorage.State.SCAN_STORAGE;f.adapter().tick();
            assertFalse(ProtectedInventoryCleanup.outstanding(f.worker().getPersistentData()));
            f.nativeGoal().chestPos=pos;f.nativeGoal().container=source;area.storageMap.put(pos,source);
            f.nativeGoal().state=GetNeededItemsFromStorage.State.OPEN_CHEST;f.adapter().tick();
            assertEquals(ProtectedInventoryCleanup.CLEANUP,ProtectedInventoryCleanup.read(f.worker().getPersistentData())
                    .get(ProtectedStorageAccess.Kind.NEEDED));
            var reopened=fixture();var snapshot=f.worker().getPersistentData().copy();
            when(reopened.worker().getPersistentData()).thenReturn(snapshot);
            assertFalse(ProtectedStorageAccess.drainCleanup(reopened.worker(),reopened.goals()));
            f.nativeGoal().state=GetNeededItemsFromStorage.State.CLOSE_CHEST_DONE;f.adapter().tick();
            assertFalse(ProtectedInventoryCleanup.outstanding(f.worker().getPersistentData()));
            var closedSnapshot=f.worker().getPersistentData().copy();
            when(reopened.worker().getPersistentData()).thenReturn(closedSnapshot);
            assertTrue(ProtectedStorageAccess.drainCleanup(reopened.worker(),reopened.goals()));
            verify(source,never()).setChanged();verify(f.nativeGoal(),never()).stop();
        }
    }

    private static final class UnknownAdapter extends ProtectedInventoryGoal {
        UnknownAdapter(BuilderEntity worker,Goal delegate,Session session) {
            super(worker,flags(delegate),session);
        }
        private static Goal flags(Goal goal) {when(goal.getFlags()).thenReturn(EnumSet.noneOf(Goal.Flag.class));return goal;}
        ProtectedStorageAccess.Kind kind(){return ProtectedStorageAccess.Kind.NEEDED;}
        String beforeStart(){return null;}String beforeTick(){return null;}String cleanup(){return null;}
    }
}
