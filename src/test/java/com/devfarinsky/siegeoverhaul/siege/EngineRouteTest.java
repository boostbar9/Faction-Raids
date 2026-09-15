package com.devfarinsky.siegeoverhaul.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineRouteTest {

    @Test
    void progressClearsStalls() {
        assertEquals(0, EngineRoute.stalls(5, 100.0, 90.0));
        assertEquals(0, EngineRoute.stalls(5, 100.0, 97.0));
    }

    @Test
    void standingStillAccumulatesStalls() {
        assertEquals(1, EngineRoute.stalls(0, 100.0, 99.0));
        assertEquals(2, EngineRoute.stalls(1, 100.0, 100.0));
        assertEquals(3, EngineRoute.stalls(2, 100.0, 104.0));
    }

    @Test
    void firstPassHasNoBaseline() {
        assertEquals(0, EngineRoute.stalls(3, 0.0, 120.0));
    }

    @Test
    void detourAlternatesAndWidens() {
        assertEquals(0, EngineRoute.detour(0));
        assertEquals(4, EngineRoute.detour(1));
        assertEquals(-4, EngineRoute.detour(2));
        assertEquals(8, EngineRoute.detour(3));
        assertEquals(EngineRoute.MAX_DETOUR, EngineRoute.detour(9));
        assertEquals(-EngineRoute.MAX_DETOUR, EngineRoute.detour(10));
    }

    @Test
    void stepShortensButNeverDisappears() {
        assertEquals(EngineRoute.MAX_STEP, EngineRoute.step(0));
        assertEquals(20, EngineRoute.step(1));
        assertEquals(EngineRoute.MIN_STEP, EngineRoute.step(20));
    }

    @Test
    void liftOnlyAfterRepeatedFailures() {
        assertFalse(EngineRoute.shouldLift(3, 4));
        assertTrue(EngineRoute.shouldLift(4, 4));
        assertFalse(EngineRoute.shouldLift(5, 4));
        assertTrue(EngineRoute.shouldLift(8, 4));
        assertFalse(EngineRoute.shouldLift(4, 0));
    }
}
