package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreBuildingLayoutTest {
    @Test void ordinary720pGetsSixModelsAndSmallViewportsPageBeforeModelsBecomeIcons() {
        var ordinary = new CoreBuildingLayout(CoreHireLayout.fit(640, 360));
        assertFalse(ordinary.detailedCatalogue());
        assertEquals(3, ordinary.catalogueColumns());
        assertEquals(2, ordinary.catalogueRows());
        assertTrue(ordinary.planWidth() >= 130);
        assertTrue(ordinary.planHeight() >= 84);
        assertTrue(ordinary.illustratedPerimeter());
        assertTrue(ordinary.splitPerimeter());
        assertTrue(ordinary.stackedMaterials());
        assertEquals(2, ordinary.perimeterTextLines());
        var compact = new CoreBuildingLayout(CoreHireLayout.fit(320, 240));
        assertEquals(2, compact.catalogueColumns());
        assertEquals(1, compact.catalogueRows());
        assertEquals(3, compact.cataloguePages());
        assertEquals(2, compact.plansPerPage());
        assertTrue(compact.planHeight() >= 72);
        assertFalse(compact.illustratedPerimeter());
        var wideShort = new CoreBuildingLayout(CoreHireLayout.fit(640, 260));
        assertEquals(2, wideShort.catalogueColumns());
        assertEquals(1, wideShort.catalogueRows());
        assertTrue(wideShort.pagedCatalogue());
        assertFalse(wideShort.illustratedPerimeter());
    }

    @Test void allSixPlansRemainReachableWithReadableModelsAndActionsAtEveryScale() {
        for (int width = 120; width <= 1920; width += 23) {
            for (int height = 90; height <= 1080; height += 19) {
                var frame = CoreHireLayout.fit(width, height);
                var layout = new CoreBuildingLayout(frame);
                String size = width + "x" + height;
                assertTrue(layout.planWidth() >= 130, size);
                assertTrue(layout.planHeight() >= 72, size);
                for (int plan = 0; plan < 6; plan++) {
                    assertTrue(layout.planX(plan) >= layout.x(), size);
                    assertTrue(layout.planX(plan) + layout.planWidth()
                            <= layout.x() + layout.catalogueWidth(), size);
                    assertTrue(layout.planY(plan) >= layout.bodyY(), size);
                    assertTrue(layout.planY(plan) + layout.planHeight()
                            <= (layout.detailedCatalogue() ? layout.bottom() : layout.actionY() - 6), size);
                }
                assertTrue(layout.planActionX() >= layout.x(), size);
                assertTrue(layout.planActionX() + layout.planActionWidth() <= layout.x() + layout.width(), size);
                assertTrue(layout.actionY() + CoreBuildingLayout.ACTION_HEIGHT <= frame.contentBottom(), size);
                assertTrue(layout.plansPerPage() * layout.cataloguePages() >= 6);
                assertTrue((layout.cataloguePages() - 1) * layout.plansPerPage() < 6);
                if (layout.pagedCatalogue()) {
                    assertTrue(layout.x() + 28 < layout.planActionX());
                    assertTrue(layout.planActionX() + layout.planActionWidth() < layout.x() + layout.width() - 28);
                }
                for (int first = 0; first < 6; first += layout.plansPerPage()) {
                    for (int a = first; a < Math.min(6, first + layout.plansPerPage()); a++) {
                        for (int b = a + 1; b < Math.min(6, first + layout.plansPerPage()); b++) {
                            assertTrue(layout.planX(a) + layout.planWidth() <= layout.planX(b)
                                    || layout.planX(b) + layout.planWidth() <= layout.planX(a)
                                    || layout.planY(a) + layout.planHeight() <= layout.planY(b)
                                    || layout.planY(b) + layout.planHeight() <= layout.planY(a), size);
                        }
                    }
                }
                if (layout.detailedCatalogue()) {
                    assertTrue(layout.x() + layout.catalogueWidth() + 8 <= layout.detailX(), size);
                }
            }
        }
    }

    @Test void sectionMaterialAndReportBandsNeverOverlapControlsOrFooter() {
        for (int width = 120; width <= 1920; width += 23) {
            for (int height = 90; height <= 1080; height += 19) {
                var frame = CoreHireLayout.fit(width, height);
                var layout = new CoreBuildingLayout(frame);
                String size = width + "x" + height;
                assertTrue(layout.sectionWidth() >= 92, size);
                assertTrue(layout.sectionX(2) + layout.sectionWidth() <= layout.x() + layout.width(), size);
                assertTrue(layout.sectionY() + CoreBuildingLayout.SECTION_HEIGHT < layout.bodyY(), size);
                assertTrue(layout.materialWidth(3) >= 84, "Complete material labels: " + size);
                for (int material = 0; material < 3; material++) {
                    assertTrue(layout.materialX(material, 3) >= layout.x(), size);
                    assertTrue(layout.materialX(material, 3) + layout.materialWidth(3)
                            <= layout.x() + layout.width(), size);
                    assertTrue(layout.materialY(material) + CoreBuildingLayout.ACTION_HEIGHT
                            < layout.perimeterActionY(), size);
                }
                assertTrue(layout.perimeterTextLines() >= 2, size);
                assertTrue(layout.perimeterTextY() + layout.perimeterTextLines() * 10
                        <= (layout.splitPerimeter() ? layout.bottom() : layout.actionY() - 4), size);
                if (layout.illustratedPerimeter()) {
                    assertTrue(layout.perimeterExampleHeight() >= 94, size);
                    assertTrue(layout.perimeterExampleY() + layout.perimeterExampleHeight()
                            <= (layout.splitPerimeter() ? layout.bottom() - 24 : layout.actionY() - 6), size);
                }
                if (layout.splitPerimeter()) {
                    assertEquals(layout.perimeterDetailX(), layout.x() + layout.perimeterPreviewWidth() + 10, size);
                    assertTrue(layout.perimeterActionX() >= layout.perimeterDetailX() + 10, size);
                    assertTrue(layout.perimeterActionX() + layout.perimeterActionWidth()
                            <= layout.x() + layout.width() - 10, size);
                    assertTrue(layout.perimeterActionY() + CoreBuildingLayout.ACTION_HEIGHT
                            < layout.perimeterTextY(), size);
                } else {
                    assertTrue(layout.materialY() + CoreBuildingLayout.ACTION_HEIGHT < layout.perimeterTextY(), size);
                }
                assertTrue(layout.reportRows() >= 1, size);
                assertTrue(layout.reportY() + layout.reportRows() * layout.reportRowHeight()
                        <= layout.actionY() - 4, size);
                assertTrue(layout.actionY() + CoreBuildingLayout.ACTION_HEIGHT < frame.footerY(), size);
            }
        }
    }

    @Test void nearbyReportPaginationIsBoundedAndIncludesEveryReturnedJob() {
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {1920, 1080}}) {
            var layout = new CoreBuildingLayout(CoreHireLayout.fit(size[0], size[1]));
            assertEquals(1, layout.reportPages(0));
            for (int count = 1; count <= 12; count++) {
                int pages = layout.reportPages(count);
                assertTrue(pages * layout.reportRows() >= count);
                assertTrue((pages - 1) * layout.reportRows() < count);
            }
        }
    }

    @Test void wideReportsSeparateTruthfulStatusFromProgressWithoutClaimingGlobalQueueData() {
        for (int[] size : new int[][]{{640, 360}, {854, 480}, {1920, 1080}}) {
            var layout = new CoreBuildingLayout(CoreHireLayout.fit(size[0], size[1]));
            assertTrue(layout.splitReport());
            assertEquals(108, layout.reportRowHeight());
            assertTrue(layout.reportMainWidth() >= 338);
            assertTrue(layout.reportDetailWidth() >= 232);
            assertEquals(layout.reportDetailX(), layout.x() + layout.reportMainWidth() + 10);
            assertEquals(layout.x() + layout.width(), layout.reportDetailX() + layout.reportDetailWidth());
        }
        var compact = new CoreBuildingLayout(CoreHireLayout.fit(320, 240));
        assertFalse(compact.splitReport());
        assertEquals(compact.width(), compact.reportMainWidth());
        assertEquals(64, compact.reportRowHeight());
    }

    @Test void tinyWindowsAndGuiScaleFallbackKeepEveryBuildingHitboxOnTheLogicalCanvas() {
        for (int width = 1; width <= 320; width += 13) {
            for (int height = 1; height <= 240; height += 11) {
                var frame = CoreHireLayout.fit(width, height);
                var layout = new CoreBuildingLayout(frame);
                assertTrue(frame.scale() > 0 && frame.scale() <= 1);
                assertTrue((frame.x() + frame.width() + 3) * frame.scale() <= width + 1);
                assertTrue((frame.y() + frame.height() + 4) * frame.scale() <= height + 1);
                assertTrue(layout.planHeight() >= 72);
                assertTrue(layout.materialWidth(3) >= 84);
                double buttonX = (layout.perimeterActionX() + layout.perimeterActionWidth() / 2.0) * frame.scale();
                double buttonY = (layout.perimeterActionY() + CoreBuildingLayout.ACTION_HEIGHT / 2.0) * frame.scale();
                assertTrue(frame.logicalX(buttonX) >= layout.perimeterActionX());
                assertTrue(frame.logicalX(buttonX) <= layout.perimeterActionX() + layout.perimeterActionWidth());
                assertTrue(frame.logicalY(buttonY) >= layout.perimeterActionY());
                assertTrue(frame.logicalY(buttonY) <= layout.perimeterActionY() + CoreBuildingLayout.ACTION_HEIGHT);
                assertTrue(layout.reportY() + layout.reportRows() * layout.reportRowHeight() <= layout.actionY() - 4);
            }
        }
    }

    @Test void buildingRenamesPresentationWithoutChangingNavigationIdentity() {
        assertEquals("Building", CoreCommandPage.DEFENSES.label());
        assertEquals(4, CoreCommandPage.DEFENSES.ordinal());
        assertEquals(7, CoreCommandPage.values().length);
    }
    @Test void wholeProjectSummaryAndCancelControlsFitWithoutCoveringNavigationAtEveryScale() {
        for (int width = 1; width <= 1920; width += 23) {
            for (int height = 1; height <= 1080; height += 19) {
                var layout = new CoreBuildingLayout(CoreHireLayout.fit(width, height));
                String size = width + "x" + height;
                assertTrue(layout.reportProjectHeight() >= 64, size);
                assertTrue(layout.reportCancelWidth() >= 144, "Complete cancel label: " + size);
                assertTrue(layout.reportCancelX() >= layout.x(), size);
                assertTrue(layout.reportCancelX() + layout.reportCancelWidth() <= layout.x() + layout.width(), size);
                assertTrue(layout.reportCancelY() + CoreBuildingLayout.ACTION_HEIGHT <= layout.bottom(), size);
                if (layout.splitReport()) {
                    assertTrue(layout.reportProjectHeight() >= 180, size);
                    assertTrue(layout.reportCancelX() >= layout.reportDetailX() + 10, size);
                    assertTrue(layout.reportCancelY() + CoreBuildingLayout.ACTION_HEIGHT < layout.actionY(), size);
                } else {
                    assertEquals(layout.actionY(), layout.reportCancelY(), size);
                    assertTrue(layout.x() + layout.reportCancelNavigationWidth() + CoreBuildingLayout.GAP <= layout.reportCancelX(), size);
                    assertTrue(layout.reportCancelX() + layout.reportCancelWidth() + CoreBuildingLayout.GAP
                            <= layout.x() + layout.width() - layout.reportCancelNavigationWidth(), size);
                }
                // Five compact text baselines end before the card border, even at minimum scale.
                assertTrue(49 + 9 < layout.reportProjectHeight(), size);
            }
        }
    }

}
