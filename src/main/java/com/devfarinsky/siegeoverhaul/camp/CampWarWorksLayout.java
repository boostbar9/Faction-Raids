package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finite blueprints for the war works a camp keeps adding after its perimeter: the shared
 * faction hall with a distinct interior and keystone, plus small tents for later expansion.
 * Local positive Z points toward the entrance; the keystone is always at x=0, y=1, z=-2.
 */
final class CampWarWorksLayout {
    private CampWarWorksLayout() {}

    static Map<Long, String> structure(BlockPos center, Direction entrance, CampWarWorks.Work work, String factionId) {
        Map<Long, String> plan = CampUpgradeLayout.hall(center, entrance, work.ordinal(), factionId);
        switch (work) {
            case SUPPLY_DEPOT -> {
                for (int x : new int[]{-2, 2}) for (int z = -2; z <= 0; z++)
                    put(plan, center, entrance, x, 1, z, z == 0 ? "minecraft:hay_block" : "minecraft:spruce_log");
                put(plan, center, entrance, -2, 2, -2, "minecraft:hay_block");
                put(plan, center, entrance, 2, 2, -2, "minecraft:spruce_log");
            }
            case LOOKOUT -> {
                // A signal spire above the eave makes the lookout readable from the defenders' walls.
                for (int y = 5; y <= 7; y++) put(plan, center, entrance, 0, y, 0, "minecraft:spruce_log");
                put(plan, center, entrance, 0, 8, 0, "minecraft:lantern");
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos arm = center.relative(side).above(7);
                    plan.put(arm.asLong(), "minecraft:spruce_fence");
                }
                put(plan, center, entrance, -2, 1, -2, "minecraft:hay_block");
                put(plan, center, entrance, 2, 1, -2, "minecraft:crafting_table");
            }
            case INFIRMARY -> {
                for (int x : new int[]{-2, 2}) for (int z = -2; z <= 1; z++)
                    put(plan, center, entrance, x, 1, z, z == -2 ? "minecraft:hay_block" : "minecraft:white_wool");
            }
            case WEAPONSMITH -> {
                put(plan, center, entrance, -2, 1, -2, "minecraft:anvil");
                put(plan, center, entrance, 2, 1, -2, "minecraft:stonecutter");
                put(plan, center, entrance, -2, 1, -1, "minecraft:smithing_table");
                put(plan, center, entrance, 2, 1, -1, "minecraft:crafting_table");
            }
            case BARRACKS -> {
                for (int x : new int[]{-2, 2}) for (int z = -2; z <= 1; z++)
                    put(plan, center, entrance, x, 1, z, "minecraft:red_wool");
            }
            case WAR_SHRINE -> {
                for (int x : new int[]{-1, 1}) put(plan, center, entrance, x, 1, -2, "minecraft:amethyst_block");
                put(plan, center, entrance, -2, 1, -2, "minecraft:crying_obsidian");
                put(plan, center, entrance, 2, 1, -2, "minecraft:crying_obsidian");
            }
        }
        put(plan, center, entrance, 0, 1, -2, work.keystoneBlock);
        return plan;
    }

    /**
     * A five-by-five ridge tent in the camp colours, standing at {@code floor} height. Columns
     * whose ground is lower get a dirt pad so nothing floats. About forty cells.
     */
    static Map<Long, String> tent(BlockPos floor, Map<Long, Integer> groundY, String cloth) {
        Map<Long, String> plan = new LinkedHashMap<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos column = floor.offset(dx, 0, dz);
            int ground = groundY.getOrDefault(column.atY(0).asLong(), floor.getY());
            for (int y = ground; y < floor.getY(); y++) plan.put(column.atY(y).asLong(), "minecraft:dirt");
        }
        for (int dz = -2; dz <= 2; dz++) {
            // Ridge along Z: sides at height 1, slopes at 2, ridge at 3; open ends.
            for (int dx = -2; dx <= 2; dx++) {
                int height = 3 - Math.abs(dx);
                plan.put(floor.offset(dx, height, dz).asLong(), cloth);
            }
            if (Math.abs(dz) == 2) plan.put(floor.offset(0, 1, dz).asLong(), "minecraft:spruce_fence");
        }
        plan.put(floor.offset(0, 1, 0).asLong(), "minecraft:hay_block");
        return plan;
    }

    private static void put(Map<Long, String> plan, BlockPos center, Direction front, int x, int y, int z, String block) {
        CampUpgradeLayout.put(plan, center, front, x, y, z, block);
    }
}
