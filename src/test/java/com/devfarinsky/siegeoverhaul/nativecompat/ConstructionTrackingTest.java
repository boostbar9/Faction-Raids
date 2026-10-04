package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionTrackingTest {
    @Test void oppositeEndOfLongThinNativePlanExceedsOldMarkerDistanceButIsCovered() {
        Vec3 marker = new Vec3(.5, 65, .5);
        AABB plan = new AABB(0, 64, 0, 128, 71, 5);
        assertTrue(ConstructionTracking.covers(marker, plan));
        double oppositeDistance = marker.distanceToSqr(new Vec3(145, 67, 2));
        assertTrue(oppositeDistance > 80 * 80);
        assertTrue(oppositeDistance < ConstructionTracking.renderDistanceSquared(marker, plan));
        assertEquals(16, ConstructionTracking.TRACKING_CHUNKS);
    }
    @Test void maximumEnvelopeIsFiniteAndRejectsAPlanThatCannotFitWithViewingMargin() {
        assertFalse(ConstructionTracking.covers(Vec3.ZERO, new AABB(0, 0, 0, 512, 10, 512)));
        assertEquals(256 * 256, ConstructionTracking.renderDistanceSquared(Vec3.ZERO, new AABB(0, 0, 0, 512, 10, 512)));
        assertTrue(ConstructionTracking.renderDistanceSquared(Vec3.ZERO, new AABB(0, 0, 0, 5, 5, 5)) >= 64 * 64);
    }
}
