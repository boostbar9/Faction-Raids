package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the real dependency goal's internal stop paths, not just wrapper.stop(). */
class ProtectedUpkeepCallbackTest extends MinecraftTestSupport {
    @Test void nativeStartCallingPartlyFailingStopIsNeverReplayed() throws Exception {
        exerciseInternalStop(true);
    }

    @Test void nativeTimerExpiryCallingPartlyFailingStopWithNoWritesIsNeverReplayed() throws Exception {
        exerciseInternalStop(false);
    }

    private static void exerciseInternalStop(boolean duringStart) throws Exception {
        var worker=mock(BuilderEntity.class);var data=new CompoundTag();
        when(worker.getPersistentData()).thenReturn(data);
        var level=mock(ServerLevel.class);var pos=new BlockPos(0,64,0);var source=mock(BarrelBlockEntity.class);
        when(worker.getUpkeepPos()).thenReturn(pos);when(worker.getOnPos()).thenReturn(pos);
        when(worker.position()).thenReturn(duringStart?new Vec3(200,64,0):Vec3.atCenterOf(pos));
        when(worker.getCommandSenderWorld()).thenReturn(level);
        when(worker.getNavigation()).thenReturn(mock(PathNavigation.class));
        when(worker.getLookControl()).thenReturn(mock(LookControl.class));
        when(level.getBlockState(pos)).thenReturn(Blocks.BARREL.defaultBlockState());
        when(level.getBlockEntity(pos)).thenReturn(source);
        var nativeGoal=spy(new RecruitUpkeepPosGoal(worker));
        // A failure after the timer/payment side effects is genuinely partial.
        nativeGoal.canResetPaymentTimer=true;worker.paymentTimer=0;
        doThrow(new IllegalStateException("dirty callback failed after payment/timer finalization")).when(source).setChanged();
        var session=new ProtectedInventoryGoal.Session(worker);
        var adapter=new ProtectedUpkeepAccess(worker,nativeGoal,ProtectedStorageAccess.Kind.POSITION_UPKEEP,session);
        try(var guard=mockStatic(NativeConstructionGuard.class);
            var context=mockStatic(ProtectedStorageContext.class);
            var capacity=mockStatic(ProtectedTransferCapacity.class)) {
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(true);
            context.when(()->ProtectedStorageContext.level(worker)).thenReturn(level);
            context.when(()->ProtectedStorageContext.source(level,source,pos))
                    .thenReturn(new ProtectedStorageContext.Source(source,pos,Set.of(pos)));
            assertDoesNotThrow(adapter::start);
            if(!duringStart) {
                nativeGoal.setTimer=true;nativeGoal.timer=1;
                assertDoesNotThrow(adapter::tick);
                assertTrue(adapter.writes.isEmpty(),"timer-expiry cleanup selects no transfer writes");
                verify(nativeGoal,times(1)).tick();
            }
            verify(nativeGoal,times(1)).stop();verify(worker,times(1)).resetPaymentTimer();
            assertFalse(nativeGoal.canResetPaymentTimer);assertFalse(nativeGoal.setTimer);
            assertFalse(adapter.canContinueToUse());
            guard.when(()->NativeConstructionGuard.hasProtectedReceipt(worker)).thenReturn(false);
            adapter.stop();adapter.start();adapter.tick();assertFalse(session.ready());assertFalse(session.ready());
            verify(nativeGoal,times(1)).stop();verify(source,times(1)).setChanged();
            var saved=data.copy();assertEquals(ProtectedInventoryCleanup.REVIEW,
                    ProtectedInventoryCleanup.read(saved).get(ProtectedStorageAccess.Kind.POSITION_UPKEEP));
            var reopened=mock(BuilderEntity.class);when(reopened.getPersistentData()).thenReturn(saved);
            var freshSession=new ProtectedInventoryGoal.Session(reopened);
            assertFalse(freshSession.ready());assertFalse(freshSession.cleanupComplete());
        }
    }
}
