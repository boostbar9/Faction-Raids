package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedInspectionLayoutTest {
    @Test void nativePreviewAndAllActionsFitScaleTwoThreeAndMinimumViewport() {
        for (int[] viewport : List.of(new int[]{640, 360}, new int[]{427, 240}, new int[]{320, 240}, new int[]{854, 480})) {
            var layout = ProtectedInspectionLayout.create(viewport[0], viewport[1], 4);
            assertTrue(layout.content());
            for (var box : List.of(layout.panel(), layout.preview(), layout.materials(), layout.projection(), layout.cancel(), layout.close()))
                inside(box, viewport[0], viewport[1]);
            assertTrue(layout.preview().right() < layout.materials().x());
            assertTrue(layout.preview().bottom() + 9 < layout.projection().y());
            assertTrue(layout.materials().bottom() + 4 * 20 <= layout.projection().y());
            assertTrue(layout.projection().right() < layout.cancel().x());
            assertTrue(layout.cancel().right() < layout.close().x());
        }
    }
    @Test void smallWindowsPageNativeMaterialRowsWithoutCoveringActions() {
        var layout = ProtectedInspectionLayout.create(280, 180, 4);
        assertTrue(layout.content()); assertTrue(layout.pagedMaterials());
        assertTrue(layout.materials().bottom() + layout.materialCapacity() * 20 <= layout.materialsPage().y());
        assertTrue(layout.materialsPage().bottom() < layout.projection().y());
        inside(layout.close(), 280, 180);
    }
    @Test void verySmallWindowsKeepCloseCancelAndProjectionAccessible() {
        for (int[] viewport : List.of(new int[]{200, 120}, new int[]{160, 100}, new int[]{100, 80})) {
            var layout = ProtectedInspectionLayout.create(viewport[0], viewport[1], 4);
            for (var box : List.of(layout.projection(), layout.cancel(), layout.close())) inside(box, viewport[0], viewport[1]);
            assertTrue(layout.projection().right() < layout.cancel().x());
            assertTrue(layout.cancel().right() < layout.close().x());
        }
    }
    private void inside(ProtectedInspectionLayout.Rect box, int width, int height) {
        assertTrue(box.width() > 0 && box.height() > 0);
        assertTrue(box.x() >= 0 && box.y() >= 0);
        assertTrue(box.right() <= width && box.bottom() <= height);
    }
}
