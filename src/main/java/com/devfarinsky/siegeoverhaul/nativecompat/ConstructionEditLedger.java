package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exact commissioned structural and clearance cells are indexed even while their marker is unloaded. */
final class ConstructionEditLedger extends SavedData {
    static final int MAX_JOBS = 64, MAX_CELLS = 262144;
    private static final String NAME = "siege_construction_edits";
    private record ProjectDestruction(UUID area, String receipt) {}
    private record Site(Set<Long> cells, Set<Long> edited, boolean builderDestroyed, boolean completeReservation,
                        ProjectDestruction destruction) {}
    private final Map<UUID, Site> sites = new HashMap<>();
    private final Map<Long, Set<UUID>> index = new HashMap<>();
    private final Set<UUID> retired = new java.util.LinkedHashSet<>();
    private final Map<UUID, ProtectedBuilderHandLifecycle.Receipt> handLifecycles = new HashMap<>();
    private ConstructionProjectLeases projectLeases = new ConstructionProjectLeases();
    private record ValidatedLease(Site site, Object lease, Set<Long> global,
                                  com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage stage,
                                  String hash, long generation) {}
    private final Map<UUID, ValidatedLease> validatedLeases = new HashMap<>();
    private com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments assignments = new com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments();
    private boolean invalid;
    private int totalCells;
    private UUID generation = UUID.randomUUID();
    private com.devfarinsky.siegeoverhaul.core.PerimeterBuilderCrew crew = new com.devfarinsky.siegeoverhaul.core.PerimeterBuilderCrew(generation);

