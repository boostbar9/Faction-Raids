package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Lossless partition of one already compiled perimeter. Never generates another wall. */
public final class PerimeterStageLayout {
    public static final int MAX_TARGETS = 32_768, MAX_RESERVED = 65_536, MAX_COLUMNS = 20_480;
    public static final int MAX_STAGES = 256, MAX_VALIDATIONS = 8_192;
    public static final long MAX_SCAN_VOLUME = 1_048_576L;
    private static final Comparator<Long> COLUMN_ORDER = Comparator
            .comparingInt((Long p) -> BlockPos.of(p).getX() >> 4)
            .thenComparingInt(p -> BlockPos.of(p).getZ() >> 4)
            .thenComparingInt(p -> BlockPos.of(p).getX()).thenComparingInt(p -> BlockPos.of(p).getZ());

    private PerimeterStageLayout() {}

    /** Must check the unchanged native blueprint's actual COMPOUND_TAG write/read roundtrip. */
    @FunctionalInterface public interface NativeStageValidator { String problem(Stage stage); }

    public static final class Stage {
        private final int index;
        private final List<Long> columns;
        private final Map<Long, String> targets;
        private final Set<Long> clearance, reservation;
        private final BlockPos min, max;
        private final String digest;
        public Stage(int index, List<Long> columns, Map<Long, String> targets, Set<Long> clearance,
                     BlockPos min, BlockPos max, String digest) {
            this.index = index; this.columns = List.copyOf(columns);
            this.targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
            this.clearance = Collections.unmodifiableSet(new LinkedHashSet<>(clearance));
            this.min = min.immutable(); this.max = max.immutable(); this.digest = digest;
            Set<Long> reserved = new HashSet<>(this.targets.keySet()); reserved.addAll(this.clearance);
            this.reservation = Set.copyOf(reserved);
        }
        public int index() { return index; }
        public List<Long> columns() { return columns; }
        public Map<Long, String> targets() { return targets; }
        public Set<Long> clearance() { return clearance; }
        public BlockPos min() { return min; }
        public BlockPos max() { return max; }
        public String digest() { return digest; }
        public BlockPos origin() { return new BlockPos(max.getX(), min.getY(), min.getZ()); }
        public int width() { return max.getX() - min.getX() + 1; }
        public int depth() { return max.getZ() - min.getZ() + 1; }
        public int height() { return max.getY() - min.getY() + 1; }
        public Set<Long> reservation() { return reservation; }
        @Override public boolean equals(Object other) {
            return other instanceof Stage that && index == that.index && columns.equals(that.columns)
                    && targets.equals(that.targets) && clearance.equals(that.clearance)
                    && min.equals(that.min) && max.equals(that.max) && Objects.equals(digest, that.digest);
        }
        @Override public int hashCode() { return Objects.hash(index, columns, targets, clearance, min, max, digest); }
    }

    public record Layout(List<Stage> stages, String digest) {
        public Layout { stages = List.copyOf(stages); }
    }

    /** Throws for any unrepresentable atom or invalid input. No partial layout escapes. */
    public static Layout partition(PerimeterBlueprint.Plan plan, NativeStageValidator validator) {
        Objects.requireNonNull(validator, "An actual native serializer validator is required");
        Map<Long, Atom> atoms = atoms(plan);
        Map<Long, List<Atom>> buckets = new LinkedHashMap<>();
        atoms.keySet().stream().sorted(COLUMN_ORDER).forEach(column -> {
            BlockPos p = BlockPos.of(column);
            long chunk = net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            buckets.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(atoms.get(column));
        });
        Partition builder = new Partition(validator);
        for (List<Atom> bucket : buckets.values()) builder.accept(bucket);
        builder.seal();
        Layout layout = new Layout(builder.stages, layoutDigest(builder.stages));
        validate(plan, layout);
        return layout;
    }

