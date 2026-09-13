package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyDefaultsTest extends MinecraftTestSupport {

    @Test
    void v428TuningAndEstablishedLootPricesSurviveThePatchRebase() {
        assertEquals(5, RaidConfig.VICTORY_EMERALDS_PER_WAVE.get());
        assertEquals(900, TerritoryFortification.PRICE);
        assertArrayEquals(new int[] {480, 400}, SiegeYard.PRICES);
        assertArrayEquals(new int[] {700, 500, 900, 600}, TerritoryBuffs.PRICES);
        assertEquals(16, CoreLoot.price(0));
        assertEquals(48, CoreLoot.price(1));
        assertEquals(96, CoreLoot.price(2));
    }
}
