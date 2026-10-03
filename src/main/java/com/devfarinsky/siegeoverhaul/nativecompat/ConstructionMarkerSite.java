package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Physical shovel and native pick box on clear accessible ground, independent of blueprint origin. */
final class ConstructionMarkerSite {
    private ConstructionMarkerSite() {}

    static BlockPos find(ServerPlayer owner, AcceptedConstructionPlan plan) {
        ServerLevel level = owner.serverLevel();
        String key = SiegeCore.key(owner);
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(key);
        var claim = RecruitsClaimsBridge.resolveDefendingClaim(level, anchor).orElse(null);
        if (claim == null) return null;
        var identity = anchor.withIdentity(claim.ownerFactionStringId(), anchor.teamDisplay());
        Map<ChunkPos, Boolean> permissions = new HashMap<>();
        Predicate<BlockPos> permitted = pos -> permissions.computeIfAbsent(new ChunkPos(pos), chunk ->
                claim.chunks().contains(chunk) && !ClaimBridge.isForeignClaim(level, chunk, identity))
                && level.mayInteract(owner, pos);
        BlockPos anchorPos = owner.blockPosition();
        var columns = new ArrayList<BlockPos>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) columns.add(anchorPos.offset(x, 0, z));
        columns.sort(Comparator.comparingDouble(pos -> pos.distSqr(anchorPos)));
        // Same-floor sites win over roofs; the fallback is only two blocks up/down.
        for (int pass = 0; pass < 2; pass++) for (BlockPos column : columns) {
            if (!level.hasChunkAt(column)) continue;
            BlockPos feet = column;
            if (pass == 1) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                if (y == column.getY() || Math.abs(y - column.getY()) > 2) continue;
                feet = column.atY(y);
            }
            if (safe(level, owner, feet, plan, permitted)) return feet;
        }
        return null;
    }

    static boolean safe(ServerLevel level, ServerPlayer owner, BlockPos feet,
                        AcceptedConstructionPlan plan, Predicate<BlockPos> permitted) {
        AABB box = markerBox(feet);
        if (feet.getY() <= level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 1, box.minZ),
                BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ)))) {
            if (!level.hasChunkAt(cell) || !permitted.test(cell) || plan.cells.containsKey(cell)) return false;
            BlockState state = level.getBlockState(cell);
            if (state.hasBlockEntity() || level.getBlockEntity(cell) != null || !state.getFluidState().isEmpty() || hazardous(state)) return false;
            if (cell.getY() >= feet.getY() && !state.isAir()) return false;
            if (cell.getY() < feet.getY() && !NativeConstructionGuard.stableNeighbor(state)) return false;
        }
        BlockPos floor = feet.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || !level.noCollision((Entity) null, box)
                || !level.getEntities((Entity) null, box, NativeConstructionGuard::blocksPlacement).isEmpty()) return false;
        Vec3 eye = owner.getEyePosition(), target = new Vec3(feet.getX() + .5, feet.getY() + 1, feet.getZ() + .5);
        // A close current ray target is usable without assuming a creative reach extension.
        if (eye.distanceToSqr(target) > 3 * 3) return false;
        // Establish the whole short ray's loaded envelope before vanilla clip,
        // including a third chunk crossed by a diagonal near chunk corners.
        AABB rayBounds = new AABB(eye, target).inflate(.001);
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(rayBounds.minX, rayBounds.minY, rayBounds.minZ),
                BlockPos.containing(rayBounds.maxX, rayBounds.maxY, rayBounds.maxZ))) {
            if (!level.hasChunkAt(cell)) return false;
            if (plan.cells.containsKey(cell) && new AABB(cell).clip(eye, target).isPresent()) return false;
        }
        return level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, owner))
                .getType() == HitResult.Type.MISS;
    }

    static AABB markerBox(BlockPos feet) {
        return new AABB(feet.getX() - .1, feet.getY(), feet.getZ() - .1,
                feet.getX() + 1.1, feet.getY() + 2, feet.getZ() + 1.1);
    }

    private static boolean hazardous(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
}