    /** Rechecks exact membership/bounds/digests. Network validation is separately required before activation. */
    public static void validate(PerimeterBlueprint.Plan plan, Layout layout) {
        Map<Long, Atom> atoms = atoms(plan);
        if (layout == null || layout.stages().isEmpty() || layout.stages().size() > MAX_STAGES)
            throw invalid("Invalid number of perimeter stages");
        Set<Long> visited = new HashSet<>();
        int index = 0;
        for (Stage stage : layout.stages()) {
            if (stage == null || stage.index() != index++ || stage.columns().isEmpty()) throw invalid("Invalid stage order");
            List<Atom> members = new ArrayList<>();
            for (long column : stage.columns()) {
                Atom atom = atoms.get(column);
                if (atom == null || !visited.add(column)) throw invalid("Duplicate or unknown stage column");
                members.add(atom);
            }
            Stage exact = stage(stage.index(), members);
            if (!exact.equals(stage) || !withinBounds(stage)) throw invalid("Stage does not match the approved global plan");
        }
        if (!visited.equals(atoms.keySet()) || !layoutDigest(layout.stages()).equals(layout.digest()))
            throw invalid("Incomplete or changed perimeter partition");
    }

    static Layout restoreLayout(List<List<Long>> memberships, PerimeterBlueprint.Plan plan) {
        Map<Long, Atom> atoms = atoms(plan);
        if (memberships.isEmpty() || memberships.size() > MAX_STAGES) throw invalid("Invalid saved stage count");
        List<Stage> stages = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (List<Long> columns : memberships) {
            List<Atom> members = new ArrayList<>();
            for (long column : columns) {
                Atom atom = atoms.get(column);
                if (atom == null || !seen.add(column)) throw invalid("Duplicate or unknown saved stage column");
                members.add(atom);
            }
            if (members.isEmpty()) throw invalid("Empty saved stage");
            stages.add(stage(stages.size(), members));
        }
        Layout result = new Layout(stages, layoutDigest(stages));
        validate(plan, result);
        return result;
    }

    public static boolean withinBounds(Stage stage) {
        if (stage.targets().isEmpty() || stage.targets().size() > MAX_TARGETS
                || (long) stage.targets().size() + stage.clearance().size() > MAX_RESERVED
                || stage.width() < 1 || stage.width() > 256 || stage.depth() < 1 || stage.depth() > 256
                || stage.height() < 1 || stage.height() > 384) return false;
        long volume = (long) stage.width() * stage.depth() * (stage.height() + 1L);
        return volume <= MAX_SCAN_VOLUME && volume + stage.targets().size() <= MAX_SCAN_VOLUME + MAX_RESERVED;
    }

    static long column(long packed) { BlockPos p = BlockPos.of(packed); return new BlockPos(p.getX(), 0, p.getZ()).asLong(); }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String layoutDigest(List<Stage> stages) {
        StringBuilder out = new StringBuilder("perimeter-layout-v1\n");
        for (Stage stage : stages) out.append(stage.index()).append(':').append(stage.digest()).append('\n');
        return sha256(out.toString());
    }

