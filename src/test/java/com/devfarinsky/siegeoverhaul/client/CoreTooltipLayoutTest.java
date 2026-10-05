package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreTooltipLayoutTest {
    @Test void tallNativeTooltipsStayInsideAllViewportEdgesWithoutRemovingLines() {
        for (int width : new int[]{120, 240, 320, 480, 720, 1440})
            for (int height : new int[]{90, 180, 240, 360, 480, 960})
                for (int tooltipHeight : new int[]{10, 162, 242, 502})
                    for (int x : new int[]{0, width / 2, width}) for (int y : new int[]{0, height / 2, height}) {
                        float scale = CoreTooltipLayout.scale(width, height, 260, tooltipHeight);
                        var box = CoreTooltipLayout.fit(width, height, 260, tooltipHeight, x, y, scale);
                        assertTrue(box.x() >= 5.9f && box.y() >= 5.9f);
                        assertTrue(box.x() + box.width() <= width - 5.9f);
                        assertTrue(box.y() + box.height() <= height - 5.9f);
                        assertTrue(scale > 0 && scale <= 1);
                    }
    }
    @Test void roomyTooltipsUseNativeScaleAndRemainBesideTheirActualAnchor() {
        float scale = CoreTooltipLayout.scale(1440, 960, 250, 162);
        assertEquals(1, scale);
        var box = CoreTooltipLayout.fit(1440, 960, 250, 162, 720, 400, scale);
        assertEquals(732, box.x()); assertEquals(388, box.y());
    }
}
