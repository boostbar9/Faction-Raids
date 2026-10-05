package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHireLayout;

/** Read-only rewards get their own bounded page, never the purchase-button band. */
record CoreLootGalleryLayout(CoreHireLayout frame) {
    int x() { return frame.x() + 10; }
    int y() { return frame.contentY(); }
    int width() { return frame.width() - 20; }
    int tierY() { return y() + 22; }
    int gridY() { return y() + 44; }
    int actionY() { return frame.contentBottom() - 18; }
    int columns() { return Math.max(2, Math.min(4, width() / 130)); }
    int rows() { return actionY() - gridY() >= 148 ? 2 : 1; }
    int pageSize() { return columns() * rows(); }
    int cardWidth() { return (width() - (columns() - 1) * 4) / columns(); }
    int cardHeight() { return (actionY() - gridY() - 4 - (rows() - 1) * 4) / rows(); }
    int cardX(int index) { return x() + index % columns() * (cardWidth() + 4); }
    int cardY(int index) { return gridY() + index / columns() * (cardHeight() + 4); }
    int pages(int count) { return Math.max(1, (count + pageSize() - 1) / pageSize()); }
}
