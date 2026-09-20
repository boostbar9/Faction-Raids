package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * Custom-painted command-center buttons.
 *
 * <p>Two visual variants share the same widget: a full-width tab that reads as
 * a metal tab-stop over parchment, and an action button that reads as a forged
 * key. Buttons retain vanilla focus, narration and keyboard activation so
 * accessibility keeps working.
 *
 * <p>Buttons may show a real Minecraft item sprite when that conveys useful
 * information (currently the emerald on treasury controls). Decorative
 * procedural glyphs are intentionally excluded so labels stay unambiguous.
 */
public final class CoreButton extends Button {
    private final BooleanSupplier selected;
    private final boolean tab;
    private final ItemStack itemIcon;

    public CoreButton(Component text, OnPress press,
                      int x, int y, int width, int height,
                      boolean tab, BooleanSupplier selected) {
        this(text, press, x, y, width, height, tab, selected, ItemStack.EMPTY);
    }


    /**
     * Variant that draws a real Minecraft item sprite as the glyph. Used for
     * currency controls so they show the vanilla emerald instead of a
     * hand-drawn stand-in.
     */
    public CoreButton(Component text, OnPress press,
                      int x, int y, int width, int height,
                      boolean tab, BooleanSupplier selected, ItemStack itemIcon) {
        super(x, y, width, height, text, press, DEFAULT_NARRATION);
        this.tab = tab;
        this.selected = selected;
        this.itemIcon = itemIcon == null ? ItemStack.EMPTY : itemIcon;
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

        // Flat navy controls with a restrained gold selection line feel more
        // like a command console while remaining consistent with the setting.
        int border = !active ? CommandPalette.CARD_BORDER_DIM
                : chosen ? CommandPalette.BEVEL_LIGHT
                : hover ? CommandPalette.CARD_BORDER_HOVER
                : CommandPalette.CARD_BORDER;
        int fillTop = !active ? CommandPalette.CARD_TOP_DIM
                : chosen ? 0xff2a3448
                : hover ? CommandPalette.CARD_HOVER_TOP
                : tab ? 0xff182237 : CommandPalette.CARD_TOP;
        int fillBottom = !active ? CommandPalette.CARD_BOTTOM_DIM
                : chosen ? 0xff151d2d
                : hover ? CommandPalette.CARD_HOVER_BOTTOM
                : tab ? 0xff0e1523 : CommandPalette.CARD_BOTTOM;

        int x = getX(), y = getY(), w = width, h = height;

        g.fill(x, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y, x + w - 1, y + h, border);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, fillTop, fillBottom);
        if (chosen) {
            // Active tabs use a modern bottom rail; confirmation actions keep
            // the same language without introducing another pictogram.
            int indicatorY = tab ? y + h - 2 : y + 1;
            g.fill(x + 3, indicatorY, x + w - 3, indicatorY + 1,
                    CommandPalette.ACCENT_GOLD);
        } else if (hover && active) {
            g.fill(x + 4, y + 1, x + w - 4, y + 2, CommandPalette.PANEL_INSET);
        }

        var font = Minecraft.getInstance().font;
        int textColor = !active ? CommandPalette.TEXT_DIM
                : chosen ? CommandPalette.TEXT
                : hover ? 0xfff7f1df
                : CommandPalette.TEXT_MUTED;

        int iconSize = Math.min(h - 4, 12);
        int textLeft = x + 6;
        // At high GUI scales some buttons become too narrow for both the real
        // item sprite and complete label. Keep the actionable text readable.
        boolean showIcon = !itemIcon.isEmpty()
                && w >= iconSize + font.width(getMessage()) + 18;
        if (showIcon) {
            ItemIcons.draw(g, itemIcon, x + 4, y + (h - iconSize) / 2, iconSize);
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
