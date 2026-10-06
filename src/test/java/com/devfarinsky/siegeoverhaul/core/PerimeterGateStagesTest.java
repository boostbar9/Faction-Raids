package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    @Test void syntheticPartitionMatchesRecordedCountsAndInferredObstacleCount() {
        var chunks = stagedTerritory();
        var claim = topologyClaim(chunks);
        var lowered = stagedLoweredBand(claim);
        var loweredColumns = new HashSet<Long>();
        lowered.forEach(pos -> loweredColumns.add(xz(pos).asLong()));
        stagedFillDips(claim, loweredColumns).forEach(pos -> loweredColumns.add(xz(pos).asLong()));
        var draft = PerimeterSteppedGeometry.compile(claim, new PerimeterSteppedGeometry.Terrain() {
            @Override public PerimeterSteppedGeometry.Ground ground(PerimeterSteppedTopology.Cell column) {
                return PerimeterSteppedGeometry.Ground.safe(loweredColumns.contains(new BlockPos(column.x(), 0, column.z()).asLong()) ? 64 : 65);
            }
            @Override public String passageProblem(PerimeterSteppedTopology.Cell column, int feetY,
                                                   PerimeterSteppedGeometry.Region region) {
                return null;
            }
        }, PerimeterSteppedGeometry.Block.COBBLESTONE, new PerimeterSteppedGeometry.Limits(-64, 320, 8,
                Math.min(PerimeterPreview.MAX_CELLS, PerimeterStageLayout.MAX_TARGETS),
                PerimeterStageLayout.MAX_RESERVED, 32_768, 16_384));
        assertTrue(draft.feasible(), draft.problem());
        assertEquals(4, draft.gates().size());
        var plan = PerimeterSteppedBlueprint.convert(chunks, draft);
        assertEquals(3831, plan.blocks().size());
        assertEquals(3, plan.materialCounts().getOrDefault("minecraft:dirt", 0));
        // This capacity fixture is not native serializer or navigation evidence.
        var layout = PerimeterGateStages.partition(plan, stage ->
                stage.targets().size() <= 3480 ? null : "CI native staged fixture chunk split");
        assertFalse(layout.stages().isEmpty());
        assertEquals(2, layout.stages().size());
        assertEquals(3480, layout.stages().get(0).targets().size());
        assertEquals(351, layout.stages().get(1).targets().size());
        var remaining = new HashSet<Long>();
        for (int z = 0; z <= 15; z++) remaining.add(new BlockPos(128, 66, z).asLong());
        remaining.add(new BlockPos(129, 66, 0).asLong());
        long reconstructed = layout.stages().get(0).targets().keySet().stream().map(BlockPos::of)
                .filter(pos -> pos.getY() < 66 || pos.getY() == 66 && !remaining.contains(pos.asLong()))
                .count();
        assertEquals(1044, reconstructed, "stages=" + layout.stages().size() + ", stage0Targets="
                + layout.stages().get(0).targets().size() + ", gates=" + draft.gates().stream()
                .map(gate -> gate.facing() + ":" + gate.outerFeet()).toList());
    }

    private static PerimeterBlueprint.Plan wall(Set<ChunkPos> claim) {
        return PerimeterBlueprint.create(claim, (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.COBBLESTONE);
    }

    private static Set<ChunkPos> stagedTerritory() {
        var chunks = new HashSet<ChunkPos>();
        for (int x = 8; x <= 12; x++) for (int z = 0; z <= 4; z++) chunks.add(new ChunkPos(x, z));
        return Set.copyOf(chunks);
    }

    private static Set<PerimeterSteppedTopology.Chunk> topologyClaim(Set<ChunkPos> chunks) {
        var claim = new java.util.TreeSet<PerimeterSteppedTopology.Chunk>();
        chunks.forEach(chunk -> claim.add(new PerimeterSteppedTopology.Chunk(chunk.x, chunk.z)));
        return Set.copyOf(claim);
    }

    private static List<BlockPos> stagedLoweredBand(Set<PerimeterSteppedTopology.Chunk> claim) {
        var topology = PerimeterSteppedTopology.create(claim);
        assertTrue(topology.valid(), topology.problem());
        for (var loop : topology.loops()) if (loop.outer()) {
            var bands = loop.bands();
            for (int i = 0; i < bands.size(); i++) {
                var band = bands.get(i);
                var previous = bands.get((i + bands.size() - 1) % bands.size());
                var next = bands.get((i + 1) % bands.size());
                if (band.kind() == PerimeterSteppedProfile.Kind.STRAIGHT
                        && previous.kind() == PerimeterSteppedProfile.Kind.STRAIGHT
                        && next.kind() == PerimeterSteppedProfile.Kind.STRAIGHT)
                    return band.cells().stream().sorted()
                            .map(cell -> new BlockPos(cell.x(), 64, cell.z())).toList();
            }
        }
        throw new AssertionError("Fixture claim lacks a complete straight band");
    }

    private static List<BlockPos> stagedFillDips(Set<PerimeterSteppedTopology.Chunk> claim, Set<Long> lowered) {
        var topology = PerimeterSteppedTopology.create(claim);
        for (var loop : topology.loops()) if (loop.outer()) for (var band : loop.bands())
            if (band.kind() == PerimeterSteppedProfile.Kind.STRAIGHT) {
                var cells = band.cells().stream().sorted()
                        .filter(cell -> !lowered.contains(new BlockPos(cell.x(), 0, cell.z()).asLong()))
                        .limit(3).map(cell -> new BlockPos(cell.x(), 64, cell.z())).toList();
                if (cells.size() == 3) return cells;
            }
        throw new AssertionError("Fixture claim lacks separate fill-dip cells");
    }

    private static BlockPos xz(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }
}
