package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Checks the whole hired unit before spawning; never loads terrain or edits blocks. */
final class HirePlacement {
    private HirePlacement() {}

    static boolean safe(ServerLevel level, Mob mob, BlockPos feet, Predicate<BlockPos> permitted) {
        AABB body = mob.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(mob.position()));
        var border = level.getWorldBorder();
        if (body.minY - 1 < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                || body.minX < border.getMinX() || body.maxX > border.getMaxX()
                || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) return false;
        int minX = Mth.floor(body.minX), maxX = Mth.ceil(body.maxX) - 1;
        int minZ = Mth.floor(body.minZ), maxZ = Mth.ceil(body.maxZ) - 1;
        int minY = Mth.floor(body.minY), maxY = Mth.ceil(body.maxY) - 1;
        // Check all touched chunks before querying blocks, collisions or claims.
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!level.hasChunkAt(new BlockPos(x, minY, z))) return false;
        }
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            BlockPos floor = new BlockPos(x, minY - 1, z);
            BlockState support = level.getBlockState(floor);
            if (!permitted.test(floor.above()) || dangerous(support) || !support.getFluidState().isEmpty()
                    || !support.isFaceSturdy(level, floor, Direction.UP)) return false;
            for (int y = minY; y <= maxY; y++) {
                BlockState cell = level.getBlockState(new BlockPos(x, y, z));
                if (dangerous(cell) || !cell.getFluidState().isEmpty()) return false;
            }
        }
        return level.noCollision(mob, body) && level.getEntities(mob, body).isEmpty();
    }

    private static boolean dangerous(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW);
    }
}
