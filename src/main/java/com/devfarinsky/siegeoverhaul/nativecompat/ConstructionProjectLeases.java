package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded child identities for one global reservation. This never grants world mutation authority. */
final class ConstructionProjectLeases {
    static final int MAX_PROJECTS = 64, MAX_STAGES = 256, MAX_ACTIVE_CELLS = 65_536;
    private record Project(long generation, String hash, List<UUID> stages, int active,
                           Set<Long> cells, boolean retired) {
        Project {
            stages = List.copyOf(stages); cells = Set.copyOf(cells);
        }
    }
    private final Map<UUID, Project> projects = new HashMap<>();
    private final Map<UUID, UUID> parents = new HashMap<>();

    boolean reservedIdentifier(UUID id) { return projects.containsKey(id) || parents.containsKey(id); }
    UUID parent(UUID child) { return parents.get(child); }
    Object identityToken(UUID project) { return projects.get(project); }
    Set<UUID> projectIds() { return Set.copyOf(projects.keySet()); }
    boolean identity(UUID id,long generation,String hash,List<UUID> stages) {
        Project project=projects.get(id);
        return identity(project,generation,hash) && project.stages().equals(stages);
    }
    int activeIndex(UUID id) { Project project=projects.get(id);return project==null?-2:project.active(); }

    boolean canRegister(UUID id, long generation, String hash, List<UUID> stages, Set<UUID> otherIdentifiers) {
        if (!validIdentity(id) || generation < 1 || hash == null || !hash.matches("[0-9a-f]{64}")
                || stages == null || stages.isEmpty() || stages.size() > MAX_STAGES
                || projects.size() >= MAX_PROJECTS || reservedIdentifier(id) || otherIdentifiers.contains(id)) return false;
        Set<UUID> unique = new HashSet<>();
        for (UUID stage : stages) if (!validIdentity(stage) || stage.equals(id) || !unique.add(stage)
                || reservedIdentifier(stage) || otherIdentifiers.contains(stage)) return false;
        return true;
    }

    boolean register(UUID id, long generation, String hash, List<UUID> stages, Set<UUID> otherIdentifiers) {
        if (!canRegister(id, generation, hash, stages, otherIdentifiers)) return false;
        projects.put(id, new Project(generation, hash, stages, -1, Set.of(), false));
        stages.forEach(child -> parents.put(child, id));
        return true;
    }

    /** Called only after the authoritative manifest validates the exact next stage subset. */
    boolean lease(UUID id, long generation, String hash, int index, UUID child,
                  Set<Long> cells, Set<Long> globalCells) {
        Project project = projects.get(id);
        if (!identity(project, generation, hash) || index < 0 || index >= project.stages().size()
                || !project.stages().get(index).equals(child) || cells == null || cells.isEmpty()
                || cells.size() > MAX_ACTIVE_CELLS || globalCells == null || !globalCells.containsAll(cells)) return false;
        if (index == project.active()) return !project.retired() && project.cells().equals(cells);
        if (index != project.active() + 1 || project.active() >= 0 && !project.retired()) return false;
        projects.put(id, new Project(generation, hash, project.stages(), index, cells, false));
        return true;
    }

    boolean matches(UUID child, Set<Long> cells) {
        UUID id = parents.get(child); Project project = projects.get(id);
        return active(project, child) && project.cells().equals(cells);
    }
    boolean matches(UUID id, long generation, String hash, int index, UUID child, Set<Long> cells) {
        Project project = projects.get(id);
        return identity(project, generation, hash) && project.active() == index
                && active(project, child) && project.cells().equals(cells);
    }
    boolean active(UUID child) { return active(projects.get(parents.get(child)), child); }
    boolean retired(UUID child) {
        Project project = projects.get(parents.get(child));
        if (project == null || child == null) return false;
        int index = project.stages().indexOf(child);
        return index >= 0 && (index < project.active() || index == project.active() && project.retired());
    }
    boolean retire(UUID child) {
        if (retired(child)) return true;
        UUID id = parents.get(child); Project project = projects.get(id);
        if (!active(project, child)) return false;
        projects.put(id, new Project(project.generation(), project.hash(), project.stages(),
                project.active(), project.cells(), true));
        return true;
    }
    UUID activeChild(UUID id) {
        Project project = projects.get(id);
        return project == null || project.active() < 0 ? null : project.stages().get(project.active());
    }
    void remove(UUID id) {
        Project project = projects.remove(id);
        if (project != null) project.stages().forEach(parents::remove);
    }

