package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.nativecompat.BlueprintNetworkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterStageLayoutTest extends MinecraftTestSupport {
    @Test void fiveByFiveUsesActualNativeQuotaAndPreservesAll3900Targets() {
        var plan = flat(rectangle(-2, -2, 5, 5));
        assertTrue(plan.valid(), plan.problemSummary()); assertEquals(3900, plan.blocks().size());
        String wholeProblem = BlueprintNetworkBudget.problem(TerritoryFortification.blueprint(plan.blocks(), plan.min(), plan.max()));
        var layout = PerimeterStageLayout.partition(plan, stage -> BlueprintNetworkBudget.problem(
                TerritoryFortification.blueprint(stage.targets(), stage.min(), stage.max())));
        assertTrue(layout.stages().size() >= 1);
        if (wholeProblem != null) assertTrue(layout.stages().size() > 1);
        assertExactPartition(plan, layout);
        for (var stage : layout.stages()) {
            assertTrue(PerimeterStageLayout.withinBounds(stage));
            assertNull(BlueprintNetworkBudget.problem(TerritoryFortification.blueprint(stage.targets(), stage.min(), stage.max())));
        }
        assertFalse(plan.blocks().containsKey(new BlockPos(0, 64, 0).asLong()), "Interior shared chunk borders stay open");
    }

    @Test void holesNegativeCoordinatesAndDisconnectedComponentsKeepExactGlobalPlan() {
        Set<ChunkPos> ring = rectangle(-3, -3, 3, 3); ring.remove(new ChunkPos(-2, -2));
        for (Set<ChunkPos> shape : List.of(ring, Set.of(new ChunkPos(-3, -2), new ChunkPos(1, 1)),
                Set.of(new ChunkPos(-2, -2), new ChunkPos(-1, -2), new ChunkPos(-2, -1)))) {
            var plan = flat(shape); assertTrue(plan.valid(), plan.problemSummary());
            var stages = PerimeterStageLayout.partition(plan, part -> part.targets().size() <= 600 ? null : "Test capacity");
            assertExactPartition(plan, stages);
        }
    }

    @Test void terrainAndEveryPaletteKeepFoundationsAndHeadroomInTheSameStage() {
        for (var palette : List.of(PerimeterBlueprint.Palette.COBBLESTONE, PerimeterBlueprint.Palette.STONE_BRICKS, PerimeterBlueprint.Palette.OAK)) {
            var plan = PerimeterBlueprint.create(rectangle(-2, 0, 3, 2), (x, z) -> PerimeterBlueprint.Surface.ready(64 + Math.floorMod(x + z, 5)), palette);
            assertTrue(plan.valid(), plan.problemSummary());
            assertTrue(plan.materialCounts().getOrDefault("minecraft:dirt", 0) > 0);
            assertExactPartition(plan, PerimeterStageLayout.partition(plan, part -> part.targets().size() <= 700 ? null : "Test capacity"));
        }
    }

    @Test void inputOrderDoesNotChangePartitionOrDigest() {
        var plan = flat(rectangle(-2, -1, 4, 3));
        List<Long> positions = new ArrayList<>(plan.blocks().keySet()); Collections.reverse(positions);
        Map<Long, String> reversed = new LinkedHashMap<>(); positions.forEach(p -> reversed.put(p, plan.blocks().get(p)));
        var columns = new ArrayList<>(plan.columns()); Collections.reverse(columns);
        var clearance = new ArrayList<>(plan.clearance()); Collections.reverse(clearance);
        var reordered = new PerimeterBlueprint.Plan(reversed, columns, new LinkedHashSet<>(clearance), plan.min(), plan.max(),
                plan.runs(), plan.connections(), plan.materialCounts(), List.of());
        PerimeterStageLayout.NativeStageValidator cap = part -> part.targets().size() <= 800 ? null : "Test capacity";
        assertEquals(PerimeterStageLayout.partition(plan, cap), PerimeterStageLayout.partition(reordered, cap));
    }

    @Test void impossibleWholeColumnRejectsEntireCommissionAndNeverReturnsPartialLayout() {
        var plan = flat(Set.of(new ChunkPos(0, 0))); AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> PerimeterStageLayout.partition(plan, part -> {
            calls.incrementAndGet(); return "No entire column fits";
        }));
        assertTrue(calls.get() < 32);
        assertThrows(NullPointerException.class, () -> PerimeterStageLayout.partition(plan, null));
    }

    @Test void oversizedStageOrCorruptMembershipFailsClosed() {
        var plan = flat(Set.of(new ChunkPos(0, 0)));
        var valid = PerimeterStageLayout.partition(plan, part -> part.targets().size() <= 500 ? null : "Test capacity");
        var parts = new ArrayList<>(valid.stages()); parts.remove(parts.size() - 1);
        assertThrows(IllegalArgumentException.class, () -> PerimeterStageLayout.validate(plan, new PerimeterStageLayout.Layout(parts, valid.digest())));
        parts.clear(); parts.addAll(valid.stages()); parts.add(parts.get(0));
        assertThrows(IllegalArgumentException.class, () -> PerimeterStageLayout.validate(plan, new PerimeterStageLayout.Layout(parts, valid.digest())));
        var badTargets = new LinkedHashMap<>(plan.blocks()); badTargets.put(badTargets.keySet().iterator().next(), "minecraft:unknown_material");
        var bad = new PerimeterBlueprint.Plan(badTargets, plan.columns(), plan.clearance(), plan.min(), plan.max(),
                plan.runs(), plan.connections(), plan.materialCounts(), List.of());
        assertThrows(IllegalArgumentException.class, () -> PerimeterStageLayout.partition(bad, part -> null));
    }

    static void assertExactPartition(PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout) {
        Map<Long, String> union = new HashMap<>(); Set<Long> clear = new HashSet<>(), reserved = new HashSet<>();
        Map<Long, Integer> columnStages = new HashMap<>();
        for (var stage : layout.stages()) {
            for (var target : stage.targets().entrySet()) {
                assertNull(union.put(target.getKey(), target.getValue())); assertTrue(reserved.add(target.getKey()));
                assertColumn(columnStages, target.getKey(), stage.index());
            }
            for (long cell : stage.clearance()) {
                assertTrue(clear.add(cell)); assertTrue(reserved.add(cell)); assertColumn(columnStages, cell, stage.index());
            }
        }
        assertEquals(plan.blocks(), union); assertEquals(plan.clearance(), clear);
        assertEquals(plan.blocks().size() + plan.clearance().size(), reserved.size());
        assertEquals(plan.columns().size(), columnStages.size()); PerimeterStageLayout.validate(plan, layout);
    }
    private static void assertColumn(Map<Long, Integer> seen, long cell, int stage) {
        Integer previous = seen.putIfAbsent(PerimeterStageLayout.column(cell), stage);
        assertTrue(previous == null || previous == stage, "A whole column must have exactly one stage");
    }
    static PerimeterBlueprint.Plan flat(Set<ChunkPos> shape) {
        return PerimeterBlueprint.create(shape, (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.COBBLESTONE);
    }
    static Set<ChunkPos> rectangle(int x, int z, int width, int depth) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) chunks.add(new ChunkPos(x + dx, z + dz));
        return chunks;
    }
}
