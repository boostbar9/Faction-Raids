package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CivilianReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Keyboard-selectable roster row; compact screens show one detailed resident per page. */
final class CivilianResidentButton extends Button {
    private final BooleanSupplier selected;
    private CivilianReport.Resident resident;
    CivilianResidentButton(int x, int y, int width, int height, OnPress action, BooleanSupplier selected) {
        super(x, y, width, height, Component.empty(), action, DEFAULT_NARRATION);
        this.selected = selected;
    }
    CivilianReport.Resident resident() { return resident; }
    void show(CivilianReport.Resident value) {
        resident = value;
        setMessage(Component.literal(value.label() + ". " + profession(value) + ". " + value.status()));
    }
    static String profession(CivilianReport.Resident r) {
        if (!r.loaded()) return "Details unavailable";
        var id = r.profession();
        return Component.translatable("entity." + id.getNamespace() + ".villager." + id.getPath()).getString()
                + " · Lv " + r.level() + (r.baby() ? " · Child" : " · Adult");
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (resident == null) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        CommandFrame.card(g, x, y, w, h, selected.getAsBoolean() ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_STEEL, isHoveredOrFocused());
        if (isFocused()) g.fill(x, y, x + w, y + 1, CommandPalette.ACCENT_TEAL);
        int left = x + 7;
        if (h >= 56 && resident.loaded()) {
            CivilianPortrait.draw(g, x + 5, y + 5, Math.min(64, h - 10), mouseX, mouseY, resident);
            left = x + Math.min(64, h - 10) + 12;
        }
        line(g, resident.label(), left, y + 5, x + w - left - 7, CommandPalette.TEXT);
        line(g, profession(resident), left, y + 16, x + w - left - 7, CommandPalette.TEXT_MUTED);
        line(g, resident.status(), left, y + 27, x + w - left - 7,
                resident.paused() ? CommandPalette.ACCENT_GOLD : CommandPalette.TEXT_MUTED);
        if (h >= 56 && resident.loaded()) line(g, (resident.bed() ? "Bed remembered" : "No bed remembered") + " · "
                + (resident.workstation() ? "Workstation remembered" : "No workstation remembered"),
                left, y + 40, x + w - left - 7, CommandPalette.TEXT_MUTED);
    }
    private static void line(GuiGraphics g, String text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) > width) text = font.plainSubstrByWidth(text, Math.max(1, width - font.width("…"))) + "…";
        g.drawString(font, text, x, y, color, false);
    }
}
