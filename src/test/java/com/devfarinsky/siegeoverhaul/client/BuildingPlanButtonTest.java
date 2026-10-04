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

    @Test void modelsRemainVisibleOnPagedCompactCardsAndDetailedCards() {
        assertFalse(BuildingPlanButton.showsThumbnail(198, 62));
        assertFalse(BuildingPlanButton.showsThumbnail(99, 100));
        assertTrue(BuildingPlanButton.showsThumbnail(198, 83));
        assertTrue(BuildingPlanButton.showsThumbnail(140, 78));
        assertTrue(BuildingPlanButton.showsThumbnail(130, 84));
        assertTrue(BuildingPlanButton.showsThumbnail(198, 98));
    }
    @Test void everySupportedCataloguePageHasAnActualModelSlot() {
        for (int w = 120; w <= 1920; w += 23) for (int h = 90; h <= 1080; h += 19) {
            var layout = new com.devfarinsky.siegeoverhaul.core.CoreBuildingLayout(
                    com.devfarinsky.siegeoverhaul.core.CoreHireLayout.fit(w, h));
            assertTrue(BuildingPlanButton.showsThumbnail(layout.planWidth(), layout.planHeight()), w + "x" + h);
        }
    }

}
