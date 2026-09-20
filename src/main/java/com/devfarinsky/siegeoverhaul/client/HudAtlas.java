package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Central access point for the immersive HUD texture atlas at
 * {@code siegeoverhaul:textures/gui/hud_atlas.png}.
 *
 * <p>The modern Command Center paints its panels procedurally and retains
 * only the recognizable faction crest from the atlas. This avoids repeated
 * full-window texture tiling while keeping one thematic heraldic anchor.
 */
public final class HudAtlas {
    public static final ResourceLocation TEXTURE =
            new ResourceLocation("siegeoverhaul", "textures/gui/hud_atlas.png");

    public static final int ATLAS = 256;

    // Crest banner (32x24).
    public static final int[] CREST_BANNER = {64, 0, 32, 24};

    private HudAtlas() {}

    /** Draw an atlas region at ({@code x},{@code y}) at native size. */
    public static void blit(GuiGraphics g, int[] region, int x, int y) {
        g.blit(TEXTURE, x, y, region[0], region[1], region[2], region[3], ATLAS, ATLAS);
    }

}
