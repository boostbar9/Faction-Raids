package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

/** Read-only, bounded presentation for held-plan reviews. Never changes a plan or sends an action. */
final class PlanReviewHud {
    private PlanReviewHud() {}
    record Bounds(int x, int y, int width, int height, int textWidth, int lineCapacity) {}
    private record Line(FormattedCharSequence text, int color) {}

    /** Main-hand plan text wins when different plan types occupy both hands. */
    static boolean shouldDisplay(boolean thisPlanInMainHand, boolean otherPlanInMainHand) {
        return thisPlanInMainHand || !otherPlanInMainHand;
    }

    static Bounds bounds(int viewportWidth, int viewportHeight, int requestedLines) {
        return bounds(viewportWidth, viewportHeight, requestedLines, MAX_TEXT_WIDTH);
    }

    static final int MAX_TEXT_WIDTH = 300;

    /**
     * Top-left card sized to its text. The bottom-left anchor used to sit on the chat
     * and over the action bar, hiding the plan items' own feedback messages.
     */
    static Bounds bounds(int viewportWidth, int viewportHeight, int requestedLines, int textWidth) {
        int width = Math.max(1, Math.min(Math.min(MAX_TEXT_WIDTH, Math.max(60, textWidth)), viewportWidth - 22));
        int capacity = Math.max(1, (viewportHeight - 76) / 11);
        int count = Math.max(1, Math.min(capacity, requestedLines));
        int height = count * 11 + 10;
        return new Bounds(6, 6, width + 12, height, width, capacity);
    }

    static void render(GuiGraphics graphics, Font font, int viewportWidth, int viewportHeight,
                       String title, String status, boolean ready, List<String> details) {
        var initial = bounds(viewportWidth, viewportHeight, 1);
        var lines = new ArrayList<Line>();
        append(lines, font, title, initial.textWidth(), CommandPalette.ACCENT_GOLD);
        // Keep the authoritative readiness/problem ahead of optional explanatory material text.
        append(lines, font, status, initial.textWidth(), ready ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_BLOOD);
        for (String detail : details) append(lines, font, detail, initial.textWidth(), CommandPalette.TEXT_MUTED);
        int widest = 0;
        for (var line : lines) widest = Math.max(widest, font.width(line.text()));
        var box = bounds(viewportWidth, viewportHeight, lines.size(), widest);
        CommandFrame.hudSurface(graphics, box.x(), box.y(), box.width(), box.height(),
                ready ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_BLOOD);
        int count = Math.min(lines.size(), box.lineCapacity());
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            graphics.drawString(font, line.text(), box.x() + 8, box.y() + 5 + i * 11, line.color(), true);
        }
    }
    private static void append(List<Line> lines, Font font, String text, int width, int color) {
        for (var line : font.split(Component.literal(text), Math.max(1, width))) lines.add(new Line(line, color));
    }
}
