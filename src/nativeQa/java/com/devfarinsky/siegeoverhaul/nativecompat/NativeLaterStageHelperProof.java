package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only Case L closed-transfer/load-census provenance after A0 retirement. Never a
 * storage/placement permit, ordinary working-operation check, or recovery operation.
 * The caller independently authenticates the already-loaded authoritative project and
 * exact A0/A1 verification/retirement journal snapshots before loading and until cancel.
 * It also owns raw offline-to-HIGHEST cargo/persistent equality, exact LOWEST arming,
 * per-join split/rebind evidence, registration, tickets and grounded END observations.
 * A valid pending proof is usable ONLY for that recorded load census. Transfer still
 * requires the unchanged transit eligibility checks, including a closed nonpending hand.
 * Normal A2 requests/borrowed hands may reject this narrow proof without implying a bug.
 */
final class NativeLaterStageHelperProof {
    private NativeLaterStageHelperProof() {}

    // BEGIN dependency-free decision: executed directly by the focused Java harness.
    static final String WHOLE = "SiegeWholePerimeter", CHILD = "SiegeProtectedAreaReceipt",
            CHILD_GENERATION = "SiegeProtectedLedgerGeneration", NATIVE_AREA = "SiegePlayerFortificationAreaId",
            NATIVE_OWNER = "SiegePlayerFortificationOwner", NATIVE_POS = "SiegePlayerFortificationPos",
            HAND_PENDING = "SiegeProtectedHandRefreshPending", REBINDS = "SiegeProtectedHandRebinds",
            STATUS = "SiegeConstructionPause";
    // retireBuilderAssociation -> PlayerFortificationJobs.unlink -> BuilderGroundRecovery.reset.
    // The whole selector and immutable hand are deliberately NOT removed.
    private static final Set<String> DETACHED_KEYS = Set.of(CHILD, CHILD_GENERATION, NATIVE_AREA,
            NATIVE_OWNER, NATIVE_POS, "SiegePlayerFortificationMisses", "SiegeWallGroundRecovery");

    enum State { STALE_A0_SUSPENDED, DETACHED_SUSPENDED, ACTIVE_A2, REJECTED }
    record Proof(State state, String reason, Map<String, Object> evidence) {
        Proof { evidence = Map.copyOf(evidence); }
        boolean valid() { return state != State.REJECTED; }
    }
    record Stage(int index, UUID area, String digest, int targets) {}
    record Identity(UUID project, long generation, String hash, String core, String faction, UUID owner,
                    UUID coordinator, int targets, int claimChunks, int feeVersion, int price,
                    String payment, List<Stage> stages, UUID ledger) {
        Identity { stages = List.copyOf(stages); }
    }
    record Receipt(UUID project, long generation, String hash, int stage, UUID area, String digest, int targets) {}
    record Progress(String state, int activeStage, List<Receipt> receipts, boolean immutableExact) {
        Progress { receipts = List.copyOf(receipts); }
    }
    record Hand(UUID receipt, UUID builder, UUID owner, UUID area, UUID generation) {}
    record Selector(UUID project, long generation, String hash, String core, UUID ledger) {}
    record Association(Selector selector, UUID child, UUID generation, UUID nativeArea, UUID nativeOwner) {
        boolean whole(Identity expected) {
            return new Selector(expected.project(), expected.generation(), expected.hash(), expected.core(), expected.ledger()).equals(selector);
        }
        boolean bound(Identity expected, UUID area) {
            return whole(expected) && area.equals(child) && expected.ledger().equals(generation)
                    && area.equals(nativeArea) && expected.owner().equals(nativeOwner);
        }
        boolean detached(Identity expected) {
            return whole(expected) && child == null && generation == null && nativeArea == null && nativeOwner == null;
        }
    }
    record Member(int admitted, boolean active, boolean ready, boolean withdrawn, int retired,
                  boolean suspended, int rejoin, Set<Integer> grants, int legacyEnd, boolean resolved, boolean destroyed) {
        Member { grants = Set.copyOf(grants); }
        boolean common() {
            return admitted == 0 && ready && !withdrawn && retired == -1 && rejoin == -1
                    && legacyEnd == -1 && !resolved && !destroyed;
        }
        boolean suspendedA0() { return common() && !active && suspended && grants.equals(Set.of(0)); }
        boolean activeA2() { return common() && active && !suspended && grants.equals(Set.of(0, 2)); }
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
                 boolean currentAreaExact, long currentPosition, Operations operations,
                 Map<String, String> persistent, int rebinds) {
        Actor { persistent = Map.copyOf(persistent); }
    }
    record Frozen(Hand hand, Association association, Map<String, String> persistent, int rebinds, boolean closed) {
        Frozen { persistent = Map.copyOf(persistent); }
    }
    record History(boolean known, boolean projectIdentity, boolean a2Lease, boolean pastRetired,
                   boolean futureAbsent, boolean originalCoordinator, boolean helperNeverCoordinator,
                   boolean destructionAbsent, boolean stageGranted, Set<UUID> cleanupAreas,
                   UUID admissionArea, UUID unresolved, Member member) {
        History { cleanupAreas = Set.copyOf(cleanupAreas); }
    }
    record Facts(Identity expected, Identity current, Progress progress, UUID helper, UUID owner,
                 UUID originalLedger, UUID currentLedger, Hand originalHand, Hand ledgerHand,
                 Actor actor, Frozen frozen, History history) {}

