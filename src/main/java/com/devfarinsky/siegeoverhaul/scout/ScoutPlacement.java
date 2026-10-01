package com.devfarinsky.siegeoverhaul.scout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Bounded, read-only terrain selection for scout parties and their lookout. */
final class ScoutPlacement {
    private static final double[] ANGLE_OFFSETS = {
            0.0, Math.PI / 12.0, -Math.PI / 12.0,
            Math.PI / 6.0, -Math.PI / 6.0,
            Math.PI / 4.0, -Math.PI / 4.0,
            Math.PI / 2.0, -Math.PI / 2.0
    };
    private static final int[][] PARTY_OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {2, 1}, {-2, 1}, {2, -1}, {-2, -1},
            {1, 2}, {-1, 2}, {1, -2}, {-1, -2}
    };

    private ScoutPlacement() {}

    /** Prefer the configured approach, then nearby angles and distances without loading chunks. */
    static BlockPos findSpawn(ServerLevel level, BlockPos anchor, int distance, double preferredAngle) {
        int inner = Math.max(16, distance - 12);
        int[] radii = {distance, inner, distance + 12};
        for (int radius : radii) {
            for (double offset : ANGLE_OFFSETS) {
                int x = anchor.getX() + (int) Math.round(Math.cos(preferredAngle + offset) * radius);
                int z = anchor.getZ() + (int) Math.round(Math.sin(preferredAngle + offset) * radius);
                BlockPos candidate = accessibleSurface(level, x, z, anchor.getY(), 48);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    /**
     * Keep the lookout on the incoming side of the objective. This avoids asking
     * scouts to cross the defended base merely because a roof or hill is taller.
     */
    static BlockPos findLookout(ServerLevel level, BlockPos objective, BlockPos spawn, int radius) {
        double incomingAngle = Math.atan2(spawn.getZ() - objective.getZ(), spawn.getX() - objective.getX());
        int inner = Math.max(16, radius - 8);
        int[] radii = {radius, inner, radius + 8};
        for (int sampleRadius : radii) {
            for (double offset : ANGLE_OFFSETS) {
                int x = objective.getX() + (int) Math.round(Math.cos(incomingAngle + offset) * sampleRadius);
                int z = objective.getZ() + (int) Math.round(Math.sin(incomingAngle + offset) * sampleRadius);
                BlockPos candidate = accessibleSurface(level, x, z, objective.getY(), 48);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    /** Re-sample Y for every party member; never reuse the center's height on a slope. */
    static List<BlockPos> partyPositions(ServerLevel level, BlockPos center, int requested) {
        List<BlockPos> result = new ArrayList<>(Math.max(0, requested));
        for (int[] offset : PARTY_OFFSETS) {
            if (result.size() >= requested) break;
            BlockPos candidate = accessibleSurface(level, center.getX() + offset[0], center.getZ() + offset[1],
                    center.getY(), 2);
            if (candidate != null && !result.contains(candidate)) result.add(candidate);
        }
        return result;
    }

    /** Package-visible for focused terrain regression tests. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.hasChunkAt(column)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        double halfWidth = EntityType.PILLAGER.getWidth() / 2.0;
        AABB scoutBody = new AABB(x + 0.5 - halfWidth, y, z + 0.5 - halfWidth,
                x + 0.5 + halfWidth, y + EntityType.PILLAGER.getHeight(), z + 0.5 + halfWidth);
        var border = level.getWorldBorder();
        if (scoutBody.minY < level.getMinBuildHeight() || scoutBody.maxY > level.getMaxBuildHeight()
                || scoutBody.minX < border.getMinX() || scoutBody.maxX > border.getMaxX()
                || scoutBody.minZ < border.getMinZ() || scoutBody.maxZ > border.getMaxZ()) return null;
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        // Use block shape classes rather than data-pack tags: placement can run
        // during early world startup, and a trunk top is never a useful route
        // even if another pack has not finished binding its log tags yet.
        if (floor.getBlock() instanceof RotatedPillarBlock || floor.getBlock() instanceof LeavesBlock
                || dangerous(floor)
                || !floor.getFluidState().isEmpty()
                || !floor.isFaceSturdy(level, floorPos, Direction.UP)) return null;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos bodyPos = feet.above(dy);
            BlockState body = level.getBlockState(bodyPos);
            if (dangerous(body) || !body.getFluidState().isEmpty()
                    || !body.getCollisionShape(level, bodyPos).isEmpty()) return null;
        }
        return feet;
    }

    private static BlockPos accessibleSurface(ServerLevel level, int x, int z, int referenceY, int maxDelta) {
        BlockPos feet = surface(level, x, z);
        if (feet == null || Math.abs(feet.getY() - referenceY) > maxDelta) return null;
        // A valid column also needs an adjacent step. This rejects tree trunks,
        // isolated pillars and cliff tips that a scout could spawn on but not leave.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = surface(level, x + direction.getStepX(), z + direction.getStepZ());
            if (neighbor != null && Math.abs(neighbor.getY() - feet.getY()) <= 1) return feet;
        }
        return null;
    }

    private static boolean dangerous(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
}
