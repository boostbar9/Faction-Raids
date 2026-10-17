package com.devfarinsky.siegeoverhaul.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure height-feasibility proposal for prevalidated, short perimeter bands on closed walk loops.
 * Does not compile a block blueprint, validate a Minecraft collision shape, authorize grading,
 * select a physical ramp, or change an accepted plan. Every non-level join remains an explicit
 * pending physical/native-verification requirement. See docs/terrain-following-perimeter.md.
 */
public final class PerimeterSteppedProfile {
    private static final int WORLD_LIMIT = 30_000_000;
    private static final long INF = Long.MAX_VALUE / 4;
    private PerimeterSteppedProfile() {}

    public enum Kind { STRAIGHT, CORNER, GATE }
    public enum Facing { NORTH, EAST, SOUTH, WEST }
    public enum Role { WALL, INSIDE_GATE_PAD, OUTSIDE_GATE_PAD }
    public enum ProblemCode { INVALID_INPUT, NO_HEIGHT_PROFILE, WORK_BUDGET, GRADING_BUDGET }

    /** surfaceY is the first air cell above verified ground; gradingAllowed is a proposal hint, not authority. */
    public record Sample(int x, int z, int surfaceY, Role role, boolean gradingAllowed) {}
    /** stepAfter describes this band's boundary with the next band in its explicitly ordered closed loop. */
    public record Band(int id, int length, Kind kind, Facing facing, List<Sample> samples, boolean stepAfter) {
        public Band { samples = List.copyOf(samples); }
    }
    public record Lane(int fromX, int fromZ, int toX, int toZ) {}
    /** Five adjacent lane pairs: outer skin, three-wide walk, inner skin. Every pair crosses one block. */
    public record Seam(int fromBand, int toBand, Facing direction, List<Lane> lanes) {
        public Seam { lanes = List.copyOf(lanes); }
    }
    public record Loop(int componentId, int loopId, boolean outer, List<Band> bands, List<Seam> seams) {
        public Loop { bands = List.copyOf(bands); seams = List.copyOf(seams); }
    }
    public record Limits(int maxCut, int maxFill, int minBaseY, int maxBaseY, int maxBands,
                         int maxSamples, long maxOperations, long maxCutCells, long maxFillCells,
                         boolean requireCardinalGates) {
        public static final Limits DEFAULT = new Limits(4, 8, -63, 314, 4096, 20_480,
                4_000_000L, 8192, 8192, true);
        public Limits {
            if (maxCut < 0 || maxCut > 4 || maxFill < 0 || maxFill > 8 || minBaseY < -2047 || maxBaseY > 2042
                    || minBaseY > maxBaseY || maxBands < 1 || maxBands > 4096 || maxSamples < 1 || maxSamples > 20_480
                    || maxOperations < 1 || maxOperations > 4_000_000L || maxCutCells < 0 || maxCutCells > 65_536
                    || maxFillCells < 0 || maxFillCells > 65_536) throw new IllegalArgumentException("Invalid stepped-profile limits");
        }
    }
    public record AssignedBand(int componentId, int loopId, int bandId, int baseY, Kind kind, Facing facing, List<Sample> samples) {
        public AssignedBand { samples = samples.stream().sorted(Comparator.comparingInt(Sample::x).thenComparingInt(Sample::z)).toList(); }
    }
    public record ColumnLevel(Sample sample, int baseY) {}
    /** No slab/stair/shape choice is encoded here: this is a requirement, never an executable transition. */
    public record PendingTransition(int componentId, int loopId, int fromBand, int toBand, int fromY, int toY, Seam seam) {}
    /** Complete level and stepped seam metadata is retained for the later physical geometry compiler. */
    public record SeamLevel(int componentId, int loopId, Seam seam, int fromY, int toY) {}
    public record Problem(ProblemCode code, String message) {}
    public record Proposal(List<AssignedBand> bands, List<ColumnLevel> columns, List<PendingTransition> transitions,
                           List<SeamLevel> seams, long cutCells, long fillCells, long operations, List<Problem> problems) {
        public Proposal {
            bands = List.copyOf(bands); columns = List.copyOf(columns); transitions = List.copyOf(transitions);
            seams = List.copyOf(seams); problems = List.copyOf(problems);
        }
        public boolean heightFeasible() { return problems.isEmpty() && !bands.isEmpty(); }
        /** Even a flat height assignment lacks the separate geometry/world/native proof required for construction. */
        public boolean executable() { return false; }
    }

