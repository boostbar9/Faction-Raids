package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterBlueprintTest extends MinecraftTestSupport {
    private static final int BASE_Y = 64;
    private static final PerimeterBlueprint.Palette COBBLE = PerimeterBlueprint.Palette.COBBLESTONE;

    @Test void sixteenBlockClaimFitsFourCornersAndSixCellRemaindersWithoutGridRounding() {
        var plan = flat(Set.of(new ChunkPos(0, 0)));
        assertTrue(plan.valid(), plan.problemSummary());
        assertEquals(220, plan.footprint().size());
        assertEquals(968, plan.blocks().size());
        assertEquals(Map.of("minecraft:cobblestone", 748, "minecraft:oak_planks", 220), plan.materialCounts());
        assertEquals(new BlockPos(0, 64, 0), plan.min());
        assertEquals(new BlockPos(15, 69, 15), plan.max());
        assertEquals(4, plan.runs().size());
        assertEquals(8, plan.connections().size());
        Set<BlockPos> corners = new HashSet<>();
        for (var run : plan.runs()) {
            corners.add(run.start()); corners.add(run.end());
            assertEquals(12, run.length());
            assertEquals(6, run.straightLength());
        }
        assertEquals(Set.of(new BlockPos(2, 67, 2), new BlockPos(2, 67, 13),
                new BlockPos(13, 67, 2), new BlockPos(13, 67, 13)), corners);
    }

    @Test void eachConvexCornerExactlyMatchesAnExistingRotatedManualCorner() {
        var plan = flat(Set.of(new ChunkPos(0, 0)));
        for (int x : List.of(2, 13)) for (int z : List.of(2, 13)) {
            BlockPos center = new BlockPos(x, BASE_Y, z);
            Map<Long, String> cornerCells = new java.util.HashMap<>();
            plan.blocks().forEach((packed, material) -> {
                BlockPos cell = BlockPos.of(packed);
                if (Math.abs(cell.getX() - x) <= 2 && Math.abs(cell.getZ() - z) <= 2)
                    cornerCells.put(packed, material);
            });
            boolean matches = false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var manual = DefenseBlueprint.create(DefenseBlueprint.Kind.CORNER, center, direction);
                matches |= manual.blocks().equals(cornerCells);
            }
            assertTrue(matches, "Manual corner mismatch at " + center);
        }
    }

    @Test void straightCrossSectionHasThreeStoneLayersOakDeckAndThreeWideClearWalk() {
        var plan = flat(Set.of(new ChunkPos(0, 0)));
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++)
                assertEquals("minecraft:cobblestone", plan.blocks().get(new BlockPos(x, BASE_Y + y, 7).asLong()));
            assertEquals("minecraft:oak_planks", plan.blocks().get(new BlockPos(x, 67, 7).asLong()));
            assertEquals(x == 0 || x == 4 ? "minecraft:cobblestone" : null,
                    plan.blocks().get(new BlockPos(x, 68, 7).asLong()));
            assertTrue(plan.clearance().contains(new BlockPos(x, 69, 7).asLong()));
            if (x > 0 && x < 4) assertTrue(plan.clearance().contains(new BlockPos(x, 68, 7).asLong()));
        }
    }

    @Test void sixteenThirtyTwoFortyEightAndEveryFiveGridRemainderKeepExactClaimEdges() {
        for (int chunksWide = 1; chunksWide <= 5; chunksWide++) {
            var plan = flat(rectangle(-3, -2, chunksWide, 2));
            int width = chunksWide * 16;
            assertTrue(plan.valid(), plan.problemSummary());
            assertEquals(-48, plan.min().getX());
            assertEquals(-48 + width - 1, plan.max().getX());
            assertEquals(width * 32 - (width - 10) * 22, plan.columns().size());
            for (var run : plan.runs())
                assertEquals(run.direction().getAxis() == Direction.Axis.X ? width - 10 : 22, run.straightLength());
            assertReservedCellsInsideClaim(plan, rectangle(-3, -2, chunksWide, 2));
        }
    }

    @Test void negativeCoordinatesTranslateExactlyWithoutChangingCountsOrFacing() {
        Set<ChunkPos> shape = Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1));
        Set<ChunkPos> moved = new HashSet<>();
        for (ChunkPos chunk : shape) moved.add(new ChunkPos(chunk.x - 4, chunk.z - 3));
        var first = flat(shape);
        var second = flat(moved);
        assertEquals(first.materialCounts(), second.materialCounts());
        first.blocks().forEach((position, material) -> assertEquals(material,
                second.blocks().get(BlockPos.of(position).offset(-64, 0, -48).asLong())));
        assertEquals(first.blocks().size(), second.blocks().size());
        assertReservedCellsInsideClaim(second, moved);
    }

    @Test void inwardCornerHasFullClaimedJoinInsteadOfTwoDisconnectedEdgeStrips() {
        Set<ChunkPos> chunks = Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1));
        var plan = flat(chunks);
        assertTrue(plan.valid(), plan.problemSummary());
        assertEquals(6, plan.runs().size());
        for (int x = 11; x <= 15; x++) for (int z = 11; z <= 15; z++)
            assertEquals("minecraft:oak_planks", plan.blocks().get(new BlockPos(x, 67, z).asLong()));
        assertEquals("minecraft:cobblestone", plan.blocks().get(new BlockPos(15, 68, 15).asLong()));
        assertFalse(plan.blocks().containsKey(new BlockPos(13, 68, 13).asLong()));
        assertReservedCellsInsideClaim(plan, chunks);
        assertAllWalkCellsConnected(plan, 1);
    }

    @Test void holeHasAnIndependentClosedInnerWalkWhollyInsideClaim() {
        Set<ChunkPos> chunks = rectangle(-1, -1, 3, 3);
        chunks.remove(new ChunkPos(0, 0));
        var plan = flat(chunks);
        assertTrue(plan.valid(), plan.problemSummary());
        assertEquals(8, plan.runs().size());
        assertAllWalkCellsConnected(plan, 2);
        assertReservedCellsInsideClaim(plan, chunks);
        assertEquals(Set.of(0), new HashSet<>(plan.columns().stream().map(PerimeterBlueprint.Column::componentId).toList()));
    }

    @Test void disconnectedAndDiagonalClaimsRemainSeparateWithoutOutsideBridges() {
        for (Set<ChunkPos> chunks : List.of(Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)),
                Set.of(new ChunkPos(-1, -1), new ChunkPos(0, 0)))) {
            var plan = flat(chunks);
            assertTrue(plan.valid(), plan.problemSummary());
            assertEquals(1936, plan.blocks().size());
            assertEquals(8, plan.runs().size());
            assertAllWalkCellsConnected(plan, 2);
            assertEquals(2, plan.columns().stream().map(PerimeterBlueprint.Column::componentId).distinct().count());
            assertReservedCellsInsideClaim(plan, chunks);
        }
    }

    @Test void allThreeByThreeChunkTopologiesHaveClaimedClosedJoinedWalksAndExactDeduplication() {
        // Covers concavity, holes, diagonal contacts, multiple components and all rotations/reflections.
        for (int mask = 1; mask < 512; mask++) {
            Set<ChunkPos> chunks = new HashSet<>();
            for (int bit = 0; bit < 9; bit++) if ((mask & (1 << bit)) != 0)
                chunks.add(new ChunkPos(bit % 3 - 1, bit / 3 - 1));
            var plan = flat(chunks);
            assertTrue(plan.valid(), "Topology " + mask + ": " + plan.problemSummary());
            assertReservedCellsInsideClaim(plan, chunks);
            assertEquals(plan.blocks().size(), plan.materialCounts().values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(plan.columns().size(), plan.columns().stream().map(PerimeterBlueprint.Column::base).distinct().count());
            Set<Long> centerline = new HashSet<>();
            plan.columns().stream().filter(c -> c.inwardDistance() == 3).forEach(c -> centerline.add(c.base().above(3).asLong()));
            Set<Long> runs = new HashSet<>();
            for (var run : plan.runs()) for (int i = 0; i < run.length(); i++)
                runs.add(run.start().relative(run.direction(), i).asLong());
            assertEquals(centerline, runs, "Every centerline cell must belong to a closed fitted run");
            for (var column : plan.columns()) {
                int distance = column.inwardDistance();
                assertTrue(distance >= 1 && distance <= 5);
                // All cells closer than the selected distance stay inside; some cell at that distance is outside.
                boolean outsideAtDistance = false;
                for (int dx = -distance; dx <= distance; dx++) for (int dz = -distance; dz <= distance; dz++) {
                    boolean claimed = chunks.contains(new ChunkPos(column.base().offset(dx, 0, dz)));
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < distance) assertTrue(claimed);
                    else outsideAtDistance |= !claimed;
                }
                assertTrue(outsideAtDistance);
            }
        }
    }

    @Test void connectionsExposeOpenThreeWideCornerFacesWithMatchingDeckHeight() {
        var plan = flat(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1)));
        for (var connection : plan.connections()) {
            assertEquals(3, connection.width());
            assertEquals(67, connection.deckY());
            var run = plan.runs().get(connection.runId());
            assertEquals(run.componentId(), connection.componentId());
            for (int cross = -1; cross <= 1; cross++) {
                BlockPos deck = connection.center().relative(connection.facing().getClockWise(), cross);
                assertEquals("minecraft:oak_planks", plan.blocks().get(deck.asLong()));
                assertTrue(plan.clearance().contains(deck.above().asLong()));
                assertTrue(plan.clearance().contains(deck.above(2).asLong()));
            }
        }
    }

    @Test void unequalSurfacesHaveOneLevelDeckAndExactBoundedDirtSupports() {
        var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                (x, z) -> PerimeterBlueprint.Surface.ready(x < 8 ? 60 : 64), COBBLE);
        assertTrue(plan.valid(), plan.problemSummary());
        assertEquals(440, plan.materialCounts().get("minecraft:dirt"));
        assertEquals(1408, plan.blocks().size());
        assertEquals(new BlockPos(0, 60, 0), plan.min());
        for (var column : plan.columns()) {
            assertEquals(64, column.base().getY());
            assertEquals(column.base().getX() < 8 ? 4 : 0, column.supportDepth());
            for (int y = column.foundationBase().getY(); y < 64; y++)
                assertEquals("minecraft:dirt", plan.blocks().get(column.base().atY(y).asLong()));
        }
    }

    @Test void separateComponentsMayUseIndependentSafeLevelsWithoutPhantomTransitions() {
        var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)),
                (x, z) -> PerimeterBlueprint.Surface.ready(x < 16 ? 64 : 72), COBBLE);
        assertTrue(plan.valid(), plan.problemSummary());
        assertFalse(plan.materialCounts().containsKey("minecraft:dirt"));
        for (var column : plan.columns()) assertEquals(column.base().getX() < 16 ? 64 : 72, column.base().getY());
        assertEquals(1936, plan.blocks().size());
    }

    @Test void eightSupportBlocksAreAllowedButNineBlockReliefRejectsTheWholeWall() {
        for (int relief : List.of(8, 9)) {
            var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                    (x, z) -> PerimeterBlueprint.Surface.ready(x == 0 && z == 0 ? 64 - relief : 64), COBBLE);
            if (relief == 8) {
                assertTrue(plan.valid(), plan.problemSummary());
                assertEquals(8, plan.materialCounts().get("minecraft:dirt"));
            } else assertBlocked(plan, PerimeterBlueprint.ProblemCode.FOUNDATION_LIMIT);
        }
    }

    @Test void unsafeOrUnresolvedSurfaceNeverReturnsAPartialBuildablePerimeter() {
        var blocked = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                (x, z) -> x == 0 && z == 0 ? PerimeterBlueprint.Surface.blocked("Protected ground")
                        : PerimeterBlueprint.Surface.ready(64), COBBLE);
        assertBlocked(blocked, PerimeterBlueprint.ProblemCode.UNSAFE_SURFACE);
        assertEquals("Protected ground", blocked.problemSummary());
        assertEquals(new BlockPos(0, 0, 0), blocked.problems().get(0).position());
        assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), (x, z) -> null, COBBLE),
                PerimeterBlueprint.ProblemCode.UNSAFE_SURFACE);
        var calls = new AtomicInteger();
        var unavailable = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), (x, z) -> {
            calls.incrementAndGet(); throw new IllegalStateException("Unloaded");
        }, COBBLE);
        assertBlocked(unavailable, PerimeterBlueprint.ProblemCode.UNSAFE_SURFACE);
        assertEquals(32, calls.get());
    }

    @Test void worldHeightIncludesFoundationFootingAndFullWalkHeadroom() {
        for (int y : List.of(-64, 315, Integer.MIN_VALUE, Integer.MAX_VALUE))
            assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                    (x, z) -> PerimeterBlueprint.Surface.ready(y), COBBLE), PerimeterBlueprint.ProblemCode.HEIGHT_LIMIT);
        for (int y : List.of(-63, 314)) assertTrue(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                (x, z) -> PerimeterBlueprint.Surface.ready(y), COBBLE).valid());
    }

    @Test void budgetsRejectBeforeSurfaceReadsWherePossibleAndNeverTruncate() {
        AtomicInteger reads = new AtomicInteger();
        PerimeterBlueprint.SurfaceResolver resolver = (x, z) -> {
            reads.incrementAndGet(); return PerimeterBlueprint.Surface.ready(64);
        };
        var limits = new PerimeterBlueprint.Limits(1, 64, 220, 968, 8, -64, 320, 256, 1_048_576L);
        assertTrue(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), resolver, COBBLE, limits).valid());
        assertEquals(220, reads.get()); reads.set(0);
        assertBlocked(PerimeterBlueprint.create(rectangle(0, 0, 2, 1), resolver, COBBLE, limits),
                PerimeterBlueprint.ProblemCode.CLAIM_LIMIT);
        assertEquals(0, reads.get());
        limits = new PerimeterBlueprint.Limits(10, 63, 220, 968, 8, -64, 320, 256, 1_048_576L);
        assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), resolver, COBBLE, limits),
                PerimeterBlueprint.ProblemCode.BOUNDARY_LIMIT);
        assertEquals(0, reads.get());
        limits = new PerimeterBlueprint.Limits(10, 100, 219, 968, 8, -64, 320, 256, 1_048_576L);
        assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), resolver, COBBLE, limits),
                PerimeterBlueprint.ProblemCode.FOOTPRINT_LIMIT);
        assertEquals(0, reads.get());
        limits = new PerimeterBlueprint.Limits(10, 200, 1000, 2000, 8, -64, 320, 32, 1_048_576L);
        assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)), resolver, COBBLE, limits),
                PerimeterBlueprint.ProblemCode.BOUNDS_LIMIT);
        assertEquals(0, reads.get());
        limits = new PerimeterBlueprint.Limits(10, 100, 220, 967, 8, -64, 320, 256, 1_048_576L);
        assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), resolver, COBBLE, limits),
                PerimeterBlueprint.ProblemCode.BLOCK_LIMIT);
    }

    @Test void emptyOverflowAndUnboundedNativeEnvelopeFailClosed() {
        assertBlocked(flat(Set.of()), PerimeterBlueprint.ProblemCode.EMPTY_CLAIM);
        for (ChunkPos chunk : List.of(new ChunkPos(Integer.MAX_VALUE, 0), new ChunkPos(Integer.MIN_VALUE, 0),
                new ChunkPos(0, 1_875_000)))
            assertBlocked(flat(Set.of(chunk)), PerimeterBlueprint.ProblemCode.COORDINATE_LIMIT);
        assertBlocked(flat(Set.of(new ChunkPos(0, 0), new ChunkPos(16, 0))), PerimeterBlueprint.ProblemCode.BOUNDS_LIMIT);
        var limits = new PerimeterBlueprint.Limits(10, 100, 220, 1000, 8, -64, 320, 256, 1535);
        assertBlocked(PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                (x, z) -> PerimeterBlueprint.Surface.ready(64), COBBLE, limits), PerimeterBlueprint.ProblemCode.BOUNDS_LIMIT);
    }

    @Test void allThreeLegacyPaletteChoicesRetainExactDeduplicatedRequirements() {
        for (var palette : List.of(COBBLE, PerimeterBlueprint.Palette.STONE_BRICKS, PerimeterBlueprint.Palette.OAK)) {
            var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                    (x, z) -> PerimeterBlueprint.Surface.ready(64), palette);
            assertTrue(plan.valid(), plan.problemSummary());
            assertEquals(968, plan.blocks().size());
            assertEquals(968, plan.materialCounts().values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(palette.wall(), plan.blocks().get(new BlockPos(0, 64, 0).asLong()));
            assertEquals("minecraft:oak_planks", plan.blocks().get(new BlockPos(0, 67, 0).asLong()));
            assertEquals(palette == PerimeterBlueprint.Palette.OAK ? 1 : 2, plan.materialCounts().size());
        }
    }

    @Test void inputIterationOrderCannotChangePlanOrConnectorIdentities() {
        List<ChunkPos> chunks = new ArrayList<>(rectangle(-1, -1, 3, 3));
        chunks.remove(new ChunkPos(0, 0));
        var first = flat(new LinkedHashSet<>(chunks));
        Collections.reverse(chunks);
        assertEquals(first, flat(new LinkedHashSet<>(chunks)));
        assertThrows(UnsupportedOperationException.class, () -> first.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.columns().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.clearance().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.materialCounts().clear());
    }

    private static PerimeterBlueprint.Plan flat(Set<ChunkPos> chunks) {
        return PerimeterBlueprint.create(chunks, (x, z) -> PerimeterBlueprint.Surface.ready(BASE_Y), COBBLE);
    }

    private static Set<ChunkPos> rectangle(int x, int z, int width, int depth) {
        Set<ChunkPos> result = new HashSet<>();
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) result.add(new ChunkPos(x + dx, z + dz));
        return result;
    }

    private static void assertBlocked(PerimeterBlueprint.Plan plan, PerimeterBlueprint.ProblemCode code) {
        assertFalse(plan.valid());
        assertFalse(plan.problems().isEmpty());
        assertEquals(code, plan.problems().get(0).code());
        assertTrue(plan.blocks().isEmpty(), "A blocked plan must not expose partial construction cells");
        assertTrue(plan.columns().isEmpty());
        assertTrue(plan.clearance().isEmpty());
        assertTrue(plan.materialCounts().isEmpty());
        assertNull(plan.min()); assertNull(plan.max());
    }

    private static void assertReservedCellsInsideClaim(PerimeterBlueprint.Plan plan, Set<ChunkPos> chunks) {
        assertTrue(plan.valid(), plan.problemSummary());
        Set<Long> cells = new HashSet<>(plan.blocks().keySet());
        assertTrue(Collections.disjoint(cells, plan.clearance()));
        cells.addAll(plan.clearance());
        for (long packed : cells) {
            BlockPos pos = BlockPos.of(packed);
            assertTrue(chunks.contains(new ChunkPos(pos)), "Outside claim: " + pos);
            assertTrue(pos.getX() >= plan.min().getX() && pos.getX() <= plan.max().getX());
            assertTrue(pos.getY() >= plan.min().getY() && pos.getY() <= plan.max().getY());
            assertTrue(pos.getZ() >= plan.min().getZ() && pos.getZ() <= plan.max().getZ());
        }
    }

    private static void assertAllWalkCellsConnected(PerimeterBlueprint.Plan plan, int expectedLoops) {
        Set<BlockPos> walk = new HashSet<>();
        plan.columns().stream().filter(c -> !c.parapet()).forEach(c -> walk.add(c.base()));
        int components = 0;
        while (!walk.isEmpty()) {
            BlockPos seed = walk.iterator().next();
            walk.remove(seed);
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                for (Direction direction : Direction.Plane.HORIZONTAL)
                    if (walk.remove(pos.relative(direction))) queue.addLast(pos.relative(direction));
            }
            components++;
        }
        assertEquals(expectedLoops, components);
    }
}
