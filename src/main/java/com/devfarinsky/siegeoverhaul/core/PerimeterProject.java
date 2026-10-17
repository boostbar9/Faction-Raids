package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable, server-only authority for one reviewed whole-territory commission. */
public final class PerimeterProject {
    public static final int FORMAT_VERSION = 1, GATE_FORMAT_VERSION = 2, FEE_VERSION = 1, NEW_PROJECT_PRICE = 64;
    public static final String GATE_EXECUTION_BLOCKER = "Gate-aware perimeter execution awaits live approach verification; no worker assignment or payment is allowed.";
    public enum State { PREPARED_UNPAID, PREPARED_PAID, RUNNING, STAGE_VERIFIED,
        WAITING_FOR_NEXT_STAGE, VERIFYING_COMPLETE, COMPLETE, CANCELED, RECOVERY_BLOCKED }

    public record Header(UUID projectId, long generation, UUID owner, UUID builder, String coreKey,
                         BlockPos originalCore, String faction, int material, String reviewedFingerprint,
                         Set<ChunkPos> territory, int feeVersion, int quotedPrice) {
        public Header {
            Objects.requireNonNull(projectId); Objects.requireNonNull(owner); Objects.requireNonNull(builder);
            Objects.requireNonNull(originalCore); Objects.requireNonNull(territory);
            if (projectId.equals(new UUID(0, 0)) || owner.equals(new UUID(0, 0)) || builder.equals(new UUID(0, 0))
                    || generation < 1 || !bounded(coreKey, 256) || !bounded(faction, 256) || material < 0 || material > 2
                    || !coreKey.equals("team:" + faction) || !digest(reviewedFingerprint) || territory.isEmpty() || territory.size() > PerimeterTerritory.MAX_CHUNKS
                    || feeVersion != 1 || quotedPrice != 64) throw invalid("Invalid perimeter identity or recorded quote");
            originalCore = originalCore.immutable(); territory = Set.copyOf(territory);
            for (ChunkPos chunk : territory) {
                if (chunk == null || (long) chunk.x * 16 < -30_000_000 || (long) chunk.x * 16 + 15 >= 30_000_000
                        || (long) chunk.z * 16 < -30_000_000 || (long) chunk.z * 16 + 15 >= 30_000_000)
                    throw invalid("Invalid reviewed territory coordinates");
            }
            if (!territory.contains(new ChunkPos(originalCore))) throw invalid("Original core is outside reviewed territory");
        }
        public static Header newCommission(UUID projectId, long generation, UUID owner, UUID builder,
                                           String coreKey, BlockPos core, String faction, int material,
                                           String reviewedFingerprint, Set<ChunkPos> territory) {
            return new Header(projectId, generation, owner, builder, coreKey, core, faction, material,
                    reviewedFingerprint, territory, FEE_VERSION, NEW_PROJECT_PRICE);
        }
    }
    public record Stage(int index, UUID areaId, String digest, PerimeterStageLayout.Stage layout) {
        public Set<Long> reservation() { return layout.reservation(); }
    }
    public record PaymentReceipt(UUID projectId, long generation, String manifestHash, int feeVersion,
                                 int quotedPrice, int debited, boolean creative) {}
    public record StageReceipt(UUID projectId, long generation, String manifestHash, int stageIndex,
                               UUID areaId, String stageDigest, int verifiedTargets) {}
    public record Check(UUID projectId, long generation, String manifestHash, long revision, State state, int activeStage) {}

    private final Header header;
    private final PerimeterGateContract gateContract;
    private final List<Integer> gateStageComponents;
    private final PerimeterBlueprint.Plan plan;
    private final PerimeterStageLayout.Layout layout;
    private final Map<Long, BlockState> targets, before, clearanceBefore;
    private final List<Stage> stages;
    private final Set<Long> reservation;
    private final String manifestHash;
    private final State state, recoveryState;
    private final int activeStage;
    private final long revision;
    private final List<StageReceipt> receipts;
    private final PaymentReceipt payment;
    private final String blocker;

