package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread-only core-compound transactions. No entity spawning, ledger inference, refund or stage payment.
 * The caller supplies the authoritative core object and its SavedData dirty callback.
 * This makes one SavedData update; it is not an atomic transaction across Minecraft save files.
 */
public final class PerimeterProjectStore {
    public static final String KEY = "PerimeterProjects";
    public static final int MAX_PROJECTS = 64, MAX_TOTAL_RESERVED = 262_144;
    /** At most 16 * MAX_STAGES identities, and one quarter of the existing reservation budget as stage IDs. */
    public static final int MAX_HISTORY_PROJECTS = 4_096, MAX_HISTORY_STAGE_IDS = MAX_TOTAL_RESERVED / 4;
    private PerimeterProjectStore() {}

    public enum PaymentStatus { PAID, ALREADY_PAID, INSUFFICIENT_FUNDS }
    /** A compact terminal retry has terminal != null and project == null; it never reconstructs active authority. */
    public record PaymentResult(PaymentStatus status, PerimeterProject project, PerimeterTerminalReceipt terminal) {
        public PaymentResult(PaymentStatus status, PerimeterProject project) { this(status, project, null); }
        public boolean paid() { return status != PaymentStatus.INSUFFICIENT_FUNDS; }
    }
    private record Contents(List<PerimeterProject> projects, List<PerimeterTerminalReceipt> terminals) {}

    /**
     * Validated immutable view with an O(1), identity-only freshness check. No raw mutable Tag escapes.
     * This store exclusively owns KEY: all writers must replace KEY via this API, never edit its children.
     * A runtime cache must drop snapshots on server stop; relocation/reload fail matches automatically.
     * A terminal receipt is a permanent denial of live authority, never an absent/reconstructable project.
     */
    public static final class Snapshot {
        private final CompoundTag authoritativeCore;
        private final Tag recordIdentity;
        private final Contents contents;
        private final java.util.Map<UUID, PerimeterTerminalReceipt> terminalIds, retiredAreas;
        private Snapshot(CompoundTag core, Tag identity, Contents contents) {
            this.authoritativeCore = core; this.recordIdentity = identity; this.contents = contents;
            var ids = new java.util.HashMap<UUID, PerimeterTerminalReceipt>();
            var areas = new java.util.HashMap<UUID, PerimeterTerminalReceipt>();
            for (PerimeterTerminalReceipt terminal : contents.terminals()) {
                ids.put(terminal.projectId(), terminal);
                for (var stage : terminal.stages()) areas.put(stage.areaId(), terminal);
            }
            this.terminalIds = java.util.Map.copyOf(ids); this.retiredAreas = java.util.Map.copyOf(areas);
        }
        public boolean matches(CompoundTag core) { return core == authoritativeCore && core.get(KEY) == recordIdentity; }
        public List<PerimeterProject> projects() { return contents.projects(); }
        public List<PerimeterTerminalReceipt> terminals() { return contents.terminals(); }
        public PerimeterProject get(UUID id) { return project(contents, id); }
        public PerimeterTerminalReceipt terminal(UUID id) { return terminalIds.get(id); }
        public PerimeterTerminalReceipt terminalForStage(UUID areaId) { return retiredAreas.get(areaId); }
        public boolean known(UUID id) { return get(id) != null || terminal(id) != null; }
    }
    public static Snapshot snapshot(CompoundTag core) {
        Contents contents = decode(core); return new Snapshot(core, core.get(KEY), contents);
    }

    /** Missing full data is never permission to reconstruct. Check terminal(core,id) for compact denials. */
    public static PerimeterProject get(CompoundTag core, UUID id) { return project(decode(core), Objects.requireNonNull(id)); }
    public static PerimeterTerminalReceipt terminal(CompoundTag core, UUID id) { return terminalRecord(decode(core), Objects.requireNonNull(id)); }
    /** Invalid full or terminal evidence invalidates the entire collection; no skipped/partial recovery. */
    public static List<PerimeterProject> all(CompoundTag core) { return decode(core).projects(); }
    public static List<PerimeterTerminalReceipt> terminals(CompoundTag core) { return decode(core).terminals(); }

