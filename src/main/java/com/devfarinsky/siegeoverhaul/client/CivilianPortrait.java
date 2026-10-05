package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CivilianReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native client-only villager model using a loaded resident’s synchronized appearance. */
final class CivilianPortrait {
    private static Villager preview;
    private static Object previewLevel;
    private static CivilianReport.Resident shown;
    private CivilianPortrait() {}

    static void draw(GuiGraphics graphics, int x, int y, int size, float mouseX, float mouseY, CivilianReport.Resident resident) {
        graphics.fill(x, y, x + size, y + size, 0xff4a3826);
        graphics.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, 0xff2a2016, 0xff130f0c);
        var level = Minecraft.getInstance().level;
        if (level == null || resident == null || !resident.loaded()) return;
        if (previewLevel != level || preview == null) {
            preview = EntityType.VILLAGER.create(level);
            previewLevel = level;
            shown = null;
        }
        if (preview == null) {
            graphics.renderItem(new ItemStack(Items.VILLAGER_SPAWN_EGG), x + size / 2 - 8, y + size / 2 - 8);
            return;
        }
        try {
            if (!resident.equals(shown)) {
                var professions = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION;
                var types = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE;
                if (!professions.containsKey(resident.profession()) || !types.containsKey(resident.type())) return;
                preview.setVillagerData(new net.minecraft.world.entity.npc.VillagerData(
                        types.get(resident.type()), professions.get(resident.profession()), resident.level()));
                preview.setAge(resident.baby() ? -24000 : 0);
                shown = resident;
            }
            int cx = x + size / 2, cy = y + size - Math.max(3, size / 12);
            float lookX = Math.max(-40, Math.min(40, cx - mouseX));
            float lookY = Math.max(-40, Math.min(40, cy - size * .7f - mouseY));
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, cx, cy,
                    Math.max(8, (int) ((size - Math.max(3, size / 12) * 2) / 2.05f)), lookX, lookY, preview);
        } catch (RuntimeException failure) {
            graphics.renderItem(new ItemStack(Items.VILLAGER_SPAWN_EGG), x + size / 2 - 8, y + size / 2 - 8);
        }
    }

    static void clear() { preview = null; previewLevel = null; shown = null; }
}
