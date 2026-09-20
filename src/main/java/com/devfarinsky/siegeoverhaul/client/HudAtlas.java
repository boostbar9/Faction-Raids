package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Central access point for the immersive HUD texture atlas at
 * {@code siegeoverhaul:textures/gui/hud_atlas.png}.
 *
 * <p>All coordinates are pre-baked into named constants to keep call sites
 * readable. Only the frame, backing tiles, crest, and treasury treatment are
 * retained; ambiguous decorative icons and animated glows were removed from
 * the Command Center.
 */
public final class HudAtlas {
    public static final ResourceLocation TEXTURE =
            new ResourceLocation("siegeoverhaul", "textures/gui/hud_atlas.png");

    public static final int ATLAS = 256;

    // Corners (24x24 each). Layout is TL, TR, BL, BR.
    public static final int[] CORNER_TL = {0, 0, 24, 24};
    public static final int[] CORNER_TR = {24, 0, 24, 24};
    public static final int[] CORNER_BL = {0, 24, 24, 24};
    public static final int[] CORNER_BR = {24, 24, 24, 24};

    // Tiling strips (16x16).
    public static final int[] TILE_DIVIDER = {48, 0, 16, 16};
    public static final int[] TILE_HEADER_RIBBON = {48, 16, 16, 16};
    public static final int[] TILE_PARCHMENT = {48, 32, 16, 16};

    // Crest banner (32x24).
    public static final int[] CREST_BANNER = {64, 0, 32, 24};

    // Treasury pill (96x32).
    public static final int[] TREASURY_PILL = {0, 48, 96, 32};

    // Backdrop (128x64) - candlelit navy vellum.
    public static final int[] BACKDROP = {0, 128, 128, 64};


    private HudAtlas() {}

    /** Draw an atlas region at ({@code x},{@code y}) at native size. */
    public static void blit(GuiGraphics g, int[] region, int x, int y) {
        g.blit(TEXTURE, x, y, region[0], region[1], region[2], region[3], ATLAS, ATLAS);
    }

    /** Draw an atlas region at ({@code x},{@code y}) scaled to ({@code w},{@code h}). */
    public static void blit(GuiGraphics g, int[] region, int x, int y, int w, int h) {
        g.blit(TEXTURE, x, y, w, h, region[0], region[1], region[2], region[3], ATLAS, ATLAS);
    }

    /**
     * Tile a 16x16 atlas region across the given rectangle. Used for divider
     * bars, parchment fills, and header ribbons.
     */
    public static void tileHoriz(GuiGraphics g, int[] region, int x, int y, int w) {
        int tw = region[2];
        int drawn = 0;
        while (drawn < w) {
            int step = Math.min(tw, w - drawn);
            g.blit(TEXTURE, x + drawn, y, w - drawn >= tw ? tw : step, region[3],
                    region[0], region[1], step, region[3], ATLAS, ATLAS);
            drawn += step;
        }
    }

}
