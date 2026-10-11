package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only Case C evidence, never a work permit or a production recovery operation.
 * Both transfer eligibility and the observed pending-load census must use this same proof.
 * The caller still owns recorded HIGHEST/LOWEST custody, raw cargo equality, exact per-join
 * rebind deltas, registration, ticket ordering and five naturally grounded END observations.
 */
final class NativeTerminalHelperProof {
    private NativeTerminalHelperProof() {}

    // BEGIN dependency-free decision: executed directly by the focused Java harness.
    static final String WHOLE = "SiegeWholePerimeter", CHILD = "SiegeProtectedAreaReceipt",
            CHILD_GENERATION = "SiegeProtectedLedgerGeneration", NATIVE_AREA = "SiegePlayerFortificationAreaId",
            NATIVE_OWNER = "SiegePlayerFortificationOwner", HAND_PENDING = "SiegeProtectedHandRefreshPending",
            REBINDS = "SiegeProtectedHandRebinds";
    private static final Set<String> DETACHED_KEYS = Set.of(WHOLE, CHILD, CHILD_GENERATION, NATIVE_AREA,
            NATIVE_OWNER, "SiegePlayerFortificationPos", "SiegePlayerFortificationMisses", "SiegeWallGroundRecovery", "SiegeConstructionPause");

    enum State { STALE_UNRESOLVED, DETACHED_RESOLVED, REJECTED }
    record Proof(State state, String reason, Map<String, Object> evidence) {
        Proof { evidence = Map.copyOf(evidence); }
        boolean valid() { return state != State.REJECTED; }
    }
    record Identity(UUID project, long generation, String hash, String core, String faction, UUID owner,
                    UUID coordinator, String state, int activeStage, int verifiedStages, int targets,
                    int claimChunks, int feeVersion, int price, String payment, List<String> stages, UUID ledger) {
        Identity { stages = List.copyOf(stages); }
    }
    record Hand(UUID receipt, UUID builder, UUID owner, UUID area, UUID generation) {}
    record Selector(UUID project, long generation, String hash, String core, UUID ledger) {}
    record Association(Selector selector, UUID child, UUID generation, UUID nativeArea, UUID nativeOwner) {
        boolean original(Identity expected, UUID area) {
            return new Selector(expected.project(), expected.generation(), expected.hash(), expected.core(), expected.ledger()).equals(selector)
                    && area.equals(child) && expected.ledger().equals(generation)
                    && area.equals(nativeArea) && expected.owner().equals(nativeOwner);
        }
        boolean detached() {
            return selector == null && child == null && generation == null && nativeArea == null && nativeOwner == null;
        }
    }
    record Member(int admitted, boolean active, boolean ready, boolean withdrawn, int retired,
                  boolean suspended, int rejoin, Set<Integer> grants, int legacyEnd, boolean resolved, boolean destroyed) {
        Member { grants = Set.copyOf(grants); }
        boolean common() {
            return admitted == 0 && !active && ready && !withdrawn && rejoin == -1
                    && grants.equals(Set.of(0)) && legacyEnd == -1 && !destroyed;
        }
        boolean stale() { return common() && suspended && retired == -1 && !resolved; }
        boolean detached() { return common() && !suspended && retired == 0 && resolved; }
    }
    record Operations(int beforeItemSlot, boolean activeUse, boolean review, boolean requests,
                      boolean cleanup, boolean storage, boolean ordinary, boolean finitePose,
                      boolean equalHandValue, boolean canonicalCounts, boolean thirdSlotAlias,
                      boolean mainIdentity, boolean pending) {
        boolean closed() {
            return beforeItemSlot == -1 && !activeUse && !review && !requests && !cleanup && !storage
                    && ordinary && finitePose && equalHandValue && canonicalCounts && !thirdSlotAlias
                    && (pending || mainIdentity);
        }
    }
    record Actor(UUID helper, UUID owner, Hand hand, Association association, UUID currentArea,
                 boolean currentAreaExact, Operations operations, Map<String, String> persistent, int rebinds) {
        Actor { persistent = Map.copyOf(persistent); }
    }
    record Frozen(Hand hand, Association association, Map<String, String> persistent, int rebinds, boolean closed) {
        Frozen { persistent = Map.copyOf(persistent); }
    }
    record History(boolean known, boolean reservationsAbsent, boolean destructionAbsent, Set<UUID> cleanupAreas,
                   UUID admissionArea, UUID unresolved, Member member) {
        History { cleanupAreas = Set.copyOf(cleanupAreas); }
    }
    record Facts(Identity expected, Identity terminal, UUID helper, UUID owner, UUID originalLedger,
                 UUID currentLedger, UUID firstArea, Hand originalHand, Hand ledgerHand,
                 Actor actor, Frozen frozen, History history) {}

