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
        int width = Math.max(1, Math.min(460, viewportWidth - 16));
        int capacity = Math.max(1, (viewportHeight - 76) / 11);
        int count = Math.max(1, Math.min(capacity, requestedLines));
        int height = count * 11 + 10;
        return new Bounds(5, Math.max(4, viewportHeight - 60 - height), width + 6, height, width, capacity);
    }

    static void render(GuiGraphics graphics, Font font, int viewportWidth, int viewportHeight,
                       String title, String status, boolean ready, List<String> details) {
        var initial = bounds(viewportWidth, viewportHeight, 1);
        var lines = new ArrayList<Line>();
        append(lines, font, title, initial.textWidth() - 4, CommandPalette.ACCENT_GOLD);
        // Keep the authoritative readiness/problem ahead of optional explanatory material text.
        append(lines, font, status, initial.textWidth() - 4,
                ready ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_BLOOD);
        for (String detail : details) append(lines, font, detail, initial.textWidth() - 4, CommandPalette.TEXT_MUTED);
        var box = bounds(viewportWidth, viewportHeight, lines.size());
        CommandFrame.surface(graphics, box.x(), box.y(), box.width(), box.height());
        int count = Math.min(lines.size(), box.lineCapacity());
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            graphics.drawString(font, line.text(), box.x() + 5, box.y() + 5 + i * 11, line.color(), false);
        }
    }
    private static void append(List<Line> lines, Font font, String text, int width, int color) {
        for (var line : font.split(Component.literal(text), Math.max(1, width))) lines.add(new Line(line, color));
    }
}
