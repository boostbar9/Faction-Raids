package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.camp.CampVegetation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Predicate;

/**
 * Natural obstructions a player's builders clear before a reviewed job is handed to Workers.
 *
 * <p>Reviews treat these cells as empty and confirmation removes them right before the native
 * area is created, so the protected handoff still sees exactly the reviewed (empty) states.
 * Only vanilla blocks without block entities or fluids qualify; anything player-made, modded,
 * waterlogged or holding data stays protected and still needs manual clearance.
 */
public final class SiteClearance {
    /** Longest trunk followed upward so a cleared tree does not leave floating logs. */
    static final int TRUNK_LIMIT = 32;
    private SiteClearance() {}

    /** Plants, snow cover, vines and natural trees: cleared from planned cells and their placement neighbours. */
    public static boolean vegetation(BlockGetter level, BlockPos pos, BlockState state) {
        if (!vanilla(state)) return false;
        Block block = state.getBlock();
        if (CampVegetation.plant(state) || block instanceof SnowLayerBlock || block instanceof VineBlock
                || block instanceof GlowLichenBlock || block instanceof SweetBerryBushBlock
                || block instanceof MushroomBlock || block instanceof SaplingBlock || block instanceof SugarCaneBlock
                || block instanceof CactusBlock || block instanceof BambooStalkBlock || block instanceof BambooSaplingBlock
                || block instanceof CocoaBlock || block instanceof AzaleaBlock || block instanceof PinkPetalsBlock
                || block instanceof HangingRootsBlock || state.is(Blocks.MOSS_CARPET))
            return true;
        if (block instanceof LeavesBlock) return state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
        return naturalLog(state) && level != null && pos != null && treeTrunk(level, pos);
    }

    /** Natural ground dug out of reserved wall and headroom cells when a wall is cut into a slope. */
    public static boolean terrain(BlockState state) {
        if (!vanilla(state)) return false;
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD) || state.is(Blocks.CLAY) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.STONE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE)
                || state.is(Blocks.COPPER_ORE);
    }

    /** True when a reserved cell would be cleared on confirmation. */
    public static boolean clearsCell(BlockGetter level, BlockPos pos, BlockState state) {
        return vegetation(level, pos, state) || terrain(state);
    }

    /** The state a review records for a reserved cell: clearable natural blocks count as already empty. */
    public static BlockState reviewed(BlockGetter level, BlockPos pos, BlockState state, String target) {
        if (state == null || target != null && target.equals(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()))))
            return state;
        return clearsCell(level, pos, state) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Neighbour cells that confirmation will clear, so placement reviews can ignore them. */
    public static Predicate<BlockPos> clearedNeighbours(BlockGetter level, Set<BlockPos> reserved) {
        return pos -> {
            BlockState state = level.getBlockState(pos);
            return vegetation(level, pos, state) || reserved.contains(pos) && terrain(state);
        };
    }

    /**
     * Breaks natural obstructions in {@code cells} (vegetation and terrain) and natural vegetation
     * within the two-block placement neighbourhood of {@code targets}. Items drop as if mined.
     * Cells whose current block already matches its target in {@code keep} are left alone.
     */
    public static int clear(ServerLevel level, Collection<BlockPos> cells, Collection<BlockPos> targets, Map<Long, String> keep) {
        Set<BlockPos> work = new HashSet<>();
        for (BlockPos cell : cells) {
            if (!usable(level, cell)) continue;
            BlockState state = level.getBlockState(cell);
            String target = keep == null ? null : keep.get(cell.asLong());
            if (target != null && target.equals(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())))) continue;
            if (clearsCell(level, cell, state)) work.add(cell.immutable());
        }
        for (BlockPos target : targets) {
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                if (distance == 0 || distance > 2) continue;
                BlockPos pos = target.offset(x, y, z);
                if (!work.contains(pos) && usable(level, pos) && vegetation(level, pos, level.getBlockState(pos)))
                    work.add(pos.immutable());
            }
        }
        // Take the rest of a cut tree's trunk so no logs are left hanging over the wall.
        for (BlockPos pos : List.copyOf(work)) {
            if (!naturalLog(level.getBlockState(pos))) continue;
            BlockPos above = pos.above();
            for (int i = 0; i < TRUNK_LIMIT && usable(level, above) && naturalLog(level.getBlockState(above)); i++, above = above.above())
                work.add(above.immutable());
        }
        List<BlockPos> ordered = new ArrayList<>(work);
        // Top-down, so falling or supported blocks never land in a cell already cleared.
        ordered.sort(Comparator.comparingInt(BlockPos::getY).reversed().thenComparingLong(BlockPos::asLong));
        int cleared = 0;
        for (BlockPos pos : ordered) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !usable(level, pos)) continue;
            if (level.destroyBlock(pos, true)) cleared++;
        }
        return cleared;
    }

    /** Bark-topped vanilla tree logs; stripped logs and six-sided wood are building blocks. */
    static boolean naturalLog(BlockState state) {
        if (!vanilla(state) || !(state.getBlock() instanceof RotatedPillarBlock)) return false;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_log") && !path.startsWith("stripped_");
    }

    /** A log is part of a tree when the top of its vertical run touches natural (non-persistent) leaves. */
    static boolean treeTrunk(BlockGetter level, BlockPos pos) {
        BlockPos top = pos;
        for (int i = 0; i < TRUNK_LIMIT && naturalLog(level.getBlockState(top.above())); i++) top = top.above();
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            BlockState state = level.getBlockState(top.offset(x, y, z));
            if (state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT)
                    && !state.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    private static boolean vanilla(BlockState state) {
        if (state == null || state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static boolean usable(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.getBlockEntity(pos) == null;
    }
}
