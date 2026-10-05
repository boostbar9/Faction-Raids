package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only candidate compiler for four outer entrances per edge-connected claim component.
 * This is deliberately not connected to commissioning: the current saved manifest cannot protect
 * approach columns outside the wall/claim. See docs/cardinal-perimeter-gates.md before integrating.
 * A result describes newly reviewed construction only, never permission to remove existing blocks.
 */
public final class PerimeterGateLayout {
    public static final int PASSAGE_WIDTH = 3, PASSAGE_HEIGHT = 3, APPROACH_DEPTH = 3;
    public static final int MAX_PASSAGE_CHECKS = 16_384, MAX_GEOMETRY_CHECKS = 16_384;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final List<Direction> CARDINALS = List.of(Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST);
    private static final Comparator<ChunkPos> CHUNK_ORDER = Comparator
            .comparingInt((ChunkPos p) -> p.x).thenComparingInt(p -> p.z);

    private PerimeterGateLayout() {}

    public enum Region { WALL, INSIDE_APPROACH, OUTSIDE_APPROACH }

    /**
     * Null means a verified three-high passage at exactly feet, with dry safe footing below.
     * The caller must check loaded terrain, collisions, fluids, block entities, permissions and
     * reservations. For WALL only, the unchanged plan may supply the floor. Approach floors and
     * headroom must already be usable without any mutation. Exceptions fail closed.
     * This read-only callback must not load chunks, clear plants, place blocks or navigate entities.
     */
    @FunctionalInterface public interface PassageValidator {
        String problem(BlockPos feet, Region region);
    }

    public enum ProblemCode { INVALID_INPUT, NO_OUTER_ENTRANCE, NO_SAFE_APPROACH, BUDGET_LIMIT }
    public record Problem(ProblemCode code, int componentId, Direction direction, String message) {}

    /** outerCenter is the middle of the outer skin, at feet height; facing points out of the claim. */
    public record Gate(int componentId, Direction facing, BlockPos outerCenter, Set<Long> passage,
                       Set<Long> insideApproach, Set<Long> outsideApproach, Set<Long> approachFooting) {
        public Gate {
            outerCenter = outerCenter.immutable();
            passage = freeze(passage); insideApproach = freeze(insideApproach);
            outsideApproach = freeze(outsideApproach); approachFooting = freeze(approachFooting);
        }
    }

    /**
     * wallOpenings are omitted NEW wall targets, not demolition targets. Approach cells, including
     * solid footing, require a distinct immutable read-only reservation contract before activation.
     * Invalid results expose no partial entrances or cell sets.
     */
    public record Layout(List<Gate> gates, Set<Long> wallOpenings, Set<Long> passageClearance,
                         Set<Long> approachClearance, Set<Long> approachFooting, List<Problem> problems) {
        public Layout {
            gates = List.copyOf(gates); wallOpenings = freeze(wallOpenings);
            passageClearance = freeze(passageClearance); approachClearance = freeze(approachClearance);
            approachFooting = freeze(approachFooting); problems = List.copyOf(problems);
        }
        public boolean valid() { return problems.isEmpty() && !gates.isEmpty(); }
        public String problemSummary() { return problems.isEmpty() ? "" : problems.get(0).message(); }
    }

    public static Layout create(Set<ChunkPos> chunks, PerimeterBlueprint.Plan wall, PassageValidator validator) {
        return create(chunks, wall, validator, MAX_PASSAGE_CHECKS);
    }

