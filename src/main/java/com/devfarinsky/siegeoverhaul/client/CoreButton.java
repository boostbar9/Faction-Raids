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
 * <p>Quiet navigation tabs and square action controls share one widget.
 * A teal underline marks selection, while a full outline marks keyboard focus.
 * Buttons retain vanilla focus, narration and keyboard activation so
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
    private String detail;
    private boolean primary;
    public CoreButton primary() { primary = true; return this; }

    public void setDetail(String value) { detail = value; }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        if (detail != null) output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT,
                Component.literal(detail + " emeralds. Open the faction Treasury."));
    }

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

    /** Keyboard focus is distinguishable from both hover and selection. */
    static int borderColor(boolean active, boolean focused, boolean hovered, boolean selected, boolean primary) {
        if (!active) return CommandPalette.CARD_BORDER_DIM;
        if (focused) return CommandPalette.ACCENT_TEAL;
        return hovered || selected || primary ? CommandPalette.CARD_BORDER_HOVER : CommandPalette.CARD_BORDER;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean chosen = selected.getAsBoolean();
        boolean hover = isHoveredOrFocused();

        // Square, low-noise controls retain vanilla focus/narration and unchanged hit boxes.
        int border = borderColor(active, isFocused(), hover, chosen, primary);
        int fill = !active ? CommandPalette.CARD_TOP_DIM
                : primary ? CommandPalette.CONTROL_PRIMARY : chosen ? CommandPalette.CONTROL_SELECTED
                : hover ? CommandPalette.CARD_HOVER_TOP : CommandPalette.CARD_TOP;
        int x = getX(), y = getY(), w = width, h = height;
        if (tab) {
            // Inactive navigation stays quiet; selection and keyboard focus remain explicit.
            g.fill(x, y, x + w, y + h, chosen || hover ? fill : CommandPalette.PANEL_TOP);
        } else {
            g.fill(x, y, x + w, y + h, border);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        }
        if (chosen && active) g.fill(x + 1, y + h - 2, x + w - 1, y + h, CommandPalette.ACCENT_TEAL);
        if (isFocused() && active) {
            g.fill(x, y, x + w, y + 1, CommandPalette.ACCENT_TEAL);
            g.fill(x, y, x + 1, y + h, CommandPalette.ACCENT_TEAL);
            g.fill(x + w - 1, y, x + w, y + h, CommandPalette.ACCENT_TEAL);
            g.fill(x, y + h - 1, x + w, y + h, CommandPalette.ACCENT_TEAL);
        }

        var font = Minecraft.getInstance().font;
        int textColor = !active ? CommandPalette.TEXT_DIM
                : chosen || primary ? CommandPalette.TEXT
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
        g.drawString(font, label, labelX, detail == null ? y + (h - 8) / 2 : y + 3, textColor, false);
        if (detail != null) {
            String value = font.plainSubstrByWidth(detail, Math.max(1, w - 12));
            g.drawString(font, value, x + (w - font.width(value)) / 2, y + 14,
                    CommandPalette.ACCENT_EMERALD, false);
        }
    }
}
