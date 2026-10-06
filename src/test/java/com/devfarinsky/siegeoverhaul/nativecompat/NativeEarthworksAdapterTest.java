package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest.*;
import static com.devfarinsky.siegeoverhaul.nativecompat.NativeEarthworksAdapter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Controller/contract fixtures only: fake callbacks are explicitly not native companion-mod acceptance. */
class NativeEarthworksAdapterTest extends MinecraftTestSupport {
    private static final long CUT = new BlockPos(0, 64, 0).asLong(), FILL = new BlockPos(1, 63, 0).asLong();
    private static final Stock SHOVEL = new Stock("minecraft:iron_shovel", "", 0), DIRT = new Stock("minecraft:dirt", "", 0);
    private static final Stock WALL = new Stock("minecraft:cobblestone", "", 0);

    @Test void exactDirtThenFillThenBuildConservesToolsStockAndDropsWithObservedReceipts() {
        var f = new Fixture();
        for (int stage = 0; stage < 3; stage++) {
            assertEquals(Result.STEP_OBSERVED, f.adapter.tick());
            assertEquals(stage + 1, f.store.journal.receipts().size());
            var verified = f.store.journal; assertEquals(PerimeterEarthworksJournal.State.STAGE_VERIFIED, verified.state());
            assertEquals(Result.STAGE_BOUNDARY, f.adapter.tick());
            f.store.saveJournal(verified.check(), verified.retireStage(verified.check(), "e".repeat(64)));
            f.port.time += 10; f.port.tick += 10;
        }
        assertEquals(3, f.port.calls); assertEquals(1, f.port.frame.stock().size());
        assertEquals(Map.of(new Stock(SHOVEL.item(), "", 1), 1), f.port.frame.stock());
        assertEquals(1, f.port.frame.drops().values().stream().mapToInt(Drop::count).sum());
        assertEquals(2, f.store.journal.receipts().stream().mapToInt(PerimeterEarthworksJournal.Receipt::consumedMaterialItems).sum());
        assertEquals(PerimeterEarthworksJournal.State.VERIFYING, f.store.journal.state());
        assertEquals(Result.STAGE_BOUNDARY, f.adapter.tick());
    }

    @Test void nativeCadenceAndStoredDispatchTimePreventAcceleratedOrReplacementAdapterPulses() {
        var f = new Fixture(); f.port.completeAt = 3; f.port.tick = 5; f.port.time = 5;
        assertEquals(Result.WAITING, f.adapter.tick()); assertEquals(0, f.port.calls);
        f.port.tick = 10; f.port.time = 10; assertEquals(Result.MINING, f.adapter.tick());
        assertEquals(Result.WAITING, f.adapter.tick());
        assertEquals(Result.WAITING, new NativeEarthworksAdapter(f.manifest, f.store, f.port).tick());
        assertEquals(1, f.port.calls); assertTrue(f.store.journal.receipts().isEmpty());
        f.port.tick = 20; f.port.time = 20; assertEquals(Result.MINING, f.adapter.tick());
        f.port.tick = 30; f.port.time = 30; assertEquals(Result.STEP_OBSERVED, f.adapter.tick());
        assertEquals(3, f.port.calls); assertEquals(1, f.store.journal.receipts().size());
    }

    @Test void settledPartialDigSurvivesControlledJournalReloadUsingSameIntentAndNoExtraWear() {
        var f = new Fixture(); f.port.completeAt = 2; assertEquals(Result.MINING, f.adapter.tick());
        var intent = f.store.journal.pending(); Frame untouched = f.port.frame;
        f.store.journal = PerimeterEarthworksJournal.load(f.manifest, f.store.journal.save());
        f.port.pulses = 0; // The real native worker's unsaved break counters restart; this is a labeled contract fixture.
        var reloaded = new NativeEarthworksAdapter(f.manifest, f.store, f.port);
        f.port.tick = 20; f.port.time = 20; assertEquals(Result.MINING, reloaded.tick());
        assertEquals(intent, f.store.journal.pending()); assertEquals(untouched, f.port.frame);
        f.port.tick = 30; f.port.time = 30; assertEquals(Result.STEP_OBSERVED, reloaded.tick());
        assertEquals(1, f.store.journal.receipts().size()); assertEquals(1, f.port.frame.drops().size());
    }

