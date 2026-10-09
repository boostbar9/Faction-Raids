package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/** Assignment history is separate from the immutable, paid blueprint identity. */
public final class PerimeterBuilderAssignments {
    public static final int MAX_PROJECTS = 64, MAX_REPLACEMENTS = 64;
    public enum Cause { DEATH, UNAVAILABLE, DISMISSED }
    public record Handoff(UUID from, UUID to, UUID area, Cause cause, String receipt) {
        public Handoff(UUID from, UUID to, UUID area, String death) { this(from, to, area, Cause.DEATH, death); }
        /** An ownership transfer is never evidence that the previous coordinator died. */
        public String death() { return cause == Cause.DEATH ? receipt : null; }
    }
    private record Entry(long generation, String hash, UUID original, List<Handoff> history) {}
    private final Map<UUID, Entry> entries = new HashMap<>();

    public UUID builder(PerimeterProject project) {
        Entry entry = entries.get(project.header().projectId());
        if (entry == null) return project.header().builder();
        verify(project, entry);
        return entry.history().get(entry.history().size() - 1).to();
    }
    public UUID builder(UUID id, long generation, String hash, UUID original) {
        Entry entry=entries.get(id);
        if (entry == null) return original;
        if (entry.generation()!=generation || !entry.hash().equals(hash) || !entry.original().equals(original))
            throw new IllegalArgumentException("Assignment history conflicts with terminal project");
        return entry.history().get(entry.history().size()-1).to();
    }
    /** Retained identity only: previous coordinators must never regain work authority. */
    public boolean known(PerimeterProject project, UUID worker) {
        builder(project); // Verify the full paid identity and stage history first.
        Entry entry = entries.get(project.header().projectId());
        return project.header().builder().equals(worker)
                || entry != null && entry.history().stream().anyMatch(h -> h.to().equals(worker));
    }
    public boolean known(PerimeterTerminalReceipt terminal, UUID worker) {
        builder(terminal.projectId(), terminal.generation(), terminal.manifestHash(), terminal.builder());
        Entry entry = entries.get(terminal.projectId());
        if (entry != null) {
            int lastStage = -1;
            for (Handoff handoff : entry.history()) {
                int stage = terminal.stages().stream().filter(s -> s.areaId().equals(handoff.area())).findFirst().orElseThrow(PerimeterBuilderAssignments::malformed).index();
                if (stage < lastStage || stage > terminal.activeStage()) throw malformed();
                lastStage = stage;
            }
        }
        return terminal.builder().equals(worker)
                || entry != null && entry.history().stream().anyMatch(h -> h.to().equals(worker));
    }

