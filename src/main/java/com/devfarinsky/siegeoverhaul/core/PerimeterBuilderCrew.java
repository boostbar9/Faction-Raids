package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bounded helper membership, separate from the paid manifest and coordinator replacement history.
 * Membership is not a placement permit: callers must still verify the live owner, project/stage,
 * worker receipt, native work schedule, inventory provenance and exclusive work claim.
 * Retired identities remain available solely for delayed cleanup of unloaded workers.
 */
public final class PerimeterBuilderCrew {
    public static final int MAX_PROJECTS = 64, MAX_HELPERS = 3, MAX_HISTORY = 64;
    private record Destruction(int stage, UUID area, String receipt) {}
    private record Member(int admittedStage, boolean active, boolean ready, boolean withdrawn, Integer retiredStage,
                          Destruction destruction, boolean suspended, int rejoinStage, Set<Integer> grants,
                          int legacyEnd, boolean resolved) {
        boolean working() { return active && ready && rejoinStage < 0; }
        boolean freshPending() { return active && !ready; }
    }
    private static final int UNSEALED = -2, NO_PREFIX = -1;
    private record Entry(long generation, String hash, UUID original, Map<UUID, Member> members) {}
    private final UUID ledgerGeneration;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public PerimeterBuilderCrew(UUID ledgerGeneration) {
        requireIdentity(ledgerGeneration); this.ledgerGeneration = ledgerGeneration;
    }