    /** Caller must first establish there is no uncertain prior cross-file job using this identity. */
    public static PerimeterProject prepare(CompoundTag core, PerimeterProject proposed, Runnable markDirty) {
        Objects.requireNonNull(markDirty); Objects.requireNonNull(proposed);
        if (proposed.state() != PerimeterProject.State.PREPARED_UNPAID || proposed.payment() != null || proposed.revision() != 0)
            throw invalid("Only a fresh unpaid commission can be prepared");
        Contents contents = decode(core);
        if (terminalRecord(contents, proposed.header().projectId()) != null) throw invalid("A retired perimeter identity cannot be reused or reconstructed");
        List<PerimeterProject> projects = new ArrayList<>(contents.projects());
        for (PerimeterProject existing : projects) if (existing.header().projectId().equals(proposed.header().projectId())) {
            if (!existing.manifestHash().equals(proposed.manifestHash())) throw invalid("Conflicting perimeter commission identity");
            markDirty.run(); // Repair an interrupted dirty signal from the original successful write.
            return existing; // Includes cancellation/terminal evidence: never reset it.
        }
        projects.add(proposed); CompoundTag saved = encode(projects, contents.terminals());
        core.put(KEY, saved); markDirty.run(); return proposed;
    }

    /** Compare-and-set for a legal one-step successor. This cannot change a payment or resurrect a terminal. */
    public static PerimeterProject replace(CompoundTag core, PerimeterProject.Check expected,
                                           PerimeterProject replacement, Runnable markDirty) {
        Objects.requireNonNull(markDirty); Objects.requireNonNull(expected); Objects.requireNonNull(replacement);
        Contents contents = decode(core);
        if (terminalRecord(contents, expected.projectId()) != null) throw invalid("A compact terminal cannot be replaced by an old full-record save");
        List<PerimeterProject> projects = new ArrayList<>(contents.projects()); int index = find(projects, expected.projectId());
        PerimeterProject current = projects.get(index);
        boolean sameIdentity = current.header().projectId().equals(expected.projectId())
                && current.header().generation() == expected.generation() && current.manifestHash().equals(expected.manifestHash());
        boolean exactRetry = current.check().equals(expected) || expected.revision() >= 0
                && expected.revision() < Long.MAX_VALUE && expected.revision() + 1 == current.revision();
        if (sameIdentity && exactRetry && current.sameSnapshot(replacement)) {
            markDirty.run(); return current; // Retry the interrupted persistence signal only.
        }
        current.expect(expected); current.validateSuccessor(replacement);
        projects.set(index, replacement); CompoundTag saved = encode(projects, contents.terminals());
        core.put(KEY, saved); markDirty.run(); return replacement;
    }

    /**
     * Replace geometry only after separately verified complete cleanup. A matching tuple is not world proof.
     * Runtime owns verification of ledger generation, all frozen stage IDs and exact builder detachment.
     * Unknown/interrupted cleanup must not call this operation; full recovery geometry remains retained.
     */
    public static PerimeterTerminalReceipt compact(CompoundTag core, PerimeterProject.Check expected,
                                                   PerimeterTerminalReceipt.CleanupProof proof, Runnable markDirty) {
        Objects.requireNonNull(expected); Objects.requireNonNull(proof); Objects.requireNonNull(markDirty);
        Contents contents = decode(core); PerimeterTerminalReceipt existing = terminalRecord(contents, expected.projectId());
        if (existing != null) {
            if (!existing.check().equals(expected) || !existing.cleanup().equals(proof)) throw invalid("Conflicting terminal compaction retry");
            markDirty.run(); return existing;
        }
        List<PerimeterProject> projects = new ArrayList<>(contents.projects()); int index = find(projects, expected.projectId());
        PerimeterProject project = projects.get(index); project.expect(expected);
        PerimeterTerminalReceipt receipt = PerimeterTerminalReceipt.compact(project, proof);
        List<PerimeterTerminalReceipt> terminals = new ArrayList<>(contents.terminals()); terminals.add(receipt); projects.remove(index);
        CompoundTag saved = encode(projects, terminals); core.put(KEY, saved); markDirty.run(); return receipt;
    }

