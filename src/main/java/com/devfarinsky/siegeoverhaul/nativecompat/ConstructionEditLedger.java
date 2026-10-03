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
    private record Site(Set<Long> cells, Set<Long> edited, boolean builderDestroyed, boolean completeReservation) {}
    private final Map<UUID, Site> sites = new HashMap<>();
    private final Map<Long, Set<UUID>> index = new HashMap<>();
    private final Set<UUID> retired = new java.util.LinkedHashSet<>();
    private boolean invalid;
    private int totalCells;
    private UUID generation = UUID.randomUUID();

    static ConstructionEditLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ConstructionEditLedger::load,
                ConstructionEditLedger::new, NAME);
    }

    boolean register(UUID id, Set<BlockPos> positions) {
        if (invalid || incompleteReservations() || id == null || positions == null || sites.containsKey(id) || retired.contains(id) || sites.size() + retired.size() >= MAX_JOBS
                || positions.isEmpty() || positions.size() > MAX_CELLS - totalCells) return false;
        Set<Long> cells = new HashSet<>();
        positions.forEach(pos -> cells.add(pos.asLong()));
        add(id, new Site(Set.copyOf(cells), new HashSet<>(), false, true));
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
        return !invalid && site != null && site.completeReservation() && site.cells().size() == positions.size()
                && positions.stream().allMatch(pos -> site.cells().contains(pos.asLong()));
    }

    boolean contains(UUID id) { return !invalid && sites.containsKey(id); }
    boolean retired(UUID id) { return !invalid && id != null && retired.contains(id); }
    boolean canRetire(UUID id) {
        // Absence is never cancellation evidence, including failed unregistered
        // handoffs and a restored/missing history file.
        return !invalid && id != null && (sites.containsKey(id) || retired.contains(id));
    }
    void retire(UUID id, boolean workerCleaned) {
        if (!canRetire(id)) return;
        Site site = sites.get(id);
        boolean destroyed = site != null && site.builderDestroyed();
        remove(id);
        if (workerCleaned || destroyed) retired.remove(id);
        else retired.add(id);
        setDirty();
    }
    void acknowledgeRetirement(UUID id) { if (retired.remove(id)) setDirty(); }
    void builderDestroyed(UUID id) {
        Site site = sites.get(id);
        if (site != null && !site.builderDestroyed()) {
            sites.put(id, new Site(site.cells(), site.edited(), true, site.completeReservation())); setDirty();
        }
        acknowledgeRetirement(id);
    }
    UUID generation() { return generation; }
    boolean sameGeneration(UUID expected) { return !invalid && generation.equals(expected); }

    boolean reserves(java.util.Collection<BlockPos> cells) {
        return invalid || incompleteReservations() || cells == null || cells.stream().anyMatch(pos -> pos == null || index.containsKey(pos.asLong()));
    }

    private boolean incompleteReservations() {
        // Earlier draft ledgers indexed solids only. Preserve their cancellation receipts,
        // but never infer that unrecorded clearance is free while any such site remains.
        return sites.values().stream().anyMatch(site -> !site.completeReservation());
    }

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
        ListTag retiredJobs = root.getList("Retired", Tag.TAG_COMPOUND);
        if (root.getBoolean("Invalid") || !root.hasUUID("Generation") || jobs.size() + retiredJobs.size() > MAX_JOBS) {
            ledger.invalid = true;
            return ledger;
        }
        ledger.generation = root.getUUID("Generation");
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
            ledger.add(tag.getUUID("Id"), new Site(Set.copyOf(positions), edited, tag.getBoolean("BuilderDestroyed"),
                    tag.getInt("ReservationVersion") == AcceptedConstructionReservation.VERSION));
        }
        for (Tag entry : retiredJobs) {
            CompoundTag tag = (CompoundTag) entry;
            if (!tag.hasUUID("Id") || ledger.sites.containsKey(tag.getUUID("Id")) || !ledger.retired.add(tag.getUUID("Id"))) {
                ledger.invalid = true; break;
            }
        }
        return ledger;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        sites.forEach((id, site) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putBoolean("BuilderDestroyed", site.builderDestroyed());
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
        return root;
    }
}
