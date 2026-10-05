package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Pure whole-project budget/phase assembly. It never supplies claim, payment, native-access or mining authority. */
public final class PerimeterEarthworksAssembly {
    public static final int FORMAT_VERSION = 1, MAX_REGIONS = 256, MAX_INPUT_OBSERVATIONS = 131_072;
    public record Scope(UUID project, long generation, UUID owner, UUID builder, String dimension, String faction,
                        String claimsDigest, int minY, int maxY, int feeVersion, int price) {
        public Scope {
            if (!identity(project) || !identity(owner) || !identity(builder) || generation < 1
                    || !PerimeterProject.bounded(dimension, 256) || ResourceLocation.tryParse(dimension) == null
                    || !PerimeterProject.bounded(faction, 256) || !PerimeterProject.digest(claimsDigest)
                    || minY < -2_048 || maxY > 2_048 || minY >= maxY || feeVersion != 1 || price != 64)
                throw invalid("Invalid whole-project earthworks identity or quote");
        }
        public static Scope from(PerimeterEarthworksManifest.Header h) {
            return new Scope(h.project(), h.generation(), h.owner(), h.builder(), h.dimension(), h.faction(),
                    h.claimsDigest(), h.minY(), h.maxY(), h.feeVersion(), h.price());
        }
    }
    public record Original(BlockState state, long editRevision) {
        public Original { Objects.requireNonNull(state); if (editRevision < 0) throw invalid("Unknown observed edit revision"); }
    }
    public record WallCell(long pos, BlockState original, BlockState target, long editRevision) {
        public WallCell {
            Objects.requireNonNull(original);
            if (!PerimeterProject.supported(target) || editRevision < 0) throw invalid("A new target type requires its own versioned state/item contract");
        }
    }
    public record Assembly(Scope scope, List<String> regionHashes, Map<Long, Original> originals,
                           Map<Long, BlockState> wallTargets, Map<Long, BlockState> expectedWallBefore,
                           Set<Long> gradingWrites, Set<Long> wallWrites, Set<Long> permanentReadOnly,
                           Map<String, Integer> materialCounts, int cuts, int fills, int placements,
                           int nativeStages, String digest) {
        public Assembly {
            regionHashes = List.copyOf(regionHashes); originals = freeze(originals); wallTargets = freeze(wallTargets);
            expectedWallBefore = freeze(expectedWallBefore); gradingWrites = Set.copyOf(gradingWrites); wallWrites = Set.copyOf(wallWrites);
            permanentReadOnly = Set.copyOf(permanentReadOnly); materialCounts = Collections.unmodifiableMap(new TreeMap<>(materialCounts));
        }
    }
    private PerimeterEarthworksAssembly() {}

