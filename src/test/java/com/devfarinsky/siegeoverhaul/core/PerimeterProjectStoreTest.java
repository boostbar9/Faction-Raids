package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterProjectStoreTest extends MinecraftTestSupport {
    @Test void onePaymentAcrossConfirmationRetriesActivationStagesAndCancellation() {
        CompoundTag core = core(100); var project = PerimeterProjectTest.project(); AtomicInteger dirty = new AtomicInteger();
        PerimeterProjectStore.prepare(core, project, dirty::incrementAndGet);
        assertEquals(100, FactionBank.balance(core)); assertEquals(0, FactionBank.ledgerDeltas(core).length);
        var result = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, dirty::incrementAndGet);
        assertEquals(PerimeterProjectStore.PaymentStatus.PAID, result.status()); assertEquals(36, FactionBank.balance(core));
        assertArrayEquals(new int[]{-64}, FactionBank.ledgerDeltas(core)); assertEquals(2, dirty.get());
        var repeated = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, dirty::incrementAndGet);
        assertEquals(PerimeterProjectStore.PaymentStatus.ALREADY_PAID, repeated.status()); assertEquals(3, dirty.get());
        var current = repeated.project(); var next = current.activate(current.check());
        current = PerimeterProjectStore.replace(core, current.check(), next, dirty::incrementAndGet);
        next = current.verifyStage(current.check(), current.expectedStageReceipt());
        current = PerimeterProjectStore.replace(core, current.check(), next, dirty::incrementAndGet);
        next = current.retireVerifiedStage(current.check());
        current = PerimeterProjectStore.replace(core, current.check(), next, dirty::incrementAndGet);
        assertEquals(PerimeterProject.State.WAITING_FOR_NEXT_STAGE, current.state());
        next = current.cancel(current.check(), "Canceled the whole perimeter");
        current = PerimeterProjectStore.replace(core, current.check(), next, dirty::incrementAndGet);
        int changes = dirty.get();
        repeated = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, dirty::incrementAndGet);
        assertEquals(PerimeterProject.State.CANCELED, repeated.project().state()); assertEquals(changes + 1, dirty.get());
        assertEquals(36, FactionBank.balance(core)); assertArrayEquals(new int[]{-64}, FactionBank.ledgerDeltas(core));
        assertEquals(PerimeterProject.State.CANCELED, PerimeterProjectStore.prepare(core, project, dirty::incrementAndGet).state());
    }

    @Test void creativeHasExplicitZeroDebitWithTheSame64QuoteAndModeConflictsFailClosed() {
        CompoundTag core = core(0); var project = PerimeterProjectTest.project();
        PerimeterProjectStore.prepare(core, project, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, true, () -> {}).project();
        assertEquals(64, paid.payment().quotedPrice()); assertEquals(0, paid.payment().debited()); assertTrue(paid.payment().creative());
        assertEquals(0, FactionBank.balance(core)); assertArrayEquals(new int[0], FactionBank.ledgerDeltas(core));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {}));
    }

    @Test void noAcceptanceNoPaymentAndInsufficientFundsLeavesEveryAuthoritativeFieldUnchanged() {
        CompoundTag core = core(63); var project = PerimeterProjectTest.project();
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> fail("Dirty callback must not run")));
        PerimeterProjectStore.prepare(core, project, () -> {}); CompoundTag snapshot = core.copy();
        assertEquals(PerimeterProjectStore.PaymentStatus.INSUFFICIENT_FUNDS,
                PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> fail("No debit")).status());
        assertEquals(snapshot, core);
        var canceled = project.cancel(project.check(), "Native assignment rejected");
        PerimeterProjectStore.replace(core, project.check(), canceled, () -> {});
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, true, () -> {}));
        assertEquals(63, FactionBank.balance(core));
    }

    @Test void conflictingIdentityHashPriceReceiptAndLostStoreNeverDebitAgain() {
        CompoundTag core = core(100); var project = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, project, () -> {});
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, UUID.randomUUID(), project.manifestHash(), 64, false, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), "0".repeat(64), 64, false, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 900, false, () -> {}));
        assertEquals(100, FactionBank.balance(core));
        PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {});
        CompoundTag damaged = core.copy(); damaged.getCompound(PerimeterProjectStore.KEY).getList("Entries", Tag.TAG_COMPOUND).getCompound(0).remove("Payment");
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(damaged, project.header().projectId(), project.manifestHash(), 64, false, () -> {}));
        assertEquals(36, FactionBank.balance(damaged));
        core.remove(PerimeterProjectStore.KEY);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {}));
        assertEquals(36, FactionBank.balance(core));
    }

    @Test void savedBeforeAndAfterDebitHaveConsistentReceiptsAndAmbiguousDirtyFailureRetriesIdempotently() {
        CompoundTag core = core(100); var project = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, project, () -> {});
        CompoundTag before = core.copy();
        assertThrows(IllegalStateException.class, () -> PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false,
                () -> { throw new IllegalStateException("Caller persistence signal interrupted"); }));
        CompoundTag after = core.copy();
        var oldRetry = PerimeterProjectStore.consumeOnce(before, project.header().projectId(), project.manifestHash(), 64, false, () -> {});
        var newRetry = PerimeterProjectStore.consumeOnce(after, project.header().projectId(), project.manifestHash(), 64, false, () -> {});
        assertEquals(PerimeterProjectStore.PaymentStatus.PAID, oldRetry.status());
        assertEquals(PerimeterProjectStore.PaymentStatus.ALREADY_PAID, newRetry.status());
        assertEquals(36, FactionBank.balance(before)); assertEquals(36, FactionBank.balance(after));
        assertArrayEquals(new int[]{-64}, FactionBank.ledgerDeltas(after));
        assertEquals(PerimeterProject.State.PREPARED_PAID, newRetry.project().state());
    }

    @Test void staleTransitionsCannotSkipStagesRewriteReceiptOrResurrectCanceledJob() {
        CompoundTag core = core(100); var project = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, project, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {}).project();
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, project.check(), project.paid(true), () -> {}));
        var running = paid.activate(paid.check()); var verified = running.verifyStage(running.check(), running.expectedStageReceipt());
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, paid.check(), verified, () -> {}));
        PerimeterProjectStore.replace(core, paid.check(), running, () -> {});
        var canceled = running.cancel(running.check(), "Owner canceled"); PerimeterProjectStore.replace(core, running.check(), canceled, () -> {});
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, running.check(), verified, () -> {}));
        assertEquals(PerimeterProject.State.CANCELED, PerimeterProjectStore.get(core, project.header().projectId()).state());
    }

    @Test void cacheUsesIdentityAndInvalidatesOnReplacementReloadOrRelocation() {
        CompoundTag core = core(100); var project = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, project, () -> {});
        var snapshot = PerimeterProjectStore.snapshot(core); assertTrue(snapshot.matches(core));
        assertFalse(snapshot.matches(core.copy())); assertEquals(project.manifestHash(), snapshot.get(project.header().projectId()).manifestHash());
        PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {});
        assertFalse(snapshot.matches(core)); var paid = PerimeterProjectStore.snapshot(core); assertTrue(paid.matches(core));
        core.put(PerimeterProjectStore.KEY, core.getCompound(PerimeterProjectStore.KEY).copy()); assertFalse(paid.matches(core));
    }

    @Test void boundedStoreRejectsDuplicatesUnknownVersionsAndCapacityWithoutSkippingAnything() {
        CompoundTag core = core(100); var project = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, project, () -> {});
        CompoundTag duplicate = core.copy(); ListTag entries = duplicate.getCompound(PerimeterProjectStore.KEY).getList("Entries", Tag.TAG_COMPOUND);
        entries.add(entries.get(0).copy()); assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.all(duplicate));
        CompoundTag unknown = core.copy(); unknown.getCompound(PerimeterProjectStore.KEY).putInt("Version", 999);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.all(unknown));
        CompoundTag oversized = core.copy(); ListTag many = new ListTag();
        for (int i = 0; i <= PerimeterProjectStore.MAX_PROJECTS; i++) many.add(new CompoundTag());
        oversized.getCompound(PerimeterProjectStore.KEY).put("Entries", many);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.all(oversized)); assertEquals(100, FactionBank.balance(core));
    }
    @Test void currentCheckCannotResurrectTerminalStateFromAnOldRunningBranch() {
        CompoundTag core = core(100); var fresh = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, fresh, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {}).project();
        var running = paid.activate(paid.check()); PerimeterProjectStore.replace(core, paid.check(), running, () -> {});
        var canceled = running.cancel(running.check(), "Canceled"); PerimeterProjectStore.replace(core, running.check(), canceled, () -> {});
        var branch = running.waitFor(running.check(), "Old branch one"); branch = branch.waitFor(branch.check(), "Old branch two");
        assertEquals(canceled.revision() + 1, branch.revision()); var forged = branch; CompoundTag exactBefore = core.copy();
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, canceled.check(), forged, () -> fail("No write")));
        assertEquals(exactBefore, core);
    }

    @Test void currentCheckCannotRewindReceiptsOrStageIndexUsingAValidOlderBranch() {
        CompoundTag core = core(100); var fresh = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, fresh, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {}).project();
        var running = paid.activate(paid.check()); PerimeterProjectStore.replace(core, paid.check(), running, () -> {});
        var verified = running.verifyStage(running.check(), running.expectedStageReceipt()); PerimeterProjectStore.replace(core, running.check(), verified, () -> {});
        var rewindVerified = running.waitFor(running.check(), "Earlier snapshot"); rewindVerified = rewindVerified.waitFor(rewindVerified.check(), "Rewind verification");
        var unverified = rewindVerified;
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, verified.check(), unverified, () -> fail("No write")));
        var waiting = verified.retireVerifiedStage(verified.check()); PerimeterProjectStore.replace(core, verified.check(), waiting, () -> {});
        var oldRunning = rewindVerified.waitFor(rewindVerified.check(), "Rewind the next stage");
        assertEquals(waiting.revision() + 1, oldRunning.revision());
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, waiting.check(), oldRunning, () -> fail("No write")));
        var wrongCancellation = rewindVerified.cancel(rewindVerified.check(), "Cancel using obsolete progress");
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, waiting.check(), wrongCancellation, () -> fail("No write")));
        assertEquals(waiting.save(), PerimeterProjectStore.get(core, fresh.header().projectId()).save());
    }

    @Test void currentCheckCannotSkipTheRecordedRecoveryState() {
        CompoundTag core = core(100); var fresh = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, fresh, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {}).project();
        var running = paid.activate(paid.check()); PerimeterProjectStore.replace(core, paid.check(), running, () -> {});
        var blocked = running.blockRecovery(running.check(), "Ledger identity uncertain"); PerimeterProjectStore.replace(core, running.check(), blocked, () -> {});
        var forged = running.verifyStage(running.check(), running.expectedStageReceipt()); forged = forged.waitFor(forged.check(), "Bypass reconciliation");
        var skip = forged;
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, blocked.check(), skip, () -> fail("No write")));
        var resumed = blocked.reconciled(blocked.check(), PerimeterProject.State.RUNNING);
        assertEquals(PerimeterProject.State.RUNNING, PerimeterProjectStore.replace(core, blocked.check(), resumed, () -> {}).state());
    }

    @Test void completedProjectCannotBeReopenedWithTheCurrentCheck() {
        CompoundTag core = core(100); var fresh = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, fresh, () -> {});
        var current = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {}).project();
        while (current.activeStage() < current.stages().size()) {
            var running = current.activate(current.check()); PerimeterProjectStore.replace(core, current.check(), running, () -> {});
            var verified = running.verifyStage(running.check(), running.expectedStageReceipt()); PerimeterProjectStore.replace(core, running.check(), verified, () -> {});
            current = verified.retireVerifiedStage(verified.check()); PerimeterProjectStore.replace(core, verified.check(), current, () -> {});
        }
        var verifying = current; var complete = verifying.complete(verifying.check(), verifying.manifestHash());
        PerimeterProjectStore.replace(core, verifying.check(), complete, () -> {});
        var branch = verifying.waitFor(verifying.check(), "Earlier final verification"); branch = branch.waitFor(branch.check(), "Reopen final verification");
        var reopen = branch;
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, complete.check(), reopen, () -> fail("No write")));
        assertEquals(PerimeterProject.State.COMPLETE, PerimeterProjectStore.get(core, fresh.header().projectId()).state());
    }

    @Test void everyAlreadyWrittenRetryRepairsAnInterruptedDirtySignalWithoutAnotherDebit() {
        CompoundTag core = core(100); var fresh = PerimeterProjectTest.project(); AtomicInteger dirty = new AtomicInteger();
        Runnable interrupted = () -> { throw new IllegalStateException("Dirty signal interrupted"); };
        assertThrows(IllegalStateException.class, () -> PerimeterProjectStore.prepare(core, fresh, interrupted));
        var prepared = PerimeterProjectStore.prepare(core, fresh, dirty::incrementAndGet); assertEquals(1, dirty.get());
        assertThrows(IllegalStateException.class, () -> PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, interrupted));
        var paid = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, dirty::incrementAndGet).project();
        assertEquals(2, dirty.get()); assertEquals(36, FactionBank.balance(core));
        var running = paid.activate(paid.check());
        assertThrows(IllegalStateException.class, () -> PerimeterProjectStore.replace(core, paid.check(), running, interrupted));
        var repaired = PerimeterProjectStore.replace(core, paid.check(), running, dirty::incrementAndGet);
        assertEquals(3, dirty.get()); assertEquals(running.check(), repaired.check());
        PerimeterProjectStore.replace(core, repaired.check(), repaired, dirty::incrementAndGet); assertEquals(4, dirty.get());
        assertArrayEquals(new int[]{-64}, FactionBank.ledgerDeltas(core)); assertEquals(36, FactionBank.balance(core));
    }

    private static CompoundTag core(long balance) { CompoundTag core = new CompoundTag(); core.putLong("BankEmeralds", balance); return core; }
}
