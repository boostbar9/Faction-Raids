package com.devfarinsky.siegeoverhaul.client;

/** Pure logical-pixel geometry shared by settings rendering, hit testing and navigation. */
final class ConfigScreenLayout {
    record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    final Bounds window;
    final Bounds filter;
    final Bounds viewport;
    final Bounds scrollbar;
    final int footerY;
    final int rowHeight;
    final boolean stacked;

    private ConfigScreenLayout(int width, int height) {
        int margin = width < 240 || height < 180 ? 4 : 8;
        int w = Math.max(1, Math.min(720, width - margin * 2));
        int h = Math.max(1, height - margin * 2);
        window = new Bounds((width - w) / 2, margin, w, h);
        int inset = Math.min(12, Math.max(2, w / 20));
        int contentWidth = Math.max(1, w - inset * 2);
        filter = new Bounds(window.x + inset, window.y + 32, contentWidth, 20);
        footerY = window.bottom() - 28;
        int top = filter.bottom() + 20;
        int bottom = Math.max(top, footerY - 18);
        viewport = new Bounds(filter.x, top, Math.max(1, contentWidth - 10), bottom - top);
        scrollbar = new Bounds(viewport.right() + 4, top, 5, viewport.height);
        stacked = w < 420;
        rowHeight = stacked ? 42 : 32;
    }

    static ConfigScreenLayout fit(int width, int height) { return new ConfigScreenLayout(width, height); }
    int capacity() { return viewport.height / rowHeight; }
    int maxFirst(int count) { return Math.max(0, count - capacity()); }
    int clampFirst(int first, int count) { return Math.max(0, Math.min(maxFirst(count), first)); }
    int ensureVisible(int first, int index, int count) {
        if (capacity() == 0) return 0;
        if (index < first) return clampFirst(index, count);
        if (index >= first + capacity()) return clampFirst(index - capacity() + 1, count);
        return clampFirst(first, count);
    }
    Bounds row(int slot) {
        return new Bounds(viewport.x, viewport.y + slot * rowHeight, viewport.width, rowHeight);
    }
    Bounds label(int slot) {
        Bounds row = row(slot);
        return new Bounds(row.x + 4, row.y + (stacked ? 3 : 11),
                stacked ? row.width - 8 : Math.max(1, row.width - input(slot).width - 16), 9);
    }
    Bounds input(int slot) {
        Bounds row = row(slot);
        int w = stacked ? Math.max(1, row.width - 8) : Math.max(1, Math.min(280, row.width * 43 / 100));
        return new Bounds(stacked ? row.x + 4 : row.right() - w - 4,
                row.y + (stacked ? 16 : 6), w, 20);
    }
    boolean fitsRow(int slot) {
        Bounds row = row(slot);
        return slot >= 0 && row.y >= viewport.y && row.bottom() <= viewport.bottom();
    }
    Bounds thumb(int first, int count) {
        int h = Math.min(viewport.height, Math.max(16, viewport.height * capacity() / Math.max(1, count)));
        int y = viewport.y + (viewport.height - h) * clampFirst(first, count) / Math.max(1, maxFirst(count));
        return new Bounds(scrollbar.x, y, scrollbar.width, h);
    }
    int firstAtThumb(double top, int count) {
        Bounds thumb = thumb(0, count);
        int travel = viewport.height - thumb.height;
        if (travel <= 0) return 0;
        return clampFirst((int) Math.round((top - viewport.y) * maxFirst(count) / travel), count);
    }
}