    static Proof decide(Facts f) {
        if (f == null || f.expected() == null || f.current() == null || f.progress() == null || f.actor() == null
                || f.frozen() == null || f.history() == null || f.originalHand() == null
                || f.actor().association() == null || f.frozen().association() == null || f.actor().operations() == null)
            return rejected("missing-evidence");
        Identity expected = f.expected(); Progress progress = f.progress(); History history = f.history();
        if (!expected.equals(f.current()) || !progress.immutableExact() || expected.stages().size() < 3
                || !"RUNNING".equals(progress.state()) || progress.activeStage() != 2
                || expected.payment().isEmpty() || progress.receipts().size() != 2)
            return rejected("running-a2-identity");
        for (int i = 0; i < 2; i++) {
            Stage stage = expected.stages().get(i);
            if (stage.index() != i || !new Receipt(expected.project(), expected.generation(), expected.hash(), i,
                    stage.area(), stage.digest(), stage.targets()).equals(progress.receipts().get(i)))
                return rejected("ordered-a0-a1-receipts");
        }
        UUID a0 = expected.stages().get(0).area(), a2 = expected.stages().get(2).area();
        if (expected.stages().get(2).index() != 2 || a0.equals(a2)) return rejected("a2-stage-identity");
        if (!Objects.equals(f.helper(), f.actor().helper()) || !Objects.equals(f.owner(), f.actor().owner())
                || !Objects.equals(f.owner(), expected.owner()) || Objects.equals(f.helper(), expected.coordinator()))
            return rejected("native-helper-owner");
        if (!Objects.equals(f.originalLedger(), f.currentLedger()) || !Objects.equals(f.originalLedger(), expected.ledger()))
            return rejected("ledger-generation");
        Hand hand = f.originalHand();
        if (hand.receipt() == null || hand.receipt().equals(new UUID(0, 0)) || !Objects.equals(hand.builder(), f.helper()) || !Objects.equals(hand.owner(), f.owner())
                || !a0.equals(hand.area()) || !Objects.equals(hand.generation(), f.originalLedger())
                || !hand.equals(f.actor().hand()) || !hand.equals(f.ledgerHand()) || !hand.equals(f.frozen().hand()))
            return rejected("original-a0-hand-receipt");
        if (!history.known() || !history.projectIdentity() || !history.a2Lease() || !history.pastRetired()
                || !history.futureAbsent() || !history.originalCoordinator() || !history.helperNeverCoordinator()
                || !history.destructionAbsent() || history.member() == null || !a0.equals(history.admissionArea())
                || !expected.project().equals(history.unresolved())) return rejected("exact-live-project-history");
        if (!f.frozen().closed() || !f.frozen().association().bound(expected, a0)
                || !f.frozen().persistent().containsKey(NATIVE_POS))
            return rejected("finalized-original-provenance");
        if (!f.actor().operations().closed()) return rejected("canonical-closed-operations");
        State state; Member member = history.member(); Actor actor = f.actor();
        if (member.suspendedA0() && !history.stageGranted() && history.cleanupAreas().equals(Set.of(a0))) {
            if (actor.association().bound(expected, a0) && (actor.currentArea() == null
                    || a0.equals(actor.currentArea()) && actor.currentAreaExact())) state = State.STALE_A0_SUSPENDED;
            else if (actor.association().detached(expected) && actor.currentArea() == null) state = State.DETACHED_SUSPENDED;
            else return rejected("suspended-state-channels");
        } else if (member.activeA2() && history.stageGranted() && history.cleanupAreas().equals(Set.of(a0, a2))
                && actor.association().bound(expected, a2) && a2.equals(actor.currentArea()) && actor.currentAreaExact()
                && !actor.operations().pending()) state = State.ACTIVE_A2;
        else return rejected("activation-history-channels");
        if (!persistentMatches(f, state)) return rejected("frozen-persistent-delta");
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("state", state.name()); evidence.put("helper", f.helper().toString()); evidence.put("owner", f.owner().toString());
        evidence.put("project", expected.project().toString()); evidence.put("generation", expected.generation());
        evidence.put("manifestHash", expected.hash()); evidence.put("ledgerGeneration", f.originalLedger().toString());
        evidence.put("core", expected.core()); evidence.put("coordinator", expected.coordinator().toString());
        evidence.put("originalHandReceipt", hand.receipt().toString()); evidence.put("handArea", a0.toString());
        evidence.put("originalHandExact", true); evidence.put("frozenPersistentExact", true);
        evidence.put("pendingHand", actor.operations().pending()); evidence.put("rebinds", actor.rebinds());
        evidence.put("admissionArea", history.admissionArea().toString()); evidence.put("unresolvedProject", history.unresolved().toString());
        evidence.put("cleanupAreas", history.cleanupAreas().stream().map(UUID::toString).sorted().toList());
        evidence.put("originalHand", handEvidence(hand)); evidence.put("actorHand", handEvidence(actor.hand()));
        evidence.put("ledgerHand", handEvidence(f.ledgerHand())); evidence.put("frozenHand", handEvidence(f.frozen().hand()));
        evidence.put("pastRetired", history.pastRetired()); evidence.put("a2Lease", history.a2Lease());
        evidence.put("currentStageGranted", history.stageGranted()); evidence.put("destructionAbsent", history.destructionAbsent());
        evidence.put("current", Map.of("state", progress.state(), "activeStage", progress.activeStage(), "verifiedStages", progress.receipts().size()));
        evidence.put("receipts", progress.receipts().stream().map(r -> Map.of("project", r.project().toString(),
                "generation", r.generation(), "manifestHash", r.hash(), "stageIndex", r.stage(), "areaId", r.area().toString(),
                "stageDigest", r.digest(), "verifiedTargets", r.targets())).toList());
        var memberEvidence = new LinkedHashMap<String, Object>();
        memberEvidence.put("admittedStage", member.admitted()); memberEvidence.put("active", member.active());
        memberEvidence.put("ready", member.ready()); memberEvidence.put("withdrawn", member.withdrawn());
        memberEvidence.put("retiredStage", member.retired()); memberEvidence.put("suspended", member.suspended());
        memberEvidence.put("rejoinStage", member.rejoin()); memberEvidence.put("grantedStages", member.grants().stream().sorted().toList());
        memberEvidence.put("legacyEnd", member.legacyEnd()); memberEvidence.put("resolved", member.resolved());
        memberEvidence.put("destroyed", member.destroyed()); evidence.put("member", Map.copyOf(memberEvidence));
        evidence.put("association", Map.of("wholeSelector", actor.association().selector() != null,
                "protectedChild", actor.association().child() != null, "protectedGeneration", actor.association().generation() != null,
                "nativeArea", actor.association().nativeArea() != null, "nativeOwner", actor.association().nativeOwner() != null,
                "currentArea", actor.currentArea() == null ? "" : actor.currentArea().toString()));
        return new Proof(state, "", evidence);
    }

