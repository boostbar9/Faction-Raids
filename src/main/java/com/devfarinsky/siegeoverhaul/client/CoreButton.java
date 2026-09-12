package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Custom-painted command-center buttons.
 *
 * <p>Two visual variants share the same widget: a full-width tab that reads as
 * a metal tab-stop over parchment, and an action button that reads as a forged
 * key. Buttons retain vanilla focus, narration and keyboard activation so
 * accessibility keeps working.
 *
 * <p>An optional {@link CommandIcon} is drawn to the left of the label; when
 * present, the label is centered in the remaining space.
 */
public final class CoreButton extends Button {
    private final BooleanSupplier selected;
    private final boolean tab;
    private final CommandIcon icon;

    public CoreButton(Component text, OnPress press,
                      int x, int y, int width, int height,
                      boolean tab, BooleanSupplier selected) {
        this(text, press, x, y, width, height, tab, selected, null);
    }

    public CoreButton(Component text, OnPress press,
                      int x, int y, int width, int height,
                      boolean tab, BooleanSupplier selected, CommandIcon icon) {
        super(x, y, width, height, text, press, DEFAULT_NARRATION);
        this.tab = tab;
        this.selected = selected;
        this.icon = icon;
    }

    /**
     * Legacy 3-slice panel primitive kept for callers outside this file.
     * New code should prefer {@link CommandFrame} helpers.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean chosen = selected.getAsBoolean();
        boolean hover = isHoveredOrFocused();

        // Palette for tabs vs. action keys, in three states: disabled, chosen, active.
        int borderTop = !active ? 0xff2a2018
                : chosen ? 0xffe8c968
                : hover ? 0xffc59a5c
                : 0xff5a4228;
        int borderBottom = !active ? 0xff17110c
                : chosen ? 0xff8b6a2c
                : 0xff2f2214;
        int fillTop = !active ? 0xff1a140f
                : chosen ? (tab ? 0xff3a2e1c : 0xff382c1a)
                : hover ? (tab ? 0xff2a2116 : 0xff2e2317)
                : (tab ? 0xff20180f : 0xff261c11);
        int fillBottom = !active ? 0xff0c0906
                : chosen ? 0xff20180d
                : (tab ? 0xff130e08 : 0xff17110b);

        int x = getX(), y = getY(), w = width, h = height;

        // Two-tone border with a top hairline for the metal-tab feel.
        g.fill(x, y + 1, x + w, y + h - 1, borderBottom);
        g.fill(x + 1, y, x + w - 1, y + h, borderBottom);
        g.fill(x + 1, y, x + w - 1, y + 1, borderTop);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, fillTop, fillBottom);
        if (chosen) {
            // Bright top-edge glow so the active tab reads at a glance.
            g.fill(x + 3, y + 1, x + w - 3, y + 2, 0xffe8c968);
        }

        var font = Minecraft.getInstance().font;
        int textColor = !active ? 0xff5a4d3a
                : chosen ? 0xfffff2c8
                : hover ? 0xffefe4c8
                : 0xffcdb99a;

        int iconSize = Math.min(h - 4, 12);
        int textLeft = x + 6;
        // At high GUI scales some buttons become too narrow for both their
        // glyph and complete label. Drop the decorative glyph first instead
        // of crushing or overlapping the actionable text.
        boolean showIcon = icon != null
                && w >= iconSize + font.width(getMessage()) + 18;
        if (showIcon) {
            icon.draw(g, x + 4, y + (h - iconSize) / 2, iconSize);
            textLeft = x + 6 + iconSize + 4;
        }
        int textAreaWidth = Math.max(1, x + w - 6 - textLeft);
        String fullLabel = getMessage().getString();
        String label = font.plainSubstrByWidth(fullLabel, textAreaWidth);
        if (!label.equals(fullLabel) && textAreaWidth > font.width("…")) {
            label = font.plainSubstrByWidth(fullLabel,
                    textAreaWidth - font.width("…")) + "…";
        }
        int labelWidth = font.width(label);
        int labelX = showIcon
                ? textLeft + Math.max(0, (textAreaWidth - labelWidth) / 2)
                : x + (w - labelWidth) / 2;
        g.drawString(font, label, labelX, y + (h - 8) / 2, textColor, false);
    }
}
