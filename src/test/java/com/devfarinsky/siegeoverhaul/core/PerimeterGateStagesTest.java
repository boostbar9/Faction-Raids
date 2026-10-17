package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterGateStagesTest extends MinecraftTestSupport {
    @Test void disconnectedComponentsSplitWithoutChangingAnyExactNativeCell() {
        var plan = wall(Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)));
        var oldLayout = PerimeterStageLayout.partition(plan, stage -> null);
        assertEquals(1, oldLayout.stages().size(), "Legacy mixed-component layouts remain unchanged");
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateStages.components(plan, oldLayout));
        AtomicInteger nativeCalls = new AtomicInteger();
        var gated = PerimeterGateStages.partition(plan, stage -> { nativeCalls.incrementAndGet(); return null; });
        assertEquals(2, gated.stages().size()); assertTrue(nativeCalls.get() >= 2);
        assertEquals(java.util.List.of(0, 1), PerimeterGateStages.components(plan, gated));
        Set<Long> cells = new HashSet<>(); Map<Long, String> targets = new HashMap<>();
        gated.stages().forEach(stage -> { cells.addAll(stage.reservation()); targets.putAll(stage.targets()); });
        Set<Long> expected = new HashSet<>(plan.blocks().keySet()); expected.addAll(plan.clearance());
        assertEquals(expected, cells); assertEquals(plan.blocks(), targets);
        PerimeterStageLayout.validate(plan, gated);
    }

    @Test void nativeSerializerFailuresRemainBindingAndCannotBeBypassedByComponentSplits() {
        var plan = wall(Set.of(new ChunkPos(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateStages.partition(plan, stage -> "Native serializer rejects atom"));
        var layout = PerimeterGateStages.partition(plan, stage -> stage.columns().size() <= 40 ? null : "Fixture envelope");
        assertTrue(layout.stages().size() > 1);
        assertTrue(PerimeterGateStages.components(plan, layout).stream().allMatch(id -> id == 0));
    }

    @Test void interleavedComponentsKeepTheirExactSavedMembershipThroughRestore() {
        var plan = wall(Set.of(new ChunkPos(0, 0), new ChunkPos(0, 2), new ChunkPos(1, 0)));
        var layout = PerimeterGateStages.partition(plan, stage -> null);
        var memberships = layout.stages().stream().map(PerimeterStageLayout.Stage::columns).toList();
        var restored = PerimeterStageLayout.restoreLayout(memberships, plan);
        assertEquals(layout, restored); assertEquals(PerimeterGateStages.components(plan, layout), PerimeterGateStages.components(plan, restored));
        for (var stage : layout.stages()) {
            Set<Integer> expected = new HashSet<>();
            for (long cell : stage.columns()) for (var column : plan.columns())
                if (column.base().getX() == BlockPos.of(cell).getX() && column.base().getZ() == BlockPos.of(cell).getZ())
                    expected.add(column.componentId());
            assertEquals(1, expected.size());
        }
    }

    private static PerimeterBlueprint.Plan wall(Set<ChunkPos> claim) {
        return PerimeterBlueprint.create(claim, (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.COBBLESTONE);
    }
}
