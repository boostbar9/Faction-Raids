package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

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
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable selected entrances and read-only observations for a newly reviewed wall.
 * These observations are global reservations, never native targets or native clearance.
 * This class performs no world reads and grants no permission to activate construction.
 */
public final class PerimeterGateContract {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_GATES = 1024, MAX_OBSERVATIONS = PerimeterStageLayout.MAX_RESERVED;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final List<Direction> CARDINALS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final Comparator<Gate> GATE_ORDER = Comparator.comparingInt(Gate::componentId)
            .thenComparingInt(g -> CARDINALS.indexOf(g.facing()));

    /** Exact chosen outer-skin center at passage feet height; no candidate selection on reload. */
    public record Gate(int componentId, Direction facing, BlockPos outerCenter) {
        public Gate {
            if (componentId < 0 || componentId >= MAX_GATES / 4 || facing == null || !CARDINALS.contains(facing) || outerCenter == null)
                throw invalid("Invalid gate descriptor");
            if (Math.abs((long) outerCenter.getX()) >= WORLD_LIMIT || Math.abs((long) outerCenter.getZ()) >= WORLD_LIMIT)
                throw invalid("Unsupported gate center coordinate");
            validPosition(outerCenter.asLong());
            if (outerCenter.getY() < -2047 || outerCenter.getY() > 2042) throw invalid("Unsupported gate elevation");
            outerCenter = outerCenter.immutable();
        }
    }

    private final List<Gate> gates;
    private final Map<Long, BlockState> observations;
    private final Set<Long> air, floor;
    private final Geometry geometry;
    private final String originalWallDigest, digest;

