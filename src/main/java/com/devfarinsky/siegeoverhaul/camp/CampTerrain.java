package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Predicate;

/** Small, fully planned earthworks. Validation never changes the world or loads chunks. */
public final class CampTerrain {
    public static final int CAMP_RADIUS = 9;
    private static final int EDGE_WIDTH = 3;
    private static final int MAX_CHANGE = 3;
    private static final int MAX_BLOCKS = 1024;
    private CampTerrain() {}

    public record Change(BlockPos pos, BlockState before, BlockState after) {}
    public record Plan(List<Change> changes) {
        public Plan { changes = List.copyOf(changes); }
    }

    /** Reject the entire site if even one column intersects water, structures or an excluded claim. */
    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded) {
        int radius = CAMP_RADIUS + EDGE_WIDTH;
        Map<BlockPos, Integer> heights = new HashMap<>();
        List<Change> changes = new ArrayList<>();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)
                        || excluded.test(column)) return Optional.empty();
                int oldY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                int newY = targetHeight(center.getY(), oldY, distance);
                if (Math.abs(oldY - center.getY()) > MAX_CHANGE) return Optional.empty();
                heights.put(column, newY);
                if (distance > radius) continue; // Unmodified boundary, used to check the transition.
                int bottom = Math.min(oldY, newY) - 1;
                int top = Math.max(oldY, newY) + 5;
                if (bottom < level.getMinBuildHeight() || top >= level.getMaxBuildHeight()) return Optional.empty();
                for (int y = bottom; y <= top; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    BlockState before = level.getBlockState(pos);
                    if (!before.getFluidState().isEmpty() || before.hasBlockEntity()) return Optional.empty();
                    // Strict soil whitelist: never cut stone foundations, timber, containers or ores.
                    if (y < oldY ? !isSoil(before) : !isClearance(before)) return Optional.empty();
                    BlockState after = before;
                    if (y >= newY && y < oldY) after = Blocks.AIR.defaultBlockState();
                    else if (y >= oldY && y < newY) after = Blocks.DIRT.defaultBlockState();
                    else if (oldY != newY && y >= Math.min(oldY, newY) && !before.isAir())
                        after = Blocks.AIR.defaultBlockState();
                    if (!before.equals(after)) changes.add(new Change(pos, before, after));
                    if (changes.size() > MAX_BLOCKS) return Optional.empty();
                }
            }
        }
        // Every step from camp through the blended edge to untouched ground is at most one block.
        for (var entry : heights.entrySet()) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Integer neighbor = heights.get(entry.getKey().relative(direction));
                if (neighbor != null && Math.abs(neighbor - entry.getValue()) > 1) return Optional.empty();
            }
        }
        return Optional.of(new Plan(changes));
    }

    static int targetHeight(int campY, int oldY, int distance) {
        int allowance = Math.max(0, distance - CAMP_RADIUS);
        return Math.max(campY - allowance, Math.min(campY + allowance, oldY));
    }

    private static boolean isSoil(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    private static boolean isClearance(BlockState state) {
        return state.isAir() || state.is(Blocks.GRASS) || state.is(Blocks.FERN) || state.is(Blocks.SNOW);
    }

    /** Recheck the complete plan and capture every original before the first neighbor update. */
    public static boolean apply(ServerLevel level, RaidState raid, Plan plan) {
        for (Change change : plan.changes()) {
            if (!level.hasChunkAt(change.pos()) || !level.getBlockState(change.pos()).equals(change.before())
                    || !level.getEntitiesOfClass(LivingEntity.class, new AABB(change.pos()), LivingEntity::isAlive).isEmpty())
                return false;
        }
        Map<Long, CompoundTag> previous = new HashMap<>();
        for (Change change : plan.changes()) {
            long key = change.pos().asLong();
            if (raid.campBlocks.containsKey(key)) previous.put(key, raid.campBlocks.get(key).copy());
            raid.recordCampBlock(key, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(change.after().getBlock())).toString(),
                    BlockRestoration.serializeState(level, change.pos(), change.before()));
        }
        List<Change> ordered = new ArrayList<>(plan.changes());
        ordered.sort(Comparator.comparingInt((Change c) -> c.pos().getY()).reversed());
        for (Change change : ordered) {
            if (!level.setBlock(change.pos(), change.after(), 3)) {
                // Same-thread failure: roll back the whole earthwork before allowing a camp to spawn.
                ordered.sort(Comparator.comparingInt(c -> c.pos().getY()));
                for (Change rollback : ordered) {
                    level.setBlock(rollback.pos(), rollback.before(), 3);
                    long key = rollback.pos().asLong();
                    if (previous.containsKey(key)) raid.campBlocks.put(key, previous.get(key));
                    else raid.campBlocks.remove(key);
                }
                return false;
            }
        }
        return true;
    }

    /** Dirt can turn grassy, or grass decay beneath a camp floor, without a player editing it. */
    public static boolean matchesPlaced(BlockState current, String placed) {
        var id = ForgeRegistries.BLOCKS.getKey(current.getBlock());
        if (current.isAir() || id != null && id.toString().equals(placed)) return true;
        return (placed.equals("minecraft:dirt") || placed.equals("minecraft:grass_block"))
                && (current.is(Blocks.DIRT) || current.is(Blocks.GRASS_BLOCK));
    }
}
