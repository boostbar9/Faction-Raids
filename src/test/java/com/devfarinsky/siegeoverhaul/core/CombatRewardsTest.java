package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatRewardsTest extends MinecraftTestSupport {
    @Test void streakMultiplierClimbsAtThreeSixAndTenKills() {
        int[] expected = {1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4};
        for (int streak = 0; streak < expected.length; streak++)
            assertEquals(expected[streak], CombatRewards.multiplier(streak), "streak " + streak);
        assertEquals(4, CombatRewards.multiplier(500));
    }

    @Test void streakContinuesOnlyInsideTheWindow() {
        assertEquals(1, CombatRewards.nextStreak(0, 0, 100, 160), "first kill");
        assertEquals(4, CombatRewards.nextStreak(3, 100, 260, 160), "exactly at the window edge");
        assertEquals(1, CombatRewards.nextStreak(3, 100, 261, 160), "too slow");
        assertEquals(1, CombatRewards.nextStreak(3, 500, 100, 160), "clock went backwards after a restart");
    }

    @Test void everySourceSharesOnePerRaidCap() {
        CompoundTag campaign = new CompoundTag();
        assertEquals(10, CombatRewards.payable(campaign, 10, 25));
        CombatRewards.spend(campaign, 10);
        assertEquals(15, CombatRewards.payable(campaign, 40, 25), "only the rest of the cap");
        CombatRewards.spend(campaign, 15);
        assertEquals(0, CombatRewards.payable(campaign, 1, 25));
        assertEquals(7, CombatRewards.payable(campaign, 7, 0), "0 means no cap");
        assertEquals(0, CombatRewards.payable(campaign, 0, 0));
        assertEquals(25, campaign.getInt(CombatRewards.PAID));
    }

    @Test void rollsStayBetweenHalfAndTheMaximum() {
        RandomSource random = RandomSource.create(42);
        for (int max : new int[]{1, 2, 7, 16}) for (int i = 0; i < 200; i++) {
            int value = CombatRewards.roll(random, max);
            assertTrue(value >= (max + 1) / 2 && value <= max, max + " -> " + value);
        }
        assertEquals(0, CombatRewards.roll(random, 0));
    }

    @Test void rewardPopupRoundTripsAndRejectsNonsense() {
        var packet = new RaidNetwork.RewardPopup(RaidNetwork.RewardPopup.JACKPOT, 14, 6, 3);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, RaidNetwork.RewardPopup.decode(buffer)); }
        finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.RewardPopup(3, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.RewardPopup(0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.RewardPopup(0, 1, 1, 0));
    }
}