    public static Proposal propose(List<Loop> loops) { return propose(loops, Limits.DEFAULT); }
    public static Proposal propose(List<Loop> loops, Limits limits) {
        if (limits == null) throw new IllegalArgumentException("Profile limits are required");
        String invalid = validate(loops, limits);
        if (invalid != null) return failed(ProblemCode.INVALID_INPUT, invalid, 0);
        List<AssignedBand> assigned = new ArrayList<>(); List<PendingTransition> transitions = new ArrayList<>();
        List<SeamLevel> seams = new ArrayList<>();
        Map<Xz, ColumnLevel> columns = new LinkedHashMap<>(); Budget budget = new Budget(limits.maxOperations());
        long totalCut = 0, totalFill = 0;
        try {
            for (Loop loop : loops.stream().sorted(Comparator.comparingInt(Loop::componentId).thenComparingInt(Loop::loopId)).toList()) {
                List<Band> bands = canonical(loop.bands());
                Map<Integer, Seam> seamByBand = new HashMap<>(); loop.seams().forEach(seam -> seamByBand.put(seam.fromBand(), seam));
                List<Domain> domains = new ArrayList<>();
                for (Band band : bands) {
                    Domain domain = domain(band, limits);
                    if (domain == null) return failed(ProblemCode.NO_HEIGHT_PROFILE,
                            "A band or level gate pad cannot fit its bounded ground profile.", budget.used);
                    domains.add(domain);
                }
                int[] heights = solve(bands, domains, budget);
                if (heights == null) return failed(ProblemCode.NO_HEIGHT_PROFILE,
                        "No closed profile fits the local grading bounds and protected level joins.", budget.used);
                for (int i = 0; i < bands.size(); i++) {
                    Band band = bands.get(i); int y = heights[i];
                    assigned.add(new AssignedBand(loop.componentId(), loop.loopId(), band.id(), y, band.kind(), band.facing(), band.samples()));
                    for (Sample sample : band.samples().stream().sorted(Comparator.comparingInt(Sample::x).thenComparingInt(Sample::z)).toList()) {
                        Xz key = new Xz(sample.x(), sample.z());
                        if (columns.putIfAbsent(key, new ColumnLevel(sample, y)) == null) {
                            totalCut += Math.max(0, sample.surfaceY() - y);
                            totalFill += Math.max(0, y - sample.surfaceY());
                        }
                    }
                    int next = (i + 1) % bands.size();
                    Seam seam = seamByBand.get(band.id());
                    seams.add(new SeamLevel(loop.componentId(), loop.loopId(), seam, heights[i], heights[next]));
                    if (heights[i] != heights[next]) transitions.add(new PendingTransition(loop.componentId(), loop.loopId(),
                            band.id(), bands.get(next).id(), heights[i], heights[next], seam));
                }
            }
        } catch (WorkLimit exceeded) { return failed(ProblemCode.WORK_BUDGET, "The bounded height-profile search budget was exhausted.", budget.used); }
        if (totalCut > limits.maxCutCells() || totalFill > limits.maxFillCells()) return failed(ProblemCode.GRADING_BUDGET,
                "The selected minimum-grading profile exceeds its aggregate cut/fill budget; a new route is needed.", budget.used);
        return new Proposal(assigned, new ArrayList<>(columns.values()), transitions, seams, totalCut, totalFill, budget.used, List.of());
    }

