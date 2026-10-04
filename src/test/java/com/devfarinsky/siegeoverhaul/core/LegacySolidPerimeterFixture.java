package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Independent pre-hollow one-claim geometry. Never call the current compiler to construct this fixture. */
final class LegacySolidPerimeterFixture {
    private LegacySolidPerimeterFixture() {}

    static PerimeterBlueprint.Plan plan() {
        var targets = new LinkedHashMap<Long, String>();
        var columns = new ArrayList<PerimeterBlueprint.Column>();
        var clearance = new LinkedHashSet<Long>();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int inward = Math.min(Math.min(x + 1, 16 - x), Math.min(z + 1, 16 - z));
            if (inward > 5) continue;
            columns.add(new PerimeterBlueprint.Column(new BlockPos(x, 64, z), 0, inward, 0));
            for (int y = 0; y < 6; y++) {
                long cell = new BlockPos(x, 64 + y, z).asLong();
                String material = y < 3 ? "minecraft:cobblestone" : y == 3 ? "minecraft:oak_planks"
                        : y == 4 && (inward == 1 || inward == 5) ? "minecraft:cobblestone" : null;
                if (material == null) clearance.add(cell); else targets.put(cell, material);
            }
        }
        return new PerimeterBlueprint.Plan(targets, columns, clearance,
                new BlockPos(0, 64, 0), new BlockPos(15, 69, 15), List.of(), List.of(),
                Map.of("minecraft:cobblestone", 748, "minecraft:oak_planks", 220), List.of());
    }
}