    static ConstructionEditLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ConstructionEditLedger::load,
                ConstructionEditLedger::new, NAME);
    }

    boolean register(UUID id, Set<BlockPos> positions) {
        if (invalid || incompleteReservations() || id == null || positions == null || sites.containsKey(id) || retired.contains(id) || projectLeases.reservedIdentifier(id) || sites.size() + retired.size() >= MAX_JOBS
                || positions.isEmpty() || positions.size() > MAX_CELLS - totalCells) return false;
        Set<Long> cells = new HashSet<>();
        positions.forEach(pos -> cells.add(pos.asLong()));
        add(id, new Site(Set.copyOf(cells), new HashSet<>(), false, true, null));
        setDirty();
        return true;
    }

    /** One reservation for the complete unpaid manifest; no area/fee is created here. */
    boolean canRegisterProject(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if(invalid || incompleteReservations() || project==null
                || project.state()!=com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.PREPARED_UNPAID || project.payment()!=null
                || sites.size()+retired.size()>=MAX_JOBS || project.reservation().isEmpty()
                || project.reservation().size()>MAX_CELLS-totalCells || project.reservation().stream().anyMatch(index::containsKey))return false;
        var stages=project.stages().stream().map(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage::areaId).toList();
        var identifiers=new HashSet<>(sites.keySet());identifiers.addAll(retired);
        return projectLeases.canRegister(project.header().projectId(),project.header().generation(),project.manifestHash(),stages,identifiers);
    }
    boolean registerProject(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (!canRegisterProject(project)) return false;
        UUID id = project.header().projectId();
        var stages = project.stages().stream().map(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage::areaId).toList();
        Set<UUID> identifiers = new HashSet<>(sites.keySet()); identifiers.addAll(retired);
        if (!projectLeases.canRegister(id, project.header().generation(), project.manifestHash(), stages, identifiers)) return false;
        Set<BlockPos> cells = new HashSet<>(); project.reservation().forEach(cell -> cells.add(BlockPos.of(cell)));
        if (reserves(cells) || !register(id, cells)) return false;
        if (!projectLeases.register(id, project.header().generation(), project.manifestHash(), stages, identifiers)) {
            remove(id); return false;
        }
        setDirty(); return true;
    }

    boolean leaseProjectStage(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (invalid || project == null || project.active() == null) return false;
        var state = project.state();
        if (state != com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.PREPARED_UNPAID
                && state != com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.PREPARED_PAID
                && state != com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                && state != com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.RUNNING) return false;
        var header = project.header(); Site site = sites.get(header.projectId());
        if (site == null || !site.completeReservation() || site.builderDestroyed() || !site.edited().isEmpty()
                || !site.cells().equals(project.reservation())) return false;
        boolean accepted = projectLeases.lease(header.projectId(), header.generation(), project.manifestHash(),
                project.activeStage(), project.active().areaId(), project.active().layout().reservation(), site.cells());
        if (accepted) setDirty(); return accepted;
    }

    boolean matchesProjectLease(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (invalid || project == null || project.active() == null) return false;
        var header = project.header(); Site site = sites.get(header.projectId());
        if (site == null || !site.completeReservation() || site.builderDestroyed() || !site.edited().isEmpty()) return false;
        Object token = projectLeases.identityToken(header.projectId());
        var cached = validatedLeases.get(header.projectId());
        if (cached != null && cached.site() == site && cached.lease() == token && cached.global() == project.reservation()
                && cached.stage() == project.active() && cached.generation() == header.generation()
                && cached.hash().equals(project.manifestHash())) return true;
        if (!site.cells().equals(project.reservation())
                || !projectLeases.matches(header.projectId(), header.generation(), project.manifestHash(),
                project.activeStage(), project.active().areaId(), project.active().layout().reservation())) return false;
        validatedLeases.put(header.projectId(), new ValidatedLease(site, token, project.reservation(), project.active(),
                project.manifestHash(), header.generation()));
        return true;
    }

    UUID projectForArea(UUID area) { return projectLeases.parent(area); }
    /** Global reservation proof also works between stages and during final verification. */
    boolean matchesProjectReservation(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if(invalid || project==null)return false;
        var header=project.header();Site site=sites.get(header.projectId());
        return site!=null && !site.builderDestroyed() && site.edited().isEmpty() && matchesProjectIdentity(project);
    }
    /** Cancellation may retire an edited site, but never someone else's or an incomplete reservation. */
    boolean matchesProjectIdentity(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if(invalid || project==null)return false;
        var header=project.header();Site site=sites.get(header.projectId());
        return site!=null && site.completeReservation() && site.cells().equals(project.reservation())
                && projectLeases.identity(header.projectId(),header.generation(),project.manifestHash(),
                project.stages().stream().map(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage::areaId).toList());
    }
    int projectLeaseIndex(UUID project) { return invalid?-2:projectLeases.activeIndex(project); }

    /** Only an authenticated destructive removal may mint this receipt; absence/unload is not evidence. */
    boolean projectBuilderDestroyed(com.devfarinsky.siegeoverhaul.core.PerimeterProject project,
                                    UUID builder, UUID area, UUID ledgerGeneration) {
        if (!sameGeneration(ledgerGeneration) || !matchesProjectIdentity(project)
                || !assignedBuilder(project).equals(builder)) return false;
        int stage = projectLeaseIndex(project.header().projectId());
        if (stage < 0 || stage >= project.stages().size() || !project.stages().get(stage).areaId().equals(area)
                || stage != project.activeStage() && (stage != project.activeStage() - 1 || !retired(area))) return false;
        UUID id = project.header().projectId(); Site site = sites.get(id);
        var proof = new ProjectDestruction(area, destructionReceipt(project, area));
        if (site.destruction() != null && !site.destruction().equals(proof)) return false;
        sites.put(id, new Site(site.cells(), site.edited(), true, site.completeReservation(), proof));
        validatedLeases.remove(id); setDirty(); return true;
    }

    /** Cleanup-only proof of this exact builder and leased section in this ledger generation. */
    String projectBuilderDestructionReceipt(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (!matchesProjectIdentity(project)) return null;
        Site site = sites.get(project.header().projectId()); var proof = site.destruction();
        int stage = projectLeaseIndex(project.header().projectId());
        if (!site.builderDestroyed() || proof == null || stage < 0 || stage >= project.stages().size()
                || !project.stages().get(stage).areaId().equals(proof.area())
                || !destructionReceipt(project, proof.area()).equals(proof.receipt())) return null;
        return proof.receipt();
    }

    private String destructionReceipt(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID area) {
        var h = project.header();
        String evidence = "perimeter-builder-destroyed-v1:" + h.projectId() + ":" + h.generation() + ":"
                + project.manifestHash() + ":" + assignedBuilder(project) + ":" + area + ":" + generation;
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    UUID assignedBuilder(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (invalid) throw new IllegalStateException("Construction ledger is invalid");
        return assignments.builder(project);
    }
    UUID assignedTerminalBuilder(com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt terminal) {
        if (invalid) throw new IllegalStateException("Construction ledger is invalid");
        return assignments.builder(terminal.projectId(),terminal.generation(),terminal.manifestHash(),terminal.builder());
    }
    UUID builderForArea(com.devfarinsky.siegeoverhaul.core.PerimeterProject project,UUID area) {
        if (invalid) throw new IllegalStateException("Construction ledger is invalid");
        return assignments.builderForArea(project,area);
    }
    boolean canRebindBuilder(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID previous, UUID area) {
        return !invalid && matchesProjectReservation(project) && assignments.canRebind(project, previous, area);
    }
    boolean replaceDeadBuilder(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID replacement) {
        if (crewMember(project, replacement))
            return transferCrewCoordinator(project, assignedBuilder(project), replacement,
                    com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments.Cause.DEATH);
        if (knownCrewMember(project, replacement)) return false;
        String proof = projectBuilderDestructionReceipt(project);
        if (proof == null || edited(project.header().projectId()) || project.active() == null) return false;
        UUID id = project.header().projectId(); Site site = sites.get(id);
        try {
            assignments.replace(project, assignedBuilder(project), replacement, site.destruction().area(), proof);
            sites.put(id, new Site(site.cells(), site.edited(), false, site.completeReservation(), null));
            validatedLeases.remove(id); setDirty(); return true;
        } catch (RuntimeException refusal) { return false; }
    }

    /** Current and historical coordinators are cleanup identities only; assignedBuilder alone grants the role. */
    boolean knownProjectBuilder(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        if (invalid || project == null || worker == null) return false;
        try { return assignments.known(project, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    boolean knownTerminalBuilder(com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt terminal, UUID worker) {
        if (invalid || terminal == null || worker == null || !sameGeneration(terminal.cleanup().ledgerGeneration())) return false;
        try { return assignments.known(terminal, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return false; }
    }

    /**
     * Caller authenticates the loaded helper's owner/native inventory and the old coordinator's actual absence/dismissal.
     * This changes one role, not the paid manifest; absence never creates a destruction receipt.
     */
    boolean transferCrewCoordinator(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID expectedOld, UUID newHelper,
                                    com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments.Cause cause) {
        if (invalid || project == null || project.payment() == null || project.active() == null || cause == null
                || !matchesProjectIdentity(project) || edited(project.header().projectId()) || !crewMember(project, newHelper)) return false;
        boolean death = cause == com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments.Cause.DEATH;
        String proof = death ? projectBuilderDestructionReceipt(project) : null;
        if (death ? proof == null : !matchesProjectReservation(project)) return false;
        int stage = projectLeaseIndex(project.header().projectId());
        if (stage < 0 || stage >= project.stages().size()) return false;
        UUID area = project.stages().get(stage).areaId();
        boolean current = stage == project.activeStage() && projectLeases.matches(project.header().projectId(),
                project.header().generation(), project.manifestHash(), stage, area, project.active().layout().reservation());
        boolean previous = stage == project.activeStage() - 1 && retired(area)
                && project.state() == com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.WAITING_FOR_NEXT_STAGE;
        if (!current && !previous) return false;
        try {
            if (!assignedBuilder(project).equals(expectedOld)) return false;
            // Validate both copies before publishing either half of the role handoff.
            var nextAssignments = com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments.load(assignments.save());
            var nextCrew = com.devfarinsky.siegeoverhaul.core.PerimeterBuilderCrew.load(crew.save(), generation);
            if (death) nextAssignments.replace(project, expectedOld, newHelper, area, proof);
            else nextAssignments.transfer(project, expectedOld, newHelper, area, cause, generation);
            if (!nextCrew.retire(project, newHelper)) return false;
            nextCrew.verifyAssignments(nextAssignments); nextAssignments.verifyTransferReceipts(generation);
            assignments = nextAssignments; crew = nextCrew;
            if (death) {
                UUID id = project.header().projectId(); Site site = sites.get(id);
                sites.put(id, new Site(site.cells(), site.edited(), false, site.completeReservation(), null));
            }
            validatedLeases.remove(project.header().projectId()); setDirty(); return true;
        } catch (IllegalArgumentException conflictingIdentity) { return false; }
    }

    /** Membership is only a selector; native placement still requires the exact live project/worker authorities. */
    Set<UUID> crewMembers(com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (invalid || project == null) throw new IllegalStateException("Construction crew history is unavailable");
        return crew.active(project);
    }
    boolean crewMember(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        if (invalid || project == null || worker == null) return false;
        try { return crew.active(project, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    /** Includes retired helpers for delayed cleanup; grants no work or inventory authority on its own. */
    boolean knownCrewMember(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        if (invalid || project == null || worker == null) return false;
        try { return crew.known(project, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    boolean knownTerminalCrewMember(com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt terminal, UUID worker) {
        if (invalid || terminal == null || worker == null || !sameGeneration(terminal.cleanup().ledgerGeneration())) return false;
        try { return crew.known(terminal, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    /** Caller additionally authenticates exact owner, idle native worker state and retained inventory provenance. */
    boolean enlistCrew(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        if (worker == null || worker.equals(new UUID(0, 0)) || !matchesProjectLease(project) || !matchesProjectReservation(project)
                || project.payment() == null || project.state() != com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.RUNNING) return false;
        try {
            if (assignments.known(project, worker) || !crew.enlist(project, worker)) return false;
            setDirty(); return true;
        } catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    /** Revocation is intentionally possible during pause/cancellation and after reservation release. */
    boolean retireCrew(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        if (invalid || project == null || worker == null) return false;
        try {
            if (!crew.retire(project, worker)) return false;
            setDirty(); return true;
        } catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    /** An authenticated helper death only retires that helper; it never poisons the site's coordinator/reservation. */
    boolean markCrewBuilderDestroyed(com.devfarinsky.siegeoverhaul.core.PerimeterProject project,
                                     UUID worker, UUID area, UUID ledgerGeneration) {
        if (!sameGeneration(ledgerGeneration) || !matchesProjectIdentity(project) || !crewMember(project, worker)) return false;
        int stage = projectLeaseIndex(project.header().projectId());
        if (stage < 0 || stage >= project.stages().size() || !project.stages().get(stage).areaId().equals(area)
                || stage != project.activeStage() && (stage != project.activeStage() - 1 || !retired(area))) return false;
        try {
            if (!crew.destroyed(project, worker, area, stage)) return false;
            setDirty(); return true;
        } catch (IllegalArgumentException conflictingIdentity) { return false; }
    }
    boolean crewBuilderDestroyed(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        return crewBuilderDestructionReceipt(project, worker) != null;
    }
    String crewBuilderDestructionReceipt(com.devfarinsky.siegeoverhaul.core.PerimeterProject project, UUID worker) {
        if (invalid || project == null || worker == null) return null;
        try { return crew.destructionReceipt(project, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return null; }
    }
    String terminalCrewBuilderDestructionReceipt(com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt terminal, UUID worker) {
        if (!knownTerminalCrewMember(terminal, worker)) return null;
        try { return crew.destructionReceipt(terminal, worker); }
        catch (IllegalArgumentException conflictingIdentity) { return null; }
    }

    private static Set<Long> packed(Set<BlockPos> positions) {
        Set<Long> result = new HashSet<>(); positions.forEach(pos -> result.add(pos.asLong())); return result;
    }

    private void add(UUID id, Site site) {
        sites.put(id, site);
        totalCells += site.cells().size();
        site.cells().forEach(pos -> index.computeIfAbsent(pos, ignored -> new HashSet<>()).add(id));
    }

    boolean matches(UUID id, Set<BlockPos> positions) {
        if (invalid || positions == null) return false;
        UUID parent = projectLeases.parent(id);
        Site site = sites.get(parent == null ? id : parent);
        if (site == null || !site.completeReservation()) return false;
        if (parent != null) return projectLeases.matches(id, packed(positions));
        return site.cells().size() == positions.size()
                && positions.stream().allMatch(pos -> site.cells().contains(pos.asLong()));
    }

    boolean contains(UUID id) {
        UUID parent = projectLeases.parent(id);
        return !invalid && (parent == null ? sites.containsKey(id) : sites.containsKey(parent) && projectLeases.active(id));
    }
    boolean completeReservation(UUID id) {
        UUID parent = projectLeases.parent(id); Site site = sites.get(parent == null ? id : parent);
        return !invalid && site != null && site.completeReservation() && (parent == null || projectLeases.active(id));
    }
    boolean retired(UUID id) { return !invalid && id != null && (retired.contains(id) || projectLeases.retired(id)); }
    boolean canRetire(UUID id) {
        // Absence is never cancellation evidence, including failed unregistered
        // handoffs and a restored/missing history file.
        return !invalid && id != null && (sites.containsKey(id) || retired.contains(id) || projectLeases.active(id) || projectLeases.retired(id));
    }
    void retire(UUID id, boolean workerCleaned) {
        if (!canRetire(id)) return;
        if (projectLeases.parent(id) != null) {
            if (projectLeases.retire(id)) setDirty();
            return; // Stage retirement must never release the whole-project reservation.
        }
        Site site = sites.get(id);
        boolean destroyed = site != null && site.builderDestroyed();
        UUID activeChild = projectLeases.activeChild(id);
        boolean project = projectLeases.projectIds().contains(id);
        remove(id);
        UUID cleanup = project ? activeChild : id;
        retired.remove(id);
        if (cleanup != null) {
            if (workerCleaned || destroyed) retired.remove(cleanup);
            else retired.add(cleanup);
        }
        setDirty();
    }
    void acknowledgeRetirement(UUID id) { if (retired.remove(id)) setDirty(); }
    void builderDestroyed(UUID id) {
        UUID parent = projectLeases.parent(id);
        UUID siteId = parent == null ? id : parent;
        Site site = sites.get(siteId);
        if (site != null && !site.builderDestroyed()) {
            sites.put(siteId, new Site(site.cells(), site.edited(), true, site.completeReservation(), site.destruction())); setDirty();
        }
        acknowledgeRetirement(id);
    }
    UUID generation() { return generation; }
    boolean sameGeneration(UUID expected) { return !invalid && generation.equals(expected); }

    /** One bounded provenance record per commissioned builder; retirement releases all spatial reservations. */
    ProtectedBuilderHandLifecycle.Receipt handLifecycle(UUID builder) { return handLifecycles.get(builder); }

    /** Read-only capacity/identity preflight, before creating an unpaid job or invoking its native setter. */
    boolean canRetainHandLifecycle(CompoundTag data, UUID builder) {
        if (invalid || builder == null || data == null) return false;
        if (handLifecycles.containsKey(builder) || ProtectedBuilderHandLifecycle.selected(data))
            return ProtectedBuilderHandLifecycle.matches(data, builder, this);
        return handLifecycles.size() < ProtectedBuilderHandLifecycle.MAX_BUILDERS;
    }

    /** Caller must authenticate the live/retired source and verify the native mirror before minting provenance. */
    boolean retainHandLifecycle(CompoundTag data, UUID builder, UUID owner, UUID area) {
        if (!canRetainHandLifecycle(data, builder)) return false;
        if (handLifecycles.containsKey(builder)) return true;
        try {
            var receipt = new ProtectedBuilderHandLifecycle.Receipt(UUID.randomUUID(), builder, owner, area, generation);
            handLifecycles.put(builder, receipt); setDirty();
            data.put(ProtectedBuilderHandLifecycle.KEY, receipt.save()); return true;
        } catch (RuntimeException malformed) { return false; }
    }

    boolean reserves(java.util.Collection<BlockPos> cells) {
        return invalid || incompleteReservations() || cells == null || cells.stream().anyMatch(pos -> pos == null || index.containsKey(pos.asLong()));
    }

    private boolean incompleteReservations() {
        // Earlier draft ledgers indexed solids only. Preserve their cancellation receipts,
        // but never infer that unrecorded clearance is free while any such site remains.
        return sites.values().stream().anyMatch(site -> !site.completeReservation());
    }

    boolean edited(UUID id) {
        UUID parent = projectLeases.parent(id);
        Site site = sites.get(parent == null ? id : parent);
        return invalid || site == null || !site.edited().isEmpty();
    }

    void record(BlockPos pos) {
        for (UUID id : index.getOrDefault(pos.asLong(), Set.of())) {
            if (sites.get(id).edited().add(pos.asLong())) { validatedLeases.remove(id); setDirty(); }
        }
    }

    void remove(UUID id) {
        // A child ID is never a second independently removable site.
        Site site = sites.remove(id);
        if (site == null) return;
        projectLeases.remove(id); validatedLeases.remove(id);
        totalCells -= site.cells().size();
        for (long pos : site.cells()) {
            Set<UUID> ids = index.get(pos);
            ids.remove(id);
            if (ids.isEmpty()) index.remove(pos);
        }
        setDirty();
    }

    static ConstructionEditLedger load(CompoundTag root) {
        var ledger = new ConstructionEditLedger();
        // NBT getters coerce wrong types to empty/false. Never let damaged history
        // erase edit, destruction or retirement evidence while retaining authority.
        if (!keys(root, Set.of("Sites", "Retired", "Generation", "Invalid"), Set.of("ProjectLeases", "HandLifecycles", "BuilderAssignments", "BuilderCrew"))
                || !compoundList(root, "Sites") || !compoundList(root, "Retired")
                || !identity(root, "Generation") || !canonicalBoolean(root, "Invalid")
                || root.getBoolean("Invalid")) {
            ledger.invalid = true;
            return ledger;
        }
        ListTag jobs = (ListTag) root.get("Sites"), retiredJobs = (ListTag) root.get("Retired");
        if ((long) jobs.size() + retiredJobs.size() > MAX_JOBS) { ledger.invalid = true; return ledger; }
        ledger.generation = root.getUUID("Generation");
        // Old saves have no helper field. Bind their empty crew to the restored, not constructor-random generation.
        ledger.crew = new com.devfarinsky.siegeoverhaul.core.PerimeterBuilderCrew(ledger.generation);
        for (Tag entry : jobs) {
            CompoundTag tag = (CompoundTag) entry;
            // Missing/zero ReservationVersion is the explicitly supported old
            // solids-only index: preserve cancellation, never grant construction.
            if (!keys(tag, Set.of("Id", "Cells", "Edited", "BuilderDestroyed"), Set.of("ReservationVersion", "BuilderDestruction"))
                    || !identity(tag, "Id") || !tag.contains("Cells", Tag.TAG_LONG_ARRAY)
                    || !tag.contains("Edited", Tag.TAG_LONG_ARRAY) || !canonicalBoolean(tag, "BuilderDestroyed")
                    || tag.contains("ReservationVersion") && (!tag.contains("ReservationVersion", Tag.TAG_INT)
                    || tag.getInt("ReservationVersion") != 0 && tag.getInt("ReservationVersion") != AcceptedConstructionReservation.VERSION)) {
                ledger.invalid = true; break;
            }
            long[] cells = tag.getLongArray("Cells"), edits = tag.getLongArray("Edited");
            if (cells.length == 0 || edits.length > cells.length
                    || cells.length > MAX_CELLS - ledger.totalCells
                    || ledger.sites.containsKey(tag.getUUID("Id"))) {
                ledger.invalid = true;
                break;
            }
            Set<Long> positions = new HashSet<>(), edited = new HashSet<>();
            for (long cell : cells) positions.add(cell);
            for (long cell : edits) edited.add(cell);
            if (positions.size() != cells.length || edited.size() != edits.length || !positions.containsAll(edited)) {
                ledger.invalid = true;
                break;
            }
            ProjectDestruction destruction = null;
            if (tag.contains("BuilderDestruction")) {
                CompoundTag proof = tag.getCompound("BuilderDestruction");
                if (!tag.contains("BuilderDestruction", Tag.TAG_COMPOUND) || !tag.getBoolean("BuilderDestroyed")
                        || !keys(proof, Set.of("Area", "Receipt"), Set.of()) || !identity(proof, "Area")
                        || !proof.contains("Receipt", Tag.TAG_STRING) || !proof.getString("Receipt").matches("[0-9a-f]{64}")) {
                    ledger.invalid = true; break;
                }
                destruction = new ProjectDestruction(proof.getUUID("Area"), proof.getString("Receipt"));
            }
            ledger.add(tag.getUUID("Id"), new Site(Set.copyOf(positions), edited, tag.getBoolean("BuilderDestroyed"),
                    tag.getInt("ReservationVersion") == AcceptedConstructionReservation.VERSION, destruction));
        }
        for (Tag entry : retiredJobs) {
            CompoundTag tag = (CompoundTag) entry;
            if (!keys(tag, Set.of("Id"), Set.of()) || !identity(tag, "Id")
                    || ledger.sites.containsKey(tag.getUUID("Id")) || !ledger.retired.add(tag.getUUID("Id"))) {
                ledger.invalid = true; break;
            }
        }
        if (!ledger.invalid && root.contains("ProjectLeases")) {
            try {
                if (!root.contains("ProjectLeases", Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
                Map<UUID, Set<Long>> reservations = new HashMap<>();
                ledger.sites.forEach((id, site) -> {
                    if (site.completeReservation()) reservations.put(id, site.cells());
                });
                ledger.projectLeases = ConstructionProjectLeases.load(root.getCompound("ProjectLeases"), reservations, ledger.retired);
            } catch (RuntimeException malformed) { ledger.invalid = true; }
        }
        if (!ledger.invalid && root.contains("HandLifecycles")) {
            try {
                if (!compoundList(root, "HandLifecycles")) throw new IllegalArgumentException();
                var hands = (ListTag) root.get("HandLifecycles");
                if (hands.size() > ProtectedBuilderHandLifecycle.MAX_BUILDERS) throw new IllegalArgumentException();
                Set<UUID> receipts = new HashSet<>();
                for (Tag entry : hands) {
                    var receipt = ProtectedBuilderHandLifecycle.read((CompoundTag) entry);
                    if (!receipt.generation().equals(ledger.generation) || !receipts.add(receipt.receipt())
                            || ledger.handLifecycles.putIfAbsent(receipt.builder(), receipt) != null)
                        throw new IllegalArgumentException();
                }
            } catch (RuntimeException malformed) { ledger.invalid = true; }
        }
        if (!ledger.invalid && root.contains("BuilderAssignments")) {
            try {
                if (!root.contains("BuilderAssignments", Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
                ledger.assignments = com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments.load(root.getCompound("BuilderAssignments"));
                ledger.assignments.verifyTransferReceipts(ledger.generation);
            } catch (RuntimeException malformed) { ledger.invalid = true; }
        }
        if (!ledger.invalid && root.contains("BuilderCrew")) {
            try {
                if (!root.contains("BuilderCrew", Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
                ledger.crew = com.devfarinsky.siegeoverhaul.core.PerimeterBuilderCrew.load(root.getCompound("BuilderCrew"), ledger.generation);
                ledger.crew.verifyAssignments(ledger.assignments);
            } catch (RuntimeException malformed) { ledger.invalid = true; }
        }
        return ledger;
    }

    private static boolean keys(CompoundTag tag, Set<String> required, Set<String> optional) {
        return tag != null && tag.getAllKeys().containsAll(required)
                && tag.getAllKeys().stream().allMatch(key -> required.contains(key) || optional.contains(key));
    }
    private static boolean compoundList(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) return false;
        ListTag list = (ListTag) tag.get(key);
        return list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND;
    }
    private static boolean canonicalBoolean(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_BYTE) && (tag.getByte(key) == 0 || tag.getByte(key) == 1);
    }
    private static boolean identity(CompoundTag tag, String key) {
        return tag.hasUUID(key) && !tag.getUUID(key).equals(new UUID(0, 0));
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        sites.forEach((id, site) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putBoolean("BuilderDestroyed", site.builderDestroyed());
            if (site.destruction() != null) {
                CompoundTag proof = new CompoundTag(); proof.putUUID("Area", site.destruction().area());
                proof.putString("Receipt", site.destruction().receipt()); tag.put("BuilderDestruction", proof);
            }
            tag.putInt("ReservationVersion", site.completeReservation() ? AcceptedConstructionReservation.VERSION : 0);
            tag.putLongArray("Cells", site.cells().stream().mapToLong(Long::longValue).toArray());
            tag.putLongArray("Edited", site.edited().stream().mapToLong(Long::longValue).toArray());
            list.add(tag);
        });
        root.put("Sites", list);
        ListTag canceled = new ListTag();
        retired.forEach(id -> { CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); canceled.add(tag); });
        root.put("Retired", canceled);
        root.putUUID("Generation", generation);
        root.putBoolean("Invalid", invalid);
        root.put("ProjectLeases", projectLeases.save());
        ListTag hands = new ListTag();
        handLifecycles.values().forEach(receipt -> hands.add(receipt.save()));
        root.put("HandLifecycles", hands);
        root.put("BuilderAssignments", assignments.save());
        root.put("BuilderCrew", crew.save());
        return root;
    }
}
