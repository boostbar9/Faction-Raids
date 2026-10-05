package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import static com.devfarinsky.siegeoverhaul.core.CaptureStatus.Participation.*;
import static com.devfarinsky.siegeoverhaul.core.CaptureStatus.Contest.*;
import static org.junit.jupiter.api.Assertions.*;

class CaptureStatusTest {
    @Test void actualCylinderIncludesExactHorizontalAndFeetHeightEdges() {
        assertTrue(CaptureGeometry.inside(6, 2, 0, 6, 2));
        assertTrue(CaptureGeometry.inside(0, -2, -6, 6, 2));
        assertFalse(CaptureGeometry.inside(6.0001, 0, 0, 6, 2));
        assertFalse(CaptureGeometry.inside(0, 2.0001, 0, 6, 2));
        assertFalse(CaptureGeometry.inside(0, -2.0001, 0, 6, 2));
        assertFalse(CaptureGeometry.inside(6, 0, 6, 6, 2));
        assertFalse(CaptureGeometry.inside(Double.NaN, 0, 0, 6, 2));
        assertFalse(CaptureGeometry.inside(0, Double.POSITIVE_INFINITY, 0, 6, 2));
    }
    @Test void geometryReasonsAreDistinctFromSightAndGamemodeExclusions() {
        assertEquals(OUTSIDE, CaptureStatus.participation(7, 0, 0, 6, 2, true, true, true));
        assertEquals(HEIGHT, CaptureStatus.participation(0, 3, 0, 6, 2, true, true, true));
        assertEquals(BLOCKED, CaptureStatus.participation(0, 0, 0, 6, 2, true, true, false));
        assertEquals(INELIGIBLE, CaptureStatus.participation(0, 0, 0, 6, 2, false, true, true));
        assertEquals(UNAVAILABLE, CaptureStatus.participation(0, 0, 0, 6, 2, true, false, true));
        assertEquals(COUNTED, CaptureStatus.participation(6, -2, 0, 6, 2, true, true, true));
    }
    @Test void displayedContestExactlyMatchesExistingProgressDirection() {
        for (int allies = 0; allies < 5; allies++) for (int enemies = 0; enemies < 5; enemies++) {
            int next = CoreControl.advance(100, 2400, allies, enemies);
            var status = CaptureStatus.contest(50, allies, enemies);
            if (next > 100) assertEquals(ADVANCING, status);
            else if (next == 100) assertEquals(TIED, status);
            else assertTrue(status == EMPTY || status == OUTNUMBERED);
        }
        assertEquals(EMPTY, CaptureStatus.contest(100, 0, 0));
        assertEquals(TIED, CaptureStatus.contest(100, 2, 2));
        assertEquals(OUTNUMBERED, CaptureStatus.contest(100, 1, 2));
        assertEquals(COMPLETE, CaptureStatus.contest(100, 2, 1));
    }
    @Test void clientCannotClaimCountedBeforeServerConfirmationOrThroughWalls() {
        assertTrue(CaptureStatus.participationText(COUNTED, OUTSIDE, 5, 6).contains("Awaiting server"));
        assertTrue(CaptureStatus.participationText(BLOCKED, COUNTED, 5, 6).contains("Blocked"));
        assertTrue(CaptureStatus.participationText(COUNTED, COUNTED, 5, 6).contains("You count"));
        assertTrue(CaptureStatus.participationText(OUTSIDE, COUNTED, 7.25, 6).contains("1.3 blocks"));
        assertTrue(CaptureStatus.participationText(OUTSIDE, COUNTED, 6.01, 6).contains("0.1 blocks"));
        assertTrue(CaptureStatus.participationText(OUTSIDE, COUNTED, 6.000001, 6).contains("0.1 blocks"));
        assertTrue(CaptureGeometry.inside(6, 0, 0, 6, 2), "Distance wording must not change the inclusive boundary");
    }
    @Test void countedUnitsUseReadableSingularAndPluralLabels() {
        assertEquals("1 ally / 1 enemy", CaptureStatus.countsText(1, 1));
        assertEquals("0 allies / 2 enemies", CaptureStatus.countsText(0, 2));
    }

}
