package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Set;

/** Read-only surface and local work-access checks for new player wall commissions. */
final class WallSurface {
    private WallSurface() {}

    /** Carries the actual rejected column to the free review without changing placement rules. */
    record Ground(BlockPos base, String problem) {
        static Ground ready(BlockPos base) { return new Ground(base, null); }
        static Ground blocked(String problem) { return new Ground(null, problem); }
    }

    static BlockPos base(ServerLevel level, Set<ChunkPos> claim, BlockPos column) {
        BlockPos surface = ground(level, column);
        if (surface == null) return null;
        BlockPos best = null;
        // An actual surface cannot be clamped to core height: that creates jobs
        // inside hills, or suspended over ravines. Reject inaccessible sections.
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
            if (dx==0 && dz==0) continue;
            BlockPos neighbor = surface.offset(dx,0,dz);
            if (!interior(claim, neighbor)) continue;
            BlockPos stand = ground(level, neighbor);
            if (stand == null) continue;
            // Raise shallow low spots to accessible interior ground. Never lower
            // the blueprint into a hill or fill a gap deeper than our budget.
            BlockPos base = surface.atY(Math.max(surface.getY(), stand.getY()));
            if (base.getY() - surface.getY() > TerritoryFortification.FOUNDATION_DEPTH
                    || Math.abs(stand.getY() - base.getY()) > 1
                    || base.getY() + TerritoryFortification.WALL_HEIGHT
                    + TerritoryFortification.CORNER_EXTRA > level.getMaxBuildHeight()) continue;
            boolean foundationClear = true;
            for (int y=surface.getY(); y<base.getY(); y++) {
                BlockPos fill = surface.atY(y);
                if (!TerritoryFortification.safeWallReplacement(level.getBlockState(fill))
                        || !level.getWorldBorder().isWithinBounds(fill)) { foundationClear=false; break; }
            }
            if (!foundationClear) continue;
            boolean clear = true;
            for (int dy = 0; dy < 2; dy++) {
                BlockPos p = stand.above(dy);
                if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || !TerritoryFortification.safeWallReplacement(level.getBlockState(p))
                        || level.getBlockState(p).is(Blocks.WITHER_ROSE)) { clear = false; break; }
            }
            if (clear && (best == null || base.getY() < best.getY())) best = base;
        }
        return best;
    }

    private static boolean interior(Set<ChunkPos> claim, BlockPos p) {
        ChunkPos chunk=new ChunkPos(p);
        if (!claim.contains(chunk)) return false;
        return !(p.getX()==chunk.getMinBlockX() && !claim.contains(new ChunkPos(chunk.x-1,chunk.z)))
                && !(p.getX()==chunk.getMaxBlockX() && !claim.contains(new ChunkPos(chunk.x+1,chunk.z)))
                && !(p.getZ()==chunk.getMinBlockZ() && !claim.contains(new ChunkPos(chunk.x,chunk.z-1)))
                && !(p.getZ()==chunk.getMaxBlockZ() && !claim.contains(new ChunkPos(chunk.x,chunk.z+1)));
    }

    private static boolean naturalGround(net.minecraft.world.level.block.state.BlockState state) {
        return com.devfarinsky.siegeoverhaul.camp.CampRoad.soil(state)
                || state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.MUD) || state.is(Blocks.SNOW_BLOCK);
    }

    static BlockPos ground(ServerLevel level, BlockPos column) {
        return inspectGround(level, column).base();
    }

    static Ground inspectGround(ServerLevel level, BlockPos column) {
        String horizontal = "X " + column.getX() + ", Z " + column.getZ();
        if (!level.hasChunkAt(column))
            return Ground.blocked("Perimeter terrain is not loaded at " + horizontal + ". Load the whole boundary and review again; no payment taken.");
        if (!level.getWorldBorder().isWithinBounds(column))
            return Ground.blocked("The perimeter crosses the world border at " + horizontal + ". No payment taken.");
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        // Ignore canopy height, but never put jobs beneath preserved tree trunks.
        for (int i=0; i<16 && y>level.getMinBuildHeight(); i++,y--) {
            BlockPos below=column.atY(y-1);
            var support=level.getBlockState(below);
            if (support.is(BlockTags.LEAVES) || support.is(BlockTags.LOGS) || support.is(BlockTags.SAPLINGS)) continue;
            if (y>=level.getMaxBuildHeight())
                return Ground.blocked("The perimeter footing exceeds the build height at " + horizontal + ". No payment taken.");
            if (!support.getFluidState().isEmpty() || support.is(Blocks.WATER) || support.is(Blocks.LAVA))
                return Ground.blocked("Fluid blocks perimeter footing at " + describe(below, support) + ". Fluids are protected; no payment taken.");
            if (!naturalGround(support))
                return Ground.blocked("Perimeter footing is not natural ground at " + describe(below, support) + ". Existing structures are protected; no payment taken.");
            if (!support.isFaceSturdy(level,below,Direction.UP)
                    || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                    || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS))
                return Ground.blocked("Perimeter footing is not a safe solid surface at " + describe(below, support) + ". No payment taken.");
            BlockPos feet=column.atY(y);
            var occupied = level.getBlockState(feet);
            String compatibility = com.devfarinsky.siegeoverhaul.camp.CampVegetation.optionalPlantProblem(occupied);
            if (compatibility != null) return Ground.blocked("Perimeter build space is occupied at "
                    + describe(feet, occupied) + ". " + compatibility + " No payment taken.");
            return TerritoryFortification.safeWallReplacement(occupied) ? Ground.ready(feet)
                    : Ground.blocked("Perimeter build space is occupied at " + describe(feet, occupied)
                    + ". Existing blocks are protected; no payment taken.");
        }
        return Ground.blocked("No natural perimeter footing was found within the bounded surface check at " + horizontal + ". No payment taken.");
    }

    private static String describe(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return pos.toShortString() + " (" + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()) + ")";
    }
}
