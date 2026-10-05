package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterSteppedBlueprintTest {
    @Test void flatSteppedGatesPartitionThroughTheExistingNativeWallLayout() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(64));
        var plan = PerimeterSteppedBlueprint.convert(Set.of(new ChunkPos(0, 0)), draft);
        assertTrue(plan.valid());
        assertEquals(draft.targets().size(), plan.blocks().size());
        assertTrue(draft.gates().stream().flatMap(gate -> gate.passage().stream())
                .filter(pos -> pos.column().chunk().equals(new PerimeterSteppedTopology.Chunk(0, 0)))
                .anyMatch(pos -> plan.clearance().contains(packed(pos)) && !plan.blocks().containsKey(packed(pos))));
        var layout = PerimeterStageLayout.partition(plan, ignored -> null);
        assertDoesNotThrow(() -> PerimeterStageLayout.validate(plan, layout));
    }

    @Test void shortDipBecomesContiguousDirtSupportInTheSameNativeWallPlan() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(cell.equals(new PerimeterSteppedTopology.Cell(0, 0)) ? 63 : 64));
        var plan = PerimeterSteppedBlueprint.convert(Set.of(new ChunkPos(0, 0)), draft);
        assertEquals("minecraft:dirt", plan.blocks().get(new BlockPos(0, 63, 0).asLong()));
        var column = plan.columns().stream().filter(c -> c.base().equals(new BlockPos(0, 64, 0))).findFirst().orElseThrow();
        assertEquals(1, column.supportDepth());
        assertTrue(PerimeterConstruction.nativeScanWithinBudget(plan));
    }

    private static PerimeterSteppedGeometry.Draft draft(Function<PerimeterSteppedTopology.Cell, PerimeterSteppedGeometry.Ground> ground) {
        var result = PerimeterSteppedGeometry.compile(Set.of(new PerimeterSteppedTopology.Chunk(0, 0)), new PerimeterSteppedGeometry.Terrain() {
            @Override public PerimeterSteppedGeometry.Ground ground(PerimeterSteppedTopology.Cell column) { return ground.apply(column); }
            @Override public String passageProblem(PerimeterSteppedTopology.Cell column, int feetY, PerimeterSteppedGeometry.Region region) { return null; }
        }, PerimeterSteppedGeometry.Block.COBBLESTONE, PerimeterSteppedGeometry.Limits.DEFAULT);
        assertTrue(result.feasible(), result.problem());
        return result;
    }

    private static long packed(PerimeterSteppedGeometry.Pos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()).asLong(); }
}
