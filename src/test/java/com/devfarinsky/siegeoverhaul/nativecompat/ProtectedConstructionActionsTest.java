package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProtectedConstructionActionsTest extends com.devfarinsky.siegeoverhaul.MinecraftTestSupport {
    private record ReportFixture(PerimeterProject project, ConstructionEditLedger ledger, ServerLevel level,
                                 Mob builder, CompoundTag data) {}

    private static ReportFixture reportFixture(boolean succession) {
        var prepared = ConstructionProjectLedgerTest.project(); var ledger = new ConstructionEditLedger();
        assertTrue(ledger.registerProject(prepared)); assertTrue(ledger.leaseProjectStage(prepared));
        var core = new CompoundTag(); core.putLong("BankEmeralds", 64);
        PerimeterProjectStore.prepare(core, prepared, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, prepared.header().projectId(), prepared.manifestHash(),
                64, false, () -> {}).project();
        var project = paid.activate(paid.check());
        UUID assigned = project.header().builder();
        if (succession) {
            assigned = UUID.randomUUID();
            assertTrue(ledger.projectBuilderDestroyed(project, project.header().builder(), project.active().areaId(), ledger.generation()));
            assertTrue(ledger.replaceDeadBuilder(project, assigned));
            ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        }
        var level = mock(ServerLevel.class); var builder = mock(Mob.class); var data = new CompoundTag();
        when(builder.getUUID()).thenReturn(assigned); when(builder.isAlive()).thenReturn(true);
        when(builder.level()).thenReturn(level); when(builder.getPersistentData()).thenReturn(data);
        when(level.getEntity(assigned)).thenReturn(builder);
        PerimeterProjectLink.set(builder, project, assigned);
        assertTrue(PerimeterProjectLink.bindLedgerGeneration(builder, ledger.generation()));
        return new ReportFixture(project, ledger, level, builder, data);
    }

    @Test void projectReportUsesSavedSuccessorAndLeavesOriginalPaidIdentityAndAllStateUnchanged() {
        var f = reportFixture(true); var original = mock(Mob.class);
        when(original.isAlive()).thenReturn(false);
        when(f.level().getEntity(f.project().header().builder())).thenReturn(original);
        var projectBefore = f.project().save(); var ledgerBefore = f.ledger().save(new CompoundTag());
        var builderBefore = f.data().copy();
        try (var storage = mockStatic(ConstructionEditLedger.class); var workers = mockStatic(WorkersBridge.class)) {
            storage.when(() -> ConstructionEditLedger.get(f.level())).thenReturn(f.ledger());
            workers.when(() -> WorkersBridge.isBuilder(f.builder())).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(f.builder())).thenReturn(f.project().header().owner());
            assertSame(f.builder(), ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            assertNotEquals(f.project().header().builder(), f.builder().getUUID());
            assertEquals(projectBefore, f.project().save()); assertEquals(ledgerBefore, f.ledger().save(new CompoundTag()));
            assertEquals(builderBefore, f.data());
            verify(f.level(), never()).getEntity(f.project().header().builder());
            verify(f.level(), never()).getBlockState(any()); verify(f.level(), never()).setBlock(any(), any(), anyInt());
            verifyNoInteractions(original);
        }
    }

    @Test void projectReportStillResolvesTheUnchangedOriginalAssignment() {
        var f = reportFixture(false);
        try (var storage = mockStatic(ConstructionEditLedger.class); var workers = mockStatic(WorkersBridge.class)) {
            storage.when(() -> ConstructionEditLedger.get(f.level())).thenReturn(f.ledger());
            workers.when(() -> WorkersBridge.isBuilder(f.builder())).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(f.builder())).thenReturn(f.project().header().owner());
            assertSame(f.builder(), ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            assertEquals(f.project().header().builder(), f.builder().getUUID());
        }
    }

    @Test void projectReportRejectsUnavailableForeignStaleAndCrossProjectActorsWithoutOriginalFallback() {
        var f = reportFixture(true); UUID assigned = f.builder().getUUID(); var validData = f.data().copy();
        var ledgerBefore = f.ledger().save(new CompoundTag());
        try (var storage = mockStatic(ConstructionEditLedger.class); var workers = mockStatic(WorkersBridge.class)) {
            storage.when(() -> ConstructionEditLedger.get(f.level())).thenReturn(f.ledger());
            workers.when(() -> WorkersBridge.isBuilder(f.builder())).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(f.builder())).thenReturn(f.project().header().owner());
            assertSame(f.builder(), ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            when(f.level().getEntity(assigned)).thenReturn(null);
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            when(f.level().getEntity(assigned)).thenReturn(f.builder());
            when(f.builder().isAlive()).thenReturn(false);
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            when(f.builder().isAlive()).thenReturn(true);
            when(f.builder().level()).thenReturn(mock(ServerLevel.class));
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            when(f.builder().level()).thenReturn(f.level());
            when(f.builder().getUUID()).thenReturn(f.project().header().builder());
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            when(f.builder().getUUID()).thenReturn(assigned);
            workers.when(() -> WorkersBridge.isBuilder(f.builder())).thenReturn(false);
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            workers.when(() -> WorkersBridge.isBuilder(f.builder())).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(f.builder())).thenReturn(UUID.randomUUID());
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            workers.when(() -> WorkersBridge.readWorkerOwner(f.builder())).thenReturn(f.project().header().owner());
            for (String field : java.util.List.of("Project", "LedgerGeneration")) {
                f.data().getCompound(PerimeterProjectLink.KEY).putUUID(field, UUID.randomUUID());
                var damaged = f.data().copy();
                assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
                assertEquals(damaged, f.data(), "A report must not repair a stale selector");
                f.data().put(PerimeterProjectLink.KEY, validData.getCompound(PerimeterProjectLink.KEY).copy());
            }
            f.data().getCompound(PerimeterProjectLink.KEY).remove("LedgerGeneration");
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            f.data().put(PerimeterProjectLink.KEY, validData.getCompound(PerimeterProjectLink.KEY).copy());
            assertSame(f.builder(), ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            assertEquals(ledgerBefore, f.ledger().save(new CompoundTag()));
            verify(f.level(), never()).getEntity(f.project().header().builder());
        }
    }

    @Test void projectReportNeverFallsBackWhenAssignmentHistoryOrLedgerIdentityIsCorrupt() {
        var f = reportFixture(true); var damaged = f.ledger().save(new CompoundTag());
        var entries = damaged.getCompound("BuilderAssignments").getList("Projects", Tag.TAG_COMPOUND);
        assertEquals(1, entries.size()); entries.getCompound(0).putString("Hash", "b".repeat(64));
        var corrupt = ConstructionEditLedger.load(damaged);
        try (var storage = mockStatic(ConstructionEditLedger.class)) {
            storage.when(() -> ConstructionEditLedger.get(f.level())).thenReturn(corrupt);
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            storage.when(() -> ConstructionEditLedger.get(f.level())).thenReturn(new ConstructionEditLedger());
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            storage.when(() -> ConstructionEditLedger.get(f.level())).thenThrow(new IllegalStateException("Unavailable fixture"));
            assertNull(ProtectedConstructionActions.projectBuilderForReport(f.level(), f.project()));
            verify(f.level(), never()).getEntity(any(UUID.class));
        }
    }

    @Test void currentReportCountsStillRequireExactMarkerAndAuthoritativeProjectCheck() {
        var f = reportFixture(true); var p = f.project(); var area = mock(ProtectedBuildArea.class);
        var tag = new CompoundTag();
        when(area.getPersistentData()).thenReturn(tag); when(area.level()).thenReturn(f.level());
        when(area.getUUID()).thenReturn(p.active().areaId()); when(area.isAlive()).thenReturn(true);
        var scope = new PerimeterProjectAuthority.Scope(p.header().projectId(), p.header().generation(),
                p.manifestHash(), p.header().coreKey(), p.activeStage(), p.active().digest());
        try (var authority = mockStatic(PerimeterProjectAuthority.class)) {
            authority.when(() -> PerimeterProjectAuthority.read(tag)).thenReturn(scope);
            authority.when(() -> PerimeterProjectAuthority.project(f.level(), scope)).thenReturn(p);
            assertTrue(ProtectedConstructionActions.validProjectProgress(area, f.builder(), p));
            when(area.getUUID()).thenReturn(UUID.randomUUID());
            assertFalse(ProtectedConstructionActions.validProjectProgress(area, f.builder(), p));
            when(area.getUUID()).thenReturn(p.active().areaId());
            authority.when(() -> PerimeterProjectAuthority.problem(f.level(), f.builder(), area, false, true))
                    .thenReturn("Paused: marker snapshot or lease mismatch");
            assertFalse(ProtectedConstructionActions.validProjectProgress(area, f.builder(), p));
            authority.when(() -> PerimeterProjectAuthority.problem(f.level(), f.builder(), area, false, true)).thenReturn(null);
            authority.when(() -> PerimeterProjectAuthority.project(f.level(), scope))
                    .thenReturn(p.cancel(p.check(), "Newer authoritative state"));
            assertFalse(ProtectedConstructionActions.validProjectProgress(area, f.builder(), p));
            authority.when(() -> PerimeterProjectAuthority.project(f.level(), scope)).thenReturn(p);
            authority.when(() -> PerimeterProjectAuthority.read(tag)).thenReturn(new PerimeterProjectAuthority.Scope(
                    UUID.randomUUID(), scope.generation(), scope.manifestHash(), scope.coreKey(), scope.stage(), scope.stageDigest()));
            assertFalse(ProtectedConstructionActions.validProjectProgress(area, f.builder(), p));
            authority.when(() -> PerimeterProjectAuthority.read(tag)).thenReturn(new PerimeterProjectAuthority.Scope(
                    scope.projectId(), scope.generation() + 1, scope.manifestHash(), scope.coreKey(), scope.stage(), scope.stageDigest()));
            assertFalse(ProtectedConstructionActions.validProjectProgress(area, f.builder(), p));
            assertTrue(tag.isEmpty());
            verify(area, never()).removeAuthorized(); verify(f.level(), never()).setBlock(any(), any(), anyInt());
        }
    }

    @Test void cancellationAndProjectionRequireTheExactNearbyOwner() {
        UUID owner = UUID.randomUUID();
        assertTrue(ProtectedConstructionActions.authorized(owner, owner, true));
        assertFalse(ProtectedConstructionActions.authorized(UUID.randomUUID(), owner, true));
        assertFalse(ProtectedConstructionActions.authorized(owner, owner, false));
        assertFalse(ProtectedConstructionActions.authorized(null, owner, true));
    }
    @Test void anUnavailableProvenBuilderCanCancelButOlderUnprovenBuilderRetainsItsSnapshot() {
        var level = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var server = org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        var owner = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var area = org.mockito.Mockito.mock(ProtectedBuildArea.class);
        UUID ownerId = UUID.randomUUID(), areaId = UUID.randomUUID(), builderId = UUID.randomUUID();
        org.mockito.Mockito.when(owner.serverLevel()).thenReturn(level);
        org.mockito.Mockito.when(owner.getServer()).thenReturn(server);
        org.mockito.Mockito.when(owner.getUUID()).thenReturn(ownerId);
        org.mockito.Mockito.when(owner.isAlive()).thenReturn(true);
        org.mockito.Mockito.when(server.getAllLevels()).thenReturn(java.util.List.of(level));
        org.mockito.Mockito.when(level.getEntity(areaId)).thenReturn(area);
        org.mockito.Mockito.when(area.isAlive()).thenReturn(true);
        org.mockito.Mockito.when(area.getUUID()).thenReturn(areaId);
        org.mockito.Mockito.when(area.getPlayerUUID()).thenReturn(ownerId);
        org.mockito.Mockito.when(area.reservedBuilderId()).thenReturn(builderId);
        org.mockito.Mockito.when(area.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        var ledger = new ConstructionEditLedger();
        ledger.register(areaId, java.util.Set.of(net.minecraft.core.BlockPos.ZERO));
        try (var storage = org.mockito.Mockito.mockStatic(ConstructionEditLedger.class)) {
            storage.when(() -> ConstructionEditLedger.get(level)).thenReturn(ledger);
            ProtectedConstructionActions.handle(owner, areaId, ProtectedConstructionActions.CANCEL);
            org.mockito.Mockito.verify(area, org.mockito.Mockito.never()).removeAuthorized();
            assertTrue(ledger.contains(areaId));
            assertTrue(ledger.retainHandLifecycle(new net.minecraft.nbt.CompoundTag(), builderId, ownerId, areaId));
            ProtectedConstructionActions.handle(owner, areaId, ProtectedConstructionActions.CANCEL);
        }
        org.mockito.Mockito.verify(area).removeAuthorized();
        assertFalse(ledger.contains(areaId)); assertTrue(ledger.retired(areaId));
        org.mockito.Mockito.verify(level, org.mockito.Mockito.never()).getBlockState(org.mockito.ArgumentMatchers.any());
    }

    @Test void nativeSectionCancellationRoutesToTheWholeProjectWithoutRetiringOneLease() {
        var level = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var sender = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var area = org.mockito.Mockito.mock(ProtectedBuildArea.class);
        var project = org.mockito.Mockito.mock(com.devfarinsky.siegeoverhaul.core.PerimeterProject.class);
        var header = org.mockito.Mockito.mock(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Header.class);
        var stage = org.mockito.Mockito.mock(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage.class);
        UUID owner = UUID.randomUUID(), areaId = UUID.randomUUID(), projectId = UUID.randomUUID();
        var persistent = new net.minecraft.nbt.CompoundTag();
        var scope = new PerimeterProjectAuthority.Scope(projectId, 7, "a".repeat(64), "team:test", 0, "b".repeat(64));
        org.mockito.Mockito.when(sender.serverLevel()).thenReturn(level);
        org.mockito.Mockito.when(sender.getUUID()).thenReturn(owner);
        org.mockito.Mockito.when(sender.isAlive()).thenReturn(true);
        org.mockito.Mockito.when(level.getEntity(areaId)).thenReturn(area);
        org.mockito.Mockito.when(area.isAlive()).thenReturn(true);
        org.mockito.Mockito.when(area.getUUID()).thenReturn(areaId);
        org.mockito.Mockito.when(area.getPlayerUUID()).thenReturn(owner);
        org.mockito.Mockito.when(area.getPersistentData()).thenReturn(persistent);
        org.mockito.Mockito.when(project.header()).thenReturn(header);
        org.mockito.Mockito.when(header.owner()).thenReturn(owner);
        org.mockito.Mockito.when(project.stages()).thenReturn(java.util.List.of(stage));
        org.mockito.Mockito.when(stage.areaId()).thenReturn(areaId);
        org.mockito.Mockito.when(stage.digest()).thenReturn("b".repeat(64));
        try (var authority = org.mockito.Mockito.mockStatic(PerimeterProjectAuthority.class);
             var runtime = org.mockito.Mockito.mockStatic(NativePerimeterProjects.class);
             var ledger = org.mockito.Mockito.mockStatic(ConstructionEditLedger.class)) {
            authority.when(() -> PerimeterProjectAuthority.tracked(area)).thenReturn(true);
            authority.when(() -> PerimeterProjectAuthority.read(persistent)).thenReturn(scope);
            authority.when(() -> PerimeterProjectAuthority.project(level, scope)).thenReturn(project);
            runtime.when(() -> NativePerimeterProjects.cancelFromMarker(sender, area)).thenReturn(true);
            ProtectedConstructionActions.handle(sender, areaId, ProtectedConstructionActions.CANCEL);
            runtime.verify(() -> NativePerimeterProjects.cancelFromMarker(sender, area));
            ledger.verifyNoInteractions();
            org.mockito.Mockito.verify(area, org.mockito.Mockito.never()).removeAuthorized();
        }
    }

}
