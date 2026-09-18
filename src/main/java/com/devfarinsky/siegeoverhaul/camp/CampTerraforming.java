package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v4.36.0: pave hostile terrain (water, uneven ground) into a buildable
 * plane BEFORE the palisade goes down. Every replaced block is snapshotted
 * through {@link RaidSavedData.RaidState#recordCampBlock} so the standard
 * cleanup pass can restore the original water and terrain when the raid
 * ends. Player-placed blocks and existing structures are never overwritten
 * (the vegetation check filters everything not naturally growing / naturally
 * placed).
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #paveFootprint} - hard fill for the 21x21 camp footprint
 *   before {@code buildWarCamp} runs. Guarantees a level plane at
 *   {@code campY - 1} so the palisade sits on solid ground.</li>
 *   <li>{@link #smoothOnce} - single-block-per-tick territory smoothing
 *   after the camp is claimed. Fills nearby water and cliff drops so
 *   Workers 2 builders have a walkable expansion pad. Cheap enough to
 *   run every N raid ticks.</li>
 * </ul>
 */
public final class CampTerraforming {
    private CampTerraforming() {}

    /**
     * Pave the entire {@code (2*halfExtent+1)^2} column footprint at
     * {@code campY - 1}. Water gets filled with dirt from sea floor up to
     * {@code campY - 1}. Terrain above {@code campY - 1} is cut back down.
     * Anything above the plane is cleared to air so tents and the palisade
     * have headroom. Returns the number of block ops performed (for logging).
     */
    public static int paveFootprint(ServerLevel level, RaidSavedData.RaidState state,
                                     BlockPos center, int halfExtent) {
        int floorY = center.getY() - 1;
        int ceilingY = center.getY() + 4;
        int ops = 0;
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int dx = -halfExtent; dx <= halfExtent; dx++) {
            for (int dz = -halfExtent; dz <= halfExtent; dz++) {
                int wx = center.getX() + dx;
                int wz = center.getZ() + dz;
                if (!level.hasChunkAt(new BlockPos(wx, floorY, wz))) continue;
                // Fill any air/water from the natural surface floor up to
                // floorY. If the surface is already higher than floorY, cut
                // the extra material off (down to floorY + 1 empty).
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                // Cap the fill depth so a deep ocean column doesn't turn
                // into a solid dirt tower to the seafloor; 8 blocks is
                // enough to give the palisade a stable footing without
                // creating an obvious dirt wall visible from the shore.
                int fillFrom = Math.max(floorY - 8, Math.min(surfaceY, floorY));
                for (int y = fillFrom; y <= floorY; y++) {
                    BlockPos p = new BlockPos(wx, y, wz);
                    if (replace(level, state, p, y == floorY ? grass : dirt)) ops++;
                }
                // Clear excess terrain above the plane so the camp has
                // headroom. Leaves and vegetation are already filtered by
                // CampVegetation.replaceable; solid stone/dirt on a hill
                // gets removed here.
                for (int y = floorY + 1; y <= ceilingY; y++) {
                    BlockPos p = new BlockPos(wx, y, wz);
                    if (replace(level, state, p, Blocks.AIR.defaultBlockState())) ops++;
                }
            }
        }
        FactionLogger.LOG.info("Terraformed camp footprint at {} ({}x{}, {} ops)",
                center, halfExtent * 2 + 1, halfExtent * 2 + 1, ops);
        return ops;
    }

    /**
     * Perform a single expansion step for the given camp: scan a small
     * ring of columns around {@code center} at radius {@code radius},
     * pave the FIRST hostile column found (water surface or cliff drop),
     * and return. One block per call keeps this cheap enough to run
     * every raid tick.
     *
     * <p>Uses {@code state.terrainSmoothingCursor} as a running scan
     * index so successive calls cover the whole ring over time instead
     * of always looking at the same spots.
     */
    public static boolean smoothOnce(ServerLevel level, RaidSavedData.RaidState state, int radius) {
        if (state.campPos == null) return false;
        int cx = state.campPos.getX();
        int cz = state.campPos.getZ();
        int floorY = state.campPos.getY() - 1;
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        // 40 columns visited per call, resuming from the cursor.
        int diameter = radius * 2 + 1;
        int total = diameter * diameter;
        int visited = 0;
        while (visited++ < 40) {
            int idx = state.terrainSmoothingCursor++;
            if (state.terrainSmoothingCursor >= total) state.terrainSmoothingCursor = 0;
            int dx = (idx % diameter) - radius;
            int dz = (idx / diameter) - radius;
            // Only smooth OUTSIDE the palisade footprint; skip a 10-block
            // core so we don't churn the camp itself.
            if (Math.abs(dx) <= 10 && Math.abs(dz) <= 10) continue;
            int wx = cx + dx;
            int wz = cz + dz;
            if (!level.hasChunkAt(new BlockPos(wx, floorY, wz))) continue;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
            // Water sitting at or just below the plane: fill it in.
            if (surfaceY < floorY && !level.getFluidState(new BlockPos(wx, floorY, wz)).isEmpty()) {
                for (int y = Math.max(surfaceY, floorY - 4); y <= floorY; y++) {
                    replace(level, state, new BlockPos(wx, y, wz), y == floorY ? grass : dirt);
                }
                return true;
            }
            // Cliff drop: bring the top of the column up to the plane.
            if (surfaceY < floorY - 1) {
                for (int y = Math.max(surfaceY + 1, floorY - 4); y <= floorY; y++) {
                    replace(level, state, new BlockPos(wx, y, wz), y == floorY ? grass : dirt);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Replace {@code pos} with {@code target} if the current block is a
     * natural terrain block (air, water, dirt, sand, gravel, stone, leaves,
     * plants). Player-placed blocks and structures are left alone. Records
     * the original state in the raid's cleanup ledger so it can be restored
     * when the raid ends.
     */
    private static boolean replace(ServerLevel level, RaidSavedData.RaidState state,
                                     BlockPos pos, BlockState target) {
        BlockState current = level.getBlockState(pos);
        if (current.equals(target)) return false;
        if (!isNaturalTerrain(current)) return false;
        CompoundTag original = NbtUtils.writeBlockState(current);
        level.setBlock(pos, target, 3);
        // Register the resource-location string of the placed block. Air
        // gets tracked too so cleanup can put water/whatever back where
        // we cleared it.
        String placedId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(target.getBlock()).toString();
        state.recordCampBlock(pos.asLong(), placedId, original);
        return true;
    }

    /**
     * Return true only for blocks that were placed by world generation:
     * air, fluids, common overworld surface / subsurface blocks, and
     * ordinary vegetation. Player builds (any block outside these
     * whitelisted categories) are preserved.
     */
    private static boolean isNaturalTerrain(BlockState state) {
        // Air and fluid are always fair game.
        if (state.isAir() || !state.getFluidState().isEmpty()) return true;
        // Delegate the vegetation / naturally-replaceable check to the
        // existing camp helper so both paths agree on what counts as
        // player-owned vs natural.
        if (CampVegetation.replaceable(state)) return true;
        var block = state.getBlock();
        return block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL
                || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                || block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL
                || block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.DIORITE
                || block == Blocks.ANDESITE || block == Blocks.TUFF || block == Blocks.DEEPSLATE
                || block == Blocks.CLAY || block == Blocks.MOSS_BLOCK || block == Blocks.MYCELIUM
                || block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.ICE
                || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE;
    }

    /**
     * Loosened validity check for a fallback camp site: same footprint as
     * the strict version, but tolerates water and larger height variance
     * because {@link #paveFootprint} will normalize the surface before the
     * palisade lands. Still rejects world-border / dimension issues and
     * player-owned blocks inside the footprint.
     *
     * @param maxVariance vertical variance the terraformer will tolerate
     * (from config: {@code campTerraformMaxDepth}).
     */
    public static boolean acceptableForTerraforming(ServerLevel level, BlockPos center,
                                                    BlockPos anchor, int maxVariance) {
        if (!level.getWorldBorder().isWithinBounds(center)) return false;
        if (Math.abs(center.getY() - anchor.getY()) > 64) return false;
        int floorY = center.getY() - 1;
        final int r = 9;
        for (int dx = -r; dx <= r; dx += 3) {
            for (int dz = -r; dz <= r; dz += 3) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) return false;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ());
                if (Math.abs(y - center.getY()) > maxVariance) return false;
                // Reject if a player build sits inside the footprint.
                BlockState surface = level.getBlockState(new BlockPos(column.getX(), y - 1, column.getZ()));
                if (!surface.isAir() && !surface.getFluidState().isEmpty()) continue;
                if (!surface.isAir() && !CampVegetation.replaceable(surface)
                        && !isNaturalSurface(surface)) return false;
                // Also make sure the plane column isn't full of a player build.
                BlockState plane = level.getBlockState(new BlockPos(column.getX(), floorY, column.getZ()));
                if (!plane.isAir() && plane.getFluidState().isEmpty()
                        && !CampVegetation.replaceable(plane) && !isNaturalSurface(plane)) return false;
            }
        }
        return true;
    }

    private static boolean isNaturalSurface(BlockState s) {
        var b = s.getBlock();
        return b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL
                || b == Blocks.COARSE_DIRT || b == Blocks.ROOTED_DIRT
                || b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.GRAVEL
                || b == Blocks.STONE || b == Blocks.GRANITE || b == Blocks.DIORITE
                || b == Blocks.ANDESITE || b == Blocks.TUFF || b == Blocks.DEEPSLATE
                || b == Blocks.CLAY || b == Blocks.MOSS_BLOCK || b == Blocks.MYCELIUM
                || b == Blocks.SNOW || b == Blocks.SNOW_BLOCK || b == Blocks.ICE
                || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE;
    }
}
