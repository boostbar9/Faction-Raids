package com.devfarinsky.factionraids.naval;

import com.devfarinsky.factionraids.RaidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects an open-water staging point near a raid objective.
 *
 * A staging point is a water surface block (open sky above, water below) that
 * is part of a body of at least {@link RaidConfig#NAVAL_MIN_WATER_BODY} contiguous
 * water blocks, and lies within {@link RaidConfig#NAVAL_STAGING_RADIUS} of the
 * objective. The result is deterministic per objective: we scan the same ring
 * of candidate positions and return the first one that meets the size floor.
 *
 * Rationale: raids should feel amphibious only when there is actually enough
 * water for a small flotilla to matter. A one-block puddle or a shallow river
 * ford should not force boat spawns.
 */
public final class NavalStagingScanner {

    private NavalStagingScanner() {}

    /** Result of a staging scan; never null, but {@link #found()} may be false. */
    public record NavalStaging(BlockPos surface, BlockPos beach, double approachAngle, boolean found) {
        public static NavalStaging none() {
            return new NavalStaging(null, null, 0.0, false);
        }
    }

    /**
     * Search for a naval staging point. Returns {@link NavalStaging#none()} when
     * amphibious raids are disabled or no qualifying water body exists near the
     * objective.
     */
    public static NavalStaging scan(ServerLevel level, BlockPos objective) {
        if (!RaidConfig.ENABLE_AMPHIBIOUS_RAIDS.get()) return NavalStaging.none();

        int radius = RaidConfig.NAVAL_STAGING_RADIUS.get();
        int minBody = RaidConfig.NAVAL_MIN_WATER_BODY.get();
        // Candidate ring: sample 24 angles around the objective at the search
        // radius, walk inward until we hit water. First hit whose body is big
        // enough wins. Cheap and deterministic \u2014 no per-tick chunk scanning.
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0) * i / 24.0;
            int x = objective.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = objective.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos surface = topWaterAt(level, x, z);
            if (surface == null) continue;

            int size = floodFillSize(level, surface, minBody);
            if (size < minBody) continue;

            // Beach: the closest solid ground on the objective-side shore.
            BlockPos beach = findBeach(level, surface, objective);
            if (beach == null) continue;

            double approach = Math.atan2(objective.getZ() - surface.getZ(),
                                         objective.getX() - surface.getX());
            return new NavalStaging(surface, beach, approach, true);
        }
        return NavalStaging.none();
    }

    /** Top-most water surface column at (x, z), or null if that column is dry. */
    private static BlockPos topWaterAt(ServerLevel level, int x, int z) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        // Walk down a few blocks in case the heightmap-top is a leaf/plant.
        for (int dy = 0; dy < 6; dy++) {
            BlockPos p = new BlockPos(x, topY - dy, z);
            BlockState state = level.getBlockState(p);
            if (state.getFluidState().getType() == Fluids.WATER) {
                // Only return if the block above is air or non-solid \u2014 boats need sky.
                BlockPos above = p.above();
                if (level.getBlockState(above).isAir()) return p;
            }
        }
        return null;
    }

    /**
     * Bounded flood-fill on water blocks. Stops early once the count reaches
     * {@code target}, so worst case cost is proportional to the size floor,
     * not the whole ocean.
     */
    private static int floodFillSize(Level level, BlockPos start, int target) {
        Set<Long> visited = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());
        int count = 0;
        while (!queue.isEmpty() && count < target) {
            BlockPos p = queue.poll();
            if (level.getBlockState(p).getFluidState().getType() != Fluids.WATER) continue;
            count++;
            for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}) {
                BlockPos n = p.offset(d[0], d[1], d[2]);
                if (visited.add(n.asLong())) queue.add(n);
            }
        }
        return count;
    }

    /**
     * Walk from the staging point toward the objective one block at a time; the
     * first solid, non-water block whose top is walkable is the beach. Returns
     * null if we run out of search distance without finding shore.
     */
    private static BlockPos findBeach(ServerLevel level, BlockPos surface, BlockPos objective) {
        Vec3 dir = Vec3.atCenterOf(objective).subtract(Vec3.atCenterOf(surface)).normalize();
        for (int step = 0; step < 32; step++) {
            int nx = surface.getX() + (int) Math.round(dir.x * step);
            int nz = surface.getZ() + (int) Math.round(dir.z * step);
            int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
            BlockPos landing = new BlockPos(nx, ny, nz);
            BlockState below = level.getBlockState(landing.below());
            boolean landIsSolid = !below.isAir()
                    && below.getFluidState().isEmpty()
                    && below.getBlock() != Blocks.WATER;
            boolean standCleared = level.getBlockState(landing).isAir()
                    && level.getBlockState(landing.above()).isAir();
            if (landIsSolid && standCleared) return landing;
        }
        return null;
    }
}
