package com.devfarinsky.siegeoverhaul.core;

/** One bounded content area shared by Building widgets, drawing and hit tests. */
public record CoreBuildingLayout(CoreHireLayout frame) {
    public static final int SECTION_HEIGHT = 18;
    public static final int GAP = 4;
    public static final int ACTION_HEIGHT = 20;

    public int x() { return frame.x() + frame.outerMargin(); }
    public int width() { return frame.width() - frame.outerMargin() * 2; }
    // Building already names its current section; reclaim the duplicate roomy page header.
    public int sectionY() { return frame.pageHeaderY(); }
    public int sectionWidth() { return Math.min(148, (width() - GAP * 2) / 3); }
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
        return detailedCatalogue() || width() >= 410 && bodyHeight() >= 206 ? 3 : 2;
    }
    /** Page the catalogue before live block models collapse into labels or material icons. */
    public int catalogueRows() {
        int available = (detailedCatalogue() ? bottom() : actionY() - 6) - bodyY();
        return Math.max(1, Math.min(6 / catalogueColumns(), (available + GAP) / (72 + GAP)));
    }
    public int plansPerPage() { return catalogueColumns() * catalogueRows(); }
    public int cataloguePages() { return (6 + plansPerPage() - 1) / plansPerPage(); }
    public boolean pagedCatalogue() { return cataloguePages() > 1; }
    public int planWidth() { return (catalogueWidth() - GAP * (catalogueColumns() - 1)) / catalogueColumns(); }
    public int planHeight() {
        int available = (detailedCatalogue() ? bottom() : actionY() - 6) - bodyY();
        return (available - GAP * (catalogueRows() - 1)) / catalogueRows();
    }
    public int planX(int plan) { return x() + plan % catalogueColumns() * (planWidth() + GAP); }
    public int planY(int plan) { return bodyY() + (plan % plansPerPage()) / catalogueColumns() * (planHeight() + GAP); }
    public int planActionX() { return detailedCatalogue() ? detailX() + 8 : x() + (pagedCatalogue() ? 32 : 0); }
    public int planActionWidth() { return detailedCatalogue() ? detailWidth() - 16 : width() - (pagedCatalogue() ? 64 : 0); }

    /** A detail card needs room for full native-font labels and the free-review explanation. */
    public boolean splitPerimeter() { return width() >= 580 && bodyHeight() >= 220; }
    public int perimeterDetailWidth() { return splitPerimeter() ? Math.max(246, width() * 2 / 5) : 0; }
    public int perimeterDetailX() { return x() + width() - perimeterDetailWidth(); }
    public int perimeterPreviewWidth() { return splitPerimeter() ? width() - perimeterDetailWidth() - 10 : width(); }
    public boolean stackedMaterials() { return splitPerimeter() && perimeterDetailWidth() < 280; }
    public int materialY() { return splitPerimeter() ? bodyY() + 89 : bodyY() + 32; }
    public int materialY(int material) { return materialY() + (stackedMaterials() ? material * (ACTION_HEIGHT + GAP) : 0); }
    public int materialWidth(int count) {
        int area = splitPerimeter() ? perimeterDetailWidth() - 20 : width();
        return stackedMaterials() ? area : (area - GAP * (count - 1)) / count;
    }
    public int materialX(int material, int count) {
        int left = splitPerimeter() ? perimeterDetailX() + 10 : x();
        return left + (stackedMaterials() ? 0 : material * (materialWidth(count) + GAP));
    }
    public int perimeterActionX() { return splitPerimeter() ? perimeterDetailX() + 10 : x(); }
    public int perimeterActionY() { return splitPerimeter() ? bottom() - 50 : actionY(); }
    public int perimeterActionWidth() { return splitPerimeter() ? perimeterDetailWidth() - 20 : width(); }
    public int perimeterTextY() { return splitPerimeter() ? perimeterActionY() + 28 : materialY() + ACTION_HEIGHT + 7; }
    public int perimeterTextLines() {
        return splitPerimeter() ? 2 : illustratedPerimeter() ? 3
                : Math.max(0, (actionY() - 4 - perimeterTextY()) / 10);
    }
    public boolean illustratedPerimeter() { return splitPerimeter() || width() >= 480 && perimeterExampleHeight() >= 94; }
    public int perimeterExampleY() { return splitPerimeter() ? bodyY() + 34 : perimeterTextY() + 34; }
    public int perimeterExampleHeight() {
        return Math.max(0, (splitPerimeter() ? bottom() - 24 : actionY() - 6) - perimeterExampleY());
    }

    public int reportHeaderY() { return bodyY(); }
    public int reportY() { return bodyY() + 14; }
    public boolean splitReport() { return width() >= 580 && bodyHeight() >= 220; }
    public int reportDetailWidth() { return splitReport() ? Math.max(224, width() * 2 / 5) : 0; }
    public int reportMainWidth() { return splitReport() ? width() - reportDetailWidth() - 10 : width(); }
    public int reportDetailX() { return x() + width() - reportDetailWidth(); }
    public int reportRowHeight() { return splitReport() ? 108 : bodyHeight() < 180 ? 64 : 74; }
    public int reportRows() { return Math.max(1, (actionY() - 4 - reportY()) / reportRowHeight()); }
    public int reportRowY(int row) { return reportY() + row * reportRowHeight(); }
    public int reportPages(int jobs) { return Math.max(1, (Math.max(0, jobs) + reportRows() - 1) / reportRows()); }
    public int reportNavigationWidth() { return (width() - GAP) / 2; }
    /** One selected whole project leaves a single unambiguous cancel target, including on small screens. */
    public int reportProjectHeight() { return actionY() - reportY() - 6; }
    public int reportCancelNavigationWidth() { return Math.min(70, Math.max(30, width() / 8)); }
    public int reportCancelX() { return splitReport() ? reportDetailX() + 10 : x() + reportCancelNavigationWidth() + GAP; }
    public int reportCancelY() { return splitReport() ? reportY() + reportProjectHeight() - 40 : actionY(); }
    public int reportCancelWidth() { return splitReport() ? reportDetailWidth() - 20 : width() - (reportCancelNavigationWidth() + GAP) * 2; }
}
