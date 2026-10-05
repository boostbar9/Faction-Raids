package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarthworksJobLedgerTest extends MinecraftTestSupport {
    @Test void unpaidPreparedJobsCannotArmCallbacksOrSupply() {
        var ledger = new EarthworksJobLedger(); var job = prepare(ledger);
        assertFalse(job.active()); assertFalse(job.recoveryAdmissionEstablished());
        assertFalse(job.compareSupplyDigest("", "a".repeat(64)));
        assertFalse(job.saveJournal(job.read().journal().check(), job.read().journal().arm(job.read().journal().check())));
        job.acknowledgeDebit(); assertTrue(job.active());
        assertThrows(IllegalStateException.class, job::acknowledgeDebit);
    }
    @Test void strictRoundtripRetainsAuthorityButDoesNotInventCrashAtomicRecovery() {
        var ledger = new EarthworksJobLedger(); var job = prepare(ledger); job.acknowledgeDebit();
        assertTrue(job.compareSupplyDigest("", "a".repeat(64)));
        var tag = ledger.save(new CompoundTag()); var loaded = EarthworksJobLedger.load(tag);
        assertFalse(loaded.uncertain()); assertEquals(tag, loaded.save(new CompoundTag()));
        var restored = loaded.job(job.manifest.header().project());
        assertTrue(restored.paid()); assertFalse(restored.recoveryAdmissionEstablished());
        assertFalse(restored.compareSupplyDigest("a".repeat(64), "b".repeat(64)));
        assertEquals("a".repeat(64), restored.supplyDigest());
    }
    @Test void callbackFenceAndObservedOutcomeRetainExactFramesAcrossSaveAndCancel() {
        var ledger = new EarthworksJobLedger(); var job = prepare(ledger); job.acknowledgeDebit();
        var journal = job.read().journal(); assertTrue(job.saveJournal(journal.check(), journal.arm(journal.check())));
        journal = job.read().journal(); var frame = frame(job.manifest);
        var ticket = job.fence(journal.check(), journal.pending().hash(), frame, 10);
        assertThrows(IllegalStateException.class, () -> job.fence(job.read().journal().check(), ticket.intentHash(), frame, 20));
        assertTrue(job.recordOutcome(ticket, frame)); assertEquals(frame, job.outcome(ticket).after());
        job.cancel();
        var restored = EarthworksJobLedger.load(ledger.save(new CompoundTag())).job(job.manifest.header().project());
        assertEquals(PerimeterEarthworksJournal.State.CANCELED, restored.read().journal().state());
        assertEquals(ticket, restored.read().inFlight()); assertEquals(frame, restored.outcome(ticket).before());
        assertFalse(restored.finish(ticket, restored.read().journal().check(), restored.read().journal()));
    }
    @Test void settledPartialPulseClearsOnlyItsFenceAndPreservesPendingIntent() {
        var ledger = new EarthworksJobLedger(); var job = prepare(ledger); job.acknowledgeDebit();
        var journal = job.read().journal(); job.saveJournal(journal.check(), journal.arm(journal.check())); journal = job.read().journal();
        var ticket = job.fence(journal.check(), journal.pending().hash(), frame(job.manifest), 10);
        job.recordOutcome(ticket, frame(job.manifest));
        assertTrue(job.finish(ticket, journal.check(), journal)); assertNull(job.read().inFlight());
        assertEquals(journal.pending(), job.read().journal().pending()); assertEquals(10, job.lastDispatchGameTime());
        assertFalse(job.finish(ticket, journal.check(), journal));
    }
    @Test void malformedStateAndUnknownFieldsNeverBecomeEmptyAuthority() {
        var ledger = new EarthworksJobLedger(); prepare(ledger);
        var tag = ledger.save(new CompoundTag()); tag.putString("Unknown", "value");
        var bad = EarthworksJobLedger.load(tag); assertTrue(bad.uncertain());
        assertThrows(IllegalStateException.class, () -> bad.save(new CompoundTag()));
    }
    @Test void supplyComparisonRejectsStaleHistoryAndRetainsTerminalDigest() {
        var ledger = new EarthworksJobLedger(); var job = prepare(ledger); job.acknowledgeDebit();
        assertTrue(job.compareSupplyDigest("", "a".repeat(64)));
        assertFalse(job.compareSupplyDigest("", "b".repeat(64)));
        assertFalse(job.compareSupplyDigest("a".repeat(64), "invalid"));
        job.cancel(); assertEquals("a".repeat(64), job.supplyDigest());
        assertFalse(job.compareSupplyDigest("a".repeat(64), "b".repeat(64)));
    }
    @Test void malformedJobAndOrphanSupplySelectorsRemainGuardedWithoutAValidLease() {
        var worker = mock(Mob.class); assertFalse(NativeEarthworksJobs.selected(worker));
        var data = new CompoundTag(); when(worker.getPersistentData()).thenReturn(data);
        assertFalse(NativeEarthworksJobs.selected(worker));
        data.putString("SiegeEarthworksSupplyV1", "malformed"); assertTrue(NativeEarthworksJobs.selected(worker));
        data.remove("SiegeEarthworksSupplyV1"); data.putString(NativeEarthworksJobs.KEY, "malformed");
        assertTrue(NativeEarthworksJobs.selected(worker));
    }
    @Test void missingSelectorWithUnreadableWorldLedgerNeverEnablesLegacyStorage() {
        var worker = mock(com.talhanation.workers.entities.BuilderEntity.class);
        var data = new CompoundTag(); when(worker.getPersistentData()).thenReturn(data);
        var level = mock(net.minecraft.server.level.ServerLevel.class); when(worker.level()).thenReturn(level);
        when(level.getDataStorage()).thenThrow(new IllegalStateException("unreadable ledger"));
        assertTrue(NativeEarthworksJobs.selected(worker)); assertTrue(EarthworksInventoryAccess.guarded(worker));
        var delegate = mock(net.minecraft.world.entity.ai.goal.Goal.class);
        when(delegate.getFlags()).thenReturn(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE));
        var goal = new ProtectedInventoryGoal(worker, delegate, new ProtectedInventoryGoal.Session(worker)) {
            @Override ProtectedStorageAccess.Kind kind() { return ProtectedStorageAccess.Kind.NEEDED; }
            @Override String beforeStart() { return null; }
            @Override String beforeTick() { return null; }
            @Override String cleanup() { return null; }
        };
        goal.start(); goal.tick(); verify(delegate, never()).start(); verify(delegate, never()).tick();
    }
    @Test void duplicateAreaIsRejectedBeforeChangingEitherAuthorityOrSave() {
        var ledger = new EarthworksJobLedger(); var first = prepare(ledger); var before = ledger.save(new CompoundTag());
        var second = NativeEarthworksAdapterTest.manifest();
        var next = new PerimeterEarthworksManifest(second.header(), new ArrayList<>(second.observations().values()), second.steps().subList(0,2));
        assertThrows(IllegalStateException.class, () -> ledger.prepare(next,
                new PerimeterEarthworksJournal.Binding(UUID.randomUUID(), "a".repeat(64), "b".repeat(64)), first.area, BlockPos.ZERO));
        assertEquals(before, ledger.save(new CompoundTag()));
        assertFalse(EarthworksJobLedger.load(before).uncertain());
        assertEquals(before, EarthworksJobLedger.load(before).save(new CompoundTag()));
    }
    @Test void invalidReadableWorldLedgerKeepsMissingSelectorGuarded() {
        var worker = mock(com.talhanation.workers.entities.BuilderEntity.class);
        when(worker.getPersistentData()).thenReturn(new CompoundTag());
        var level = mock(net.minecraft.server.level.ServerLevel.class); when(worker.level()).thenReturn(level);
        var invalid = EarthworksJobLedger.load(new CompoundTag()); assertTrue(invalid.uncertain());
        try (var authority = mockStatic(EarthworksJobLedger.class)) {
            authority.when(() -> EarthworksJobLedger.get(level)).thenReturn(invalid);
            assertTrue(NativeEarthworksJobs.selected(worker)); assertTrue(EarthworksInventoryAccess.guarded(worker));
            var delegate = mock(net.minecraft.world.entity.ai.goal.Goal.class);
            when(delegate.getFlags()).thenReturn(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE));
            var goal = new ProtectedInventoryGoal(worker, delegate, new ProtectedInventoryGoal.Session(worker)) {
                @Override ProtectedStorageAccess.Kind kind() { return ProtectedStorageAccess.Kind.NEEDED; }
                @Override String beforeStart() { return null; }
                @Override String beforeTick() { return null; }
                @Override String cleanup() { return null; }
            };
            goal.start(); goal.tick(); verify(delegate, never()).start(); verify(delegate, never()).tick();
        }
    }
    @Test void newSupplyRefusesUnsupportedOrUnavailableNativeRuntimeBeforeLeaseOrTransfer() {
        var worker = mock(com.talhanation.workers.entities.BuilderEntity.class);
        var data = new CompoundTag(); data.putString(NativeEarthworksJobs.KEY,"untrusted");
        when(worker.getPersistentData()).thenReturn(data);when(worker.level()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        try (var runtime=mockStatic(WorkersConstructionRuntime.class)) {
            runtime.when(WorkersConstructionRuntime::problem).thenReturn("Unsupported native companion version");
            assertEquals("Unsupported native companion version", NativeEarthworksJobs.inventoryProblem(worker,Set.of()));
            runtime.when(WorkersConstructionRuntime::problem).thenThrow(new LinkageError("runtime unavailable"));
            assertNotNull(NativeEarthworksJobs.inventoryProblem(worker,Set.of()));
        }
    }
    private static EarthworksJobLedger.Job prepare(EarthworksJobLedger ledger) {
        var source = NativeEarthworksAdapterTest.manifest();
        var manifest = new PerimeterEarthworksManifest(source.header(), new ArrayList<>(source.observations().values()), source.steps().subList(0, 2));
        return ledger.prepare(manifest, new PerimeterEarthworksJournal.Binding(UUID.randomUUID(), "a".repeat(64), "b".repeat(64)), UUID.randomUUID(), BlockPos.ZERO);
    }
    private static NativeEarthworksAdapter.Frame frame(PerimeterEarthworksManifest manifest) {
        Map<Long,NativeEarthworksAdapter.Cell> cells = new HashMap<>(); manifest.observations().forEach((p,c) -> cells.put(p,new NativeEarthworksAdapter.Cell(c.original(),c.editRevision())));
        return new NativeEarthworksAdapter.Frame(cells, Map.of(new NativeEarthworksAdapter.Stock("minecraft:iron_shovel", "unicode-\uD800", 0), 1), Map.of(), Map.of());
    }
}
