package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksAssembly;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** New-job world authority. Never reads or writes the accepted v1 project format. */
final class EarthworksJobLedger extends SavedData {
    static final int MAX_JOBS = 16;
    private static final String NAME = "siege_earthworks_jobs_v1";
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private boolean invalid;

    static EarthworksJobLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(EarthworksJobLedger::load, EarthworksJobLedger::new, NAME);
    }
    Job job(UUID project) { return invalid ? null : jobs.get(project); }
    Job worker(UUID builder) {
        if (invalid) return null;
        return jobs.values().stream().filter(j -> j.manifest.header().builder().equals(builder)).findFirst().orElse(null);
    }
    boolean uncertain() { return invalid; }

    /** Internal preparation only. Paid authority is established by the controller after the real debit. */
    Job prepare(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal.Binding binding, UUID area, BlockPos core) {
        if (invalid || jobs.size() >= MAX_JOBS || area == null || area.equals(new UUID(0, 0)) || core == null
                || jobs.containsKey(manifest.header().project()) || worker(manifest.header().builder()) != null
                || jobs.values().stream().anyMatch(job -> job.area.equals(area)))
            throw new IllegalStateException("Earthworks ledger identity/capacity conflict");
        Job job = new Job(manifest, PerimeterEarthworksJournal.begin(manifest, binding), area, core);
        jobs.put(manifest.header().project(), job); setDirty(); return job;
    }

    final class Job implements NativeEarthworksAdapter.Store {
        final PerimeterEarthworksManifest manifest;
        final UUID area;
        final BlockPos core;
        final String aggregate;
        private PerimeterEarthworksJournal journal;
        private NativeEarthworksAdapter.Ticket fence;
        private NativeEarthworksAdapter.Frame before, after;
        private String supply = "", audit = "";
        private long sequence, lastDispatch = Long.MIN_VALUE;
        private boolean paid, live = true;

        private Job(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal, UUID area, BlockPos core) {
            this.manifest = manifest; this.journal = journal; this.area = area; this.core = core.immutable();
            if (manifest.steps().isEmpty() || manifest.steps().size() > NativeEarthworksAdapter.MAX_STEPS
                    || manifest.observations().size() > NativeEarthworksAdapter.MAX_OBSERVATIONS
                    || manifest.steps().stream().anyMatch(s -> s.kind() == PerimeterEarthworksManifest.Kind.BUILD))
                throw new IllegalArgumentException("Initial native slice accepts bounded local CUT/FILL only");
            // Recompute the aggregate from immutable inputs. An externally constructed Assembly is never authority.
            aggregate = PerimeterEarthworksAssembly.assemble(PerimeterEarthworksAssembly.Scope.from(manifest.header()),
                    List.of(manifest), List.of(), List.of(), 0).digest();
        }
        boolean paid() { return paid; }
        void acknowledgeDebit() { if (!live || paid) throw new IllegalStateException("Conflicting earthworks debit"); paid = true; setDirty(); }
        String supplyDigest() { return supply; }
        boolean compareSupplyDigest(String expected, String next) {
            if (!active() || !Objects.equals(supply, expected) || !digest(next)) return false;
            supply = next; setDirty(); return true;
        }
        boolean active() { return !invalid && live && paid && journal.state() != PerimeterEarthworksJournal.State.CANCELED
                && journal.state() != PerimeterEarthworksJournal.State.COMPLETE; }
        @Override public NativeEarthworksAdapter.State read() { return new NativeEarthworksAdapter.State(journal, fence); }
        @Override public boolean recoveryAdmissionEstablished() { return !invalid && live && paid; }
        @Override public long lastDispatchGameTime() { return lastDispatch; }
        @Override public boolean saveJournal(PerimeterEarthworksJournal.Check expected, PerimeterEarthworksJournal next) {
            if (!active() || fence != null || !journal.check().equals(expected) || !sameBinding(next)) return false;
            journal = next; setDirty(); return true;
        }
        @Override public NativeEarthworksAdapter.Ticket fence(PerimeterEarthworksJournal.Check expected, String intentHash,
                NativeEarthworksAdapter.Frame snapshot, long time) {
            if (!active() || fence != null || !journal.check().equals(expected) || journal.pending() == null
                    || !journal.pending().hash().equals(intentHash) || time <= lastDispatch || sequence == Long.MAX_VALUE)
                throw new IllegalStateException("Stale or concurrent native callback fence");
            // Encode before retaining, including all bounded evidence; a codec failure cannot partially arm a callback.
            frame(snapshot);
            fence = new NativeEarthworksAdapter.Ticket(intentHash, ++sequence, NativeEarthworksAdapter.frameHash(snapshot));
            before = snapshot; after = null; lastDispatch = time; setDirty(); return fence;
        }
        @Override public boolean recordOutcome(NativeEarthworksAdapter.Ticket expected, NativeEarthworksAdapter.Frame observed) {
            if (!active() || fence == null || !fence.equals(expected)) return false;
            if (after != null) return after.equals(observed);
            frame(observed); after = observed; setDirty(); return true;
        }
        @Override public NativeEarthworksAdapter.Outcome outcome(NativeEarthworksAdapter.Ticket expected) {
            return fence != null && fence.equals(expected) && after != null ? new NativeEarthworksAdapter.Outcome(fence, before, after) : null;
        }
        @Override public boolean finish(NativeEarthworksAdapter.Ticket expected, PerimeterEarthworksJournal.Check check,
                PerimeterEarthworksJournal next) {
            if (!active() || fence == null || !fence.equals(expected) || after == null || !journal.check().equals(check)
                    || !sameBinding(next)) return false;
            // A bounded chain retains completed/partial callback identity without saving every historical full frame.
            audit = NativeEarthworksAdapter.evidenceHash(audit, fence.toString(), NativeEarthworksAdapter.frameHash(after));
            journal = next; fence = null; before = after = null; setDirty(); return true;
        }
        private boolean sameBinding(PerimeterEarthworksJournal next) {
            return next != null && manifest.hash().equals(next.manifestHash()) && journal.binding().equals(next.binding());
        }
        void cancel() { journal = journal.cancel(journal.check(), "Canceled with callback and supply evidence retained"); setDirty(); } // Keep unresolved callback and supply evidence.
        private CompoundTag save() {
            CompoundTag out = new CompoundTag(); out.put("Manifest", manifest.save()); out.put("Journal", journal.save());
            out.putUUID("Area", area); out.putLong("Core", core.asLong()); out.putString("Aggregate", aggregate);
            out.putBoolean("Paid", paid); out.putString("Supply", supply); out.putString("Audit", audit);
            out.putLong("Sequence", sequence); out.putLong("LastDispatch", lastDispatch);
            if (fence != null) {
                out.putString("Intent", fence.intentHash()); out.putString("BeforeHash", fence.beforeHash());
                out.put("Before", frame(before)); if (after != null) out.put("After", frame(after));
            }
            return out;
        }
    }

    static EarthworksJobLedger load(CompoundTag tag) {
        var ledger = new EarthworksJobLedger();
        try {
            require(tag, "Version", Tag.TAG_INT); require(tag, "Jobs", Tag.TAG_LIST);
            if (tag.getInt("Version") != 1 || !tag.getAllKeys().equals(Set.of("Version", "Jobs"))) throw bad();
            var rows = list(tag, "Jobs", MAX_JOBS); Set<UUID> builders = new HashSet<>(), areas = new HashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag row = rows.getCompound(i);
                require(row, "Manifest", Tag.TAG_COMPOUND); require(row, "Journal", Tag.TAG_COMPOUND);
                var manifest = PerimeterEarthworksManifest.load(row.getCompound("Manifest"));
                if (!row.hasUUID("Area")) throw bad(); require(row, "Core", Tag.TAG_LONG);
                var job = ledger.new Job(manifest, PerimeterEarthworksJournal.load(manifest, row.getCompound("Journal")),
                        row.getUUID("Area"), BlockPos.of(row.getLong("Core")));
                require(row, "Paid", Tag.TAG_BYTE); require(row, "Sequence", Tag.TAG_LONG); require(row, "LastDispatch", Tag.TAG_LONG);
                job.paid = row.getBoolean("Paid"); job.sequence = row.getLong("Sequence"); job.lastDispatch = row.getLong("LastDispatch");
                job.supply = optionalDigest(row, "Supply"); job.audit = optionalDigest(row, "Audit");
                if (job.sequence < 0 || !job.aggregate.equals(row.getString("Aggregate"))) throw bad();
                if (row.contains("Before")) {
                    job.before = readFrame(row.getCompound("Before"));
                    job.fence = new NativeEarthworksAdapter.Ticket(row.getString("Intent"), job.sequence, row.getString("BeforeHash"));
                    if (job.journal.pending() == null || !job.journal.pending().hash().equals(job.fence.intentHash())
                            || !NativeEarthworksAdapter.frameHash(job.before).equals(job.fence.beforeHash())) throw bad();
                    if (row.contains("After")) job.after = readFrame(row.getCompound("After"));
                }
                if (!row.equals(job.save()) || !builders.add(manifest.header().builder()) || !areas.add(job.area)
                        || ledger.jobs.putIfAbsent(manifest.header().project(), job) != null) throw bad();
                // Disk ordering is not a transaction. No deserialized job runs until a separate controlled recovery proof exists.
                job.live = false;
            }
        } catch (RuntimeException | LinkageError malformed) { ledger.invalid = true; }
        return ledger;
    }
    @Override public CompoundTag save(CompoundTag out) {
        if (invalid) throw new IllegalStateException("Malformed earthworks authority must be preserved, not rewritten");
        out.putInt("Version", 1); ListTag rows = new ListTag(); jobs.values().forEach(job -> rows.add(job.save())); out.put("Jobs", rows); return out;
    }
    private static CompoundTag stock(NativeEarthworksAdapter.Stock stock) {
        CompoundTag out = new CompoundTag(); out.putString("Item", stock.item()); out.putString("Metadata", stock.metadata()); out.putInt("Damage", stock.damage()); return out;
    }
    private static NativeEarthworksAdapter.Stock readStock(CompoundTag row) {
        require(row, "Item", Tag.TAG_STRING); require(row, "Metadata", Tag.TAG_STRING); require(row, "Damage", Tag.TAG_INT);
        return new NativeEarthworksAdapter.Stock(row.getString("Item"), row.getString("Metadata"), row.getInt("Damage"));
    }
    private static CompoundTag frame(NativeEarthworksAdapter.Frame frame) {
        CompoundTag out = new CompoundTag(); ListTag cells = new ListTag(), stocks = new ListTag(), drops = new ListTag(), xp = new ListTag();
        new TreeMap<>(frame.cells()).forEach((pos, cell) -> { CompoundTag row = new CompoundTag(); row.putLong("Pos", pos); row.put("State", NbtUtils.writeBlockState(cell.state())); row.putLong("Revision", cell.editRevision()); cells.add(row); });
        frame.stock().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(NativeEarthworksAdapter.Stock::item).thenComparing(NativeEarthworksAdapter.Stock::metadata).thenComparingInt(NativeEarthworksAdapter.Stock::damage))).forEach(e -> { CompoundTag row = stock(e.getKey()); row.putInt("Count", e.getValue()); stocks.add(row); });
        new TreeMap<>(frame.drops()).forEach((id, drop) -> { CompoundTag row = stock(drop.stock()); row.putUUID("Id", id); row.putInt("Count", drop.count()); drops.add(row); });
        new TreeMap<>(frame.experience()).forEach((id, count) -> { CompoundTag row = new CompoundTag(); row.putUUID("Id", id); row.putInt("Count", count); xp.add(row); });
        out.put("Cells", cells); out.put("Stock", stocks); out.put("Drops", drops); out.put("Experience", xp); return out;
    }
    private static NativeEarthworksAdapter.Frame readFrame(CompoundTag tag) {
        Map<Long, NativeEarthworksAdapter.Cell> cells = new HashMap<>(); Map<NativeEarthworksAdapter.Stock, Integer> stocks = new HashMap<>();
        Map<UUID, NativeEarthworksAdapter.Drop> drops = new HashMap<>(); Map<UUID, Integer> xp = new HashMap<>();
        for (Tag value : list(tag, "Cells", NativeEarthworksAdapter.MAX_OBSERVATIONS)) { CompoundTag row = (CompoundTag)value; require(row,"Pos",Tag.TAG_LONG); require(row,"Revision",Tag.TAG_LONG); require(row,"State",Tag.TAG_COMPOUND);
            var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), row.getCompound("State"));
            if (!NbtUtils.writeBlockState(state).equals(row.getCompound("State")) || cells.putIfAbsent(row.getLong("Pos"), new NativeEarthworksAdapter.Cell(state, row.getLong("Revision"))) != null) throw bad(); }
        for (Tag value : list(tag,"Stock",NativeEarthworksAdapter.MAX_STACK_KEYS)) { CompoundTag row=(CompoundTag)value; require(row,"Count",Tag.TAG_INT); if(stocks.putIfAbsent(readStock(row),row.getInt("Count"))!=null)throw bad(); }
        for (Tag value : list(tag,"Drops",NativeEarthworksAdapter.MAX_DROPS)) { CompoundTag row=(CompoundTag)value; require(row,"Count",Tag.TAG_INT); if(!row.hasUUID("Id") || drops.putIfAbsent(row.getUUID("Id"),new NativeEarthworksAdapter.Drop(readStock(row),row.getInt("Count")))!=null)throw bad(); }
        for (Tag value : list(tag,"Experience",NativeEarthworksAdapter.MAX_DROPS)) { CompoundTag row=(CompoundTag)value; require(row,"Count",Tag.TAG_INT); if(!row.hasUUID("Id") || xp.putIfAbsent(row.getUUID("Id"),row.getInt("Count"))!=null)throw bad(); }
        var result = new NativeEarthworksAdapter.Frame(cells, stocks, drops, xp); if(!frame(result).equals(tag))throw bad(); return result;
    }
    private static ListTag list(CompoundTag tag, String key, int bound) { require(tag,key,Tag.TAG_LIST); ListTag list=(ListTag)tag.get(key); if(list.size()>bound || !list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND)throw bad(); return list; }
    private static String optionalDigest(CompoundTag tag,String key) { require(tag,key,Tag.TAG_STRING); String value=tag.getString(key); if(!value.isEmpty()&&!digest(value))throw bad(); return value; }
    private static boolean digest(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
    private static void require(CompoundTag tag,String key,int type) { if(!tag.contains(key,type))throw bad(); }
    private static IllegalArgumentException bad() { return new IllegalArgumentException("Malformed earthworks world ledger"); }
}
