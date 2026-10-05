package com.devfarinsky.siegeoverhaul.core;

/** Read-only explanations of the existing cylinder and fixed-time majority contest. */
public final class CaptureStatus {
    private CaptureStatus() {}

    public enum Participation { COUNTED, OUTSIDE, HEIGHT, BLOCKED, INELIGIBLE, CREATIVE, SPECTATOR, DEAD, UNAVAILABLE }
    public enum Contest { ADVANCING, TIED, OUTNUMBERED, EMPTY, COMPLETE }

    public static Participation participation(double dx, double dy, double dz, int radius, int vertical,
                                               boolean eligible, boolean loaded, boolean clearSight) {
        if (!eligible) return Participation.INELIGIBLE;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz) || !loaded)
            return Participation.UNAVAILABLE;
        if (dx * dx + dz * dz > (double) radius * radius) return Participation.OUTSIDE;
        if (!CaptureGeometry.inside(dx, dy, dz, radius, vertical)) return Participation.HEIGHT;
        return clearSight ? Participation.COUNTED : Participation.BLOCKED;
    }

    public static Participation eligibility(boolean alive, boolean creative, boolean spectator) {
        if (!alive) return Participation.DEAD;
        if (spectator) return Participation.SPECTATOR;
        if (creative) return Participation.CREATIVE;
        return Participation.COUNTED;
    }

    public static Contest contest(int percent, int allies, int enemies) {
        if (allies == 0) return Contest.EMPTY;
        if (allies < enemies) return Contest.OUTNUMBERED;
        if (allies == enemies) return Contest.TIED;
        return percent >= 100 ? Contest.COMPLETE : Contest.ADVANCING;
    }

    public static String contestText(int percent, int allies, int enemies) {
        return switch (contest(percent, allies, enemies)) {
            case ADVANCING -> "Capturing";
            case TIED -> "Paused: tied numbers";
            case OUTNUMBERED -> "Outnumbered: progress falling";
            case EMPTY -> percent > 0 ? "Unheld: progress falling" : "Enter the ring to capture";
            case COMPLETE -> "Capture complete";
        };
    }

    public static String countsText(int allies, int enemies) {
        return allies + (allies == 1 ? " ally / " : " allies / ") + enemies + (enemies == 1 ? " enemy" : " enemies");
    }

    public static String participationText(Participation local, Participation server, double horizontal, int radius) {
        return switch (local) {
            case OUTSIDE -> "OUTSIDE · Move " + String.format(java.util.Locale.ROOT, "%.1f", Math.max(.1, Math.ceil(Math.max(0, horizontal - radius) * 10) / 10)) + " blocks closer";
            case HEIGHT -> "WRONG HEIGHT · Reach the core's floor";
            case BLOCKED -> "INSIDE · Blocked by a wall or roof";
            case INELIGIBLE -> "NOT COUNTED · You are not eligible to capture";
            case CREATIVE -> "NOT COUNTED · Creative players do not capture";
            case SPECTATOR -> "NOT COUNTED · Spectators do not capture";
            case DEAD -> "NOT COUNTED · Return alive to capture";
            case UNAVAILABLE -> "Waiting for loaded terrain";
            case COUNTED -> server == Participation.COUNTED
                    ? "INSIDE · You count toward capture" : "INSIDE · Awaiting server position check";
        };
    }
}