    private static boolean validIdentity(UUID id) { return id != null && !id.equals(new UUID(0, 0)); }
    private static boolean identity(Project project, long generation, String hash) {
        return project != null && project.generation() == generation && project.hash().equals(hash);
    }
    private static boolean active(Project project, UUID child) {
        return project != null && project.active() >= 0 && !project.retired()
                && project.stages().get(project.active()).equals(child);
    }

    CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 1); ListTag entries = new ListTag();
        projects.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Project project = entry.getValue(); CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", entry.getKey()); tag.putLong("Generation", project.generation()); tag.putString("Hash", project.hash());
            ListTag stages = new ListTag();
            project.stages().forEach(id -> { CompoundTag stage = new CompoundTag(); stage.putUUID("Id", id); stages.add(stage); });
            tag.put("Stages", stages); tag.putInt("Active", project.active()); tag.putBoolean("Retired", project.retired());
            tag.putLongArray("Cells", project.cells().stream().mapToLong(Long::longValue).sorted().toArray()); entries.add(tag);
        });
        root.put("Projects", entries); return root;
    }

    static ConstructionProjectLeases load(CompoundTag root, Map<UUID, Set<Long>> reservations,
                                          Set<UUID> retiredIdentifiers) {
        if (!root.getAllKeys().equals(Set.of("Version","Projects")) || !root.contains("Version",Tag.TAG_INT)
                || root.getInt("Version") != 1 || !root.contains("Projects", Tag.TAG_LIST)) throw invalid();
        ListTag entries = root.getList("Projects", Tag.TAG_COMPOUND);
        if (((ListTag) root.get("Projects")).size() != entries.size() || entries.size() > MAX_PROJECTS) throw invalid();
        ConstructionProjectLeases result = new ConstructionProjectLeases();
        for (Tag raw : entries) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.getAllKeys().equals(Set.of("Id","Generation","Hash","Stages","Active","Retired","Cells"))) throw invalid();
            if (!tag.hasUUID("Id") || !tag.contains("Generation", Tag.TAG_LONG) || !tag.contains("Hash", Tag.TAG_STRING)
                    || !tag.contains("Stages", Tag.TAG_LIST) || !tag.contains("Active", Tag.TAG_INT)
                    || !tag.contains("Retired", Tag.TAG_BYTE) || !tag.contains("Cells", Tag.TAG_LONG_ARRAY)) throw invalid();
            UUID id = tag.getUUID("Id"); Set<Long> global = reservations.get(id);
            ListTag encodedStages = tag.getList("Stages", Tag.TAG_COMPOUND);
            if (global == null || ((ListTag) tag.get("Stages")).size() != encodedStages.size()
                    || encodedStages.isEmpty() || encodedStages.size() > MAX_STAGES) throw invalid();
            java.util.ArrayList<UUID> stages = new java.util.ArrayList<>();
            for (Tag rawStage : encodedStages) {
                CompoundTag stage = (CompoundTag) rawStage; if (!stage.getAllKeys().equals(Set.of("Id")) || !stage.hasUUID("Id")) throw invalid(); stages.add(stage.getUUID("Id"));
            }
            Set<UUID> others = new HashSet<>(reservations.keySet()); others.remove(id); others.addAll(retiredIdentifiers);
            if (!result.register(id, tag.getLong("Generation"), tag.getString("Hash"), stages, others)) throw invalid();
            if (tag.getByte("Retired") != 0 && tag.getByte("Retired") != 1) throw invalid();
            int active = tag.getInt("Active"); boolean retired = tag.getBoolean("Retired"); long[] encoded = tag.getLongArray("Cells");
            if (encoded.length > MAX_ACTIVE_CELLS || active < -1 || active >= stages.size()) throw invalid();
            Set<Long> cells = new HashSet<>(); for (long cell : encoded) if (!cells.add(cell)) throw invalid();
            if (active == -1 && (!cells.isEmpty() || retired) || active >= 0 && (cells.isEmpty() || !global.containsAll(cells))) throw invalid();
            result.projects.put(id, new Project(tag.getLong("Generation"), tag.getString("Hash"), stages, active, cells, retired));
        }
        return result;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Malformed perimeter stage reservation history"); }
}
