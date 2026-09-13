package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TreasuryDeliveryTest extends MinecraftTestSupport {
    @Test void liveTransactionsReachOnlyTheirFactionOnceAndStopClearsQueue() {
        var server = mock(MinecraftServer.class);
        var players = mock(PlayerList.class);
        var member = mock(ServerPlayer.class);
        var stranger = mock(ServerPlayer.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of(member, stranger));
        var saved = new RaidSavedData();
        var core = new CompoundTag();
        saved.siegeCores.put("team:test", core);
        var start = new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, server);
        var end = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server);
        TreasuryNotifications.stopped(null);
        try (var lifecycle = mockStatic(ServerLifecycleHooks.class);
             var saves = mockStatic(RaidSavedData.class);
             var keys = mockStatic(SiegeCore.class)) {
            lifecycle.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            saves.when(() -> RaidSavedData.get(server)).thenReturn(saved);
            keys.when(() -> SiegeCore.key(member)).thenReturn("team:test");
            keys.when(() -> SiegeCore.key(stranger)).thenReturn("team:other");
            assertEquals(25, FactionBank.credit(core, 25));
            assertEquals(5, FactionBank.credit(core, 5));
            assertTrue(FactionBank.debit(core, 10));
            assertFalse(FactionBank.debit(core, 999));
            FactionBank.credit(core, 0);
            FactionBank.credit(core.copy(), 50);
            TreasuryNotifications.flush(start);
            verifyNoInteractions(member, stranger);
            TreasuryNotifications.flush(end);
            verify(member, times(3)).sendSystemMessage(any(Component.class));
            var screen = ArgumentCaptor.forClass(Component.class);
            verify(member).displayClientMessage(screen.capture(), eq(true));
            assertEquals("+30 emeralds deposited to Treasury | -10 emeralds removed from Treasury", screen.getValue().getString());
            verifyNoInteractions(stranger);
            clearInvocations(member);
            TreasuryNotifications.flush(end);
            verifyNoInteractions(member);
            FactionBank.credit(core, 1);
            TreasuryNotifications.stopped(null);
            TreasuryNotifications.flush(end);
            verifyNoInteractions(member);
        } finally { TreasuryNotifications.stopped(null); }
    }
}
