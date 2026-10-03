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

/** Only commissioned cells are indexed, including while their marker chunk is unloaded. */
final class ConstructionEditLedger extends SavedData {
    static final int MAX_JOBS = 64, MAX_CELLS = 262144;
    private static final String NAME = "siege_construction_edits";
    private record Site(Set<Long> cells, Set<Long> edited) {}
    private final Map<UUID, Site> sites = new HashMap<>();
    private final Map<Long, Set<UUID>> index = new HashMap<>();
    private boolean invalid;
    private int totalCells;

    static ConstructionEditLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ConstructionEditLedger::load,
                ConstructionEditLedger::new, NAME);
    }

    boolean register(UUID id, Set<BlockPos> positions) {
        if (invalid || id == null || positions == null || sites.containsKey(id) || sites.size() >= MAX_JOBS
                || positions.isEmpty() || positions.size() > MAX_CELLS - totalCells) return false;
        Set<Long> cells = new HashSet<>();
        positions.forEach(pos -> cells.add(pos.asLong()));
        add(id, new Site(Set.copyOf(cells), new HashSet<>()));
        setDirty();
        return true;
    }

    private void add(UUID id, Site site) {
        sites.put(id, site);
        totalCells += site.cells().size();
        site.cells().forEach(pos -> index.computeIfAbsent(pos, ignored -> new HashSet<>()).add(id));
    }

    boolean matches(UUID id, Set<BlockPos> positions) {
        Site site = sites.get(id);
        return !invalid && site != null && site.cells().size() == positions.size()
                && positions.stream().allMatch(pos -> site.cells().contains(pos.asLong()));
    }

    boolean contains(UUID id) { return !invalid && sites.containsKey(id); }

    boolean edited(UUID id) {
        Site site = sites.get(id);
        return invalid || site == null || !site.edited().isEmpty();
    }

    void record(BlockPos pos) {
        for (UUID id : index.getOrDefault(pos.asLong(), Set.of())) {
            if (sites.get(id).edited().add(pos.asLong())) setDirty();
        }
    }

    void remove(UUID id) {
        Site site = sites.remove(id);
        if (site == null) return;
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
        ListTag jobs = root.getList("Sites", Tag.TAG_COMPOUND);
        if (root.getBoolean("Invalid") || jobs.size() > MAX_JOBS) {
            ledger.invalid = true;
            return ledger;
        }
        for (Tag entry : jobs) {
            CompoundTag tag = (CompoundTag) entry;
            long[] cells = tag.getLongArray("Cells"), edits = tag.getLongArray("Edited");
            if (!tag.hasUUID("Id") || cells.length == 0 || edits.length > cells.length
                    || cells.length > MAX_CELLS - ledger.totalCells
                    || ledger.sites.containsKey(tag.getUUID("Id"))) {
                ledger.invalid = true;
                break;
            }
            Set<Long> positions = new HashSet<>(), edited = new HashSet<>();
            for (long cell : cells) positions.add(cell);
            for (long cell : edits) edited.add(cell);
            if (positions.size() != cells.length || !positions.containsAll(edited)) {
                ledger.invalid = true;
                break;
            }
            ledger.add(tag.getUUID("Id"), new Site(Set.copyOf(positions), edited));
        }
        return ledger;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        sites.forEach((id, site) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putLongArray("Cells", site.cells().stream().mapToLong(Long::longValue).toArray());
            tag.putLongArray("Edited", site.edited().stream().mapToLong(Long::longValue).toArray());
            list.add(tag);
        });
        root.put("Sites", list);
        root.putBoolean("Invalid", invalid);
        return root;
    }
}
