package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Keeps all native item tooltip text and styles, even for tall enchanted armor at high GUI scales. */
final class CoreItemTooltip {
    private CoreItemTooltip() {}
    static CoreTooltipLayout draw(GuiGraphics g, Font font, ItemStack item, int width, int height, int x, int y) {
        return drawText(g, font, Screen.getTooltipFromItem(Minecraft.getInstance(), item), width, height, x, y);
    }
    static CoreTooltipLayout drawText(GuiGraphics g, Font font, java.util.List<net.minecraft.network.chat.Component> text,
                                     int width, int height, int x, int y) {
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : text)
            lines.addAll(font.split(line, Math.max(24, Math.min(360, width - 20))));
        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(1);
        // Native text tooltip components are ten pixels high; two extra pixels cover the title gap.
        int contentHeight = lines.size() * 10 + 2;
        float scale = CoreTooltipLayout.scale(width, height, contentWidth, contentHeight);
        CoreTooltipLayout[] placed = new CoreTooltipLayout[1];
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.renderTooltip(font, lines, (ignoredWidth, ignoredHeight, ignoredX, ignoredY, tipWidth, tipHeight) -> {
            placed[0] = CoreTooltipLayout.fit(width, height, tipWidth, tipHeight, x, y, scale);
            return new org.joml.Vector2i((int) Math.ceil(placed[0].x() / scale) + 4,
                    (int) Math.ceil(placed[0].y() / scale) + 4);
        }, (int) (x / scale), (int) (y / scale));
        g.pose().popPose();
        return placed[0];
    }
}
