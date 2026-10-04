package com.devfarinsky.siegeoverhaul.client;

/** Logical-pixel bounds shared by Codex painting, widgets and regression checks. */
public record SiegeCommandLayout(Rect panel, Rect header, Rect navigation, Rect body, Rect footer) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    public static SiegeCommandLayout fit(int viewportWidth, int viewportHeight) {
        int width = Math.max(120, viewportWidth), height = Math.max(120, viewportHeight);
        int panelWidth = Math.min(620, width - 16), panelHeight = Math.min(360, height - 16);
        int x = (width - panelWidth) / 2, y = (height - panelHeight) / 2;
        int inside = panelWidth - 16;
        int footerY = y + panelHeight - 28;
        return new SiegeCommandLayout(new Rect(x, y, panelWidth, panelHeight),
                new Rect(x, y, panelWidth, 34), new Rect(x + 8, y + 38, inside, 20),
                new Rect(x + 8, y + 64, inside, Math.max(1, footerY - y - 70)),
                new Rect(x + 8, footerY, inside, 20));
    }

    public Rect tab(int index) { return segment(navigation, index, 3); }
    public Rect action(int index) { return segment(footer, index, 4); }
    public Rect previous() { return new Rect(body.x() + 6, body.bottom() - 22, Math.min(88, (body.width() - 24) / 3), 18); }
    public Rect next() { var previous = previous(); return new Rect(body.right() - 6 - previous.width(), previous.y(), previous.width(), previous.height()); }
    public Rect guideViewport() { return new Rect(body.x() + 10, body.y() + 22, Math.max(1, body.width() - 20), Math.max(1, body.height() - 50)); }
    public int journalRows() { return Math.max(1, (body.height() - 28) / 44); }
    public int journalPages(int count) { return Math.max(1, (Math.max(0, count) + journalRows() - 1) / journalRows()); }

    private static Rect segment(Rect rect, int index, int count) {
        int gap = 4, start = index * (rect.width() + gap) / count;
        int end = (index + 1) * (rect.width() + gap) / count - gap;
        return new Rect(rect.x() + start, rect.y(), Math.max(1, end - start), rect.height());
    }
}
