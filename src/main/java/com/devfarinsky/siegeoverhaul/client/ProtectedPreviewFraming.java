package com.devfarinsky.siegeoverhaul.client;

/** Initial Workers preview camera math only; actual geometry rendering stays inside its native widget. */
public final class ProtectedPreviewFraming {
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public record Frame(double zoom, double dragX, double dragY, double projectedWidth,
                        double projectedHeight, boolean fits) {}
    private static final double COS = Math.cos(Math.toRadians(25)), SIN = Math.sin(Math.toRadians(25));
    private ProtectedPreviewFraming() {}

    public static Frame initial(int viewportWidth, int viewportHeight, int areaWidth, int areaDepth, Bounds blocks) {
        // Native widget defaults: rotationX=25, rotationY=180. Its red anchor box is
        // at (areaWidth-1,0,0), size 1x2x1, inflated .01. Include it even for sparse plans.
        Bounds bounds = new Bounds(Math.min(blocks.minX(), areaWidth - 1.01), Math.min(blocks.minY(), -.01),
                Math.min(blocks.minZ(), -.01), Math.max(blocks.maxX(), areaWidth + .01),
                Math.max(blocks.maxY(), 2.01), Math.max(blocks.maxZ(), 1.01));
        double minX = areaWidth - bounds.maxX(), maxX = areaWidth - bounds.minX();
        // T(pivot) * Rx(25) * Ry(180) * T(-pivot), before native inverted-Y screen scaling.
        double minY = COS * bounds.minY() + SIN * (bounds.minZ() - areaDepth / 2.0);
        double maxY = COS * bounds.maxY() + SIN * (bounds.maxZ() - areaDepth / 2.0);
        double rangeX = Math.max(.01, maxX - minX), rangeY = Math.max(.01, maxY - minY);
        double availableX = Math.max(1, viewportWidth - 16), availableY = Math.max(1, viewportHeight - 16);
        double zoom = Math.max(3, Math.min(20, Math.min(availableX / rangeX, availableY / rangeY)));
        // Native right-drag adds dx/zoom to offsetX and subtracts dy/zoom from offsetY.
        return new Frame(zoom, -zoom * (minX + maxX) / 2, zoom * (minY + maxY) / 2,
                rangeX * zoom, rangeY * zoom, rangeX * zoom <= availableX && rangeY * zoom <= availableY);
    }
}
