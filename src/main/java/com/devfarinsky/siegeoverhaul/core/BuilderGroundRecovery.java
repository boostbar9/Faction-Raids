package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Last-resort surface recovery for an actively commissioned wall builder, never a general NPC teleport. */
public final class BuilderGroundRecovery {
    private static final String STATE = "SiegeWallGroundRecovery";
    private BuilderGroundRecovery() {}

    public static void reset(Mob builder) { builder.getPersistentData().remove(STATE); }

    /** Called every 40 ticks after job/owner validation; native interruptions reset the observation window. */
    public static void tick(ServerLevel level, Mob builder) {
        if (!builder.isAlive() || builder.isNoAi() || builder.isPassenger() || builder.isLeashed()
                || builder.getTarget() != null || !WorkersBridge.readyForGroundRecovery(builder)) {
            reset(builder); return;
        }
        var root = builder.getPersistentData();
        var state = root.getCompound(STATE);
        Vec3 now = builder.position();
        long time = level.getGameTime();
        // Time may move backwards on reload. Never turn a stale timer into an immediate rescue.
        long previous = state.getLong("Time");
        Vec3 last = new Vec3(state.getDouble("X"), state.getDouble("Y"), state.getDouble("Z"));
        boolean stationary = state.contains("Time") && time > previous && time - previous <= 80
                && now.distanceToSqr(last) < 0.25D;
        int stalled = stationary ? state.getInt("Still") + 1 : 0;
        if (!stationary) state.putInt("Repaths", 0);
        state.putDouble("X", now.x); state.putDouble("Y", now.y); state.putDouble("Z", now.z);
        state.putLong("Time", time); state.putInt("Still", stalled);
        root.put(STATE, state);
        if (stalled < 3) return; // Six seconds without moving, not ordinary pathfinding.
        state.putInt("Still", 0); // Also bounds unsuccessful searches to once per six seconds.
        BlockPos current = builder.blockPosition();
        if (!level.hasChunkAt(current)) return;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, current.getX(), current.getZ());
        boolean buried = !level.noCollision(builder, builder.getBoundingBox()) || surface - now.y >= 2;
        if (!buried) {
            // An open pit's heightmap is its floor. Recognize it only when all four adjacent
            // columns rise by at least two blocks, so a worker beside an ordinary slope stays put.
            buried = true;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos p = current.relative(side);
                if (!level.hasChunkAt(p) || level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        p.getX(), p.getZ()) - now.y < 2) { buried = false; break; }
            }
        }
        if (!buried) { unstick(level, builder, state); return; }
        BlockPos destination = findSurface(level, builder);
        if (destination == null) return;
        builder.getNavigation().stop();
        builder.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        builder.setDeltaMovement(Vec3.ZERO);
        builder.fallDistance = 0;
        reset(builder);
    }

    /**
     * A builder that keeps walking into a block it just placed (its path steps up over the new
     * wall, but the follower already counted that node as passed) never moves again. The first
     * six-second stall drops the path so the native goal re-plans; a second consecutive stall
     * moves it to the furthest safe node of its own path within reach, past the obstruction.
     */
    static void unstick(ServerLevel level, Mob builder, CompoundTag state) {
        var navigation = builder.getNavigation();
        Path path = navigation.getPath();
        if (navigation.isDone() || path == null) { state.putInt("Repaths", 0); return; }
        int repaths = state.getInt("Repaths");
        navigation.stop();
        if (repaths < 1) { state.putInt("Repaths", repaths + 1); return; }
        BlockPos hop = pathHop(level, builder, path);
        if (hop == null) return;
        builder.teleportTo(hop.getX() + 0.5D, hop.getY(), hop.getZ() + 0.5D);
        builder.setDeltaMovement(Vec3.ZERO);
        builder.fallDistance = 0;
        reset(builder);
    }

    /** Furthest node of the builder's own path within 3.5 blocks horizontally and two vertically. */
    static BlockPos pathHop(ServerLevel level, Mob builder, Path path) {
        BlockPos origin = builder.blockPosition();
        for (int i = path.getNodeCount() - 1; i >= 0; i--) {
            BlockPos node = path.getNode(i).asBlockPos();
            double dx = node.getX() + 0.5D - builder.getX(), dz = node.getZ() + 0.5D - builder.getZ();
            if (node.equals(origin) || dx * dx + dz * dz > 3.5D * 3.5D || Math.abs(node.getY() - origin.getY()) > 2) continue;
            if (!level.hasChunkAt(node) || !level.hasChunkAt(node.below())) continue;
            var support = level.getBlockState(node.below());
            if (!support.isFaceSturdy(level, node.below(), Direction.UP) || !support.getFluidState().isEmpty()
                    || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE) || support.is(Blocks.SOUL_CAMPFIRE)) continue;
            if (standable(level, builder, node)) return node;
        }
        return null;
    }

    private static boolean standable(ServerLevel level, Mob builder, BlockPos feet) {
        AABB body = builder.getBoundingBox().move(feet.getX() + 0.5D - builder.getX(),
                feet.getY() - builder.getY(), feet.getZ() + 0.5D - builder.getZ());
        var border = level.getWorldBorder();
        if (body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                || body.minX < border.getMinX() || body.maxX > border.getMaxX()
                || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) return false;
        for (BlockPos p : BlockPos.betweenClosed((int)Math.floor(body.minX), (int)Math.floor(body.minY),
                (int)Math.floor(body.minZ), (int)Math.floor(body.maxX), (int)Math.floor(body.maxY), (int)Math.floor(body.maxZ))) {
            if (!level.hasChunkAt(p) || !level.getFluidState(p).isEmpty()
                    // A wither rose is a camp-clearable flower with no collision, but unsafe to stand in.
                    || level.getBlockState(p).is(Blocks.WITHER_ROSE)
                    || (!level.getBlockState(p).isAir()
                        && !com.devfarinsky.siegeoverhaul.camp.CampVegetation.plant(level.getBlockState(p)))) return false;
        }
        return level.noCollision(builder, body);
    }

    /** At most 81 local columns; no chunk loading, digging, inventory edits or job replacement. */
    static BlockPos findSurface(ServerLevel level, Mob builder) {
        BlockPos origin = builder.blockPosition();
        BlockPos best = null;
        double score = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos column = origin.offset(dx, 0, dz);
            if (!level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (y < origin.getY() + 1 || y > origin.getY() + 12) continue;
            BlockPos feet = column.atY(y), floor = feet.below();
            var support = level.getBlockState(floor);
            if (!support.isFaceSturdy(level, floor, Direction.UP) || !support.getFluidState().isEmpty()
                    || support.is(BlockTags.LEAVES) || support.is(BlockTags.LOGS)
                    || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                    || support.is(Blocks.SOUL_CAMPFIRE) || !level.canSeeSky(feet)) continue;
            AABB body = builder.getBoundingBox().move(feet.getX() + 0.5D - builder.getX(),
                    feet.getY() - builder.getY(), feet.getZ() + 0.5D - builder.getZ());
            var border = level.getWorldBorder();
            if (body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                    || body.minX < border.getMinX() || body.maxX > border.getMaxX()
                    || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) continue;
            boolean clear = true;
            for (BlockPos p : BlockPos.betweenClosed((int)Math.floor(body.minX), (int)Math.floor(body.minY),
                    (int)Math.floor(body.minZ), (int)Math.floor(body.maxX), (int)Math.floor(body.maxY), (int)Math.floor(body.maxZ))) {
                if (!level.hasChunkAt(p) || !level.getFluidState(p).isEmpty()
                        // A wither rose is a camp-clearable flower with no collision, but unsafe to stand in.
                        || level.getBlockState(p).is(Blocks.WITHER_ROSE)
                        || (!level.getBlockState(p).isAir()
                            && !com.devfarinsky.siegeoverhaul.camp.CampVegetation.plant(level.getBlockState(p)))) {
                    clear = false; break;
                }
            }
            if (!clear || !level.noCollision(builder, body)) continue;
            double distance = builder.position().distanceToSqr(Vec3.atBottomCenterOf(feet));
            if (distance < score) { best = feet; score = distance; }
        }
        return best;
    }
}