    static Proof decide(Facts f) {
        if (f == null || f.expected() == null || f.terminal() == null || f.actor() == null
                || f.frozen() == null || f.history() == null || f.originalHand() == null)
            return rejected("missing-evidence");
        if (!f.expected().equals(f.terminal()) || !"CANCELED".equals(f.terminal().state())
                || f.terminal().activeStage() != 0 || f.terminal().verifiedStages() != 0
                || f.terminal().payment().isEmpty()) return rejected("terminal-identity");
        if (!Objects.equals(f.helper(), f.actor().helper()) || !Objects.equals(f.owner(), f.actor().owner())
                || !Objects.equals(f.owner(), f.expected().owner()) || Objects.equals(f.helper(), f.expected().coordinator()))
            return rejected("native-helper-owner");
        if (!Objects.equals(f.originalLedger(), f.currentLedger()) || !Objects.equals(f.originalLedger(), f.expected().ledger()))
            return rejected("ledger-generation");
        Hand hand = f.originalHand();
        if (!Objects.equals(hand.builder(), f.helper()) || !Objects.equals(hand.owner(), f.owner())
                || !Objects.equals(hand.area(), f.firstArea()) || !Objects.equals(hand.generation(), f.originalLedger())
                || !hand.equals(f.actor().hand()) || !hand.equals(f.ledgerHand()) || !hand.equals(f.frozen().hand()))
            return rejected("original-hand-receipt");
        History history = f.history();
        if (!history.known() || !history.reservationsAbsent() || !history.destructionAbsent() || history.member() == null
                || !history.cleanupAreas().equals(Set.of(f.firstArea())) || !f.firstArea().equals(history.admissionArea()))
            return rejected("terminal-history-scope");
        if (!f.frozen().closed() || !f.frozen().association().original(f.expected(), f.firstArea()))
            return rejected("finalized-original-provenance");
        if (!f.actor().operations().closed()) return rejected("canonical-closed-operations");
        State state;
        if (history.member().stale() && f.expected().project().equals(history.unresolved())
                && f.actor().association().original(f.expected(), f.firstArea())
                && (f.actor().currentArea() == null || f.firstArea().equals(f.actor().currentArea()) && f.actor().currentAreaExact()))
            state = State.STALE_UNRESOLVED;
        else if (history.member().detached() && history.unresolved() == null && f.actor().association().detached()
                && f.actor().currentArea() == null)
            state = State.DETACHED_RESOLVED;
        else return rejected("terminal-state-channels");
        if (!persistentMatches(f, state)) return rejected("frozen-persistent-delta");
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("state", state.name()); evidence.put("helper", f.helper().toString()); evidence.put("owner", f.owner().toString());
        evidence.put("project", f.expected().project().toString()); evidence.put("generation", f.expected().generation());
        evidence.put("manifestHash", f.expected().hash()); evidence.put("ledgerGeneration", f.originalLedger().toString());
        evidence.put("originalHandReceipt", hand.receipt().toString()); evidence.put("handArea", f.firstArea().toString());
        evidence.put("cleanupAreas", List.of(f.firstArea().toString())); evidence.put("resolved", history.member().resolved());
        evidence.put("pendingHand", f.actor().operations().pending()); evidence.put("rebinds", f.actor().rebinds());
        evidence.put("core", f.expected().core()); evidence.put("coordinator", f.expected().coordinator().toString());
        evidence.put("terminal", Map.of("state", f.terminal().state(), "activeStage", f.terminal().activeStage(),
                "verifiedStages", f.terminal().verifiedStages(), "targetCount", f.terminal().targets(),
                "claimChunks", f.terminal().claimChunks(), "feeVersion", f.terminal().feeVersion(),
                "quotedPrice", f.terminal().price(), "payment", f.terminal().payment(), "stages", f.terminal().stages()));
        Member member = history.member();
        var memberEvidence = new LinkedHashMap<String, Object>();
        memberEvidence.put("admittedStage", member.admitted()); memberEvidence.put("active", member.active());
        memberEvidence.put("ready", member.ready()); memberEvidence.put("withdrawn", member.withdrawn());
        memberEvidence.put("retiredStage", member.retired()); memberEvidence.put("suspended", member.suspended());
        memberEvidence.put("rejoinStage", member.rejoin()); memberEvidence.put("grantedStages", List.of(0));
        memberEvidence.put("legacyEnd", member.legacyEnd()); memberEvidence.put("resolved", member.resolved());
        memberEvidence.put("destroyed", member.destroyed()); evidence.put("member", Map.copyOf(memberEvidence));
        evidence.put("unresolvedProject", history.unresolved() == null ? "" : history.unresolved().toString());
        evidence.put("admissionArea", history.admissionArea().toString()); evidence.put("reservationsAbsent", true);
        evidence.put("destructionAbsent", true); evidence.put("originalHandExact", true); evidence.put("frozenPersistentExact", true);
        evidence.put("association", Map.of("wholeSelector", f.actor().association().selector() != null,
                "protectedChild", f.actor().association().child() != null, "protectedGeneration", f.actor().association().generation() != null,
                "nativeArea", f.actor().association().nativeArea() != null, "nativeOwner", f.actor().association().nativeOwner() != null,
                "currentArea", f.actor().currentArea() == null ? "" : f.actor().currentArea().toString()));
        return new Proof(state, "", evidence);
    }

