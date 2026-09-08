package com.devfarinsky.siegeoverhaul.raid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectivePressureTest {
    @Test void effortCannotCaptureAnEmptyOrDefendedPoint() {
        assertEquals(80, ObjectivePressure.advance(100, 1000, 0, 0, 20, 200));
        assertEquals(80, ObjectivePressure.advance(100, 1000, 2, 2, 20, 200));
        assertEquals(80, ObjectivePressure.advance(100, 1000, 2, 3, 20, 200));
    }
    @Test void controllingRaidersStillReceiveBoundedEffortBonus() {
        assertEquals(140, ObjectivePressure.advance(100, 1000, 3, 2, 20, 20));
        assertEquals(1000, ObjectivePressure.advance(990, 1000, 1, 0, 20, 20));
        assertEquals(0, ObjectivePressure.advance(10, 1000, 0, 1, 20, 20));
    }
    @Test void feedbackExplainsRecoveryAndDisabledDecay() {
        assertTrue(ObjectivePressure.status(2,2,100,20).startsWith("Recovering"));
        assertTrue(ObjectivePressure.status(2,2,100,0).startsWith("Held"));
        assertTrue(ObjectivePressure.status(3,2,100,20).startsWith("Capturing"));
        assertTrue(ObjectivePressure.status(0,0,0,20).startsWith("Secure"));
    }
}
