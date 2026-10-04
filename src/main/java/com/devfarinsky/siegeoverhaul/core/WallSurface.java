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
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        // Ignore canopy height, but never put jobs beneath preserved tree trunks.
        for (int i=0; i<16 && y>level.getMinBuildHeight(); i++,y--) {
            BlockPos below=column.atY(y-1);
            var support=level.getBlockState(below);
            if (support.is(BlockTags.LEAVES) || support.is(BlockTags.LOGS) || support.is(BlockTags.SAPLINGS)) continue;
            if (y>=level.getMaxBuildHeight() || !naturalGround(support)
                    || !support.isFaceSturdy(level,below,Direction.UP)
                    || !support.getFluidState().isEmpty() || support.is(Blocks.WATER) || support.is(Blocks.LAVA)
                    || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                    || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS)) return null;
            BlockPos feet=column.atY(y);
            return TerritoryFortification.safeWallReplacement(level.getBlockState(feet)) ? feet : null;
        }
        return null;
    }
}