    private PerimeterProject(Header header, PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                             Map<Long, BlockState> targets, Map<Long, BlockState> before, Map<Long, BlockState> clearanceBefore,
                             State state, State recoveryState, int activeStage, long revision,
                             List<StageReceipt> receipts, PaymentReceipt payment, String blocker, PerimeterGateContract gateContract) {
        this.gateContract = gateContract;
        this.header = Objects.requireNonNull(header); this.plan = freezePlan(plan); this.layout = Objects.requireNonNull(layout);
        this.targets = freeze(targets); this.before = freeze(before); this.clearanceBefore = freeze(clearanceBefore);
        Set<Long> reserved = new java.util.HashSet<>(this.targets.keySet()); reserved.addAll(this.clearanceBefore.keySet());
        reserved.addAll(observations().keySet());
        if (reserved.size() > PerimeterStageLayout.MAX_RESERVED) throw invalid("Complete gate reservation exceeds the manifest budget");
        this.reservation = Set.copyOf(reserved);
        this.state = Objects.requireNonNull(state); this.recoveryState = recoveryState; this.activeStage = activeStage;
        this.revision = revision; this.receipts = List.copyOf(receipts); this.payment = payment;
        if (blocker == null || blocker.length() > 256) throw invalid("Invalid project blocker");
        this.blocker = blocker;
        validatePlan();
        this.gateStageComponents = gateContract == null ? List.of() : PerimeterGateStages.components(this.plan, this.layout);
        this.manifestHash = calculateHash();
        List<Stage> frozen = new ArrayList<>();
        for (PerimeterStageLayout.Stage part : layout.stages()) {
            String digest = stageDigest(part);
            UUID area = UUID.nameUUIDFromBytes(("siege-perimeter-stage-v1:" + header.projectId() + ":"
                    + header.generation() + ":" + part.index() + ":" + digest).getBytes(StandardCharsets.UTF_8));
            frozen.add(new Stage(part.index(), area, digest, part));
        }
        this.stages = List.copyOf(frozen);
        validateState();
    }

    public static PerimeterProject prepare(Header header, PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                                           Map<Long, BlockState> before, Map<Long, BlockState> clearanceBefore) {
        return prepare(header, plan, layout, before, clearanceBefore, null);
    }

    /** Opt-in data preparation only. Native execution remains blocked until live gate verification is integrated. */
    public static PerimeterProject prepareWithGates(Header header, PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                                                    Map<Long, BlockState> before, Map<Long, BlockState> clearanceBefore,
                                                    PerimeterGateContract gateContract) {
        return prepare(header, plan, layout, before, clearanceBefore, Objects.requireNonNull(gateContract));
    }

    private static PerimeterProject prepare(Header header, PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                                            Map<Long, BlockState> before, Map<Long, BlockState> clearanceBefore,
                                            PerimeterGateContract gateContract) {
        Map<Long, BlockState> targets = new LinkedHashMap<>();
        plan.blocks().forEach((cell, id) -> {
            ResourceLocation name = ResourceLocation.tryParse(id);
            if (name == null || !BuiltInRegistries.BLOCK.containsKey(name)) throw invalid("Unknown target material");
            BlockState target = BuiltInRegistries.BLOCK.get(name).defaultBlockState();
            if (!supported(target)) throw invalid("Unsupported perimeter target material");
            targets.put(cell, target);
        });
        return new PerimeterProject(header, plan, layout, targets, before, clearanceBefore,
                State.PREPARED_UNPAID, null, 0, 0, List.of(), null, "", gateContract);
    }