    private record Xz(int x, int z) {}
    private record Seen(int component, Sample sample) {}
    private static String validate(List<Loop> loops, Limits limits) {
        if (loops == null || loops.isEmpty() || loops.size() > limits.maxBands()) return "Complete bounded walk loops are required.";
        int bands = 0, samples = 0; Set<Integer> bandIds = new HashSet<>(); Set<String> loopIds = new HashSet<>();
        Map<Xz, Seen> columns = new HashMap<>(); Map<Integer, Integer> outerCounts = new HashMap<>();
        Map<Integer, Set<Facing>> gates = new HashMap<>(); Set<Integer> components = new HashSet<>();
        for (Loop loop : loops) {
            if (loop == null || loop.componentId() < 0 || loop.componentId() >= 4096 || loop.loopId() < 0
                    || !loopIds.add(loop.componentId() + ":" + loop.loopId()) || loop.bands().size() < 4
                    || loop.seams().size() != loop.bands().size())
                return "Invalid or duplicated closed-loop identity.";
            components.add(loop.componentId());
            if (loop.outer()) outerCounts.merge(loop.componentId(), 1, Integer::sum);
            Map<Integer, Map<Xz, Sample>> bandColumns = new HashMap<>();
            for (Band band : loop.bands()) {
                if (++bands > limits.maxBands()) return "The height-profile band budget was exceeded.";
                if (band == null || band.id() < 0 || !bandIds.add(band.id()) || band.kind() == null || band.samples().isEmpty()
                        || band.length() < 3 || band.length() > 8 || band.kind() == Kind.CORNER && band.length() != 5
                        || band.kind() == Kind.GATE && (band.facing() == null || !loop.outer())
                        || band.kind() != Kind.GATE && band.facing() != null)
                    return "Invalid short band, corner, or outer gate.";
                if (band.kind() == Kind.GATE && !gates.computeIfAbsent(loop.componentId(), ignored -> new HashSet<>()).add(band.facing()))
                    return "A component repeats a cardinal gate.";
                boolean wall = false, inside = false, outside = false; Set<Xz> own = new HashSet<>();
                Map<Xz, Sample> inBand = new HashMap<>(); bandColumns.put(band.id(), inBand);
                for (Sample sample : band.samples()) {
                    if (++samples > limits.maxSamples()) return "The height-profile sample budget was exceeded.";
                    if (sample == null || sample.role() == null || Math.abs((long) sample.x()) >= WORLD_LIMIT
                            || Math.abs((long) sample.z()) >= WORLD_LIMIT || sample.surfaceY() < limits.minBaseY()
                            || sample.surfaceY() > limits.maxBaseY() || sample.role() == Role.OUTSIDE_GATE_PAD && sample.gradingAllowed()
                            || sample.role() != Role.WALL && band.kind() != Kind.GATE) return "Invalid or unauthorized profile sample.";
                    Xz key = new Xz(sample.x(), sample.z());
                    if (!own.add(key)) return "A band repeats a ground column.";
                    inBand.put(key, sample);
                    Seen prior = columns.putIfAbsent(key, new Seen(loop.componentId(), sample));
                    // Shared untouched pad columns pin both bands to the same existing height. Mutable overlap
                    // requires a separate merged landing group, which this isolated proposal does not invent.
                    if (prior != null && (prior.component() != loop.componentId() || prior.sample().gradingAllowed()
                            || sample.gradingAllowed() || prior.sample().surfaceY() != sample.surfaceY() || prior.sample().role() != sample.role()
                            || prior.sample().role() == Role.WALL || sample.role() == Role.WALL))
                        return "Overlapping mutable bands need one explicitly merged level landing group.";
                    wall |= sample.role() == Role.WALL; inside |= sample.role() == Role.INSIDE_GATE_PAD; outside |= sample.role() == Role.OUTSIDE_GATE_PAD;
                }
                if (!wall || band.kind() == Kind.GATE && (!inside || !outside)) return "A gate requires wall, inside-pad and untouched outside-pad samples.";
                if (!validFootprint(band)) return "A band needs its exact short five-wide footprint and complete adjacent 3-by-3 gate pads.";
            }
            Map<Integer, Seam> seams = new HashMap<>();
            for (Seam seam : loop.seams()) {
                if (seam == null || seam.direction() == null || seam.lanes().size() != 5
                        || seams.putIfAbsent(seam.fromBand(), seam) != null) return "Missing or duplicated five-wide seam metadata.";
            }
            for (int i = 0; i < loop.bands().size(); i++) {
                Band from = loop.bands().get(i), to = loop.bands().get((i + 1) % loop.bands().size());
                Seam seam = seams.get(from.id());
                if (seam == null || seam.toBand() != to.id() || !validSeam(seam, bandColumns.get(from.id()), bandColumns.get(to.id())))
                    return "The complete closed-loop seams must connect five adjacent wall lanes exactly.";
            }
        }
        for (int component : components) {
            if (outerCounts.getOrDefault(component, 0) != 1) return "Each component requires one explicitly identified outer loop.";
            if (limits.requireCardinalGates() && !gates.getOrDefault(component, Set.of()).equals(Set.of(Facing.values())))
                return "Each component requires north, east, south and west level gate pads.";
        }
        return null;
    }

