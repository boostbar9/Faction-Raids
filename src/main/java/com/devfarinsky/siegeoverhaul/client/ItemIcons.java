package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vanilla item glyphs for the command center HUD.
 *
 * <p>Currency readouts use the real Minecraft emerald sprite rather than a
 * hand-drawn look-alike, so the HUD always matches whatever resource pack the
 * player has loaded. {@link GuiGraphics#renderItem} always draws at 16x16, so
 * {@link #draw} scales the pose stack to reach smaller HUD sizes without
 * disturbing the surrounding render state.
 */
public final class ItemIcons {
    /** The emerald sprite used everywhere the HUD shows emeralds. */
    public static final ItemStack EMERALD = new ItemStack(Items.EMERALD);

    private static final int NATIVE = 16;

    private ItemIcons() {}

    /** Draw {@code stack} with its top-left at ({@code x},{@code y}) at 16x16. */
    public static void draw(GuiGraphics g, ItemStack stack, int x, int y) {
        draw(g, stack, x, y, NATIVE);
    }

    /** Draw {@code stack} scaled so its rendered square is {@code size} pixels. */
    public static void draw(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (size <= 0 || stack.isEmpty()) return;
        if (size == NATIVE) {
            g.renderItem(stack, x, y);
            return;
        }
        float scale = size / (float) NATIVE;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** Draw the emerald sprite at {@code size} pixels. */
    public static void emerald(GuiGraphics g, int x, int y, int size) {
        draw(g, EMERALD, x, y, size);
    }
}
