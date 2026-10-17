package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterGateLayoutTest extends MinecraftTestSupport {
    private static final Set<ChunkPos> ONE = Set.of(new ChunkPos(0, 0));
    private static final PerimeterGateLayout.PassageValidator CLEAR = (feet, region) -> null;

    @Test void oneChunkHasFourThreeWideThreeHighEntrancesAndExactApproaches() {
        var wall = flat(ONE);
        var layout = PerimeterGateLayout.create(ONE, wall, CLEAR);
        assertTrue(layout.valid(), layout.problemSummary());
        assertEquals(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST),
                layout.gates().stream().map(PerimeterGateLayout.Gate::facing).toList());
        assertEquals(72, layout.wallOpenings().size());
        assertEquals(180, layout.passageClearance().size());
        assertEquals(List.of(new BlockPos(7, 64, 0), new BlockPos(15, 64, 7),
                        new BlockPos(7, 64, 15), new BlockPos(0, 64, 7)),
                layout.gates().stream().map(PerimeterGateLayout.Gate::outerCenter).toList());
        for (var gate : layout.gates()) {
            assertEquals(0, gate.componentId());
            assertEquals(45, gate.passage().size());
            assertEquals(27, gate.insideApproach().size());
            assertEquals(27, gate.outsideApproach().size());
            assertEquals(18, gate.approachFooting().size());
            assertTrue(Collections.disjoint(gate.passage(), gate.insideApproach()));
            assertTrue(Collections.disjoint(gate.passage(), gate.outsideApproach()));
            for (long cell : gate.insideApproach()) assertTrue(ONE.contains(new ChunkPos(BlockPos.of(cell))));
            for (long cell : gate.outsideApproach()) assertFalse(ONE.contains(new ChunkPos(BlockPos.of(cell))));
            for (long cell : gate.approachFooting()) assertEquals(63, BlockPos.of(cell).getY());
        }
        assertTrue(wall.blocks().keySet().containsAll(layout.wallOpenings()));
        assertTrue(layout.passageClearance().containsAll(layout.wallOpenings()));
    }

    @Test void openingsOnlyOmitNewSkinTargetsAndRetainDeckRailsSupportsAndCorners() {
        var wall = PerimeterBlueprint.create(ONE, (x, z) -> PerimeterBlueprint.Surface.ready(x < 8 ? 62 : 64),
                PerimeterBlueprint.Palette.COBBLESTONE);
        var original = new HashMap<>(wall.blocks());
        var layout = PerimeterGateLayout.create(ONE, wall, CLEAR);
        assertTrue(layout.valid(), layout.problemSummary());
        Map<Long, String> projected = new HashMap<>(wall.blocks());
        layout.wallOpenings().forEach(projected::remove);
        assertEquals(original, wall.blocks(), "Compiler must not change its source plan");
        assertEquals(wall.blocks().size() - 72, projected.size());
        for (var entry : wall.blocks().entrySet()) {
            BlockPos p = BlockPos.of(entry.getKey());
            boolean corner = (p.getX() < 5 || p.getX() > 10) && (p.getZ() < 5 || p.getZ() > 10);
            if (p.getY() < 64 || p.getY() >= 67 || corner) assertEquals(entry.getValue(), projected.get(entry.getKey()));
        }
        for (long cell : layout.passageClearance()) assertFalse(projected.containsKey(cell));
        assertEquals(wall.columns().size(), projected.keySet().stream().map(BlockPos::of)
                .map(p -> new BlockPos(p.getX(), 0, p.getZ())).distinct().count());
    }

    @Test void blockedPreferredRouteTriesOtherPositionsAndChecksAllThreeLanes() {
        var checks = new ArrayList<BlockPos>();
        var layout = PerimeterGateLayout.create(ONE, flat(ONE), (feet, region) -> {
            checks.add(feet);
            return region == PerimeterGateLayout.Region.OUTSIDE_APPROACH && feet.getZ() < 0 && feet.getX() == 8
                    ? "A chest blocks the outer lane" : null;
        });
        assertTrue(layout.valid(), layout.problemSummary());
        assertEquals(new BlockPos(6, 64, 0), layout.gates().get(0).outerCenter());
        assertTrue(checks.stream().anyMatch(p -> p.getZ() < 0 && p.getX() == 8));
        assertFalse(layout.gates().get(0).outsideApproach().stream().map(BlockPos::of).anyMatch(p -> p.getX() == 8));
    }

    @Test void unsafeWaterCliffsObstaclesAndUnloadedTerrainRejectWithoutPartialOutput() {
        for (String reason : List.of("Water", "Unsafe floor drop", "Solid obstacle", "Unloaded chunk")) {
            var result = PerimeterGateLayout.create(ONE, flat(ONE), (feet, region) ->
                    region == PerimeterGateLayout.Region.OUTSIDE_APPROACH && feet.getZ() > 15 ? reason : null);
            assertBlocked(result, PerimeterGateLayout.ProblemCode.NO_SAFE_APPROACH);
            assertEquals(Direction.SOUTH, result.problems().get(0).direction());
            assertTrue(result.problemSummary().contains(reason));
        }
        assertBlocked(PerimeterGateLayout.create(ONE, flat(ONE), (feet, region) -> { throw new IllegalStateException(); }),
                PerimeterGateLayout.ProblemCode.NO_SAFE_APPROACH);
        assertBlocked(PerimeterGateLayout.create(ONE, flat(ONE), (feet, region) -> ""),
                PerimeterGateLayout.ProblemCode.NO_SAFE_APPROACH);
    }

    @Test void elevatedWallDoesNotImplyUsableApproachFloor() {
        var wall = PerimeterBlueprint.create(ONE, (x, z) -> PerimeterBlueprint.Surface.ready(x == 0 && z == 0 ? 68 : 64),
                PerimeterBlueprint.Palette.COBBLESTONE);
        assertTrue(wall.valid());
        var layout = PerimeterGateLayout.create(ONE, wall, (feet, region) -> {
            assertEquals(68, feet.getY());
            return region == PerimeterGateLayout.Region.WALL ? null : "The approach floor is four blocks below the passage";
        });
        assertBlocked(layout, PerimeterGateLayout.ProblemCode.NO_SAFE_APPROACH);
    }

    @Test void concaveClaimHasOnlyExteriorGatesAndNoSharedInternalEdges() {
        var claim = Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1));
        var layout = PerimeterGateLayout.create(claim, flat(claim), CLEAR);
        assertTrue(layout.valid(), layout.problemSummary());
        assertEquals(4, layout.gates().size());
        for (var gate : layout.gates()) {
            assertTrue(claim.contains(new ChunkPos(gate.outerCenter())));
            assertFalse(claim.contains(new ChunkPos(gate.outerCenter().relative(gate.facing()))));
            for (long cell : gate.passage()) assertTrue(claim.contains(new ChunkPos(BlockPos.of(cell))));
            for (long cell : gate.outsideApproach()) assertFalse(claim.contains(new ChunkPos(BlockPos.of(cell))));
        }
    }

    @Test void enclosedHoleNeverBecomesAnEntranceOrFallbackRoute() {
        Set<ChunkPos> ring = rectangle(-1, -1, 3, 3); ring.remove(new ChunkPos(0, 0));
        var layout = PerimeterGateLayout.create(ring, flat(ring), CLEAR);
        assertTrue(layout.valid(), layout.problemSummary());
        assertEquals(4, layout.gates().size());
        for (var gate : layout.gates()) for (long cell : gate.outsideApproach())
            assertNotEquals(new ChunkPos(0, 0), new ChunkPos(BlockPos.of(cell)));
        assertBlocked(PerimeterGateLayout.create(ring, flat(ring), (feet, region) ->
                        region == PerimeterGateLayout.Region.OUTSIDE_APPROACH && feet.getZ() < -16 ? "Unsafe outer north" : null),
                PerimeterGateLayout.ProblemCode.NO_SAFE_APPROACH);
    }

    @Test void disconnectedAndDiagonalComponentsGetFourIndependentEntrancesEach() {
        for (var claim : List.of(Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)),
                Set.of(new ChunkPos(-1, -1), new ChunkPos(0, 0)))) {
            var wall = PerimeterBlueprint.create(claim, (x, z) -> PerimeterBlueprint.Surface.ready(x < 16 ? 64 : 72),
                    PerimeterBlueprint.Palette.COBBLESTONE);
            var layout = PerimeterGateLayout.create(claim, wall, CLEAR);
            assertTrue(layout.valid(), layout.problemSummary());
            assertEquals(8, layout.gates().size());
            assertEquals(Set.of(0, 1), new HashSet<>(layout.gates().stream().map(PerimeterGateLayout.Gate::componentId).toList()));
            for (int component = 0; component < 2; component++) {
                int id = component;
                assertEquals(Set.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST),
                        new HashSet<>(layout.gates().stream().filter(g -> g.componentId() == id).map(PerimeterGateLayout.Gate::facing).toList()));
            }
        }
    }

    @Test void islandEnclosedInsideAnotherComponentsHoleFailsRatherThanLyingAboutOuterAccess() {
        Set<ChunkPos> claim = rectangle(0, 0, 5, 5);
        claim.removeAll(rectangle(1, 1, 3, 3)); claim.add(new ChunkPos(2, 2));
        var layout = PerimeterGateLayout.create(claim, flat(claim), CLEAR);
        assertBlocked(layout, PerimeterGateLayout.ProblemCode.NO_OUTER_ENTRANCE);
        assertEquals(1, layout.problems().get(0).componentId());
    }

    @Test void shuffledClaimsAndNegativeTranslationsPreserveDeterministicGateSelection() {
        var original = new ArrayList<>(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1)));
        var claim = new LinkedHashSet<>(original);
        var first = PerimeterGateLayout.create(claim, flat(claim), CLEAR);
        Collections.reverse(original); var reordered = new LinkedHashSet<>(original);
        assertEquals(first, PerimeterGateLayout.create(reordered, flat(reordered), CLEAR));
        var shifted = new HashSet<ChunkPos>();
        claim.forEach(c -> shifted.add(new ChunkPos(c.x - 7, c.z - 5)));
        var translated = PerimeterGateLayout.create(shifted, flat(shifted), CLEAR);
        assertTrue(translated.valid());
        for (int i = 0; i < first.gates().size(); i++) {
            assertEquals(first.gates().get(i).outerCenter().offset(-112, 0, -80), translated.gates().get(i).outerCenter());
            assertEquals(first.gates().get(i).facing(), translated.gates().get(i).facing());
        }
        assertThrows(UnsupportedOperationException.class, () -> first.gates().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.wallOpenings().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.gates().get(0).outsideApproach().clear());
    }

    @Test void terrainChecksAreBoundedCachedAndFailClosed() {
        AtomicInteger checks = new AtomicInteger();
        var result = PerimeterGateLayout.create(ONE, flat(ONE), (feet, region) -> { checks.incrementAndGet(); return null; }, 2);
        assertBlocked(result, PerimeterGateLayout.ProblemCode.BUDGET_LIMIT);
        assertEquals(2, checks.get());
        var seen = new HashSet<String>();
        assertTrue(PerimeterGateLayout.create(ONE, flat(ONE), (feet, region) -> {
            assertTrue(seen.add(feet + ":" + region), "Repeated live check of the same exact column and role");
            return region == PerimeterGateLayout.Region.OUTSIDE_APPROACH && feet.getZ() < 0 && feet.getX() == 8 ? "Blocked" : null;
        }).valid());
    }

    @Test void invalidOrMismatchedGeometryRejectsBeforeTerrainReads() {
        AtomicInteger checks = new AtomicInteger();
        PerimeterGateLayout.PassageValidator counted = (feet, region) -> { checks.incrementAndGet(); return null; };
        assertBlocked(PerimeterGateLayout.create(Set.of(), flat(ONE), counted), PerimeterGateLayout.ProblemCode.INVALID_INPUT);
        assertBlocked(PerimeterGateLayout.create(Set.of(new ChunkPos(1, 0)), flat(ONE), counted), PerimeterGateLayout.ProblemCode.INVALID_INPUT);
        assertBlocked(PerimeterGateLayout.create(Set.of(new ChunkPos(Integer.MAX_VALUE, 0)), flat(ONE), counted), PerimeterGateLayout.ProblemCode.INVALID_INPUT);
        assertBlocked(PerimeterGateLayout.create(ONE, flat(Set.of()), counted), PerimeterGateLayout.ProblemCode.INVALID_INPUT);
        assertEquals(0, checks.get());
    }

    @Test void duplicatedOrOverlappingRunsRejectBeforeGeometryExpansionOrTerrainReads() {
        var wall = flat(ONE);
        var first = wall.runs().get(0);
        var runs = new ArrayList<>(wall.runs());
        runs.add(first);
        AtomicInteger calls = new AtomicInteger();
        var validator = (PerimeterGateLayout.PassageValidator) (feet, region) -> { calls.incrementAndGet(); return null; };
        assertBlocked(PerimeterGateLayout.create(ONE, withRuns(wall, runs), validator), PerimeterGateLayout.ProblemCode.INVALID_INPUT);
        runs.set(runs.size() - 1, new PerimeterBlueprint.Run(runs.size() - 1, first.componentId(),
                first.start(), first.end(), first.direction()));
        assertBlocked(PerimeterGateLayout.create(ONE, withRuns(wall, runs), validator), PerimeterGateLayout.ProblemCode.INVALID_INPUT);
        assertEquals(0, calls.get());
    }

    private static PerimeterBlueprint.Plan withRuns(PerimeterBlueprint.Plan wall, List<PerimeterBlueprint.Run> runs) {
        return new PerimeterBlueprint.Plan(wall.blocks(), wall.columns(), wall.clearance(), wall.min(), wall.max(),
                runs, wall.connections(), wall.materialCounts(), wall.problems());
    }

    @Test void allThreeByThreeClaimTopologiesEitherHaveFourOuterEntrancesOrExplainImpossibleAccess() {
        for (int mask = 1; mask < 512; mask++) {
            Set<ChunkPos> claim = new HashSet<>();
            for (int bit = 0; bit < 9; bit++) if ((mask & 1 << bit) != 0) claim.add(new ChunkPos(bit % 3 - 1, bit / 3 - 1));
            var wall = flat(claim); var layout = PerimeterGateLayout.create(claim, wall, CLEAR);
            Map<ChunkPos, Integer> components = new HashMap<>();
            wall.columns().forEach(c -> components.put(new ChunkPos(c.base()), c.componentId()));
            boolean possible = true;
            for (int component : new HashSet<>(components.values())) for (Direction direction : Direction.Plane.HORIZONTAL) {
                boolean reachable = components.entrySet().stream().filter(e -> e.getValue() == component)
                        .map(e -> new ChunkPos(e.getKey().x + direction.getStepX(), e.getKey().z + direction.getStepZ()))
                        .anyMatch(p -> reachesOuterBorder(claim, p));
                possible &= reachable;
            }
            assertEquals(possible, layout.valid(), "Topology " + mask + ": " + layout.problemSummary());
            if (!possible) {
                assertBlocked(layout, PerimeterGateLayout.ProblemCode.NO_OUTER_ENTRANCE);
                continue;
            }
            assertEquals(4 * new HashSet<>(components.values()).size(), layout.gates().size());
            assertEquals(18 * layout.gates().size(), layout.wallOpenings().size());
            for (var gate : layout.gates()) for (long cell : gate.outsideApproach()) {
                BlockPos p = BlockPos.of(cell); assertFalse(claim.contains(new ChunkPos(p)));
                assertFalse(wall.blocks().containsKey(cell));
            }
        }
    }

    @Test void separateComponentsBoxingInAnEmptyChunkCannotUseThatHoleAsAnExit() {
        Set<ChunkPos> claim = Set.of(new ChunkPos(0, -1), new ChunkPos(1, 0),
                new ChunkPos(0, 1), new ChunkPos(-1, 0));
        assertBlocked(PerimeterGateLayout.create(claim, flat(claim), CLEAR), PerimeterGateLayout.ProblemCode.NO_OUTER_ENTRANCE);
    }

    /** Independent small-grid oracle: 3x3 fixtures have reached the outside at coordinate +/-2. */
    private static boolean reachesOuterBorder(Set<ChunkPos> claim, ChunkPos start) {
        if (claim.contains(start)) return false;
        Set<ChunkPos> seen = new HashSet<>(); ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        seen.add(start); queue.add(start);
        while (!queue.isEmpty()) {
            ChunkPos p = queue.removeFirst();
            if (Math.abs(p.x) >= 2 || Math.abs(p.z) >= 2) return true;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                ChunkPos next = new ChunkPos(p.x + d.getStepX(), p.z + d.getStepZ());
                if (!claim.contains(next) && seen.add(next)) queue.addLast(next);
            }
        }
        return false;
    }

    private static PerimeterBlueprint.Plan flat(Set<ChunkPos> chunks) {
        return PerimeterBlueprint.create(chunks, (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.COBBLESTONE);
    }
    private static Set<ChunkPos> rectangle(int x, int z, int width, int depth) {
        Set<ChunkPos> result = new HashSet<>();
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) result.add(new ChunkPos(x + dx, z + dz));
        return result;
    }
    private static void assertBlocked(PerimeterGateLayout.Layout result, PerimeterGateLayout.ProblemCode code) {
        assertFalse(result.valid()); assertEquals(code, result.problems().get(0).code());
        assertTrue(result.gates().isEmpty()); assertTrue(result.wallOpenings().isEmpty());
        assertTrue(result.passageClearance().isEmpty()); assertTrue(result.approachClearance().isEmpty());
        assertTrue(result.approachFooting().isEmpty());
    }
}
