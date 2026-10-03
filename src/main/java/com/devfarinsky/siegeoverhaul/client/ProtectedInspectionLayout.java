package com.devfarinsky.siegeoverhaul.client;

/** Responsive bounds shared by native inspection widgets, mouse targets and layout regressions. */
public final class ProtectedInspectionLayout {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }
    public record Layout(Rect panel, Rect preview, Rect materials, Rect materialsPage,
                         Rect projection, Rect cancel, Rect close, int padding,
                         int materialCapacity, boolean pagedMaterials, boolean content, boolean details) {}
    private ProtectedInspectionLayout() {}

    public static Layout create(int viewportWidth, int viewportHeight, int materialCount) {
        int width = Math.max(1, viewportWidth), height = Math.max(1, viewportHeight);
        int margin = Math.min(12, Math.max(2, Math.min(width, height) / 20));
        int panelWidth = Math.max(1, Math.min(620, width - 2 * margin));
        int panelHeight = Math.max(1, Math.min(330, height - 2 * margin));
        int x = (width - panelWidth) / 2, y = (height - panelHeight) / 2;
        int padding = Math.min(10, Math.max(4, panelWidth / 30));
        int gap = Math.min(10, Math.max(4, panelWidth / 40));
        int buttonHeight = Math.min(20, Math.max(12, panelHeight / 10));
        int footerY = Math.max(y, y + panelHeight - padding - buttonHeight);
        int contentY = y + (panelHeight >= 200 ? 54 : panelHeight >= 150 ? 40 : 24);
        int contentHeight = Math.max(0, footerY - contentY - 12);
        int insideWidth = Math.max(1, panelWidth - padding * 2);
        int sideWidth = Math.min(180, Math.max(120, insideWidth / 3));
        int previewWidth = Math.max(0, insideWidth - gap - sideWidth);
        int buttonWidth = Math.max(1, (insideWidth - 2 * gap) / 3);
        int buttonX = x + padding;
        int capacity = Math.max(1, Math.min(5, (contentHeight - 20) / 20));
        boolean paged = Math.max(0, materialCount) > capacity;
        if (paged) capacity = Math.max(1, Math.min(5, (contentHeight - 40) / 20));
        boolean content = contentHeight >= 40 && previewWidth >= 60 && sideWidth >= 60;
        return new Layout(new Rect(x, y, panelWidth, panelHeight),
                new Rect(x + padding, contentY, previewWidth, contentHeight),
                new Rect(x + padding + previewWidth + gap, contentY, sideWidth, 20),
                new Rect(x + padding + previewWidth + gap, contentY + Math.max(0, contentHeight - 18), sideWidth, 18),
                new Rect(buttonX, footerY, buttonWidth, buttonHeight),
                new Rect(buttonX + buttonWidth + gap, footerY, buttonWidth, buttonHeight),
                new Rect(buttonX + 2 * (buttonWidth + gap), footerY, Math.max(1, insideWidth - 2 * (buttonWidth + gap)), buttonHeight),
                padding, capacity, paged, content, panelHeight >= 200);
    }
}
