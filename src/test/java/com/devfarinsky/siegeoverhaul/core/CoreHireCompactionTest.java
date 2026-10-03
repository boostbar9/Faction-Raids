package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the compacted Loot and Territory bands added in 4.29.0. */
class CoreHireCompactionTest extends MinecraftTestSupport {

    @Test void lootRowsAreCappedSoTheFreedBandIsRealSpace() {
        for (int w : new int[]{240, 320, 640, 960, 1920}) {
            for (int h : new int[]{180, 240, 360, 540, 1080}) {
                var layout = CoreHireLayout.fit(w, h);
                int rowHeight = layout.marketHeight();
                assertTrue(rowHeight >= 40,
                        "row too short at " + w + "x" + h);
                assertTrue(rowHeight <= 56,
                        "row not capped at " + w + "x" + h);
                // The freed band must never start above the last loot row.
                assertTrue(layout.marketFreeTop() >= layout.marketY(2) + rowHeight);
                assertTrue(layout.marketFreeTop() + layout.marketFreeHeight()
                        <= layout.contentBottom());
            }
        }
    }

    @Test void lootTitlesAndRevealProgressStayAboveTheirControlsAtEverySize() {
        for (int w = 240; w <= 1100; w += 17) for (int h = 180; h <= 700; h += 13) {
            var layout = CoreHireLayout.fit(w, h);
            for (int row = 0; row < 3; row++) {
                int top = layout.marketY(row);
                int buttonTop = top + layout.marketHeight() - (layout.compact() ? 17 : 21);
                int progressBottom = top + (layout.compact() ? 20 : 32);
                assertTrue(progressBottom + 2 <= buttonTop, w + "x" + h);
                assertTrue(top + layout.marketHeight() <= layout.contentBottom());
            }
        }
    }

    @Test void roomyTerritoryCardsActuallyDisplayUpgradeDescriptions() {
        var layout = CoreHireLayout.fit(960, 540);
        assertFalse(layout.compact());
        assertTrue(layout.territoryDescriptionLines() >= 3);
    }

    @Test void territoryCardsAreCappedAndLeaveRoomBelowTheGrid() {
        for (int w : new int[]{240, 320, 640, 960, 1920}) {
            for (int h : new int[]{180, 240, 360, 540, 1080}) {
                var layout = CoreHireLayout.fit(w, h);
                int cardHeight = layout.territoryCardHeight();
                assertTrue(cardHeight >= 46);
                assertTrue(cardHeight <= 92);
                assertTrue(layout.territoryFreeTop()
                        >= layout.territoryCardY(2) + cardHeight);
                assertTrue(layout.territoryFreeHeight() >= 0);
                assertTrue(layout.territoryFreeTop() + layout.territoryFreeHeight()
                        <= layout.contentBottom());
            }
        }
    }
    @Test void territoryUsesTheFormerPerimeterBandWithoutOverlappingItsFooter() {
        for (int w = 240; w <= 640; w += 7) for (int h = 180; h <= 480; h += 7) {
            var layout = CoreHireLayout.fit(w, h);
            int cardBottom = layout.territoryCardY(3) + layout.territoryCardHeight();
            assertTrue(cardBottom <= layout.contentBottom(), w + "x" + h);
            assertTrue(layout.territoryFreeTop() + layout.territoryFreeHeight()
                    <= layout.contentBottom(), w + "x" + h);
        }
    }
}
