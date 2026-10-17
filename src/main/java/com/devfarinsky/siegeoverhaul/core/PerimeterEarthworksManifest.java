package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

/**
 * Immutable, server-only proposal for one bounded level region. Terrain-following walls may use this for local pads. Not an accepted project or mining authority.
 * No live caller consumes this format. Origins and adapter descriptors are recorded evidence, not
 * inferred truth: admission, specific-removal consent, native work access and accounting are separate gates.
 * Existing version-one PerimeterProject saves are deliberately not migrated through this contract.
 */
public final class PerimeterEarthworksManifest {
    public static final int FORMAT_VERSION = 1, MAX_CUTS = 4_096, MAX_PLACEMENTS = 32_768;
    public static final int MAX_OBSERVATIONS = 65_536, MAX_STAGES = 256;
    public enum Role { WORK, PROTECTED_OCCUPANCY, GATE_CLEARANCE, GATE_FLOOR, DEPENDENCY }
    public enum Kind { CUT, FILL, BUILD }
    public enum Origin { UNKNOWN, RECORDED_WORLDGEN, KNOWN_PLAYER_EDIT }
    public enum Family { SOIL, STONE, WOOD, MODDED }

    public record Header(UUID project, long generation, UUID owner, UUID builder, String dimension,
                         String faction, String claimsDigest, String layoutDigest, String policy,
                         int component, int planeY, int minY, int maxY, int feeVersion, int price) {
        public Header {
            if (!identity(project) || !identity(owner) || !identity(builder) || generation < 1
                    || !resource(dimension) || !bounded(faction, 256) || !digest(claimsDigest)
                    || !digest(layoutDigest) || !bounded(policy, 128) || component < 0
                    || minY < -2_048 || maxY > 2_048 || minY >= maxY
                    || planeY <= minY || (long) planeY + 6 > maxY || feeVersion != 1 || price != 64)
                throw invalid("Invalid earthworks identity, bounds or quote");
        }
    }
    /** A durable edit counter must change on same-state player edits; zero does not prove natural origin. */
    public record Observation(long pos, BlockState original, Role role, long editRevision) {
        public Observation {
            Objects.requireNonNull(original); Objects.requireNonNull(role);
            if (editRevision < 0) throw invalid("Invalid observed edit revision");
        }
    }
    /**
     * A descriptor alone does not establish that an adapter was independently audited or is installed.
     * This first format supports only a declared single-cell effect. Trees/paired plants need a later
     * bounded dependency contract and are refused by the eventual admission layer, not name/tag exemptions.
     */
    public record Removal(Origin origin, Family family, String adapter, String version, String sourceDigest) {
        public Removal {
            Objects.requireNonNull(origin); Objects.requireNonNull(family);
            if (origin == Origin.KNOWN_PLAYER_EDIT || !resource(adapter) || !bounded(version, 64) || !digest(sourceDigest))
                throw invalid("Unsafe or incomplete removal evidence");
        }
    }
    /** List order is the exact native work order. A stage has one kind and cannot be skipped or mixed. */
    public record Step(int stage, Kind kind, long pos, BlockState before, BlockState after, Removal removal) {
        public Step {
            Objects.requireNonNull(kind); Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (stage < 0 || stage >= MAX_STAGES || before.equals(after)
                    || before.hasBlockEntity() || after.hasBlockEntity()
                    || before.getBlock() instanceof EntityBlock || after.getBlock() instanceof EntityBlock
                    || fluidBearing(before) || fluidBearing(after))
                throw invalid("Unsafe earthworks state transition");
            if (kind == Kind.CUT) {
                if (before.isAir() || !after.equals(Blocks.AIR.defaultBlockState()) || removal == null)
                    throw invalid("A cut requires an exact solid original, air result and removal evidence");
            } else if (!before.equals(Blocks.AIR.defaultBlockState()) || removal != null
                    || !PerimeterProject.supported(after)
                    || kind == Kind.FILL && !after.equals(Blocks.DIRT.defaultBlockState())) {
                throw invalid("Placement is distinct from mining and requires a supported material");
            }
        }
    }

