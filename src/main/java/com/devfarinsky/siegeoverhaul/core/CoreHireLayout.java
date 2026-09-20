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
public record CoreHireLayout(int x, int y, int width, int height,
                             boolean compact, float scale,
                             int viewportWidth, int viewportHeight) {
    /** Minecraft normally keeps the scaled GUI at least this large. */
    public static final int MIN_VIEWPORT_WIDTH = 320;
    public static final int MIN_VIEWPORT_HEIGHT = 240;
    public static final int MAX_PANEL_WIDTH = 760;
    public static final int MAX_PANEL_HEIGHT = 420;
    public static final int TAB_TOP = 32;
    public static final int TAB_HEIGHT = 20;
    public static final int RIBBON_TOP = 55;
    public static final int RIBBON_HEIGHT = 15;
    public static final int CONTENT_TOP = 74;
    /** Roomy screens get a compact page title and live context line. */
    public static final int PAGE_HEADER_HEIGHT = 18;
    public static final int PAGE_HEADER_GAP = 4;
    public static final int FOOTER_HEIGHT = 18;
    public static final int ARMY_ACTION_HEIGHT = 18;
    private static final int MIN_PANEL_WIDTH = 304;
    private static final int MIN_PANEL_HEIGHT = 224;
    private static final int FULL_CARD_WIDTH = 230;
    private static final int FULL_CARD_HEIGHT = 96;
    /**
     * Loot and Territory content is capped at the height it actually needs.
     * Anything beyond these caps is handed back as a free band the tabs can
     * grow into, instead of inflating every card with dead space.
     */
    private static final int FULL_MARKET_HEIGHT = 56;
    private static final int COMPACT_MARKET_HEIGHT = 26;
    private static final int MIN_MARKET_HEIGHT = 24;
    private static final int FULL_TERRITORY_CARD_HEIGHT = 74;
    private static final int COMPACT_TERRITORY_CARD_HEIGHT = 52;
    private static final int MIN_TERRITORY_CARD_HEIGHT = 46;

    public static CoreHireLayout fit(int screenWidth, int screenHeight) {
        int physicalWidth = Math.max(1, screenWidth);
        int physicalHeight = Math.max(1, screenHeight);

        // Minecraft's normal GUI-scale chooser maintains a 320x240 logical
        // viewport, but tiny resizable windows and some modded scale options
        // can go below it. Keep one stable logical canvas and scale the whole
        // HUD down uniformly in that exceptional case. This preserves every
        // button hitbox and vertical band instead of letting the footer and
        // Army controls collapse into the cards.
        float scale = Math.min(1.0F, Math.min(
                physicalWidth / (float) MIN_VIEWPORT_WIDTH,
                physicalHeight / (float) MIN_VIEWPORT_HEIGHT));
        int viewportWidth = Math.max(MIN_VIEWPORT_WIDTH,
                (int) Math.floor(physicalWidth / scale));
        int viewportHeight = Math.max(MIN_VIEWPORT_HEIGHT,
                (int) Math.floor(physicalHeight / scale));

        // Grow with the Minecraft-scaled viewport instead of snapping to one
        // fixed window. Caps keep line lengths and mouse travel comfortable on
        // ultrawide / 4K displays while the body receives all intermediate
        // space on ordinary resolutions.
        int w = Math.max(MIN_PANEL_WIDTH,
                Math.min(MAX_PANEL_WIDTH, viewportWidth - 16));
        int h = Math.max(MIN_PANEL_HEIGHT,
                Math.min(MAX_PANEL_HEIGHT, viewportHeight - 16));
        int x = Math.max(0, (viewportWidth - w) / 2);
        int y = Math.max(0, (viewportHeight - h) / 2);

        CoreHireLayout roomy = new CoreHireLayout(
                x, y, w, h, false, scale, viewportWidth, viewportHeight);
        // Select the detailed card only when its *actual derived geometry*
        // has enough room. This reacts correctly to wide-short and tall-narrow
        // aspect ratios instead of relying on a single resolution cutoff.
        boolean compact = roomy.cardWidth() < FULL_CARD_WIDTH
                || roomy.cardHeight() < FULL_CARD_HEIGHT;
        return new CoreHireLayout(
                x, y, w, h, compact, scale, viewportWidth, viewportHeight);
    }

    /** Convert physical mouse coordinates into this layout's logical canvas. */
    public double logicalX(double physicalX) { return physicalX / scale; }
    public double logicalY(double physicalY) { return physicalY / scale; }
    public int logicalX(int physicalX) { return (int) Math.floor(logicalX((double) physicalX)); }
    public int logicalY(int physicalY) { return (int) Math.floor(logicalY((double) physicalY)); }

    public int outerMargin() { return 10; }
    public int columnGap() { return width < 360 ? 6 : 10; }
    public int rowGap() { return height < 260 ? 4 : 5; }
    public int tabGap() { return width < 420 ? 2 : 4; }
    public int controlGap() { return width < 420 ? 4 : 6; }
    public int feedbackWidth() {
        return compact ? Math.max(64, Math.min(78, width / 4)) : 110;
    }
    public int tabWidth(int count) {
        return Math.max(1,
                (width - outerMargin() * 2 - tabGap() * (count - 1)) / count);
    }
    public int tabX(int index, int count) {
        return x + outerMargin() + index * (tabWidth(count) + tabGap());
    }

    public int tabY() { return y + TAB_TOP; }
    public int ribbonY() { return y + RIBBON_TOP; }
    public int pageHeaderY() { return y + CONTENT_TOP; }
    public int pageHeaderHeight() { return compact ? 0 : PAGE_HEADER_HEIGHT; }
    public int contentY() {
        return pageHeaderY() + (compact ? 0 : PAGE_HEADER_HEIGHT + PAGE_HEADER_GAP);
    }
    public int footerY() { return y + height - FOOTER_HEIGHT; }
    public int contentBottom() { return footerY() - 4; }

    /** Top of the dedicated Army deployment-kit row. */
    public int siegeYardY() { return footerY() - ARMY_ACTION_HEIGHT - 5; }

    /** Bottom edge reserved exclusively for the four rotating hire cards. */
    public int armyCardsBottom() { return siegeYardY() - 5; }

    public int cardWidth() { return (width - outerMargin() * 2 - columnGap()) / 2; }

    public int cardHeight() {
        return Math.max(40, (armyCardsBottom() - contentY() - rowGap()) / 2);
    }

    public int cardX(int i) {
        return x + outerMargin() + (i % 2) * (cardWidth() + columnGap());
    }

    public int cardY(int i) {
        return contentY() + (i / 2) * (cardHeight() + rowGap());
    }

    /** Shared Territory geometry for cards, buttons and hover regions, above the fortification strip. */
    public int territoryCardWidth() { return (width - 28) / 2; }

    /**
     * Territory cards no longer stretch to fill every spare pixel. They are
     * capped at the height their own content actually needs, so the leftover
     * space becomes a reusable band for future Territory features instead of
     * padding out four half-empty cards.
     */
    public int territoryCardHeight() {
        int available = (contentBottom() - 32 - contentY() - 8) / 2;
        return Math.max(MIN_TERRITORY_CARD_HEIGHT,
                Math.min(available, compact
                        ? COMPACT_TERRITORY_CARD_HEIGHT : FULL_TERRITORY_CARD_HEIGHT));
    }
    public int territoryCardX(int i) { return x + 10 + (i % 2) * (territoryCardWidth() + 8); }
    public int territoryCardY(int i) { return contentY() + (i / 2) * (territoryCardHeight() + 8); }
    public int territoryButtonY(int i) { return territoryCardY(i) + territoryCardHeight() - 24; }
    public int territoryDescriptionLines() {
        // Description text starts 28px into the card and must end at least
        // 6px above the purchase button, which sits 24px from the bottom.
        return Math.max(0, (territoryCardHeight() - 28 - 30) / 10);
    }

    /** First free pixel row below the Territory card grid. */
    public int territoryFreeTop() { return territoryCardY(2) + territoryCardHeight() + 8; }

    /** Height of the free Territory band left above the fortification strip. */
    public int territoryFreeHeight() {
        return Math.max(0, contentBottom() - 32 - territoryFreeTop());
    }

    /**
     * Loot rows are capped the same way. Three compact rows leave a free band
     * under the Loot grid that new loot features can claim without another
     * layout pass.
     */
    public int marketHeight() {
        int available = (contentBottom() - contentY() - rowGap() * 2) / 3;
        return Math.max(MIN_MARKET_HEIGHT,
                Math.min(available, compact ? COMPACT_MARKET_HEIGHT : FULL_MARKET_HEIGHT));
    }

    public int marketY(int i) {
        return contentY() + i * (marketHeight() + rowGap());
    }

    /** First free pixel row below the three Loot rows. */
    public int marketFreeTop() { return marketY(2) + marketHeight() + rowGap(); }

    /** Height of the free Loot band left above the footer. */
    public int marketFreeHeight() { return Math.max(0, contentBottom() - marketFreeTop()); }
}
