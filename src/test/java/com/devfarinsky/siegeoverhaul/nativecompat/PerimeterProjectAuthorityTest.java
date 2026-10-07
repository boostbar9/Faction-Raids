package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterGateProjectFixture;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerimeterProjectAuthorityTest extends MinecraftTestSupport {
    @Test void laterStageUsesSavedComponentPadsAndFreshObservationsOutsideItsRecipeBounds() {
        var prepared = PerimeterGateProjectFixture.stepped();
        var core = new CompoundTag(); core.putLong("BankEmeralds", 64);
        PerimeterProjectStore.prepare(core, prepared, () -> {});
        PerimeterStageJournal.prepare(core, prepared, () -> {});
        BlockPos marker = prepared.header().originalCore();
        PerimeterStageJournal.begin(core, prepared, marker, () -> {});
        PerimeterStageJournal.live(core, prepared, () -> {});
        var project = PerimeterProjectStore.consumeOnce(core, prepared.header().projectId(), prepared.manifestHash(),
                prepared.header().quotedPrice(), false, () -> {}).project();
        project = PerimeterProjectStore.replace(core, project.check(), project.activate(project.check()), () -> {});
        while (project.activeStage() < project.stages().size() - 1) {
            project = PerimeterProjectStore.replace(core, project.check(),
                    project.verifyStage(project.check(), project.expectedStageReceipt()), () -> {});
            PerimeterStageJournal.retired(core, project, () -> {});
            project = PerimeterProjectStore.replace(core, project.check(), project.retireVerifiedStage(project.check()), () -> {});
            PerimeterStageJournal.begin(core, project, marker, () -> {});
            PerimeterStageJournal.live(core, project, () -> {});
            project = PerimeterProjectStore.replace(core, project.check(), project.activate(project.check()), () -> {});
        }
        var running = PerimeterProject.load(project.save());
        var bounds = running.active().layout();
        assertTrue(running.activeStage() > 0);
        assertTrue(running.gateContract().gates().stream().noneMatch(g -> {
            BlockPos p = g.outerCenter();
            return p.getX() >= bounds.min().getX() && p.getX() <= bounds.max().getX()
                    && p.getZ() >= bounds.min().getZ() && p.getZ() <= bounds.max().getZ();
        }), "Later stage must have no gate in its own bounds");
        var data = new RaidSavedData(); data.siegeCores.put(running.header().coreKey(), core);
        var server = mock(MinecraftServer.class); var level = mock(ServerLevel.class); var players = mock(PlayerList.class);
        var owner = mock(ServerPlayer.class); var builder = mock(Mob.class); var area = mock(Entity.class);
        var ledger = mock(ConstructionEditLedger.class); var persistent = new CompoundTag();
        when(level.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(true);
        when(server.getPlayerList()).thenReturn(players); when(players.getPlayer(running.header().owner())).thenReturn(owner);
        when(owner.serverLevel()).thenReturn(level); when(owner.isAlive()).thenReturn(true); when(owner.mayBuild()).thenReturn(true);
        when(builder.level()).thenReturn(level); when(builder.getUUID()).thenReturn(running.header().builder());
        when(area.level()).thenReturn(level); when(area.getPersistentData()).thenReturn(persistent);
        when(area.getUUID()).thenReturn(running.active().areaId()); when(area.blockPosition()).thenReturn(marker);
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(new WorldBorder()); when(level.hasChunkAt(any())).thenReturn(true);
        when(level.mayInteract(eq(owner), any())).thenReturn(true);
        var observations = new java.util.HashMap<>(running.observations());
        when(level.getBlockState(any())).thenAnswer(i -> observations.get(((BlockPos)i.getArgument(0)).asLong()));
        when(ledger.matchesProjectLease(any())).thenReturn(true);
        when(ledger.assignedBuilder(any())).thenAnswer(i -> ((PerimeterProject)i.getArgument(0)).header().builder());
        PerimeterProjectAuthority.stamp(area, running);
        try (var saves = mockStatic(RaidSavedData.class); var workers = mockStatic(WorkersBridge.class);
             var claims = mockStatic(RecruitsClaimsBridge.class); var ledgers = mockStatic(ConstructionEditLedger.class);
             var guards = mockStatic(NativeConstructionGuard.class); var foreign = mockStatic(com.devfarinsky.siegeoverhaul.compat.ClaimBridge.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            workers.when(() -> WorkersBridge.readWorkerOwner(builder)).thenReturn(running.header().owner());
            workers.when(() -> WorkersBridge.readOwner(area)).thenReturn(running.header().owner());
            claims.when(() -> RecruitsClaimsBridge.getFactionTerritory(level, running.header().faction(), PerimeterTerritory.MAX_CHUNKS))
                    .thenReturn(new RecruitsClaimsBridge.TerritorySnapshot(running.header().faction(), running.header().territory(), null));
            claims.when(() -> RecruitsClaimsBridge.isChunkOwnedBy(eq(level), any(), eq(running.header().faction()))).thenReturn(true);
            ledgers.when(() -> ConstructionEditLedger.get(level)).thenReturn(ledger);
            guards.when(() -> NativeConstructionGuard.currentArea(builder)).thenReturn(area);
            guards.when(() -> NativeConstructionGuard.matchesProjectSnapshot(eq(area), any())).thenReturn(true);
            var expected = running.gateContract().gates().stream().map(g -> g.outerCenter().relative(g.facing(), 2))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(4, expected.size());
            assertEquals(expected, NativePerimeterProjects.gateDetourPads(builder, area));
            var changed = running.gateContract().gates().get(0).outerCenter().relative(
                    running.gateContract().gates().get(0).facing(), -6);
            assertTrue(observations.get(changed.asLong()).isAir());
            observations.put(changed.asLong(), Blocks.STONE.defaultBlockState());
            assertTrue(NativePerimeterProjects.gateDetourPads(builder, area).isEmpty(), "Changed inside pad blocks outside detour too");
            observations.put(changed.asLong(), Blocks.AIR.defaultBlockState());
            assertEquals(expected, NativePerimeterProjects.gateDetourPads(builder, area));
            when(level.hasChunkAt(changed)).thenReturn(false);
            assertTrue(NativePerimeterProjects.gateDetourPads(builder, area).isEmpty());
            when(level.hasChunkAt(changed)).thenReturn(true);
            PerimeterProjectStore.replace(core, running.check(), running.cancel(running.check(), "Fixture cancellation"), () -> {});
            assertTrue(NativePerimeterProjects.gateDetourPads(builder, area).isEmpty());
            verify(level, never()).setBlock(any(), any(), anyInt());
        }
    }

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
            nativeProjects.when(()->NativePerimeterProjects.assignedBuilder(builder,running)).thenReturn(true);
            nativeProjects.when(()->NativePerimeterProjects.gateObservationProblem(eq(level),eq(owner),any(),eq(component)))
                    .thenReturn("Paused: gate approach permissions changed");
            assertEquals("Paused: gate approach permissions changed",PerimeterProjectAuthority.problem(level,builder,area,true,false));
        }
    }
}
