package com.devfarinsky.siegeoverhaul.core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * New-only, immutable two-phase data contract. This validates recorded values, not a world,
 * compiler provenance, payment, native material demand, or persistence. No execution path uses it.
 */
public final class PerimeterSteppedAssembly {
    public static final int FORMAT_VERSION = 1, FEE_VERSION = 1, PRICE = 64;
    public static final int MAX_TARGETS = 32_768, MAX_OBSERVATIONS = 65_536, MAX_CLAIMS = 4096;
    public static final int MAX_MATERIALS = 4;
    public static final int MAX_STATE_CODE_UNITS = 4_194_304;
    public static final long MAX_ENVELOPE_VOLUME = 1_048_576;
    private static final Set<String> WALL_BLOCKS = Set.of("minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:oak_planks");
    private static final UUID ZERO = new UUID(0, 0);

    public enum Kind { FILL, STRUCTURE }
    public enum Edit { PLACE, KEEP }
    public enum ObservationRole { CLEARANCE, SUPPORT }

    public record Position(int x, int y, int z) implements Comparable<Position> {
        public Position {
            require(Math.abs((long) x) < 30_000_000 && Math.abs((long) z) < 30_000_000
                    && y >= -2048 && y < 2048, "Position is outside supported world bounds");
        }
        @Override public int compareTo(Position other) {
            int c = Integer.compare(x, other.x);
            if (c == 0) c = Integer.compare(z, other.z);
            return c == 0 ? Integer.compare(y, other.y) : c;
        }
        public Chunk chunk() { return new Chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)); }
    }
    public record Chunk(int x, int z) implements Comparable<Chunk> {
        public Chunk {
            require((long) x * 16 > -30_000_000 && (long) x * 16 + 15 < 30_000_000
                    && (long) z * 16 > -30_000_000 && (long) z * 16 + 15 < 30_000_000,
                    "Claim is outside supported world bounds");
        }
        @Override public int compareTo(Chunk other) {
            int c = Integer.compare(x, other.x); return c == 0 ? Integer.compare(z, other.z) : c;
        }
    }
    /** Exact block/property snapshot. No stringification or omitted property is used for hashing. */
    public record StateValue(String block, Map<String, String> properties) {
        public static final StateValue AIR = new StateValue("minecraft:air", Map.of());
        public static final StateValue DIRT = new StateValue("minecraft:dirt", Map.of());
        public StateValue {
            require(resource(block), "Invalid block identity");
            require(properties != null && properties.size() <= 32, "State property budget exceeded");
            TreeMap<String, String> copy = new TreeMap<>();
            properties.forEach((key, value) -> {
                require(text(key, 64) && text(value, 64), "Invalid exact state property"); copy.put(key, value);
            });
            properties = Collections.unmodifiableMap(copy);
        }
    }
    /** observedEditRevision is an exact external edit-ledger observation, not inferred from a block. */
    public record Target(Kind phase, int component, StateValue original, StateValue target,
                         Edit edit, long observedEditRevision) {
        public Target {
            Objects.requireNonNull(phase); Objects.requireNonNull(original); Objects.requireNonNull(target); Objects.requireNonNull(edit);
            require(component >= 0 && component < MAX_CLAIMS && observedEditRevision >= 0, "Invalid target observation");
            require(target.properties().isEmpty(), "Only property-free full-block targets are supported");
            if (phase == Kind.FILL) {
                require(original.equals(StateValue.AIR) && target.equals(StateValue.DIRT) && edit == Edit.PLACE,
                        "Fill must be a bounded AIR-to-DIRT placement");
            } else {
                require(WALL_BLOCKS.contains(target.block()), "Unsupported structure full-block target");
                require(edit == Edit.PLACE ? original.equals(StateValue.AIR) : original.equals(target),
                        "Structure edit must preserve its exact original observation");
            }
        }
    }
    public record Observation(ObservationRole role, StateValue state, long observedEditRevision) {
        public Observation {
            Objects.requireNonNull(role); Objects.requireNonNull(state);
            require(observedEditRevision >= 0, "Invalid observation edit revision");
            require(role == ObservationRole.CLEARANCE ? state.equals(StateValue.AIR) : !state.equals(StateValue.AIR),
                    "Unsupported clearance or support state");
        }
    }
    public record Header(UUID projectId, long generation, UUID owner, UUID builder, String faction,
                         String coreKey, Position originalCore, String dimension, UUID ledgerId,
                         long ledgerGeneration, Set<Chunk> claims, int minY, int maxY,
                         String reviewedFingerprint, int feeVersion, int quotedPrice) {
        public Header {
            requireId(projectId); requireId(owner); requireId(builder); requireId(ledgerId);
            Objects.requireNonNull(originalCore);
            require(generation > 0 && ledgerGeneration > 0 && text(faction, 128) && text(coreKey, 256)
                    && coreKey.equals("team:" + faction) && resource(dimension), "Invalid parent identity");
            require(minY >= -2048 && maxY <= 2048 && minY < maxY, "Invalid world height bounds");
            require(originalCore.y() >= minY && originalCore.y() < maxY, "Core outside recorded world height");
            require(claims != null && !claims.isEmpty() && claims.size() <= MAX_CLAIMS, "Invalid exact claims");
            claims = Collections.unmodifiableSet(new TreeSet<>(claims));
            require(claims.contains(originalCore.chunk()), "Core outside exact claims");
            require(digest(reviewedFingerprint) && feeVersion == FEE_VERSION && quotedPrice == PRICE, "Invalid review or one-parent quote");
        }
    }
    /** A receipt binds to this entire identity; it does not acquire authority by having this shape. */
    public record Binding(UUID projectId, long generation, UUID ledgerId, long ledgerGeneration, String digest) {}
    public record Phase(int index, Kind kind, UUID areaId, String digest, List<Integer> dependencies,
                        Map<Position, Target> targets, Set<Position> reservation, Map<String, Integer> materialBill) {
        public Phase {
            dependencies = List.copyOf(dependencies);
            targets = Collections.unmodifiableMap(new TreeMap<>(targets));
            reservation = Collections.unmodifiableSet(new TreeSet<>(reservation));
            materialBill = Collections.unmodifiableMap(new TreeMap<>(materialBill));
        }
    }

    private final Header header;
    private final int geometryVersion;
    private final String geometryDigest;
    private final Map<Position, Target> targets;
    private final Map<Position, Observation> observations;
    private final Set<Position> reservation;
    private final Map<String, Integer> materialBill;
    private final List<Phase> phases;
    private final String digest;

    private PerimeterSteppedAssembly(Header header, int geometryVersion, String geometryDigest,
                                     Map<Position, Target> targets, Map<Position, Observation> observations,
                                     Map<String, Integer> plannedMaterialBill) {
        this.header = Objects.requireNonNull(header);
        require(geometryVersion > 0 && digest(geometryDigest), "Missing exact compiler version/digest");
        this.geometryVersion = geometryVersion; this.geometryDigest = geometryDigest;
        require(targets != null && !targets.isEmpty() && targets.size() <= MAX_TARGETS, "Target budget exceeded");
        require(observations != null && observations.size() <= MAX_OBSERVATIONS, "Observation budget exceeded");
        require((long) targets.size() + observations.size() <= MAX_OBSERVATIONS, "Aggregate reservation budget exceeded");
        this.targets = Collections.unmodifiableMap(new TreeMap<>(targets));
        this.observations = Collections.unmodifiableMap(new TreeMap<>(observations));
        require(Collections.disjoint(this.targets.keySet(), this.observations.keySet()), "Mutation and read-only ownership overlap");
        TreeSet<Position> reserved = new TreeSet<>(this.targets.keySet()); reserved.addAll(this.observations.keySet());
        reserved.forEach(p -> require(p.y() >= header.minY() && p.y() < header.maxY(), "Observation outside recorded world height"));
        this.targets.forEach((position, target) -> {
            Objects.requireNonNull(target); require(header.claims().contains(position.chunk()), "Mutation outside exact parent claims");
        });
        this.observations.values().forEach(Objects::requireNonNull);
        long stateUnits = 0;
        for (Target t : this.targets.values()) stateUnits += stateUnits(t.original()) + stateUnits(t.target());
        for (Observation o : this.observations.values()) stateUnits += stateUnits(o.state());
        require(stateUnits <= MAX_STATE_CODE_UNITS, "Aggregate exact-state budget exceeded");
        long width = (long) reserved.stream().mapToInt(Position::x).max().orElseThrow() - reserved.stream().mapToInt(Position::x).min().orElseThrow() + 1;
        long depth = (long) reserved.stream().mapToInt(Position::z).max().orElseThrow() - reserved.stream().mapToInt(Position::z).min().orElseThrow() + 1;
        long height = (long) reserved.stream().mapToInt(Position::y).max().orElseThrow() - reserved.stream().mapToInt(Position::y).min().orElseThrow() + 1;
        // Divide before multiplying so extreme legal coordinates cannot overflow into a small volume.
        require(width <= MAX_ENVELOPE_VOLUME / depth && width * depth <= MAX_ENVELOPE_VOLUME / height,
                "Whole reservation exceeds native envelope budget");
        this.reservation = Collections.unmodifiableSet(reserved);
        Map<String, Integer> calculated = bill(this.targets);
        require(plannedMaterialBill != null && plannedMaterialBill.size() <= MAX_MATERIALS
                && calculated.equals(plannedMaterialBill), "Material bill differs from exact PLACE membership");
        this.materialBill = Collections.unmodifiableMap(new TreeMap<>(calculated));
        boolean hasFill = this.targets.values().stream().anyMatch(t -> t.phase() == Kind.FILL);
        boolean hasStructure = this.targets.values().stream().anyMatch(t -> t.phase() == Kind.STRUCTURE);
        require(hasStructure, "Structure phase must not be empty");
        this.digest = hash(this::writeContract);
        List<Phase> result = new ArrayList<>();
        for (Kind kind : phaseKinds(hasFill, hasStructure)) {
            TreeMap<Position, Target> membership = new TreeMap<>();
            this.targets.forEach((p, t) -> { if (t.phase() == kind) membership.put(p, t); });
            int phaseIndex = result.size();
            TreeSet<Position> ownReservation = new TreeSet<>(membership.keySet()); ownReservation.addAll(this.observations.keySet());
            String phaseDigest = hash(out -> {
                string(out, "siege-stepped-phase-v1"); string(out, this.digest);
                out.writeInt(phaseIndex); out.writeInt(kind.ordinal());
            });
            byte[] idBytes = HexFormat.of().parseHex(phaseDigest);
            UUID areaId = UUID.nameUUIDFromBytes(idBytes);
            require(!areaId.equals(header.projectId()), "Phase area collides with parent identity");
            result.add(new Phase(phaseIndex, kind, areaId, phaseDigest,
                    kind == Kind.STRUCTURE && hasFill ? List.of(0) : List.of(), membership, ownReservation, bill(membership)));
        }
        Set<UUID> areaIds = new java.util.HashSet<>();
        for (Phase phase : result) require(areaIds.add(phase.areaId()), "Phase area identities must differ");
        this.phases = List.copyOf(result);
    }

    /** Inputs must come from separately verified compiler/world observations. This method cannot establish that provenance. */
    public static PerimeterSteppedAssembly assemble(Header header, int geometryVersion, String geometryDigest,
                                                    Map<Position, Target> targets, Map<Position, Observation> observations,
                                                    Map<String, Integer> plannedMaterialBill) {
        return new PerimeterSteppedAssembly(header, geometryVersion, geometryDigest, targets, observations, plannedMaterialBill);
    }
    public Header header() { return header; }
    public int geometryVersion() { return geometryVersion; }
    public String geometryDigest() { return geometryDigest; }
    public Map<Position, Target> targets() { return targets; }
    public Map<Position, Observation> observations() { return observations; }
    public Set<Position> reservation() { return reservation; }
    /** Planned full-block demand only. The actual native parser must independently agree before activation. */
    public Map<String, Integer> materialBill() { return materialBill; }
    public List<Phase> phases() { return phases; }
    public String digest() { return digest; }
    public Binding binding() { return new Binding(header.projectId(), header.generation(), header.ledgerId(), header.ledgerGeneration(), digest); }
    public boolean executionSupported() { return false; }

    private static Map<String, Integer> bill(Map<Position, Target> targets) {
        TreeMap<String, Integer> result = new TreeMap<>();
        targets.values().forEach(t -> { if (t.edit() == Edit.PLACE) result.merge(t.target().block(), 1, Math::addExact); });
        return result;
    }
    private void writeContract(DataOutputStream out) throws IOException {
        string(out, "siege-stepped-assembly"); out.writeInt(FORMAT_VERSION);
        uuid(out, header.projectId()); out.writeLong(header.generation()); uuid(out, header.owner()); uuid(out, header.builder());
        string(out, header.faction()); string(out, header.coreKey()); position(out, header.originalCore()); string(out, header.dimension());
        uuid(out, header.ledgerId()); out.writeLong(header.ledgerGeneration()); out.writeInt(header.minY()); out.writeInt(header.maxY());
        string(out, header.reviewedFingerprint()); out.writeInt(header.feeVersion()); out.writeInt(header.quotedPrice());
        out.writeInt(header.claims().size()); for (Chunk c : header.claims()) { out.writeInt(c.x()); out.writeInt(c.z()); }
        out.writeInt(geometryVersion); string(out, geometryDigest);
        out.writeInt(targets.size());
        for (var entry : targets.entrySet()) {
            position(out, entry.getKey()); Target t = entry.getValue(); out.writeInt(t.phase().ordinal()); out.writeInt(t.component());
            state(out, t.original()); state(out, t.target()); out.writeInt(t.edit().ordinal()); out.writeLong(t.observedEditRevision());
        }
        out.writeInt(observations.size());
        for (var entry : observations.entrySet()) {
            position(out, entry.getKey()); Observation o = entry.getValue(); out.writeInt(o.role().ordinal());
            state(out, o.state()); out.writeLong(o.observedEditRevision());
        }
        // Commit to exact ordered phase memberships, dependencies and reservations as well as source maps.
        boolean hasFill = targets.values().stream().anyMatch(t -> t.phase() == Kind.FILL);
        boolean hasStructure = targets.values().stream().anyMatch(t -> t.phase() == Kind.STRUCTURE);
        List<Kind> orderedPhases = phaseKinds(hasFill, hasStructure);
        out.writeInt(orderedPhases.size());
        for (int phaseIndex = 0; phaseIndex < orderedPhases.size(); phaseIndex++) {
            Kind phase = orderedPhases.get(phaseIndex);
            out.writeInt(phaseIndex); out.writeInt(phase.ordinal());
            out.writeInt(phase == Kind.STRUCTURE && hasFill ? 1 : 0);
            if (phase == Kind.STRUCTURE && hasFill) out.writeInt(0);
            List<Position> members = targets.entrySet().stream().filter(e -> e.getValue().phase() == phase).map(Map.Entry::getKey).toList();
            out.writeInt(members.size()); for (Position p : members) position(out, p);
            TreeSet<Position> reserved = new TreeSet<>(members); reserved.addAll(observations.keySet());
            out.writeInt(reserved.size()); for (Position p : reserved) position(out, p);
        }
        out.writeInt(reservation.size()); for (Position p : reservation) position(out, p);
        out.writeInt(materialBill.size()); for (var e : materialBill.entrySet()) { string(out, e.getKey()); out.writeInt(e.getValue()); }
    }
    private static long stateUnits(StateValue state) {
        long size = state.block().length();
        for (var property : state.properties().entrySet()) size += property.getKey().length() + property.getValue().length();
        return size;
    }
    private static List<Kind> phaseKinds(boolean hasFill, boolean hasStructure) {
        List<Kind> result = new ArrayList<>(2);
        if (hasFill) result.add(Kind.FILL);
        if (hasStructure) result.add(Kind.STRUCTURE);
        return result;
    }
    private static void state(DataOutputStream out, StateValue state) throws IOException {
        string(out, state.block()); out.writeInt(state.properties().size());
        for (var e : state.properties().entrySet()) { string(out, e.getKey()); string(out, e.getValue()); }
    }
    private static void position(DataOutputStream out, Position p) throws IOException { out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z()); }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    /** Length-framed UTF-16 code units: even unpaired surrogates cannot alias through UTF-8 replacement. */
    private static void string(DataOutputStream out, String text) throws IOException { out.writeInt(text.length()); for (int i = 0; i < text.length(); i++) out.writeChar(text.charAt(i)); }
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static String hash(Writer writer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                writer.write(out);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    static boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private static boolean resource(String value) { return value != null && value.length() <= 256 && value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"); }
    static void requireId(UUID value) { require(value != null && !ZERO.equals(value), "Missing exact receipt or identity UUID"); }
    static void require(boolean condition, String reason) { if (!condition) throw new IllegalArgumentException(reason); }
}