    public UUID builderForArea(PerimeterProject project, UUID area) {
        Entry entry=entries.get(project.header().projectId());
        if (entry == null) return project.header().builder();
        verify(project,entry); int index=stageIndex(project,area); UUID builder=entry.original();
        for (Handoff handoff:entry.history()) {
            if (stageIndex(project,handoff.area())>index) break;
            builder=handoff.to();
        } return builder;
    }
    private static int stageIndex(PerimeterProject project,UUID area) {
        return project.stages().stream().filter(s->s.areaId().equals(area)).findFirst().orElseThrow().index();
    }
    public boolean canRebind(PerimeterProject project, UUID previous, UUID area) {
        Entry entry = entries.get(project.header().projectId());
        if (entry == null) return false;
        verify(project, entry);
        // A saved marker can lag several same-section replacements after a crash.
        return entry.history().stream().anyMatch(h -> h.from().equals(previous) && h.area().equals(area));
    }
    /** Caller authenticates the durable death receipt and loaded idle replacement first. */
    public void replace(PerimeterProject project, UUID expected, UUID replacement, UUID area, String death) {
        append(project, expected, replacement, area, Cause.DEATH, death);
    }
    /** Caller independently proves unavailability/dismissal and authenticates the loaded replacement helper. */
    public void transfer(PerimeterProject project, UUID expected, UUID replacement, UUID area, Cause cause, UUID ledgerGeneration) {
        if (cause == null || cause == Cause.DEATH || !identity(ledgerGeneration)) throw malformed();
        Entry identity = new Entry(project.header().generation(), project.manifestHash(), project.header().builder(), List.of());
        append(project, expected, replacement, area, cause,
                transferReceipt(project.header().projectId(), identity, expected, replacement, area, cause, ledgerGeneration));
    }
    private void append(PerimeterProject project, UUID expected, UUID replacement, UUID area, Cause cause, String receipt) {
        if (project.payment() == null || project.active() == null
                || project.state() == PerimeterProject.State.CANCELED || project.state() == PerimeterProject.State.COMPLETE
                || project.state() == PerimeterProject.State.RECOVERY_BLOCKED
                || !builder(project).equals(expected) || !identity(replacement) || replacement.equals(expected)
                || !(project.active().areaId().equals(area) || project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                    && project.activeStage()>0 && project.stages().get(project.activeStage()-1).areaId().equals(area)) || receipt == null || !receipt.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid replacement builder handoff");
        Entry old = entries.get(project.header().projectId());
        List<Handoff> history = new ArrayList<>(old == null ? List.of() : old.history());
        if (history.size() >= MAX_REPLACEMENTS || old == null && entries.size() >= MAX_PROJECTS
                || replacement.equals(project.header().builder())
                || history.stream().anyMatch(h -> h.to().equals(replacement)))
            throw new IllegalArgumentException("Replacement history is full or builder was already retired");
        if (!history.isEmpty() && stageIndex(project,history.get(history.size()-1).area())>stageIndex(project,area))
            throw new IllegalArgumentException("Replacement stage history moved backwards");
        history.add(new Handoff(expected, replacement, area, cause, receipt));
        entries.put(project.header().projectId(), new Entry(project.header().generation(), project.manifestHash(),
                project.header().builder(), List.copyOf(history)));
    }
    private static void verify(PerimeterProject p, Entry e) {
        if (p.header().generation() != e.generation() || !p.manifestHash().equals(e.hash())
                || !p.header().builder().equals(e.original())
                || e.history().stream().anyMatch(h -> p.stages().stream().noneMatch(s -> s.areaId().equals(h.area()))))
            throw new IllegalArgumentException("Assignment history conflicts with paid project");
        int lastStage = -1;
        for (Handoff handoff : e.history()) {
            int stage = stageIndex(p, handoff.area());
            if (stage < lastStage || stage > p.activeStage()) throw malformed();
            lastStage = stage;
        }
    }
    public CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 2); ListTag projects = new ListTag();
        entries.forEach((id, e) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Project", id); tag.putLong("Generation", e.generation());
            tag.putString("Hash", e.hash()); tag.putUUID("Original", e.original()); ListTag history = new ListTag();
            for (Handoff h : e.history()) {
                CompoundTag v = new CompoundTag(); v.putUUID("From", h.from()); v.putUUID("To", h.to());
                v.putUUID("Area", h.area()); v.putString("Cause", h.cause().name()); v.putString("Receipt", h.receipt()); history.add(v);
            }
            tag.put("History", history); projects.add(tag);
        }); root.put("Projects", projects); return root;
    }
    public static PerimeterBuilderAssignments load(CompoundTag root) {
        if (root == null || !root.getAllKeys().equals(Set.of("Version", "Projects")) || !root.contains("Version", Tag.TAG_INT)
                || (root.getInt("Version") != 1 && root.getInt("Version") != 2) || !root.contains("Projects", Tag.TAG_LIST)) throw malformed();
        int version = root.getInt("Version");
        ListTag projects = root.getList("Projects", Tag.TAG_COMPOUND);
        if (projects.size() != ((ListTag)root.get("Projects")).size() || projects.size() > MAX_PROJECTS) throw malformed();
        var result = new PerimeterBuilderAssignments();
        for (Tag raw : projects) {
            CompoundTag t = (CompoundTag)raw;
            if (!t.getAllKeys().equals(Set.of("Project", "Generation", "Hash", "Original", "History"))
                    || !uuid(t,"Project") || !uuid(t,"Original") || !t.contains("Generation",Tag.TAG_LONG)
                    || t.getLong("Generation") < 1 || !t.contains("Hash",Tag.TAG_STRING)
                    || !t.getString("Hash").matches("[0-9a-f]{64}") || !t.contains("History",Tag.TAG_LIST)) throw malformed();
            ListTag list = t.getList("History",Tag.TAG_COMPOUND);
            if (list.size() != ((ListTag)t.get("History")).size() || list.isEmpty() || list.size() > MAX_REPLACEMENTS) throw malformed();
            List<Handoff> history = new ArrayList<>(); Set<UUID> builders = new HashSet<>(); UUID from = t.getUUID("Original"); builders.add(from);
            for (Tag record : list) {
                CompoundTag v = (CompoundTag)record;
                Set<String> fields = version == 1 ? Set.of("From", "To", "Area", "Death") : Set.of("From", "To", "Area", "Cause", "Receipt");
                String receiptKey = version == 1 ? "Death" : "Receipt";
                if (!v.getAllKeys().equals(fields) || !uuid(v,"From") || !uuid(v,"To")
                        || !uuid(v,"Area") || !from.equals(v.getUUID("From")) || !builders.add(v.getUUID("To"))
                        || !v.contains(receiptKey,Tag.TAG_STRING) || !v.getString(receiptKey).matches("[0-9a-f]{64}")) throw malformed();
                Cause cause = Cause.DEATH;
                if (version == 2) {
                    if (!v.contains("Cause", Tag.TAG_STRING)) throw malformed();
                    try { cause = Cause.valueOf(v.getString("Cause")); }
                    catch (IllegalArgumentException unknown) { throw malformed(); }
                }
                history.add(new Handoff(from, v.getUUID("To"), v.getUUID("Area"), cause, v.getString(receiptKey))); from=v.getUUID("To");
            }
            if (result.entries.putIfAbsent(t.getUUID("Project"),new Entry(t.getLong("Generation"),t.getString("Hash"),
                    t.getUUID("Original"),List.copyOf(history))) != null) throw malformed();
        } return result;
    }
    /** Nondeath handoffs are scoped to this exact durable ledger generation. Legacy death chains are unchanged. */
    public void verifyTransferReceipts(UUID ledgerGeneration) {
        if (!identity(ledgerGeneration)) throw malformed();
        entries.forEach((id, entry) -> entry.history().forEach(h -> {
            if (h.cause() != Cause.DEATH && !h.receipt().equals(transferReceipt(id, entry, h.from(), h.to(), h.area(), h.cause(), ledgerGeneration)))
                throw malformed();
        }));
    }
    private static String transferReceipt(UUID project, Entry entry, UUID from, UUID to, UUID area, Cause cause, UUID ledgerGeneration) {
        String evidence = "perimeter-coordinator-transfer-v1:" + project + ":" + entry.generation() + ":" + entry.hash()
                + ":" + entry.original() + ":" + from + ":" + to + ":" + area + ":" + cause.name() + ":" + ledgerGeneration;
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean identity(UUID id) { return id != null && !id.equals(new UUID(0,0)); }
    private static boolean uuid(CompoundTag t,String key) { return t.hasUUID(key) && identity(t.getUUID(key)); }
    private static IllegalArgumentException malformed() { return new IllegalArgumentException("Malformed builder assignment history"); }
}