    private static boolean persistentMatches(Facts f, State state) {
        int before = f.frozen().rebinds(), after = f.actor().rebinds(); boolean pending = f.actor().operations().pending();
        // At most one disk join and one return join follow finalization. The driver must prove
        // each observed split-specific increment; this upper bound never substitutes for that.
        int limit = state == State.STALE_A0_SUSPENDED ? (pending ? 0 : 1) : pending ? 1 : 2;
        if (before < 0 || after < before || (long) after - before > limit) return false;
        var expected = new LinkedHashMap<>(f.frozen().persistent());
        if (state != State.STALE_A0_SUSPENDED) {
            DETACHED_KEYS.forEach(expected::remove);
            // Successful ordinary disk restoration clears STATUS, even if already aliased
            // (zero rebind increment); old-child detachment can finish in that same tick.
            expected.remove(STATUS);
        } else if (after > before && !pending) expected.remove(STATUS);
        if (state == State.ACTIVE_A2) {
            // resumeCrewMember + linkWorker write only these exact new live-child channels.
            UUID a2 = f.expected().stages().get(2).area();
            expected.put(CHILD, uuidTag(a2)); expected.put(CHILD_GENERATION, uuidTag(f.originalLedger()));
            expected.put(NATIVE_AREA, uuidTag(a2)); expected.put(NATIVE_OWNER, uuidTag(f.owner()));
            expected.put(NATIVE_POS, Long.toString(f.actor().currentPosition()) + "L");
        }
        if (pending) expected.put(HAND_PENDING, "1b");
        if (after != before) expected.put(REBINDS, Integer.toString(after));
        return expected.equals(f.actor().persistent());
    }
    private static String uuidTag(UUID id) {
        long high = id.getMostSignificantBits(), low = id.getLeastSignificantBits();
        return "[I;" + (int)(high >> 32) + "," + (int)high + "," + (int)(low >> 32) + "," + (int)low + "]";
    }
    private static Map<String, Object> handEvidence(Hand hand) {
        return Map.of("receipt", hand.receipt().toString(), "builder", hand.builder().toString(), "owner", hand.owner().toString(),
                "area", hand.area().toString(), "generation", hand.generation().toString());
    }
    private static Proof rejected(String reason) { return new Proof(State.REJECTED, reason, Map.of("reason", reason)); }
    // END dependency-free decision.

