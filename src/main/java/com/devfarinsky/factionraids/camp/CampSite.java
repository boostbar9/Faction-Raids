package com.devfarinsky.factionraids.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

/**
 * Picks a real-world position for a raider camp given the target's anchor
 * and the raiders' approach vector. Selection rules:
 *
 * <ul>
 *     <li>Distance: between {@code MIN_DISTANCE} and {@code MAX_DISTANCE}
 *         blocks from the anchor, along the approach angle</li>
 *     <li>Terrain: solid, non-water, non-lava ground at surface height</li>
 *     <li>Flatness: sampled 3x3 grid must be within {@code MAX_HEIGHT_DELTA}
 *         blocks of the center — otherwise we jitter and re-sample</li>
 *     <li>Fallback: after {@code MAX_ATTEMPTS} we return the best partial
 *         match so the raid always has SOMETHING to work with</li>
 * </ul>
 *
 * <p>This is deliberately stateless and thread-safe — it may be called from
 * the raid tick or from an admin command with equal safety.
 */
public final class CampSite {

    private static final int MIN_DISTANCE = 32;
    private static final int MAX_DISTANCE = 64;
    private static final int MAX_HEIGHT_DELTA = 3;
    private static final int MAX_ATTEMPTS = 12;

    private CampSite() {}

    /**
     * Chooses a camp center for the raid.
     *
     * @param level         world to build in
     * @param targetAnchor  the raid target position (the defender's anchor)
     * @param approachAngle angle in radians the raiders come from
     * @param footprint     camp footprint size — used to check flatness
     * @return the chosen center, or empty if no plausible site was found
     */
    public static Optional<BlockPos> choose(ServerLevel level, BlockPos targetAnchor,
                                             double approachAngle, CampBlueprint.Size footprint) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double distance = MIN_DISTANCE + (MAX_DISTANCE - MIN_DISTANCE) * (attempt / (double) MAX_ATTEMPTS);
            double angle = approachAngle + (attempt % 3 - 1) * 0.15D; // small jitter
            int dx = (int) Math.round(Math.cos(angle) * distance);
            int dz = (int) Math.round(Math.sin(angle) * distance);
            BlockPos candidate = surfaceAt(level, targetAnchor.getX() + dx, targetAnchor.getZ() + dz);
            if (candidate == null) continue;
            int score = scoreFlatness(level, candidate, footprint);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                if (score >= flatnessTarget(footprint)) return Optional.of(candidate);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Highest non-fluid surface at the given XZ. Returns null if the column
     * is fully liquid or in the void.
     */
    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (y <= level.getMinBuildHeight()) return null;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().getType() != Fluids.EMPTY) return null;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return pos.above();
        return pos.above();
    }

    /**
     * Score a candidate by how flat the 3x3 grid of samples across the camp
     * footprint is. Higher is better; the max is 0 (perfectly flat).
     */
    private static int scoreFlatness(ServerLevel level, BlockPos center, CampBlueprint.Size footprint) {
        int halfX = Math.max(3, footprint.width() / 2);
        int halfZ = Math.max(3, footprint.depth() / 2);
        int centerY = center.getY();
        int worstDelta = 0;
        for (int dx = -halfX; dx <= halfX; dx += halfX) {
            for (int dz = -halfZ; dz <= halfZ; dz += halfZ) {
                BlockPos sample = surfaceAt(level, center.getX() + dx, center.getZ() + dz);
                if (sample == null) return Integer.MIN_VALUE + 1;
                worstDelta = Math.max(worstDelta, Math.abs(sample.getY() - centerY));
            }
        }
        return -worstDelta;
    }

    /** Anything closer than this is "flat enough" and short-circuits the search. */
    private static int flatnessTarget(CampBlueprint.Size footprint) {
        // Smaller camps can be pickier; larger camps take what they get.
        return footprint.width() <= 9 ? -1 : -MAX_HEIGHT_DELTA;
    }

    /** True when the given world can host a camp (not the End, not the void). */
    public static boolean canHostCamp(Level level) {
        return level != null && !level.dimension().location().getPath().equals("the_end");
    }
}