    static Layout create(Set<ChunkPos> chunks, PerimeterBlueprint.Plan wall,
                         PassageValidator validator, int maxPassageChecks) {
        Objects.requireNonNull(validator, "A live read-only passage validator is required");
        if (maxPassageChecks < 1 || maxPassageChecks > MAX_PASSAGE_CHECKS)
            throw new IllegalArgumentException("Invalid gate passage-check budget");
        if (!bounded(chunks, wall)) return failed(ProblemCode.INVALID_INPUT, -1, null,
                "A complete bounded, freshly compiled wall and its exact territory are required.");
        Set<ChunkPos> claim = Set.copyOf(chunks);
        Map<ChunkPos, Integer> components = components(claim);
        Map<Integer, Bounds> bounds = new HashMap<>();
        components.forEach((chunk, id) -> bounds.computeIfAbsent(id, ignored -> new Bounds()).add(chunk));
        Set<ChunkPos> exterior = exterior(claim);
        Map<Long, PerimeterBlueprint.Column> columns = new HashMap<>();
        for (var column : wall.columns()) {
            if (column == null || column.base() == null || column.supportDepth() < 0 || column.supportDepth() > 64
                    || column.inwardDistance() < 1 || column.inwardDistance() > PerimeterBlueprint.WALL_WIDTH
                    || !Objects.equals(components.get(new ChunkPos(column.base())), column.componentId())
                    || columns.putIfAbsent(xz(column.base()), column) != null)
                return failed(ProblemCode.INVALID_INPUT, -1, null, "Wall columns do not match the reviewed territory.");
        }
        Map<Key, List<Site>> candidates = new HashMap<>();
        Map<Long, Boolean> runCells = new HashMap<>();
        Set<Long> repeatedEndpoints = new HashSet<>();
        int runIndex = 0, geometryChecks = 0;
        for (var run : wall.runs()) {
            if (!validRun(run, components) || run.id() != runIndex++) return failed(ProblemCode.INVALID_INPUT, -1, null,
                    "Wall runs do not match the reviewed territory.");
            if ((long) geometryChecks + run.length() > MAX_GEOMETRY_CHECKS) return failed(ProblemCode.BUDGET_LIMIT, -1, null,
                    "The complete entrance geometry exceeds its bounded work budget.");
            geometryChecks += run.length();
            for (int offset = 0; offset < run.length(); offset++) {
                BlockPos p = run.start().relative(run.direction(), offset);
                var column = columns.get(xz(p));
                if (column == null || column.componentId() != run.componentId() || column.inwardDistance() != 3
                        || column.deckY() != p.getY()) return failed(ProblemCode.INVALID_INPUT, -1, null,
                        "A wall run is not an exact fitted walk centerline.");
                boolean endpoint = offset == 0 || offset == run.length() - 1;
                Boolean prior = runCells.putIfAbsent(xz(p), endpoint);
                if (prior != null && (!prior || !endpoint || !repeatedEndpoints.add(xz(p))))
                    return failed(ProblemCode.INVALID_INPUT, -1, null, "Wall runs overlap or repeat.");
            }
            // Keep the complete three-wide opening beyond the intact five-cell corner footprint.
            // Store only lightweight sites; expand cell sets one candidate at a time during review.
            for (int offset = 4; offset <= run.length() - 5; offset++) {
                BlockPos center = run.start().relative(run.direction(), offset).below(PerimeterBlueprint.DECK_OFFSET);
                for (Direction facing : List.of(run.direction().getClockWise(), run.direction().getCounterClockWise()))
                    candidates.computeIfAbsent(new Key(run.componentId(), facing), ignored -> new ArrayList<>())
                            .add(new Site(run.componentId(), center, facing));
            }
        }
        List<Gate> selected = new ArrayList<>();
        Map<Probe, String> checked = new HashMap<>();
        for (int component = 0; component < bounds.size(); component++) for (Direction direction : CARDINALS) {
            List<Site> options = candidates.getOrDefault(new Key(component, direction), List.of());
            Gate accepted = null;
            boolean hasOuterEntrance = false;
            String firstProblem = null;
            for (Site site : options.stream().sorted(order(bounds.get(component), direction)).toList()) {
                Gate option = candidate(site.component(), site.center(), site.facing(), claim, components, exterior, columns, wall);
                if (option == null) continue;
                hasOuterEntrance = true;
                String problem = null;
                for (Probe probe : probes(option)) {
                    if (!checked.containsKey(probe)) {
                        if (checked.size() >= maxPassageChecks) return failed(ProblemCode.BUDGET_LIMIT, component, direction,
                                "The complete entrance review exceeds its bounded terrain-check budget.");
                        String result;
                        try { result = validator.problem(probe.feet(), probe.region()); }
                        catch (RuntimeException unavailable) { result = "Entrance terrain could not be verified."; }
                        checked.put(probe, result == null ? "" : result.isBlank() ? "Entrance terrain is blocked." : result);
                    }
                    String result = checked.get(probe);
                    if (!result.isEmpty()) { problem = result; break; }
                }
                if (problem == null) { accepted = option; break; }
                if (firstProblem == null) firstProblem = problem;
            }
            if (!hasOuterEntrance) return failed(ProblemCode.NO_OUTER_ENTRANCE, component, direction,
                    "No complete " + direction.getName() + " entrance opens onto the outer unclaimed area.");
            if (accepted == null) return failed(ProblemCode.NO_SAFE_APPROACH, component, direction,
                    "No safe " + direction.getName() + " entrance with clear inside and outside approaches. " + firstProblem);
            selected.add(accepted);
        }
        Set<Long> openings = new LinkedHashSet<>(), passage = new LinkedHashSet<>(), approach = new LinkedHashSet<>(), footing = new LinkedHashSet<>();
        for (Gate gate : selected) {
            passage.addAll(gate.passage());
            gate.passage().stream().filter(wall.blocks()::containsKey).forEach(openings::add);
            approach.addAll(gate.insideApproach()); approach.addAll(gate.outsideApproach()); footing.addAll(gate.approachFooting());
        }
        Set<Long> reserved = new HashSet<>(wall.blocks().keySet()); reserved.addAll(wall.clearance());
        reserved.addAll(approach); reserved.addAll(footing);
        if (reserved.size() > PerimeterStageLayout.MAX_RESERVED) return failed(ProblemCode.BUDGET_LIMIT, -1, null,
                "The complete wall and entrance reservations exceed the manifest budget.");
        return new Layout(selected, openings, passage, approach, footing, List.of());
    }

