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
    private record Member(int admittedStage, boolean active) {}
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
        members.put(worker, new Member(project.activeStage(), true));
        entries.put(project.header().projectId(), new Entry(project.header().generation(), project.manifestHash(),
                project.header().builder(), Map.copyOf(members)));
        return true;
    }

    /** Revocation denies work immediately, but does not discard any worker's inventory or receipts. */
    public boolean retire(PerimeterProject project, UUID worker) {
        Entry old = entry(project);
        if (old == null || !old.members().containsKey(worker) || !old.members().get(worker).active()) return false;
        Map<UUID, Member> members = new LinkedHashMap<>(old.members());
        members.put(worker, new Member(old.members().get(worker).admittedStage(), false));
        entries.put(project.header().projectId(), new Entry(old.generation(), old.hash(), old.original(), Map.copyOf(members)));
        return true;
    }

    private Entry entry(PerimeterProject project) {
        if (project == null) throw new IllegalArgumentException("Missing perimeter project");
        Entry entry = entries.get(project.header().projectId());
        if (entry != null && (entry.generation() != project.header().generation()
                || !entry.hash().equals(project.manifestHash()) || !entry.original().equals(project.header().builder())
                || entry.members().values().stream().anyMatch(member -> member.admittedStage() >= project.stages().size()
                    || member.admittedStage() > project.activeStage())))
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
                tag.putBoolean("Active", member.getValue().active()); tag.putInt("AdmittedStage", member.getValue().admittedStage()); members.add(tag);
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
                if (!member.getAllKeys().equals(Set.of("Worker", "Active", "AdmittedStage")) || !uuid(member, "Worker")
                        || !member.contains("AdmittedStage", Tag.TAG_INT) || member.getInt("AdmittedStage") < 0
                        || member.getInt("AdmittedStage") >= PerimeterStageLayout.MAX_STAGES
                        || !member.contains("Active", Tag.TAG_BYTE)
                        || member.getByte("Active") != 0 && member.getByte("Active") != 1
                        || member.getUUID("Worker").equals(project.getUUID("Original"))) throw malformed();
                boolean enabled = member.getBoolean("Active"); UUID worker = member.getUUID("Worker");
                if (members.putIfAbsent(worker, new Member(member.getInt("AdmittedStage"), enabled)) != null
                        || enabled && (++active > MAX_HELPERS || !activeWorkers.add(worker))) throw malformed();
            }
            if (crew.entries.putIfAbsent(project.getUUID("Project"), new Entry(project.getLong("Generation"),
                    project.getString("Hash"), project.getUUID("Original"), Map.copyOf(members))) != null) throw malformed();
        }
        return crew;
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
