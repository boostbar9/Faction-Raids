package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.BuildArea;
import com.talhanation.workers.world.BuildBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual authority and owning-wrapper selection, with mocked native effects/world services. Not gameplay proof. */
class EarthworksAreaSelectionTest extends MinecraftTestSupport {
    @Test void unmarkedForeignBuilderWithAbsentLedgerNeverFallsBackToOrdinaryWorkOrInventory(){
        try(var f=new Fixture()){
            f.data.remove(NativeEarthworksJobs.KEY);f.jobsMock.when(()->EarthworksJobLedger.get(f.level)).thenReturn(new EarthworksJobLedger());
            f.assertDenied();
        }
    }
    @Test void mismatchedBuilderAreaProjectAndOwnerStayOnTheGuardedPath(){
        for(int change=0;change<4;change++)try(var f=new Fixture()){
            if(change==0)when(f.worker.getUUID()).thenReturn(UUID.randomUUID());
            if(change==1)when(f.area.getUUID()).thenReturn(UUID.randomUUID());
            if(change==2)f.data.getCompound(NativeEarthworksJobs.KEY).putUUID("Project",UUID.randomUUID());
            if(change==3)f.workers.when(()->WorkersBridge.readWorkerOwner(f.worker)).thenReturn(UUID.randomUUID());
            f.assertDenied();
        }
    }
    @Test void exactOriginalOwnerAndWorldIdentityStillYieldTheCurrentLease(){
        try(var f=new Fixture()){
            assertTrue(NativeEarthworksJobs.selected(f.worker));var lease=NativeEarthworksJobs.inventoryLease(f.worker);assertNotNull(lease);
            assertEquals(f.job.area,lease.area());assertEquals(f.job.manifest.header().owner(),lease.owner());
            assertEquals(f.job.manifest.hash(),lease.manifestHash());assertEquals(0,lease.nextStep());
        }
    }
    @Test void knownNewMarkerCannotEscapeSelectionThroughMissingPersistentData(){
        var worker=mock(BuilderEntity.class);worker.currentBuildArea=mock(EarthworksBuildArea.class);
        assertTrue(NativeEarthworksJobs.selected(worker));assertNull(NativeEarthworksJobs.inventoryLease(worker));
    }
    @Test void ordinaryAndV1AreaTypesKeepTheirExistingSelectionAndReceipts(){
        try(var f=new Fixture()){
            f.jobsMock.when(()->EarthworksJobLedger.get(f.level)).thenReturn(new EarthworksJobLedger());f.data.remove(NativeEarthworksJobs.KEY);
            f.worker.currentBuildArea=mock(BuildArea.class);assertFalse(NativeEarthworksJobs.selected(f.worker));
            f.worker.currentBuildArea=mock(ProtectedBuildArea.class);f.data.putUUID("SiegeProtectedAreaReceipt",UUID.randomUUID());
            assertFalse(NativeEarthworksJobs.selected(f.worker));assertTrue(NativeConstructionGuard.hasProtectedReceipt(f.worker));
            var original=f.effects();var wrapper=new EarthworksWorkGoal(f.worker,original,()->{throw new AssertionError("No new grading scope expected");});
            assertTrue(wrapper.canUse());wrapper.start();wrapper.tick();wrapper.stop();verify(original).tick();
        }
    }
    private static final class Fixture implements AutoCloseable {
        final BuilderEntity worker=mock(BuilderEntity.class);final EarthworksBuildArea area=mock(EarthworksBuildArea.class);
        final ServerLevel level=mock(ServerLevel.class);final MinecraftServer server=mock(MinecraftServer.class);
        final CompoundTag data=new CompoundTag();final SimpleContainer inventory=new SimpleContainer(new ItemStack(Items.DIRT));
        final AtomicReference<net.minecraft.world.level.block.state.BlockState> world=new AtomicReference<>(Blocks.AIR.defaultBlockState());
        final EarthworksJobLedger jobs=new EarthworksJobLedger();final ConstructionEditLedger edits=new ConstructionEditLedger();
        final RaidSavedData saved=new RaidSavedData();final EarthworksJobLedger.Job job;
        final MockedStatic<EarthworksJobLedger> jobsMock=mockStatic(EarthworksJobLedger.class);
        final MockedStatic<ConstructionEditLedger> editsMock=mockStatic(ConstructionEditLedger.class);
        final MockedStatic<RaidSavedData> savedMock=mockStatic(RaidSavedData.class);
        final MockedStatic<WorkersBridge> workers=mockStatic(WorkersBridge.class);
        final MockedStatic<WorkersConstructionRuntime> runtime=mockStatic(WorkersConstructionRuntime.class);
        Fixture(){
            var all=NativeEarthworksAdapterTest.manifest();var manifest=new PerimeterEarthworksManifest(all.header(),new ArrayList<>(all.observations().values()),all.steps().subList(0,2));
            job=jobs.prepare(manifest,new PerimeterEarthworksJournal.Binding(edits.generation(),"a".repeat(64),"b".repeat(64)),UUID.randomUUID(),BlockPos.ZERO);job.acknowledgeDebit();
            assertTrue(edits.register(job.area,manifest.observations().keySet().stream().map(BlockPos::of).collect(Collectors.toSet())));
            data.put(NativeEarthworksJobs.KEY,NativeEarthworksJobs.selector(job));
            var core=new CompoundTag();var receipt=new CompoundTag();receipt.putUUID("Project",manifest.header().project());receipt.putUUID("Area",job.area);
            receipt.putString("Manifest",manifest.hash());receipt.putString("Receipt",job.read().journal().binding().paymentReceipt());receipt.putInt("Price",64);
            var rows=new ListTag();rows.add(receipt);core.put("SiegeEarthworksDebitsV1",rows);saved.siegeCores.put("team:"+manifest.header().faction(),core);
            when(level.getServer()).thenReturn(server);when(server.isSameThread()).thenReturn(true);when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(worker.level()).thenReturn(level);when(worker.isAlive()).thenReturn(true);when(worker.getUUID()).thenReturn(manifest.header().builder());when(worker.getPersistentData()).thenReturn(data);
            worker.currentBuildArea=area;when(area.level()).thenReturn(level);when(area.getUUID()).thenReturn(job.area);when(area.matches(job)).thenReturn(true);
            area.stackToPlace=new Stack<>();area.stackToPlace.push(new BuildBlock(BlockPos.ZERO,Blocks.DIRT.defaultBlockState()));
            jobsMock.when(()->EarthworksJobLedger.get(level)).thenReturn(jobs);editsMock.when(()->ConstructionEditLedger.get(level)).thenReturn(edits);savedMock.when(()->RaidSavedData.get(server)).thenReturn(saved);
            workers.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(manifest.header().owner());workers.when(()->WorkersBridge.readOwner(area)).thenReturn(manifest.header().owner());
            runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
        }
        Goal effects(){
            var goal=mock(Goal.class);when(goal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));when(goal.canUse()).thenReturn(true);when(goal.canContinueToUse()).thenReturn(true);
            doAnswer(call->{world.set(Blocks.DIRT.defaultBlockState());area.stackToPlace.clear();inventory.removeItem(0,1);return null;}).when(goal).tick();return goal;
        }
        void assertDenied(){
            assertTrue(NativeEarthworksJobs.selected(worker));assertNull(NativeEarthworksJobs.inventoryLease(worker));
            var ordinary=effects();var work=new EarthworksWorkGoal(worker,ordinary,()->null);assertFalse(work.canUse());work.start();work.tick();
            var storage=effects();var protectedStorage=new ProtectedInventoryGoal(worker,storage,new ProtectedInventoryGoal.Session(worker)){
                @Override ProtectedStorageAccess.Kind kind(){return ProtectedStorageAccess.Kind.NEEDED;}
                @Override String beforeStart(){return null;}@Override String beforeTick(){return null;}@Override String cleanup(){return null;}
            };
            protectedStorage.start();protectedStorage.tick();verify(ordinary,never()).start();verify(ordinary,never()).tick();verify(storage,never()).start();verify(storage,never()).tick();
            assertTrue(world.get().isAir());assertEquals(1,area.stackToPlace.size());assertEquals(1,inventory.getItem(0).getCount());
        }
        @Override public void close(){runtime.close();workers.close();savedMock.close();editsMock.close();jobsMock.close();}
    }
}
