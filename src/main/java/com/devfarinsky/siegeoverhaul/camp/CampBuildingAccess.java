package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.EnemyCoreSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
import java.util.function.Predicate;

/** Bounded, three-wide entrance steps. Planning never changes the world. */
final class CampBuildingAccess {
    private CampBuildingAccess() {}

    static boolean clearInterior(ServerLevel level, RaidSavedData.RaidState raid,
                                 BlockPos center, Map<Long, String> plan) {
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) for (int y=1;y<=3;y++) {
            BlockPos p=center.offset(x,y,z);
            if (EnemyCoreSite.reserved(raid,p) || CampStructures.accessColumn(raid,p)) return false;
            if (!plan.containsKey(p.asLong()) && (!level.getFluidState(p).isEmpty()
                    || level.getBlockEntity(p)!=null || !CampVegetation.replaceable(level.getBlockState(p)))) return false;
        }
        return true;
    }

    static Optional<Map<Long, String>> plan(ServerLevel level, RaidSavedData.RaidState raid,
                                           BlockPos center, Direction entrance,
                                           Predicate<BlockPos> allowed) {
        Map<Long, String> result = new LinkedHashMap<>();
        int previous = center.getY(); // Pavilion floor block, not the NPC's feet.
        for (int depth = 4; depth <= 7; depth++) {
            BlockPos row = center.relative(entrance, depth);
            int[] ground = new int[3];
            int floor = previous - 1;
            for (int side = -1; side <= 1; side++) {
                BlockPos column = row.relative(entrance.getClockWise(), side);
                if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)
                        || !allowed.test(column)) return Optional.empty();
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ());
                ground[side + 1] = surface;
                floor = Math.max(floor, surface - 1);
                BlockPos support = column.atY(surface - 1);
                if (Math.abs(surface - center.getY()) > 2
                        || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                        || !level.getFluidState(support).isEmpty()) return Optional.empty();
            }
            if (Math.abs(floor - previous) > 1 || floor < level.getMinBuildHeight()
                    || floor + 2 >= level.getMaxBuildHeight()) return Optional.empty();
            boolean meetsGround = true;
            for (int side = -1; side <= 1; side++) {
                BlockPos column = row.relative(entrance.getClockWise(), side);
                int surface = ground[side + 1];
                meetsGround &= floor == surface - 1;
                // Validate both fill and two blocks of walking headroom. Never
                // overwrite a road, queued fortification, keep or player block.
                for (int y = surface; y <= floor + 2; y++) {
                    BlockPos p = column.atY(y);
                    if (EnemyCoreSite.reserved(raid, p) || CampStructures.accessColumn(raid,p)
                            || raid.campBlocks.containsKey(p.asLong())
                            || raid.pendingFortifications.containsKey(p.asLong())
                            || !level.getFluidState(p).isEmpty() || level.getBlockEntity(p) != null
                            || !CampVegetation.replaceable(level.getBlockState(p))) return Optional.empty();
                    if (y <= floor) result.put(p.asLong(), "minecraft:spruce_planks");
                }
            }
            if (depth >= 5 && meetsGround) return Optional.of(result);
            previous = floor;
        }
        return Optional.empty();
    }
}
