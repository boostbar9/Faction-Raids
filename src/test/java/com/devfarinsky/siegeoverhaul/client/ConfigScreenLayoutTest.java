package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigScreenLayoutTest {
    @Test void inputsLabelsAndActionsFitEverySupportedLogicalResolution() {
        for (int[] size : new int[][]{{320, 240}, {480, 360}, {854, 480}, {240, 180}}) {
            var layout = ConfigScreenLayout.fit(size[0], size[1]);
            inside(layout.window, size[0], size[1]);
            inside(layout.filter, size[0], size[1]);
            inside(layout.viewport, size[0], size[1]);
            inside(layout.scrollbar, size[0], size[1]);
            assertTrue(layout.capacity() > 0);
            assertTrue(layout.filter.bottom() < layout.viewport.y());
            assertTrue(layout.viewport.bottom() <= layout.footerY - 18);
            assertTrue(layout.footerY + 20 < layout.window.bottom());
            for (int slot = 0; slot < layout.capacity(); slot++) {
                assertTrue(layout.fitsRow(slot));
                var label = layout.label(slot);
                var input = layout.input(slot);
                inside(label, size[0], size[1]);
                inside(input, size[0], size[1]);
                assertTrue(input.bottom() < layout.row(slot).bottom());
                assertTrue(input.right() < layout.viewport.right());
                if (layout.stacked) assertTrue(label.bottom() < input.y());
                else assertTrue(label.right() < input.x());
            }
            assertFalse(layout.fitsRow(-1));
            assertFalse(layout.fitsRow(layout.capacity()));
        }
    }

    @Test void compactWindowsStackFieldsAndWideWindowsUseTheAvailableSpace() {
        assertTrue(ConfigScreenLayout.fit(320, 240).stacked);
        assertFalse(ConfigScreenLayout.fit(480, 360).stacked);
        assertTrue(ConfigScreenLayout.fit(854, 480).viewport.width()
                > ConfigScreenLayout.fit(480, 360).viewport.width());
        var tiny = ConfigScreenLayout.fit(200, 120);
        assertEquals(0, tiny.capacity());
        inside(tiny.filter, 200, 120);
        assertTrue(tiny.footerY + 20 <= 120);
    }

    @Test void everyHiddenSettingCanBeRevealedWithoutPartialRowsOrOverscroll() {
        for (int[] size : new int[][]{{320, 240}, {480, 360}, {854, 480}}) {
            var layout = ConfigScreenLayout.fit(size[0], size[1]);
            int first = 0;
            for (int index = 0; index < 173; index++) {
                first = layout.ensureVisible(first, index, 173);
                assertTrue(index >= first && index < first + layout.capacity());
                assertTrue(layout.fitsRow(index - first));
            }
            assertEquals(layout.maxFirst(173), first);
            for (int index = 172; index >= 0; index--) {
                first = layout.ensureVisible(first, index, 173);
                assertTrue(index >= first && index < first + layout.capacity());
            }
            assertEquals(0, first);
            assertEquals(0, layout.clampFirst(90, 0));
            assertEquals(0, layout.clampFirst(-100, 173));
            assertEquals(layout.maxFirst(173), layout.clampFirst(10000, 173));
        }
    }

    @Test void scrollbarDragAndHitTestingUseTheSameBoundedCoordinates() {
        var layout = ConfigScreenLayout.fit(320, 240);
        var thumb = layout.thumb(0, 173);
        assertEquals(layout.viewport.y(), thumb.y());
        assertEquals(0, layout.firstAtThumb(-1000, 173));
        assertEquals(layout.maxFirst(173), layout.firstAtThumb(10000, 173));
        var last = layout.thumb(layout.maxFirst(173), 173);
        assertEquals(layout.viewport.bottom(), last.bottom());
        assertTrue(layout.viewport.contains(layout.viewport.x(), layout.viewport.y()));
        assertFalse(layout.viewport.contains(layout.viewport.right(), layout.viewport.y()));
        assertFalse(layout.viewport.contains(layout.viewport.x(), layout.viewport.bottom()));
        assertFalse(layout.viewport.contains(layout.viewport.x() - 1, layout.viewport.y()));
    }

    private static void inside(ConfigScreenLayout.Bounds bounds, int width, int height) {
        assertTrue(bounds.width() > 0 && bounds.height() >= 0);
        assertTrue(bounds.x() >= 0 && bounds.y() >= 0);
        assertTrue(bounds.right() <= width && bounds.bottom() <= height);
    }
}
