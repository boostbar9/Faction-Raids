package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedPreviewFramingTest {
    @Test void smallPlanAndNativeAnchorFitAndCenterAtBothRealGuiScales() {
        var blocks = new ProtectedPreviewFraming.Bounds(0, 0, 0, 1, 3, 5);
        for (int[] viewport : List.of(new int[]{640,360}, new int[]{427,240}, new int[]{320,240})) {
            var box = ProtectedInspectionLayout.create(viewport[0], viewport[1], 2).preview();
            var frame = ProtectedPreviewFraming.initial(box.width(), box.height(), 4, 5, blocks);
            assertTrue(frame.fits());
            assertTrue(frame.projectedWidth() <= box.width() - 16);
            assertTrue(frame.projectedHeight() <= box.height() - 16);
            assertProjectedBoxInside(frame, blocks, 4, 5, box.width(), box.height());
            assertProjectedBoxInside(frame, new ProtectedPreviewFraming.Bounds(2.99,-.01,-.01,4.01,2.01,1.01),
                    4,5,box.width(),box.height());
            assertTrue(frame.dragX() < 0, "Native pivot translation needs leftward centering");
            assertTrue(frame.dragY() > 0, "Native inverted Y needs a downward pan");
        }
    }
    @Test void approvedWallAndTowerEnvelopesFitTheCompactNativeWidget() {
        var box = ProtectedInspectionLayout.create(427,240,2).preview();
        for (int[] dimensions : List.of(new int[]{5,5,5}, new int[]{5,9,6}, new int[]{9,9,6})) {
            var blocks = new ProtectedPreviewFraming.Bounds(0,0,0,dimensions[0],dimensions[2],dimensions[1]);
            var frame = ProtectedPreviewFraming.initial(box.width(),box.height(),dimensions[0],dimensions[1],blocks);
            assertTrue(frame.fits());
            assertProjectedBoxInside(frame,blocks,dimensions[0],dimensions[1],box.width(),box.height());
        }
    }
    @Test void hugePlansRespectNativeZoomFloorAndReportPanRatherThanPretendToFit() {
        var frame = ProtectedPreviewFraming.initial(240,120,128,128,
                new ProtectedPreviewFraming.Bounds(0,0,0,128,6,128));
        assertEquals(3, frame.zoom()); assertFalse(frame.fits());
        assertTrue(Double.isFinite(frame.dragX()) && Double.isFinite(frame.dragY()));
    }
    private void assertProjectedBoxInside(ProtectedPreviewFraming.Frame frame, ProtectedPreviewFraming.Bounds bounds,
                                         int areaWidth, int areaDepth, int width, int height) {
        for (double x : new double[]{bounds.minX(),bounds.maxX()})
            for (double y : new double[]{bounds.minY(),bounds.maxY()})
                for (double z : new double[]{bounds.minZ(),bounds.maxZ()}) {
                    double screenX = frame.zoom() * (areaWidth - x) + frame.dragX();
                    double projectedY = Math.cos(Math.toRadians(25))*y + Math.sin(Math.toRadians(25))*(z-areaDepth/2.0);
                    double screenY = -frame.zoom()*projectedY + frame.dragY();
                    assertTrue(Math.abs(screenX) <= width/2.0 - 7.99);
                    assertTrue(Math.abs(screenY) <= height/2.0 - 7.99);
                }
    }
}
