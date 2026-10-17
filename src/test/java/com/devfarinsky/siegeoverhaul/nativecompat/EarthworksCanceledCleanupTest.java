package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real retained authority and protected cleanup wrappers, with mocked native sources/delegates. */
class EarthworksCanceledCleanupTest extends MinecraftTestSupport {
    @Test void exactCanceledScopeDrainsOnceWithoutNewWorkTransferOrDebit()throws Exception{
        try(var f=new Fixture()){
            f.neededWrapped.start();verify(f.needed).start();f.cancel();var journal=f.job.read();var core=f.core.copy();
            assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));
            verify(f.needed,times(1)).stop();verify(f.needed,never()).tick();verify(f.original,never()).start();verify(f.original,never()).tick();
            assertNull(NativeEarthworksJobs.inventoryLease(f.worker));assertNotNull(NativeEarthworksJobs.inventoryProblem(f.worker,Set.of()));
            assertFalse(f.work.canUse());assertFalse(f.neededAdapter.canUse());f.neededAdapter.start();verify(f.needed,times(1)).start();
            assertEquals(journal,f.job.read());assertEquals(core,f.core);assertEquals("",f.job.supplyDigest());assertTrue(f.worker.neededItems.isEmpty());
        }
    }
    @Test void retainedRunningLocalGoalIsStoppedExactlyOnceBeforeAnyFreshCallback()throws Exception{
        try(var f=new Fixture()){
            var port=mock(WorkersEarthworksPort.class);var nativeWork=mock(BuilderWorkGoal.class);
            when(nativeWork.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));when(port.nativeGoal()).thenReturn(nativeWork);when(port.nativeEligible()).thenReturn(true);
            var local=new LocalEarthworksGoal(f.job.manifest,f.job,port);f.job.runtimeWorker=f.worker;f.job.runtimeGoal=local;
            var wrapped=f.goals.stream().filter(g->g.getGoal()==f.work).findFirst().orElseThrow();wrapped.start();verify(nativeWork).start();
            f.cancel();var journal=f.job.read();assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));
            verify(nativeWork,times(1)).stop();verify(nativeWork,never()).tick();verify(f.original,never()).start();verify(f.original,never()).stop();assertEquals(journal,f.job.read());
        }
    }
    @Test void savedReloadCannotBorrowTheOldLiveWrapperForCanceledCleanup()throws Exception{
        try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();var reopened=EarthworksJobLedger.load(f.jobs.save(new CompoundTag()));
            f.jobsMock.when(()->EarthworksJobLedger.get(f.level)).thenReturn(reopened);
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,never()).stop();verify(f.needed,never()).tick();
        }
    }
    @Test void unavailableSourceDefersOriginalStopAndResumesOnlyThatCleanup()throws Exception{
        try(var f=new Fixture();var sources=mockStatic(ProtectedStorageContext.class)){
            var source=mock(Container.class);var pos=new BlockPos(3,65,3);f.needed.chestPos=pos;f.needed.container=source;var blocked=new AtomicBoolean(true);
            sources.when(()->ProtectedStorageContext.level(f.worker)).thenReturn(f.level);
            sources.when(()->ProtectedStorageContext.cleanup(f.level,source,pos)).thenAnswer(c->{if(blocked.get())throw new IllegalStateException("Unloaded source");return null;});
            f.neededWrapped.start();f.cancel();var journal=f.job.read();
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,never()).stop();
            assertEquals(ProtectedInventoryCleanup.CLEANUP,ProtectedInventoryCleanup.read(f.data).get(ProtectedStorageAccess.Kind.NEEDED));
            blocked.set(false);assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,times(1)).stop();
            assertEquals(journal,f.job.read());verify(f.needed,never()).tick();
        }
    }
    @Test void reviewFenceAndThrowingStopNeverReplay()throws Exception{
        try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();ProtectedInventoryCleanup.record(f.data,ProtectedStorageAccess.Kind.NEEDED,ProtectedInventoryCleanup.REVIEW);var before=f.data.copy();
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,never()).stop();assertEquals(before,f.data);
        }
        try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();doThrow(new IllegalStateException("Partial native close")).when(f.needed).stop();
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,times(1)).stop();
            assertEquals(ProtectedInventoryCleanup.REVIEW,ProtectedInventoryCleanup.read(f.data).get(ProtectedStorageAccess.Kind.NEEDED));
        }
    }
    @Test void foreignSelectorPaymentAreaReservationAndRuntimeRefuseBeforeCleanup()throws Exception{
        for(int kind=0;kind<5;kind++)try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();
            if(kind==0)f.data.getCompound(NativeEarthworksJobs.KEY).putUUID("Project",UUID.randomUUID());
            if(kind==1)f.core.remove("SiegeEarthworksDebitsV1");
            if(kind==2)when(f.area.getUUID()).thenReturn(UUID.randomUUID());
            if(kind==3)f.edits.remove(f.job.area);
            if(kind==4)f.runtime.when(WorkersConstructionRuntime::problem).thenReturn("Unknown runtime");
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,never()).stop();verify(f.needed,never()).tick();
        }
    }
    @Test void absentUnwrappedAndOrphanedInventoryHooksCannotBeManufacturedAfterCancellation()throws Exception{
        for(int kind=0;kind<4;kind++)try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();
            if(kind==0)f.goals.remove(f.neededWrapped);
            if(kind==1){f.goals.remove(f.depositWrapped);f.goals.add(new WrappedGoal(2,f.deposit));}
            if(kind==2)f.session.pending.add(new UnknownAdapter(f.worker,f.session));
            if(kind==3){
                var constructor=ProtectedStorageAccess.class.getDeclaredConstructor(BuilderEntity.class,AbstractChestGoal.class,ProtectedStorageAccess.Kind.class,ProtectedInventoryGoal.Session.class);
                constructor.setAccessible(true);f.goals.remove(f.depositWrapped);
                f.goals.add(new WrappedGoal(2,constructor.newInstance(f.worker,f.deposit,ProtectedStorageAccess.Kind.DEPOSIT,new ProtectedInventoryGoal.Session(f.worker))));
            }
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,never()).stop();verify(f.needed,never()).tick();
        }
    }
    @Test void stillStartedPendingMemberRefusesBeforeTheFirstNativeStop()throws Exception{
        try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();f.session.pending.add(f.neededAdapter);var before=f.data.copy();
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,never()).stop();assertEquals(before,f.data);
        }
    }
    @Test void staleCompletedPendingMemberCannotReplayItsNativeStop()throws Exception{
        try(var f=new Fixture()){
            f.neededWrapped.start();f.cancel();assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,times(1)).stop();
            f.session.pending.add(f.neededAdapter);var before=f.data.copy();
            assertFalse(NativeEarthworksJobs.beforeWorkerTick(f.worker));verify(f.needed,times(1)).stop();assertEquals(before,f.data);
        }
    }
    @Test void nativeEntityUpkeepStopFinalizesEarnedTimerExactlyOnce()throws Exception{
        try(var f=new Fixture()){
            var active=spy(f.entityAdapter);doReturn(null).when(active).beforeStart();doNothing().when(active).afterStart();
            f.goals.remove(f.entityWrapped);var wrapped=new WrappedGoal(4,active);f.goals.add(wrapped);
            when(f.worker.getUpkeepCooldown()).thenReturn(300);f.worker.forcedUpkeep=true;f.worker.paymentTimer=0;
            var field=RecruitUpkeepEntityGoal.class.getDeclaredField("canResetPaymentTimer");field.setAccessible(true);field.setBoolean(f.entity,true);
            doCallRealMethod().when(f.entity).stop();wrapped.start();f.cancel();
            assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));assertTrue(NativeEarthworksJobs.beforeWorkerTick(f.worker));
            verify(f.worker,times(1)).setUpkeepTimer(300);verify(f.worker,times(1)).resetPaymentTimer();assertFalse(f.worker.forcedUpkeep);
            verify(f.entity,times(1)).stop();verify(f.entity,never()).tick();
        }
    }
    private static final class UnknownAdapter extends ProtectedInventoryGoal {
        UnknownAdapter(BuilderEntity w,Session s){super(w,emptyGoal(),s);}
        private static Goal emptyGoal(){var goal=mock(Goal.class);when(goal.getFlags()).thenReturn(EnumSet.noneOf(Goal.Flag.class));return goal;}
        @Override ProtectedStorageAccess.Kind kind(){return ProtectedStorageAccess.Kind.NEEDED;}
        @Override String beforeStart(){return null;}@Override String beforeTick(){return null;}@Override String cleanup(){return null;}
    }
    private static final class Fixture implements AutoCloseable {
        final BuilderEntity worker=mock(BuilderEntity.class);final EarthworksBuildArea area=mock(EarthworksBuildArea.class);
        final ServerLevel level=mock(ServerLevel.class);final MinecraftServer server=mock(MinecraftServer.class);
        final CompoundTag data=new CompoundTag(),core=new CompoundTag();final EarthworksJobLedger jobs=new EarthworksJobLedger();final ConstructionEditLedger edits=new ConstructionEditLedger();
        final RaidSavedData saved=new RaidSavedData();final EarthworksJobLedger.Job job;
        final BuilderWorkGoal original=mock(BuilderWorkGoal.class);final EarthworksWorkGoal work;
        final GetNeededItemsFromStorage needed=mock(GetNeededItemsFromStorage.class);final DepositItemsToStorage deposit=mock(DepositItemsToStorage.class);
        final RecruitUpkeepPosGoal position=mock(RecruitUpkeepPosGoal.class);final RecruitUpkeepEntityGoal entity=mock(RecruitUpkeepEntityGoal.class);
        final ProtectedInventoryGoal.Session session=new ProtectedInventoryGoal.Session(worker);
        final ProtectedStorageAccess neededAdapter;final ProtectedUpkeepAccess entityAdapter;
        final WrappedGoal neededWrapped,depositWrapped,entityWrapped;final Set<WrappedGoal> goals=new LinkedHashSet<>();
        final MockedStatic<EarthworksJobLedger> jobsMock=mockStatic(EarthworksJobLedger.class);
        final MockedStatic<ConstructionEditLedger> editsMock=mockStatic(ConstructionEditLedger.class);
        final MockedStatic<RaidSavedData> savedMock=mockStatic(RaidSavedData.class);
        final MockedStatic<WorkersBridge> workers=mockStatic(WorkersBridge.class);
        final MockedStatic<WorkersConstructionRuntime> runtime=mockStatic(WorkersConstructionRuntime.class);
        final MockedStatic<NativeInventoryAuthority> inventory=mockStatic(NativeInventoryAuthority.class);
        Fixture()throws Exception{
            var all=NativeEarthworksAdapterTest.manifest();var manifest=new PerimeterEarthworksManifest(all.header(),new ArrayList<>(all.observations().values()),all.steps().subList(0,2));
            job=jobs.prepare(manifest,new PerimeterEarthworksJournal.Binding(edits.generation(),"a".repeat(64),"b".repeat(64)),UUID.randomUUID(),BlockPos.ZERO);job.acknowledgeDebit();
            assertTrue(edits.register(job.area,manifest.observations().keySet().stream().map(BlockPos::of).collect(Collectors.toSet())));
            data.put(NativeEarthworksJobs.KEY,NativeEarthworksJobs.selector(job));assertTrue(edits.retainHandLifecycle(data,manifest.header().builder(),manifest.header().owner(),job.area));
            var receipt=new CompoundTag();receipt.putUUID("Project",manifest.header().project());receipt.putUUID("Area",job.area);receipt.putString("Manifest",manifest.hash());receipt.putString("Receipt",job.read().journal().binding().paymentReceipt());receipt.putInt("Price",64);
            var rows=new ListTag();rows.add(receipt);core.put("SiegeEarthworksDebitsV1",rows);saved.siegeCores.put("team:"+manifest.header().faction(),core);
            when(level.getServer()).thenReturn(server);when(server.isSameThread()).thenReturn(true);when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(worker.level()).thenReturn(level);when(worker.isAlive()).thenReturn(true);when(worker.getUUID()).thenReturn(manifest.header().builder());when(worker.getPersistentData()).thenReturn(data);worker.neededItems=new ArrayList<>();
            worker.currentBuildArea=area;when(area.level()).thenReturn(level);when(area.getUUID()).thenReturn(job.area);when(area.matches(job)).thenReturn(true);
            jobsMock.when(()->EarthworksJobLedger.get(level)).thenReturn(jobs);editsMock.when(()->ConstructionEditLedger.get(level)).thenReturn(edits);savedMock.when(()->RaidSavedData.get(server)).thenReturn(saved);
            workers.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(manifest.header().owner());workers.when(()->WorkersBridge.readOwner(area)).thenReturn(manifest.header().owner());runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
            when(original.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));work=new EarthworksWorkGoal(worker,original,()->NativeEarthworksJobs.workGoal(worker));goals.add(new WrappedGoal(0,work));
            needed.worker=worker;deposit.worker=worker;position.recruit=worker;entity.recruit=worker;
            for(Goal nativeGoal:List.of(needed,deposit,position,entity))when(nativeGoal.getFlags()).thenReturn(EnumSet.noneOf(Goal.Flag.class));
            var constructor=ProtectedStorageAccess.class.getDeclaredConstructor(BuilderEntity.class,AbstractChestGoal.class,ProtectedStorageAccess.Kind.class,ProtectedInventoryGoal.Session.class);constructor.setAccessible(true);
            neededAdapter=constructor.newInstance(worker,needed,ProtectedStorageAccess.Kind.NEEDED,session);neededWrapped=new WrappedGoal(1,neededAdapter);goals.add(neededWrapped);
            depositWrapped=new WrappedGoal(2,constructor.newInstance(worker,deposit,ProtectedStorageAccess.Kind.DEPOSIT,session));goals.add(depositWrapped);
            goals.add(new WrappedGoal(3,new ProtectedUpkeepAccess(worker,position,ProtectedStorageAccess.Kind.POSITION_UPKEEP,session)));
            entityAdapter=new ProtectedUpkeepAccess(worker,entity,ProtectedStorageAccess.Kind.ENTITY_UPKEEP,session);entityWrapped=new WrappedGoal(4,entityAdapter);goals.add(entityWrapped);
            var selector=mock(GoalSelector.class);when(selector.getAvailableGoals()).thenReturn(goals);var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);field.set(worker,selector);
        }
        void cancel(){var journal=job.read().journal();assertTrue(job.saveJournal(journal.check(),journal.cancel(journal.check(),"Reviewed cancellation")));}
        @Override public void close(){inventory.close();runtime.close();workers.close();savedMock.close();editsMock.close();jobsMock.close();}
    }
}
