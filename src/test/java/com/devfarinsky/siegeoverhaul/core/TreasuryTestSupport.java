package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

abstract class TreasuryTestSupport extends MinecraftTestSupport {
    RaidSavedData saved;
    MinecraftServer server;
    MockedStatic<RaidSavedData> storage;
    @BeforeEach void openTreasury() {
        saved = new RaidSavedData();
        server = mock(MinecraftServer.class);
        storage = mockStatic(RaidSavedData.class);
        storage.when(() -> RaidSavedData.get(server)).thenReturn(saved);
    }
    @AfterEach void closeTreasury() { storage.close(); }
    CompoundTag fund(ServerPlayer player, long amount) {
        try {
            var field = ServerPlayer.class.getField("server");
            field.setAccessible(true); field.set(player, server);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        PlayerTeam team = mock(PlayerTeam.class);
        when(team.getName()).thenReturn("test"); when(player.getTeam()).thenReturn(team);
        CompoundTag core = new CompoundTag(); core.putLong("BankEmeralds", amount);
        saved.siegeCores.put("team:test", core);
        return core;
    }
}
