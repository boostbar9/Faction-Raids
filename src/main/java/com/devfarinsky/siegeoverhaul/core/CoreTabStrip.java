package com.devfarinsky.siegeoverhaul.core;

/** A bounded window of readable tabs. Additional pages never shrink buttons indefinitely. */
public record CoreTabStrip(int x, int width, int count, int first, int visible, int gap) {
    public static final int MIN_TAB_WIDTH = 72;
    public static final int ARROW_WIDTH = 20;

    public static CoreTabStrip fit(int x, int width, int count, int selected, int gap) {
        if (count < 1 || width < MIN_TAB_WIDTH + 2 * (ARROW_WIDTH + gap) || gap < 0)
            throw new IllegalArgumentException("Invalid tab strip bounds");
        int allCapacity = (width + gap) / (MIN_TAB_WIDTH + gap);
        int visible = count <= allCapacity ? count
                : Math.max(1, (width - 2 * (ARROW_WIDTH + gap) + gap) / (MIN_TAB_WIDTH + gap));
        int index = Math.max(0, Math.min(count - 1, selected));
        int first = Math.max(0, Math.min(count - visible, index - visible / 2));
        return new CoreTabStrip(x, width, count, first, visible, gap);
    }
    public boolean overflow() { return visible < count; }
    public boolean shows(int index) { return index >= first && index < first + visible; }
    public int tabWidth() {
        return (width - (overflow() ? 2 * (ARROW_WIDTH + gap) : 0) - gap * (visible - 1)) / visible;
    }
    public int tabX(int index) {
        return x + (overflow() ? ARROW_WIDTH + gap : 0) + (index - first) * (tabWidth() + gap);
    }
    public int next(int selected, int direction) { return Math.floorMod(selected + Integer.signum(direction), count); }
    public static boolean contains(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
