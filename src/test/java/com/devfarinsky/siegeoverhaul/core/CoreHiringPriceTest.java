package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreHiringPriceTest extends MinecraftTestSupport {
    @Test void everyRarityTierIsPricedApartEvenOnCheapBaseRecruits() {
        int[] byTier = new int[CoreHiring.RARITY_NAMES.length];
        for (int role = CoreHiring.HERO_ID_MIN; role <= CoreHiring.HERO_ID_MAX; role++) {
            int tier = CoreHiring.heroTier(role);
            int price = CoreHiring.heroPrice(1, role);
            if (byTier[tier] == 0) byTier[tier] = price;
            assertEquals(byTier[tier], price, "heroes of one rarity share a price");
        }
        for (int tier = 1; tier < byTier.length; tier++) {
            assertTrue(byTier[tier] > byTier[tier - 1],
                    "tier " + tier + " must cost more than tier " + (tier - 1));
        }
        assertEquals(CoreHiring.TIER_COST_FLOOR[0], byTier[0]);
    }

    @Test void richerBaseRecruitPricesScaleHeroesByTheirRarityMultiplier() {
        assertEquals(800, CoreHiring.heroPrice(100, CoreHiring.HERO_ID_MIN));
        assertEquals(4000, CoreHiring.heroPrice(100, CoreHiring.HERO_ID_MAX));
        assertEquals(32767, CoreHiring.heroPrice(Integer.MAX_VALUE, CoreHiring.HERO_ID_MAX));
        assertEquals(CoreHiring.TIER_COST_FLOOR[4], CoreHiring.heroPrice(0, CoreHiring.HERO_ID_MAX));
        assertEquals(7, CoreHiring.heroPrice(7, 0));
    }
}
