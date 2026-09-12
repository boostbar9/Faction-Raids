package com.devfarinsky.siegeoverhaul.core;

/**
 * Responsive two-column command panels at every supported Minecraft GUI scale.
 *
 * <p>The old layout derived each card independently from the full window
 * height. That let the Army yard controls grow into the footer and left the
 * desktop card renderer trying to fit inside 40-60px cards at larger GUI
 * scales. This layout owns each vertical band explicitly, so tabs, status,
 * content, Army controls and footer can never overlap.</p>
 */
public record CoreHireLayout(int x, int y, int width, int height, boolean compact) {
    public static final int TAB_TOP = 32;
    public static final int TAB_HEIGHT = 20;
    public static final int RIBBON_TOP = 55;
    public static final int RIBBON_HEIGHT = 15;
    public static final int CONTENT_TOP = 74;
    public static final int FOOTER_HEIGHT = 18;
    public static final int ARMY_ACTION_HEIGHT = 18;
    private static final int OUTER_MARGIN = 10;
    private static final int COLUMN_GAP = 10;
    private static final int ROW_GAP = 5;

    public static CoreHireLayout fit(int screenWidth, int screenHeight) {
        int w = Math.max(304, Math.min(660, screenWidth - 16));
        int h = Math.max(224, Math.min(350, screenHeight - 16));
        // Minecraft's scaled dimensions can be smaller than our normal
        // 320x240 floor in unusual window sizes. Never position the panel
        // outside the visible top/left edge in that case.
        w = Math.min(w, screenWidth);
        h = Math.min(h, screenHeight);
        return new CoreHireLayout(
                Math.max(0, (screenWidth - w) / 2),
                Math.max(0, (screenHeight - h) / 2),
                w, h,
                // The full card needs roughly 100px of vertical room for its
                // portrait, description, price chip and action. Switch before
                // those elements are forced into the same rows.
                w < 500 || h < 330);
    }

    public int tabY() { return y + TAB_TOP; }
    public int ribbonY() { return y + RIBBON_TOP; }
    public int contentY() { return y + CONTENT_TOP; }
    public int footerY() { return y + height - FOOTER_HEIGHT; }
    public int contentBottom() { return footerY() - 4; }

    /** Top of the dedicated Army deployment-kit row. */
    public int siegeYardY() { return footerY() - ARMY_ACTION_HEIGHT - 5; }

    /** Bottom edge reserved exclusively for the four rotating hire cards. */
    public int armyCardsBottom() { return siegeYardY() - 5; }

    public int cardWidth() { return (width - OUTER_MARGIN * 2 - COLUMN_GAP) / 2; }

    public int cardHeight() {
        return Math.max(40, (armyCardsBottom() - contentY() - ROW_GAP) / 2);
    }

    public int cardX(int i) {
        return x + OUTER_MARGIN + (i % 2) * (cardWidth() + COLUMN_GAP);
    }

    public int cardY(int i) {
        return contentY() + (i / 2) * (cardHeight() + ROW_GAP);
    }

    public int marketHeight() {
        return Math.max(36, (contentBottom() - contentY() - ROW_GAP * 2) / 3);
    }

    public int marketY(int i) {
        return contentY() + i * (marketHeight() + ROW_GAP);
    }
}
