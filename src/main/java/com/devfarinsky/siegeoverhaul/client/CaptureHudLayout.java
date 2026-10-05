package com.devfarinsky.siegeoverhaul.client;

/** Keep the read-only objective card above the central aiming area, including compact GUI scale 3. */
public final class CaptureHudLayout {
    private CaptureHudLayout() {}
    public record Bounds(int x, int y, int width, int height, int lines) {}
    public static Bounds bounds(int viewportWidth, int viewportHeight, int requestedLines) {
        int width = Math.max(1, Math.min(350, viewportWidth - 16));
        int capacity = Math.max(0, (viewportHeight / 2 - 44) / 10);
        int lines = Math.min(Math.max(0, requestedLines), capacity);
        return new Bounds((viewportWidth - width) / 2, 8, width, lines * 10 + 18, lines);
    }
}