    public Set<UUID> active(PerimeterProject project) {
        Entry entry = entry(project);
        return entry == null ? Set.of() : entry.members().entrySet().stream()
                .filter(member -> member.getValue().working()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> members(PerimeterProject project) {
        Entry e = entry(project); return e == null ? Set.of() : e.members().keySet();
    }
    public boolean known(PerimeterProject project, UUID worker) {
        Entry entry = entry(project);
        return entry != null && entry.members().containsKey(worker);
    }

    public boolean active(PerimeterProject project, UUID worker) {
        Entry entry = entry(project);
        return entry != null && entry.members().containsKey(worker) && entry.members().get(worker).working();
    }

    public Set<UUID> pending(PerimeterProject project) {
        Entry entry = entry(project);
        return entry == null ? Set.of() : entry.members().entrySet().stream()
                .filter(member -> member.getValue().active() && !member.getValue().ready()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    public Set<UUID> reserved(PerimeterProject project) {
        Entry entry = entry(project);
        return entry == null ? Set.of() : entry.members().entrySet().stream()
                .filter(member -> member.getValue().active()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    public boolean pending(PerimeterProject project, UUID worker) {
        Entry entry = entry(project); Member member = entry == null ? null : entry.members().get(worker);
        return member != null && member.active() && !member.ready();
    }
    /** Read-only lookup for destructive removal before the worker-side selector has been saved. */
    public UUID pendingProject(UUID worker) {
        if (worker == null) return null;
        UUID found = null;
        for (var entry : entries.entrySet()) {
            Member member = entry.getValue().members().get(worker);
            if (member == null || !member.active() || member.ready()) continue;
            if (found != null) throw malformed();
            found = entry.getKey();
        }
        return found;
    }

    public Set<UUID> suspended(PerimeterProject project) {
        Entry entry = entry(project);
        return entry == null ? Set.of() : entry.members().entrySet().stream()
                .filter(e -> e.getValue().suspended()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    public boolean suspended(PerimeterProject project, UUID worker) {
        Member m = member(project, worker); return m != null && m.suspended();
    }
    public boolean rejoining(PerimeterProject project, UUID worker) {
        Member m = member(project, worker); return m != null && m.active() && m.rejoinStage() >= 0;
    }
    public int rejoinStage(PerimeterProject project, UUID worker) {
        Member m = member(project, worker); return m == null ? -1 : m.rejoinStage();
    }
    /** Historical ACTIVE membership is intentionally broader than current-stage work authority. */
    public boolean stageGranted(PerimeterProject project, UUID worker) {
        Member m = member(project, worker);
        return m != null && m.working() && m.grants().contains(project.activeStage());
    }
    public boolean resolved(PerimeterProject project, UUID worker) {
        Member m = member(project, worker); return m != null && m.resolved();
    }
    public boolean resolved(PerimeterTerminalReceipt terminal, UUID worker) {
        Entry e = entry(terminal); Member m = e == null ? null : e.members().get(worker);
        return m != null && m.resolved();
    }
    /** Independent reverse selector for recovery when an actor save already cleared its selector. */
    public UUID unresolvedProject(UUID worker) {
        if (worker == null) return null;
        UUID found = null;
        for (var e : entries.entrySet()) {
            Member m = e.getValue().members().get(worker);
            if (m == null || m.resolved()) continue;
            if (found != null) throw malformed();
            found = e.getKey();
        }
        return found;
    }
    private Member member(PerimeterProject project, UUID worker) {
        Entry e = entry(project); return e == null ? null : e.members().get(worker);
    }
    private void put(UUID project, Entry e, UUID worker, Member member) {
        Map<UUID, Member> members = new LinkedHashMap<>(e.members()); members.put(worker, member);
        entries.put(project, new Entry(e.generation(), e.hash(), e.original(), Map.copyOf(members)));
    }
    /** Seal once before controller stage progress or compaction; never grow history on later reads. */
    public boolean hasUnsealedLegacy(UUID worker) {
        return worker != null && entries.values().stream().anyMatch(e -> e.members().containsKey(worker)
                && e.members().get(worker).legacyEnd() == UNSEALED);
    }
    public boolean hasUnsealedLegacy(PerimeterProject project) {
        Entry e = entry(project); return e != null && e.members().values().stream().anyMatch(m -> m.legacyEnd() == UNSEALED);
    }
    public boolean sealLegacy(PerimeterProject project) { return sealLegacy(project, project.activeStage()); }
    public boolean sealLegacy(PerimeterProject project, int boundary) {
        if (boundary < 0 || boundary > project.activeStage()) throw malformed();
        Entry e = entry(project); return seal(project.header().projectId(), e, boundary, project.stages().size());
    }
    public boolean hasUnsealedLegacy(PerimeterTerminalReceipt terminal) {
        Entry e = entry(terminal); return e != null && e.members().values().stream().anyMatch(m -> m.legacyEnd() == UNSEALED);
    }
    public boolean sealLegacy(PerimeterTerminalReceipt terminal) { return sealLegacy(terminal, terminal.activeStage()); }
    public boolean sealLegacy(PerimeterTerminalReceipt terminal, int boundary) {
        if (boundary < 0 || boundary > terminal.activeStage()) throw malformed();
        Entry e = entry(terminal); return seal(terminal.projectId(), e, boundary, terminal.totalStageCount());
    }
    private boolean seal(UUID id, Entry e, int progress, int count) {
        if (e == null) return false;
        boolean changed = false; Map<UUID, Member> members = new LinkedHashMap<>(e.members());
        for (var saved : e.members().entrySet()) {
            Member m = saved.getValue(); if (m.legacyEnd() != UNSEALED) continue;
            int end = Math.min(progress, count - 1);
            if (m.admittedStage() > end) throw malformed();
            if (m.freshPending()) end = Math.min(end, m.admittedStage());
            if (m.retiredStage() != null) end = Math.min(end, m.retiredStage());
            if (m.destruction() != null) end = Math.min(end, m.destruction().stage());
            members.put(saved.getKey(), new Member(m.admittedStage(), m.active(), m.ready(), m.withdrawn(),
                    m.retiredStage(), m.destruction(), m.suspended(), m.rejoinStage(), m.grants(),
                    m.withdrawn() ? NO_PREFIX : end, m.resolved()));
            changed = true;
        }
        if (changed) entries.put(id, new Entry(e.generation(), e.hash(), e.original(), Map.copyOf(members)));
        return changed;
    }
    /** Absence revokes work, not identity or cleanup. Fresh pending admission remains separate. */
    public boolean suspend(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !m.active() || !m.ready() || m.resolved() || m.destruction() != null) return false;
        sealLegacy(project); e = entry(project); m = e.members().get(worker);
        put(project.header().projectId(), e, worker, new Member(m.admittedStage(), false, true, false,
                null, null, true, -1, m.grants(), m.legacyEnd(), false));
        return true;
    }
    /** Persist exact destination before any worker-side association write. Same intent is a no-op. */
    public boolean beginStage(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || project.payment() == null || project.active() == null
                || project.state() != PerimeterProject.State.RUNNING || m.resolved() || m.destruction() != null
                || m.withdrawn() || !m.ready() || !(m.active() || m.suspended())) return false;
        if (m.rejoinStage() >= 0) return m.rejoinStage() == project.activeStage();
        if (m.working() && m.grants().contains(project.activeStage())) return true;
        if (m.suspended() && e.members().values().stream().filter(Member::active).count() >= MAX_HELPERS) return false;
        UUID unresolved = unresolvedProject(worker);
        if (unresolved != null && !unresolved.equals(project.header().projectId())) return false;
        sealLegacy(project); e = entry(project); m = e.members().get(worker);
        Set<Integer> grants = new java.util.HashSet<>(m.grants()); grants.add(project.activeStage());
        put(project.header().projectId(), e, worker, new Member(m.admittedStage(), true, true, false,
                null, null, false, project.activeStage(), Set.copyOf(grants), m.legacyEnd(), false));
        return true;
    }
    /** Authenticated fresh-admission replay, including v2 intents that predate explicit grants. */
    public boolean preparePendingAdmission(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !m.freshPending() || m.resolved() || m.destruction() != null || m.withdrawn()
                || project.payment() == null || project.active() == null || project.state() != PerimeterProject.State.RUNNING
                || m.admittedStage() != project.activeStage()) return false;
        if (m.grants().contains(m.admittedStage())) return true;
        // Legacy cleanup compatibility cannot stand in for a new work grant. Keep this fresh,
        // pending and pinned to its original admission stage until ordinary activation-last.
        sealLegacy(project); e = entry(project); m = e.members().get(worker);
        put(project.header().projectId(), e, worker, new Member(m.admittedStage(), true, false, false,
                null, null, false, -1, Set.of(m.admittedStage()), m.legacyEnd(), false));
        return true;
    }
    /** Final admission step, after every worker-side hook, receipt and native assignment is verified. */
    public boolean activate(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !m.active() || project.payment() == null || project.state() != PerimeterProject.State.RUNNING
                || !m.grants().contains(project.activeStage())
                || (m.rejoinStage() >= 0 ? m.rejoinStage() : m.admittedStage()) != project.activeStage() && !m.working()) return false;
        if (m.working()) return true;
        put(project.header().projectId(), e, worker, new Member(m.admittedStage(), true, true, false,
                null, null, false, -1, m.grants(), m.legacyEnd(), false));
        return true;
    }
    /** Caller inspected and detached every fresh pending association; cannot erase older participation. */
    public boolean cancelPending(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !m.freshPending() || m.withdrawn() || m.destruction() != null) return false;
        put(project.header().projectId(), e, worker, new Member(m.admittedStage(), false, false, true,
                m.admittedStage(), null, false, -1, Set.of(), NO_PREFIX, true));
        return true;
    }
    /** Caller proves loaded exact cleanup. Historical identity remains a same-project tombstone. */
    public boolean resolve(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || project.state() != PerimeterProject.State.CANCELED && project.state() != PerimeterProject.State.COMPLETE
                && (m.active() || m.suspended())) return false;
        sealLegacy(project); e = entry(project); m = e.members().get(worker);
        return resolve(project.header().projectId(), e, worker, m, project.activeStage(), project.stages().size());
    }
    public boolean resolve(PerimeterTerminalReceipt terminal, UUID worker) {
        sealLegacy(terminal); Entry e = entry(terminal); Member m = e == null ? null : e.members().get(worker);
        return m != null && resolve(terminal.projectId(), e, worker, m, terminal.activeStage(), terminal.totalStageCount());
    }
    private boolean resolve(UUID id, Entry e, UUID worker, Member m, int stage, int count) {
        if (m.resolved()) return true;
        put(id, e, worker, new Member(m.admittedStage(), false, m.ready(), m.withdrawn(),
                m.retiredStage() == null ? Math.min(stage, count - 1) : m.retiredStage(), m.destruction(),
                false, -1, m.grants(), m.legacyEnd(), true));
        return true;
    }

    /** Geometry-free retained identity is cleanup authority only, never admission or placement authority. */
    public boolean known(PerimeterTerminalReceipt terminal, UUID worker) {
        Entry entry = entry(terminal);
        return entry != null && entry.members().containsKey(worker);
    }

    public boolean areaKnown(PerimeterProject project, UUID worker, UUID area) {
        Entry entry = entry(project); Member member = entry == null ? null : entry.members().get(worker);
        if (member == null || area == null) return false;
        return project.stages().stream().anyMatch(stage -> stage.areaId().equals(area)
                && knownStage(member, stage.index(), project.activeStage()));
    }
    public boolean areaKnown(PerimeterTerminalReceipt terminal, UUID worker, UUID area) {
        Entry entry = entry(terminal); Member member = entry == null ? null : entry.members().get(worker);
        if (member == null || area == null) return false;
        return terminal.stages().stream().anyMatch(stage -> stage.areaId().equals(area)
                && knownStage(member, stage.index(), terminal.activeStage()));
    }
    public UUID admissionArea(PerimeterProject project, UUID worker) {
        Entry entry = entry(project); Member member = entry == null ? null : entry.members().get(worker);
        return member == null || member.withdrawn() ? null : project.stages().get(member.admittedStage()).areaId();
    }
    public UUID admissionArea(PerimeterTerminalReceipt terminal, UUID worker) {
        Entry entry = entry(terminal); Member member = entry == null ? null : entry.members().get(worker);
        return member == null || member.withdrawn() ? null : terminal.stages().get(member.admittedStage()).areaId();
    }
    private static boolean knownStage(Member member, int stage, int progress) {
        if (member.withdrawn() || stage < member.admittedStage() || stage > progress) return false;
        if (member.grants().contains(stage)) return true;
        int end = member.legacyEnd() == UNSEALED ? progress : member.legacyEnd();
        return stage <= end && (member.retiredStage() == null || stage <= member.retiredStage())
                && (member.destruction() == null || stage <= member.destruction().stage());
    }

    /** Cross-file coordinator history cannot also authorize the same active helper identity. */
    public void verifyAssignments(PerimeterBuilderAssignments assignments) {
        if (assignments == null) throw malformed();
        entries.forEach((id, entry) -> {
            // A former coordinator is just as final as the current one. Checking only the
            // latest assignment would let a corrupted earlier promotion regain helper work.
            assignments.builder(id, entry.generation(), entry.hash(), entry.original());
            entry.members().forEach((worker, member) -> {
                if ((member.active() || member.suspended())
                        && assignments.known(id, entry.generation(), entry.hash(), entry.original(), worker)) throw malformed();
            });
        });
    }

    /** Caller authenticates an idle, loaded, exactly owned worker before saving a pending admission intent. */
    public boolean enlist(PerimeterProject project, UUID worker) {
        requireIdentity(worker);
        if (project.payment() == null || project.active() == null
                || project.state() != PerimeterProject.State.RUNNING
                || project.header().builder().equals(worker)) return false;
        Entry old = entry(project);
        if (old == null && entries.size() >= MAX_PROJECTS) return false;
        Map<UUID, Member> members = new LinkedHashMap<>(old == null ? Map.of() : old.members());
        // Never reactivate an old selector. A returning worker first completes its retained cleanup.
        if (members.containsKey(worker) || members.size() >= MAX_HISTORY
                || members.values().stream().filter(Member::active).count() >= MAX_HELPERS) return false;
        if (unresolvedProject(worker) != null) return false;
        members.put(worker, new Member(project.activeStage(), true, false, false, null, null, false, -1, Set.of(project.activeStage()), NO_PREFIX, false));
        entries.put(project.header().projectId(), new Entry(project.header().generation(), project.manifestHash(),
                project.header().builder(), Map.copyOf(members)));
        return true;
    }

    /** Final revocation; unlike suspension, this identity can never rejoin this project. */
    public boolean retire(PerimeterProject project, UUID worker) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !(m.active() || m.suspended())) return false;
        sealLegacy(project); e = entry(project); m = e.members().get(worker);
        put(project.header().projectId(), e, worker, new Member(m.admittedStage(), false, m.ready(), m.withdrawn(),
                Math.min(project.activeStage(), project.stages().size() - 1), m.destruction(), false, -1,
                m.grants(), m.legacyEnd(), m.resolved()));
        return true;
    }
    /** Caller authenticates an actual destructive event and the exact granted child identity. */
    public boolean destroyed(PerimeterProject project, UUID worker, UUID area, int stage) {
        Entry e = entry(project); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !(m.active() || m.suspended()) || m.resolved() || stage < 0 || stage >= project.stages().size()
                || !knownStage(m, stage, project.activeStage()) || !project.stages().get(stage).areaId().equals(area)
                || !m.ready() && stage != m.admittedStage()) return false;
        sealLegacy(project); e = entry(project); m = e.members().get(worker);
        return destroy(project.header().projectId(), e, worker, m, area, stage);
    }
    public boolean destroyed(PerimeterTerminalReceipt terminal, UUID worker, UUID area, int stage) {
        sealLegacy(terminal); Entry e = entry(terminal); Member m = e == null ? null : e.members().get(worker);
        if (m == null || !(m.active() || m.suspended()) || m.resolved() || stage < 0 || stage >= terminal.totalStageCount()
                || !knownStage(m, stage, terminal.activeStage()) || !terminal.stages().get(stage).areaId().equals(area)) return false;
        return destroy(terminal.projectId(), e, worker, m, area, stage);
    }
    private boolean destroy(UUID id, Entry e, UUID worker, Member m, UUID area, int stage) {
        String receipt = destructionReceipt(id, e, worker, m.admittedStage(), stage, area);
        put(id, e, worker, new Member(m.admittedStage(), false, m.ready(), false, stage,
                new Destruction(stage, area, receipt), false, -1, m.grants(), m.legacyEnd(), false));
        return true;
    }

    public String destructionReceipt(PerimeterProject project, UUID worker) {
        Entry entry = entry(project);
        Member member = entry == null ? null : entry.members().get(worker);
        return member == null || member.destruction() == null ? null : member.destruction().receipt();
    }

    public String destructionReceipt(PerimeterTerminalReceipt terminal, UUID worker) {
        Entry entry = entry(terminal);
        Member member = entry == null ? null : entry.members().get(worker);
        return member == null || member.destruction() == null ? null : member.destruction().receipt();
    }

    private Entry entry(PerimeterTerminalReceipt terminal) {
        if (terminal == null) throw malformed();
        if (!ledgerGeneration.equals(terminal.cleanup().ledgerGeneration())) return null;
        Entry entry = entries.get(terminal.projectId());
        if (entry != null && (entry.generation() != terminal.generation() || !entry.hash().equals(terminal.manifestHash())
                || !entry.original().equals(terminal.builder()) || terminal.payment() == null
                || entry.members().values().stream().anyMatch(member -> member.admittedStage() >= terminal.totalStageCount()
                    || member.admittedStage() > terminal.activeStage()
                    || invalidScope(member, terminal.activeStage(), terminal.totalStageCount())
                    || member.retiredStage() != null && (member.retiredStage() >= terminal.totalStageCount() || member.retiredStage() > terminal.activeStage())
                    || member.destruction() != null
                    && (member.destruction().stage() >= terminal.totalStageCount()
                    || member.destruction().stage() > terminal.activeStage()
                    || !terminal.stages().get(member.destruction().stage()).areaId().equals(member.destruction().area())))))
            throw malformed();
        return entry;
    }

    private Entry entry(PerimeterProject project) {
        if (project == null) throw new IllegalArgumentException("Missing perimeter project");
        Entry entry = entries.get(project.header().projectId());
        if (entry != null && (entry.generation() != project.header().generation()
                || !entry.hash().equals(project.manifestHash()) || !entry.original().equals(project.header().builder())
                || entry.members().values().stream().anyMatch(member -> member.admittedStage() >= project.stages().size()
                    || member.admittedStage() > project.activeStage()
                    || invalidScope(member, project.activeStage(), project.stages().size())
                    || member.retiredStage() != null && (member.retiredStage() >= project.stages().size() || member.retiredStage() > project.activeStage())
                    || member.destruction() != null
                    && (member.destruction().stage() >= project.stages().size()
                    || member.destruction().stage() > project.activeStage()
                    || !project.stages().get(member.destruction().stage()).areaId().equals(member.destruction().area())))))
            throw malformed();
        return entry;
    }

    private static boolean invalidScope(Member m, int progress, int count) {
        return m.rejoinStage() > progress || m.rejoinStage() >= count || m.legacyEnd() > progress || m.legacyEnd() >= count
                || m.grants().stream().anyMatch(stage -> stage > progress || stage >= count);
    }
    private static long[] bitmap(Set<Integer> stages) {
        long[] bits = new long[4]; for (int stage : stages) bits[stage / 64] |= 1L << (stage % 64); return bits;
    }
    private static Set<Integer> stages(long[] bitmap) {
        if (bitmap.length != 4) throw malformed();
        Set<Integer> result = new java.util.HashSet<>();
        for (int i = 0; i < PerimeterStageLayout.MAX_STAGES; i++)
            if ((bitmap[i / 64] & 1L << (i % 64)) != 0) result.add(i);
        return Set.copyOf(result);
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3); root.putUUID("LedgerGeneration", ledgerGeneration); ListTag projects = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag project = new CompoundTag();
            project.putUUID("Project", id); project.putLong("Generation", entry.generation());
            project.putString("Hash", entry.hash()); project.putUUID("Original", entry.original());
            ListTag members = new ListTag();
            entry.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(member -> {
                CompoundTag tag = new CompoundTag(); tag.putUUID("Worker", member.getKey());
                tag.putBoolean("Active", member.getValue().active()); tag.putBoolean("Ready", member.getValue().ready());
                tag.putBoolean("Withdrawn", member.getValue().withdrawn()); tag.putInt("AdmittedStage", member.getValue().admittedStage());
                tag.putInt("RetiredStage", member.getValue().retiredStage() == null ? -1 : member.getValue().retiredStage());
                tag.putBoolean("Suspended", member.getValue().suspended()); tag.putInt("RejoinStage", member.getValue().rejoinStage());
                tag.putLongArray("GrantedStages", bitmap(member.getValue().grants())); tag.putInt("LegacyEnd", member.getValue().legacyEnd());
                tag.putBoolean("Resolved", member.getValue().resolved());
                if (member.getValue().destruction() != null) {
                    Destruction destroyed = member.getValue().destruction(); CompoundTag proof = new CompoundTag();
                    proof.putInt("Stage", destroyed.stage()); proof.putUUID("Area", destroyed.area());
                    proof.putString("Receipt", destroyed.receipt()); tag.put("Destruction", proof);
                }
                members.add(tag);
            });
            project.put("Members", members); projects.add(project);
        });
        root.put("Projects", projects); return root;
    }

    public static PerimeterBuilderCrew load(CompoundTag root, UUID expectedGeneration) {
        if (root == null || !root.getAllKeys().equals(Set.of("Version", "LedgerGeneration", "Projects"))
                || !uuid(root, "LedgerGeneration") || !root.getUUID("LedgerGeneration").equals(expectedGeneration)
                || !root.contains("Version", Tag.TAG_INT) || root.getInt("Version") < 1 || root.getInt("Version") > 3) throw malformed();
        int version = root.getInt("Version");
        ListTag projects = compounds(root, "Projects");
        if (projects.size() > MAX_PROJECTS) throw malformed();
        PerimeterBuilderCrew crew = new PerimeterBuilderCrew(expectedGeneration);
        Set<UUID> activeWorkers = new java.util.HashSet<>();
        Set<UUID> unresolvedWorkers = new java.util.HashSet<>();
        for (Tag raw : projects) {
            CompoundTag project = (CompoundTag) raw;
            if (!project.getAllKeys().equals(Set.of("Project", "Generation", "Hash", "Original", "Members"))
                    || !uuid(project, "Project") || !uuid(project, "Original")
                    || !project.contains("Generation", Tag.TAG_LONG) || project.getLong("Generation") < 1
                    || !project.contains("Hash", Tag.TAG_STRING) || !project.getString("Hash").matches("[0-9a-f]{64}")) throw malformed();
            ListTag savedMembers = compounds(project, "Members");
            if (savedMembers.isEmpty() || savedMembers.size() > MAX_HISTORY) throw malformed();
            Map<UUID, Member> members = new LinkedHashMap<>(); int active = 0;
            for (Tag saved : savedMembers) {
                CompoundTag member = (CompoundTag) saved;
                Set<String> required = version == 1 ? Set.of("Worker", "Active", "AdmittedStage") : version == 2
                        ? Set.of("Worker", "Active", "Ready", "Withdrawn", "RetiredStage", "AdmittedStage")
                        : Set.of("Worker", "Active", "Ready", "Withdrawn", "RetiredStage", "AdmittedStage", "Suspended", "RejoinStage", "GrantedStages", "LegacyEnd", "Resolved");
                if (!member.getAllKeys().containsAll(required)
                        || member.getAllKeys().stream().anyMatch(key -> !required.contains(key) && !key.equals("Destruction"))
                        || !uuid(member, "Worker")
                        || !member.contains("AdmittedStage", Tag.TAG_INT) || member.getInt("AdmittedStage") < 0
                        || member.getInt("AdmittedStage") >= PerimeterStageLayout.MAX_STAGES
                        || !member.contains("Active", Tag.TAG_BYTE)
                        || member.getByte("Active") != 0 && member.getByte("Active") != 1
                        || member.getUUID("Worker").equals(project.getUUID("Original"))) throw malformed();
                boolean enabled = member.getBoolean("Active"); UUID worker = member.getUUID("Worker");
                boolean ready = version == 1 || member.getBoolean("Ready"), withdrawn = version != 1 && member.getBoolean("Withdrawn");
                if (version >= 2 && (!canonicalBoolean(member, "Ready") || !canonicalBoolean(member, "Withdrawn"))
                        || withdrawn && (enabled || ready)) throw malformed();
                Integer retiredStage = null;
                if (version >= 2) {
                    if (!member.contains("RetiredStage", Tag.TAG_INT)) throw malformed();
                    int savedRetirement = member.getInt("RetiredStage");
                    if (savedRetirement < -1 || savedRetirement >= PerimeterStageLayout.MAX_STAGES
                            || enabled && savedRetirement != -1
                            || savedRetirement >= 0 && savedRetirement < member.getInt("AdmittedStage")) throw malformed();
                    if (savedRetirement >= 0) retiredStage = savedRetirement;
                }
                if (withdrawn && (retiredStage == null || retiredStage != member.getInt("AdmittedStage"))) throw malformed();
                Destruction destruction = null;
                if (member.contains("Destruction")) {
                    CompoundTag proof = member.getCompound("Destruction");
                    if (enabled || withdrawn || !member.contains("Destruction", Tag.TAG_COMPOUND)
                            || !proof.getAllKeys().equals(Set.of("Stage", "Area", "Receipt")) || !uuid(proof, "Area")
                            || !proof.contains("Stage", Tag.TAG_INT) || proof.getInt("Stage") < member.getInt("AdmittedStage")
                            || !ready && proof.getInt("Stage") != member.getInt("AdmittedStage")
                            || proof.getInt("Stage") >= PerimeterStageLayout.MAX_STAGES
                            || !proof.contains("Receipt", Tag.TAG_STRING) || !proof.getString("Receipt").matches("[0-9a-f]{64}")) throw malformed();
                    destruction = new Destruction(proof.getInt("Stage"), proof.getUUID("Area"), proof.getString("Receipt"));
                    if (retiredStage != null && retiredStage != destruction.stage()) throw malformed();
                    retiredStage = destruction.stage();
                    Entry identity = new Entry(project.getLong("Generation"), project.getString("Hash"), project.getUUID("Original"), Map.of());
                    if (!destruction.receipt().equals(crew.destructionReceipt(project.getUUID("Project"), identity, worker,
                            member.getInt("AdmittedStage"), destruction.stage(), destruction.area()))) throw malformed();
                }
                boolean suspended = version == 3 && member.getBoolean("Suspended");
                boolean resolved = version == 3 ? member.getBoolean("Resolved") : !enabled;
                int rejoin = version == 3 ? member.getInt("RejoinStage") : -1;
                int legacy = version == 3 ? member.getInt("LegacyEnd") : withdrawn ? NO_PREFIX : UNSEALED;
                Set<Integer> grants = version == 3 ? stages(member.getLongArray("GrantedStages")) : Set.of();
                int admitted = member.getInt("AdmittedStage");
                if (version == 3 && (!canonicalBoolean(member, "Suspended") || !canonicalBoolean(member, "Resolved")
                        || !member.contains("RejoinStage", Tag.TAG_INT) || !member.contains("LegacyEnd", Tag.TAG_INT)
                        || !member.contains("GrantedStages", Tag.TAG_LONG_ARRAY)
                        || rejoin < -1 || rejoin >= PerimeterStageLayout.MAX_STAGES
                        || legacy < UNSEALED || legacy >= PerimeterStageLayout.MAX_STAGES
                        || legacy >= 0 && legacy < admitted
                        || grants.stream().anyMatch(stage -> stage < admitted)
                        || enabled && !ready && (legacy > admitted || grants.stream().anyMatch(stage -> stage != admitted))
                        || suspended && (enabled || !ready || withdrawn || destruction != null || retiredStage != null || resolved)
                        || rejoin >= 0 && (!enabled || !ready || suspended || resolved || !grants.contains(rejoin)
                            || grants.stream().anyMatch(stage -> stage > rejoin))
                        || resolved && (enabled || suspended || rejoin >= 0)
                        || withdrawn && (!grants.isEmpty() || legacy != NO_PREFIX || !resolved)
                        || legacy == NO_PREFIX && !withdrawn && !grants.contains(admitted)
                        || legacy == UNSEALED && (!grants.isEmpty() || suspended || rejoin >= 0)
                        || destruction != null && !knownStage(new Member(admitted, false, ready, false, retiredStage,
                            destruction, false, -1, grants, legacy, resolved), destruction.stage(), destruction.stage()))) throw malformed();
                if (members.putIfAbsent(worker, new Member(admitted, enabled, ready, withdrawn, retiredStage, destruction,
                        suspended, rejoin, grants, legacy, resolved)) != null
                        || enabled && (++active > MAX_HELPERS || !activeWorkers.add(worker))
                        || !resolved && !unresolvedWorkers.add(worker)) throw malformed();
            }
            if (crew.entries.putIfAbsent(project.getUUID("Project"), new Entry(project.getLong("Generation"),
                    project.getString("Hash"), project.getUUID("Original"), Map.copyOf(members))) != null) throw malformed();
        }
        return crew;
    }

    private String destructionReceipt(UUID project, Entry entry, UUID worker, int admittedStage, int stage, UUID area) {
        String evidence = "perimeter-crew-destroyed-v1:" + project + ":" + entry.generation() + ":" + entry.hash()
                + ":" + entry.original() + ":" + worker + ":" + admittedStage + ":" + stage + ":" + area + ":" + ledgerGeneration;
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean canonicalBoolean(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_BYTE) && (tag.getByte(key) == 0 || tag.getByte(key) == 1);
    }
    private static ListTag compounds(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_LIST)) throw malformed();
        ListTag list = (ListTag) parent.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw malformed();
        return list;
    }
    private static boolean uuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) && !tag.getUUID(key).equals(new UUID(0, 0));
    }
    private static void requireIdentity(UUID id) {
        if (id == null || id.equals(new UUID(0, 0))) throw malformed();
    }
    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("Malformed or conflicting perimeter crew identity");
    }
}