    private record Atom(long column, Map<Long, String> targets, Set<Long> clearance) {}
    private static Map<Long, Atom> atoms(PerimeterBlueprint.Plan plan) {
        if (plan == null || !plan.valid() || plan.blocks().size() > MAX_TARGETS || plan.columns().isEmpty()
                || plan.columns().size() > MAX_COLUMNS || (long) plan.blocks().size() + plan.clearance().size() > MAX_RESERVED)
            throw invalid("The complete perimeter exceeds its bounded manifest budget");
        Map<Long, Atom> atoms = new HashMap<>();
        for (PerimeterBlueprint.Column source : plan.columns()) {
            if (source == null || source.base() == null || source.supportDepth() < 0 || source.supportDepth() > 64
                    || source.inwardDistance() < 1 || source.inwardDistance() > 5 || source.componentId() < 0
                    || source.componentId() >= PerimeterTerritory.MAX_CHUNKS) throw invalid("Missing perimeter column");
            long key = column(source.base().asLong());
            if (atoms.putIfAbsent(key, new Atom(key, new LinkedHashMap<>(), new LinkedHashSet<>())) != null)
                throw invalid("Duplicate perimeter column");
        }
        plan.blocks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Atom atom = atoms.get(column(entry.getKey()));
            if (atom == null || entry.getValue() == null || !Set.of("minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:dirt").contains(entry.getValue()))
                throw invalid("Unknown perimeter target column or material");
            atom.targets().put(entry.getKey(), entry.getValue());
        });
        plan.clearance().stream().sorted().forEach(cell -> {
            Atom atom = atoms.get(column(cell));
            if (atom == null || plan.blocks().containsKey(cell)) throw invalid("Unknown or overlapping clearance cell");
            atom.clearance().add(cell);
        });
        for (Atom atom : atoms.values()) if (atom.targets().isEmpty()) throw invalid("A perimeter column has no targets");
        Stage whole = stage(0, atoms.values().stream().sorted(Comparator.comparing(Atom::column, COLUMN_ORDER)).toList());
        if (!whole.min().equals(plan.min()) || !whole.max().equals(plan.max())
                || whole.width() < 1 || whole.width() > 256 || whole.depth() < 1 || whole.depth() > 256
                || whole.height() < 1 || whole.height() > 384
                || (long) whole.width() * whole.depth() * whole.height() > MAX_SCAN_VOLUME) throw invalid("Global bounds do not match all reserved cells");
        return atoms;
    }

    private static Stage stage(int index, List<Atom> atoms) {
        Map<Long, String> targets = new LinkedHashMap<>(); Set<Long> clearance = new LinkedHashSet<>();
        List<Long> columns = new ArrayList<>();
        for (Atom atom : atoms) { columns.add(atom.column()); targets.putAll(atom.targets()); clearance.addAll(atom.clearance()); }
        Set<Long> cells = new HashSet<>(targets.keySet()); cells.addAll(clearance);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long cell : cells) {
            BlockPos p = BlockPos.of(cell);
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        BlockPos min = new BlockPos(minX, minY, minZ), max = new BlockPos(maxX, maxY, maxZ);
        StringBuilder content = new StringBuilder("perimeter-stage-v1\n");
        columns.forEach(p -> content.append("c:").append(p).append('\n'));
        targets.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> content.append("t:")
                .append(e.getKey()).append(':').append(e.getValue()).append('\n'));
        clearance.stream().sorted().forEach(p -> content.append("a:").append(p).append('\n'));
        return new Stage(index, columns, targets, clearance, min, max, sha256(content.toString()));
    }

    private static final class Partition {
        final NativeStageValidator validator; final List<Stage> stages = new ArrayList<>();
        List<Atom> current = new ArrayList<>(); Stage accepted; int validations;
        Partition(NativeStageValidator validator) { this.validator = validator; }
        void accept(List<Atom> atoms) {
            List<Atom> candidate = new ArrayList<>(current); candidate.addAll(atoms);
            Stage proposed = stage(stages.size(), candidate);
            if (++validations > MAX_VALIDATIONS) throw invalid("Perimeter partition validation budget exceeded");
            String problem = withinBounds(proposed) ? validator.problem(proposed) : "Native stage envelope is too large";
            if (problem == null) { current = candidate; accepted = proposed; return; }
            if (!current.isEmpty()) { seal(); accept(atoms); return; }
            if (atoms.size() == 1) throw invalid("One complete column cannot fit a native stage: " + problem);
            int middle = atoms.size() / 2;
            accept(atoms.subList(0, middle)); accept(atoms.subList(middle, atoms.size()));
        }
        void seal() {
            if (current.isEmpty()) return;
            if (stages.size() >= MAX_STAGES) throw invalid("Too many native perimeter stages");
            stages.add(accepted); current = new ArrayList<>(); accepted = null;
        }
    }
    private static IllegalArgumentException invalid(String problem) { return new IllegalArgumentException(problem); }
}
