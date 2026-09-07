package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Builder-assisted construction at the real war camp, using the siege's restoration ledger. */
public final class CampBuilder {
    private static final int BLOCKS_PER_BUILDER = 4;
    private CampBuilder() {}

    public static void startCamp(ServerLevel level, RaidState raid) {
        if (raid.campPos == null || raid.pendingCampBlocks.isEmpty()
                || !RaidConfig.ENABLE_CAMP_CONSTRUCTION.get() || !WorkersBridge.available()) return;
        for (int i = 0; i < RaidConfig.CAMP_BUILDER_MAX.get(); i++) {
            BlockPos spawn = standingPosition(level, raid.campPos.offset(7 + i, 0, 0), raid.campPos.getY(), Vec3.atCenterOf(raid.campPos));
            if (spawn == null) continue;
            WorkersBridge.spawnBuilder(level, spawn, raid.teamKey).ifPresent(worker -> raid.campWorkers.add(worker.getUUID()));
        }
        raid.campUsesWorkers = !raid.campWorkers.isEmpty();
    }

    /** Called once per periodic siege pass. All pending jobs and crew IDs survive world saves. */
    public static void tick(ServerLevel level, RaidState raid, BiConsumer<BlockPos, Block> place) {
        if (raid.pendingCampBlocks.isEmpty()) return;
        // Do not force-load the camp, or time out while its chunk is unloaded.
        BlockPos first = BlockPos.of(raid.pendingCampBlocks.keySet().iterator().next());
        if (!level.hasChunkAt(first)) return;
        if (advanceTimeout(raid)) return;
        if (!raid.campUsesWorkers || !WorkersBridge.available() || !RaidConfig.ENABLE_CAMP_CONSTRUCTION.get()) {
            if (raid.campUsesWorkers) cleanup(level, raid);
            placeNearby(raid, null, 12, place);
            return;
        }
        for (UUID id : raid.campWorkers) {
            if (raid.pendingCampBlocks.isEmpty()) break;
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob worker) || !worker.isAlive()) continue;
            BlockPos target = BlockPos.of(raid.pendingCampBlocks.keySet().iterator().next());
            BlockPos stand = standingPosition(level, target, raid.campPos.getY(), worker.position());
            if (stand == null || !WorkersBridge.moveBuilder(worker, stand)) continue;
            worker.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            int placed = placeNearby(raid, worker.position(), BLOCKS_PER_BUILDER, place);
            if (placed > 0) worker.swing(InteractionHand.MAIN_HAND);
        }
    }

    static boolean advanceTimeout(RaidState raid) {
        raid.campBuildTicks += ModConstants.TICK_INTERVAL;
        if (raid.campBuildTicks < ModConstants.secondsToTicks(RaidConfig.CAMP_MAX_BUILD_SECONDS.get())) return false;
        FactionLogger.LOG.info("Camp construction for {} stopped after {}s; siege continues normally",
                raid.teamKey, RaidConfig.CAMP_MAX_BUILD_SECONDS.get());
        raid.pendingCampBlocks.clear();
        return true;
    }

    /** Preserve placement order (supports before roofs); never build remotely from a missing/dead crew. */
    static int placeNearby(RaidState raid, Vec3 worker, int budget, BiConsumer<BlockPos, Block> place) {
        int count = 0;
        var iterator = raid.pendingCampBlocks.entrySet().iterator();
        while (iterator.hasNext() && count < budget) {
            Map.Entry<Long, String> job = iterator.next();
            BlockPos pos = BlockPos.of(job.getKey());
            if (worker != null && !withinReach(worker, pos)) break;
            ResourceLocation key = ResourceLocation.tryParse(job.getValue());
            Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
            if (block != null) place.accept(pos, block);
            iterator.remove();
            count++;
        }
        return count;
    }

    static boolean withinReach(Vec3 worker, BlockPos pos) {
        double dx = worker.x - (pos.getX() + 0.5), dz = worker.z - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= 16 && Math.abs(worker.y - pos.getY()) <= 5;
    }

    /** Find ground beside the job; do not send workers onto a roof or through the camp wall. */
    private static BlockPos standingPosition(ServerLevel level, BlockPos target, int groundY, Vec3 from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 3; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos feet = new BlockPos(target.getX(), groundY + dy, target.getZ()).relative(direction, radius);
                    if (level.hasChunkAt(feet) && level.getWorldBorder().isWithinBounds(feet)
                            && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                            && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                        double distance = from.distanceToSqr(Vec3.atBottomCenterOf(feet));
                        if (distance < bestDistance) {
                            best = feet;
                            bestDistance = distance;
                        }
                    }
                }
            }
        }
        return best;
    }

    public static void cleanup(ServerLevel level, RaidState raid) {
        for (UUID id : new ArrayList<>(raid.campWorkers)) {
            Entity entity = level.getEntity(id);
            if (entity != null) entity.discard();
        }
        raid.campWorkers.clear();
        raid.campUsesWorkers = false;
        // Unloaded crew are removed by EntityJoinLevelEvent when their saved UUID
        // is no longer attached to an active raid. Player workers have no crew tag.
    }
}
