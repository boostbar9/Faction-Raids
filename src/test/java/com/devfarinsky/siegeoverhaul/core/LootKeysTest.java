package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootKeysTest extends MinecraftTestSupport {

    private ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getPersistentData()).thenReturn(new CompoundTag());
        return player;
    }

    @Test void killsAccumulateUntilTheyMintExactlyOneKey() {
        ServerPlayer player = player();
        int needed = LootKeys.killsPerKey();
        for (int i = 1; i < needed; i++) {
            LootKeys.credit(player, 0);
            assertEquals(0, LootKeys.keys(player));
            assertEquals(i, LootKeys.progress(player));
        }
        LootKeys.credit(player, 0);
        assertEquals(1, LootKeys.keys(player));
        assertEquals(0, LootKeys.progress(player));
    }

    @Test void plundererBonusSpeedsUpTheNextKeyWithoutSkippingAny() {
        ServerPlayer player = player();
        int needed = LootKeys.killsPerKey();
        for (int i = 0; i < needed; i++) LootKeys.credit(player, 1);
        // Two progress per kill means one full key plus banked leftovers,
        // never more than one key from a single kill.
        assertEquals(2, LootKeys.keys(player));
        assertEquals(0, LootKeys.progress(player));
    }

    @Test void spendingConsumesOneKeyAndStopsAtZero() {
        ServerPlayer player = player();
        for (int i = 0; i < LootKeys.killsPerKey(); i++) LootKeys.credit(player, 0);
        assertEquals(1, LootKeys.keys(player));
        assertTrue(LootKeys.spend(player));
        assertEquals(0, LootKeys.keys(player));
        assertFalse(LootKeys.spend(player));
        assertEquals(0, LootKeys.keys(player));
    }

    @Test void keysAreCappedAndDisabledByConfig() {
        ServerPlayer player = player();
        for (int i = 0; i < LootKeys.killsPerKey() * (LootKeys.MAX_KEYS + 4); i++) {
            LootKeys.credit(player, 0);
        }
        assertEquals(LootKeys.MAX_KEYS, LootKeys.keys(player));

        RaidConfig.ENABLE_KILL_LOOT_KEYS.set(false);
        ServerPlayer disabled = player();
        for (int i = 0; i < LootKeys.killsPerKey() * 3; i++) LootKeys.credit(disabled, 0);
        assertEquals(0, LootKeys.keys(disabled));
        assertEquals(0, LootKeys.progress(disabled));
        RaidConfig.ENABLE_KILL_LOOT_KEYS.set(true);
    }
}
