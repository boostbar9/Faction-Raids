package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;

/** One rule for every Siege Overhaul HUD card. */
public final class HudVisibility {
    private HudVisibility() {}

    /** Hidden with F1, while F3 or the TAB player list is up, and behind any open screen. */
    public static boolean hidden(Minecraft mc) {
        return mc == null || mc.player == null || mc.options.hideGui || mc.options.renderDebug
                || mc.screen != null || mc.options.keyPlayerList.isDown();
    }
}
