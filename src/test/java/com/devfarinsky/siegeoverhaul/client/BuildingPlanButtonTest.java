package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlanButtonTest extends MinecraftTestSupport {
    @Test void commonCatalogueCardsShowMaterialSpritesWithoutOverlappingText() {
        assertEquals(32, BuildingPlanButton.materialRowY(62));
        for (int height = 60; height < 84; height++) {
            int row = BuildingPlanButton.materialRowY(height);
            assertTrue(row >= 30, "Clearance dimensions must remain above the sprites");
            assertTrue(row + 16 <= height - 14, "Price needs at least four pixels of separation");
        }
    }

    @Test void tinyCardsOmitMaterialsAndTallCardsKeepTheLabeledRow() {
        assertEquals(-1, BuildingPlanButton.materialRowY(59));
        assertEquals(36, BuildingPlanButton.materialRowY(84));
        assertEquals(36, BuildingPlanButton.materialRowY(120));
    }

    @Test void modelsOnlyReplaceTheMaterialFallbackWhenTheyHaveAReadableBoundedSlot() {
        assertFalse(BuildingPlanButton.showsThumbnail(198, 62));
        assertFalse(BuildingPlanButton.showsThumbnail(129, 100));
        assertFalse(BuildingPlanButton.showsThumbnail(198, 83));
        assertTrue(BuildingPlanButton.showsThumbnail(130, 84));
        assertTrue(BuildingPlanButton.showsThumbnail(198, 98));
    }
}
