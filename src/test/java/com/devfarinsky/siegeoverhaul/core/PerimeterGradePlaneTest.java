package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterGradePlaneTest {
    private static PerimeterGradePlane.Surface at(int x, int y) { return new PerimeterGradePlane.Surface(x, 0, y); }
    @Test void preserveModeExactlyRetainsHighestSurfaceAndEightBlockSupportLimit() {
        var plane = PerimeterGradePlane.select(List.of(at(0, 60), at(1, 64)), PerimeterGradePlane.Limits.PRESERVE);
        assertTrue(plane.ready()); assertEquals(64, plane.baseY()); assertEquals(0, plane.cutCells()); assertEquals(4, plane.fillCells());
        assertTrue(PerimeterGradePlane.select(List.of(at(0, 56), at(1, 64)), PerimeterGradePlane.Limits.PRESERVE).ready());
        assertEquals(PerimeterGradePlane.Problem.RELIEF,
                PerimeterGradePlane.select(List.of(at(0, 55), at(1, 64)), PerimeterGradePlane.Limits.PRESERVE).problem());
    }
    @Test void boundedCutsSolveReliefWithoutIncreasingTheExistingFillDepth() {
        var plane = PerimeterGradePlane.select(List.of(at(0, 56), at(1, 68)), PerimeterGradePlane.Limits.REVIEWED_CUT_FILL);
        assertTrue(plane.ready()); assertEquals(64, plane.baseY()); assertEquals(4, plane.cutCells()); assertEquals(8, plane.fillCells());
        assertEquals(4, plane.columns().get(1).cutDepth()); assertEquals(8, plane.columns().get(0).fillDepth());
        assertEquals(PerimeterGradePlane.Problem.RELIEF,
                PerimeterGradePlane.select(List.of(at(0, 55), at(1, 68)), PerimeterGradePlane.Limits.REVIEWED_CUT_FILL).problem());
    }
    @Test void noRemovalIsProposedWhenTheExistingSupportPlaneIsAlreadyFeasible() {
        var plane = PerimeterGradePlane.select(List.of(at(0, 60), at(1, 64)), PerimeterGradePlane.Limits.REVIEWED_CUT_FILL);
        assertEquals(64, plane.baseY()); assertEquals(0, plane.cutCells());
    }
    @Test void aggregateBudgetsRejectWholeProposalWithoutPartialGeometry() {
        var limits = new PerimeterGradePlane.Limits(4, 8, 0, 0, -64, 320, 6);
        var rejected = PerimeterGradePlane.select(List.of(at(0, 60), at(1, 64)), limits);
        assertFalse(rejected.ready()); assertEquals(PerimeterGradePlane.Problem.BUDGET, rejected.problem());
        assertTrue(rejected.columns().isEmpty()); assertEquals(0, rejected.cutCells()); assertEquals(0, rejected.fillCells());
    }
    @Test void orderIsDeterministicInputIsUnchangedAndResultImmutable() {
        var input = new ArrayList<>(List.of(at(2, 64), at(-2, 61), at(0, 63)));
        var original = List.copyOf(input);
        var a = PerimeterGradePlane.select(input, PerimeterGradePlane.Limits.REVIEWED_CUT_FILL);
        java.util.Collections.reverse(input);
        var b = PerimeterGradePlane.select(input, PerimeterGradePlane.Limits.REVIEWED_CUT_FILL);
        assertEquals(a, b); assertEquals(List.of(original.get(2), original.get(1), original.get(0)), input);
        assertThrows(UnsupportedOperationException.class, () -> a.columns().clear());
    }
    @Test void malformedDuplicateOversizeAndWorldBoundaryInputsFailClosed() {
        assertEquals(PerimeterGradePlane.Problem.EMPTY, PerimeterGradePlane.select(List.of(), PerimeterGradePlane.Limits.PRESERVE).problem());
        assertEquals(PerimeterGradePlane.Problem.DUPLICATE_COLUMN,
                PerimeterGradePlane.select(List.of(at(0, 60), at(0, 64)), PerimeterGradePlane.Limits.PRESERVE).problem());
        for (var surface : List.of(at(Integer.MAX_VALUE, 64), at(Integer.MIN_VALUE, 64), at(0, -64), at(0, 320)))
            assertEquals(PerimeterGradePlane.Problem.INVALID_SURFACE,
                    PerimeterGradePlane.select(List.of(surface), PerimeterGradePlane.Limits.PRESERVE).problem());
        assertEquals(PerimeterGradePlane.Problem.COLUMN_LIMIT, PerimeterGradePlane.select(
                java.util.Collections.nCopies(PerimeterGradePlane.MAX_COLUMNS + 1, at(0, 64)), PerimeterGradePlane.Limits.PRESERVE).problem());
    }
    @Test void wallHeadroomIsIncludedInCandidateElevationAndCannotOverflow() {
        assertEquals(PerimeterGradePlane.Problem.RELIEF,
                PerimeterGradePlane.select(List.of(at(0, 318)), PerimeterGradePlane.Limits.PRESERVE).problem());
        var cut = PerimeterGradePlane.select(List.of(at(0, 318)), PerimeterGradePlane.Limits.REVIEWED_CUT_FILL);
        assertTrue(cut.ready()); assertEquals(314, cut.baseY()); assertEquals(4, cut.cutCells());
    }
    @Test void independentComponentsNeverForceEachOtherToACommonElevation() {
        var low = PerimeterGradePlane.select(List.of(at(-32, 60), at(-31, 62)), PerimeterGradePlane.Limits.PRESERVE);
        var high = PerimeterGradePlane.select(List.of(at(32, 100), at(33, 102)), PerimeterGradePlane.Limits.PRESERVE);
        assertEquals(62, low.baseY()); assertEquals(102, high.baseY());
    }
    @Test void invalidBudgetsAndRaisedCapsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PerimeterGradePlane.Limits(5, 8, 1, 1, -64, 320, 6));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterGradePlane.Limits(4, 9, 1, 1, -64, 320, 6));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterGradePlane.Limits(4, 8, 4097, 1, -64, 320, 6));
    }
}