    /** No project/core lookup, caches, mutation, native goals, cleanup, reconciliation or grants. */
    static Proof inspect(BuilderEntity worker, PerimeterProject accepted, PerimeterProject currentProject,
                         ConstructionEditLedger ledger, UUID helper, UUID owner, UUID ledgerGeneration,
                         ProtectedBuilderHandLifecycle.Receipt originalHand, NativeActiveHelperTransit.Inventory finalizedOffline) {
        try {
            if (worker == null || accepted == null || currentProject == null || ledger == null || originalHand == null
                    || finalizedOffline == null || helper == null || owner == null || ledgerGeneration == null)
                return rejected("missing-native-evidence");
            if (worker.getServer() == null || !worker.getServer().isSameThread()) return rejected("not-server-thread");
            if (accepted.state() != PerimeterProject.State.RUNNING || accepted.activeStage() != 0
                    || accepted.active() == null || !accepted.receipts().isEmpty() || accepted.stages().size() < 3
                    || accepted.payment() == null || accepted.payment().creative() || accepted.payment().debited() != 64)
                return rejected("not-original-paid-a0");
            if (!ledger.sameGeneration(ledgerGeneration)) return rejected("invalid-ledger");
            var data = worker.getPersistentData().copy(); var frozen = finalizedOffline.persistent();
            var current = worker.currentBuildArea;
            UUID a0 = accepted.stages().get(0).areaId(), a1 = accepted.stages().get(1).areaId(), a2 = accepted.stages().get(2).areaId();
            int currentIndex = current == null ? -1 : a0.equals(current.getUUID()) ? 0 : a2.equals(current.getUUID()) ? 2 : -1;
            boolean currentExact = false;
            if (current instanceof ProtectedBuildArea marker && currentIndex >= 0) {
                var stage = accepted.stages().get(currentIndex);
                currentExact = owner.equals(WorkersBridge.readOwner(current))
                        && accepted.header().builder().equals(marker.reservedBuilderId())
                        && PerimeterProjectAuthority.read(current.getPersistentData()).equals(new PerimeterProjectAuthority.Scope(
                        accepted.header().projectId(), accepted.header().generation(), accepted.manifestHash(), accepted.header().coreKey(),
                        currentIndex, stage.digest()));
                if (currentIndex == 2) currentExact &= current.level().dimension().equals(Level.OVERWORLD)
                        && worker.level() == current.level()
                        && current.isAlive() && !current.isRemoved() && NativeConstructionGuard.commissionPaid(current);
            }
            var actor = new Actor(worker.getUUID(), worker.getOwnerUUID(), hand(readHand(data)), association(data),
                    current == null ? null : current.getUUID(), currentExact, current == null ? 0 : current.blockPosition().asLong(),
                    operations(worker, data), persistent(data), rebinds(data));
            var frozenSlots = finalizedOffline.slots();
            var original = new Frozen(hand(readHand(frozen)), association(frozen), persistent(frozen), rebinds(frozen),
                    finalizedOffline.beforeItemSlot() == -1 && frozenSlots.size() >= 6 && frozenSlots.size() <= 128
                            && finalizedOffline.main().equals(frozenSlots.get(5)) && frozen.contains(NATIVE_POS, Tag.TAG_LONG) && !frozen.contains(HAND_PENDING)
                            && !frozen.contains("SiegeProtectedHandReviewRequired") && !frozen.contains(ProtectedInventoryCleanup.KEY));
            var history = new History(ledger.knownCrewMember(currentProject, helper), ledger.matchesProjectReservation(currentProject),
                    ledger.projectLeaseIndex(currentProject.header().projectId()) == 2 && ledger.completeReservation(a2)
                            && ledger.matches(a2, accepted.stages().get(2).reservation().stream().map(BlockPos::of).collect(Collectors.toSet())),
                    ledger.retired(a0) && ledger.retired(a1) && !ledger.contains(a0) && !ledger.contains(a1),
                    accepted.stages().stream().skip(3).noneMatch(stage -> ledger.contains(stage.areaId()) || ledger.retired(stage.areaId())),
                    accepted.header().builder().equals(ledger.assignedBuilder(currentProject)), !ledger.knownProjectBuilder(currentProject, helper),
                    ledger.crewBuilderDestructionReceipt(currentProject, helper) == null,
                    ledger.crewStageGranted(currentProject, helper), ledger.workerCleanupAreas(currentProject, helper),
                    ledger.crewAdmissionArea(currentProject, helper), ledger.unresolvedCrewProject(helper),
                    member(ledger.save(new CompoundTag()), currentProject, helper, ledgerGeneration));
            var progress = new Progress(currentProject.state().name(), currentProject.activeStage(), currentProject.receipts().stream()
                    .map(r -> new Receipt(r.projectId(), r.generation(), r.manifestHash(), r.stageIndex(), r.areaId(), r.stageDigest(), r.verifiedTargets())).toList(),
                    accepted.header().equals(currentProject.header()) && accepted.targets().equals(currentProject.targets())
                            && accepted.before().equals(currentProject.before()) && accepted.clearanceBefore().equals(currentProject.clearanceBefore())
                            && accepted.reservation().equals(currentProject.reservation()) && accepted.observations().equals(currentProject.observations())
                            && accepted.formatVersion() == currentProject.formatVersion());
            return decide(new Facts(identity(accepted, ledgerGeneration), identity(currentProject, ledgerGeneration), progress,
                    helper, owner, ledgerGeneration, ledger.generation(), hand(originalHand), hand(ledger.handLifecycle(helper)), actor, original, history));
        } catch (RuntimeException | LinkageError unavailable) {
            return rejected("unreadable-native-provenance:" + unavailable.getClass().getSimpleName());
        }
    }
    private static Identity identity(PerimeterProject project, UUID generation) {
        var h = project.header();
        return new Identity(h.projectId(), h.generation(), project.manifestHash(), h.coreKey(), h.faction(), h.owner(), h.builder(),
                project.targets().size(), h.territory().size(), h.feeVersion(), h.quotedPrice(),
                project.payment() == null ? "" : project.payment().toString(), project.stages().stream().map(stage ->
                new Stage(stage.index(), stage.areaId(), stage.digest(), stage.layout().targets().size())).toList(), generation);
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

    /** Exact v3 helper admission, separate from every coordinator identity. Never seals history. */
    private static Member member(CompoundTag ledger, PerimeterProject current, UUID helper, UUID generation) {
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
            if (!current.header().projectId().equals(optionalUuid(project, "Project"))) continue;
            if (!project.contains("Generation", Tag.TAG_LONG) || project.getLong("Generation") != current.header().generation()
                    || !project.contains("Hash", Tag.TAG_STRING) || !current.manifestHash().equals(project.getString("Hash"))
                    || !current.header().builder().equals(optionalUuid(project, "Original"))) throw new IllegalArgumentException("Crew identity");
            var members = compounds(project, "Members", 64);
            if (found != null || members.size() != 1) throw new IllegalArgumentException("Repeated or new crew admission");
            var saved = members.getCompound(0);
            if (!helper.equals(optionalUuid(saved, "Worker")) || !saved.getAllKeys().equals(Set.of("Worker", "Active", "Ready",
                    "Withdrawn", "RetiredStage", "AdmittedStage", "Suspended", "RejoinStage", "GrantedStages", "LegacyEnd", "Resolved")))
                throw new IllegalArgumentException("Changed helper or destruction history");
            if (!saved.contains("GrantedStages", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("Missing explicit grants");
            long[] bits = saved.getLongArray("GrantedStages"); Set<Integer> grants;
            if (Arrays.equals(bits, new long[]{1L, 0L, 0L, 0L})) grants = Set.of(0);
            else if (Arrays.equals(bits, new long[]{5L, 0L, 0L, 0L})) grants = Set.of(0, 2);
            else throw new IllegalArgumentException("Changed historical grants");
            found = new Member(integer(saved, "AdmittedStage"), bool(saved, "Active"), bool(saved, "Ready"), bool(saved, "Withdrawn"),
                    integer(saved, "RetiredStage"), bool(saved, "Suspended"), integer(saved, "RejoinStage"), grants,
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