    /** One stored quote; explicit creative zero debit. Stage activation must never call this payment operation. */
    public static PaymentResult consumeOnce(CompoundTag core, UUID projectId, String manifestHash,
                                            int price, boolean creative, Runnable markDirty) {
        Objects.requireNonNull(markDirty); Objects.requireNonNull(projectId); Contents contents = decode(core);
        PerimeterTerminalReceipt terminal = terminalRecord(contents, projectId);
        if (terminal != null) {
            if (!terminal.manifestHash().equals(manifestHash) || terminal.quotedPrice() != price || terminal.payment() == null
                    || terminal.payment().creative() != creative) throw invalid("Conflicting or unpaid terminal payment retry");
            markDirty.run(); return new PaymentResult(PaymentStatus.ALREADY_PAID, null, terminal);
        }
        List<PerimeterProject> projects = new ArrayList<>(contents.projects()); int index = find(projects, projectId);
        PerimeterProject project = projects.get(index);
        if (!project.manifestHash().equals(manifestHash) || project.header().quotedPrice() != price) throw invalid("Conflicting perimeter payment request");
        if (project.payment() != null) {
            if (project.payment().creative() != creative) throw invalid("Conflicting perimeter payment mode");
            markDirty.run(); return new PaymentResult(PaymentStatus.ALREADY_PAID, project);
        }
        if (project.state() != PerimeterProject.State.PREPARED_UNPAID) throw invalid("Project cannot take another payment");
        if (!creative && FactionBank.balance(core) < price) return new PaymentResult(PaymentStatus.INSUFFICIENT_FUNDS, project);
        PerimeterProject paid = project.paid(creative); projects.set(index, paid);
        CompoundTag saved = encode(projects, contents.terminals()); // Serialize everything before touching the Treasury.
        CompoundTag debit = new CompoundTag();
        if (!creative) {
            if (core.contains("BankEmeralds") && !core.contains("BankEmeralds", Tag.TAG_LONG)
                    || core.contains("BankLedger") && !core.contains("BankLedger", Tag.TAG_LONG_ARRAY)) throw invalid("Malformed authoritative Treasury record");
            long[] ledger = core.getLongArray("BankLedger");
            if (ledger.length > FactionBank.LEDGER_MAX) throw invalid("Oversize Treasury debit record");
            debit.putLong("BankEmeralds", FactionBank.balance(core)); debit.putLongArray("BankLedger", ledger.clone());
            if (!FactionBank.debit(debit, price)) return new PaymentResult(PaymentStatus.INSUFFICIENT_FUNDS, project);
            FactionBank.record(debit, -price);
        }
        // One SavedData mutation; independent worker/marker/lease files still require reconciliation.
        if (!creative) {
            core.putLong("BankEmeralds", debit.getLong("BankEmeralds")); core.putLongArray("BankLedger", debit.getLongArray("BankLedger"));
        }
        core.put(KEY, saved); markDirty.run(); if (!creative) TreasuryNotifications.changed(core, -price);
        return new PaymentResult(PaymentStatus.PAID, paid);
    }

    private static Contents decode(CompoundTag core) {
        Objects.requireNonNull(core);
        if (!core.contains(KEY)) return new Contents(List.of(), List.of());
        if (!core.contains(KEY, Tag.TAG_COMPOUND)) throw invalid("Malformed perimeter project store");
        CompoundTag store = core.getCompound(KEY);
        if (!store.contains("Version", Tag.TAG_INT)) throw invalid("Missing perimeter project store version");
        int version = store.getInt("Version");
        if (version != 1 && version != 2 || !store.getAllKeys().equals(version == 1 ? Set.of("Version", "Entries") : Set.of("Version", "Entries", "Terminals")))
            throw invalid("Unknown perimeter project store version");
        ListTag entries = list(store, "Entries", MAX_PROJECTS);
        ListTag terminalTags = version == 1 ? new ListTag() : list(store, "Terminals", MAX_HISTORY_PROJECTS);
        if ((long) entries.size() + terminalTags.size() > MAX_HISTORY_PROJECTS) throw invalid("Perimeter history identity capacity reached");
        long cells = 0, stageIds = 0;
        // Bound aggregate work before decoding palettes or reconstructing any native stage.
        for (Tag raw : entries) {
            CompoundTag project = (CompoundTag) raw;
            if (!project.contains("Targets", Tag.TAG_LIST) || !project.contains("Clearance", Tag.TAG_LIST)) throw invalid("Missing project cells");
            cells += ((ListTag) project.get("Targets")).size() + (long) ((ListTag) project.get("Clearance")).size();
            stageIds += list(project, "Stages", PerimeterStageLayout.MAX_STAGES).size();
            if (cells > MAX_TOTAL_RESERVED || stageIds > MAX_HISTORY_STAGE_IDS) throw invalid("Perimeter storage budget exceeded");
        }
        for (Tag raw : terminalTags) {
            stageIds += list((CompoundTag) raw, "Stages", PerimeterStageLayout.MAX_STAGES).size();
            if (stageIds > MAX_HISTORY_STAGE_IDS) throw invalid("Perimeter retired-stage identity capacity reached");
        }
        List<PerimeterProject> projects = new ArrayList<>(); List<PerimeterTerminalReceipt> terminals = new ArrayList<>(); Set<UUID> identifiers = new HashSet<>();
        for (Tag raw : entries) {
            PerimeterProject project = PerimeterProject.load((CompoundTag) raw); unique(identifiers, project.header().projectId());
            for (var stage : project.stages()) unique(identifiers, stage.areaId()); projects.add(project);
        }
        for (Tag raw : terminalTags) {
            PerimeterTerminalReceipt terminal = PerimeterTerminalReceipt.load((CompoundTag) raw); unique(identifiers, terminal.projectId());
            for (var stage : terminal.stages()) unique(identifiers, stage.areaId()); terminals.add(terminal);
        }
        return new Contents(List.copyOf(projects), List.copyOf(terminals));
    }