    private PerimeterGateContract(List<Gate> gates, Set<Long> air, Set<Long> floor,
                                  Map<Long, BlockState> observations, String originalWallDigest) {
        if (gates == null || gates.isEmpty() || gates.size() > MAX_GATES || gates.size() % 4 != 0
                || observations == null || observations.isEmpty() || observations.size() > MAX_OBSERVATIONS
                || air == null || floor == null || !PerimeterProject.digest(originalWallDigest))
            throw invalid("Invalid bounded gate contract");
        if (gates.stream().anyMatch(java.util.Objects::isNull) || observations.keySet().stream().anyMatch(java.util.Objects::isNull))
            throw invalid("Missing gate or observation position");
        this.gates = gates.stream().sorted(GATE_ORDER).toList();
        for (int i = 0; i < this.gates.size(); i++) {
            Gate gate = this.gates.get(i);
            if (gate.componentId() != i / 4 || gate.facing() != CARDINALS.get(i % 4))
                throw invalid("Each component requires exactly four different cardinal gates");
        }
        this.geometry = geometry(this.gates);
        this.air = freeze(air); this.floor = freeze(floor);
        if (!this.air.equals(geometry.air()) || !this.floor.containsAll(geometry.approachFloor())
                || !Collections.disjoint(this.air, this.floor)) throw invalid("Changed or overlapping observation roles");
        Set<Long> extraFloor = new HashSet<>(this.floor); extraFloor.removeAll(geometry.approachFloor());
        if (!geometry.passageFloor().containsAll(extraFloor)) throw invalid("Unrelated passage floor observation");
        Set<Long> cells = new HashSet<>(this.air); cells.addAll(this.floor);
        if (!cells.equals(observations.keySet())) throw invalid("Incomplete gate observations");
        Map<Long, BlockState> copy = new LinkedHashMap<>();
        observations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() == null || entry.getValue() == null) throw invalid("Missing gate observation state");
            validPosition(entry.getKey());
            // Exact states, including every property, must survive this codec losslessly.
            readState(NbtUtils.writeBlockState(entry.getValue()));
            copy.put(entry.getKey(), entry.getValue());
        });
        this.observations = Collections.unmodifiableMap(copy);
        this.originalWallDigest = originalWallDigest;
        this.digest = calculateDigest();
    }

    public List<Gate> gates() { return gates; }
    public Map<Long, BlockState> observations() { return observations; }
    public Set<Long> airObservations() { return air; }
    public Set<Long> floorObservations() { return floor; }
    public String digest() { return digest; }

    /** Does not trust the public Layout/Gate records: all supplied cell sets must match their descriptors. */
    public static PerimeterGateContract create(Set<ChunkPos> territory, PerimeterBlueprint.Plan originalWall,
                                               PerimeterGateLayout.Layout layout, Map<Long, BlockState> observationBefore) {
        if (layout == null || !layout.valid() || layout.gates().size() > MAX_GATES) throw invalid("Complete gate selection required");
        List<Gate> descriptors = new ArrayList<>();
        for (var supplied : layout.gates()) {
            if (supplied == null) throw invalid("Missing gate descriptor");
            Gate descriptor = new Gate(supplied.componentId(), supplied.facing(), supplied.outerCenter());
            Geometry exact = geometry(List.of(descriptor));
            if (!supplied.passage().equals(exact.passage()) || !supplied.insideApproach().equals(exact.inside())
                    || !supplied.outsideApproach().equals(exact.outside()) || !supplied.approachFooting().equals(exact.approachFloor()))
                throw invalid("Forged gate cell geometry");
            descriptors.add(descriptor);
        }
        Geometry exact = geometry(descriptors);
        if (!layout.wallOpenings().equals(exact.openings()) || !layout.passageClearance().equals(exact.passage())
                || !layout.approachClearance().equals(exact.air()) || !layout.approachFooting().equals(exact.approachFloor()))
            throw invalid("Forged aggregate gate geometry");
        Wall checked = validateWall(territory, originalWall, Set.of());
        validateGates(descriptors, exact, checked);
        Set<Long> floors = expectedFloors(exact, checked.columns());
        var result = new PerimeterGateContract(descriptors, exact.air(), floors, observationBefore, wallDigest(originalWall));
        result.validateAgainst(territory, result.applyOpenings(originalWall));
        return result;
    }

    /** Omits only newly planned skins. It never requests demolition or changes approach cells. */
    public PerimeterBlueprint.Plan applyOpenings(PerimeterBlueprint.Plan originalWall) {
        if (originalWall == null || !originalWallDigest.equals(wallDigest(originalWall)))
            throw invalid("Gate contract does not match the original reviewed wall");
        Map<Long, String> blocks = new LinkedHashMap<>(originalWall.blocks());
        Set<Long> clearance = new LinkedHashSet<>(originalWall.clearance());
        for (long cell : geometry.openings()) {
            if (blocks.remove(cell) == null || !clearance.add(cell)) throw invalid("Missing original gate skin");
        }
        return copyPlan(originalWall, blocks, clearance);
    }

    /** Exact saved-column/territory validation; runs may be empty and no wall is recompiled. */
    public void validateAgainst(Set<ChunkPos> territory, PerimeterBlueprint.Plan gatedWall) {
        Wall checked = validateWall(territory, gatedWall, geometry.openings());
        validateGates(gates, geometry, checked);
        if (!floor.equals(expectedFloors(geometry, checked.columns()))) throw invalid("Missing or unexpected natural passage floor");
        Set<Long> nativeCells = new HashSet<>(gatedWall.blocks().keySet()); nativeCells.addAll(gatedWall.clearance());
        if (!Collections.disjoint(nativeCells, observations.keySet())
                || (long) nativeCells.size() + observations.size() > PerimeterStageLayout.MAX_RESERVED)
            throw invalid("Observations overlap native cells or exceed the global reservation budget");
        Map<Long, String> original = new LinkedHashMap<>(gatedWall.blocks());
        geometry.openings().forEach(cell -> original.put(cell, checked.skin()));
        Set<Long> originalClearance = new LinkedHashSet<>(gatedWall.clearance()); originalClearance.removeAll(geometry.openings());
        if (!originalWallDigest.equals(wallDigest(copyPlan(gatedWall, original, originalClearance))))
            throw invalid("Changed reviewed wall behind gate contract");
    }

    public CompoundTag save() {
        CompoundTag out = new CompoundTag(); out.putInt("Version", FORMAT_VERSION);
        out.putString("Wall", originalWallDigest); out.putString("Digest", digest);
        ListTag selected = new ListTag();
        for (Gate gate : gates) {
            CompoundTag item = new CompoundTag(); item.putInt("Component", gate.componentId());
            item.putString("Facing", gate.facing().getName()); item.putLong("Center", gate.outerCenter().asLong()); selected.add(item);
        }
        out.put("Gates", selected);
        ListTag palette = new ListTag(), states = new ListTag(); Map<BlockState, Integer> ids = new HashMap<>();
        observations.forEach((cell, state) -> {
            Integer index = ids.get(state);
            if (index == null) { index = palette.size(); ids.put(state, index); palette.add(NbtUtils.writeBlockState(state)); }
            CompoundTag item = new CompoundTag(); item.putLong("Pos", cell); item.putInt("Before", index);
            item.putInt("Role", air.contains(cell) ? 0 : 1); states.add(item);
        });
        out.put("Palette", palette); out.put("Observations", states); return out;
    }

    /** Cheap budget preflight for the enclosing SavedData store, before state/geometry expansion. */
    public static int encodedObservationCount(CompoundTag tag) {
        keys(tag, Set.of("Version", "Wall", "Digest", "Gates", "Palette", "Observations"));
        if (integer(tag, "Version") != FORMAT_VERSION) throw invalid("Unknown gate contract version");
        list(tag, "Gates", MAX_GATES); list(tag, "Palette", MAX_OBSERVATIONS);
        return list(tag, "Observations", MAX_OBSERVATIONS).size();
    }

    public static PerimeterGateContract load(CompoundTag tag) {
        encodedObservationCount(tag);
        String wall = hash(tag, "Wall"), expectedDigest = hash(tag, "Digest");
        List<Gate> selected = new ArrayList<>();
        for (Tag raw : list(tag, "Gates", MAX_GATES)) {
            CompoundTag item = (CompoundTag) raw; keys(item, Set.of("Component", "Facing", "Center"));
            String facing = string(item, "Facing", 5);
            Direction direction = CARDINALS.stream().filter(d -> d.getName().equals(facing)).findFirst()
                    .orElseThrow(() -> invalid("Unknown gate facing"));
            selected.add(new Gate(integer(item, "Component"), direction, BlockPos.of(number(item, "Center"))));
        }
        List<BlockState> palette = new ArrayList<>(); Set<BlockState> unique = new HashSet<>();
        for (Tag raw : list(tag, "Palette", MAX_OBSERVATIONS)) {
            BlockState state = readState((CompoundTag) raw);
            if (!unique.add(state)) throw invalid("Duplicate gate observation palette entry");
            palette.add(state);
        }
        Map<Long, BlockState> observations = new LinkedHashMap<>(); Set<Long> air = new HashSet<>(), floor = new HashSet<>();
        Set<Integer> used = new HashSet<>();
        for (Tag raw : list(tag, "Observations", MAX_OBSERVATIONS)) {
            CompoundTag item = (CompoundTag) raw; keys(item, Set.of("Pos", "Before", "Role"));
            long cell = number(item, "Pos"); validPosition(cell);
            int index = integer(item, "Before"), role = integer(item, "Role");
            if (index < 0 || index >= palette.size() || role < 0 || role > 1
                    || observations.putIfAbsent(cell, palette.get(index)) != null) throw invalid("Invalid or duplicate gate observation");
            used.add(index); (role == 0 ? air : floor).add(cell);
        }
        if (used.size() != palette.size()) throw invalid("Unused gate observation palette entry");
        var result = new PerimeterGateContract(selected, air, floor, observations, wall);
        if (!result.digest().equals(expectedDigest)) throw invalid("Changed gate contract digest");
        return result;
    }

    private record Geometry(Set<Long> passage, Set<Long> inside, Set<Long> outside, Set<Long> air,
                            Set<Long> approachFloor, Set<Long> passageFloor, Set<Long> openings) {}
    private static Geometry geometry(List<Gate> gates) {
        if (gates == null || gates.isEmpty() || gates.size() > MAX_GATES) throw invalid("Invalid gate geometry count");
        Set<Long> passage = new HashSet<>(), inside = new HashSet<>(), outside = new HashSet<>();
        Set<Long> approachFloor = new HashSet<>(), passageFloor = new HashSet<>(), openings = new HashSet<>();
        for (Gate gate : gates) {
            if (gate == null) throw invalid("Missing gate");
            for (int across = -1; across <= 1; across++) for (int depth = -7; depth <= 3; depth++) {
                BlockPos feet = gate.outerCenter().relative(gate.facing().getClockWise(), across).relative(gate.facing(), depth);
                validPosition(feet.asLong()); validPosition(feet.below().asLong()); validPosition(feet.above(2).asLong());
                if (depth >= -4 && depth <= 0) {
                    passageFloor.add(feet.below().asLong());
                    for (int y = 0; y < 3; y++) {
                        long cell = feet.above(y).asLong();
                        if (!passage.add(cell)) throw invalid("Overlapping gate passages");
                        if (depth == -4 || depth == 0) openings.add(cell);
                    }
                } else {
                    approachFloor.add(feet.below().asLong());
                    for (int y = 0; y < 3; y++) (depth < -4 ? inside : outside).add(feet.above(y).asLong());
                }
            }
        }
        Set<Long> air = new HashSet<>(inside); air.addAll(outside);
        Set<Long> allFloors = new HashSet<>(approachFloor); allFloors.addAll(passageFloor);
        if (!Collections.disjoint(passage, air) || !Collections.disjoint(passage, allFloors)
                || !Collections.disjoint(air, allFloors) || !Collections.disjoint(approachFloor, passageFloor)
                || (long) passage.size() + air.size() + allFloors.size() > PerimeterStageLayout.MAX_RESERVED)
            throw invalid("Overlapping roles or excessive gate geometry");
        return new Geometry(freeze(passage), freeze(inside), freeze(outside), freeze(air),
                freeze(approachFloor), freeze(passageFloor), freeze(openings));
    }

    private record Wall(Map<Long, PerimeterBlueprint.Column> columns, Map<ChunkPos, Integer> components,
                        Set<ChunkPos> exterior, String skin) {}
    private static Wall validateWall(Set<ChunkPos> territory, PerimeterBlueprint.Plan wall, Set<Long> openings) {
        if (territory == null || territory.isEmpty() || territory.size() > PerimeterTerritory.MAX_CHUNKS
                || wall == null || !wall.valid() || wall.columns().isEmpty() || wall.columns().size() > PerimeterStageLayout.MAX_COLUMNS
                || wall.blocks().size() > PerimeterStageLayout.MAX_TARGETS
                || (long) wall.blocks().size() + wall.clearance().size() > PerimeterStageLayout.MAX_RESERVED)
            throw invalid("A complete bounded wall and territory are required");
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos chunk : territory) {
            if (chunk == null || (long) chunk.x * 16 < -WORLD_LIMIT || (long) chunk.z * 16 < -WORLD_LIMIT
                    || (long) chunk.x * 16 + 15 >= WORLD_LIMIT || (long) chunk.z * 16 + 15 >= WORLD_LIMIT)
                throw invalid("Unsupported gate territory coordinate");
            minX = Math.min(minX, chunk.x * 16); minZ = Math.min(minZ, chunk.z * 16);
            maxX = Math.max(maxX, chunk.x * 16 + 15); maxZ = Math.max(maxZ, chunk.z * 16 + 15);
        }
        if ((long) maxX - minX + 1 > 256 || (long) maxZ - minZ + 1 > 256) throw invalid("Excessive gate territory extent");
        Map<ChunkPos, Integer> components = components(territory);
        Map<Long, PerimeterBlueprint.Column> columns = new HashMap<>(); Map<Integer, Integer> heights = new HashMap<>();
        for (var column : wall.columns()) {
            if (column == null || column.base() == null || column.supportDepth() < 0 || column.supportDepth() > 64
                    || column.inwardDistance() < 1 || column.inwardDistance() > 5
                    || !Integer.valueOf(column.componentId()).equals(components.get(new ChunkPos(column.base())))
                    || Math.abs((long) column.base().getX()) >= WORLD_LIMIT || Math.abs((long) column.base().getZ()) >= WORLD_LIMIT
                    || (long) column.base().getY() - column.supportDepth() < -2048 || (long) column.base().getY() + 5 > 2047
                    || columns.putIfAbsent(xz(column.base()), column) != null) throw invalid("Invalid saved gate wall column");
            Integer prior = heights.putIfAbsent(column.componentId(), column.base().getY());
            if (prior != null && prior != column.base().getY()) throw invalid("Uneven component passage elevation");
        }
        // Validate the complete five-layer footprint, including corner distance, directly from territory.
        int expectedColumns = 0;
        for (ChunkPos chunk : territory) {
            List<ChunkPos> absent = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (!territory.contains(new ChunkPos(chunk.x + dx, chunk.z + dz))) absent.add(new ChunkPos(dx, dz));
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int inward = 16;
                for (ChunkPos offset : absent) {
                    int gapX = offset.x < 0 ? x + 1 : offset.x > 0 ? 16 - x : 0;
                    int gapZ = offset.z < 0 ? z + 1 : offset.z > 0 ? 16 - z : 0;
                    inward = Math.min(inward, Math.max(gapX, gapZ));
                }
                if (inward > 5) continue;
                expectedColumns++;
                var column = columns.get(new BlockPos(chunk.x * 16 + x, 0, chunk.z * 16 + z).asLong());
                if (column == null || column.inwardDistance() != inward) throw invalid("Wall does not exactly cover the reviewed claim boundary");
            }
        }
        if (columns.size() != expectedColumns) throw invalid("Extra wall footprint columns");
        String skin = null;
        Map<Long, String> expectedTargets = new HashMap<>(); Set<Long> expectedClearance = new HashSet<>();
        for (var column : columns.values()) {
            if (column.inwardDistance() == 3) {
                int neighbours = 0;
                for (Direction d : CARDINALS) {
                    var next = columns.get(xz(column.base().relative(d)));
                    if (next != null && next.inwardDistance() == 3) neighbours++;
                }
                if (neighbours != 2) throw invalid("Broken or branched wall centerline");
            }
            if (column.parapet()) {
                String material = wall.blocks().get(column.base().above(4).asLong());
                if (material == null || !Set.of("minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:oak_planks").contains(material)
                        || skin != null && !skin.equals(material)) throw invalid("Changed wall skin palette");
                skin = material;
            }
        }
        if (skin == null) throw invalid("Missing wall skins");
        Map<String, Integer> counts = new HashMap<>();
        for (var column : columns.values()) for (int y = -column.supportDepth(); y < 6; y++) {
            long cell = column.base().above(y).asLong();
            String material = y < 0 ? "minecraft:dirt" : y == 3 ? "minecraft:oak_planks"
                    : column.parapet() && (y < 3 || y == 4) ? skin : null;
            if (openings.contains(cell)) {
                if (!column.parapet() || y < 0 || y > 2) throw invalid("Opening removes a protected wall target");
                material = null;
            }
            if (material == null) expectedClearance.add(cell);
            else { expectedTargets.put(cell, material); counts.merge(material, 1, Integer::sum); }
            if ((long) expectedTargets.size() + expectedClearance.size() > PerimeterStageLayout.MAX_RESERVED)
                throw invalid("Excessive complete wall geometry");
        }
        if (!expectedTargets.equals(wall.blocks()) || !expectedClearance.equals(wall.clearance())
                || !counts.equals(wall.materialCounts()) || !expectedClearance.containsAll(openings))
            throw invalid("Wall targets or clearance do not match exact gate geometry");
        Set<Long> reserved = new HashSet<>(expectedTargets.keySet()); reserved.addAll(expectedClearance);
        BlockPos min = bounds(reserved, false), max = bounds(reserved, true);
        if (!min.equals(wall.min()) || !max.equals(wall.max()) || (long) max.getY() - min.getY() + 1 > 384
                || (long) (max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1) * (max.getY() - min.getY() + 1)
                > PerimeterStageLayout.MAX_SCAN_VOLUME) throw invalid("Changed gate wall bounds");
        return new Wall(columns, components, exterior(territory), skin);
    }

    private static void validateGates(List<Gate> gates, Geometry geometry, Wall wall) {
        int componentCount = new HashSet<>(wall.components().values()).size();
        if (gates.size() != componentCount * 4) throw invalid("Missing gates for a true claim component");
        Set<String> ids = new HashSet<>();
        for (Gate gate : gates) {
            if (gate.componentId() >= componentCount || !ids.add(gate.componentId() + ":" + gate.facing()))
                throw invalid("Duplicate or unknown cardinal gate");
            BlockPos center = gate.outerCenter().relative(gate.facing(), -2);
            Direction tangent = gate.facing().getClockWise();
            // Four cells to each side retain the entire five-wide corner footprint, without saved runs.
            for (int offset = -4; offset <= 4; offset++) {
                var column = wall.columns().get(xz(center.relative(tangent, offset)));
                if (column == null || column.inwardDistance() != 3 || column.componentId() != gate.componentId()
                        || column.base().getY() != center.getY()) throw invalid("Gate cuts a corner or leaves its straight wall run");
            }
            for (int across = -1; across <= 1; across++) for (int depth = -7; depth <= 3; depth++) {
                BlockPos feet = gate.outerCenter().relative(tangent, across).relative(gate.facing(), depth);
                var column = wall.columns().get(xz(feet)); ChunkPos chunk = new ChunkPos(feet);
                if (depth >= -4 && depth <= 0) {
                    if (column == null || column.componentId() != gate.componentId() || column.inwardDistance() != 1 - depth
                            || column.base().getY() != feet.getY()) throw invalid("Gate does not cross the exact five-wide wall");
                } else if (column != null || (depth < -4
                        ? !Integer.valueOf(gate.componentId()).equals(wall.components().get(chunk))
                        : wall.components().containsKey(chunk) || !wall.exterior().contains(chunk)))
                    throw invalid("Gate approach enters another wall, component, or enclosed hole");
            }
        }
        if (geometry.openings().size() != gates.size() * 18) throw invalid("Invalid gate opening count");
    }

    private static Set<Long> expectedFloors(Geometry geometry, Map<Long, PerimeterBlueprint.Column> columns) {
        Set<Long> result = new HashSet<>(geometry.approachFloor());
        for (long cell : geometry.passageFloor()) {
            var column = columns.get(xz(BlockPos.of(cell)));
            if (column == null) throw invalid("Missing passage floor column");
            if (column.supportDepth() == 0) result.add(cell);
        }
        return result;
    }

    private static Map<ChunkPos, Integer> components(Set<ChunkPos> claim) {
        Map<ChunkPos, Integer> result = new HashMap<>(); int id = 0;
        for (ChunkPos seed : claim.stream().sorted(Comparator.comparingInt((ChunkPos p) -> p.x).thenComparingInt(p -> p.z)).toList()) {
            if (result.containsKey(seed)) continue;
            ArrayDeque<ChunkPos> queue = new ArrayDeque<>(); queue.add(seed); result.put(seed, id);
            while (!queue.isEmpty()) {
                ChunkPos current = queue.removeFirst();
                for (Direction d : CARDINALS) {
                    ChunkPos next = new ChunkPos(current.x + d.getStepX(), current.z + d.getStepZ());
                    if (claim.contains(next) && result.putIfAbsent(next, id) == null) queue.addLast(next);
                }
            }
            id++;
        }
        return result;
    }

    private static Set<ChunkPos> exterior(Set<ChunkPos> claim) {
        int minX = claim.stream().mapToInt(p -> p.x).min().orElseThrow() - 1;
        int maxX = claim.stream().mapToInt(p -> p.x).max().orElseThrow() + 1;
        int minZ = claim.stream().mapToInt(p -> p.z).min().orElseThrow() - 1;
        int maxZ = claim.stream().mapToInt(p -> p.z).max().orElseThrow() + 1;
        Set<ChunkPos> result = new HashSet<>(); ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        ChunkPos seed = new ChunkPos(minX, minZ); result.add(seed); queue.add(seed);
        while (!queue.isEmpty()) {
            ChunkPos current = queue.removeFirst();
            for (Direction d : CARDINALS) {
                ChunkPos next = new ChunkPos(current.x + d.getStepX(), current.z + d.getStepZ());
                if (next.x >= minX && next.x <= maxX && next.z >= minZ && next.z <= maxZ
                        && !claim.contains(next) && result.add(next)) queue.addLast(next);
            }
        }
        return result;
    }

    private String calculateDigest() {
        StringBuilder text = new StringBuilder("perimeter-gates-v1\n").append(originalWallDigest).append('\n');
        for (Gate gate : gates) text.append("gate:").append(gate.componentId()).append(':').append(gate.facing().getName())
                .append(':').append(gate.outerCenter().asLong()).append('\n');
        observations.forEach((cell, state) -> text.append(air.contains(cell) ? "air:" : "floor:").append(cell).append(':')
                .append(PerimeterProject.stateKey(state)).append('\n'));
        return PerimeterStageLayout.sha256(text.toString());
    }
    private static String wallDigest(PerimeterBlueprint.Plan wall) {
        if (wall == null || wall.min() == null || wall.max() == null || wall.columns().size() > PerimeterStageLayout.MAX_COLUMNS
                || wall.blocks().size() > PerimeterStageLayout.MAX_TARGETS
                || (long) wall.blocks().size() + wall.clearance().size() > PerimeterStageLayout.MAX_RESERVED)
            throw invalid("Invalid original wall digest input");
        StringBuilder text = new StringBuilder("perimeter-gate-source-v1\n").append(wall.min().asLong()).append(':').append(wall.max().asLong()).append('\n');
        wall.columns().stream().sorted(Comparator.comparingLong(c -> c.base().asLong())).forEach(c -> text.append("column:")
                .append(c.base().asLong()).append(':').append(c.supportDepth()).append(':').append(c.inwardDistance()).append(':').append(c.componentId()).append('\n'));
        new TreeMap<>(wall.blocks()).forEach((cell, material) -> text.append("target:").append(cell).append(':').append(material).append('\n'));
        wall.clearance().stream().sorted().forEach(cell -> text.append("clear:").append(cell).append('\n'));
        return PerimeterStageLayout.sha256(text.toString());
    }
    private static PerimeterBlueprint.Plan copyPlan(PerimeterBlueprint.Plan source, Map<Long, String> targets, Set<Long> clearance) {
        Map<String, Integer> counts = new LinkedHashMap<>(); targets.values().forEach(material -> counts.merge(material, 1, Integer::sum));
        List<PerimeterBlueprint.Column> columns = source.columns().stream().map(c -> new PerimeterBlueprint.Column(
                c.base().immutable(), c.supportDepth(), c.inwardDistance(), c.componentId())).toList();
        return new PerimeterBlueprint.Plan(targets, columns, clearance, source.min().immutable(), source.max().immutable(),
                source.runs(), source.connections(), counts, source.problems());
    }
    private static BlockPos bounds(Set<Long> cells, boolean max) {
        int x = max ? Integer.MIN_VALUE : Integer.MAX_VALUE, y = x, z = x;
        for (long cell : cells) {
            BlockPos p = BlockPos.of(cell);
            x = max ? Math.max(x, p.getX()) : Math.min(x, p.getX());
            y = max ? Math.max(y, p.getY()) : Math.min(y, p.getY());
            z = max ? Math.max(z, p.getZ()) : Math.min(z, p.getZ());
        }
        return new BlockPos(x, y, z);
    }
    private static void validPosition(long cell) {
        BlockPos p = BlockPos.of(cell);
        if (Math.abs((long) p.getX()) >= WORLD_LIMIT || Math.abs((long) p.getZ()) >= WORLD_LIMIT)
            throw invalid("Unsupported gate observation coordinate");
    }
    private static long xz(BlockPos p) { return new BlockPos(p.getX(), 0, p.getZ()).asLong(); }
    private static Set<Long> freeze(Set<Long> cells) {
        if (cells.size() > MAX_OBSERVATIONS || cells.stream().anyMatch(java.util.Objects::isNull)) throw invalid("Invalid observation set");
        return Collections.unmodifiableSet(new LinkedHashSet<>(cells.stream().sorted().toList()));
    }
    private static BlockState readState(CompoundTag tag) {
        if (tag == null || !tag.getAllKeys().contains("Name") || !Set.of("Name", "Properties").containsAll(tag.getAllKeys()))
            throw invalid("Invalid observed block state fields");
        ResourceLocation name = ResourceLocation.tryParse(string(tag, "Name", 256));
        if (name == null || !BuiltInRegistries.BLOCK.containsKey(name)) throw invalid("Unknown observed block");
        if (tag.contains("Properties")) {
            require(tag, "Properties", Tag.TAG_COMPOUND); CompoundTag properties = tag.getCompound("Properties");
            if (properties.getAllKeys().size() > 32) throw invalid("Too many observation state properties");
            for (String key : properties.getAllKeys()) if (!PerimeterProject.bounded(key, 64)
                    || !PerimeterProject.bounded(string(properties, key, 128), 128)) throw invalid("Invalid observation state property");
        }
        BlockState result = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        if (!tag.equals(NbtUtils.writeBlockState(result))) throw invalid("Observed state cannot be restored losslessly");
        return result;
    }
    private static void keys(CompoundTag tag, Set<String> exact) {
        if (tag == null || !tag.getAllKeys().equals(exact)) throw invalid("Missing or unknown gate contract fields");
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw invalid("Missing or wrongly typed gate field: " + key);
    }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String string(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_STRING); String text = tag.getString(key);
        if (text.isEmpty() || text.length() > max) throw invalid("Invalid gate text field: " + key); return text;
    }
    private static String hash(CompoundTag tag, String key) {
        String text = string(tag, key, 64); if (!PerimeterProject.digest(text)) throw invalid("Invalid gate digest"); return text;
    }
    private static ListTag list(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_LIST); ListTag values = (ListTag) tag.get(key);
        if (values.isEmpty() || values.size() > max || values.getElementType() != Tag.TAG_COMPOUND)
            throw invalid("Invalid bounded gate list: " + key); return values;
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