    /**
     * All local grading retires before ordinary flexible wall BUILD may start. Shared dependencies may
     * be read by multiple regions, but a write overlapping another region's observation is refused:
     * merge those regions or add a separately proved dependency schedule instead of guessing an order.
     */
    public static Assembly assemble(Scope scope, List<PerimeterEarthworksManifest> regions, List<WallCell> wall,
            List<PerimeterEarthworksManifest.Observation> readOnly, int wallNativeStages) {
        Objects.requireNonNull(scope); Objects.requireNonNull(regions); Objects.requireNonNull(wall); Objects.requireNonNull(readOnly);
        if (regions.size() > MAX_REGIONS || wall.size() > PerimeterStageLayout.MAX_TARGETS || readOnly.size() > PerimeterStageLayout.MAX_RESERVED
                || regions.isEmpty() && wall.isEmpty() || wallNativeStages < 0 || wallNativeStages > PerimeterStageLayout.MAX_STAGES)
            throw invalid("Invalid whole-project assembly size");
        long input = (long) wall.size() + readOnly.size();
        for (var region : regions) { Objects.requireNonNull(region); input += region.observations().size(); }
        if (input > MAX_INPUT_OBSERVATIONS) throw invalid("Aggregate snapshot input exceeds its bounded decode/assembly budget");
        Map<Long, Original> originals = new HashMap<>(); Map<Long, BlockState> postGrading = new HashMap<>();
        Map<Long, Set<Integer>> observedBy = new HashMap<>(); Map<Long, Integer> writeOwner = new HashMap<>();
        Set<Long> permanent = new HashSet<>(), placementCells = new HashSet<>(), columns = new HashSet<>();
        List<String> hashes = new ArrayList<>(); Map<String, Integer> materials = new TreeMap<>();
        int cuts = 0, fills = 0, placements = 0, stages = wallNativeStages;
        for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
            var region = regions.get(regionIndex);
            if (!Scope.from(region.header()).equals(scope)) throw invalid("Regions cannot borrow another project, claim, owner, world or payment quote");
            hashes.add(region.hash()); stages += region.stageDigests().size();
            if (stages > PerimeterStageLayout.MAX_STAGES) throw invalid("Combined native stages exceed the project cap");
            for (var cell : region.observations().values()) {
                merge(scope, originals, cell.pos(), new Original(cell.original(), cell.editRevision()));
                observedBy.computeIfAbsent(cell.pos(), ignored -> new HashSet<>()).add(regionIndex);
                if (cell.role() == PerimeterEarthworksManifest.Role.GATE_CLEARANCE || cell.role() == PerimeterEarthworksManifest.Role.GATE_FLOOR
                        || cell.role() == PerimeterEarthworksManifest.Role.PROTECTED_OCCUPANCY) permanent.add(cell.pos());
            }
            for (var step : region.steps()) {
                Integer owner = writeOwner.putIfAbsent(step.pos(), regionIndex);
                if (owner != null && owner != regionIndex) throw invalid("Two regions own the same mutation cell");
                postGrading.put(step.pos(), step.after()); addColumn(columns, step.pos());
                if (step.kind() == PerimeterEarthworksManifest.Kind.CUT) cuts++;
                else {
                    placements++; placementCells.add(step.pos()); materials.merge(PerimeterProject.stateKey(step.after()), 1, Integer::sum);
                    if (step.kind() == PerimeterEarthworksManifest.Kind.FILL) fills++;
                }
                if (cuts > PerimeterEarthworksManifest.MAX_CUTS || placements > PerimeterEarthworksManifest.MAX_PLACEMENTS)
                    throw invalid("Splitting regions cannot reset whole-project cut or placement budgets");
            }
        }
        for (long pos : writeOwner.keySet()) if (observedBy.get(pos).size() > 1)
            throw invalid("A local write conflicts with another region's frozen observation");
        for (var cell : readOnly) {
            Objects.requireNonNull(cell);
            if (cell.role() == PerimeterEarthworksManifest.Role.WORK) throw invalid("A read-only approach cannot grant mutation rights");
            merge(scope, originals, cell.pos(), new Original(cell.original(), cell.editRevision())); permanent.add(cell.pos());
        }
        for (long pos : writeOwner.keySet()) if (permanent.contains(pos)) throw invalid("Grading would mutate a permanently read-only observation");
        Map<Long, BlockState> targets = new HashMap<>(), beforeBuild = new HashMap<>(); Set<Long> wallWrites = new HashSet<>();
        for (WallCell cell : wall) {
            Objects.requireNonNull(cell); merge(scope, originals, cell.pos(), new Original(cell.original(), cell.editRevision()));
            if (targets.putIfAbsent(cell.pos(), cell.target()) != null) throw invalid("Duplicate wall target must be reconciled before assembly");
            BlockState expected = postGrading.getOrDefault(cell.pos(), cell.original()); beforeBuild.put(cell.pos(), expected);
            // A region placement and its matching structural wall target share one actual item charge.
            if (placementCells.contains(cell.pos()) && !expected.equals(cell.target()))
                throw invalid("A wall cannot overwrite a different paid local-placement target");
            if (!expected.equals(cell.target())) {
                if (permanent.contains(cell.pos())) throw invalid("Wall placement would mutate a read-only gate/occupancy observation");
                wallWrites.add(cell.pos()); placements++; materials.merge(PerimeterProject.stateKey(cell.target()), 1, Integer::sum);
            }
            placementCells.add(cell.pos()); addColumn(columns, cell.pos());
            if (placements > PerimeterEarthworksManifest.MAX_PLACEMENTS || placementCells.size() > PerimeterStageLayout.MAX_TARGETS)
                throw invalid("Grading plus wall geometry exceeds the whole-project placement/serialization budget");
        }
        Collections.sort(hashes);
        String digest = digest(scope, hashes, originals, targets, beforeBuild, permanent, materials, cuts, fills, placements, stages);
        return new Assembly(scope, hashes, originals, targets, beforeBuild, writeOwner.keySet(), wallWrites, permanent,
                materials, cuts, fills, placements, stages, digest);
    }
    private static void merge(Scope scope, Map<Long, Original> cells, long packed, Original value) {
        BlockPos pos = BlockPos.of(packed);
        if (pos.getX() < -30_000_000 || pos.getX() >= 30_000_000 || pos.getZ() < -30_000_000 || pos.getZ() >= 30_000_000
                || pos.getY() < scope.minY() || pos.getY() >= scope.maxY()) throw invalid("Snapshot outside the reviewed world bounds");
        Original old = cells.putIfAbsent(packed, value);
        if (old != null && !old.equals(value)) throw invalid("Shared originals or same-state edit revisions conflict");
        if (cells.size() > PerimeterStageLayout.MAX_RESERVED) throw invalid("Combined observations exceed the project reservation budget");
    }
    private static void addColumn(Set<Long> columns, long packed) {
        BlockPos pos = BlockPos.of(packed); columns.add(new BlockPos(pos.getX(), 0, pos.getZ()).asLong());
        if (columns.size() > PerimeterStageLayout.MAX_COLUMNS) throw invalid("Combined work columns exceed the project cap");
    }
    private static String digest(Scope scope, List<String> regions, Map<Long, Original> originals,
            Map<Long, BlockState> targets, Map<Long, BlockState> before, Set<Long> readOnly, Map<String, Integer> materials,
            int cuts, int fills, int placements, int stages) {
        Hash hash = new Hash(); hash.part("earthworks-assembly-v1").part(scope.project()).part(scope.generation()).part(scope.owner())
                .part(scope.builder()).part(scope.dimension()).part(scope.faction()).part(scope.claimsDigest()).part(scope.minY()).part(scope.maxY())
                .part(scope.feeVersion()).part(scope.price()).part(cuts).part(fills).part(placements).part(stages).part(regions.size());
        regions.forEach(hash::part); hash.part(originals.size());
        new TreeMap<>(originals).forEach((pos, cell) -> hash.part(pos).part(PerimeterProject.stateKey(cell.state())).part(cell.editRevision()));
        hash.part(targets.size()); new TreeMap<>(targets).forEach((pos, state) -> hash.part(pos).part(PerimeterProject.stateKey(state))
                .part(PerimeterProject.stateKey(before.get(pos))));
        hash.part(readOnly.size()); readOnly.stream().sorted().forEach(hash::part);
        hash.part(materials.size()); materials.forEach((id, count) -> hash.part(id).part(count)); return hash.finish();
    }
    private static <T> Map<Long, T> freeze(Map<Long, T> map) { return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(map))); }
    private static boolean identity(UUID id) { return id != null && !id.equals(new UUID(0, 0)); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static final class Hash {
        final MessageDigest digest;
        Hash() { try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
        Hash part(Object value) {
            String text = value.toString(); digest.update(ByteBuffer.allocate(4).putInt(text.length()).array());
            for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); digest.update((byte)(c >>> 8)); digest.update((byte)c); } return this;
        }
        String finish() { return HexFormat.of().formatHex(digest.digest()); }
    }
}
