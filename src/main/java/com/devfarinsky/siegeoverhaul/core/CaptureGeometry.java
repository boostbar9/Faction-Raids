package com.devfarinsky.siegeoverhaul.core;

/** Shared, allocation-free cylinder predicate. Offsets are from the core block center to entity feet. */
public final class CaptureGeometry {
    private CaptureGeometry() {}
    public static boolean inside(double dx, double dy, double dz, int radius, int vertical) {
        return radius > 0 && vertical >= 0 && Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && dx * dx + dz * dz <= (double)radius * radius && Math.abs(dy) <= vertical;
    }
}