    static PerimeterProject restore(Header header, PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                                    Map<Long, BlockState> targets, Map<Long, BlockState> before, Map<Long, BlockState> clearance,
                                    State state, State recoveryState, int activeStage, long revision,
                                    List<StageReceipt> receipts, PaymentReceipt payment, String blocker, String expectedHash,
                                    PerimeterGateContract gateContract) {
        PerimeterProject project = new PerimeterProject(header, plan, layout, targets, before, clearance,
                state, recoveryState, activeStage, revision, receipts, payment, blocker, gateContract);
        if (!project.manifestHash.equals(expectedHash)) throw invalid("Changed perimeter manifest hash");
        return project;
    }

    public Header header() { return header; }
    public int formatVersion() { return gateContract == null ? FORMAT_VERSION : GATE_FORMAT_VERSION; }
    public PerimeterGateContract gateContract() { return gateContract; }
    public Map<Long, BlockState> observations() { return gateContract == null ? Map.of() : gateContract.observations(); }
    /** A persisted v2 contract is not live authorization. Remove this barrier only with complete gate runtime checks. */
    public boolean executionSupported() { return gateContract == null; }
    public int gateStageComponent(int stage) {
        if (gateContract == null || stage < 0 || stage >= gateStageComponents.size()) throw invalid("No gate component for this stage");
        return gateStageComponents.get(stage);
    }
    public PerimeterBlueprint.Plan plan() { return plan; }
    public PerimeterStageLayout.Layout layout() { return layout; }
    public Map<Long, BlockState> targets() { return targets; }
    public Map<Long, BlockState> before() { return before; }
    public Map<Long, BlockState> clearanceBefore() { return clearanceBefore; }
    public Set<Long> reservation() { return reservation; }
    public List<Stage> stages() { return stages; }
    public String manifestHash() { return manifestHash; }
    public State state() { return state; }
    public State recoveryState() { return recoveryState; }
    public int activeStage() { return activeStage; }
    public long revision() { return revision; }
    public List<StageReceipt> receipts() { return receipts; }
    public PaymentReceipt payment() { return payment; }
    public String blocker() { return blocker; }
    public int completedTargetCount() { return receipts.stream().mapToInt(StageReceipt::verifiedTargets).sum(); }
    public Check check() { return new Check(header.projectId(), header.generation(), manifestHash, revision, state, activeStage); }
    public Stage active() { return activeStage < stages.size() ? stages.get(activeStage) : null; }
    public CompoundTag save() { return PerimeterProjectCodec.save(this); }
    public static PerimeterProject load(CompoundTag tag) { return PerimeterProjectCodec.load(tag); }

