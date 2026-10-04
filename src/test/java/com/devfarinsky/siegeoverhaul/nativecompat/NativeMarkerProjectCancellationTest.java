package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real saved manifest and stage journal; cancellation identity comes from the server-resolved native marker. */
class NativeMarkerProjectCancellationTest extends MinecraftTestSupport {
    @Test void originalOwnerCanCancelAtExactOldMarkerAfterChangingFaction() {
        try(var f=new Fixture()) {
            assertTrue(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            assertEquals(PerimeterProject.State.CANCELED,f.current().state());
            assertEquals(936,FactionBank.balance(f.core));
            assertArrayEquals(new int[]{-64},FactionBank.ledgerDeltas(f.core));
            assertFalse(f.other.contains(PerimeterProjectStore.KEY));
            f.cores.verifyNoInteractions();
            verify(f.area,never()).removeAuthorized(); // Unknown cleanup remains retained.
            verify(f.level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void currentFactionHudLookupCannotSelectAnOldFactionProject() {
        try(var f=new Fixture()) {
            assertFalse(NativePerimeterProjects.cancel(f.owner,f.project.header().projectId(),1));
            assertEquals(PerimeterProject.State.RUNNING,f.current().state());
            assertTrue(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
        }
    }
    @Test void anotherPlayerCannotCancelEvenIfNativeMarkerOwnerWasChanged() {
        try(var f=new Fixture()) {
            UUID other=UUID.randomUUID();when(f.owner.getUUID()).thenReturn(other);when(f.area.getPlayerUUID()).thenReturn(other);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));f.assertUnchanged();
        }
    }
    @Test void remoteDeadSpectatorAndWrongDimensionRequestsAreRejected() {
        try(var f=new Fixture()) {
            when(f.owner.distanceToSqr(f.area)).thenReturn(257d);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            when(f.owner.distanceToSqr(f.area)).thenReturn(1d);when(f.owner.isAlive()).thenReturn(false);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            when(f.owner.isAlive()).thenReturn(true);when(f.owner.isSpectator()).thenReturn(true);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            when(f.owner.isSpectator()).thenReturn(false);when(f.level.dimension()).thenReturn(Level.NETHER);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));f.assertUnchanged();
        }
    }
    @Test void copiedStaleOrMovedMarkersCannotSelectAProject() {
        try(var f=new Fixture()) {
            when(f.level.getEntity(f.area.getUUID())).thenReturn(mock(ProtectedBuildArea.class));
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            when(f.level.getEntity(f.area.getUUID())).thenReturn(f.area);when(f.area.blockPosition()).thenReturn(BlockPos.ZERO);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            when(f.area.blockPosition()).thenReturn(f.marker);
            f.persistent.getCompound(PerimeterProjectAuthority.KEY).putInt("Stage",1);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));f.assertUnchanged();
        }
    }
    @Test void changedGenerationHashStageDigestAndCoreAreRejected() {
        try(var f=new Fixture()) {
            CompoundTag original=f.persistent.getCompound(PerimeterProjectAuthority.KEY).copy();
            for(String key:new String[]{"Generation","Hash","StageHash","Core"}) {
                f.persistent.put(PerimeterProjectAuthority.KEY,original.copy());
                var tag=f.persistent.getCompound(PerimeterProjectAuthority.KEY);
                if(key.equals("Generation"))tag.putLong(key,2);
                else tag.putString(key,key.equals("Core")?"team:new":"f".repeat(64));
                assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area),key);f.assertUnchanged();
            }
        }
    }
    @Test void missingJournalCannotAuthorizeCancellationAndRepeatNeverRefunds() {
        try(var f=new Fixture()) {
            var journal=f.core.get(PerimeterStageJournal.KEY).copy();f.core.remove(PerimeterStageJournal.KEY);
            assertFalse(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));f.assertUnchanged();
            f.core.put(PerimeterStageJournal.KEY,journal);
            assertTrue(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            assertTrue(NativePerimeterProjects.cancelFromMarker(f.owner,f.area));
            assertEquals(936,FactionBank.balance(f.core));assertArrayEquals(new int[]{-64},FactionBank.ledgerDeltas(f.core));
        }
    }
    private static final class Fixture implements AutoCloseable {
        PerimeterProject project=ConstructionProjectLedgerTest.project();
        final MinecraftServer server=mock(MinecraftServer.class);
        final ServerLevel level=mock(ServerLevel.class);
        final ServerPlayer owner=mock(ServerPlayer.class);
        final ProtectedBuildArea area=mock(ProtectedBuildArea.class);
        final RaidSavedData data=new RaidSavedData();
        final CompoundTag core=new CompoundTag(),other=new CompoundTag(),persistent=new CompoundTag();
        final BlockPos marker=new BlockPos(8,65,8);
        final org.mockito.MockedStatic<RaidSavedData> saved=mockStatic(RaidSavedData.class);
        final org.mockito.MockedStatic<SiegeCore> cores=mockStatic(SiegeCore.class);
        final org.mockito.MockedStatic<ConstructionEditLedger> ledgers=mockStatic(ConstructionEditLedger.class);
        Fixture() {
            when(level.getServer()).thenReturn(server);when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(server.isSameThread()).thenReturn(true);
            when(owner.serverLevel()).thenReturn(level);when(owner.isAlive()).thenReturn(true);
            when(owner.getUUID()).thenReturn(project.header().owner());when(owner.distanceToSqr(area)).thenReturn(1d);
            when(area.getUUID()).thenReturn(project.active().areaId());when(area.getPlayerUUID()).thenReturn(project.header().owner());
            when(area.getPersistentData()).thenReturn(persistent);when(area.level()).thenReturn(level);when(area.isAlive()).thenReturn(true);
            when(area.blockPosition()).thenReturn(marker);when(level.getEntity(area.getUUID())).thenReturn(area);
            data.siegeCores.put(project.header().coreKey(),core);data.siegeCores.put("team:new",other);
            saved.when(()->RaidSavedData.get(any())).thenReturn(data);cores.when(()->SiegeCore.key(owner)).thenReturn("team:new");
            ledgers.when(()->ConstructionEditLedger.get(level)).thenReturn(new ConstructionEditLedger());
            core.putLong("BankEmeralds",1000);core.putLong("Position",project.header().originalCore().asLong());
            PerimeterProjectStore.prepare(core,project,()->{});PerimeterStageJournal.prepare(core,project,()->{});
            PerimeterStageJournal.begin(core,project,marker,()->{});PerimeterStageJournal.live(core,project,()->{});
            PerimeterProjectAuthority.stamp(area,project);
            project=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,false,()->{}).project();
            project=PerimeterProjectStore.replace(core,project.check(),project.activate(project.check()),()->{});
        }
        PerimeterProject current(){return PerimeterProjectStore.get(core,project.header().projectId());}
        void assertUnchanged(){assertEquals(PerimeterProject.State.RUNNING,current().state());assertEquals(936,FactionBank.balance(core));}
        public void close(){PerimeterProjectAuthority.stopped(server);ledgers.close();cores.close();saved.close();}
    }
}
