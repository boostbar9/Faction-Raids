package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.util.Mth;

/**
 * Veteran bonuses for camp guards, ramped with the siege instead of applied in
 * full from the first minute.
 *
 * <p>Camp guards used to be created with a flat 50 HP floor, +2 attack damage
 * and knockback resistance the moment the camp was established, which made the
 * very first enemy camp far harder than the wave that followed it. The garrison
 * now starts close to its native Recruits stat line and earns the full veteran
 * profile as the siege reaches its later waves.
 */
public final class GuardStrength {
    /** Health floor for a raw, freshly posted sentry. */
    public static final double BASE_HEALTH = 24.0D;
    /** Extra health a fully veteran sentry reaches. */
    public static final double VETERAN_HEALTH_BONUS = 26.0D;
    /** Extra attack damage a fully veteran sentry reaches. */
    public static final double VETERAN_DAMAGE_BONUS = 2.0D;
    /** Knockback resistance a fully veteran sentry reaches. */
    public static final double VETERAN_KNOCKBACK = 0.35D;
    /** Number of discrete ramp steps; keeps attribute writes rare. */
    public static final int STEPS = 4;

    private GuardStrength() {}

    /**
     * Fraction of the veteran profile earned at the given wave. Wave 0 and the
     * opening wave earn nothing; the horizon wave earns all of it.
     */
    public static double ramp(int wave, int horizonWaves, double scale) {
        int horizon = Math.max(1, horizonWaves);
        double progress = Mth.clamp((wave - 1) / (double) horizon, 0.0D, 1.0D);
        return Math.max(0.0D, progress * scale);
    }

    /** Quantized ramp, so guards only get restatted when they actually advance a step. */
    public static int step(int wave, int horizonWaves) {
        return (int) Math.round(Mth.clamp(ramp(wave, horizonWaves, 1.0D), 0.0D, 1.0D) * STEPS);
    }

    /** Max health for a guard whose native max health is {@code nativeHealth}. */
    public static double health(double nativeHealth, double ramp) {
        return Math.max(nativeHealth, BASE_HEALTH + VETERAN_HEALTH_BONUS * Math.max(0.0D, ramp));
    }

    /** Attack damage for a guard whose native attack damage is {@code nativeDamage}. */
    public static double damage(double nativeDamage, double ramp) {
        return nativeDamage + VETERAN_DAMAGE_BONUS * Math.max(0.0D, ramp);
    }

    /** Knockback resistance earned at this point of the ramp. */
    public static double knockback(double ramp) {
        return VETERAN_KNOCKBACK * Mth.clamp(ramp, 0.0D, 1.0D);
    }
}
