package com.devfarinsky.siegeoverhaul.nativecompat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedConstructionActionsTest extends com.devfarinsky.siegeoverhaul.MinecraftTestSupport {
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
