package com.devfarinsky.siegeoverhaul.core;

/** One bounded content area shared by Building widgets, drawing and hit tests. */
public record CoreBuildingLayout(CoreHireLayout frame) {
    public static final int SECTION_HEIGHT = 18;
    public static final int GAP = 4;
    public static final int ACTION_HEIGHT = 20;

    public int x() { return frame.x() + frame.outerMargin(); }
    public int width() { return frame.width() - frame.outerMargin() * 2; }
    public int sectionY() { return frame.contentY(); }
    public int sectionWidth() { return (width() - GAP * 2) / 3; }
    public int sectionX(int section) { return x() + section * (sectionWidth() + GAP); }
    public int bodyY() { return sectionY() + SECTION_HEIGHT + 6; }
    public int bottom() { return frame.contentBottom(); }
    public int bodyHeight() { return bottom() - bodyY(); }
    public int actionY() { return bottom() - ACTION_HEIGHT; }

    /** The desktop detail panel is omitted before it could squeeze the catalogue. */
    public boolean detailedCatalogue() { return width() >= 620 && bodyHeight() >= 190; }
    public int detailWidth() { return detailedCatalogue() ? Math.max(210, width() / 3) : 0; }
    public int detailX() { return x() + width() - detailWidth(); }
    public int catalogueWidth() { return width() - (detailedCatalogue() ? detailWidth() + 8 : 0); }
    /** A third column also makes room for illustrations at ordinary 720p/GUI-scale-two sizes. */
    public int catalogueColumns() {
        return detailedCatalogue() || width() >= 480 && bodyHeight() >= 206 ? 3 : 2;
    }
    public int catalogueRows() { return 6 / catalogueColumns(); }
    public int planWidth() { return (catalogueWidth() - GAP * (catalogueColumns() - 1)) / catalogueColumns(); }
    public int planHeight() {
        int available = (detailedCatalogue() ? bottom() : actionY() - 6) - bodyY();
        return (available - GAP * (catalogueRows() - 1)) / catalogueRows();
    }
    public int planX(int plan) { return x() + plan % catalogueColumns() * (planWidth() + GAP); }
    public int planY(int plan) { return bodyY() + plan / catalogueColumns() * (planHeight() + GAP); }
    public int planActionX() { return detailedCatalogue() ? detailX() + 8 : x(); }
    public int planActionWidth() { return detailedCatalogue() ? detailWidth() - 16 : width(); }

    public int materialY() { return bodyY() + 32; }
    public int materialWidth(int count) { return (width() - GAP * (count - 1)) / count; }
    public int materialX(int material, int count) { return x() + material * (materialWidth(count) + GAP); }
    public int perimeterTextY() { return materialY() + ACTION_HEIGHT + 7; }
    public int perimeterTextLines() {
        return illustratedPerimeter() ? 3 : Math.max(0, (actionY() - 4 - perimeterTextY()) / 10);
    }
    public boolean illustratedPerimeter() { return width() >= 480 && perimeterExampleHeight() >= 94; }
    public int perimeterExampleY() { return perimeterTextY() + 34; }
    public int perimeterExampleHeight() { return Math.max(0, actionY() - 6 - perimeterExampleY()); }

    public int reportHeaderY() { return bodyY(); }
    public int reportY() { return bodyY() + 14; }
    public int reportRowHeight() { return bodyHeight() < 180 ? 64 : 74; }
    public int reportRows() { return Math.max(1, (actionY() - 4 - reportY()) / reportRowHeight()); }
    public int reportRowY(int row) { return reportY() + row * reportRowHeight(); }
    public int reportPages(int jobs) { return Math.max(1, (Math.max(0, jobs) + reportRows() - 1) / reportRows()); }
    public int reportNavigationWidth() { return (width() - GAP) / 2; }
}