    void expect(Check expected) { if (!check().equals(expected)) throw invalid("Stale project transition"); }
    PerimeterProject paid(boolean creative) {
        if (state != State.PREPARED_UNPAID || payment != null) throw invalid("Project is not awaiting its one payment");
        PaymentReceipt receipt = new PaymentReceipt(header.projectId(), header.generation(), manifestHash,
                header.feeVersion(), header.quotedPrice(), creative ? 0 : header.quotedPrice(), creative);
        return changed(State.PREPARED_PAID, null, activeStage, receipts, receipt, "");
    }
    public PerimeterProject activate(Check expected) {
        expect(expected);
        if (state != State.PREPARED_PAID && state != State.WAITING_FOR_NEXT_STAGE) throw invalid("Stage is not ready for reconciled activation");
        return changed(State.RUNNING, null, activeStage, receipts, payment, "");
    }
    /** Caller supplies this only after proving every exact target and native queue completion. */
    public StageReceipt expectedStageReceipt() {
        if (active() == null) throw invalid("No stage remains");
        return new StageReceipt(header.projectId(), header.generation(), manifestHash, activeStage,
                active().areaId(), active().digest(), active().layout().targets().size());
    }
    public PerimeterProject verifyStage(Check expected, StageReceipt receipt) {
        expect(expected);
        if (receipt != null && receipt.stageIndex() >= 0 && receipt.stageIndex() < receipts.size()) {
            if (!receipts.get(receipt.stageIndex()).equals(receipt)) throw invalid("Conflicting stage verification receipt");
            return this;
        }
        if (state != State.RUNNING || !expectedStageReceipt().equals(receipt)) throw invalid("Stage completion is not proven for the active lease");
        List<StageReceipt> next = new ArrayList<>(receipts); next.add(receipt);
        return changed(State.STAGE_VERIFIED, null, activeStage, next, payment, "");
    }
    /** Call after exact old worker detachment and child-lease retirement are durably reconciled. */
    public PerimeterProject retireVerifiedStage(Check expected) {
        expect(expected);
        if (state != State.STAGE_VERIFIED) throw invalid("An unverified stage cannot retire or advance");
        int next = activeStage + 1;
        return changed(next == stages.size() ? State.VERIFYING_COMPLETE : State.WAITING_FOR_NEXT_STAGE,
                null, next, receipts, payment, "");
    }
    /** External world/ledger verification is mandatory; receipts alone are not completion. */
    public PerimeterProject complete(Check expected, String verifiedWholeManifestHash) {
        expect(expected);
        if (state != State.VERIFYING_COMPLETE || !manifestHash.equals(verifiedWholeManifestHash)) throw invalid("Whole perimeter completion is not verified");
        return changed(State.COMPLETE, null, activeStage, receipts, payment, "");
    }
    public PerimeterProject cancel(Check expected, String reason) {
        expect(expected);
        if (state == State.CANCELED) return this;
        if (state == State.COMPLETE) throw invalid("Completed project cannot be canceled");
        return changed(State.CANCELED, null, activeStage, receipts, payment, reason);
    }
    public PerimeterProject waitFor(Check expected, String reason) {
        expect(expected);
        if (state == State.CANCELED || state == State.COMPLETE) throw invalid("Terminal projects cannot resume");
        if (blocker.equals(reason)) return this;
        return changed(state, recoveryState, activeStage, receipts, payment, reason);
    }
    public PerimeterProject blockRecovery(Check expected, String reason) {
        expect(expected);
        if (state == State.CANCELED || state == State.COMPLETE) return this;
        return changed(State.RECOVERY_BLOCKED, state == State.RECOVERY_BLOCKED ? recoveryState : state,
                activeStage, receipts, payment, reason);
    }
    /** Runtime must reconcile all files first. This never recreates a marker or charges a fee. */
    public PerimeterProject reconciled(Check expected, State verifiedPriorState) {
        expect(expected);
        if (state != State.RECOVERY_BLOCKED || recoveryState != verifiedPriorState) throw invalid("Unknown cross-file recovery state");
        return changed(recoveryState, null, activeStage, receipts, payment, "");
    }

    private PerimeterProject changed(State next, State recovery, int stage, List<StageReceipt> verified,
                                     PaymentReceipt paid, String reason) {
        if (revision == Long.MAX_VALUE) throw invalid("Project revision exhausted");
        return new PerimeterProject(this, next, recovery, stage, verified, paid, reason);
    }
    /** Progress copies share only deeply frozen geometry; they never rebuild or rehash it. */
    private PerimeterProject(PerimeterProject original, State state, State recoveryState, int activeStage,
                             List<StageReceipt> receipts, PaymentReceipt payment, String blocker) {
        this.gateContract = original.gateContract; this.gateStageComponents = original.gateStageComponents;
        this.header = original.header; this.plan = original.plan; this.layout = original.layout;
        this.targets = original.targets; this.before = original.before; this.clearanceBefore = original.clearanceBefore;
        this.stages = original.stages; this.reservation = original.reservation; this.manifestHash = original.manifestHash;
        this.state = Objects.requireNonNull(state); this.recoveryState = recoveryState; this.activeStage = activeStage;
        this.receipts = List.copyOf(receipts); this.payment = payment; this.revision = original.revision + 1;
        if (blocker == null || blocker.length() > 256) throw invalid("Invalid project blocker");
        this.blocker = blocker; validateState();
    }

