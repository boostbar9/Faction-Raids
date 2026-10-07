package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native boundaries are mocked; the manifest/journal/reservation/payment transaction is real. */
class NativePerimeterAdmissionTest extends MinecraftTestSupport {
    @Test void firstNativeAcceptancePrecedesOnePaymentAndRepeatCannotCreateOrChargeAnotherProject() throws Exception {
        try(var fixture=new Fixture()) {
            assertTrue(fixture.start());
            assertEquals(List.of("candidate","native-queues","protected","assigned","activated"),fixture.order);
            assertEquals(936,FactionBank.balance(fixture.core));
            assertArrayEquals(new int[]{-64},FactionBank.ledgerDeltas(fixture.core));
            var project=PerimeterProjectStore.all(fixture.core).get(0);
            assertEquals(PerimeterProject.State.RUNNING,project.state());assertEquals(64,project.payment().debited());
            assertTrue(fixture.ledger.matchesProjectLease(project));
            assertEquals(PerimeterStageJournal.State.LIVE,PerimeterStageJournal.get(fixture.core,project).at(0).state());
            assertFalse(fixture.start());assertEquals(1,PerimeterProjectStore.all(fixture.core).size());
            assertEquals(936,FactionBank.balance(fixture.core));
            verify(fixture.builder,never()).setItemSlot(any(),any());verify(fixture.builder,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
            verify(fixture.level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void refusedNativeAssignmentCannotDebitOrActivate() throws Exception {
        try(var fixture=new Fixture()) {
            fixture.accepts=false;assertFalse(fixture.start());assertEquals(1000,FactionBank.balance(fixture.core));
            assertArrayEquals(new int[0],FactionBank.ledgerDeltas(fixture.core));assertFalse(fixture.order.contains("activated"));
            var project=PerimeterProjectStore.all(fixture.core).get(0);
            assertEquals(PerimeterProject.State.CANCELED,project.state());assertNull(project.payment());
        }
    }
    @Test void busyNativeBuilderAndOverlappingLedgerFailBeforePersistingAnyNewManifest() throws Exception {
        try(var fixture=new Fixture()) {
            fixture.bridge.when(()->WorkersBridge.hasActiveBuildArea(fixture.builder)).thenReturn(true);
            assertFalse(fixture.start());assertTrue(fixture.order.isEmpty());assertFalse(fixture.core.contains(PerimeterProjectStore.KEY));
            fixture.bridge.when(()->WorkersBridge.hasActiveBuildArea(fixture.builder)).thenReturn(false);
            assertTrue(fixture.ledger.register(UUID.randomUUID(),Set.of(BlockPos.of(fixture.model.reservation().iterator().next()))));
            assertFalse(fixture.start());assertTrue(fixture.order.isEmpty());assertFalse(fixture.core.contains(PerimeterProjectStore.KEY));
            assertEquals(1000,FactionBank.balance(fixture.core));
        }
    }
    @Test void fullHandHistoryFailsBeforeProjectJournalReservationCandidateOrPayment() throws Exception {
        try(var f=new Fixture()) {
            for(int i=0;i<ProtectedBuilderHandLifecycle.MAX_BUILDERS;i++)
                assertTrue(f.ledger.retainHandLifecycle(new CompoundTag(),UUID.randomUUID(),f.model.header().owner(),UUID.randomUUID()));
            assertFalse(f.start());assertTrue(f.order.isEmpty());
            assertFalse(f.core.contains(PerimeterProjectStore.KEY));assertFalse(f.core.contains(PerimeterStageJournal.KEY));
            assertEquals(1000,FactionBank.balance(f.core));assertTrue(f.builder.getPersistentData().isEmpty());
            assertEquals(0,f.ledger.save(new CompoundTag()).getList("Sites",10).size());
        }
    }
    @Test void insufficientTreasuryAfterAcceptanceNeverMarksNativeAreaPaid() throws Exception {
        try(var fixture=new Fixture()) {
            fixture.core.putLong("BankEmeralds",63);assertFalse(fixture.start());assertEquals(63,FactionBank.balance(fixture.core));
            assertFalse(fixture.order.contains("activated"));assertNull(PerimeterProjectStore.all(fixture.core).get(0).payment());
        }
    }

    @Test void absentEntityDataCannotCreateNativeAreaOrCharge() throws Exception {
        try(var fixture=new Fixture()) {
            when(fixture.level.areEntitiesLoaded(anyLong())).thenReturn(false);
            assertFalse(fixture.start());assertEquals(1000,FactionBank.balance(fixture.core));
            assertFalse(fixture.order.contains("native-queues"));
            verify(fixture.level,never()).addFreshEntity(any());
        }
    }
    @Test void loadedButNonTickingChunkCannotCreateNativeAreaOrCharge() throws Exception {
        try(var fixture=new Fixture()) {
            when(fixture.chunks.isPositionTicking(anyLong())).thenReturn(false);
            assertFalse(fixture.start());assertEquals(1000,FactionBank.balance(fixture.core));
            assertFalse(fixture.order.contains("native-queues"));
            verify(fixture.level,never()).addFreshEntity(any());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final PerimeterProject model=ConstructionProjectLedgerTest.project();
        final MinecraftServer server=mock(MinecraftServer.class);
        final ServerLevel level=mock(ServerLevel.class);
        final ServerChunkCache chunks=mock(ServerChunkCache.class);
        final ServerPlayer owner=mock(ServerPlayer.class);
        final Mob builder=mock(Mob.class);
        final ProtectedBuildArea area=mock(ProtectedBuildArea.class);
        final RaidSavedData data=new RaidSavedData();final CompoundTag core=new CompoundTag();
        final ConstructionEditLedger ledger=new ConstructionEditLedger();
        final Map<UUID,Entity> entities=new HashMap<>();final List<String> order=new ArrayList<>();
        final PerimeterProject[] proposed={null};boolean accepts=true;
        final org.mockito.MockedStatic<RaidSavedData> saved=mockStatic(RaidSavedData.class);
        final org.mockito.MockedStatic<SiegeCore> cores=mockStatic(SiegeCore.class);
        final org.mockito.MockedStatic<WorkersBridge> bridge=mockStatic(WorkersBridge.class);
        final org.mockito.MockedStatic<RecruitsClaimsBridge> claims=mockStatic(RecruitsClaimsBridge.class);
        final org.mockito.MockedStatic<ConstructionEditLedger> ledgers=mockStatic(ConstructionEditLedger.class);
        final org.mockito.MockedStatic<ProtectedConstructionAreas> factory=mockStatic(ProtectedConstructionAreas.class);
        final org.mockito.MockedStatic<NativeConstructionGuard> guards=mockStatic(NativeConstructionGuard.class);
        final org.mockito.MockedStatic<WallBuilderAccess> access=mockStatic(WallBuilderAccess.class);
        Fixture() throws Exception {
            var h=model.header();core.putLong("BankEmeralds",1000);core.putLong("Position",h.originalCore().asLong());data.siegeCores.put(h.coreKey(),core);
            saved.when(()->RaidSavedData.get(any())).thenReturn(data);
            when(level.getServer()).thenReturn(server);when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.hasChunkAt(any())).thenReturn(true);when(level.areEntitiesLoaded(anyLong())).thenReturn(true);
            when(level.getChunkSource()).thenReturn(chunks);when(chunks.isPositionTicking(anyLong())).thenReturn(true);
            when(server.getAllLevels()).thenReturn(List.of(level));
            when(owner.getUUID()).thenReturn(h.owner());when(owner.serverLevel()).thenReturn(level);when(owner.isAlive()).thenReturn(true);when(owner.mayBuild()).thenReturn(true);
            when(builder.getUUID()).thenReturn(h.builder());when(builder.level()).thenReturn(level);when(builder.isAlive()).thenReturn(true);
            when(builder.getPersistentData()).thenReturn(new CompoundTag());entities.put(h.builder(),builder);
            when(area.getPersistentData()).thenReturn(new CompoundTag());when(area.level()).thenReturn(level);when(area.isAlive()).thenReturn(true);
            when(area.blockPosition()).thenReturn(new BlockPos(8,64,8));when(area.getUUID()).thenAnswer(i->proposed[0].active().areaId());
            when(level.getEntity(any(UUID.class))).thenAnswer(i->entities.get(i.getArgument(0)));
            when(level.addFreshEntity(area)).thenAnswer(i->{entities.put(area.getUUID(),area);return true;});
            cores.when(()->SiegeCore.key(owner)).thenReturn(h.coreKey());
            cores.when(()->SiegeCore.point(server,h.coreKey())).thenReturn(new RaidSavedData.DefensePoint("siege_core",Level.OVERWORLD.location(),h.originalCore()));
            bridge.when(()->WorkersBridge.isBuilder(builder)).thenReturn(true);bridge.when(()->WorkersBridge.readWorkerOwner(builder)).thenReturn(h.owner());
            bridge.when(()->WorkersBridge.readOwner(area)).thenReturn(h.owner());
            claims.when(()->RecruitsClaimsBridge.getFactionTerritory(level,h.faction(),PerimeterTerritory.MAX_CHUNKS))
                    .thenReturn(new RecruitsClaimsBridge.TerritorySnapshot(h.faction(),h.territory(),null));
            ledgers.when(()->ConstructionEditLedger.get(level)).thenReturn(ledger);
            factory.when(()->ProtectedConstructionAreas.createStage(eq(owner),eq(builder),any())).thenAnswer(i->{
                proposed[0]=i.getArgument(2);order.add("candidate");assertFalse(core.contains(PerimeterProjectStore.KEY));return area;
            });
            bridge.when(()->WorkersBridge.startBlueprint(eq(area),any())).thenAnswer(i->{order.add("native-queues");return null;});
            guards.when(()->NativeConstructionGuard.protectStage(eq(owner),eq(builder),eq(area),any())).thenAnswer(i->{
                order.add("protected");assertNull(PerimeterProjectStore.all(core).get(0).payment());return true;
            });
            access.when(()->WallBuilderAccess.install(builder)).thenReturn(true);
            bridge.when(()->WorkersBridge.assignBuildAreaDirectly(builder,area)).thenAnswer(i->{order.add("assigned");return accepts;});
            guards.when(()->NativeConstructionGuard.activate(area)).thenAnswer(i->{
                order.add("activated");assertEquals(936,FactionBank.balance(core));
                assertEquals(PerimeterProject.State.RUNNING,PerimeterProjectStore.all(core).get(0).state());return true;
            });
        }
        boolean start() {var h=model.header();return NativePerimeterProjects.start(owner,builder,h.originalCore(),h.material(),model.plan(),model.layout(),
                model.before(),model.clearanceBefore(),null,new RecruitsClaimsBridge.TerritorySnapshot(h.faction(),h.territory(),null),h.reviewedFingerprint());}
        public void close(){access.close();guards.close();factory.close();ledgers.close();claims.close();bridge.close();cores.close();saved.close();}
    }
}
