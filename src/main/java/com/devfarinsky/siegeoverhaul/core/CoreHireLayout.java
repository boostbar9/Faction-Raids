package com.devfarinsky.siegeoverhaul.core;

/** Coordinates use Minecraft's scaled GUI dimensions, recalculated on every resize. */
public record CoreHireLayout(int x, int y, int width, int height, boolean compact) {
    public static CoreHireLayout fit(int screenWidth, int screenHeight) {
        int width = Math.min(760, screenWidth - 16);
        int height = Math.min(330, screenHeight - 16);
        return new CoreHireLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height, width < 500 || height < 310);
    }
    public int cardWidth() { return compact ? width - 20 : (width - 40) / 3; }
    public int cardHeight() { return compact ? (height - 76) / 3 : height - 88; }
    public int cardX(int index) { return x + 10 + (compact ? 0 : index * (cardWidth() + 10)); }
    public int cardY(int index) { return y + 48 + (compact ? index * (cardHeight() + 4) : 0); }
}
