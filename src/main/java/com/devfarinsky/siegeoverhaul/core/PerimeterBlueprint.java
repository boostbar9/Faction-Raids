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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only, exact claim-boundary compiler for the five-wide manual wall style.
 *
 * <p>The footprint is the first five Chebyshev-distance layers inside the claim.
 * Layers one and five carry the body skins and parapets; layers two through four form the three-wide
 * walk above protected, unexcavated hollow space. Using diagonal neighbours is important: without them an inward corner
 * leaves a hole between two otherwise correct edge strips. Nothing is rounded
 * to the manual structures' global five-block grid.</p>
 *
 * <p>Each edge-connected claim component has one level deck. Uneven ground is
 * supported down to each verified surface, within a finite depth; unsafe relief
 * rejects the entire plan instead of leaving a gap or a jagged walking surface.
 * The caller still owns live block, entity, permission, storage and native-job
 * validation. This compiler never reads or writes the world.</p>
 */
public final class PerimeterBlueprint {
    public static final int WALL_WIDTH = 5;
    public static final int WALK_WIDTH = 3;
    public static final int DECK_OFFSET = 3;
    public static final int CLEAR_HEIGHT = 6;
    private static final int MAX_PROBLEMS = 32;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final List<Direction> HORIZONTAL = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final Comparator<ChunkPos> CHUNK_ORDER = Comparator
            .comparingInt((ChunkPos c) -> c.x).thenComparingInt(c -> c.z);
    private static final Comparator<Xz> COLUMN_ORDER = Comparator
            .comparingInt(Xz::x).thenComparingInt(Xz::z);

    private PerimeterBlueprint() {}

    /** Existing automatic choices, retaining the manual reference's oak deck. */
    public record Palette(String wall, String deck, String foundation) {
        public static final Palette COBBLESTONE = new Palette(
                "minecraft:cobblestone", "minecraft:oak_planks", "minecraft:dirt");
        public static final Palette STONE_BRICKS = new Palette(
                "minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:dirt");
        public static final Palette OAK = new Palette(
                "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:dirt");

        public Palette {
            if (wall == null || wall.isBlank() || deck == null || deck.isBlank()
                    || foundation == null || foundation.isBlank())
                throw new IllegalArgumentException("All perimeter materials are required");
        }
    }

    /** Finite planning caps; claim, boundary, footprint and horizontal-span caps precede surface reads. */
    public record Limits(int maxClaimChunks, int maxBoundaryLength, int maxFootprintColumns,
                         int maxBlocks, int maxFoundationDepth, int minBuildHeight,
                         int maxBuildHeight, int maxHorizontalSpan, long maxBoundsVolume) {
        public static final Limits DEFAULT = new Limits(
                4096, 4096, 20_480, 131_072, 8, -64, 320, 256, 1_048_576L);

        public Limits {
            if (maxClaimChunks < 1 || maxBoundaryLength < 1 || maxFootprintColumns < 1
                    || maxBlocks < 1 || maxFoundationDepth < 0 || maxFoundationDepth > 64
                    || minBuildHeight < -2048 || maxBuildHeight > 2048
                    || maxBuildHeight <= minBuildHeight || maxHorizontalSpan < 1 || maxBoundsVolume < 1)
                throw new IllegalArgumentException("Invalid perimeter limits");
        }
    }

    @FunctionalInterface
    public interface SurfaceResolver {
        /** First build Y above dry, safe, solid footing; never a column on water or a protected structure. */
        Surface resolve(int x, int z);
    }

    public record Surface(int baseY, String problem) {
        public static Surface ready(int baseY) { return new Surface(baseY, null); }
        public static Surface blocked(String reason) {
            return new Surface(0, reason == null || reason.isBlank() ? "Unsafe ground" : reason);
        }
    }

    public enum ProblemCode {
        EMPTY_CLAIM, CLAIM_LIMIT, COORDINATE_LIMIT, BOUNDARY_LIMIT, FOOTPRINT_LIMIT,
        BROKEN_JOIN, UNSAFE_SURFACE, FOUNDATION_LIMIT, HEIGHT_LIMIT, BLOCK_LIMIT, BOUNDS_LIMIT
    }

    /** Position is null for a whole-plan budget/input error, otherwise it identifies the blocked column. */
    public record Problem(ProblemCode code, BlockPos position, String message) {}

    public record Column(BlockPos base, int supportDepth, int inwardDistance, int componentId) {
        public BlockPos foundationBase() { return base.below(supportDepth); }
        public int deckY() { return base.getY() + DECK_OFFSET; }
        public boolean parapet() { return inwardDistance == 1 || inwardDistance == WALL_WIDTH; }
    }

