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
    private record Member(int admittedStage, boolean active, Destruction destruction) {}
    private record Entry(long generation, String hash, UUID original, Map<UUID, Member> members) {}
    private final UUID ledgerGeneration;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public PerimeterBuilderCrew(UUID ledgerGeneration) {
        requireIdentity(ledgerGeneration); this.ledgerGeneration = ledgerGeneration;
    }

    public Set<UUID> active(PerimeterProject project) {
        Entry entry = entry(project);
        return entry == null ? Set.of() : entry.members().entrySet().stream()
                .filter(member -> member.getValue().active()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public boolean known(PerimeterProject project, UUID worker) {
        Entry entry = entry(project);
        return entry != null && entry.members().containsKey(worker);
    }

    public boolean active(PerimeterProject project, UUID worker) {
        Entry entry = entry(project);
        return entry != null && entry.members().containsKey(worker) && entry.members().get(worker).active();
    }

    /** Geometry-free retained identity is cleanup authority only, never admission or placement authority. */
    public boolean known(PerimeterTerminalReceipt terminal, UUID worker) {
        Entry entry = entry(terminal);
        return entry != null && entry.members().containsKey(worker);
    }

    /** Cross-file coordinator history cannot also authorize the same active helper identity. */
    public void verifyAssignments(PerimeterBuilderAssignments assignments) {
        if (assignments == null) throw malformed();
        entries.forEach((id, entry) -> {
            UUID coordinator = assignments.builder(id, entry.generation(), entry.hash(), entry.original());
            Member member = entry.members().get(coordinator);
            if (member != null && member.active()) throw malformed();
        });
    }

    /** Caller authenticates an idle, loaded, exactly owned worker before saving membership. */
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
        if (entries.values().stream().anyMatch(e -> e.members().containsKey(worker) && e.members().get(worker).active())) return false;
        members.put(worker, new Member(project.activeStage(), true, null));
        entries.put(project.header().projectId(), new Entry(project.header().generation(), project.manifestHash(),
                project.header().builder(), Map.copyOf(members)));
        return true;
    }

    /** Revocation denies work immediately, but does not discard any worker's inventory or receipts. */
    public boolean retire(PerimeterProject project, UUID worker) {
        Entry old = entry(project);
        if (old == null || !old.members().containsKey(worker) || !old.members().get(worker).active()) return false;
        Map<UUID, Member> members = new LinkedHashMap<>(old.members());
        members.put(worker, new Member(old.members().get(worker).admittedStage(), false, old.members().get(worker).destruction()));
        entries.put(project.header().projectId(), new Entry(old.generation(), old.hash(), old.original(), Map.copyOf(members)));
        return true;
    }

    /** Caller must independently authenticate destructive removal and the exact live/recently retired lease. */
    public boolean destroyed(PerimeterProject project, UUID worker, UUID area, int stage) {
        Entry old = entry(project);
        if (old == null || !old.members().containsKey(worker) || !old.members().get(worker).active()
                || stage < old.members().get(worker).admittedStage() || stage >= project.stages().size()
                || stage != project.activeStage() && stage != project.activeStage() - 1
                || !project.stages().get(stage).areaId().equals(area)) return false;
        Member member = old.members().get(worker);
        String receipt = destructionReceipt(project.header().projectId(), old, worker, member.admittedStage(), stage, area);
        Map<UUID, Member> members = new LinkedHashMap<>(old.members());
        members.put(worker, new Member(member.admittedStage(), false, new Destruction(stage, area, receipt)));
        entries.put(project.header().projectId(), new Entry(old.generation(), old.hash(), old.original(), Map.copyOf(members)));
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
                    || member.admittedStage() > terminal.activeStage() || member.destruction() != null
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
                    || member.admittedStage() > project.activeStage() || member.destruction() != null
                    && (member.destruction().stage() >= project.stages().size()
                    || member.destruction().stage() > project.activeStage()
                    || !project.stages().get(member.destruction().stage()).areaId().equals(member.destruction().area())))))
            throw malformed();
        return entry;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 1); root.putUUID("LedgerGeneration", ledgerGeneration); ListTag projects = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag project = new CompoundTag();
            project.putUUID("Project", id); project.putLong("Generation", entry.generation());
            project.putString("Hash", entry.hash()); project.putUUID("Original", entry.original());
            ListTag members = new ListTag();
            entry.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(member -> {
                CompoundTag tag = new CompoundTag(); tag.putUUID("Worker", member.getKey());
                tag.putBoolean("Active", member.getValue().active()); tag.putInt("AdmittedStage", member.getValue().admittedStage());
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
                || !root.contains("Version", Tag.TAG_INT) || root.getInt("Version") != 1) throw malformed();
        ListTag projects = compounds(root, "Projects");
        if (projects.size() > MAX_PROJECTS) throw malformed();
        PerimeterBuilderCrew crew = new PerimeterBuilderCrew(expectedGeneration);
        Set<UUID> activeWorkers = new java.util.HashSet<>();
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
                if (!member.getAllKeys().containsAll(Set.of("Worker", "Active", "AdmittedStage"))
                        || member.getAllKeys().stream().anyMatch(key -> !Set.of("Worker", "Active", "AdmittedStage", "Destruction").contains(key))
                        || !uuid(member, "Worker")
                        || !member.contains("AdmittedStage", Tag.TAG_INT) || member.getInt("AdmittedStage") < 0
                        || member.getInt("AdmittedStage") >= PerimeterStageLayout.MAX_STAGES
                        || !member.contains("Active", Tag.TAG_BYTE)
                        || member.getByte("Active") != 0 && member.getByte("Active") != 1
                        || member.getUUID("Worker").equals(project.getUUID("Original"))) throw malformed();
                boolean enabled = member.getBoolean("Active"); UUID worker = member.getUUID("Worker");
                Destruction destruction = null;
                if (member.contains("Destruction")) {
                    CompoundTag proof = member.getCompound("Destruction");
                    if (enabled || !member.contains("Destruction", Tag.TAG_COMPOUND)
                            || !proof.getAllKeys().equals(Set.of("Stage", "Area", "Receipt")) || !uuid(proof, "Area")
                            || !proof.contains("Stage", Tag.TAG_INT) || proof.getInt("Stage") < member.getInt("AdmittedStage")
                            || proof.getInt("Stage") >= PerimeterStageLayout.MAX_STAGES
                            || !proof.contains("Receipt", Tag.TAG_STRING) || !proof.getString("Receipt").matches("[0-9a-f]{64}")) throw malformed();
                    destruction = new Destruction(proof.getInt("Stage"), proof.getUUID("Area"), proof.getString("Receipt"));
                    Entry identity = new Entry(project.getLong("Generation"), project.getString("Hash"), project.getUUID("Original"), Map.of());
                    if (!destruction.receipt().equals(crew.destructionReceipt(project.getUUID("Project"), identity, worker,
                            member.getInt("AdmittedStage"), destruction.stage(), destruction.area()))) throw malformed();
                }
                if (members.putIfAbsent(worker, new Member(member.getInt("AdmittedStage"), enabled, destruction)) != null
                        || enabled && (++active > MAX_HELPERS || !activeWorkers.add(worker))) throw malformed();
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
