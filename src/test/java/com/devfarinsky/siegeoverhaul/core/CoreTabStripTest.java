package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class CoreTabStripTest {
    @Test void currentAndFuturePagesKeepReadableNonOverlappingTargets() {
        for (int w : new int[]{240, 320, 400, 640, 960, 1920}) {
            for (int h : new int[]{180, 240, 360, 1080}) {
                var layout = CoreHireLayout.fit(w, h);
                for (int count = 1; count <= 40; count++) {
                    for (int selected = 0; selected < count; selected++) {
                        var strip = layout.tabs(count, selected);
                        assertTrue(strip.shows(selected));
                        assertTrue(strip.tabWidth() >= CoreTabStrip.MIN_TAB_WIDTH);
                        int previousEnd = strip.x() + (strip.overflow() ? CoreTabStrip.ARROW_WIDTH : 0);
                        for (int i = strip.first(); i < strip.first() + strip.visible(); i++) {
                            assertTrue(strip.tabX(i) >= previousEnd);
                            previousEnd = strip.tabX(i) + strip.tabWidth();
                        }
                        assertTrue(previousEnd <= strip.x() + strip.width()
                                - (strip.overflow() ? CoreTabStrip.ARROW_WIDTH : 0));
                    }
                }
            }
        }
    }
    @Test void cyclingReachesEveryPageAndWrapsBothWays() {
        var strip = CoreHireLayout.fit(320, 240).tabs(40, 0);
        var reached = new HashSet<Integer>();
        int selected = 0;
        for (int i = 0; i < 40; i++) { reached.add(selected); selected = strip.next(selected, 1); }
        assertEquals(40, reached.size());
        assertEquals(0, selected);
        assertEquals(39, strip.next(0, -1));
        assertEquals(0, strip.next(39, 1));
        assertEquals(4, strip.next(4, 0));
    }
    @Test void roomyCurrentCatalogNeedsNoOverflowAndTinyWindowKeepsItReachable() {
        int count = CoreCommandPage.values().length;
        assertFalse(CoreHireLayout.fit(960, 540).tabs(count, 0).overflow());
        for (int i = 0; i < count; i++) {
            var strip = CoreHireLayout.fit(240, 180).tabs(count, i);
            assertTrue(strip.overflow());
            assertTrue(strip.shows(i));
        }
    }
    @Test void headerBalancesNeverOverlapTitleOrCloseButton() {
        for (int w = 240; w <= 1920; w += 17) {
            for (int h : new int[]{180, 240, 360, 540}) {
                var layout = CoreHireLayout.fit(w, h);
                assertTrue(layout.headerTitleWidth() >= 42);
                assertTrue(layout.x() + 42 + layout.headerTitleWidth() < layout.treasuryX());
                assertTrue(layout.treasuryX() + layout.treasuryWidth() < layout.purseX());
                assertTrue(layout.purseX() + layout.purseWidth() < layout.x() + layout.width() - 22);
                assertTrue(layout.y() + 30 <= layout.tabY());
            }
        }
    }
    @Test void scopedHitTestingUsesLogicalCoordinatesAndExcludesAdjacentBands() {
        var layout = CoreHireLayout.fit(240, 180);
        var strip = layout.tabs(5, 2);
        double physicalX = (strip.tabX(2) + 5) * layout.scale();
        double physicalY = (layout.tabY() + 5) * layout.scale();
        assertTrue(CoreTabStrip.contains(layout.logicalX(physicalX), layout.logicalY(physicalY),
                strip.x(), layout.tabY(), strip.width(), CoreHireLayout.TAB_HEIGHT));
        assertFalse(CoreTabStrip.contains(strip.x(), layout.ribbonY(), strip.x(), layout.tabY(), strip.width(), CoreHireLayout.TAB_HEIGHT));
        assertFalse(CoreTabStrip.contains(strip.x() - 1, layout.tabY(), strip.x(), layout.tabY(), strip.width(), CoreHireLayout.TAB_HEIGHT));
        assertFalse(CoreTabStrip.contains(strip.x() + strip.width(), layout.tabY(), strip.x(), layout.tabY(), strip.width(), CoreHireLayout.TAB_HEIGHT));
    }
    @Test void staleSelectionClampsToAnExistingPage() {
        assertTrue(CoreHireLayout.fit(320,240).tabs(5,99).shows(4));
        assertTrue(CoreHireLayout.fit(320,240).tabs(5,-10).shows(0));
        assertThrows(IllegalArgumentException.class, () -> CoreTabStrip.fit(0, 284, 0, 0, 2));
    }
}
