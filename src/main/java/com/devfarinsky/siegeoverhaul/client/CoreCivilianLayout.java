package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHireLayout;

/** Resident list/detail and recruitment stay in distinct responsive bands. */
record CoreCivilianLayout(CoreHireLayout frame) {
    int x() { return frame.x() + 10; }
    int y() { return frame.contentY(); }
    int width() { return frame.width() - 20; }
    boolean split() { return !frame.compact(); }
    int bodyY() { return y() + 26; }
    int recruitY() { return frame.contentBottom() - 20; }
    int navigationY() { return recruitY() - 22; }
    int bodyHeight() { return navigationY() - bodyY() - 4; }
    int listWidth() { return split() ? width() * 2 / 5 : width(); }
    int detailX() { return x() + listWidth() + 6; }
    int detailWidth() { return width() - listWidth() - 6; }
    int rows() { return split() ? Math.max(1, bodyHeight() / 38) : 1; }
    int rowHeight() { return split() ? 36 : bodyHeight(); }
    int rowY(int row) { return bodyY() + row * 38; }
    int pages(int count) { return Math.max(1, (count + rows() - 1) / rows()); }
}
