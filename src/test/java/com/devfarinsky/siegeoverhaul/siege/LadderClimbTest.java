package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderClimbTest {

    /** Ladder column centre; the wall it is fixed to lies to the east. */
    private static final Vec3 COLUMN = new Vec3(10.5, 64.0, 20.5);
    private static final Direction INTO_WALL = Direction.EAST;

    @Test
    void axesPointIntoTheWallAndAlongIt() {
        assertEquals(1.0, LadderClimb.into(INTO_WALL).x, 1e-9);
        assertEquals(0.0, LadderClimb.into(INTO_WALL).z, 1e-9);
        assertEquals(0.0, LadderClimb.along(INTO_WALL).x, 1e-9);
        assertEquals(1.0, LadderClimb.along(INTO_WALL).z, 1e-9);
    }

    @Test
    void driftIsSignedAlongTheWallFace() {
        assertEquals(0.0, LadderClimb.drift(COLUMN, COLUMN, INTO_WALL), 1e-9);
        assertEquals(0.4, LadderClimb.drift(COLUMN.add(0, 3, 0.4), COLUMN, INTO_WALL), 1e-9);
        assertEquals(-0.4, LadderClimb.drift(COLUMN.add(0, 3, -0.4), COLUMN, INTO_WALL), 1e-9);
        // Depth toward the wall must not be mistaken for sideways drift.
        assertEquals(0.0, LadderClimb.drift(COLUMN.add(0.3, 0, 0), COLUMN, INTO_WALL), 1e-9);
        assertEquals(0.3, LadderClimb.depth(COLUMN.add(0.3, 0, 0), COLUMN, INTO_WALL), 1e-9);
    }

    @Test
    void climbingPressesIntoTheLadderAndRises() {
        Vec3 velocity = LadderClimb.climb(COLUMN, COLUMN, INTO_WALL);
        assertEquals(LadderClimb.PRESS, velocity.x, 1e-9);
        assertEquals(LadderClimb.RISE, velocity.y, 1e-9);
        assertEquals(0.0, velocity.z, 1e-9);
    }

    @Test
    void driftIsCorrectedBackTowardTheColumn() {
        Vec3 drifted = LadderClimb.climb(COLUMN.add(0, 2, 0.35), COLUMN, INTO_WALL);
        assertTrue(drifted.z < 0, "a climber that slid one way must be pushed back the other");
        Vec3 other = LadderClimb.climb(COLUMN.add(0, 2, -0.35), COLUMN, INTO_WALL);
        assertTrue(other.z > 0);
        // Corrections stay inside the vanilla climbing motion clamp.
        Vec3 far = LadderClimb.climb(COLUMN.add(0, 2, 5.0), COLUMN, INTO_WALL);
        assertEquals(-LadderClimb.LATERAL, far.z, 1e-9);
        assertTrue(Math.abs(far.z) <= 0.15);
    }

    @Test
    void crestingLiftsHarderThanOrdinaryClimbing() {
        Vec3 climb = LadderClimb.climb(COLUMN, COLUMN, INTO_WALL);
        Vec3 crest = LadderClimb.crest(COLUMN, COLUMN, INTO_WALL);
        assertTrue(crest.y > climb.y);
        assertTrue(crest.x > climb.x);
    }

    @Test
    void crestBandStartsJustBelowTheExit() {
        assertFalse(LadderClimb.cresting(70.0, 72.0));
        assertTrue(LadderClimb.cresting(71.0, 72.0));
        assertTrue(LadderClimb.cresting(72.5, 72.0));
    }

    @Test
    void regripRecoversTheColumnWithoutClimbingFurther() {
        Vec3 falling = new Vec3(0, -0.4, 0);
        Vec3 recover = LadderClimb.regrip(COLUMN.add(0, 3, 0.5), COLUMN, INTO_WALL, falling);
        assertTrue(recover.z < 0, "recovery must pull back toward the column");
        assertTrue(recover.x > 0, "recovery must press back into the rungs");
        assertTrue(recover.y > falling.y, "recovery must arrest the fall");
        assertTrue(recover.y <= 0.0, "recovery must not lift a mob that is off the rungs");
    }

    @Test
    void columnRangeCoversTheLadderBlockButNotTheNextOne() {
        assertTrue(LadderClimb.onColumn(COLUMN.add(0, 4, 0), COLUMN, LadderClimb.GRIP_RANGE_SQ));
        assertTrue(LadderClimb.onColumn(COLUMN.add(0.9, 4, 0), COLUMN, LadderClimb.GRIP_RANGE_SQ));
        assertFalse(LadderClimb.onColumn(COLUMN.add(3.0, 4, 0), COLUMN, LadderClimb.GRIP_RANGE_SQ));
        assertTrue(LadderClimb.onColumn(COLUMN.add(1.4, 4, 0), COLUMN, LadderClimb.REGRIP_RANGE_SQ));
    }

    @Test
    void approachStandsInFrontOfTheLadderLinedUpWithIt() {
        Vec3 spot = LadderClimb.approach(COLUMN, INTO_WALL, 1.0);
        assertEquals(COLUMN.x - 1.0, spot.x, 1e-9);
        assertEquals(COLUMN.z, spot.z, 1e-9);
        assertEquals(0.0, LadderClimb.drift(spot, COLUMN, INTO_WALL), 1e-9);
    }
}