    private record Box(int minX, int minZ, int maxX, int maxZ, int cells) {
        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
    }
    private static Box box(Band band, Role role) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, cells = 0;
        for (Sample sample : band.samples()) if (sample.role() == role) {
            minX = Math.min(minX, sample.x()); minZ = Math.min(minZ, sample.z());
            maxX = Math.max(maxX, sample.x()); maxZ = Math.max(maxZ, sample.z()); cells++;
        }
        return cells == 0 ? null : new Box(minX, minZ, maxX, maxZ, cells);
    }
    private static boolean validFootprint(Band band) {
        Box wall = box(band, Role.WALL);
        if (wall == null || wall.cells() != band.length() * 5
                || !(wall.width() == 5 && wall.depth() == band.length() || wall.width() == band.length() && wall.depth() == 5)) return false;
        if (band.kind() != Kind.GATE) return true;
        Box inside = box(band, Role.INSIDE_GATE_PAD), outside = box(band, Role.OUTSIDE_GATE_PAD);
        if (inside == null || outside == null || inside.cells() != 9 || outside.cells() != 9
                || inside.width() != 3 || inside.depth() != 3 || outside.width() != 3 || outside.depth() != 3) return false;
        if (band.facing() == Facing.NORTH || band.facing() == Facing.SOUTH) {
            if (wall.depth() != 5 || inside.minX() != outside.minX() || inside.minX() < wall.minX() || inside.maxX() > wall.maxX()) return false;
            return band.facing() == Facing.NORTH
                    ? outside.maxZ() == wall.minZ() - 1 && inside.minZ() == wall.maxZ() + 1
                    : outside.minZ() == wall.maxZ() + 1 && inside.maxZ() == wall.minZ() - 1;
        }
        if (wall.width() != 5 || inside.minZ() != outside.minZ() || inside.minZ() < wall.minZ() || inside.maxZ() > wall.maxZ()) return false;
        return band.facing() == Facing.EAST
                ? outside.minX() == wall.maxX() + 1 && inside.maxX() == wall.minX() - 1
                : outside.maxX() == wall.minX() - 1 && inside.minX() == wall.maxX() + 1;
    }

    private static boolean validSeam(Seam seam, Map<Xz, Sample> from, Map<Xz, Sample> to) {
        int dx = switch (seam.direction()) { case EAST -> 1; case WEST -> -1; default -> 0; };
        int dz = switch (seam.direction()) { case SOUTH -> 1; case NORTH -> -1; default -> 0; };
        Integer acrossX = null, acrossZ = null; Lane previous = null;
        for (Lane lane : seam.lanes()) {
            if (lane == null || (long) lane.toX() - lane.fromX() != dx || (long) lane.toZ() - lane.fromZ() != dz) return false;
            Sample a = from.get(new Xz(lane.fromX(), lane.fromZ())), b = to.get(new Xz(lane.toX(), lane.toZ()));
            if (a == null || b == null || a.role() != Role.WALL || b.role() != Role.WALL) return false;
            if (previous != null) {
                int x = lane.fromX() - previous.fromX(), z = lane.fromZ() - previous.fromZ();
                if (Math.abs(x) + Math.abs(z) != 1 || x * dx + z * dz != 0) return false;
                if (acrossX == null) { acrossX = x; acrossZ = z; }
                else if (acrossX != x || acrossZ != z) return false;
            }
            previous = lane;
        }
        return true;
    }

    private record Domain(int min, int max, long[] cost) {}
    private static Domain domain(Band band, Limits limits) {
        int min = limits.minBaseY(), max = limits.maxBaseY();
        for (Sample sample : band.samples()) {
            min = Math.max(min, sample.surfaceY() - (sample.gradingAllowed() ? limits.maxCut() : 0));
            max = Math.min(max, sample.surfaceY() + (sample.gradingAllowed() ? limits.maxFill() : 0));
        }
        if (min > max) return null;
        long[] cost = new long[max - min + 1];
        for (int y = min; y <= max; y++) for (Sample sample : band.samples()) cost[y - min] += Math.abs((long) y - sample.surfaceY());
        return new Domain(min, max, cost);
    }

    /** Exact dynamic program for each closed loop, minimizing grading volume, then number of pending transitions. */
    private static int[] solve(List<Band> bands, List<Domain> domains, Budget budget) {
        long bestCost = INF; int bestChanges = Integer.MAX_VALUE; int[] best = null; int n = bands.size();
        Domain first = domains.get(0);
        for (int firstY = first.min(); firstY <= first.max(); firstY++) {
            long[] costs = new long[first.cost().length]; Arrays.fill(costs, INF);
            int[] changes = new int[costs.length]; Arrays.fill(changes, Integer.MAX_VALUE);
            costs[firstY - first.min()] = first.cost()[firstY - first.min()]; changes[firstY - first.min()] = 0;
            int[][] parents = new int[n][];
            for (int i = 1; i < n; i++) {
                Domain previous = domains.get(i - 1), current = domains.get(i);
                long[] nextCosts = new long[current.cost().length]; Arrays.fill(nextCosts, INF);
                int[] nextChanges = new int[nextCosts.length]; Arrays.fill(nextChanges, Integer.MAX_VALUE);
                parents[i] = new int[nextCosts.length]; Arrays.fill(parents[i], Integer.MIN_VALUE);
                int step = stepAllowed(bands.get(i - 1), bands.get(i)) ? 1 : 0;
                for (int y = current.min(); y <= current.max(); y++) {
                    int at = y - current.min();
                    for (int previousY = Math.max(previous.min(), y - step); previousY <= Math.min(previous.max(), y + step); previousY++) {
                        budget.take(); int old = previousY - previous.min(); if (costs[old] == INF) continue;
                        long cost = costs[old] + current.cost()[at]; int changed = changes[old] + (previousY == y ? 0 : 1);
                        if (cost < nextCosts[at] || cost == nextCosts[at] && changed < nextChanges[at]) {
                            nextCosts[at] = cost; nextChanges[at] = changed; parents[i][at] = previousY;
                        }
                    }
                }
                costs = nextCosts; changes = nextChanges;
            }
            Domain last = domains.get(n - 1); int closingStep = stepAllowed(bands.get(n - 1), bands.get(0)) ? 1 : 0;
            for (int lastY = Math.max(last.min(), firstY - closingStep); lastY <= Math.min(last.max(), firstY + closingStep); lastY++) {
                budget.take(); int at = lastY - last.min(); if (costs[at] == INF) continue;
                int changed = changes[at] + (lastY == firstY ? 0 : 1);
                if (costs[at] < bestCost || costs[at] == bestCost && changed < bestChanges) {
                    int[] heights = new int[n]; heights[n - 1] = lastY;
                    for (int i = n - 1; i > 0; i--) heights[i - 1] = parents[i][heights[i] - domains.get(i).min()];
                    best = heights; bestCost = costs[at]; bestChanges = changed;
                }
            }
        }
        return best;
    }
    private static boolean stepAllowed(Band left, Band right) {
        return left.stepAfter() && left.kind() == Kind.STRAIGHT && right.kind() == Kind.STRAIGHT;
    }
    private static List<Band> canonical(List<Band> bands) {
        int start = 0;
        for (int i = 1; i < bands.size(); i++) if (bands.get(i).id() < bands.get(start).id()) start = i;
        List<Band> result = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) result.add(bands.get((start + i) % bands.size()));
        return List.copyOf(result);
    }
    private static final class Budget {
        final long max; long used;
        Budget(long max) { this.max = max; }
        void take() { if (used >= max) throw new WorkLimit(); used++; }
    }
    private static final class WorkLimit extends RuntimeException {}
    private static Proposal failed(ProblemCode code, String message, long used) {
        return new Proposal(List.of(), List.of(), List.of(), List.of(), 0, 0, used, List.of(new Problem(code, message)));
    }
}