    private final Header header;
    private final Map<Long, Observation> observations;
    private final List<Step> steps;
    private final Map<String, Integer> materials;
    private final List<String> stageDigests;
    private final String hash;
    private final int cutCount, fillCount;
    private final boolean unknownOrigin;

    public PerimeterEarthworksManifest(Header header, List<Observation> observations, List<Step> steps) {
        this.header = Objects.requireNonNull(header); Objects.requireNonNull(observations); Objects.requireNonNull(steps);
        if (observations.isEmpty() || observations.size() > MAX_OBSERVATIONS || steps.isEmpty()
                || steps.size() > MAX_CUTS + MAX_PLACEMENTS) throw invalid("Invalid earthworks record size");
        Map<Long, Observation> cells = new TreeMap<>();
        for (Observation cell : observations) {
            if (cell == null || !position(cell.pos(), header) || cells.putIfAbsent(cell.pos(), cell) != null)
                throw invalid("Invalid or duplicate earthworks observation");
        }
        this.observations = Collections.unmodifiableMap(new LinkedHashMap<>(cells));
        this.steps = List.copyOf(steps);
        Map<Long, BlockState> current = new HashMap<>(); Set<Long> cut = new HashSet<>(), placed = new HashSet<>();
        Map<Long, List<Integer>> cutsByColumn = new HashMap<>(), fillsByColumn = new HashMap<>();
        Map<String, Integer> supplies = new TreeMap<>(); Set<Long> workColumns = new HashSet<>();
        int stage = 0, cuts = 0, fills = 0, placements = 0; Kind stageKind = steps.get(0).kind();
        boolean unknown = false;
        for (Step step : this.steps) {
            Observation cell = cells.get(step.pos());
            if (cell == null || cell.role() != Role.WORK || step.stage() < stage || step.stage() > stage + 1)
                throw invalid("Step is outside its observed write cells or exact stage order");
            if (step.stage() != stage) {
                if (step.kind().ordinal() < stageKind.ordinal()) throw invalid("Earthworks phases cannot rewind");
                stage = step.stage(); stageKind = step.kind();
            }
            if (step.kind() != stageKind || !step.before().equals(current.getOrDefault(step.pos(), cell.original())))
                throw invalid("Changed original, mixed stage or broken cut-before-build chain");
            BlockPos pos = BlockPos.of(step.pos()); int y = pos.getY();
            long column = new BlockPos(pos.getX(), 0, pos.getZ()).asLong();
            if (workColumns.add(column) && workColumns.size() > PerimeterGradePlane.MAX_COLUMNS)
                throw invalid("Earthworks column budget exceeded");
            switch (step.kind()) {
                case CUT -> {
                    if (!placed.isEmpty() || !cut.add(step.pos()) || y < header.planeY() || y >= header.planeY() + 4)
                        throw invalid("Cut order, depth or duplicate cell is unsafe");
                    cutsByColumn.computeIfAbsent(column, ignored -> new ArrayList<>()).add(y);
                    cuts++; unknown |= step.removal().origin() == Origin.UNKNOWN;
                }
                case FILL -> {
                    if (y < header.planeY() - 8 || y >= header.planeY()) throw invalid("Fill exceeds retained support bound");
                    fillsByColumn.computeIfAbsent(column, ignored -> new ArrayList<>()).add(y); fills++;
                }
                case BUILD -> {
                    if (y < header.planeY() || y >= header.planeY() + 6) throw invalid("Build outside wall height");
                }
            }
            if (step.kind() != Kind.CUT) {
                if (!placed.add(step.pos())) throw invalid("A material cell cannot be paid or placed twice");
                placements++; supplies.merge(PerimeterProject.stateKey(step.after()), 1, Integer::sum);
            }
            current.put(step.pos(), step.after());
        }
        if (this.steps.get(0).stage() != 0 || cuts > MAX_CUTS || placements > MAX_PLACEMENTS)
            throw invalid("Earthworks exceeds whole-proposal budgets");
        for (List<Integer> column : cutsByColumn.values()) contiguous(column, -1, header.planeY());
        for (List<Integer> column : fillsByColumn.values()) contiguous(column, 1, header.planeY() - 1);
        for (Observation cell : cells.values()) if (cell.role() == Role.WORK && !current.containsKey(cell.pos()))
            throw invalid("Unassigned write observation");
        this.cutCount = cuts; this.fillCount = fills; this.unknownOrigin = unknown;
        this.materials = Collections.unmodifiableMap(new LinkedHashMap<>(supplies));
        this.hash = calculateHash();
        List<Hash> stageHashes = new ArrayList<>();
        for (int i = 0; i <= stage; i++) stageHashes.add(new Hash().part("earthworks-stage-v1").part(hash).part(i));
        for (int i = 0; i < this.steps.size(); i++) stageHashes.get(this.steps.get(i).stage()).part(i);
        this.stageDigests = stageHashes.stream().map(Hash::finish).toList();
    }

