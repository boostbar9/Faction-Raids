package com.devfarinsky.siegeoverhaul.client;

/** Physical GUI-coordinate bounds including the native tooltip's four-pixel border. */
record CoreTooltipLayout(float x, float y, float width, float height, float scale) {
    static float scale(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight) {
        return Math.min(1f, Math.min(Math.max(1, viewportWidth - 12) / (float) (contentWidth + 8),
                Math.max(1, viewportHeight - 12) / (float) (contentHeight + 8)));
    }
    static CoreTooltipLayout fit(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight,
                                 int anchorX, int anchorY, float scale) {
        float width = (contentWidth + 8) * scale, height = (contentHeight + 8) * scale;
        float x = anchorX + 12 + width <= viewportWidth - 6 ? anchorX + 12 : anchorX - 12 - width;
        x = Math.max(6, Math.min(viewportWidth - width - 6, x));
        float y = Math.max(6, Math.min(viewportHeight - height - 6, anchorY - 12));
        return new CoreTooltipLayout(x, y, width, height, scale);
    }
}
