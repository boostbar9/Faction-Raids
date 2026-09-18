package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
     * Populate {@code state.terraformQueue} with every block position that
     * needs paving for the camp footprint, ordered from the center outward
     * in concentric rings. Does NOT touch the world - callers must drain
     * the queue via {@link #tickTerraformQueue} so the paving is visible
     * as a build-out over time instead of a single instant slap of dirt.
     *
     * <p>Ordering matters: center-out reads as "workers laid the middle
     * pad first, then expanded to the edges", which is how a real crew
     * would work. Random ordering looks like glitchy world generation.
     */
    public static int queueFootprint(ServerLevel level, RaidSavedData.RaidState state,
                                     BlockPos center, int halfExtent) {
        return queueRing(level, state, center, halfExtent, 0, halfExtent + 2);
    }

    /**
     * Populate the terraform queue with a Chebyshev-ring band {@code [startRing, endRing]}
     * around {@code center}. Use this to split paving into phases: an
     * inner synchronous fill under the palisade, then an outer expansion
     * that visibly builds over time. Successive calls with the same
     * center accumulate positions in the queue.
     */
    public static int queueRing(ServerLevel level, RaidSavedData.RaidState state,
                                BlockPos center, int halfExtent, int startRing, int endRing) {
        if (state.terraformCenter == null || !state.terraformCenter.equals(center)) {
            state.terraformQueue.clear();
            state.terraformCenter = center;
            state.terraformHalfExtent = halfExtent;
        }
        int floorY = center.getY() - 1;
        int ceilingY = center.getY() + 4;
        int total = 0;
        // Center-out ring walk: ring 0 is the center column, ring N is the
        // square shell at Chebyshev distance N. v4.36.0 polish: pave one
        // extra ring past the palisade so the outer edge of the camp
        // meets the natural terrain on a level border instead of
        // dropping straight from grass to a cliff or water.
        for (int ring = startRing; ring <= endRing; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int wx = center.getX() + dx;
                    int wz = center.getZ() + dz;
                    if (!level.hasChunkAt(new BlockPos(wx, floorY, wz))) continue;
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                    // Outer polish ring: keep the fill shallow (max 3
                    // blocks below the plane) so we tuck the border into
                    // the existing terrain instead of laying down an
                    // obvious dirt collar around the camp.
                    int maxDepth = ring > halfExtent ? 3 : 8;
                    int fillFrom = Math.max(floorY - maxDepth, Math.min(surfaceY, floorY));
                    // Fill columns bottom-up so the workers appear to lay
                    // dirt starting from the floor and stack up.
                    for (int y = fillFrom; y <= floorY; y++) {
                        state.terraformQueue.add(new BlockPos(wx, y, wz).asLong());
                        total++;
                    }
                    // Then clear excess terrain above the plane, top-down
                    // (so tall obstacles collapse from the top).
                    for (int y = ceilingY; y > floorY; y--) {
                        state.terraformQueue.add(new BlockPos(wx, y, wz).asLong());
                        total++;
                    }
                }
            }
        }
        FactionLogger.LOG.info("Queued {} terraforming ops for camp at {} ({}x{})",
                total, center, halfExtent * 2 + 1, halfExtent * 2 + 1);
        return total;
    }

    /**
     * Synchronously pave the palisade-only rings (0..{@code halfExtent}).
     * Used when the fence needs solid ground immediately; call
     * {@link #queueRing} afterwards to add the over-time outer polish
     * band without disturbing the just-placed inner fill.
     */
    public static int paveFootprint(ServerLevel level, RaidSavedData.RaidState state,
                                     BlockPos center, int halfExtent) {
        queueRing(level, state, center, halfExtent, 0, halfExtent);
        int ops = 0;
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        int floorY = center.getY() - 1;
        for (long packed : state.terraformQueue) {
            BlockPos p = BlockPos.of(packed);
            BlockState target = p.getY() > floorY ? Blocks.AIR.defaultBlockState()
                    : (p.getY() == floorY ? grass : dirt);
            if (replace(level, state, p, target)) ops++;
        }
        state.terraformQueue.clear();
        return ops;
    }

    /**
     * Drain up to {@code opsPerTick} block ops from {@code state.terraformQueue},
     * emitting a block-place sound and dirt particles at each block so the
     * paving reads as workers actively building the pad instead of a
     * single instantaneous slap of dirt. Returns true when the queue is
     * empty (so the caller can advance the raid to the next phase).
     */
    public static boolean tickTerraformQueue(ServerLevel level, RaidSavedData.RaidState state,
                                             int opsPerTick) {
        if (state.terraformQueue.isEmpty() || state.terraformCenter == null) return true;
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int floorY = state.terraformCenter.getY() - 1;
        int placed = 0;
        while (placed < opsPerTick && !state.terraformQueue.isEmpty()) {
            long packed = state.terraformQueue.remove(0);
            BlockPos p = BlockPos.of(packed);
            BlockState target = p.getY() > floorY ? air
                    : (p.getY() == floorY ? grass : dirt);
            BlockState pre = level.getBlockState(p);
            if (replace(level, state, p, target)) {
                // Placement sound + dirt particles so the block visibly
                // "gets built" instead of just appearing. For clears we
                // use the pre-existing block's break sound to sell the
                // removal.
                if (target.isAir()) {
                    level.playSound(null, p, pre.getSoundType().getBreakSound(),
                            SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pre),
                            p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6,
                            0.25, 0.25, 0.25, 0.02);
                } else {
                    level.playSound(null, p, target.getSoundType().getPlaceSound(),
                            SoundSource.BLOCKS, 0.7f, 0.9f + level.random.nextFloat() * 0.2f);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target),
                            p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 4,
                            0.2, 0.05, 0.2, 0.01);
                }
                placed++;
            }
            // Skipped ops (player builds we won't overwrite) don't count
            // against the per-tick budget - the loop just moves on until
            // it finds a placeable position or the queue empties.
        }
        if (state.terraformQueue.isEmpty()) {
            FactionLogger.LOG.info("Terraforming complete at {}", state.terraformCenter);
            state.terraformCenter = null;
            state.terraformHalfExtent = 0;
            return true;
        }
        return false;
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
        // v4.39.0: allow a few individual sample rejections rather than
        // failing the whole site on one bad column. A camp that is 90%
        // level with one tall pillar or one deep pit is still buildable
        // once the terraformer runs.
        int rejectSlack = 3;
        int used = 0;
        for (int dx = -r; dx <= r; dx += 3) {
            for (int dz = -r; dz <= r; dz += 3) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) return false;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ());
                if (Math.abs(y - center.getY()) > maxVariance) { if (++used > rejectSlack) return false; continue; }
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
