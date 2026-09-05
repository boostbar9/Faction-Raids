package com.devfarinsky.factionraids.effort;

import com.devfarinsky.factionraids.RaidConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks raider effort contributions per raid so
 * {@code updateCaptureProgress} can reward real work (kills, block breaches)
 * on top of the presence-at-objective baseline.
 *
 * <p>Contributions are stored as remaining "bonus ticks" (each accrues to
 * {@code captureTicks} at 1:1) that decay one raid-tick per one call to
 * {@link #consume}. This gives each event a short-lived, capped effect
 * (default 5 s per kill, 2 s per breach), so raids can't be
 * mass-triggered by farming defenders far from the objective.
 *
 * <p>In-memory only. Restart resets to zero; the presence-based baseline
 * still keeps the raid moving.
 */
public final class RaidEffortTracker {

    private static final Map<String, Bucket> BUCKETS = new HashMap<>();

    private RaidEffortTracker() {}

    /** Record a defender killed by a raider. Adds a capped bonus to the raid. */
    public static void onDefenderKilled(String teamKey) {
        if (teamKey == null || teamKey.isEmpty()) return;
        int reward = RaidConfig.EFFORT_KILL_BONUS_SECONDS.get() * 20;
        int cap = RaidConfig.EFFORT_MAX_BONUS_SECONDS.get() * 20;
        bucket(teamKey).add(reward, cap);
    }

    /** Record a wall/gate block broken by a raider. */
    public static void onBreachTick(String teamKey) {
        if (teamKey == null || teamKey.isEmpty()) return;
        int reward = RaidConfig.EFFORT_BREACH_BONUS_SECONDS.get() * 20;
        int cap = RaidConfig.EFFORT_MAX_BONUS_SECONDS.get() * 20;
        bucket(teamKey).add(reward, cap);
    }

    /**
     * Drain up to {@code perTick} bonus ticks. Returns the amount actually
     * drained so the caller can add it to {@code captureTicks} or
     * {@code breachTicks} for the raid's active phase.
     */
    public static int consume(String teamKey, int perTick) {
        Bucket b = BUCKETS.get(teamKey);
        if (b == null || b.remaining <= 0) return 0;
        int take = Math.min(perTick, b.remaining);
        b.remaining -= take;
        return take;
    }

    /** Wipe a raid's bucket \u2014 call on raid end. */
    public static void forget(String teamKey) {
        BUCKETS.remove(teamKey);
    }

    private static Bucket bucket(String teamKey) {
        return BUCKETS.computeIfAbsent(teamKey, k -> new Bucket());
    }

    private static final class Bucket {
        int remaining;

        void add(int amount, int cap) {
            remaining = Math.min(cap, remaining + amount);
        }
    }
}
