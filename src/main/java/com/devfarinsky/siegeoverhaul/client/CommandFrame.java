package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural medieval-fantasy framing for the command center HUD.
 *
 * <p>The frame is drawn with pure rectangle fills — no textures, no per-frame
 * allocations, no vertex buffers. It gives the HUD a sleek, modern silhouette
 * (soft drop shadow, thin double border, faint corner rivets) while keeping
 * the fantasy palette (dark iron, aged bronze, deep parchment) that reads as
 * a Minecraft-native interface.
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
        // Soft ambient drop shadow behind the whole panel.
        g.fill(x - 6, y + 4, x + w + 6, y + h + 8, CommandPalette.SHADOW_OUTER);
        g.fill(x - 4, y + 2, x + w + 4, y + h + 6, CommandPalette.SHADOW_INNER);

        // Aged-bronze outer bevel.
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, CommandPalette.BEVEL_LIGHT);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, CommandPalette.BEVEL_DARK);

        // Deep parchment body with a subtle vertical gradient.
        g.fillGradient(x, y, x + w, y + h,
                CommandPalette.PANEL_TOP, CommandPalette.PANEL_BOTTOM);

        // Inner thin gold hairline for a polished feel.
        int hair = CommandPalette.HAIRLINE;
        g.fill(x + 3, y + 3, x + w - 3, y + 4, hair);
        g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, hair);
        g.fill(x + 3, y + 3, x + 4, y + h - 3, hair);
        g.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, hair);

        // Forged-iron rivet in each corner.
        rivet(g, x + 2, y + 2);
        rivet(g, x + w - 5, y + 2);
        rivet(g, x + 2, y + h - 5);
        rivet(g, x + w - 5, y + h - 5);
    }

    /**
     * Draw the header banner strip at the top of the window: a darker slab
     * with a two-color separator and space reserved for a crown-and-title
     * cluster on the left plus a treasury chip on the right.
     */
    public static void header(GuiGraphics g, int x, int y, int w, int height) {
        g.fillGradient(x + 4, y + 4, x + w - 4, y + 4 + height,
                CommandPalette.HEADER_TOP, CommandPalette.HEADER_BOTTOM);
        // Twin separator lines: bronze over shadow for a struck-metal look.
        g.fill(x + 4, y + 4 + height, x + w - 4, y + 5 + height, CommandPalette.HAIRLINE);
        g.fill(x + 4, y + 5 + height, x + w - 4, y + 6 + height, CommandPalette.PANEL_BOTTOM);
    }

    /**
     * Draw an interior card panel with a subtle bevel and an accent hairline
     * along the top edge. Used for hire cards, loot cards, blessing cards,
     * bank widgets, roster panels.
     */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y + 1, x + w, y + h - 1, CommandPalette.CARD_BORDER);
        g.fill(x + 1, y, x + w - 1, y + h, CommandPalette.CARD_BORDER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                CommandPalette.CARD_TOP, CommandPalette.CARD_BOTTOM);
        // Accent stripe: a bright band top-left that fades into the card.
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
