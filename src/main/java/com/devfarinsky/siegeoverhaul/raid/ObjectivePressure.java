package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.ModConstants;

/** Presence is required for every increment, including stored raider effort bonuses. */
public final class ObjectivePressure {
    private ObjectivePressure() {}

    public static boolean enemyControls(int attackers, int defenders) {
        return attackers > 0 && attackers > defenders;
    }

    public static int advance(int current, int maximum, int attackers, int defenders, int decay, int bonus) {
        long next = enemyControls(attackers, defenders)
                ? (long) current + ModConstants.TICK_INTERVAL + Math.max(0, bonus)
                : (long) current - Math.max(0, decay);
        return (int) Math.max(0, Math.min(Math.max(0, maximum), next));
    }

    public static String status(int attackers, int defenders, int progress, int decay) {
        String trend = enemyControls(attackers, defenders) ? "Capturing"
                : progress > 0 && decay > 0 ? "Recovering" : progress > 0 ? "Held" : "Secure";
        return trend + " | " + attackers + " attackers / " + defenders + " defenders in ring";
    }
}
