package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreBuildingLayoutTest {
    @Test void ordinary720pViewportGetsSixIllustratedCardsAndSmallViewportsRetainCompactGrid() {
        var ordinary = new CoreBuildingLayout(CoreHireLayout.fit(640, 360));
        assertFalse(ordinary.detailedCatalogue());
        assertEquals(3, ordinary.catalogueColumns());
        assertEquals(2, ordinary.catalogueRows());
        assertTrue(ordinary.planWidth() >= 130);
        assertTrue(ordinary.planHeight() >= 84);
        assertTrue(ordinary.illustratedPerimeter());
        assertEquals(3, ordinary.perimeterTextLines());
        var compact = new CoreBuildingLayout(CoreHireLayout.fit(320, 240));
        assertEquals(2, compact.catalogueColumns());
        assertEquals(3, compact.catalogueRows());
        assertFalse(compact.illustratedPerimeter());
        var wideShort = new CoreBuildingLayout(CoreHireLayout.fit(640, 260));
        assertEquals(2, wideShort.catalogueColumns());
        assertEquals(3, wideShort.catalogueRows());
        assertFalse(wideShort.illustratedPerimeter());
    }

    @Test void allSixPlansAndTheirActionFitWithoutPaginationAtEveryScale() {
        for (int width = 120; width <= 1920; width += 23) {
            for (int height = 90; height <= 1080; height += 19) {
                var frame = CoreHireLayout.fit(width, height);
                var layout = new CoreBuildingLayout(frame);
                String size = width + "x" + height;
                assertTrue(layout.planWidth() >= 130, size);
                assertTrue(layout.planHeight() >= 23, size);
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
                assertEquals(6, layout.catalogueColumns() * layout.catalogueRows());
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
                assertTrue(layout.materialY() + CoreBuildingLayout.ACTION_HEIGHT < layout.perimeterTextY(), size);
                assertTrue(layout.materialX(2, 3) + layout.materialWidth(3) <= layout.x() + layout.width(), size);
                assertTrue(layout.perimeterTextLines() >= 2, size);
                assertTrue(layout.perimeterTextY() + layout.perimeterTextLines() * 10 <= layout.actionY() - 4, size);
                if (layout.illustratedPerimeter()) {
                    assertTrue(layout.perimeterTextY() + layout.perimeterTextLines() * 10 < layout.perimeterExampleY(), size);
                    assertTrue(layout.perimeterExampleHeight() >= 94, size);
                    assertTrue(layout.perimeterExampleY() + layout.perimeterExampleHeight() <= layout.actionY() - 6, size);
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

    @Test void buildingRenamesPresentationWithoutChangingNavigationIdentity() {
        assertEquals("Building", CoreCommandPage.DEFENSES.label());
        assertEquals(4, CoreCommandPage.DEFENSES.ordinal());
        assertEquals(7, CoreCommandPage.values().length);
    }
}
