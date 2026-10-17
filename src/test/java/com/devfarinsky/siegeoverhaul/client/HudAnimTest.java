package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudAnimTest {
    @Test void easesTowardTheTargetAndSettles() {
        float shown = 0.40F;
        for (int frame = 0; frame < 120; frame++) shown = HudAnim.approach(shown, 0.45F, 16);
        assertEquals(0.45F, shown, 1e-6);
        float half = HudAnim.approach(0.40F, 0.45F, 100);
        assertTrue(half > 0.40F && half < 0.45F, "a single frame moves part of the way");
    }
    @Test void largeJumpsAndBadInputSnap() {
        assertEquals(0.9F, HudAnim.approach(0.1F, 0.9F, 16));
        assertEquals(0.3F, HudAnim.approach(Float.NaN, 0.3F, 16));
        assertEquals(0F, HudAnim.approach(0.3F, Float.NaN, 16));
        assertEquals(0.2F, HudAnim.approach(0.2F, 0.25F, 0));
    }
}
