package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureHudLayoutTest {
    @Test void compactAndRoomyCardsStayAboveAimAndAwayFromHotbar() {
        for (int[] viewport : new int[][]{{320,240},{480,360},{720,480},{1440,960}}) {
            for (int lines = 1; lines <= 20; lines++) {
                var box = CaptureHudLayout.bounds(viewport[0], viewport[1], lines);
                assertTrue(box.x() >= 0 && box.x() + box.width() <= viewport[0]);
                assertTrue(box.y() >= 0 && box.y() + box.height() <= viewport[1] / 2 - 18,
                        "The aiming reticle needs a clear center region");
                assertTrue(box.y() + box.height() < viewport[1] - 60);
                assertTrue(box.lines() <= lines);
            }
        }
        assertEquals(4, CaptureHudLayout.bounds(320, 240, 4).lines());
    }
}
