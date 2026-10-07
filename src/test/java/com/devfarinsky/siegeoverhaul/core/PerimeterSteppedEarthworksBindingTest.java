package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterSteppedEarthworksBindingTest extends MinecraftTestSupport {
    @Test void oneCellDipBindsToARealFillManifestBeforeWallAssembly() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(cell.equals(new PerimeterSteppedTopology.Cell(0, 0)) ? 63 : 64));
        assertEquals(1, draft.fillCells());
        var bound = PerimeterSteppedEarthworksBinding.bind(scope(), draft, snapshot(draft), 1);
        assertEquals(1, bound.fillRegions().size());
        var fill = bound.fillRegions().get(0);
        assertEquals(1, fill.steps().size());
        assertEquals(PerimeterEarthworksManifest.Kind.FILL, fill.steps().get(0).kind());
        assertEquals(Blocks.DIRT.defaultBlockState(), fill.steps().get(0).after());
        assertTrue(fill.observations().containsKey(fill.steps().get(0).pos()));
        assertTrue(fill.observations().size() >= 25, "native adapter neighbor dependencies are part of the manifest");
        assertEquals(1, bound.assembly().fills());
        assertEquals(1, bound.assembly().targetBlockCounts().get("minecraft:dirt"));
        assertEquals(draft.targets().values().stream().filter(t -> t.phase() == PerimeterSteppedGeometry.Phase.STRUCTURE).count(),
                bound.assembly().wallWrites().size());
    }

    @Test void adjacentShortFillsShareOneRegionSoTheirDependencySnapshotsDoNotConflict() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(cell.x() >= 0 && cell.x() <= 1 && cell.z() == 0 ? 63 : 64));
        assertEquals(2, draft.fillCells());
        var bound = PerimeterSteppedEarthworksBinding.bind(scope(), draft, snapshot(draft), 1);
        assertEquals(1, bound.fillRegions().size());
        assertEquals(2, bound.fillRegions().get(0).steps().size());
        assertEquals(2, bound.assembly().fills());
    }

    @Test void flatNoFillDraftBindsDirectlyToOneStructureWallPhase() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(64));
        assertEquals(0, draft.fillCells());
        var bound = PerimeterSteppedEarthworksBinding.bind(scope(), draft, snapshot(draft), 1);
        assertTrue(bound.fillRegions().isEmpty());
        assertEquals(0, bound.assembly().fills());
        assertEquals(1, bound.assembly().nativeStages());
        assertEquals(bound.wallCells().size(), bound.assembly().wallWrites().size());
    }

    @Test void alreadyBuiltWallCellsArePreservedAndNotChargedAsNewSupply() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(64));
        var kept = draft.targets().entrySet().stream()
                .filter(entry -> entry.getValue().phase() == PerimeterSteppedGeometry.Phase.STRUCTURE)
                .findFirst().orElseThrow();
        var bound = PerimeterSteppedEarthworksBinding.bind(scope(), draft, pos -> {
            if (pos.equals(kept.getKey())) return new PerimeterSteppedEarthworksBinding.Original(state(kept.getValue().block()), 7);
            return snapshot(draft).original(pos);
        }, 1);
        assertFalse(bound.assembly().wallWrites().contains(packed(kept.getKey())));
        String key = PerimeterProject.stateKey(state(kept.getValue().block()));
        assertEquals(draft.targetCounts().get(kept.getValue().block()) - 1, bound.assembly().targetBlockCounts().get(key));
    }

    @Test void changedFillOriginalFailsBeforeCreatingAnAssembly() {
        var draft = draft(cell -> PerimeterSteppedGeometry.Ground.safe(cell.equals(new PerimeterSteppedTopology.Cell(0, 0)) ? 63 : 64));
        assertThrows(IllegalArgumentException.class, () -> PerimeterSteppedEarthworksBinding.bind(scope(), draft, pos -> {
            if (draft.targets().get(pos) != null && draft.targets().get(pos).phase() == PerimeterSteppedGeometry.Phase.FILL)
                return new PerimeterSteppedEarthworksBinding.Original(Blocks.DIRT.defaultBlockState(), 0);
            return snapshot(draft).original(pos);
        }, 1));
    }

    private static PerimeterSteppedGeometry.Draft draft(Function<PerimeterSteppedTopology.Cell, PerimeterSteppedGeometry.Ground> ground) {
        var result = PerimeterSteppedGeometry.compile(Set.of(new PerimeterSteppedTopology.Chunk(0, 0)), new PerimeterSteppedGeometry.Terrain() {
            @Override public PerimeterSteppedGeometry.Ground ground(PerimeterSteppedTopology.Cell column) { return ground.apply(column); }
            @Override public String passageProblem(PerimeterSteppedTopology.Cell column, int feetY, PerimeterSteppedGeometry.Region region) { return null; }
        }, PerimeterSteppedGeometry.Block.COBBLESTONE, PerimeterSteppedGeometry.Limits.DEFAULT);
        assertTrue(result.feasible(), result.problem());
        return result;
    }

    private static PerimeterSteppedEarthworksBinding.Snapshot snapshot(PerimeterSteppedGeometry.Draft draft) {
        return pos -> {
            var target = draft.targets().get(pos);
            if (target != null) return new PerimeterSteppedEarthworksBinding.Original(
                    target.phase() == PerimeterSteppedGeometry.Phase.FILL ? Blocks.AIR.defaultBlockState() : Blocks.AIR.defaultBlockState(), 0);
            if (draft.clearance().contains(pos)) return new PerimeterSteppedEarthworksBinding.Original(Blocks.AIR.defaultBlockState(), 0);
            return new PerimeterSteppedEarthworksBinding.Original(pos.y() < 64 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 0);
        };
    }

    private static PerimeterEarthworksAssembly.Scope scope() {
        return new PerimeterEarthworksAssembly.Scope(UUID.fromString("11111111-1111-1111-1111-111111111111"), 1,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "minecraft:overworld", "test", "a".repeat(64), -64, 320, 1, 64);
    }

    private static long packed(PerimeterSteppedGeometry.Pos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()).asLong(); }
    private static BlockState state(PerimeterSteppedGeometry.Block block) {
        return switch (block) {
            case COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState();
            case STONE_BRICKS -> Blocks.STONE_BRICKS.defaultBlockState();
            case OAK_PLANKS -> Blocks.OAK_PLANKS.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
        };
    }
}
