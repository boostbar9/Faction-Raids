package com.devfarinsky.siegeoverhaul.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Pure, bounded elevation proposal for one already-bounded level region, such as a local gate pad.
 * This is not world/provenance/permission admission or permission to excavate.
 * Live construction deliberately does not call this staged planner yet.
 */
public final class PerimeterGradePlane {
    public static final int MAX_COLUMNS = 20_480;
    private static final int WORLD_LIMIT = 30_000_000;
    private PerimeterGradePlane() {}

    public record Surface(int x, int z, int firstAirY) {}
    public record Column(int x, int z, int originalSurfaceY, int baseY) {
        public int cutDepth() { return Math.max(0, originalSurfaceY - baseY); }
        public int fillDepth() { return Math.max(0, baseY - originalSurfaceY); }
    }
    public record Limits(int maxCutDepth, int maxFillDepth, long maxCutCells, long maxFillCells,
                         int minBuildHeight, int maxBuildHeight, int wallClearHeight) {
        public static final Limits PRESERVE = new Limits(0, 8, 0, 32_768, -64, 320, 6);
        public static final Limits REVIEWED_CUT_FILL = new Limits(4, 8, 4_096, 32_768, -64, 320, 6);
        public Limits {
            if (maxCutDepth < 0 || maxCutDepth > 4 || maxFillDepth < 0 || maxFillDepth > 8
                    || maxCutCells < 0 || maxCutCells > 4_096 || maxFillCells < 0 || maxFillCells > 32_768
                    || minBuildHeight < -2_048 || maxBuildHeight > 2_048 || minBuildHeight >= maxBuildHeight
                    || wallClearHeight < 1 || wallClearHeight > 64)
                throw new IllegalArgumentException("Invalid bounded grading limits");
        }
    }
    public enum Problem { NONE, EMPTY, COLUMN_LIMIT, INVALID_SURFACE, DUPLICATE_COLUMN, RELIEF, BUDGET }
    public record Selection(List<Column> columns, int baseY, long cutCells, long fillCells, Problem problem) {
        public Selection { columns = List.copyOf(columns); Objects.requireNonNull(problem); }
        public boolean ready() { return problem == Problem.NONE && !columns.isEmpty(); }
    }

    /**
     * Keep the highest feasible plane to minimize removal; within that plane every count is exact.
     * At most thirteen candidate elevations and MAX_COLUMNS read-only input records are examined.
     * Does not sample the world, classify a block as natural, modify the input or change any old plan.
     */
    public static Selection select(List<Surface> input, Limits limits) {
        Objects.requireNonNull(input); Objects.requireNonNull(limits);
        if (input.isEmpty()) return failed(Problem.EMPTY);
        if (input.size() > MAX_COLUMNS) return failed(Problem.COLUMN_LIMIT);
        var seen = new HashSet<Long>();
        int lowSurface = Integer.MAX_VALUE, highSurface = Integer.MIN_VALUE;
        List<Surface> ordered = new ArrayList<>(input.size());
        for (Surface surface : input) {
            if (surface == null || surface.x() < -WORLD_LIMIT || surface.x() >= WORLD_LIMIT
                    || surface.z() < -WORLD_LIMIT || surface.z() >= WORLD_LIMIT
                    || surface.firstAirY() <= limits.minBuildHeight() || surface.firstAirY() >= limits.maxBuildHeight())
                return failed(Problem.INVALID_SURFACE);
            long key = ((long) surface.x() << 32) ^ (surface.z() & 0xffffffffL);
            if (!seen.add(key)) return failed(Problem.DUPLICATE_COLUMN);
            lowSurface = Math.min(lowSurface, surface.firstAirY());
            highSurface = Math.max(highSurface, surface.firstAirY());
            ordered.add(surface);
        }
        ordered.sort(Comparator.comparingInt(Surface::x).thenComparingInt(Surface::z));
        int low = Math.max(highSurface - limits.maxCutDepth(), limits.minBuildHeight() + 1);
        int high = Math.min(Math.min(lowSurface + limits.maxFillDepth(), highSurface),
                limits.maxBuildHeight() - limits.wallClearHeight());
        if (low > high) return failed(Problem.RELIEF);
        for (int base = high; base >= low; base--) {
            long cuts = 0, fills = 0;
            for (Surface surface : ordered) {
                cuts += Math.max(0, surface.firstAirY() - base);
                fills += Math.max(0, base - surface.firstAirY());
            }
            if (cuts > limits.maxCutCells() || fills > limits.maxFillCells()) continue;
            List<Column> columns = new ArrayList<>(ordered.size());
            for (Surface surface : ordered) columns.add(new Column(surface.x(), surface.z(), surface.firstAirY(), base));
            return new Selection(columns, base, cuts, fills, Problem.NONE);
        }
        return failed(Problem.BUDGET);
    }

    private static Selection failed(Problem problem) { return new Selection(List.of(), 0, 0, 0, problem); }
}