    private static Gate candidate(int component, BlockPos center, Direction facing, Set<ChunkPos> claim,
                                  Map<ChunkPos, Integer> components, Set<ChunkPos> exterior,
                                  Map<Long, PerimeterBlueprint.Column> columns, PerimeterBlueprint.Plan wall) {
        Direction tangent = facing.getClockWise();
        Set<Long> passage = new LinkedHashSet<>(), inside = new LinkedHashSet<>(), outside = new LinkedHashSet<>(), footing = new LinkedHashSet<>();
        for (int across = -1; across <= 1; across++) for (int depth = -5; depth <= 5; depth++) {
            BlockPos feet = center.relative(tangent, across).relative(facing, depth);
            if (Math.abs((long) feet.getX()) >= WORLD_LIMIT || Math.abs((long) feet.getZ()) >= WORLD_LIMIT) return null;
            if (depth >= -2 && depth <= 2) {
                var column = columns.get(xz(feet));
                if (column == null || column.componentId() != component || column.base().getY() != center.getY()
                        || column.inwardDistance() != 3 - depth
                        || !"minecraft:oak_planks".equals(wall.blocks().get(feet.above(3).asLong()))) return null;
                for (int y = 0; y < PASSAGE_HEIGHT; y++) {
                    long cell = feet.above(y).asLong();
                    if (!wall.blocks().containsKey(cell) && !wall.clearance().contains(cell)) return null;
                    passage.add(cell);
                }
            } else {
                // Approaches must not cut any other wall, turn into a claim hole, or cross another component.
                if (columns.containsKey(xz(feet))) return null;
                ChunkPos chunk = new ChunkPos(feet);
                if (depth < -2 ? !Objects.equals(components.get(chunk), component)
                        : claim.contains(chunk) || !exterior.contains(chunk)) return null;
                Set<Long> cells = depth < -2 ? inside : outside;
                for (int y = 0; y < PASSAGE_HEIGHT; y++) cells.add(feet.above(y).asLong());
                footing.add(feet.below().asLong());
            }
        }
        return new Gate(component, facing, center.relative(facing, 2), passage, inside, outside, footing);
    }