    private static CompoundTag encode(List<PerimeterProject> projects, List<PerimeterTerminalReceipt> terminals) {
        if (projects.size() > MAX_PROJECTS || (long) projects.size() + terminals.size() > MAX_HISTORY_PROJECTS)
            throw invalid("Perimeter identity capacity reached; terminal evidence is never evicted");
        long cells = 0, stageIds = 0; Set<UUID> identifiers = new HashSet<>(); ListTag entries = new ListTag(), retired = new ListTag();
        // Full records also reserve eventual terminal metadata capacity before a new commission can be paid.
        for (PerimeterProject project : projects) {
            cells += project.targets().size() + (long) project.clearanceBefore().size(); stageIds += project.stages().size();
            if (cells > MAX_TOTAL_RESERVED || stageIds > MAX_HISTORY_STAGE_IDS) throw invalid("Perimeter full-record or history capacity reached");
            unique(identifiers, project.header().projectId()); for (var stage : project.stages()) unique(identifiers, stage.areaId()); entries.add(project.save());
        }
        for (PerimeterTerminalReceipt terminal : terminals) {
            stageIds += terminal.stages().size();
            if (stageIds > MAX_HISTORY_STAGE_IDS) throw invalid("Perimeter stage history capacity reached; no identities are evicted");
            unique(identifiers, terminal.projectId()); for (var stage : terminal.stages()) unique(identifiers, stage.areaId()); retired.add(terminal.save());
        }
        CompoundTag store = new CompoundTag(); store.putInt("Version", 2); store.put("Entries", entries); store.put("Terminals", retired); return store;
    }
    private static ListTag list(CompoundTag tag, String key, int max) {
        if (!tag.contains(key, Tag.TAG_LIST)) throw invalid("Missing perimeter record list: " + key);
        ListTag values = (ListTag) tag.get(key);
        if (values.size() > max || !values.isEmpty() && values.getElementType() != Tag.TAG_COMPOUND) throw invalid("Invalid perimeter record list: " + key);
        return values;
    }
    private static void unique(Set<UUID> seen, UUID id) {
        if (id == null || id.equals(new UUID(0, 0)) || !seen.add(id)) throw invalid("Reused perimeter project or area identity");
    }
    private static PerimeterProject project(Contents contents, UUID id) {
        for (PerimeterProject project : contents.projects()) if (project.header().projectId().equals(id)) return project; return null;
    }
    private static PerimeterTerminalReceipt terminalRecord(Contents contents, UUID id) {
        for (PerimeterTerminalReceipt terminal : contents.terminals()) if (terminal.projectId().equals(id)) return terminal; return null;
    }
    private static int find(List<PerimeterProject> projects, UUID id) {
        for (int i = 0; i < projects.size(); i++) if (projects.get(i).header().projectId().equals(id)) return i;
        throw invalid("Authoritative perimeter manifest is missing; recovery must remain blocked");
    }
    private static IllegalArgumentException invalid(String reason) { return PerimeterProject.invalid(reason); }
}
