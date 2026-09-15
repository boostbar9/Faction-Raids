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
                assertTrue(rowHeight >= 24,
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

    @Test void territoryCardsAreCappedAndLeaveRoomBelowTheGrid() {
        for (int w : new int[]{240, 320, 640, 960, 1920}) {
            for (int h : new int[]{180, 240, 360, 540, 1080}) {
                var layout = CoreHireLayout.fit(w, h);
                int cardHeight = layout.territoryCardHeight();
                assertTrue(cardHeight >= 46);
                assertTrue(cardHeight <= 74);
                assertTrue(layout.territoryFreeTop()
                        >= layout.territoryCardY(2) + cardHeight);
                assertTrue(layout.territoryFreeHeight() >= 0);
                assertTrue(layout.territoryFreeTop() + layout.territoryFreeHeight()
                        <= layout.contentBottom());
            }
        }
    }
}
