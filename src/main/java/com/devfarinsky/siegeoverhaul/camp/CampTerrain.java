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
    private static final int MAX_WATER_DEPTH = 6;
    private static final int MAX_BLOCKS = 1024;
    public static final int FALLBACK_MAX_CHANGE = 12;
    private static final int FALLBACK_EDGE_WIDTH = 6;
    private static final int FALLBACK_MAX_BLOCKS = 4096;
    private CampTerrain() {}

    public record Change(BlockPos pos, BlockState before, BlockState after) {}
    public record Plan(List<Change> changes, net.minecraft.core.Direction entrance) {
        public Plan(List<Change> changes) { this(changes, null); }
        public Plan { changes = List.copyOf(changes); }
    }

    /** Pick the least earthwork within the six-block cut/fill limit.
     * A mound under the scout must not force the entire camp up to its peak.
     * This is only a height proposal; plan() still validates every block and claim.
     */
    public static BlockPos earthworksCenter(ServerLevel level, BlockPos center) {
        return earthworksCenter(level, center, false);
    }

    public static BlockPos earthworksCenter(ServerLevel level, BlockPos center, boolean fallback) {
        int radius = CAMP_RADIUS + (fallback ? FALLBACK_EDGE_WIDTH : EDGE_WIDTH) + 1;
        int maxChange = fallback ? FALLBACK_MAX_CHANGE : MAX_CHANGE;
        int[] heights = new int[(radius * 2 + 1) * (radius * 2 + 1)];
        int count = 0;
        int low = Integer.MIN_VALUE, high = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos column = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return center;
            if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
            int ground = CampGround.survey(level,column,fallback).ground();
            // Only the flat core is leveled to this plane. Hills in the transition
            // constrain it but must not pull an already-flat camp upward.
            if (Math.max(Math.abs(dx),Math.abs(dz)) <= CAMP_RADIUS) heights[count++] = ground;
            int distance = Math.max(0, Math.abs(dx)-CAMP_RADIUS) + Math.max(0, Math.abs(dz)-CAMP_RADIUS);
            // Untouched edges constrain the slope; graded columns constrain actual cut/fill.
            int allowance = distance + (Math.max(Math.abs(dx),Math.abs(dz)) < radius ? maxChange : 0);
            low = Math.max(low,ground-allowance);
            high = Math.min(high,ground+allowance);
        }
        Arrays.sort(heights, 0, count);
        if (low > high) return center;
        return new BlockPos(center.getX(), Math.max(low, Math.min(high, heights[count / 2])), center.getZ());
    }

    public enum Rejection { UNLOADED, BORDER, CLAIM, RELIEF, EDGE, HEIGHT_LIMIT, FLUID, BLOCK_ENTITY, SOIL, CLEARANCE, BUDGET, NO_LAND_EXIT }

    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded) {
        return plan(level, center, excluded, reason -> {});
    }

    /** The observer receives exactly one reason when a site is rejected. */
    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded,
                                    java.util.function.Consumer<Rejection> rejected) {
        return plan(level,center,excluded,rejected,false);
    }

    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded,
                                    java.util.function.Consumer<Rejection> rejected, boolean fallback) {
        return plan(level,center,excluded,rejected,fallback,null);
    }

    public static Optional<Plan> plan(ServerLevel level, BlockPos center, Predicate<BlockPos> excluded,
                                    java.util.function.Consumer<Rejection> rejected, boolean fallback,
                                    net.minecraft.core.Direction gateSide) {
        int radius = CAMP_RADIUS + (fallback ? FALLBACK_EDGE_WIDTH : EDGE_WIDTH);
        int maxChange = fallback ? FALLBACK_MAX_CHANGE : MAX_CHANGE;
        int budget = fallback ? FALLBACK_MAX_BLOCKS : MAX_BLOCKS;
        Map<BlockPos, Integer> original = new HashMap<>();
        Map<BlockPos, CampGround.Column> columns = new HashMap<>();
        Map<BlockPos, Integer> heights = new HashMap<>();
        List<BlockPos> boundary = new ArrayList<>();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) return reject(rejected, Rejection.UNLOADED);
                if (!level.getWorldBorder().isWithinBounds(column)) return reject(rejected, Rejection.BORDER);
                if (excluded.test(column)) return reject(rejected, Rejection.CLAIM);
                if (Math.abs(dx) == radius + 1 && Math.abs(dz) == radius + 1) continue;
                var surveyed = CampGround.survey(level,column,fallback);
                columns.put(column,surveyed);
                int oldY = surveyed.ground();
                // The transition can follow natural slopes. Bound actual changes below,
                // rather than rejecting an untouched edge for its height above the core.
                if (Math.max(Math.abs(dx),Math.abs(dz)) <= CAMP_RADIUS
                        && Math.abs(oldY-center.getY()) > maxChange) return reject(rejected,Rejection.RELIEF);
                original.put(column, oldY);
                if (Math.max(Math.abs(dx), Math.abs(dz)) > radius) boundary.add(column);
            }
        }
        net.minecraft.core.Direction entrance = null;
        List<Change> exitChanges = List.of();
        if (fallback) {
            var preferred = gateSide == null ? net.minecraft.core.Direction.NORTH : gateSide;
            for (var direction : new net.minecraft.core.Direction[]{preferred, preferred.getClockWise(),
                    preferred.getCounterClockWise(), preferred.getOpposite()}) {
                var exit = prepareExit(level,center,radius+1,direction,excluded);
                if (exit.isPresent()) { entrance=direction; exitChanges=exit.get(); break; }
            }
            if (entrance == null) return reject(rejected,Rejection.NO_LAND_EXIT);
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
        int canopyTop = columns.values().stream().filter(CampGround.Column::tree)
                .mapToInt(c -> c.top()+2).max().orElse(Integer.MIN_VALUE);
        List<Change> changes = new ArrayList<>(exitChanges);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos column = center.offset(dx, 0, dz);
            int oldY = original.get(column), newY = heights.get(column);
            if (Math.abs(newY - oldY) > maxChange) return reject(rejected, Rejection.RELIEF);
            int bottom = Math.min(oldY, newY) - 1;
            boolean wet=fallback && CampGround.water(level.getBlockState(column.atY(oldY-1)));
            if(wet) {
                if(newY<oldY)return reject(rejected,Rejection.FLUID);
                int depth=0;
                while(depth<MAX_WATER_DEPTH && CampGround.water(level.getBlockState(column.atY(oldY-depth-1))))depth++;
                bottom=Math.min(bottom,oldY-depth-1);
                if(newY-(oldY-depth)>maxChange)return reject(rejected,Rejection.RELIEF);
                if(!isSoil(level.getBlockState(column.atY(bottom))))return reject(rejected,Rejection.FLUID);
            }
            var surveyed=columns.get(column);
            int top = Math.max(Math.max(oldY, newY) + 5, canopyTop);
            if (bottom < level.getMinBuildHeight() || top >= level.getMaxBuildHeight()) return reject(rejected, Rejection.HEIGHT_LIMIT);
            for (int y = bottom; y <= top; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                BlockState before = level.getBlockState(pos);
                boolean fillWater=wet && y<newY && CampGround.water(before);
                boolean vegetation=fallback && (CampGround.leaves(before)
                        || surveyed.tree() && y>=oldY && y<surveyed.top() && CampGround.trunk(before));
                if (!fillWater && (!before.getFluidState().isEmpty() || before.is(Blocks.WATER) || before.is(Blocks.LAVA))) return reject(rejected, Rejection.FLUID);
                if (before.hasBlockEntity()) return reject(rejected, Rejection.BLOCK_ENTITY);
                if (!fillWater && !vegetation && (y < oldY ? !isSoil(before) : !isClearance(before)))
                    return reject(rejected, y < oldY ? Rejection.SOIL : Rejection.CLEARANCE);
                BlockState after = before;
                if (fillWater) after = Blocks.DIRT.defaultBlockState();
                else if (vegetation) after = y < newY ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
                else if (y >= newY && y < oldY) after = Blocks.AIR.defaultBlockState();
                else if (y >= oldY && y < newY) after = Blocks.DIRT.defaultBlockState();
                else if (oldY != newY && y >= Math.min(oldY, newY) && !before.isAir()) after = Blocks.AIR.defaultBlockState();
                if (!before.equals(after)) changes.add(new Change(pos, before, after));
                if (changes.size() > budget) return reject(rejected, Rejection.BUDGET);
            }
        }
        return Optional.of(new Plan(changes, entrance));
    }

    /** A short supported, three-wide way out, never an island with no landing. */
    private static Optional<List<Change>> prepareExit(ServerLevel level, BlockPos center, int edge,
            net.minecraft.core.Direction direction, Predicate<BlockPos> excluded) {
        List<Change> changes = new ArrayList<>();
        Map<Integer,Integer> previous = new HashMap<>();
        int dryRows=0;
        for (int distance=edge; distance<=edge+10; distance++) {
            boolean dry = true;
            Map<Integer,Integer> row = new HashMap<>();
            for (int offset=-1; offset<=1; offset++) {
                BlockPos column=center.relative(direction,distance).relative(direction.getClockWise(),offset);
                if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)
                        || excluded.test(column)) return Optional.empty();
                var surveyed=CampGround.survey(level,column,true);
                int y=surveyed.ground(); row.put(offset,y);
                if (offset>-1 && Math.abs(y-row.get(offset-1))>1
                        || previous.containsKey(offset) && Math.abs(y-previous.get(offset))>1) return Optional.empty();
                BlockState floor=level.getBlockState(column.atY(y-1));
                boolean water=CampGround.water(floor); dry &= !water;
                int bottom=y-1;
                if (water) {
                    int depth=0;
                    while(depth<MAX_WATER_DEPTH && CampGround.water(level.getBlockState(column.atY(y-depth-1))))depth++;
                    bottom=y-depth-1;
                }
                if(bottom<level.getMinBuildHeight() || surveyed.top()+2>=level.getMaxBuildHeight()
                        || !isSoil(level.getBlockState(column.atY(bottom)))) return Optional.empty();
                int top=Math.max(y+2,surveyed.top()+2);
                for(int height=bottom;height<=top;height++) {
                    BlockPos pos=column.atY(height);BlockState before=level.getBlockState(pos);
                    boolean fill=water && height<y && CampGround.water(before);
                    boolean vegetation=CampGround.leaves(before)
                            || surveyed.tree() && height>=y && height<surveyed.top() && CampGround.trunk(before);
                    if(before.hasBlockEntity() || !fill && !before.getFluidState().isEmpty()
                            || !fill && !vegetation && (height<y ? !isSoil(before) : !isClearance(before))) return Optional.empty();
                    BlockState after=fill ? Blocks.DIRT.defaultBlockState()
                            : vegetation ? Blocks.AIR.defaultBlockState() : before;
                    if(!before.equals(after)) changes.add(new Change(pos,before,after));
                }
            }
            dryRows=dry ? dryRows+1 : 0;
            if(dryRows>=3) return Optional.of(changes);
            previous=row;
        }
        return Optional.empty();
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
        if (plan.entrance() != null)
            raid.campaign.putInt("CampEntranceFacing", plan.entrance().get2DDataValue());
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
