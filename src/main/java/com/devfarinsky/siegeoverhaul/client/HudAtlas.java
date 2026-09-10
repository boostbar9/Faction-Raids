package com.devfarinsky.siegeoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Central access point for the immersive HUD texture atlas at
 * {@code siegeoverhaul:textures/gui/hud_atlas.png}.
 *
 * <p>All coordinates are pre-baked into named constants to keep call sites
 * readable. The atlas is 256x256 with regions for ornate corners, tab and
 * currency icons, three-slice buttons, a crest banner, footer/backdrop
 * tiles, radial glows, and an animated sand-glass spinner.
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

    // Tab and currency icons (24x24).
    public static final int[] ICON_ARMY = {96, 0, 24, 24};
    public static final int[] ICON_LOOT = {120, 0, 24, 24};
    public static final int[] ICON_BANK = {96, 24, 24, 24};
    public static final int[] ICON_MAP = {120, 24, 24, 24};
    public static final int[] ICON_INTEL = {96, 48, 24, 24};
    public static final int[] ICON_EMERALD = {120, 48, 24, 24};

    // Three-slice buttons (48x16 each). Split them 16/16/16 for left/mid/right.
    public static final int[] BUTTON_GOLD = {144, 0, 48, 16};
    public static final int[] BUTTON_GOLD_HOVER = {144, 16, 48, 16};
    public static final int[] BUTTON_GOLD_PRESS = {144, 32, 48, 16};
    public static final int[] BUTTON_TEAL = {144, 48, 48, 16};

    // Treasury pill (96x32).
    public static final int[] TREASURY_PILL = {0, 48, 96, 32};

    // Footer strip (96x48).
    public static final int[] FOOTER_STRIP = {0, 80, 96, 48};

    // Backdrop (128x64) - candlelit navy vellum.
    public static final int[] BACKDROP = {0, 128, 128, 64};

    // Radial glows (64x64).
    public static final int[] GLOW_SOFT = {128, 128, 64, 64};
    public static final int[] GLOW_HOT = {192, 128, 64, 64};

    // Rivet clusters (32x32).
    public static final int[] RIVETS_GOLD = {0, 192, 32, 32};
    public static final int[] RIVETS_DARK = {32, 192, 32, 32};

    // Siege ribbon (128x64) - hanging banner used when a siege is active.
    public static final int[] SIEGE_RIBBON = {64, 192, 128, 64};

    // Spinner sand-glass (64x32 with four 16x32 frames).
    public static final int[] SPINNER = {192, 64, 64, 32};

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
     * Draw an atlas region with a colored tint. Preserves the surrounding
     * render state.
     */
    public static void blitTinted(GuiGraphics g, int[] region, int x, int y, int argb) {
        float a = ((argb >> 24) & 0xff) / 255f;
        float r = ((argb >> 16) & 0xff) / 255f;
        float gg = ((argb >> 8) & 0xff) / 255f;
        float b = (argb & 0xff) / 255f;
        RenderSystem.setShaderColor(r, gg, b, a);
        blit(g, region, x, y);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Blit a region at ({@code x},{@code y}) scaled to ({@code w},{@code h})
     * with a colored tint (used to color-code glows for treasury vs siege).
     */
    public static void blitTinted(GuiGraphics g, int[] region, int x, int y,
                                  int w, int h, int argb) {
        float a = ((argb >> 24) & 0xff) / 255f;
        float r = ((argb >> 16) & 0xff) / 255f;
        float gg = ((argb >> 8) & 0xff) / 255f;
        float b = (argb & 0xff) / 255f;
        RenderSystem.setShaderColor(r, gg, b, a);
        blit(g, region, x, y, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Enable additive blending so subsequent blits act as glows. */
    public static void enableAdditive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
    }

    /** Restore normal alpha blending. Call after {@link #enableAdditive()}. */
    public static void disableAdditive() {
        RenderSystem.defaultBlendFunc();
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

    /**
     * Draw a 3-slice button stretched to width {@code w}. {@code region} must
     * be one of the {@code BUTTON_*} regions (48x16, split 16/16/16).
     */
    public static void button3Slice(GuiGraphics g, int[] region, int x, int y, int w) {
        int rx = region[0], ry = region[1], rh = region[3];
        int left = rx;
        int mid = rx + 16;
        int right = rx + 32;
        // Left cap
        g.blit(TEXTURE, x, y, 16, rh, left, ry, 16, rh, ATLAS, ATLAS);
        // Middle stretched
        int midW = Math.max(0, w - 32);
        if (midW > 0) g.blit(TEXTURE, x + 16, y, midW, rh, mid, ry, 16, rh, ATLAS, ATLAS);
        // Right cap
        g.blit(TEXTURE, x + Math.max(16, w - 16), y, 16, rh, right, ry, 16, rh, ATLAS, ATLAS);
    }

    /** Pick the animated spinner frame (0..3) for the given game tick. */
    public static int[] spinnerFrame(int tick) {
        int f = Math.floorMod(tick / 3, 4);
        return new int[]{SPINNER[0] + f * 16, SPINNER[1], 16, SPINNER[3]};
    }
}
