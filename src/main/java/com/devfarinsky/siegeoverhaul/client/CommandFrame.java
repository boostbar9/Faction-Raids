package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural medieval-fantasy framing for the command center HUD.
 *
 * <p>The frame is drawn with rectangle fills — no per-frame allocations or
 * vertex buffers. The result deliberately uses modern, quiet surfaces and
 * thin information accents instead of tiling a busy texture over the entire
 * screen. Aged gold, midnight navy and small heraldic corners retain the
 * fantasy identity without making the menu look like an illustrated book.
 *
 * <p>All colors are provided by {@link CommandPalette} so the whole HUD stays
 * on one visual system.
 */
public final class CommandFrame {
    private CommandFrame() {}

    /**
     * Draw the outer command-center window frame at ({@code x},{@code y})
     * with the given dimensions.
     */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        // Deep floating shadow separates the command center from the world.
        g.fill(x - 6, y + 4, x + w + 6, y + h + 8, CommandPalette.SHADOW_OUTER);
        g.fill(x - 4, y + 2, x + w + 4, y + h + 6, CommandPalette.SHADOW_INNER);

        // Restrained aged-gold outline with a dark inner keyline.
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, CommandPalette.BEVEL_DARK);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, CommandPalette.BEVEL_LIGHT);

        // Midnight-navy body with a subtle vertical gradient and inner rim.
        g.fillGradient(x, y, x + w, y + h,
                CommandPalette.PANEL_TOP, CommandPalette.PANEL_BOTTOM);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, CommandPalette.PANEL_INSET);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, 0xff05070c);

        // Small architectural corners read as bronze joinery, not mystery icons.
        cornerBracket(g, x + 3, y + 3, +1, +1);
        cornerBracket(g, x + w - 4, y + 3, -1, +1);
        cornerBracket(g, x + 3, y + h - 4, +1, -1);
        cornerBracket(g, x + w - 4, y + h - 4, -1, -1);
    }

    /**
     * Draw a small ornamental L-bracket at ({@code x},{@code y}) in the given
     * direction. Used as a corner ornament on the outer window and around
     * portrait tiles for a heraldic frame feel.
     */
    public static void cornerBracket(GuiGraphics g, int x, int y, int dx, int dy) {
        int light = CommandPalette.BEVEL_LIGHT;
        int dark = CommandPalette.BEVEL_DARK;
        int arm = 10;
        int hx1 = Math.min(x, x + dx * arm);
        int hx2 = Math.max(x, x + dx * arm) + 1;
        int vy1 = Math.min(y, y + dy * arm);
        int vy2 = Math.max(y, y + dy * arm) + 1;
        g.fill(hx1, y, hx2, y + 2, dark);
        g.fill(x, vy1, x + 2, vy2, dark);
        g.fill(hx1, y, hx2, y + 1, light);
        g.fill(x, vy1, x + 1, vy2, light);
    }

    /**
     * Draw the header banner strip at the top of the window: a darker slab
     * with a two-color separator and space reserved for a crown-and-title
     * cluster on the left plus a treasury chip on the right.
     */
    public static void header(GuiGraphics g, int x, int y, int w, int height) {
        // Quiet gradient and a gold keyline keep the header crisp at any scale.
        g.fillGradient(x + 4, y + 4, x + w - 4, y + 4 + height,
                CommandPalette.HEADER_TOP, CommandPalette.HEADER_BOTTOM);
        g.fill(x + 5, y + 5, x + w - 5, y + 6, CommandPalette.PANEL_INSET);
        g.fill(x + 4, y + 4 + height, x + w - 4, y + 5 + height, CommandPalette.HAIRLINE);
        g.fill(x + 4, y + 5 + height, x + w - 4, y + 6 + height, CommandPalette.PANEL_BOTTOM);
    }

    /**
     * Draw an interior card panel with a subtle bevel and an accent hairline
     * along the top edge. Used for hire cards, loot cards, blessing cards,
     * bank widgets, roster panels.
     */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int accent) {
        card(g, x, y, w, h, accent, false);
    }

    /** Card with a brighter border/surface for a hovered actionable region. */
    public static void card(GuiGraphics g, int x, int y, int w, int h,
                            int accent, boolean hovered) {
        int border = hovered ? CommandPalette.CARD_BORDER_HOVER : CommandPalette.CARD_BORDER;
        int top = hovered ? CommandPalette.CARD_HOVER_TOP : CommandPalette.CARD_TOP;
        int bottom = hovered ? CommandPalette.CARD_HOVER_BOTTOM : CommandPalette.CARD_BOTTOM;
        g.fill(x, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y, x + w - 1, y + h, border);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
        // A narrow colored rail gives every card a clear purpose without an icon.
        g.fill(x + 1, y + 2, x + 3, y + h - 2, accent);
        g.fill(x + 3, y + 1, x + w - 2, y + 2,
                hovered ? CommandPalette.PANEL_INSET : CommandPalette.DIVIDER);
    }

    /**
     * Card variant that draws an accent top stripe. Used for status ribbons
     * where the color coding is important (siege active vs peace, etc.).
     */
    public static void cardStriped(GuiGraphics g, int x, int y, int w, int h, int accent) {
        card(g, x, y, w, h, accent);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, accent);
    }

    /**
     * Draw an interior card marked as sold/exhausted (dimmer, no accent glow).
     */
    public static void cardDimmed(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y + 1, x + w, y + h - 1, CommandPalette.CARD_BORDER_DIM);
        g.fill(x + 1, y, x + w - 1, y + h, CommandPalette.CARD_BORDER_DIM);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                CommandPalette.CARD_TOP_DIM, CommandPalette.CARD_BOTTOM_DIM);
        g.fill(x + 1, y + 2, x + 3, y + h - 2, CommandPalette.TEXT_DIM);
    }

    /**
     * Chip: a small inset pill for currency readouts, wave counters, etc.
     * The chip is right-aligned inside a caller-provided width, drawn with
     * a darker fill so text sits above the panel field.
     */
    public static void chip(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, CommandPalette.CHIP_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CommandPalette.CHIP_FILL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, accent);
    }

    /** Small text badge used for availability, tiers and short live states. */
    public static void badge(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, CommandPalette.CHIP_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CommandPalette.CHIP_FILL);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, accent);
    }

    /**
     * Divider hairline used between rows inside a card.
     */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, CommandPalette.DIVIDER);
    }

    /**
     * Draw a thin progress bar (used for retreat vote timers, blessing
     * durations, etc.). {@code progress} is clamped 0..1.
     */
    public static void progress(GuiGraphics g, int x, int y, int w, int h,
                                float progress, int accent) {
        float p = Math.max(0f, Math.min(1f, progress));
        g.fill(x, y, x + w, y + h, CommandPalette.CHIP_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CommandPalette.CHIP_FILL);
        int fill = Math.max(0, (int) ((w - 2) * p));
        if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, accent);
    }

    private static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, CommandPalette.RIVET_DARK);
        g.fill(x + 1, y + 1, x + 3, y + 3, CommandPalette.RIVET_LIGHT);
    }
}
