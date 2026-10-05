package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Bounded pre-dispatch/controller adapter for NEW reviewed local earthworks only. Not registered.
 * The store must retain/read back a callback fence before native invocation; an interrupted/uncertain
 * fence blocks all further callbacks. No production Store or admission provider is installed yet.
 */
public final class NativeEarthworksAdapter {
    public static final int MAX_STEPS = 256, MAX_OBSERVATIONS = 2_048, MAX_STACK_KEYS = 128, MAX_DROPS = 128;
    public static final String DIRT_ADAPTER = "siegeoverhaul:workers_2_0_3_single_dirt";
    public static final String DIRT_VERSION = "minecraft-1.20.1-workers-2.0.3-v1";
    public static final String DIRT_SOURCE = "f39da5ea44120a6d1e1cfec8e9900c3a39a620138b1cb7ce9a937875eba45a4d";
    public enum Result { WAITING, BLOCKED, MINING, STEP_OBSERVED, RECONCILE_REQUIRED, STAGE_BOUNDARY, TERMINAL }
    public record Cell(BlockState state, long editRevision) {
        public Cell { Objects.requireNonNull(state); if (editRevision < 0) throw invalid("Unknown edit revision"); }
    }
    /** Complete canonical stack metadata except Count and the separately captured damage value. */
    public record Stock(String item, String metadata, int damage) {
        public Stock {
            if (item == null || item.length() > 256 || metadata == null || metadata.length() > 8_192 || damage < 0)
                throw invalid("Unbounded native item evidence");
        }
    }
    public record Drop(Stock stock, int count) {
        public Drop { Objects.requireNonNull(stock); if (count < 1 || count > 64) throw invalid("Invalid ground stack"); }
    }
    public record Frame(Map<Long, Cell> cells, Map<Stock, Integer> stock, Map<UUID, Drop> drops, Map<UUID, Integer> experience) {
        public Frame {
            if (cells == null || cells.isEmpty() || cells.size() > MAX_OBSERVATIONS || stock == null || stock.size() > MAX_STACK_KEYS
                    || drops == null || drops.size() > MAX_DROPS || experience == null || experience.size() > MAX_DROPS) throw invalid("Unbounded callback snapshot");
            cells = Map.copyOf(cells); stock = Map.copyOf(stock); drops = Map.copyOf(drops); experience = Map.copyOf(experience);
            if (experience.values().stream().anyMatch(n -> n < 0)) throw invalid("Invalid ground experience");
            if (stock.values().stream().anyMatch(n -> n < 1 || n > 8_192)) throw invalid("Invalid independent inventory count");
        }
    }
    /** Immutable persistent references. The store owns the full before/after frames and their identities. */
    public record Ticket(String intentHash, long sequence, String beforeHash) {
        public Ticket { if (!digest(intentHash) || sequence < 1 || !digest(beforeHash)) throw invalid("Invalid callback fence"); }
    }
    public record State(PerimeterEarthworksJournal journal, Ticket inFlight) { public State { Objects.requireNonNull(journal); } }
    public record Outcome(Ticket ticket, Frame before, Frame after) {
        public Outcome { Objects.requireNonNull(ticket); Objects.requireNonNull(before); Objects.requireNonNull(after); }
    }
    public interface Store {
        State read();
        /** Must distinguish admitted live/quiesced saves from unclean or unknown cross-file recovery. */
        boolean recoveryAdmissionEstablished();
        /** Stored with the fence, so replacing/reloading the adapter cannot double-dispatch in one game tick. */
        long lastDispatchGameTime();
        /** Atomic in-process journal comparison/replacement; false/exception means no native dispatch. */
        boolean saveJournal(PerimeterEarthworksJournal.Check expected, PerimeterEarthworksJournal next);
        /** Must retain the complete immutable before frame and acknowledge its authoritative retained state before returning; setDirty is not fsync. */
        Ticket fence(PerimeterEarthworksJournal.Check expected, String intentHash, Frame before, long gameTime);
        /** Retain immutable observed evidence before journal advancement. A failure leaves the fence intact. */
        boolean recordOutcome(Ticket ticket, Frame after);
        /** Must authenticate the fence/evidence identity; null means the callback outcome remains unknown. */
        Outcome outcome(Ticket ticket);
        /** Atomic compare/finalize: retain audit evidence, replace the journal, then clear exactly this fence. */
        boolean finish(Ticket ticket, PerimeterEarthworksJournal.Check expected, PerimeterEarthworksJournal next);
    }
    public interface Port {
        long gameTime();
        int nativeTickCount();
        boolean nativeEligible();
        /** Exact reviewed authority, whole-project aggregate budget, all claims/dependencies, standing and next-step escape. */
        String admissionProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal);
        /** Complete loaded observations, independent inventory with a verified slot-5/main-hand alias, and ground items. */
        Frame snapshot(PerimeterEarthworksManifest manifest, PerimeterEarthworksManifest.Step step);
        /** False requests ordinary native supplies and waits. Never creates stock or interrupts storage/upkeep. */
        boolean suppliesReady(PerimeterEarthworksManifest.Step step);
        /** Native callback for precisely this one cell. No generic tick, FREE_AREA, queue scan or direct block write. */
        void invokeExact(PerimeterEarthworksManifest.Step step);
    }

    private final PerimeterEarthworksManifest manifest;
    private final Store store;
    private final Port port;
    private long lastDispatchTime = Long.MIN_VALUE;
    private String blocker = "";

    public NativeEarthworksAdapter(PerimeterEarthworksManifest manifest, Store store, Port port) {
        this.manifest = Objects.requireNonNull(manifest); this.store = Objects.requireNonNull(store); this.port = Objects.requireNonNull(port);
        if (manifest.steps().size() > MAX_STEPS || manifest.observations().size() > MAX_OBSERVATIONS)
            throw invalid("Local native work region exceeds the adapter bound");
        if (!nativePreparationOrder(manifest.steps())) throw invalid("Native supply preparation cannot serve the exact placement order");
        for (var step : manifest.steps()) {
            if (step.kind() == PerimeterEarthworksManifest.Kind.CUT && (!step.before().equals(Blocks.DIRT.defaultBlockState())
                    || !step.removal().adapter().equals(DIRT_ADAPTER) || !step.removal().version().equals(DIRT_VERSION)
                    || !step.removal().sourceDigest().equals(DIRT_SOURCE)))
                throw invalid("This native adapter supports only explicitly reviewed single-cell vanilla dirt removal");
            BlockPos target = BlockPos.of(step.pos());
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= 2
                        && !manifest.observations().containsKey(target.offset(x, y, z).asLong()))
                    throw invalid("Every bounded neighbor dependency must be in the reviewed observations");
            }
        }
    }
    /** Native PREPARE may otherwise find unrelated placeable stock and never request the active material. */
    static boolean nativePreparationOrder(java.util.List<PerimeterEarthworksManifest.Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            var active = steps.get(i); if (active.kind() == PerimeterEarthworksManifest.Kind.CUT) continue;
            int min = Integer.MAX_VALUE;
            for (int j = i; j < steps.size(); j++) if (steps.get(j).kind() != PerimeterEarthworksManifest.Kind.CUT)
                min = Math.min(min, BlockPos.of(steps.get(j).pos()).getY());
            for (int j = i; j < steps.size(); j++) {
                var pending = steps.get(j);
                if (pending.kind() != PerimeterEarthworksManifest.Kind.CUT && BlockPos.of(pending.pos()).getY() == min
                        && pending.after().getBlock().asItem() != active.after().getBlock().asItem()) return false;
            }
        }
        return true;
    }
    public String blocker() { return blocker; }

    /** One attempt at most per native cadence/game time. A repeated tick cannot accelerate native mining. */
    public Result tick() {
        try {
            State saved = store.read(); var journal = saved.journal();
            if (!store.recoveryAdmissionEstablished() || !journal.manifestHash().equals(manifest.hash()))
                return blocked(Result.RECONCILE_REQUIRED, "Lifecycle or exact manifest admission is unverified");
            if (saved.inFlight() != null) return blocked(Result.RECONCILE_REQUIRED, "Interrupted native callback needs evidence reconciliation");
            if (journal.state() == PerimeterEarthworksJournal.State.CANCELED || journal.state() == PerimeterEarthworksJournal.State.COMPLETE)
                return Result.TERMINAL;
            if (journal.state() != PerimeterEarthworksJournal.State.READY && journal.state() != PerimeterEarthworksJournal.State.PENDING)
                return Result.STAGE_BOUNDARY;
            String admission = port.admissionProblem(manifest, journal);
            if (admission != null) return blocked(Result.BLOCKED, admission);
            if (!port.nativeEligible()) return Result.WAITING;
            var step = manifest.steps().get(journal.nextStep());
            int cadence = step.kind() == PerimeterEarthworksManifest.Kind.CUT ? 10 : 5;
            if (port.nativeTickCount() % cadence != 0 || (lastDispatchTime == port.gameTime() || store.lastDispatchGameTime() == port.gameTime())) return Result.WAITING;
            if (!port.suppliesReady(step)) return Result.WAITING;
            Frame before = port.snapshot(manifest, step); requireExpected(journal, before);
            if (journal.state() == PerimeterEarthworksJournal.State.READY) {
                var pending = journal.arm(journal.check());
                if (!store.saveJournal(journal.check(), pending)) return blocked(Result.RECONCILE_REQUIRED, "Pending intent was not acknowledged by the retained store");
                journal = pending;
            }
            Ticket ticket = store.fence(journal.check(), journal.pending().hash(), before, port.gameTime());
            if (ticket == null || !ticket.intentHash().equals(journal.pending().hash()) || !ticket.beforeHash().equals(frameHash(before)))
                return blocked(Result.RECONCILE_REQUIRED, "Native callback fence was not acknowledged by the retained store");
            // Read back after persistence, then revalidate the actual first mutation target and all live gates.
            State fenced = store.read();
            if (!ticket.equals(fenced.inFlight()) || !journal.check().equals(fenced.journal().check())
                    || port.admissionProblem(manifest, journal) != null || !port.nativeEligible()
                    || !before.equals(port.snapshot(manifest, step)))
                return blocked(Result.RECONCILE_REQUIRED, "Native admission changed after the callback fence");
            lastDispatchTime = port.gameTime();
            port.invokeExact(step);
            Frame after = port.snapshot(manifest, step);
            validateOutcome(journal, before, after);
            if (!store.recordOutcome(ticket, after)) return blocked(Result.RECONCILE_REQUIRED, "Native result needs recorded reconciliation");
            return finish(journal, new Outcome(ticket, before, after));
        } catch (RuntimeException | LinkageError unavailable) {
            return blocked(Result.RECONCILE_REQUIRED, "Native callback or its evidence is unavailable; nothing was replayed");
        }
    }

    /** Read/reconcile only: this method never dispatches work, manufactures items or revives cancellation. */
    public Result reconcile() {
        try {
            State saved = store.read();
            if (!store.recoveryAdmissionEstablished() || !saved.journal().manifestHash().equals(manifest.hash()))
                return blocked(Result.RECONCILE_REQUIRED, "Unclean or foreign recovery evidence cannot authorize reconciliation");
            if (saved.inFlight() == null) return Result.WAITING;
            var journal = saved.journal();
            if (journal.state() == PerimeterEarthworksJournal.State.CANCELED)
                return blocked(Result.RECONCILE_REQUIRED, "Canceled callback evidence is retained for terminal accounting review");
            Outcome outcome = store.outcome(saved.inFlight());
            if (outcome == null || !saved.inFlight().equals(outcome.ticket()) || journal.pending() == null
                    || !journal.pending().hash().equals(outcome.ticket().intentHash())
                    || !frameHash(outcome.before()).equals(outcome.ticket().beforeHash()))
                return blocked(Result.RECONCILE_REQUIRED, "A callback outcome cannot be inferred from current world state");
            validateOutcome(journal, outcome.before(), outcome.after());
            var step = manifest.steps().get(journal.nextStep());
            if (!outcome.after().equals(port.snapshot(manifest, step)))
                return blocked(Result.RECONCILE_REQUIRED, "World or accounting changed after the recorded native outcome");
            return finish(journal, outcome);
        } catch (RuntimeException | LinkageError unavailable) {
            return blocked(Result.RECONCILE_REQUIRED, "Interrupted native evidence remains unresolved");
        }
    }
    private Result finish(PerimeterEarthworksJournal journal, Outcome outcome) {
        var step = manifest.steps().get(journal.nextStep());
        boolean completed = outcome.after().cells().get(step.pos()).state().equals(step.after());
        var next = completed ? journal.observe(journal.check(), journal.pending(), new PerimeterEarthworksJournal.Evidence(
                outcome.before().cells().get(step.pos()).state(), outcome.after().cells().get(step.pos()).state(),
                outcome.before().cells().get(step.pos()).editRevision(), outcome.after().cells().get(step.pos()).editRevision(),
                step.kind() == PerimeterEarthworksManifest.Kind.CUT ? 0 : 1,
                hash("native-earthworks-outcome-v2", outcome.ticket().intentHash(), outcome.ticket().sequence(),
                        frameHash(outcome.before()), frameHash(outcome.after())))) : journal;
        if (!store.finish(outcome.ticket(), journal.check(), next)) return blocked(Result.RECONCILE_REQUIRED, "Observed result was not finalized in the retained store");
        blocker = ""; return completed ? Result.STEP_OBSERVED : Result.MINING;
    }

    private void requireExpected(PerimeterEarthworksJournal journal, Frame frame) {
        if (!frame.experience().isEmpty()) throw invalid("Existing experience requires a full merged-orb accounting adapter");
        Map<Long, Cell> expected = new HashMap<>();
        manifest.observations().values().forEach(cell -> expected.put(cell.pos(), new Cell(cell.original(), cell.editRevision())));
        for (int i = 0; i < journal.nextStep(); i++) {
            var step = manifest.steps().get(i); expected.put(step.pos(), new Cell(step.after(), expected.get(step.pos()).editRevision()));
        }
        if (!frame.cells().equals(expected)) throw invalid("Original states, completed cells, dependencies or edit revisions changed");
    }
    private void validateOutcome(PerimeterEarthworksJournal journal, Frame before, Frame after) {
        requireExpected(journal, before); var step = manifest.steps().get(journal.nextStep());
        if (!before.experience().equals(after.experience())) throw invalid("Unsupported experience side effect");
        Map<Long, Cell> expected = new HashMap<>(before.cells()); Cell actual = after.cells().get(step.pos());
        if (actual == null || actual.editRevision() != before.cells().get(step.pos()).editRevision()) throw invalid("Player edit during native callback");
        boolean completed = actual.state().equals(step.after());
        if (!completed && !actual.state().equals(step.before())) throw invalid("Unexpected native target state");
        expected.put(step.pos(), actual);
        if (!expected.equals(after.cells())) throw invalid("Native mutation escaped its one reviewed cell");
        if (!completed) {
            if (!before.stock().equals(after.stock()) || !before.drops().equals(after.drops())) throw invalid("Items changed without the exact native target change");
            return;
        }
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT) validateDirtCut(before, after);
        else {
            if (!before.drops().equals(after.drops())) throw invalid("Placement created or changed ground drops");
            Map<Stock, Integer> remaining = new HashMap<>(before.stock());
            String item = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(step.after().getBlock()).toString();
            int consumed = 0;
            for (Stock key : before.stock().keySet()) {
                int delta = before.stock().get(key) - after.stock().getOrDefault(key, 0);
                if (delta != 0) {
                    if (delta != 1 || !key.item().equals(item) || ++consumed != 1) throw invalid("Wrong native construction consumption");
                    if (before.stock().get(key) == 1) remaining.remove(key); else remaining.put(key, before.stock().get(key) - 1);
                }
            }
            if (consumed != 1 || !remaining.equals(after.stock())) throw invalid("Native construction stock was not conserved");
        }
    }
    private static void validateDirtCut(Frame before, Frame after) {
        Map<UUID, Drop> newDrops = new HashMap<>(after.drops());
        before.drops().forEach((id, value) -> { if (!value.equals(newDrops.remove(id))) throw invalid("Existing ground items changed during mining"); });
        if (newDrops.size() != 1 || newDrops.values().stream().anyMatch(drop -> drop.count() != 1
                || !drop.stock().item().equals("minecraft:dirt") || !drop.stock().metadata().isEmpty() || drop.stock().damage() != 0))
            throw invalid("Native dirt mining did not produce exactly one ordinary dirt drop");
        Map<Stock, Integer> expected = new HashMap<>(before.stock());
        Stock tool = null;
        for (Stock key : before.stock().keySet()) if (key.item().equals("minecraft:iron_shovel")) {
            if (tool != null || before.stock().get(key) != 1) throw invalid("Ambiguous native shovel evidence"); tool = key;
        }
        if (tool == null) throw invalid("Native shovel evidence is missing");
        expected.remove(tool); expected.put(new Stock(tool.item(), tool.metadata(), tool.damage() + 1), 1);
        if (!expected.equals(after.stock())) throw invalid("Native tool wear or inventory conservation changed");
    }
    private Result blocked(Result result, String reason) { blocker = reason; return result; }
    static String frameHash(Frame frame) {
        var values = new java.util.ArrayList<Object>(); values.add("native-earthworks-frame-v2"); values.add("cells"); values.add(frame.cells().size());
        new TreeMap<>(frame.cells()).forEach((pos, cell) -> {
            values.add(pos); values.add(net.minecraft.nbt.NbtUtils.writeBlockState(cell.state()).toString()); values.add(cell.editRevision());
        });
        values.add("stock"); values.add(frame.stock().size());
        frame.stock().entrySet().stream().sorted(java.util.Comparator.comparing(entry -> stockKey(entry.getKey())))
                .forEach(entry -> { values.add(stockKey(entry.getKey())); values.add(entry.getValue()); });
        values.add("drops"); values.add(frame.drops().size());
        new TreeMap<>(frame.drops()).forEach((id, drop) -> { values.add(id); values.add(stockKey(drop.stock())); values.add(drop.count()); });
        values.add("experience"); values.add(frame.experience().size());
        new TreeMap<>(frame.experience()).forEach((id, amount) -> { values.add(id); values.add(amount); });
        return hash(values.toArray());
    }
    private static String stockKey(Stock value) { return hash(value.item(), value.metadata(), value.damage()); }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static String hash(Object... values) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                // Lossless Java UTF-16 code units: UTF-8 replacement would collapse distinct lone surrogates.
                String text = value.toString(); hash.update(ByteBuffer.allocate(4).putInt(text.length()).array());
                for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); hash.update((byte)(c >>> 8)); hash.update((byte)c); }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