    private static boolean persistentMatches(Facts f, State state) {
        int before = f.frozen().rebinds(), after = f.actor().rebinds();
        // Only the disk join and final native return can bind the mirror after finalization.
        // Exact split-specific increments are checked against each recorded load by the caller.
        if (before < 0 || after < before || (long) after - before > (state == State.STALE_UNRESOLVED ? 1 : 2)) return false;
        var expected = new LinkedHashMap<>(f.frozen().persistent());
        if (state == State.DETACHED_RESOLVED) DETACHED_KEYS.forEach(expected::remove);
        else if (after > before && !f.actor().operations().pending()) {
            // The production hand-restoration branch removes its pause status after success.
            // Raw stale admissions retain it; no arbitrary status edits/removals are accepted.
            expected.remove("SiegeConstructionPause");
        }
        if (f.actor().operations().pending()) expected.put(HAND_PENDING, "1b");
        if (after != before) expected.put(REBINDS, Integer.toString(after));
        return expected.equals(f.actor().persistent());
    }
    private static Proof rejected(String reason) { return new Proof(State.REJECTED, reason, Map.of("reason", reason)); }
    // END dependency-free decision.

    /** HAND_PENDING is allowed here only for the caller's separately recorded native-load mirror. */
    static Proof inspect(BuilderEntity worker, PerimeterProject accepted, PerimeterTerminalReceipt terminal,
                         ConstructionEditLedger ledger, UUID helper, UUID owner, UUID ledgerGeneration,
                         ProtectedBuilderHandLifecycle.Receipt originalHand, NativeActiveHelperTransit.Inventory finalizedOffline) {
        try {
            if (worker == null || accepted == null || terminal == null || ledger == null || originalHand == null
                    || finalizedOffline == null || helper == null || owner == null || ledgerGeneration == null)
                return rejected("missing-native-evidence");
            if (worker.getServer() == null || !worker.getServer().isSameThread()) return rejected("not-server-thread");
            if (accepted.state() != PerimeterProject.State.RUNNING || accepted.activeStage() != 0
                    || accepted.active() == null || !accepted.receipts().isEmpty() || accepted.payment() == null)
                return rejected("not-original-first-stage");
            UUID area = accepted.stages().get(0).areaId();
            var data = worker.getPersistentData().copy();
            var frozen = finalizedOffline.persistent(); // Deep-copy accessor; never retained actor state.
            var expected = identity(accepted, ledgerGeneration);
            var current = worker.currentBuildArea;
            boolean currentExact = current == null || current instanceof ProtectedBuildArea
                    && area.equals(current.getUUID()) && owner.equals(WorkersBridge.readOwner(current))
                    && PerimeterProjectAuthority.read(current.getPersistentData()).equals(new PerimeterProjectAuthority.Scope(
                            accepted.header().projectId(), accepted.header().generation(), accepted.manifestHash(),
                            accepted.header().coreKey(), 0, accepted.stages().get(0).digest()));
            var actor = new Actor(worker.getUUID(), worker.getOwnerUUID(), hand(readHand(data)), association(data),
                    current == null ? null : current.getUUID(), currentExact, operations(worker, data), persistent(data), rebinds(data));
            var original = new Frozen(hand(readHand(frozen)), association(frozen), persistent(frozen), rebinds(frozen),
                    finalizedOffline.beforeItemSlot() == -1 && !frozen.contains(HAND_PENDING)
                            && !frozen.contains("SiegeProtectedHandReviewRequired") && !frozen.contains(ProtectedInventoryCleanup.KEY));
            var history = new History(ledger.knownTerminalCrewMember(terminal, helper),
                    !ledger.contains(terminal.projectId()) && accepted.stages().stream().noneMatch(stage -> ledger.contains(stage.areaId())),
                    ledger.terminalCrewBuilderDestructionReceipt(terminal, helper) == null,
                    ledger.workerCleanupAreas(terminal, helper), ledger.crewAdmissionArea(terminal, helper),
                    ledger.unresolvedCrewProject(helper), member(ledger.save(new CompoundTag()), terminal, helper, ledgerGeneration));
            if (!ledger.sameGeneration(ledgerGeneration)) return rejected("invalid-ledger");
            return decide(new Facts(expected, identity(terminal), helper, owner, ledgerGeneration, ledger.generation(), area,
                    hand(originalHand), hand(ledger.handLifecycle(helper)), actor, original, history));
        } catch (RuntimeException | LinkageError unavailable) {
            // Bounded diagnostic: no raw actor NBT, recovery call, repair or production cache lookup.
            return rejected("unreadable-native-provenance:" + unavailable.getClass().getSimpleName());
        }
    }

