package com.devfarinsky.siegeoverhaul.client;

/** Avoid the aiming reticle, hotbar and the actual rendered boss-bar stack at compact GUI scales. */
public final class CaptureHudLayout {
    private CaptureHudLayout() {}
    public record Bounds(int x, int y, int width, int height, int lines) {}
    public static Bounds bounds(int viewportWidth, int viewportHeight, int requestedLines) {
        return bounds(viewportWidth, viewportHeight, requestedLines, 0);
    }
    public static Bounds bounds(int viewportWidth, int viewportHeight, int requestedLines, int bossBottom) {
        int width = Math.max(1, Math.min(350, viewportWidth - 16));
        int y = Math.max(8, bossBottom + 4);
        int desired = Math.max(0, requestedLines), fullHeight = desired * 10 + 18;
        if (y + fullHeight <= viewportHeight / 2 - 18)
            return new Bounds((viewportWidth - width) / 2, y, width, fullHeight, desired);
        // A stacked raid/commander objective keeps its whole band. Reflow into the
        // left margin so a taller card still leaves the middle of the view clear.
        width = Math.max(1, Math.min(220, viewportWidth / 2 - 26));
        int capacity = Math.max(0, (viewportHeight - 64 - y - 18) / 10);
        int lines = Math.min(desired, capacity);
        return new Bounds(8, y, width, lines * 10 + 18, lines);
    }
}
