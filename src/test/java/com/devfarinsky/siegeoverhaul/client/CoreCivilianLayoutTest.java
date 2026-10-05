package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHireLayout;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CoreCivilianLayoutTest {
    @Test void subscriptionsAreReadOnlyAndOnlyFollowVisibilityTransitions() {
        var report = new CivilianReportSubscription(); var requests = new ArrayList<Long>(); var states = new ArrayList<Boolean>();
        java.util.function.BiConsumer<Long, Boolean> action = (request, visible) -> { requests.add(request); states.add(visible); };
        report.update(false, action); report.update(true, action); report.update(true, action);
        report.update(false, action); report.update(false, action); report.update(true, action);
        assertEquals(List.of(true, false, true), states);
        assertTrue(requests.get(0) < requests.get(1) && requests.get(1) < requests.get(2));
        var reopened = new CivilianReportSubscription(); reopened.update(true, action);
        assertTrue(requests.get(3) > requests.get(2), "Reused menu ID must still have a new epoch");
    }
    @Test void residentRowsNavigationAndRecruitingNeverOverlap() {
        for (int width = 120; width <= 1920; width += 31) for (int height = 90; height <= 1080; height += 29) {
            var frame = CoreHireLayout.fit(width, height); var c = new CoreCivilianLayout(frame);
            assertTrue(c.bodyHeight() >= 56);
            for (int i = 0; i < c.rows(); i++) assertTrue(c.rowY(i) + c.rowHeight() <= c.navigationY() - 4);
            assertTrue(c.navigationY() + 18 <= c.recruitY() - 4);
            assertEquals(frame.contentBottom(), c.recruitY() + 20);
            assertTrue(c.pages(64) * c.rows() >= 64);
            if (c.split()) assertTrue(c.detailX() >= c.x() + c.listWidth() + 6);
        }
    }
}
