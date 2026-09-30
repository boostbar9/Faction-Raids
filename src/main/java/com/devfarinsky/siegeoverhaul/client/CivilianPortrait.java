package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Lightweight, client-only example of the villager housed by the civilian offer. */
final class CivilianPortrait {
    private static Villager preview;
    private static Object previewLevel;
    private CivilianPortrait() {}

    static void draw(GuiGraphics graphics, int x, int y, int size, float mouseX, float mouseY) {
        graphics.fill(x, y, x + size, y + size, 0xff4a3826);
        graphics.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, 0xff2a2016, 0xff130f0c);
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (previewLevel != level || preview == null) {
            preview = EntityType.VILLAGER.create(level);
            previewLevel = level;
        }
        if (preview == null) {
            graphics.renderItem(new ItemStack(Items.VILLAGER_SPAWN_EGG), x + size / 2 - 8, y + size / 2 - 8);
            return;
        }
        try {
            int cx = x + size / 2, cy = y + size - Math.max(3, size / 12);
            float lookX = Math.max(-40, Math.min(40, cx - mouseX));
            float lookY = Math.max(-40, Math.min(40, cy - size * .7f - mouseY));
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, cx, cy,
                    Math.max(8, (int) ((size - Math.max(3, size / 12) * 2) / 2.05f)), lookX, lookY, preview);
        } catch (RuntimeException failure) {
            graphics.renderItem(new ItemStack(Items.VILLAGER_SPAWN_EGG), x + size / 2 - 8, y + size / 2 - 8);
        }
    }

    static void clear() { preview = null; previewLevel = null; }
}
