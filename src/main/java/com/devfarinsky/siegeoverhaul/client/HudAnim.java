package com.devfarinsky.siegeoverhaul.client;

/** Frame-rate independent easing for HUD values that only arrive once a second. */
public final class HudAnim {
    /** Seconds for the displayed value to close about 63% of the gap. */
    static final double TIME_CONSTANT = 0.15;
    private HudAnim() {}

    /**
     * Move {@code shown} toward {@code target} after {@code elapsedMillis}. Large jumps
     * (a different core, a reset) snap instead of sweeping across the whole bar.
     */
    public static float approach(float shown, float target, long elapsedMillis) {
        if (Float.isNaN(target)) return 0F;
        if (Float.isNaN(shown) || Math.abs(target - shown) > 0.5F) return target;
        if (elapsedMillis <= 0) return shown;
        double k = 1 - Math.exp(-(elapsedMillis / 1000.0) / TIME_CONSTANT);
        float next = (float) (shown + (target - shown) * k);
        return Math.abs(target - next) < 0.001F ? target : next;
    }
}