    /** Corner-centre to corner-centre, inclusive; adjacent five-cell corner footprints are not stretched. */
    public record Run(int id, int componentId, BlockPos start, BlockPos end, Direction direction) {
        public int length() {
            return Math.abs(end.getX() - start.getX()) + Math.abs(end.getZ() - start.getZ()) + 1;
        }
        public int straightLength() { return length() - 6; }
        public int deckY() { return start.getY(); }
    }

    /** Deck cell in a corner's open face, facing the fitted straight span. Width is the clear walking width. */
    public record Connection(int runId, int componentId, BlockPos center, Direction facing, int width) {
        public int deckY() { return center.getY(); }
    }

    public record Plan(Map<Long, String> blocks, List<Column> columns, Set<Long> clearance,
                       BlockPos min, BlockPos max, List<Run> runs, List<Connection> connections,
                       Map<String, Integer> materialCounts, List<Problem> problems) {
        public Plan {
            blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
            columns = List.copyOf(columns);
            clearance = Collections.unmodifiableSet(new LinkedHashSet<>(clearance));
            runs = List.copyOf(runs);
            connections = List.copyOf(connections);
            materialCounts = Collections.unmodifiableMap(new LinkedHashMap<>(materialCounts));
            problems = List.copyOf(problems);
        }

        /** Invalid plans never expose a partial buildable wall. Bounds are null for invalid plans. */
        public boolean valid() { return problems.isEmpty() && !blocks.isEmpty(); }
        public List<BlockPos> footprint() { return columns.stream().map(Column::base).toList(); }
        public String problemSummary() { return problems.isEmpty() ? "" : problems.get(0).message(); }
    }

    public static Plan create(Set<ChunkPos> chunks, SurfaceResolver surfaces, Palette palette) {
        return create(chunks, surfaces, palette, Limits.DEFAULT);
    }

