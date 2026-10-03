package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded native entity visibility envelope; never requests or keeps chunks loaded. */
final class ConstructionTracking {
    static final int RANGE_BLOCKS = 256, TRACKING_CHUNKS = RANGE_BLOCKS / 16;
    static final int VIEW_MARGIN = 32;
    private ConstructionTracking() {}

    static double farthestSquared(Vec3 marker, AABB bounds) {
        double x = Math.max(Math.abs(marker.x - bounds.minX), Math.abs(marker.x - bounds.maxX));
        double y = Math.max(Math.abs(marker.y - bounds.minY), Math.abs(marker.y - bounds.maxY));
        double z = Math.max(Math.abs(marker.z - bounds.minZ), Math.abs(marker.z - bounds.maxZ));
        return x * x + y * y + z * z;
    }

    static boolean covers(Vec3 marker, AABB bounds) {
        return farthestSquared(marker, bounds) <= (RANGE_BLOCKS - VIEW_MARGIN) * (RANGE_BLOCKS - VIEW_MARGIN);
    }

    static double renderDistanceSquared(Vec3 marker, AABB bounds) {
        double radius = Math.min(RANGE_BLOCKS, Math.max(64, Math.sqrt(farthestSquared(marker, bounds)) + VIEW_MARGIN));
        return radius * radius;
    }
}
