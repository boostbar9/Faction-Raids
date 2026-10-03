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
    @Test void anUnavailableOrDeadBuilderDoesNotTrapItsOwnersReservation() {
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
        var ledger = new ConstructionEditLedger();
        ledger.register(areaId, java.util.Set.of(net.minecraft.core.BlockPos.ZERO));
        try (var storage = org.mockito.Mockito.mockStatic(ConstructionEditLedger.class)) {
            storage.when(() -> ConstructionEditLedger.get(level)).thenReturn(ledger);
            ProtectedConstructionActions.handle(owner, areaId, ProtectedConstructionActions.CANCEL);
        }
        org.mockito.Mockito.verify(area).removeAuthorized();
        assertFalse(ledger.contains(areaId)); assertTrue(ledger.retired(areaId));
        org.mockito.Mockito.verify(level, org.mockito.Mockito.never()).getBlockState(org.mockito.ArgumentMatchers.any());
    }

}
