package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Independent flat QA geometry. Never derives expected cells from the production planner's columns. */
final class NativeHollowWallOracle {
    private NativeHollowWallOracle() {}

    static Map<Long, String> targets(Set<ChunkPos> territory) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (ChunkPos chunk : territory) for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int distance = distance(territory, x, z);
                if (distance > 5) continue;
                result.put(new BlockPos(x, 68, z).asLong(), "minecraft:oak_planks");
                if (distance == 1 || distance == 5)
                    for (int y : new int[] {65, 66, 67, 69}) result.put(new BlockPos(x, y, z).asLong(), "minecraft:cobblestone");
            }
        return Map.copyOf(result);
    }

    static Set<Long> cavities(Set<ChunkPos> territory) {
        Set<Long> result = new HashSet<>();
        for (ChunkPos chunk : territory) for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int distance = distance(territory, x, z);
                if (distance >= 2 && distance <= 4)
                    for (int y = 65; y <= 67; y++) result.add(new BlockPos(x, y, z).asLong());
            }
        return Set.copyOf(result);
    }

    static void assertFlatPlan(PerimeterBlueprint.Plan plan, Set<ChunkPos> territory) {
        Map<Long, String> expected = targets(territory);
        Set<Long> clearance = new HashSet<>();
        for (ChunkPos chunk : territory) for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                if (distance(territory, x, z) > 5) continue;
                for (int y = 65; y <= 70; y++) {
                    long cell = new BlockPos(x, y, z).asLong();
                    if (!expected.containsKey(cell)) clearance.add(cell);
                }
            }
        require(plan.blocks().equals(expected) && plan.clearance().equals(clearance),
                "Flat production geometry differs from independent exact hollow targets/air oracle");
        require(!plan.blocks().containsValue("minecraft:air") && plan.clearance().containsAll(cavities(territory)),
                "Body cavities must be protected clearance, never AIR placement targets");
    }

    static void assertCavitiesAir(ServerLevel level, Set<ChunkPos> territory) {
        for (long packed : cavities(territory)) {
            BlockPos cell = BlockPos.of(packed);
            require(level.getBlockState(cell).isAir(), "Native construction changed a protected cavity: " + cell);
            require(level.getBlockState(cell.atY(64)).is(Blocks.STONE), "Native construction changed the full-width ground footing: " + cell);
        }
    }

    static Set<Long> manualCavities(BlockPos anchor) {
        Set<Long> result = new HashSet<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 3; y++)
            result.add(anchor.offset(x, y, z).asLong());
        return Set.copyOf(result);
    }

    /** This live manual fixture faces SOUTH; its end caps, deck, side skins and rails are explicit. */
    static void assertManualWallPlan(DefenseBlueprint.Plan plan, BlockPos anchor) {
        Map<Long, String> expected = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            for (int y = 0; y < 3; y++) if (Math.abs(x) == 2 || Math.abs(z) == 2)
                expected.put(anchor.offset(x, y, z).asLong(), "minecraft:cobblestone");
            expected.put(anchor.offset(x, 3, z).asLong(), "minecraft:oak_planks");
            if (Math.abs(x) == 2) expected.put(anchor.offset(x, 4, z).asLong(), "minecraft:cobblestone");
        }
        require(expected.size() == 83 && expected.values().stream().filter("minecraft:cobblestone"::equals).count() == 58
                && plan.blocks().equals(expected), "Live WALL differs from independent capped hollow 58-cobble/25-oak oracle");
    }

    static void assertManualCavitiesAir(ServerLevel level, BlockPos anchor) {
        for (long packed : manualCavities(anchor)) require(level.getBlockState(BlockPos.of(packed)).isAir(),
                "Native manual construction changed a protected body cavity: " + BlockPos.of(packed));
    }

    private static int distance(Set<ChunkPos> territory, int x, int z) {
        int distance = 6;
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
            if (!territory.contains(new ChunkPos(new BlockPos(x + dx, 65, z + dz))))
                distance = Math.min(distance, Math.max(Math.abs(dx), Math.abs(dz)));
        return distance;
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
