package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterEarthworksAssemblyTest extends MinecraftTestSupport {
    private static final UUID PROJECT = UUID.fromString("11111111-1111-1111-1111-111111111111"), OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            BUILDER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test void exactGradingToFlexibleWallHandoffUsesObservedPostGradeStatesAndOneQuote() {
        var region = cut(0, 64); long cell = pos(0, 64);
        var result = assemble(List.of(region), List.of(new PerimeterEarthworksAssembly.WallCell(cell, Blocks.DIRT.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 0)), List.of(), 1);
        assertEquals(1, result.cuts()); assertEquals(1, result.placements()); assertEquals(64, result.scope().price());
        assertEquals(Blocks.AIR.defaultBlockState(), result.expectedWallBefore().get(cell));
        assertEquals(Blocks.DIRT.defaultBlockState(), result.originals().get(cell).state());
        assertEquals(Map.of("minecraft:cobblestone", 1), result.materialCounts());
        assertTrue(result.gradingWrites().contains(cell)); assertTrue(result.wallWrites().contains(cell));
        assertThrows(UnsupportedOperationException.class, () -> result.wallTargets().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.originals().clear());
    }

    @Test void regionalFillAndMatchingWallFoundationAreDeduplicatedWithoutForecastDropCredit() {
        long cell = pos(0, 63); var air = Blocks.AIR.defaultBlockState(); var dirt = Blocks.DIRT.defaultBlockState();
        var region = new PerimeterEarthworksManifest(header(0, 64), List.of(new Observation(cell, air, Role.WORK, 0)),
                List.of(new Step(0, Kind.FILL, cell, air, dirt, null)));
        var result = assemble(List.of(region), List.of(new PerimeterEarthworksAssembly.WallCell(cell, air, dirt, 0)), List.of(), 1);
        assertEquals(1, result.placements()); assertEquals(1, result.fills()); assertTrue(result.wallWrites().isEmpty());
        assertEquals(Map.of("minecraft:dirt", 1), result.materialCounts()); assertEquals(dirt, result.expectedWallBefore().get(cell));
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(region),
                List.of(new PerimeterEarthworksAssembly.WallCell(cell, air, Blocks.COBBLESTONE.defaultBlockState(), 0)), List.of(), 1));
    }

    @Test void independentRegionLevelsAndOrderingDoNotResetFeesOrChangeAggregateDigest() {
        var low = cut(0, 64); var high = cut(20, 80);
        var first = assemble(List.of(low, high), List.of(), List.of(), 0);
        var second = assemble(List.of(high, low), List.of(), List.of(), 0);
        assertEquals(first.digest(), second.digest()); assertEquals(2, first.cuts()); assertEquals(64, first.scope().price());
        assertTrue(first.materialCounts().isEmpty(), "Mining drops do not become reserved construction stock");
        assertEquals(2, first.nativeStages());
    }

    @Test void duplicateMutationOwnersAndCrossRegionWriteObservationOverlapsAreRejected() {
        var first = cut(0, 64); assertThrows(IllegalArgumentException.class, () -> assemble(List.of(first, first), List.of(), List.of(), 0));
        var second = cut(10, 64); var observations = new ArrayList<>(first.observations().values());
        observations.add(new Observation(pos(10, 64), Blocks.DIRT.defaultBlockState(), Role.DEPENDENCY, 0));
        var overlapped = new PerimeterEarthworksManifest(first.header(), observations, first.steps());
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(overlapped, second), List.of(), List.of(), 0));
    }

    @Test void sharedReadOnlyObservationsDeduplicateButStatesAndSameStateEditsMustAgree() {
        var first = cut(0, 64); var second = cut(10, 64); long shared = pos(5, 64);
        var a = new ArrayList<>(first.observations().values()); var b = new ArrayList<>(second.observations().values());
        a.add(new Observation(shared, Blocks.AIR.defaultBlockState(), Role.DEPENDENCY, 4));
        b.add(new Observation(shared, Blocks.AIR.defaultBlockState(), Role.DEPENDENCY, 4));
        var left = new PerimeterEarthworksManifest(first.header(), a, first.steps());
        var right = new PerimeterEarthworksManifest(second.header(), b, second.steps());
        assertEquals(3, assemble(List.of(left, right), List.of(), List.of(), 0).originals().size());
        b.set(b.size() - 1, new Observation(shared, Blocks.AIR.defaultBlockState(), Role.DEPENDENCY, 5));
        var edited = new PerimeterEarthworksManifest(second.header(), b, second.steps());
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(left, edited), List.of(), List.of(), 0));
    }

    @Test void outsideApproachesNeverBecomeMutableByAddingARegionalOrWallTarget() {
        var region = cut(0, 64); long cell = pos(0, 64);
        for (Role role : List.of(Role.GATE_CLEARANCE, Role.GATE_FLOOR, Role.PROTECTED_OCCUPANCY)) {
            var readOnly = List.of(new Observation(cell, Blocks.DIRT.defaultBlockState(), role, 0));
            assertThrows(IllegalArgumentException.class, () -> assemble(List.of(region), List.of(), readOnly, 0));
            assertThrows(IllegalArgumentException.class, () -> assemble(List.of(),
                    List.of(new PerimeterEarthworksAssembly.WallCell(cell, Blocks.DIRT.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 0)), readOnly, 1));
            var unchanged = assemble(List.of(), List.of(new PerimeterEarthworksAssembly.WallCell(cell, Blocks.DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState(), 0)), readOnly, 1);
            assertTrue(unchanged.wallWrites().isEmpty()); assertEquals(0, unchanged.placements());
        }
    }

    @Test void regionsCannotResetTheFourThousandNinetySixCutBudget() {
        var a = cuts(0, 2_049); var b = cuts(5_000, 2_049);
        var error = assertThrows(IllegalArgumentException.class, () -> assemble(List.of(a, b), List.of(), List.of(), 0));
        assertTrue(error.getMessage().contains("cut or placement budgets"));
    }

    @Test void wallTargetsAndRegionalPlacementsShareOneThirtyTwoThousandBudget() {
        var a = builds(0, 8_192); var b = builds(10_000, 8_192);
        assertEquals(32_768, assemble(List.of(a, b), List.of(), List.of(), 0).placements());
        var wall = List.of(new PerimeterEarthworksAssembly.WallCell(pos(20_000, 64), Blocks.AIR.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(a, b), wall, List.of(), 1));
    }

    @Test void projectOwnerClaimsWorldAndVersionedMaterialContractsCannotBeSubstituted() {
        var first = cut(0, 64); var h = first.header();
        var foreign = new Header(h.project(), h.generation(), UUID.randomUUID(), h.builder(), h.dimension(), h.faction(), h.claimsDigest(),
                h.layoutDigest(), h.policy(), h.component(), h.planeY(), h.minY(), h.maxY(), h.feeVersion(), h.price());
        var region = new PerimeterEarthworksManifest(foreign, new ArrayList<>(first.observations().values()), first.steps());
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(region), List.of(), List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksAssembly.WallCell(pos(0, 64), Blocks.AIR.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(first), List.of(), List.of(), 256));
        assertThrows(IllegalArgumentException.class, () -> assemble(Collections.nCopies(257, first), List.of(), List.of(), 0));
    }

    @Test void conflictingWallOriginalsDuplicateTargetsAndOutOfWorldObservationsFailClosed() {
        var first = cut(0, 64); long cell = pos(0, 64);
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(first), List.of(new PerimeterEarthworksAssembly.WallCell(cell,
                Blocks.DIRT.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 1)), List.of(), 1));
        var wall = new PerimeterEarthworksAssembly.WallCell(pos(20, 64), Blocks.AIR.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 0);
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(first), List.of(wall, wall), List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(first), List.of(),
                List.of(new Observation(pos(30_000_000, 64), Blocks.AIR.defaultBlockState(), Role.GATE_CLEARANCE, 0)), 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(List.of(first), List.of(),
                List.of(new Observation(pos(20, 64), Blocks.AIR.defaultBlockState(), Role.WORK, 0)), 0));
    }

    private static PerimeterEarthworksAssembly.Assembly assemble(List<PerimeterEarthworksManifest> regions,
            List<PerimeterEarthworksAssembly.WallCell> wall, List<Observation> readOnly, int wallStages) {
        return PerimeterEarthworksAssembly.assemble(PerimeterEarthworksAssembly.Scope.from(header(0, 64)), regions, wall, readOnly, wallStages);
    }
    private static PerimeterEarthworksManifest cut(int x, int plane) {
        long pos = pos(x, plane); BlockState dirt = Blocks.DIRT.defaultBlockState();
        return new PerimeterEarthworksManifest(header(x, plane), List.of(new Observation(pos, dirt, Role.WORK, 0)),
                List.of(new Step(0, Kind.CUT, pos, dirt, Blocks.AIR.defaultBlockState(), removal())));
    }
    private static PerimeterEarthworksManifest cuts(int start, int count) {
        var observations = new ArrayList<Observation>(); var steps = new ArrayList<Step>();
        for (int i = 0; i < count; i++) {
            long pos = pos(start + i, 64); observations.add(new Observation(pos, Blocks.DIRT.defaultBlockState(), Role.WORK, 0));
            steps.add(new Step(0, Kind.CUT, pos, Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), removal()));
        }
        return new PerimeterEarthworksManifest(header(start, 64), observations, steps);
    }
    private static PerimeterEarthworksManifest builds(int start, int columns) {
        var observations = new ArrayList<Observation>(); var steps = new ArrayList<Step>();
        for (int i = 0; i < columns; i++) for (int y = 64; y < 66; y++) {
            long pos = pos(start + i, y); observations.add(new Observation(pos, Blocks.AIR.defaultBlockState(), Role.WORK, 0));
            steps.add(new Step(0, Kind.BUILD, pos, Blocks.AIR.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), null));
        }
        return new PerimeterEarthworksManifest(header(start, 64), observations, steps);
    }
    private static Header header(int component, int plane) {
        return new Header(PROJECT, 1, OWNER, BUILDER, "minecraft:overworld", "test", "a".repeat(64), "b".repeat(64),
                "local-pad-v1", component, plane, -64, 320, 1, 64);
    }
    private static Removal removal() { return new Removal(Origin.UNKNOWN, Family.SOIL, "fixture:dirt", "1", "c".repeat(64)); }
    private static long pos(int x, int y) { return new BlockPos(x, y, 0).asLong(); }
}