    @Test void uncleanRecoveryAndIncompleteFenceNeverInferSuccessFromBeforeOrAfterWorldState() {
        var f = new Fixture(); f.store.recovery = false;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(0, f.port.calls);
        f.store.recovery = true; f.store.failOutcome = true;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(1, f.port.calls);
        assertEquals(Blocks.AIR.defaultBlockState(), f.port.frame.cells().get(CUT).state());
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.reconcile());
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(1, f.port.calls);
        f.port.frame = f.store.before;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.reconcile()); assertEquals(1, f.port.calls);
    }

    @Test void recordedOutcomeReconcilesOnceAfterFinalizationFailureWithoutAnotherNativeCallback() {
        var f = new Fixture(); f.store.failFinish = true;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(1, f.port.calls);
        assertNotNull(f.store.outcome); assertTrue(f.store.journal.receipts().isEmpty());
        f.store.failFinish = false; assertEquals(Result.STEP_OBSERVED, f.adapter.reconcile());
        assertEquals(1, f.store.journal.receipts().size()); assertEquals(1, f.port.calls);
        assertEquals(Result.WAITING, f.adapter.reconcile()); assertEquals(1, f.port.calls);
    }

    @Test void cancellationRetainsAmbiguousWorldChangeAndNeverReplaysOrRefundsIt() {
        var f = new Fixture(); f.store.failFinish = true; f.adapter.tick(); Frame changed = f.port.frame;
        var pending = f.store.journal; f.store.saveJournal(pending.check(), pending.cancel(pending.check(), "Canceled"));
        f.store.failFinish = false;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.reconcile());
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick());
        assertEquals(changed, f.port.frame); assertEquals(1, f.port.calls); assertNotNull(f.store.ticket);
        assertTrue(f.store.journal.receipts().isEmpty()); assertNotNull(f.store.journal.pending());
    }

    @Test void claimStandingEscapeAndLateEligibilityFailuresPreventTheActualMutationCallback() {
        for (String reason : List.of("Outside construction claim", "Standing on the cut support", "Fill blocks the only exit",
                "Body crosses unloaded chunk", "Two-block cliff", "Stale route endpoint", "Body overlaps target", "Native inventory authority missing")) {
            var f = new Fixture(); f.port.problem = reason; Frame before = f.port.frame;
            assertEquals(Result.BLOCKED, f.adapter.tick()); assertEquals(0, f.port.calls); assertEquals(before, f.port.frame);
            assertEquals(PerimeterEarthworksJournal.State.READY, f.store.journal.state());
        }
        var f = new Fixture(); f.store.afterFence = () -> f.port.eligible = false;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(0, f.port.calls); assertNotNull(f.store.ticket);
    }

    @Test void nativeVetoCollateralChangeBadDropsAndWrongToolWearRemainFencedWithoutReceipt() {
        for (String mode : List.of("veto", "collateral", "drops", "wear", "experience")) {
            var f = new Fixture(); f.port.mode = mode;
            assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick(), mode); assertEquals(1, f.port.calls, mode);
            assertTrue(f.store.journal.receipts().isEmpty(), mode); assertNotNull(f.store.ticket, mode);
            f.port.time += 10; f.port.tick += 10;
            assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick(), mode); assertEquals(1, f.port.calls, mode);
        }
    }

    @Test void originalEditRevisionOrCompletedWorldRollbackCannotBecomeAnotherCut() {
        var changed = new Fixture();
        var cells = new HashMap<>(changed.port.frame.cells()); cells.put(CUT, new Cell(Blocks.DIRT.defaultBlockState(), 1));
        changed.port.frame = new Frame(cells, changed.port.frame.stock(), Map.of(), Map.of());
        assertEquals(Result.RECONCILE_REQUIRED, changed.adapter.tick()); assertEquals(0, changed.port.calls);
        var f = new Fixture(); assertEquals(Result.STEP_OBSERVED, f.adapter.tick());
        var verified = f.store.journal; f.store.saveJournal(verified.check(), verified.retireStage(verified.check(), "e".repeat(64)));
        cells = new HashMap<>(f.port.frame.cells()); cells.put(CUT, new Cell(Blocks.DIRT.defaultBlockState(), 0));
        f.port.frame = new Frame(cells, f.port.frame.stock(), f.port.frame.drops(), Map.of()); f.port.time = 20; f.port.tick = 20;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(1, f.port.calls);
    }

    @Test void missingSuppliesOrUnacknowledgedFenceNeverInvokeNativeWork() {
        var existingXp = new Fixture(); existingXp.port.frame = new Frame(existingXp.port.frame.cells(), existingXp.port.frame.stock(), Map.of(), Map.of(UUID.randomUUID(), 1));
        assertEquals(Result.RECONCILE_REQUIRED, existingXp.adapter.tick()); assertEquals(0, existingXp.port.calls);
        var f = new Fixture(); f.port.supplies = false;
        assertEquals(Result.WAITING, f.adapter.tick()); assertEquals(0, f.port.calls);
        f.port.supplies = true; f.store.failFence = true;
        assertEquals(Result.RECONCILE_REQUIRED, f.adapter.tick()); assertEquals(0, f.port.calls);
        assertTrue(f.store.journal.receipts().isEmpty());
    }

    @Test void removalDependencyGateRefusesPlantsFallingAttachedFluidAndModdedLikeStates() {
        for (var block : List.of(Blocks.GRASS, Blocks.DANDELION, Blocks.TALL_GRASS, Blocks.SAND, Blocks.GRAVEL,
                Blocks.TORCH, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.WATER, Blocks.CHEST, Blocks.OAK_SLAB))
            assertFalse(WorkersEarthworksPort.stableCutNeighbor(block.defaultBlockState()), block.toString());
        assertTrue(WorkersEarthworksPort.stableCutNeighbor(Blocks.AIR.defaultBlockState()));
        assertTrue(WorkersEarthworksPort.stableCutNeighbor(Blocks.STONE.defaultBlockState()));
    }

    @Test void missingExactNeighborDependenciesRejectTheAdapterBeforeAnyStoreOrWorldAction() {
        var f = new Fixture(); var observations = new ArrayList<>(f.manifest.observations().values());
        observations.removeIf(cell -> cell.role() == Role.DEPENDENCY);
        var incomplete = new PerimeterEarthworksManifest(f.manifest.header(), observations, f.manifest.steps());
        assertThrows(IllegalArgumentException.class, () -> new NativeEarthworksAdapter(incomplete, f.store, f.port));
        assertEquals(0, f.port.calls);
    }

    private static final class Fixture {
        final PerimeterEarthworksManifest manifest = manifest(); final MemoryStore store = new MemoryStore(manifest);
        final FakePort port = new FakePort(manifest); final NativeEarthworksAdapter adapter = new NativeEarthworksAdapter(manifest, store, port);
    }
    private static final class MemoryStore implements Store {
        PerimeterEarthworksJournal journal; Ticket ticket; Frame before; Outcome outcome;
        boolean recovery = true, failFence, failOutcome, failFinish; long lastTime = Long.MIN_VALUE, sequence; Runnable afterFence = () -> {};
        MemoryStore(PerimeterEarthworksManifest manifest) {
            journal = PerimeterEarthworksJournal.begin(manifest, new PerimeterEarthworksJournal.Binding(UUID.randomUUID(), "a".repeat(64), "b".repeat(64)));
        }
        @Override public State read() { return new State(journal, ticket); }
        @Override public boolean recoveryAdmissionEstablished() { return recovery; }
        @Override public long lastDispatchGameTime() { return lastTime; }
        @Override public boolean saveJournal(PerimeterEarthworksJournal.Check expected, PerimeterEarthworksJournal next) {
            if (!journal.check().equals(expected)) return false; journal = next; return true;
        }
        @Override public Ticket fence(PerimeterEarthworksJournal.Check expected, String intent, Frame frame, long time) {
            if (failFence || ticket != null || !journal.check().equals(expected)) return null;
            before = frame; ticket = new Ticket(intent, ++sequence, NativeEarthworksAdapter.frameHash(frame)); lastTime = time;
            afterFence.run(); return ticket;
        }
        @Override public boolean recordOutcome(Ticket expected, Frame after) {
            if (failOutcome || !ObjectsEqual(ticket, expected)) return false; outcome = new Outcome(ticket, before, after); return true;
        }
        @Override public Outcome outcome(Ticket expected) { return ObjectsEqual(ticket, expected) ? outcome : null; }
        @Override public boolean finish(Ticket expected, PerimeterEarthworksJournal.Check check, PerimeterEarthworksJournal next) {
            if (failFinish || !ObjectsEqual(ticket, expected) || !journal.check().equals(check)) return false;
            journal = next; ticket = null; outcome = null; return true;
        }
    }
    private static boolean ObjectsEqual(Object a, Object b) { return java.util.Objects.equals(a, b); }
    private static final class FakePort implements Port {
        Frame frame; long time = 10; int tick = 10, calls, pulses, completeAt = 1; boolean eligible = true, supplies = true; String problem, mode = "";
        FakePort(PerimeterEarthworksManifest manifest) {
            Map<Long, Cell> cells = new HashMap<>(); manifest.observations().values().forEach(cell -> cells.put(cell.pos(), new Cell(cell.original(), 0)));
            frame = new Frame(cells, Map.of(SHOVEL, 1, DIRT, 1, WALL, 1), Map.of(), Map.of());
        }
        @Override public long gameTime() { return time; }
        @Override public int nativeTickCount() { return tick; }
        @Override public boolean nativeEligible() { return eligible; }
        @Override public String admissionProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal) { return problem; }
        @Override public Frame snapshot(PerimeterEarthworksManifest manifest, Step step) { return frame; }
        @Override public boolean suppliesReady(Step step) { return supplies; }
        @Override public void invokeExact(Step step) {
            calls++; if (step.kind() == Kind.CUT && ++pulses < completeAt) return;
            Map<Long, Cell> cells = new HashMap<>(frame.cells()); Map<Stock, Integer> stock = new HashMap<>(frame.stock());
            Map<UUID, Drop> drops = new HashMap<>(frame.drops()); Map<UUID, Integer> xp = new HashMap<>(frame.experience());
            if (!mode.equals("veto")) cells.put(step.pos(), new Cell(step.after(), 0));
            if (step.kind() == Kind.CUT) {
                stock.remove(SHOVEL); stock.put(new Stock(SHOVEL.item(), "", mode.equals("wear") ? 2 : 1), 1);
                if (!mode.equals("veto")) drops.put(UUID.randomUUID(), new Drop(DIRT, mode.equals("drops") ? 2 : 1));
            } else stock.remove(step.kind() == Kind.FILL ? DIRT : WALL);
            if (mode.equals("collateral")) cells.put(new BlockPos(0, 65, 0).asLong(), new Cell(Blocks.COBBLESTONE.defaultBlockState(), 0));
            if (mode.equals("experience")) xp.put(UUID.randomUUID(), 1);
            frame = new Frame(cells, stock, drops, xp);
        }
    }
    static PerimeterEarthworksManifest manifest() {
        var header = new Header(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", "test",
                "a".repeat(64), "b".repeat(64), "local-reviewed-earthworks-v1", 0, 64, -64, 320, 1, 64);
        Map<Long, Observation> observations = new HashMap<>();
        for (long packed : List.of(CUT, FILL)) {
            BlockPos pos = BlockPos.of(packed);
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) > 2) continue;
                BlockPos neighbor = pos.offset(x, y, z);
                observations.put(neighbor.asLong(), new Observation(neighbor.asLong(), neighbor.getY() < 64 ? Blocks.DIRT.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), Role.DEPENDENCY, 0));
            }
        }
        observations.put(CUT, new Observation(CUT, Blocks.DIRT.defaultBlockState(), Role.WORK, 0));
        observations.put(FILL, new Observation(FILL, Blocks.AIR.defaultBlockState(), Role.WORK, 0));
        var removal = new Removal(Origin.UNKNOWN, Family.SOIL, DIRT_ADAPTER, DIRT_VERSION, DIRT_SOURCE);
        return new PerimeterEarthworksManifest(header, new ArrayList<>(observations.values()), List.of(
                new Step(0, Kind.CUT, CUT, Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), removal),
                new Step(1, Kind.FILL, FILL, Blocks.AIR.defaultBlockState(), Blocks.DIRT.defaultBlockState(), null),
                new Step(2, Kind.BUILD, CUT, Blocks.AIR.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), null)));
    }
}
