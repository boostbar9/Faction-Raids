package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreHiringPriceTest extends MinecraftTestSupport {
    @Test void heroPricesAreAFlatFiftyEmeraldLadderAcrossRarities() {
        int base = CoreHiring.DEFAULT_PRICE_BASE, step = CoreHiring.DEFAULT_PRICE_STEP;
        assertEquals(50, base);
        assertEquals(50, step);
        int[] byTier = new int[CoreHiring.RARITY_NAMES.length];
        for (int role = CoreHiring.HERO_ID_MIN; role <= CoreHiring.HERO_ID_MAX; role++) {
            int tier = CoreHiring.heroTier(role);
            int price = CoreHiring.heroPrice(role, base, step);
            if (byTier[tier] == 0) byTier[tier] = price;
            assertEquals(byTier[tier], price, "heroes of one rarity share a price");
        }
        assertArrayEquals(new int[]{50, 100, 150, 200, 250}, byTier);
    }

    @Test void theLadderFollowsTheConfiguredBaseAndStepAndStaysInRange() {
        assertEquals(120, CoreHiring.heroPrice(CoreHiring.HERO_ID_MIN, 120, 40));
        assertEquals(280, CoreHiring.heroPrice(CoreHiring.HERO_ID_MAX, 120, 40));
        assertEquals(0, CoreHiring.heroPrice(CoreHiring.HERO_ID_MAX, 0, 0));
        assertEquals(32767, CoreHiring.heroPrice(CoreHiring.HERO_ID_MAX, 32767, 32767));
        assertEquals(0, CoreHiring.heroPrice(0, 50, 50), "non-hero roles keep their own pricing path");
    }
}
