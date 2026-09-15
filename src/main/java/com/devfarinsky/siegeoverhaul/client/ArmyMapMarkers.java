package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of the last two army position updates, so the map can slide
 * each icon from where a unit was to where it is now instead of teleporting it
 * once a second.
 */
public final class ArmyMapMarkers {

    /** Server sends one update per siege pass; icons slide across that window. */
    public static final long INTERVAL_MS = 1000L;
    /** Stop drawing an army that has stopped reporting (siege over, or logout). */
    public static final long EXPIRY_MS = 5000L;

    private static final Map<Integer, Position> PREVIOUS = new HashMap<>();
    private static final Map<Integer, Position> CURRENT = new HashMap<>();
    private static long updatedAt;

    private ArmyMapMarkers() {}

    public record Position(double x, double z, boolean equipment) {}

    /** One icon ready to draw, already interpolated for the current frame. */
    public record Drawn(double x, double z, boolean equipment) {}

    public static synchronized void accept(RaidNetwork.ArmyMarkers packet) {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
        for (var marker : packet.markers()) {
            CURRENT.put(marker.id(), new Position(marker.x() + 0.5, marker.z() + 0.5, marker.equipment()));
        }
        updatedAt = System.currentTimeMillis();
    }

    public static synchronized void clear() {
        PREVIOUS.clear();
        CURRENT.clear();
        updatedAt = 0L;
    }

    /** Interpolation factor between the previous and the latest update. */
    static double progress(long now, long updated, long interval) {
        if (updated <= 0 || interval <= 0) return 1.0;
        double elapsed = now - updated;
        if (elapsed <= 0) return 0.0;
        return Math.min(1.0, elapsed / interval);
    }

    /** Positions to draw this frame, or an empty list when nothing is marching. */
    public static synchronized List<Drawn> current() {
        long now = System.currentTimeMillis();
        if (CURRENT.isEmpty() || updatedAt <= 0 || now - updatedAt > EXPIRY_MS) return List.of();
        double t = progress(now, updatedAt, INTERVAL_MS);
        List<Drawn> drawn = new ArrayList<>(CURRENT.size());
        CURRENT.forEach((id, position) -> {
            Position from = PREVIOUS.getOrDefault(id, position);
            drawn.add(new Drawn(from.x() + (position.x() - from.x()) * t,
                    from.z() + (position.z() - from.z()) * t, position.equipment()));
        });
        return drawn;
    }
}
