package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
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
    private static final int MAX_CHANGE = 6;
    private static final int MAX_BLOCKS = 1024;
    private CampTerrain() {}

    public record Change(BlockPos pos, BlockState before, BlockState after) {}
    public record Plan(List<Change> changes) {
        public Plan { changes = List.copyOf(changes); }
    }

    /** Pick the least earthwork within the six-block cut/fill limit.
     * A mound under the scout must not force the entire camp up to its peak.
     * This is only a height proposal; plan() still validates every block and claim.
     */
    public static BlockPos earthworksCenter(ServerLevel level, BlockPos center) {
        int radius = CAMP_RADIUS + EDGE_WIDTH + 1;
        int[] heights = new int[(radius * 2 + 1) * (radius * 2 + 1)];
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos column = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return center;
            heights[count++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    column.getX(), column.getZ());
        }
        Arrays.sort(heights);
        int low = heights[count - 1] - MAX_CHANGE;
        int high = heights[0] + MAX_CHANGE;
        if (low > high) return center;
        return new BlockPos(center.getX(), Math.max(low, Math.min(high, heights[count / 2])), center.getZ());
    }

    public enum Rejection { UNLOADED, BORDER, CLAIM, RELIEF, EDGE, HEIGHT_LIMIT, FLUID, BLOCK_ENTITY, SOIL, CLEARANCE, BUDGET }

    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded) {
        return plan(level, center, excluded, reason -> {});
    }

    /** The observer receives exactly one reason when a site is rejected. */
    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded,
                                    java.util.function.Consumer<Rejection> rejected) {
        int radius = CAMP_RADIUS + EDGE_WIDTH;
        Map<BlockPos, Integer> original = new HashMap<>();
        Map<BlockPos, Integer> heights = new HashMap<>();
        List<BlockPos> boundary = new ArrayList<>();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) return reject(rejected, Rejection.UNLOADED);
                if (!level.getWorldBorder().isWithinBounds(column)) return reject(rejected, Rejection.BORDER);
                if (excluded.test(column)) return reject(rejected, Rejection.CLAIM);
                int oldY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                if (Math.abs(oldY - center.getY()) > MAX_CHANGE) return reject(rejected, Rejection.RELIEF);
                original.put(column, oldY);
                if (Math.max(Math.abs(dx), Math.abs(dz)) > radius) boundary.add(column);
            }
        }
        // Extend the fixed flat camp and actual untouched boundary with one-block slopes.
        // Independent ring clamps can leave two-block steps along a ring. Use all boundary
        // constraints instead, and leave unrelated steps BETWEEN untouched columns alone.
        for (var entry : original.entrySet()) {
            BlockPos column = entry.getKey();
            int dx = Math.abs(column.getX() - center.getX());
            int dz = Math.abs(column.getZ() - center.getZ());
            if (Math.max(dx, dz) > radius) { heights.put(column, entry.getValue()); continue; }
            int distance = Math.max(0, dx - CAMP_RADIUS) + Math.max(0, dz - CAMP_RADIUS);
            int low = center.getY() - distance, high = center.getY() + distance;
            for (BlockPos edge : boundary) {
                int steps = Math.abs(column.getX() - edge.getX()) + Math.abs(column.getZ() - edge.getZ());
                int edgeY = original.get(edge);
                low = Math.max(low, edgeY - steps);
                high = Math.min(high, edgeY + steps);
            }
            if (low > high) return reject(rejected, Rejection.EDGE);
            heights.put(column, Math.max(low, Math.min(high, center.getY())));
        }
        List<Change> changes = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos column = center.offset(dx, 0, dz);
            int oldY = original.get(column), newY = heights.get(column);
            int bottom = Math.min(oldY, newY) - 1;
            int top = Math.max(oldY, newY) + 5;
            if (bottom < level.getMinBuildHeight() || top >= level.getMaxBuildHeight()) return reject(rejected, Rejection.HEIGHT_LIMIT);
            for (int y = bottom; y <= top; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                BlockState before = level.getBlockState(pos);
                if (!before.getFluidState().isEmpty() || before.is(Blocks.WATER) || before.is(Blocks.LAVA)) return reject(rejected, Rejection.FLUID);
                if (before.hasBlockEntity()) return reject(rejected, Rejection.BLOCK_ENTITY);
                if (y < oldY ? !isSoil(before) : !isClearance(before))
                    return reject(rejected, y < oldY ? Rejection.SOIL : Rejection.CLEARANCE);
                BlockState after = before;
                if (y >= newY && y < oldY) after = Blocks.AIR.defaultBlockState();
                else if (y >= oldY && y < newY) after = Blocks.DIRT.defaultBlockState();
                else if (oldY != newY && y >= Math.min(oldY, newY) && !before.isAir()) after = Blocks.AIR.defaultBlockState();
                if (!before.equals(after)) changes.add(new Change(pos, before, after));
                if (changes.size() > MAX_BLOCKS) return reject(rejected, Rejection.BUDGET);
            }
        }
        return Optional.of(new Plan(changes));
    }

    private static Optional<Plan> reject(java.util.function.Consumer<Rejection> observer, Rejection reason) {
        observer.accept(reason);
        return Optional.empty();
    }

    static int targetHeight(int campY, int oldY, int distance) {
        int allowance = Math.max(0, distance - CAMP_RADIUS);
        return Math.max(campY - allowance, Math.min(campY + allowance, oldY));
    }

    private static boolean isSoil(BlockState state) {
        return CampRoad.soil(state);
    }

    private static boolean isClearance(BlockState state) {
        return state.isAir() || state.is(Blocks.GRASS) || state.is(Blocks.FERN) || state.is(Blocks.SNOW)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)
                || state.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS) || state.is(net.minecraft.tags.BlockTags.TALL_FLOWERS);
    }

    /** Recheck the complete plan and capture every original before the first neighbor update. */
    public static boolean apply(ServerLevel level, RaidState raid, Plan plan) {
        if (com.devfarinsky.siegeoverhaul.compat.CorpseCompatibility.blocksAny(level,
                plan.changes().stream().map(change->change.pos().asLong()).toList())) return false;
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
