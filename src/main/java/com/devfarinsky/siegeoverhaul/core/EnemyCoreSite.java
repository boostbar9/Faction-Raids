package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.camp.CampVegetation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Predicate;

/** Bounded courtyard selection: rear first, away from the central gate/road axis. */
public final class EnemyCoreSite {
    private EnemyCoreSite() {}
    static List<BlockPos> candidates(BlockPos camp, Direction front) {
        List<BlockPos> sites = new ArrayList<>();
        for (int dy : new int[]{0, 1, -1, 2, -2}) sites.add(camp.above(dy));
        for (int depth : new int[]{-6, -3, 0, 3, 6})
            for (int side : new int[]{-5, 5, -8, 8})
                for (int dy : new int[]{0, 1, -1, 2, -2})
                    sites.add(camp.relative(front, depth).relative(front.getClockWise(), side).above(dy));
        return List.copyOf(sites);
    }
    static boolean clear(ServerLevel level, RaidSavedData.RaidState raid, BlockPos center,
                         Predicate<BlockPos> allowed) {
        // Check a level, dry 5x5 keep footprint with seven blocks of open headroom.
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
            BlockPos feet=center.offset(x,0,z);
            for (int y=-1;y<=6;y++) {
                BlockPos p=feet.above(y);
                if (p.getY()<level.getMinBuildHeight() || p.getY()>=level.getMaxBuildHeight()
                        || !level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || !allowed.test(p)) return false;
                var state=level.getBlockState(p);
                if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
                if (y<0 ? !state.isFaceSturdy(level,p,Direction.UP) : !CampVegetation.replaceable(state)) return false;
            }
            // Do not occupy a planned building, even when its roof is above the clearance box.
            for (long key : raid.pendingCampBlocks.keySet()) if (sameColumn(feet,BlockPos.of(key))) return false;
            for (long key : raid.pendingFortifications.keySet()) if (sameColumn(feet,BlockPos.of(key))) return false;
            for (String key : raid.warGate.getCompound("RoadBlocks").getAllKeys()) {
                try { if (sameColumn(feet,BlockPos.of(Long.parseLong(key)))) return false; }
                catch (NumberFormatException ignored) { /* Invalid legacy ledger cell. */ }
            }
        }
        return true;
    }
    private static boolean sameColumn(BlockPos a, BlockPos b) { return a.getX()==b.getX() && a.getZ()==b.getZ(); }
    /** Keep future construction and player obstructions out of the saved walking ring. */
    public static boolean reserved(RaidSavedData.RaidState raid, BlockPos pos) {
        BlockPos core=EnemyCore.position(raid);
        return raid.campaign.getBoolean("EnemyCoreCourtyard") && core!=null
                && Math.abs(pos.getX()-core.getX())<=2 && Math.abs(pos.getZ()-core.getZ())<=2
                && pos.getY()>=core.getY()-2 && pos.getY()<=core.getY()+5;
    }
}
