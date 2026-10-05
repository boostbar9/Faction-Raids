package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.items.LootBoxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Actual armory ItemStack, including its custom name, trim, enchantments and native tooltip. */
final class LootPreviewButton extends Button {
    private ItemStack preview = ItemStack.EMPTY;
    private LootBoxItem.Tier tier = LootBoxItem.Tier.EPIC;

    LootPreviewButton(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
    }
    void show(ItemStack stack, LootBoxItem.Tier rarity) {
        preview = stack;
        tier = rarity;
        setMessage(Component.literal("Possible " + tier.label + " reward: ").append(stack.getHoverName()));
    }
    ItemStack preview() { return preview; }

    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.tier(tier.ordinal()), isHoveredOrFocused());
        if (isFocused()) {
            g.fill(x, y, x + w, y + 1, CommandPalette.ACCENT_TEAL);
            g.fill(x, y + h - 1, x + w, y + h, CommandPalette.ACCENT_TEAL);
        }
        int size = Math.max(16, Math.min(48, h - 28));
        ItemIcons.draw(g, preview, x + (w - size) / 2, y + 3, size);
        String name = preview.getHoverName().getString();
        if (font.width(name) > w - 10) name = font.plainSubstrByWidth(name, Math.max(1, w - 10 - font.width("…"))) + "…";
        g.drawString(font, name, x + (w - font.width(name)) / 2, y + h - 22, CommandPalette.TEXT, false);
        g.drawString(font, tier.label + " · possible", x + 5, y + h - 11, CommandPalette.tier(tier.ordinal()), false);
    }
}