    public static Plan create(Set<ChunkPos> chunks, SurfaceResolver surfaces, Palette palette, Limits limits) {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(limits, "limits");
        if (chunks.isEmpty()) return failed(ProblemCode.EMPTY_CLAIM, "Your claim has no chunks to fortify.");
        if (chunks.size() > limits.maxClaimChunks())
            return failed(ProblemCode.CLAIM_LIMIT, "The claim is too large to plan in one bounded job.");
        if (chunks.stream().anyMatch(Objects::isNull))
            return failed(ProblemCode.COORDINATE_LIMIT, "The claim contains an invalid chunk.");
        Set<ChunkPos> claim = Set.copyOf(chunks);
        List<ChunkPos> ordered = claim.stream().sorted(CHUNK_ORDER).toList();
        long boundaryLength = 0;
        long claimMinX = Long.MAX_VALUE, claimMinZ = Long.MAX_VALUE;
        long claimMaxX = Long.MIN_VALUE, claimMaxZ = Long.MIN_VALUE;
        for (ChunkPos chunk : ordered) {
            long x = (long) chunk.x * 16, z = (long) chunk.z * 16;
            if (x < -WORLD_LIMIT || z < -WORLD_LIMIT || x + 15 >= WORLD_LIMIT || z + 15 >= WORLD_LIMIT)
                return failed(ProblemCode.COORDINATE_LIMIT, "The claim exceeds supported world coordinates.");
            claimMinX = Math.min(claimMinX, x); claimMaxX = Math.max(claimMaxX, x + 15);
            claimMinZ = Math.min(claimMinZ, z); claimMaxZ = Math.max(claimMaxZ, z + 15);
            for (Direction direction : HORIZONTAL)
                if (!claim.contains(neighbour(chunk, direction))) boundaryLength += 16;
            if (boundaryLength > limits.maxBoundaryLength())
                return failed(ProblemCode.BOUNDARY_LIMIT, "The complete perimeter is too long for one job.");
        }
        if (claimMaxX - claimMinX + 1 > limits.maxHorizontalSpan()
                || claimMaxZ - claimMinZ + 1 > limits.maxHorizontalSpan())
            return failed(ProblemCode.BOUNDS_LIMIT, "The complete perimeter is too spread out for one native construction area.");

        Map<ChunkPos, Integer> components = components(claim, ordered);
        Map<Xz, HorizontalColumn> horizontal = new HashMap<>();
        for (ChunkPos chunk : ordered) {
            boolean[][] absent = new boolean[3][3];
            boolean touchesBoundary = false;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                absent[dx + 1][dz + 1] = !claim.contains(new ChunkPos(chunk.x + dx, chunk.z + dz));
                touchesBoundary |= absent[dx + 1][dz + 1];
            }
            if (!touchesBoundary) continue;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int distance = distanceInside(x, z, absent);
                if (distance > WALL_WIDTH) continue;
                Xz pos = new Xz(chunk.x * 16 + x, chunk.z * 16 + z);
                horizontal.put(pos, new HorizontalColumn(pos, distance, components.get(chunk)));
                if (horizontal.size() > limits.maxFootprintColumns())
                    return failed(ProblemCode.FOOTPRINT_LIMIT, "The complete five-wide footprint exceeds the job limit.");
            }
        }
        List<HorizontalColumn> fitted = horizontal.values().stream()
                .sorted(Comparator.comparing(HorizontalColumn::position, COLUMN_ORDER)).toList();
        List<Problem> problems = new ArrayList<>();
        List<HorizontalRun> horizontalRuns = findRuns(horizontal, problems);
        if (!problems.isEmpty()) return failed(problems);

        Map<Xz, Integer> surfaceY = new HashMap<>();
        Map<Integer, Integer> levelY = new HashMap<>();
        for (HorizontalColumn column : fitted) {
            Xz pos = column.position();
            Surface surface;
            try {
                surface = surfaces.resolve(pos.x(), pos.z());
            } catch (RuntimeException unavailable) {
                surface = Surface.blocked("The surface could not be verified; reload the terrain and preview again.");
            }
            if (surface == null || surface.problem() != null) {
                problems.add(new Problem(ProblemCode.UNSAFE_SURFACE, pos.at(0),
                        surface == null ? "The whole wall needs verified dry, safe ground." : surface.problem()));
            } else if (surface.baseY() <= limits.minBuildHeight()
                    || (long) surface.baseY() + CLEAR_HEIGHT - 1 >= limits.maxBuildHeight()) {
                problems.add(new Problem(ProblemCode.HEIGHT_LIMIT, pos.at(0),
                        "The wall or its headroom exceeds the world's build height."));
            } else {
                surfaceY.put(pos, surface.baseY());
                levelY.merge(column.componentId(), surface.baseY(), Math::max);
            }
            if (problems.size() >= MAX_PROBLEMS) return failed(problems);
        }
        if (!problems.isEmpty()) return failed(problems);

        List<Column> columns = new ArrayList<>(fitted.size());
        long blockCount = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (HorizontalColumn column : fitted) {
            Xz pos = column.position();
            int baseY = levelY.get(column.componentId());
            int support = baseY - surfaceY.get(pos);
            if (support > limits.maxFoundationDepth()) {
                problems.add(new Problem(ProblemCode.FOUNDATION_LIMIT, pos.at(surfaceY.get(pos)),
                        "A level wall needs more than " + limits.maxFoundationDepth()
                                + " support blocks here. Level the ground before commissioning."));
                if (problems.size() >= MAX_PROBLEMS) return failed(problems);
            }
            Column result = new Column(pos.at(baseY), support, column.distance(), column.componentId());
            columns.add(result);
            blockCount += support + 1L + (result.parapet() ? DECK_OFFSET + 1 : 0);
            minX = Math.min(minX, pos.x()); maxX = Math.max(maxX, pos.x());
            minZ = Math.min(minZ, pos.z()); maxZ = Math.max(maxZ, pos.z());
            minY = Math.min(minY, surfaceY.get(pos)); maxY = Math.max(maxY, baseY + CLEAR_HEIGHT - 1);
        }
        if (!problems.isEmpty()) return failed(problems);
        if (blockCount > limits.maxBlocks())
            return failed(ProblemCode.BLOCK_LIMIT, "The full wall and its foundations exceed the block budget.");
        long width = (long) maxX - minX + 1, depth = (long) maxZ - minZ + 1, height = (long) maxY - minY + 1;
        if (width > limits.maxHorizontalSpan() || depth > limits.maxHorizontalSpan()
                || width > limits.maxBoundsVolume() / depth / height)
            return failed(ProblemCode.BOUNDS_LIMIT, "The complete perimeter is too spread out for one native construction area.");

        Map<Long, String> blocks = new LinkedHashMap<>();
        Set<Long> clearance = new LinkedHashSet<>();
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (Column column : columns) {
            for (int y = -column.supportDepth(); y < CLEAR_HEIGHT; y++) {
                String material = y < 0 ? palette.foundation()
                        : y < DECK_OFFSET ? (column.parapet() ? palette.wall() : null)
                        : y == DECK_OFFSET ? palette.deck()
                        : y == DECK_OFFSET + 1 && column.parapet() ? palette.wall() : null;
                long position = column.base().above(y).asLong();
                if (material == null) clearance.add(position);
                else {
                    blocks.put(position, material);
                    materials.merge(material, 1, Integer::sum);
                }
            }
        }
        List<Run> runs = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        for (HorizontalRun horizontalRun : horizontalRuns) {
            int deckY = levelY.get(horizontalRun.componentId()) + DECK_OFFSET;
            Run run = new Run(runs.size(), horizontalRun.componentId(), horizontalRun.start().at(deckY),
                    horizontalRun.end().at(deckY), horizontalRun.direction());
            runs.add(run);
            connections.add(new Connection(run.id(), run.componentId(),
                    run.start().relative(run.direction(), 2), run.direction(), WALK_WIDTH));
            connections.add(new Connection(run.id(), run.componentId(),
                    run.end().relative(run.direction().getOpposite(), 2), run.direction().getOpposite(), WALK_WIDTH));
        }
        return new Plan(blocks, columns, clearance, new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ), runs, connections, materials, List.of());
    }

    private record Xz(int x, int z) {
        Xz relative(Direction direction) { return new Xz(x + direction.getStepX(), z + direction.getStepZ()); }
        BlockPos at(int y) { return new BlockPos(x, y, z); }
    }
    private record HorizontalColumn(Xz position, int distance, int componentId) {}
    private record HorizontalRun(Xz start, Xz end, Direction direction, int componentId) {}

    private static int distanceInside(int x, int z, boolean[][] absent) {
        int distance = 16;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!absent[dx + 1][dz + 1]) continue;
            int gapX = dx < 0 ? x + 1 : dx > 0 ? 16 - x : 0;
            int gapZ = dz < 0 ? z + 1 : dz > 0 ? 16 - z : 0;
            distance = Math.min(distance, Math.max(gapX, gapZ));
        }
        return distance;
    }

    private static Map<ChunkPos, Integer> components(Set<ChunkPos> claim, List<ChunkPos> ordered) {
        Map<ChunkPos, Integer> result = new HashMap<>();
        int nextId = 0;
        for (ChunkPos start : ordered) {
            if (result.containsKey(start)) continue;
            ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
            result.put(start, nextId);
            queue.add(start);
            while (!queue.isEmpty()) {
                ChunkPos current = queue.removeFirst();
                for (Direction direction : HORIZONTAL) {
                    ChunkPos next = neighbour(current, direction);
                    if (claim.contains(next) && result.putIfAbsent(next, nextId) == null) queue.addLast(next);
                }
            }
            nextId++;
        }
        return result;
    }

    private static ChunkPos neighbour(ChunkPos chunk, Direction direction) {
        return new ChunkPos(chunk.x + direction.getStepX(), chunk.z + direction.getStepZ());
    }

    /** Prove the three-wide walk's centreline has closed, unbranched orthogonal joins. */
    private static List<HorizontalRun> findRuns(Map<Xz, HorizontalColumn> horizontal, List<Problem> problems) {
        Set<Xz> centers = new HashSet<>();
        for (HorizontalColumn column : horizontal.values()) if (column.distance() == 3) centers.add(column.position());
        List<Xz> bends = new ArrayList<>();
        for (Xz cell : centers.stream().sorted(COLUMN_ORDER).toList()) {
            List<Direction> neighbours = HORIZONTAL.stream().filter(d -> centers.contains(cell.relative(d))).toList();
            if (neighbours.size() != 2) {
                problems.add(new Problem(ProblemCode.BROKEN_JOIN, cell.at(0),
                        "The claim has a wall join that cannot retain a continuous three-wide walk."));
                if (problems.size() >= MAX_PROBLEMS) return List.of();
            } else if (neighbours.get(0).getOpposite() != neighbours.get(1)) bends.add(cell);
        }
        if (!problems.isEmpty()) return List.of();
        Set<Xz> bendSet = new HashSet<>(bends);
        List<HorizontalRun> runs = new ArrayList<>();
        for (Xz start : bends) for (Direction direction : List.of(Direction.EAST, Direction.SOUTH)) {
            if (!centers.contains(start.relative(direction))) continue;
            Xz end = start.relative(direction);
            int distance = 1;
            while (!bendSet.contains(end) && centers.contains(end) && distance <= centers.size()) {
                end = end.relative(direction);
                distance++;
            }
            if (!bendSet.contains(end) || distance < 5) {
                problems.add(new Problem(ProblemCode.BROKEN_JOIN, start.at(0),
                        "Adjacent wall corners do not have room for their complete five-wide footprints."));
                return List.of();
            }
            runs.add(new HorizontalRun(start, end, direction, horizontal.get(start).componentId()));
        }
        if (runs.isEmpty()) problems.add(new Problem(ProblemCode.BROKEN_JOIN, null, "No closed wall walk could be fitted."));
        return runs;
    }

    private static Plan failed(ProblemCode code, String message) {
        return failed(List.of(new Problem(code, null, message)));
    }

    private static Plan failed(List<Problem> problems) {
        return new Plan(Map.of(), List.of(), Set.of(), null, null, List.of(), List.of(), Map.of(), problems);
    }
}