    public Header header() { return header; }
    public Map<Long, Observation> observations() { return observations; }
    public List<Step> steps() { return steps; }
    /** Construction demand only. Expected mining drops never reduce this count. */
    public Map<String, Integer> materialCounts() { return materials; }
    public int cutCount() { return cutCount; }
    public int fillCount() { return fillCount; }
    public boolean requiresSpecificRemovalReview() { return unknownOrigin; }
    public String hash() { return hash; }
    public List<String> stageDigests() { return stageDigests; }
    public CompoundTag save() { return PerimeterEarthworksCodec.save(this); }
    public static PerimeterEarthworksManifest load(CompoundTag tag) { return PerimeterEarthworksCodec.load(tag); }

    private String calculateHash() {
        Hash value = new Hash().part("earthworks-proposal-v1");
        value.part(header.project()).part(header.generation()).part(header.owner()).part(header.builder())
                .part(header.dimension()).part(header.faction()).part(header.claimsDigest()).part(header.layoutDigest())
                .part(header.policy()).part(header.component()).part(header.planeY()).part(header.minY()).part(header.maxY())
                .part(header.feeVersion()).part(header.price()).part(observations.size());
        observations.values().forEach(cell -> value.part(cell.pos()).part(PerimeterProject.stateKey(cell.original()))
                .part(cell.role()).part(cell.editRevision()));
        value.part(steps.size());
        for (Step step : steps) {
            value.part(step.stage()).part(step.kind()).part(step.pos()).part(PerimeterProject.stateKey(step.before()))
                    .part(PerimeterProject.stateKey(step.after())).part(step.removal() != null);
            if (step.removal() != null) value.part(step.removal().origin()).part(step.removal().family())
                    .part(step.removal().adapter()).part(step.removal().version()).part(step.removal().sourceDigest());
        }
        return value.finish();
    }
    private static void contiguous(List<Integer> ys, int delta, int last) {
        for (int i = 1; i < ys.size(); i++) if (ys.get(i) != ys.get(i - 1) + delta)
            throw invalid("Earthworks column is incomplete or ordered unsafely");
        if (ys.get(ys.size() - 1) != last) throw invalid("Earthworks column does not meet its plane");
    }
    private static boolean position(long packed, Header h) {
        BlockPos p = BlockPos.of(packed);
        return p.getX() >= -30_000_000 && p.getX() < 30_000_000 && p.getZ() >= -30_000_000 && p.getZ() < 30_000_000
                && p.getY() >= h.minY() && p.getY() < h.maxY();
    }
    private static boolean fluidBearing(BlockState state) {
        // Explicit block/property fences do not rely on an initialized native state cache.
        return !state.getFluidState().isEmpty() || state.getBlock() instanceof LiquidBlock
                || state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }
    private static boolean identity(UUID id) { return id != null && !id.equals(new UUID(0, 0)); }
    private static boolean resource(String value) { return bounded(value, 256) && ResourceLocation.tryParse(value) != null; }
    static boolean bounded(String value, int max) { return PerimeterProject.bounded(value, max); }
    static boolean digest(String value) { return PerimeterProject.digest(value); }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static final class Hash {
        private final MessageDigest digest;
        Hash() {
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        Hash part(Object value) {
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes); return this;
        }
        String finish() { return HexFormat.of().formatHex(digest.digest()); }
    }
}
