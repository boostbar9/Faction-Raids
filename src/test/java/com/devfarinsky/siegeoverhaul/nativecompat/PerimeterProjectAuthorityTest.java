package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterGateProjectFixture;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerimeterProjectAuthorityTest extends MinecraftTestSupport {
    @Test void fullTerritoryComparisonCannotBeReplacedByActiveStageOrEntitySelectors() {
        var project=ConstructionProjectLedgerTest.project();var header=project.header();
        assertTrue(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot(header.faction(),header.territory(),null)));
        var expanded=new HashSet<>(header.territory());expanded.add(new ChunkPos(20,20));
        assertFalse(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot(header.faction(),expanded,null)));
        assertFalse(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot("foreign",header.territory(),null)));
        assertFalse(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot(header.faction(),header.territory(),"unavailable")));
    }
    @Test void selectorIsBoundAndMalformedRequiredVersionOrStageCannotFallBackToLegacy() {
        var project=ConstructionProjectLedgerTest.project();var area=mock(Entity.class);var data=new CompoundTag();
        when(area.getPersistentData()).thenReturn(data);when(area.getUUID()).thenReturn(project.active().areaId());
        PerimeterProjectAuthority.stamp(area,project);var selector=PerimeterProjectAuthority.read(data);
        assertEquals(project.header().projectId(),selector.projectId());assertEquals(project.manifestHash(),selector.manifestHash());
        assertEquals(project.active().digest(),selector.stageDigest());
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.stamp(area,project));
        var missing=data.copy();missing.remove(PerimeterProjectAuthority.REQUIRED);
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(missing));
        var coerced=data.copy();coerced.getCompound(PerimeterProjectAuthority.KEY).putDouble("Version",1);
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(coerced));
        var future=data.copy();future.getCompound(PerimeterProjectAuthority.KEY).putInt("Stage",256);
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(future));
        var zero=data.copy();zero.getCompound(PerimeterProjectAuthority.KEY).putUUID("Project",new UUID(0,0));
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(zero));
    }
    @Test void paidFlagAloneDoesNotAuthorizePreparedRecoveryOrTerminalStates() {
        var project=ConstructionProjectLedgerTest.project();var core=new CompoundTag();core.putLong("BankEmeralds",64);
        assertFalse(PerimeterProjectAuthority.workState(project,false,false));
        assertTrue(PerimeterProjectAuthority.workState(project,true,false));
        PerimeterProjectStore.prepare(core,project,()->{});
        project=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,false,()->{}).project();
        assertFalse(PerimeterProjectAuthority.workState(project,false,false));
        project=project.activate(project.check());assertTrue(PerimeterProjectAuthority.workState(project,false,false));
        var verified=project.verifyStage(project.check(),project.expectedStageReceipt());
        assertFalse(PerimeterProjectAuthority.workState(verified,false,false));
        assertTrue(PerimeterProjectAuthority.workState(verified,false,true));
        var canceled=project.cancel(project.check(),"Owner canceled");
        assertFalse(PerimeterProjectAuthority.workState(canceled,true,true));
        var blocked=project.blockRecovery(project.check(),"Missing marker");
        assertFalse(PerimeterProjectAuthority.workState(blocked,true,true));
    }

    @Test void activeAuthorityChecksGateObservationsBeforeReloadedStageCanResume() {
        var prepared=PerimeterGateProjectFixture.project();var core=new CompoundTag();core.putLong("BankEmeralds",64);
        PerimeterProjectStore.prepare(core,prepared,()->{});PerimeterStageJournal.prepare(core,prepared,()->{});
        BlockPos marker=prepared.header().originalCore();
        assertNotNull(PerimeterStageJournal.begin(core,prepared,marker,()->{}));PerimeterStageJournal.live(core,prepared,()->{});
        var paid=PerimeterProjectStore.consumeOnce(core,prepared.header().projectId(),prepared.manifestHash(),
                prepared.header().quotedPrice(),false,()->{}).project();
        var running=PerimeterProjectStore.replace(core,paid.check(),paid.activate(paid.check()),()->{});
        var data=new RaidSavedData();data.siegeCores.put(running.header().coreKey(),core);
        var ledger=new ConstructionEditLedger();assertTrue(ledger.registerProject(prepared));assertTrue(ledger.leaseProjectStage(prepared));
        var server=mock(MinecraftServer.class);var level=mock(ServerLevel.class);var players=mock(PlayerList.class);
        var owner=mock(ServerPlayer.class);var builder=mock(Mob.class);var area=mock(Entity.class);var persistent=new CompoundTag();
        when(level.getServer()).thenReturn(server);when(server.isSameThread()).thenReturn(true);when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayer(running.header().owner())).thenReturn(owner);when(builder.getUUID()).thenReturn(running.header().builder());
        when(area.getPersistentData()).thenReturn(persistent);when(area.getUUID()).thenReturn(running.active().areaId());
        when(area.blockPosition()).thenReturn(marker);PerimeterProjectAuthority.stamp(area,running);
        int component=running.gateStageComponent(running.activeStage());
        try(MockedStatic<RaidSavedData> saves=mockStatic(RaidSavedData.class);
            MockedStatic<WorkersBridge> workers=mockStatic(WorkersBridge.class);
            MockedStatic<RecruitsClaimsBridge> claims=mockStatic(RecruitsClaimsBridge.class);
            MockedStatic<ConstructionEditLedger> ledgers=mockStatic(ConstructionEditLedger.class);
            MockedStatic<NativePerimeterProjects> nativeProjects=mockStatic(NativePerimeterProjects.class)) {
            saves.when(()->RaidSavedData.get(server)).thenReturn(data);
            workers.when(()->WorkersBridge.readWorkerOwner(builder)).thenReturn(running.header().owner());
            workers.when(()->WorkersBridge.readOwner(area)).thenReturn(running.header().owner());
            claims.when(()->RecruitsClaimsBridge.getFactionTerritory(level,running.header().faction(), PerimeterTerritory.MAX_CHUNKS))
                    .thenReturn(new RecruitsClaimsBridge.TerritorySnapshot(running.header().faction(),running.header().territory(),null));
            ledgers.when(()->ConstructionEditLedger.get(level)).thenReturn(ledger);
            nativeProjects.when(()->NativePerimeterProjects.gateObservationProblem(eq(level),eq(owner),any(),eq(component)))
                    .thenReturn("Paused: gate approach permissions changed");
            assertEquals("Paused: gate approach permissions changed",PerimeterProjectAuthority.problem(level,builder,area,true,false));
        }
    }
}
