package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.*;

/** Open the same native screens used by Recruits' own key bindings. */
@OnlyIn(Dist.CLIENT)
public final class NativeRecruitsMenus {
    private NativeRecruitsMenus() {}
    public static void factions() { open(false); }
    public static void claims() { open(true); }
    private static void open(boolean map) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (map && !minecraft.level.dimension().equals(Level.OVERWORLD)) {
            minecraft.player.displayClientMessage(Component.literal("Recruits claims are managed in the Overworld."), false);
            return;
        }
        try {
            Screen screen = map
                    ? (Screen) Class.forName("com.talhanation.recruits.client.gui.worldmap.WorldMapScreen").getConstructor().newInstance()
                    : (Screen) Class.forName("com.talhanation.recruits.client.gui.faction.FactionMainScreen").getConstructor(Player.class).newInstance(minecraft.player);
            minecraft.setScreen(screen);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.warn("Could not open native Recruits menu", ex);
            minecraft.player.displayClientMessage(Component.literal("Could not open Recruits' interface. Use its faction/map key binding and check the mod version."), false);
        }
    }
}
