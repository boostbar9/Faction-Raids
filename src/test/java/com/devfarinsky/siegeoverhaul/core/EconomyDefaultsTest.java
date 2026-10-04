package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyDefaultsTest extends MinecraftTestSupport {

    @Test
    void newWholeTerritoryPerimeterHasAFlat64EmeraldFee() {
        assertEquals(64, TerritoryFortification.PRICE);
    }

    @Test
    void manualCommissionsFormAnAffordableLadderBelowTheCompletePerimeter() {
        assertArrayEquals(new int[]{12, 32, 48, 8, 8, 12},
                java.util.Arrays.stream(DefenseBlueprint.Kind.values()).mapToInt(kind -> kind.price).toArray());
        for (var kind : DefenseBlueprint.Kind.values()) assertTrue(kind.price < TerritoryFortification.PRICE, kind.label);
        assertEquals(DefenseBlueprint.Kind.WALL.price, DefenseBlueprint.Kind.CORNER.price);
        assertTrue(DefenseBlueprint.Kind.STAIRS.price > DefenseBlueprint.Kind.WALL.price);
        assertTrue(DefenseBlueprint.Kind.WATCHTOWER.price < DefenseBlueprint.Kind.GATEHOUSE.price);
        assertEquals(64, PerimeterProject.NEW_PROJECT_PRICE);
    }

    @Test
    void establishedLootAndUpgradePricesRemainUnchanged() {
        assertEquals(5, RaidConfig.VICTORY_EMERALDS_PER_WAVE.get());
        assertArrayEquals(new int[] {480, 400}, SiegeYard.PRICES);
        assertArrayEquals(new int[] {700, 500, 900, 600}, TerritoryBuffs.PRICES);
        assertEquals(16, CoreLoot.price(0));
        assertEquals(48, CoreLoot.price(1));
        assertEquals(96, CoreLoot.price(2));
    }
}