    private record Key(int component, Direction direction) {}
    private record Site(int component, BlockPos center, Direction facing) {
        BlockPos outerCenter() { return center.relative(facing, 2); }
    }
    private record Probe(BlockPos feet, Region region) {}
    private static List<Probe> probes(Gate gate) {
        List<Probe> result = new ArrayList<>();
        int y = gate.outerCenter().getY();
        addProbes(result, gate.passage(), y, Region.WALL);
        addProbes(result, gate.insideApproach(), y, Region.INSIDE_APPROACH);
        addProbes(result, gate.outsideApproach(), y, Region.OUTSIDE_APPROACH);
        return result;
    }
    private static void addProbes(List<Probe> result, Set<Long> cells, int y, Region region) {
        cells.stream().map(BlockPos::of).filter(p -> p.getY() == y)
                .sorted(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                .forEach(p -> result.add(new Probe(p, region)));
    }
    private static Comparator<Site> order(Bounds b, Direction direction) {
        return Comparator.comparingInt((Site gate) -> -(gate.outerCenter().getX() * direction.getStepX()
                        + gate.outerCenter().getZ() * direction.getStepZ()))
                .thenComparingLong(gate -> direction.getAxis() == Direction.Axis.X
                        ? Math.abs(2L * gate.outerCenter().getZ() - b.minZ - b.maxZ)
                        : Math.abs(2L * gate.outerCenter().getX() - b.minX - b.maxX))
                .thenComparingInt(gate -> gate.outerCenter().getX()).thenComparingInt(gate -> gate.outerCenter().getZ());
    }

    private static boolean bounded(Set<ChunkPos> chunks, PerimeterBlueprint.Plan wall) {
        if (chunks == null || chunks.isEmpty() || chunks.size() > PerimeterTerritory.MAX_CHUNKS
                || wall == null || !wall.valid() || wall.columns().isEmpty()
                || wall.columns().size() > PerimeterStageLayout.MAX_COLUMNS || wall.runs().isEmpty()
                || wall.runs().size() > PerimeterStageLayout.MAX_COLUMNS
                || wall.blocks().size() > PerimeterStageLayout.MAX_TARGETS
                || (long) wall.blocks().size() + wall.clearance().size() > PerimeterStageLayout.MAX_RESERVED) return false;
        Bounds b = new Bounds();
        for (ChunkPos chunk : chunks) {
            if (chunk == null || (long) chunk.x * 16 < -WORLD_LIMIT || (long) chunk.z * 16 < -WORLD_LIMIT
                    || (long) chunk.x * 16 + 15 >= WORLD_LIMIT || (long) chunk.z * 16 + 15 >= WORLD_LIMIT) return false;
            b.add(chunk);
        }
        return (long) b.maxX - b.minX + 1 <= 256 && (long) b.maxZ - b.minZ + 1 <= 256;
    }
    private static boolean validRun(PerimeterBlueprint.Run run, Map<ChunkPos, Integer> components) {
        if (run == null || run.start() == null || run.end() == null
                || run.direction() != Direction.EAST && run.direction() != Direction.SOUTH
                || run.start().getY() != run.end().getY() || run.start().getY() < -2044 || run.start().getY() > 2045
                || !Objects.equals(components.get(new ChunkPos(run.start())), run.componentId())
                || !Objects.equals(components.get(new ChunkPos(run.end())), run.componentId())) return false;
        long dx = (long) run.end().getX() - run.start().getX(), dz = (long) run.end().getZ() - run.start().getZ();
        return run.direction() == Direction.EAST ? dz == 0 && dx >= 5 && dx < 256 : dx == 0 && dz >= 5 && dz < 256;
    }
    private static Map<ChunkPos, Integer> components(Set<ChunkPos> claim) {
        Map<ChunkPos, Integer> result = new HashMap<>(); int id = 0;
        for (ChunkPos seed : claim.stream().sorted(CHUNK_ORDER).toList()) {
            if (result.containsKey(seed)) continue;
            ArrayDeque<ChunkPos> queue = new ArrayDeque<>(); queue.add(seed); result.put(seed, id);
            while (!queue.isEmpty()) {
                ChunkPos current = queue.removeFirst();
                for (Direction d : CARDINALS) {
                    ChunkPos next = neighbour(current, d);
                    if (claim.contains(next) && result.putIfAbsent(next, id) == null) queue.addLast(next);
                }
            }
            id++;
        }
        return result;
    }
    private static Set<ChunkPos> exterior(Set<ChunkPos> claim) {
        int minX = claim.stream().mapToInt(c -> c.x).min().orElseThrow() - 1;
        int maxX = claim.stream().mapToInt(c -> c.x).max().orElseThrow() + 1;
        int minZ = claim.stream().mapToInt(c -> c.z).min().orElseThrow() - 1;
        int maxZ = claim.stream().mapToInt(c -> c.z).max().orElseThrow() + 1;
        Set<ChunkPos> result = new HashSet<>(); ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        ChunkPos seed = new ChunkPos(minX, minZ); result.add(seed); queue.add(seed);
        while (!queue.isEmpty()) {
            ChunkPos current = queue.removeFirst();
            for (Direction d : CARDINALS) {
                ChunkPos next = neighbour(current, d);
                if (next.x >= minX && next.x <= maxX && next.z >= minZ && next.z <= maxZ
                        && !claim.contains(next) && result.add(next)) queue.addLast(next);
            }
        }
        return result;
    }
    private static final class Bounds {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        void add(ChunkPos c) {
            minX = Math.min(minX, c.x * 16); minZ = Math.min(minZ, c.z * 16);
            maxX = Math.max(maxX, c.x * 16 + 15); maxZ = Math.max(maxZ, c.z * 16 + 15);
        }
    }
    private static ChunkPos neighbour(ChunkPos p, Direction d) { return new ChunkPos(p.x + d.getStepX(), p.z + d.getStepZ()); }
    private static long xz(BlockPos p) { return new BlockPos(p.getX(), 0, p.getZ()).asLong(); }
    private static Set<Long> freeze(Set<Long> values) { return Collections.unmodifiableSet(new LinkedHashSet<>(values)); }
    private static Layout failed(ProblemCode code, int component, Direction direction, String message) {
        return new Layout(List.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                List.of(new Problem(code, component, direction, message)));
    }
}
