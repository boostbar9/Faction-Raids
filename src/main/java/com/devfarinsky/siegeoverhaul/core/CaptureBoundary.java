package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded, read-only terrain projection of the actual horizontal capture boundary. */
public final class CaptureBoundary {
    public static final int SEGMENTS = 128, REFRESH_TICKS = 10, APPROACH = 12;
    private CaptureBoundary() {}
    public record Segment(Vec3 from, Vec3 to, boolean marker) {}
    @FunctionalInterface public interface Surface { double height(double x, double z); }

    public static List<Segment> sample(BlockPos core, int radius, Surface surface) {
        if (radius < 2 || radius > 32) return List.of();
        Vec3[] points = new Vec3[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            double x = core.getX() + .5 + Math.cos(angle) * radius;
            double z = core.getZ() + .5 + Math.sin(angle) * radius;
            double y = surface.height(x, z);
            if (Double.isFinite(y)) points[i] = new Vec3(x, y + .045, z);
        }
        List<Segment> result = new ArrayList<>();
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 a = points[i], b = points[(i + 1) % SEGMENTS];
            // Never bridge missing chunks, cliffs or separate floors.
            if (a != null && b != null && Math.abs(a.y - b.y) <= 1.05)
                result.add(new Segment(a, b, i % 8 == 0));
        }
        return List.copyOf(result);
    }

    /** Highest collision surface at this exact x/z within the server's feet-height tolerance. */
    public static double surface(LevelReader level, BlockPos core, int vertical, double x, double z) {
        if (vertical < 1 || vertical > 16 || !Double.isFinite(x) || !Double.isFinite(z)) return Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        double bottom = core.getY() + .5 - vertical, top = core.getY() + .5 + vertical;
        for (int y = Mth.floor(top); y >= Mth.floor(bottom) - 1; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double best = Double.NaN;
            for (var box : shape.toAabbs()) {
                if (x - bx < box.minX || x - bx > box.maxX || z - bz < box.minZ || z - bz > box.maxZ) continue;
                double height = y + box.maxY;
                if (height >= bottom && height <= top && (!Double.isFinite(best) || height > best)) best = height;
            }
            if (Double.isFinite(best)) return best;
            // An out-of-height ceiling is not a floor, but may cover a valid room.
            // Keep scanning; normal depth testing hides the interior stroke from above.
        }
        return Double.NaN;
    }

    /** Sight reads must stay inside already loaded columns, including intermediate chunks. */
    public static boolean loadedSight(LevelReader level, BlockPos core, Vec3 feet) {
        int minX = Math.min(core.getX(), Mth.floor(feet.x)) >> 4;
        int maxX = Math.max(core.getX(), Mth.floor(feet.x)) >> 4;
        int minZ = Math.min(core.getZ(), Mth.floor(feet.z)) >> 4;
        int maxZ = Math.max(core.getZ(), Mth.floor(feet.z)) >> 4;
        // Callers only need rays inside a maximum 32-block radius.
        if (maxX - minX > 4 || maxZ - minZ > 4) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!level.hasChunk(x, z)) return false;
        return true;
    }
}