    private static Identity identity(PerimeterProject project, UUID generation) {
        var h = project.header();
        return new Identity(h.projectId(), h.generation(), project.manifestHash(), h.coreKey(), h.faction(), h.owner(), h.builder(),
                "CANCELED", 0, 0, project.targets().size(), h.territory().size(), h.feeVersion(), h.quotedPrice(),
                project.payment().toString(), project.stages().stream().map(stage -> stage.index() + ":" + stage.areaId() + ":"
                        + stage.digest() + ":" + stage.layout().targets().size()).toList(), generation);
    }
    private static Identity identity(PerimeterTerminalReceipt terminal) {
        return new Identity(terminal.projectId(), terminal.generation(), terminal.manifestHash(), terminal.coreKey(), terminal.faction(),
                terminal.owner(), terminal.builder(), terminal.state().name(), terminal.activeStage(), terminal.verifiedStages(),
                terminal.totalTargetCount(), terminal.claimChunkCount(), terminal.feeVersion(), terminal.quotedPrice(),
                terminal.payment() == null ? "" : terminal.payment().toString(), terminal.stages().stream().map(stage -> stage.index() + ":"
                        + stage.areaId() + ":" + stage.digest() + ":" + stage.targetCount()).toList(), terminal.cleanup().ledgerGeneration());
    }
    private static Hand hand(ProtectedBuilderHandLifecycle.Receipt receipt) {
        return receipt == null ? null : new Hand(receipt.receipt(), receipt.builder(), receipt.owner(), receipt.area(), receipt.generation());
    }
    private static ProtectedBuilderHandLifecycle.Receipt readHand(CompoundTag data) {
        if (!data.contains(ProtectedBuilderHandLifecycle.KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing hand");
        return ProtectedBuilderHandLifecycle.read(data.getCompound(ProtectedBuilderHandLifecycle.KEY));
    }
    private static Association association(CompoundTag data) {
        Selector selector = null;
        if (data.contains(WHOLE)) {
            var link = PerimeterProjectLink.read(data);
            selector = new Selector(link.id(), link.generation(), link.hash(), link.core(), PerimeterProjectLink.ledgerGeneration(data));
        }
        return new Association(selector, optionalUuid(data, CHILD), optionalUuid(data, CHILD_GENERATION),
                optionalUuid(data, NATIVE_AREA), optionalUuid(data, NATIVE_OWNER));
    }
    private static UUID optionalUuid(CompoundTag data, String key) {
        if (!data.contains(key)) return null;
        if (!data.hasUUID(key)) throw new IllegalArgumentException("Malformed association");
        return data.getUUID(key);
    }
    private static int rebinds(CompoundTag data) {
        if (!data.contains(REBINDS)) return 0;
        if (!data.contains(REBINDS, Tag.TAG_INT) || data.getInt(REBINDS) < 0) throw new IllegalArgumentException("Malformed rebinds");
        return data.getInt(REBINDS);
    }
    private static Map<String, String> persistent(CompoundTag data) {
        if (data.getAllKeys().size() > 128) throw new IllegalArgumentException("Unbounded actor data");
        if (data.contains(HAND_PENDING) && (!data.contains(HAND_PENDING, Tag.TAG_BYTE) || data.getByte(HAND_PENDING) != 1))
            throw new IllegalArgumentException("Noncanonical pending flag");
        var result = new LinkedHashMap<String, String>();
        for (String key : data.getAllKeys()) result.put(key, Objects.requireNonNull(data.get(key)).toString());
        return Map.copyOf(result);
    }
    private static Operations operations(BuilderEntity worker, CompoundTag data) {
        var inventory = worker.getInventory();
        if (inventory == null || inventory.getContainerSize() < 6 || inventory.getContainerSize() > 128
                || worker.goalSelector == null || worker.goalSelector.getAvailableGoals().size() > 128)
            throw new IllegalArgumentException("Native inventory/goals unavailable");
        var slot = inventory.getItem(5); var main = worker.getMainHandItem();
        boolean canonical = true, thirdAlias = false;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index);
            canonical &= stack != null && stack.getCount() >= 0
                    && stack.getCount() <= Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize());
            if (index != 5 && !slot.isEmpty()) thirdAlias |= stack == slot || stack == main;
        }
        var position = worker.position(); var velocity = worker.getDeltaMovement();
        boolean finite = Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                && Float.isFinite(worker.getXRot()) && Float.isFinite(worker.getYRot()) && Float.isFinite(worker.fallDistance);
        return new Operations(worker.getBeforeItemSlot(), ProtectedBuilderHandMirror.activeUse(worker),
                data.contains("SiegeProtectedHandReviewRequired"), worker.neededItems == null || !worker.neededItems.isEmpty(),
                !ProtectedInventoryCleanup.read(data).isEmpty() || data.contains(ProtectedInventoryCleanup.KEY),
                worker.forcedDeposit || ProtectedStorageAccess.runningProblem(worker) != null,
                !worker.isNoAi() && worker.getFollowState() == 6 && !worker.isFleeing,
                finite, ProtectedBuilderHandMirror.sameValue(slot, main), canonical, thirdAlias, main == slot,
                ProtectedBuilderHandMirror.pending(data));
    }

    /** Saved, versioned crew facts only. No new production observer or history-sealing write. */
    private static Member member(CompoundTag ledger, PerimeterTerminalReceipt terminal, UUID helper, UUID generation) {
        if (!ledger.contains("BuilderCrew", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing crew");
        var crew = ledger.getCompound("BuilderCrew");
        if (!crew.getAllKeys().equals(Set.of("Version", "LedgerGeneration", "Projects"))
                || !crew.contains("Version", Tag.TAG_INT) || crew.getInt("Version") != 3
                || !generation.equals(optionalUuid(crew, "LedgerGeneration"))) throw new IllegalArgumentException("Crew schema");
        Member found = null;
        for (Tag raw : compounds(crew, "Projects", 64)) {
            var project = (CompoundTag) raw;
            if (!project.getAllKeys().equals(Set.of("Project", "Generation", "Hash", "Original", "Members")))
                throw new IllegalArgumentException("Project history schema");
            if (!terminal.projectId().equals(optionalUuid(project, "Project"))) continue;
            if (!project.contains("Generation", Tag.TAG_LONG) || project.getLong("Generation") != terminal.generation()
                    || !project.contains("Hash", Tag.TAG_STRING) || !terminal.manifestHash().equals(project.getString("Hash"))
                    || !terminal.builder().equals(optionalUuid(project, "Original"))) throw new IllegalArgumentException("Crew identity");
            var members = compounds(project, "Members", 64);
            if (found != null || members.size() != 1) throw new IllegalArgumentException("Repeated or new crew admission");
            var saved = members.getCompound(0);
            if (!helper.equals(optionalUuid(saved, "Worker")) || !saved.getAllKeys().equals(Set.of("Worker", "Active", "Ready",
                    "Withdrawn", "RetiredStage", "AdmittedStage", "Suspended", "RejoinStage", "GrantedStages", "LegacyEnd", "Resolved")))
                throw new IllegalArgumentException("Changed helper or destruction history");
            if (!saved.contains("GrantedStages", Tag.TAG_LONG_ARRAY)
                    || !Arrays.equals(saved.getLongArray("GrantedStages"), new long[]{1L, 0L, 0L, 0L}))
                throw new IllegalArgumentException("Changed historical grants");
            found = new Member(integer(saved, "AdmittedStage"), bool(saved, "Active"), bool(saved, "Ready"), bool(saved, "Withdrawn"),
                    integer(saved, "RetiredStage"), bool(saved, "Suspended"), integer(saved, "RejoinStage"), Set.of(0),
                    integer(saved, "LegacyEnd"), bool(saved, "Resolved"), false);
        }
        return found;
    }
    private static int integer(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_INT)) throw new IllegalArgumentException("Malformed member integer");
        return data.getInt(key);
    }
    private static boolean bool(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_BYTE) || data.getByte(key) != 0 && data.getByte(key) != 1)
            throw new IllegalArgumentException("Malformed member boolean");
        return data.getBoolean(key);
    }
    private static ListTag compounds(CompoundTag data, String key, int limit) {
        if (!data.contains(key, Tag.TAG_LIST)) throw new IllegalArgumentException("Missing history list");
        var list = (ListTag) data.get(key);
        if (list.size() > limit || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Unbounded history list");
        return list;
    }
}
