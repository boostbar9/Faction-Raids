package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded full-block plans; stairs are walkable one-block steps, not special block states. */
public final class DefenseBlueprint {
    public enum Kind {
        BARRICADE("Archer Barricade", 7, 3, 3, 120,
                "A low stone screen with a raised firing step."),
        WATCHTOWER("Watchtower", 5, 9, 6, 300,
                "An open firing deck with a broad stepped approach."),
        GATEHOUSE("Gatehouse", 9, 9, 6, 450,
                "A raised fighting deck over an open three-block passage. No moving gate.");

        public final String label, description;
        public final int width, depth, height, price;
        Kind(String label, int width, int depth, int height, int price, String description) {
            this.label = label; this.width = width; this.depth = depth;
            this.height = height; this.price = price; this.description = description;
        }
        public String dimensions() { return width + " wide x " + depth + " deep x " + height + " clear"; }
    }

    public record Plan(Map<Long, String> blocks, List<BlockPos> footprint, BlockPos min, BlockPos max) {
        public Plan { blocks = Map.copyOf(blocks); footprint = List.copyOf(footprint); }
        public String materials() {
            long stone = blocks.values().stream().filter("minecraft:cobblestone"::equals).count();
            return stone + " cobblestone + " + (blocks.size() - stone) + " oak planks";
        }
    }
    private DefenseBlueprint() {}

    /** Origin is the near-center ground cell; the plan extends in the player's facing direction. */
    public static Plan create(Kind kind, BlockPos origin, Direction forward) {
        if (forward.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal facing required");
        Direction right = forward.getClockWise();
        Map<Long, String> blocks = new LinkedHashMap<>();
        var footprint = new java.util.ArrayList<BlockPos>();
        int half = kind.width / 2;
        for (int x = -half; x <= half; x++) for (int z = 0; z < kind.depth; z++) {
            BlockPos base = origin.relative(right, x).relative(forward, z);
            footprint.add(base);
            for (int y = 0; y < kind.height; y++) {
                String material = material(kind, x, y, z);
                if (material != null) blocks.put(base.above(y).asLong(), material);
            }
        }
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        return new Plan(blocks, footprint, new BlockPos(minX, origin.getY(), minZ),
                new BlockPos(maxX, origin.getY() + kind.height - 1, maxZ));
    }

    private static String material(Kind kind, int x, int y, int z) {
        String stone = "minecraft:cobblestone", wood = "minecraft:oak_planks";
        if (kind == Kind.BARRICADE) {
            if (z == 2 && (y == 0 || y == 1 && Math.abs(x) % 2 == 1)) return stone;
            return z == 1 && y == 0 ? wood : null;
        }
        int half = kind.width / 2;
        boolean stair = kind == Kind.WATCHTOWER ? x == 0 || x == 1 : x == -4 || x == -3;
        if (z < 4) return stair && y <= z ? stone : null;
        if (y < 3) {
            boolean pier = kind == Kind.GATEHOUSE ? Math.abs(x) >= 2
                    : Math.abs(x) == half && (z == 4 || z == 8);
            return pier ? stone : null;
        }
        if (y == 3) return wood;
        boolean edge = Math.abs(x) == half || z == 4 || z == 8;
        if (y == 4 && edge && !(z == 4 && stair)) return stone;
        // Keep two blocks of headroom above both stair entrances.
        if (y == 5 && Math.abs(x) == half && (z == 4 || z == 8)
                && !(z == 4 && stair)) return stone;
        return null;
    }
}
