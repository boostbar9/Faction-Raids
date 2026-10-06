package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Converts the stepped physical proposal back into the existing paid native wall plan format. */
public final class PerimeterSteppedBlueprint {
    private PerimeterSteppedBlueprint() {}

    public static PerimeterBlueprint.Plan convert(Set<ChunkPos> chunks, PerimeterSteppedGeometry.Draft draft) {
        if (chunks == null || draft == null || !draft.feasible()) throw invalid("A feasible stepped draft is required");
        Set<PerimeterSteppedTopology.Chunk> claim = new java.util.TreeSet<>();
        for (ChunkPos chunk : chunks) claim.add(new PerimeterSteppedTopology.Chunk(chunk.x, chunk.z));
        var topology = PerimeterSteppedTopology.create(claim);
        if (!topology.valid()) throw invalid(topology.problem());

        Map<Long, String> blocks = new LinkedHashMap<>();
        Map<String, Integer> materials = new LinkedHashMap<>();
        Map<PerimeterSteppedTopology.Cell, Integer> fills = new TreeMap<>();
        draft.targets().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var pos = entry.getKey(); var target = entry.getValue();
            String id = blockId(target.block());
            blocks.put(new BlockPos(pos.x(), pos.y(), pos.z()).asLong(), id);
            materials.merge(id, 1, Integer::sum);
            if (target.phase() == PerimeterSteppedGeometry.Phase.FILL)
                fills.merge(new PerimeterSteppedTopology.Cell(pos.x(), pos.z()), 1, Integer::sum);
        });

        List<PerimeterBlueprint.Column> columns = new ArrayList<>();
        for (var entry : topology.columns().entrySet()) {
            var cell = entry.getKey(); var column = entry.getValue();
            Integer base = draft.levels().get(cell);
            if (base == null) throw invalid("Stepped draft is missing a wall level");
            int supportDepth = fills.getOrDefault(cell, 0);
            for (int y = base - supportDepth; y < base; y++) {
                var target = draft.targets().get(new PerimeterSteppedGeometry.Pos(cell.x(), y, cell.z()));
                if (target == null || target.phase() != PerimeterSteppedGeometry.Phase.FILL
                        || target.block() != PerimeterSteppedGeometry.Block.DIRT)
                    throw invalid("Stepped support fill is not contiguous below the wall");
            }
            columns.add(new PerimeterBlueprint.Column(new BlockPos(cell.x(), base, cell.z()),
                    supportDepth, column.inwardDistance(), column.component()));
        }

        Set<Long> clearance = new LinkedHashSet<>();
        draft.clearance().stream().sorted().forEach(pos -> {
            if (topology.columns().containsKey(pos.column()))
                clearance.add(new BlockPos(pos.x(), pos.y(), pos.z()).asLong());
        });
        Set<Long> cells = new java.util.HashSet<>(blocks.keySet()); cells.addAll(clearance);
        if (cells.isEmpty()) throw invalid("Converted stepped plan has no native cells");
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long packed : cells) {
            BlockPos pos = BlockPos.of(packed);
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        return new PerimeterBlueprint.Plan(blocks, columns, clearance, new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ), List.of(), List.of(), materials, List.of());
    }

    private static String blockId(PerimeterSteppedGeometry.Block block) {
        return switch (block) {
            case COBBLESTONE -> "minecraft:cobblestone";
            case STONE_BRICKS -> "minecraft:stone_bricks";
            case OAK_PLANKS -> "minecraft:oak_planks";
            case DIRT -> "minecraft:dirt";
        };
    }

    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
}
