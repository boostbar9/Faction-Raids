package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuardStrengthTest extends MinecraftTestSupport {
    @Test void openingWaveGuardsKeepTheirNativeStatLine() {
        double ramp = GuardStrength.ramp(1, 5, 1.0);
        assertEquals(0.0, ramp);
        assertEquals(30.0, GuardStrength.health(30.0, ramp));
        assertEquals(GuardStrength.BASE_HEALTH, GuardStrength.health(10.0, ramp));
        assertEquals(4.0, GuardStrength.damage(4.0, ramp));
        assertEquals(0.0, GuardStrength.knockback(ramp));
        assertEquals(0, GuardStrength.step(0, 5));
        assertEquals(0, GuardStrength.step(1, 5));
    }

    @Test void veteranProfileIsReachedByTheConfiguredWaveHorizonAndNeverExceedsIt() {
        double ramp = GuardStrength.ramp(6, 5, 1.0);
        assertEquals(1.0, ramp);
        assertEquals(GuardStrength.ramp(40, 5, 1.0), ramp);
        assertEquals(50.0, GuardStrength.health(20.0, ramp));
        assertEquals(6.0, GuardStrength.damage(4.0, ramp));
        assertEquals(GuardStrength.VETERAN_KNOCKBACK, GuardStrength.knockback(ramp));
        assertEquals(GuardStrength.STEPS, GuardStrength.step(9, 5));
    }

    @Test void progressRampsMonotonicallyAndRespectsTheConfiguredScale() {
        double previous = -1.0;
        for (int wave = 0; wave <= 6; wave++) {
            double ramp = GuardStrength.ramp(wave, 5, 1.0);
            assertTrue(ramp >= previous);
            previous = ramp;
            assertTrue(GuardStrength.health(20.0, ramp) <= 50.0);
        }
        assertEquals(0.0, GuardStrength.ramp(4, 5, 0.0));
        assertEquals(GuardStrength.BASE_HEALTH, GuardStrength.health(20.0, GuardStrength.ramp(4, 5, 0.0)));
        assertTrue(GuardStrength.health(20.0, GuardStrength.ramp(5, 5, 2.0)) > 50.0);
    }
}
