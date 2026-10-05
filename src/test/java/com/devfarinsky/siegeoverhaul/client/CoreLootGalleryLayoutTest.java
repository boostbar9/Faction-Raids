package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHireLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreLootGalleryLayoutTest {
    @Test void everyNativeScaleAndTinyWindowKeepsModelsNamesAndPagingInSeparateBands() {
        for (int width = 120; width <= 1920; width += 31) for (int height = 90; height <= 1080; height += 29) {
            var frame = CoreHireLayout.fit(width, height);
            var gallery = new CoreLootGalleryLayout(frame);
            assertTrue(gallery.pageSize() >= 2 && gallery.pageSize() <= 8);
            assertTrue(gallery.cardHeight() >= 60);
            assertTrue(gallery.cardWidth() >= 120);
            for (int i = 0; i < gallery.pageSize(); i++) {
                assertTrue(gallery.cardX(i) >= gallery.x());
                assertTrue(gallery.cardX(i) + gallery.cardWidth() <= gallery.x() + gallery.width());
                assertTrue(gallery.cardY(i) >= gallery.tierY() + 18);
                assertTrue(gallery.cardY(i) + gallery.cardHeight() <= gallery.actionY() - 4);
            }
            assertEquals(frame.contentBottom(), gallery.actionY() + 18);
            assertTrue(gallery.pages(11) * gallery.pageSize() >= 11);
        }
    }
}
