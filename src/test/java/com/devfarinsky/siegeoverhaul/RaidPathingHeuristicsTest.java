package com.devfarinsky.siegeoverhaul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidPathingHeuristicsTest {

    @Test
    void objectivePusherAggroIsReducedOutsideObjective() {
        double base = 40.0 * 40.0;
        double scaled = RaidEvents.applyObjectivePusherAggroScale(base, "breacher", false, 0.65);
        assertTrue(scaled < base);
        assertEquals(base, RaidEvents.applyObjectivePusherAggroScale(base, "breacher", true, 0.65));
        assertEquals(base, RaidEvents.applyObjectivePusherAggroScale(base, "shieldman", false, 0.65));
    }

    @Test
    void advanceSpeedAppliesBurstEscalationAndFinalApproach() {
        double speed = RaidEvents.computeAdvanceSpeed(
                1.0, 8.0 * 8.0, 32.0 * 32.0, 1.30,
                true, true, 16.0 * 16.0, 1.12);
        assertEquals(1.30 * 1.15 * 1.12, speed, 1.0e-6);
    }

    @Test
    void advanceSpeedSkipsFinalApproachBoostWhenNotObjectiveFocused() {
        double speed = RaidEvents.computeAdvanceSpeed(
                1.0, 8.0 * 8.0, 32.0 * 32.0, 1.30,
                false, false, 16.0 * 16.0, 1.12);
        assertEquals(1.30, speed, 1.0e-6);
    }
}