    /** Store-level enforcement: a valid snapshot is not automatically a valid successor of this snapshot. */
    void validateSuccessor(PerimeterProject next) {
        if (!header.equals(next.header) || !manifestHash.equals(next.manifestHash) || !Objects.equals(payment, next.payment)
                || revision == Long.MAX_VALUE || next.revision != revision + 1)
            throw invalid("Conflicting project progress replacement");
        if (state == State.CANCELED || state == State.COMPLETE) throw invalid("Terminal perimeter evidence cannot be rewritten");
        boolean sameReceipts = receipts.equals(next.receipts), sameIndex = activeStage == next.activeStage;
        if (next.state == state) {
            if (!sameReceipts || !sameIndex || recoveryState != next.recoveryState) throw invalid("A pause cannot rewrite project progress");
            return;
        }
        if (next.state == State.CANCELED) {
            if (!sameReceipts || !sameIndex || next.recoveryState != null) throw invalid("Cancellation cannot rewrite stage receipts");
            return;
        }
        if (next.state == State.RECOVERY_BLOCKED) {
            if (!sameReceipts || !sameIndex || next.recoveryState != state) throw invalid("Recovery must preserve the exact interrupted state");
            return;
        }
        if (state == State.RECOVERY_BLOCKED) {
            if (next.state != recoveryState || !sameReceipts || !sameIndex || next.recoveryState != null)
                throw invalid("Recovery cannot skip or rewind the interrupted stage");
            return;
        }
        if (next.recoveryState != null) throw invalid("Unexpected recovery state");
        switch (state) {
            case PREPARED_PAID, WAITING_FOR_NEXT_STAGE -> {
                if (next.state != State.RUNNING || !sameReceipts || !sameIndex) throw invalid("Invalid stage activation successor");
            }
            case RUNNING -> {
                if (next.state != State.STAGE_VERIFIED || !sameIndex || next.receipts.size() != receipts.size() + 1
                        || !next.receipts.subList(0, receipts.size()).equals(receipts)
                        || !next.receipts.get(receipts.size()).equals(expectedStageReceipt())) throw invalid("Invalid stage verification successor");
            }
            case STAGE_VERIFIED -> {
                State expected = activeStage + 1 == stages.size() ? State.VERIFYING_COMPLETE : State.WAITING_FOR_NEXT_STAGE;
                if (next.state != expected || !sameReceipts || next.activeStage != activeStage + 1) throw invalid("Invalid verified-stage retirement successor");
            }
            case VERIFYING_COMPLETE -> {
                if (next.state != State.COMPLETE || !sameReceipts || !sameIndex) throw invalid("Invalid whole-project completion successor");
            }
            default -> throw invalid("Invalid perimeter state successor");
        }
    }
    boolean sameSnapshot(PerimeterProject other) {
        return header.equals(other.header) && manifestHash.equals(other.manifestHash) && revision == other.revision
                && state == other.state && recoveryState == other.recoveryState && activeStage == other.activeStage
                && receipts.equals(other.receipts) && Objects.equals(payment, other.payment) && blocker.equals(other.blocker);
    }
    private static PerimeterBlueprint.Plan freezePlan(PerimeterBlueprint.Plan source) {
        Objects.requireNonNull(source);
        List<PerimeterBlueprint.Column> columns = source.columns().stream().map(c -> new PerimeterBlueprint.Column(
                c.base().immutable(), c.supportDepth(), c.inwardDistance(), c.componentId())).toList();
        List<PerimeterBlueprint.Run> runs = source.runs().stream().map(r -> new PerimeterBlueprint.Run(r.id(), r.componentId(),
                r.start().immutable(), r.end().immutable(), r.direction())).toList();
        List<PerimeterBlueprint.Connection> connections = source.connections().stream().map(c -> new PerimeterBlueprint.Connection(
                c.runId(), c.componentId(), c.center().immutable(), c.facing(), c.width())).toList();
        List<PerimeterBlueprint.Problem> problems = source.problems().stream().map(p -> new PerimeterBlueprint.Problem(
                p.code(), p.position() == null ? null : p.position().immutable(), p.message())).toList();
        return new PerimeterBlueprint.Plan(source.blocks(), columns, source.clearance(), source.min().immutable(), source.max().immutable(),
                runs, connections, source.materialCounts(), problems);
    }

