package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreHireLayoutTest {
    @Test
    void roomyViewportKeepsDetailedLayout() {
        CoreHireLayout layout = CoreHireLayout.fit(1920, 1080);
        assertFalse(layout.compact());
        assertEquals(1.0F, layout.scale());
        assertTrue(layout.cardWidth() >= 230);
        assertTrue(layout.cardHeight() >= 96);
        assertEquals(CoreHireLayout.PAGE_HEADER_HEIGHT, layout.pageHeaderHeight());
        assertEquals(layout.pageHeaderY() + CoreHireLayout.PAGE_HEADER_HEIGHT
                + CoreHireLayout.PAGE_HEADER_GAP, layout.contentY());
    }

    @Test
    void constrainedViewportFallsBackToCompactLayout() {
        CoreHireLayout layout = CoreHireLayout.fit(640, 320);
        assertTrue(layout.compact());
        assertTrue(layout.cardWidth() < 230 || layout.cardHeight() < 96);
        assertEquals(0, layout.pageHeaderHeight());
        assertEquals(layout.pageHeaderY(), layout.contentY());
    }

    @Test
    void tinyWindowUsesLogicalCanvasScaling() {
        CoreHireLayout layout = CoreHireLayout.fit(240, 180);
        assertTrue(layout.scale() < 1.0F);
        assertEquals(CoreHireLayout.MIN_VIEWPORT_WIDTH, layout.viewportWidth());
        assertEquals(CoreHireLayout.MIN_VIEWPORT_HEIGHT, layout.viewportHeight());
        assertEquals(120, layout.logicalX(90));
        assertEquals(120, layout.logicalY(90));
    }
}
