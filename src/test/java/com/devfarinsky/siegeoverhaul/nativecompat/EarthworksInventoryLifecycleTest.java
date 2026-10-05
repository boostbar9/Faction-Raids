package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual owning wrapper lifecycle; lease and native callbacks are mocked, not native gameplay proof. */
class EarthworksInventoryLifecycleTest extends MinecraftTestSupport {
    @Test void changedStepProjectAndAreaStopThroughOldCleanupBeforeAnyTransfer() {
        for (int kind=0;kind<3;kind++) try (var f=new Fixture()) {
            var old=f.lease.get(); f.goal.start(); verify(f.delegate).start();
            f.lease.set(new NativeEarthworksJobs.InventoryLease(kind==0?UUID.randomUUID():old.project(), old.generation(),
                    kind==1?UUID.randomUUID():old.area(),old.manifestHash(),old.bindingHash(),kind==2?1:old.nextStep(),old.step(),old.owner(),old.faction(),old.core()));
            f.goal.tick(); verify(f.delegate,never()).tick(); verify(f.delegate).stop();
            assertEquals(1,f.cleanups); assertFalse(ProtectedInventoryCleanup.outstanding(f.data));
            f.goal.tick(); verify(f.delegate,times(1)).stop();
        }
    }
    @Test void identityFailureBeforeStartCreatesNoPhantomLifecycleOrCleanup() {
        try(var f=new Fixture()) {
            f.lease.set(null); f.goal.start(); f.goal.tick(); f.goal.stop();
            verify(f.delegate,never()).start();verify(f.delegate,never()).tick();verify(f.delegate,never()).stop();
            assertEquals(0,f.cleanups);assertFalse(ProtectedInventoryCleanup.outstanding(f.data));
        }
    }
    @Test void unchangedScopeUsesTheOriginalNativeTickAndGuardedStopExactlyOnce() {
        try(var f=new Fixture()) {
            f.goal.start();f.goal.tick();f.goal.stop();
            verify(f.delegate).start();verify(f.delegate).tick();verify(f.delegate).stop();
            assertEquals(1,f.cleanups);assertFalse(ProtectedInventoryCleanup.outstanding(f.data));
        }
    }
    private static final class Fixture implements AutoCloseable {
        final BuilderEntity worker=mock(BuilderEntity.class);
        final CompoundTag data=new CompoundTag();final Goal delegate=mock(Goal.class);
        final AtomicReference<NativeEarthworksJobs.InventoryLease> lease=new AtomicReference<>();
        final MockedStatic<NativeEarthworksJobs> authority=mockStatic(NativeEarthworksJobs.class);
        final ProtectedInventoryGoal goal;int cleanups;
        Fixture(){
            when(worker.getPersistentData()).thenReturn(data);when(delegate.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE));
            var step=new PerimeterEarthworksManifest.Step(0,PerimeterEarthworksManifest.Kind.FILL,new BlockPos(0,63,0).asLong(),Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState(),null);
            lease.set(new NativeEarthworksJobs.InventoryLease(UUID.randomUUID(),1,UUID.randomUUID(),"a".repeat(64),"b".repeat(64),0,step,UUID.randomUUID(),"test",BlockPos.ZERO));
            authority.when(()->NativeEarthworksJobs.selected(worker)).thenReturn(true);
            authority.when(()->NativeEarthworksJobs.inventoryLease(worker)).thenAnswer(call->lease.get());
            authority.when(()->NativeEarthworksJobs.inventoryProblem(eq(worker),anySet())).thenReturn(null);
            goal=new ProtectedInventoryGoal(worker,delegate,new ProtectedInventoryGoal.Session(worker)){
                @Override ProtectedStorageAccess.Kind kind(){return ProtectedStorageAccess.Kind.NEEDED;}
                @Override String beforeStart(){return null;}
                @Override String beforeTick(){return null;}
                @Override String cleanup(){cleanups++;return null;}
            };
        }
        @Override public void close(){authority.close();}
    }
}
