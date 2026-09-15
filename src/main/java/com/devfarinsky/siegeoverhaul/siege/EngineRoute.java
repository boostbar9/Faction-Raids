package com.devfarinsky.siegeoverhaul.siege;

/**
 * Stall bookkeeping for siege engines on the march.
 *
 * <p>Catapults and ballistas are far wider than a soldier, so the native
 * "walk to this position" order regularly wedges them against a tree, a ledge
 * or a house corner. Infantry already has stuck detection; engines had none,
 * which is why a catapult could sit in a ditch for an entire siege while the
 * rest of the army carried on.
 *
 * <p>Everything here is pure arithmetic so it can be tested without a level:
 * the caller records the distance to the objective at each advance pass and
 * asks for the next stall count, the lateral detour and the step length.
 */
public final class EngineRoute {

    /** Persistent keys are namespaced to avoid collisions with other mods. */
    public static final String LAST_DISTANCE = "SiegeMarchDistance";
    public static final String STALLS = "SiegeMarchStalls";

    /** Metres of progress a pass must make to count as moving. */
    public static final double PROGRESS = 3.0;
    /** Widest lateral detour used when routing around an obstacle. */
    public static final int MAX_DETOUR = 12;
    /** Longest forward step for an engine that is moving freely. */
    public static final int MAX_STEP = 24;
    /** Shortest forward step used once an engine is clearly stuck. */
    public static final int MIN_STEP = 8;

    private EngineRoute() {}

    /**
     * Stall count after a pass. Any real progress toward the objective clears
     * it; standing still (or drifting backwards) increases it.
     */
    public static int stalls(int previous, double before, double now) {
        if (before <= 0 || Double.isNaN(before)) return 0;
        if (before - now >= PROGRESS) return 0;
        return Math.min(previous + 1, 1_000);
    }

    /**
     * Sideways offset, in blocks, for the next waypoint. Alternates left and
     * right and widens as the engine keeps failing, so it sweeps around
     * whatever is in the way instead of pushing into it forever.
     */
    public static int detour(int stalls) {
        if (stalls <= 0) return 0;
        int magnitude = Math.min(MAX_DETOUR, 4 * ((stalls + 1) / 2));
        return stalls % 2 == 1 ? magnitude : -magnitude;
    }

    /** Forward step length: shorter waypoints once an engine is struggling. */
    public static int step(int stalls) {
        return stalls <= 0 ? MAX_STEP : Math.max(MIN_STEP, MAX_STEP - 4 * stalls);
    }

    /** True when detours have failed often enough to justify a short forward lift. */
    public static boolean shouldLift(int stalls, int threshold) {
        return threshold > 0 && stalls >= threshold && stalls % threshold == 0;
    }
}