    private void validatePlan() {
        PerimeterStageLayout.validate(plan, layout);
        if (!targets.keySet().equals(plan.blocks().keySet()) || !before.keySet().equals(targets.keySet())
                || !clearanceBefore.keySet().equals(plan.clearance())) throw invalid("Missing original target or clearance states");
        for (var entry : targets.entrySet()) if (!supported(entry.getValue())
                || !String.valueOf(BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock())).equals(plan.blocks().get(entry.getKey()))
                || !entry.getValue().equals(entry.getValue().getBlock().defaultBlockState())) throw invalid("Changed target state");
        // Native targets/clearance always remain inside the accepted claim. Observations have no mutation authority.
        Set<Long> nativeCells = new java.util.HashSet<>(targets.keySet()); nativeCells.addAll(clearanceBefore.keySet());
        for (long cell : nativeCells) {
            BlockPos pos = BlockPos.of(cell);
            if (!header.territory().contains(new ChunkPos(pos)) || pos.getY() < -2048 || pos.getY() > 2047)
                throw invalid("Cell outside original reviewed territory or supported height");
        }
        if (gateContract != null) {
            if (!Collections.disjoint(nativeCells, observations().keySet())) throw invalid("Read-only gate observations overlap native cells");
            gateContract.validateAgainst(header.territory(), plan);
        }
    }
    private void validateState() {
        if (revision < 0 || activeStage < 0 || activeStage > stages.size() || receipts.size() > stages.size()) throw invalid("Invalid project progress");
        for (int i = 0; i < receipts.size(); i++) {
            Stage stage = stages.get(i);
            StageReceipt exact = new StageReceipt(header.projectId(), header.generation(), manifestHash, i,
                    stage.areaId(), stage.digest(), stage.layout().targets().size());
            if (!exact.equals(receipts.get(i))) throw invalid("Stage receipts are not an exact verified prefix");
        }
        if (payment != null && !new PaymentReceipt(header.projectId(), header.generation(), manifestHash,
                header.feeVersion(), header.quotedPrice(), payment.creative() ? 0 : header.quotedPrice(), payment.creative()).equals(payment))
            throw invalid("Conflicting perimeter payment receipt");
        State effective = state == State.RECOVERY_BLOCKED ? recoveryState : state;
        if (effective == null || effective == State.RECOVERY_BLOCKED || state != State.RECOVERY_BLOCKED && recoveryState != null
                || state == State.RECOVERY_BLOCKED && (effective == State.CANCELED || effective == State.COMPLETE))
            throw invalid("Invalid recovery state");
        if (effective == State.CANCELED) {
            if (receipts.size() < activeStage || receipts.size() > activeStage + 1 || !receipts.isEmpty() && payment == null)
                throw invalid("Invalid canceled project receipts");
            return;
        }
        if ((effective == State.PREPARED_UNPAID) != (payment == null)) throw invalid("Missing or unexpected payment receipt");
        switch (effective) {
            case PREPARED_UNPAID, PREPARED_PAID -> { if (activeStage != 0 || !receipts.isEmpty()) throw invalid("Invalid prepared project"); }
            case RUNNING -> { if (activeStage >= stages.size() || receipts.size() != activeStage) throw invalid("Invalid running stage"); }
            case STAGE_VERIFIED -> { if (activeStage >= stages.size() || receipts.size() != activeStage + 1) throw invalid("Missing active stage receipt"); }
            case WAITING_FOR_NEXT_STAGE -> { if (activeStage < 1 || activeStage >= stages.size() || receipts.size() != activeStage) throw invalid("Invalid next-stage position"); }
            case VERIFYING_COMPLETE, COMPLETE -> { if (activeStage != stages.size() || receipts.size() != stages.size()) throw invalid("Incomplete global receipt prefix"); }
            default -> throw invalid("Unsupported project state");
        }
    }
    private String calculateHash() {
        HashBuilder value = new HashBuilder().append(gateContract == null ? "perimeter-project-v1\n" : "perimeter-project-v2\n");
        value.append(header.projectId()).append('\n').append(header.generation()).append('\n').append(header.owner()).append('\n')
                .append(header.builder()).append('\n').append(header.coreKey()).append('\n').append(header.originalCore().asLong()).append('\n')
                .append(header.faction()).append('\n').append(header.material()).append('\n').append(header.reviewedFingerprint()).append('\n')
                .append(header.feeVersion()).append(':').append(header.quotedPrice()).append('\n');
        header.territory().stream().map(ChunkPos::toLong).sorted().forEach(p -> value.append("claim:").append(p).append('\n'));
        plan.columns().stream().sorted(java.util.Comparator.comparingLong(c -> c.base().asLong())).forEach(c -> value.append("column:")
                .append(c.base().asLong()).append(':').append(c.supportDepth()).append(':').append(c.inwardDistance()).append(':').append(c.componentId()).append('\n'));
        appendStates(value, "target", targets); appendStates(value, "before", before); appendStates(value, "clear", clearanceBefore);
        value.append(layout.digest());
        if (gateContract != null) value.append("\ngates:").append(gateContract.digest());
        return value.finish();
    }
    private String stageDigest(PerimeterStageLayout.Stage stage) {
        HashBuilder value = new HashBuilder().append("perimeter-stage-contract-v1\n").append(manifestHash).append('\n').append(stage.digest()).append('\n');
        for (long cell : stage.targets().keySet().stream().sorted().toList()) value.append(cell).append(':')
                .append(stateKey(targets.get(cell))).append(':').append(stateKey(before.get(cell))).append('\n');
        for (long cell : stage.clearance().stream().sorted().toList()) value.append("clear:").append(cell).append(':').append(stateKey(clearanceBefore.get(cell))).append('\n');
        return value.finish();
    }
    private static void appendStates(HashBuilder out, String label, Map<Long, BlockState> states) {
        new TreeMap<>(states).forEach((cell, state) -> out.append(label).append(':').append(cell).append(':').append(stateKey(state)).append('\n'));
    }
    /** Stream bounded records into the hash instead of allocating a whole expanded manifest string. */
    private static final class HashBuilder {
        private final java.security.MessageDigest digest;
        HashBuilder() {
            try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        HashBuilder append(Object value) { digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8)); return this; }
        String finish() { return java.util.HexFormat.of().formatHex(digest.digest()); }
    }
    static String stateKey(BlockState state) {
        CompoundTag encoded = NbtUtils.writeBlockState(state);
        StringBuilder value = new StringBuilder(encoded.getString("Name"));
        CompoundTag properties = encoded.getCompound("Properties");
        properties.getAllKeys().stream().sorted().forEach(k -> value.append('[').append(k).append('=').append(properties.getString(k)).append(']'));
        return value.toString();
    }
    static boolean supported(BlockState state) { return state != null && (state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.DIRT)); }
    private static Map<Long, BlockState> freeze(Map<Long, BlockState> values) {
        Objects.requireNonNull(values);
        if (values.size() > PerimeterStageLayout.MAX_RESERVED || values.values().stream().anyMatch(Objects::isNull)) throw invalid("Invalid original block states");
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    static boolean bounded(String value, int max) { return value != null && !value.isBlank() && value.length() <= max && value.indexOf('\n') < 0 && value.indexOf('\r') < 0; }
    static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
}
