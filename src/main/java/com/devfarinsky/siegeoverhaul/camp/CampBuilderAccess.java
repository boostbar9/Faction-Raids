package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded, read-only standing checks for enemy camp spawns and fallback work. */
final class CampBuilderAccess {
    private CampBuilderAccess() {}

    static BlockPos spawnPosition(ServerLevel level, RaidState raid, BlockPos near) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos column : columns(near)) {
            if (!level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (Math.abs(y - raid.campPos.getY()) > 3) continue;
            BlockPos feet = column.atY(y);
            // A conservative worker-sized envelope before the native entity exists.
            AABB body = new AABB(feet.getX() + 0.1, y, feet.getZ() + 0.1,
                    feet.getX() + 0.9, y + 2, feet.getZ() + 0.9);
            if (!safe(level, raid, feet, body, null)) continue;
            double next = feet.distSqr(raid.campPos);
            if (next < distance) { best = feet; distance = next; }
        }
        return best;
    }

    static BlockPos workPosition(ServerLevel level, RaidState raid, Mob worker, BlockPos target) {
        Set<BlockPos> choices = new LinkedHashSet<>();
        for (BlockPos column : columns(target)) for (int dy = -3; dy <= 3; dy++) {
            BlockPos feet = column.atY(raid.campPos.getY() + dy);
            if (!CampBuilder.withinReach(Vec3.atBottomCenterOf(feet), target)) continue;
            AABB body = worker.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(worker.position()));
            if (safe(level, raid, feet, body, worker)) choices.add(feet);
        }
        if (choices.isEmpty()) return null;
        // One native multi-target search, instead of repeatedly choosing a closer
        // pocket behind a wall. Partial paths must not become new hold positions.
        var path = worker.getNavigation().createPath(choices, 0);
        return path != null && path.canReach() && choices.contains(path.getTarget()) ? path.getTarget() : null;
    }

    private static Set<BlockPos> columns(BlockPos center) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (int radius = 1; radius <= 3; radius++) for (Direction side : Direction.Plane.HORIZONTAL)
            result.add(center.relative(side, radius));
        return result;
    }

    static boolean safe(ServerLevel level, RaidState raid, BlockPos feet, AABB body, Mob worker) {
        if (body.minY <= level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()) return false;
        var border = level.getWorldBorder();
        if (body.minX < border.getMinX() || body.maxX > border.getMaxX()
                || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) return false;
        // Check the entire body and support before reading any block in it. A wider
        // companion entity must not cross an unloaded chunk or the world border.
        for (BlockPos pos : BlockPos.betweenClosed((int)Math.floor(body.minX), feet.getY() - 1,
                (int)Math.floor(body.minZ), (int)Math.floor(Math.nextDown(body.maxX)),
                (int)Math.floor(Math.nextDown(body.maxY)), (int)Math.floor(Math.nextDown(body.maxZ)))) {
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        }
        BlockPos floor = feet.below();
        var support = level.getBlockState(floor);
        if (!support.isFaceSturdy(level, floor, Direction.UP) || !support.getFluidState().isEmpty()
                || support.is(Blocks.WATER) || support.is(Blocks.LAVA) || support.is(Blocks.MAGMA_BLOCK)
                || support.is(Blocks.CAMPFIRE) || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS)) return false;
        for (BlockPos pos : BlockPos.betweenClosed((int)Math.floor(body.minX), feet.getY(),
                (int)Math.floor(body.minZ), (int)Math.floor(Math.nextDown(body.maxX)),
                (int)Math.floor(Math.nextDown(body.maxY)), (int)Math.floor(Math.nextDown(body.maxZ)))) {
            var state = level.getBlockState(pos);
            if (raid.pendingCampBlocks.containsKey(pos.asLong()) || raid.pendingFortifications.containsKey(pos.asLong())
                    || !state.getFluidState().isEmpty() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)
                    || state.is(Blocks.WITHER_ROSE) || (!state.isAir() && !CampVegetation.plant(state))) return false;
        }
        return level.noCollision(worker, body) && level.getEntitiesOfClass(LivingEntity.class, body,
                entity -> entity != worker && entity.isAlive() && !entity.isSpectator()).isEmpty();
    }
}
