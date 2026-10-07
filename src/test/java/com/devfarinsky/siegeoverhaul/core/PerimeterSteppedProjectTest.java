package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedAssembly.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedModelFixtures.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProject.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterSteppedProjectTest {
    @Test void actualReceiptChainHasOnePaymentAndTwoSeparateRetirements() {
        var p = prepare(assembly()); assertEquals(State.PREPARED_UNPAID, p.state()); assertEquals(0, p.revision());
        var payment = payment(p); p = p.recordPayment(p.check(), payment); assertEquals(State.PREPARED_PAID, p.state());
        for (int phase = 0; phase < 2; phase++) {
            var activation = activation(p, phase); p = p.recordActivation(p.check(), activation); assertEquals(State.RUNNING, p.state());
            var verified = verification(p); p = p.recordVerification(p.check(), verified); assertEquals(State.PHASE_VERIFIED, p.state()); assertEquals(phase, p.activePhase());
            var retired = retirement(p); p = p.recordRetirement(p.check(), retired);
            assertEquals(phase == 0 ? State.WAITING_FOR_NEXT_PHASE : State.VERIFYING_COMPLETE, p.state());
            assertEquals(phase + 1, p.activePhase()); assertEquals(payment, p.snapshot().payment());
        }
        assertNotEquals(State.COMPLETE, p.state()); p = p.recordCompletion(p.check(), completion(p));
        assertEquals(State.COMPLETE, p.state()); assertEquals(8, p.revision()); assertFalse(p.executionSupported());
        assertEquals(2, p.snapshot().verified().size()); assertEquals(2, p.snapshot().retired().size());
    }
    @Test void noFillContractRunsAsOneStructurePhaseAfterTheSameSinglePayment() {
        var p = prepare(structureOnlyAssembly()); assertEquals(1, p.contract().phases().size());
        var payment = payment(p); p = p.recordPayment(p.check(), payment);
        p = p.recordActivation(p.check(), activation(p, 0));
        p = p.recordVerification(p.check(), verification(p));
        p = p.recordRetirement(p.check(), retirement(p));
        assertEquals(State.VERIFYING_COMPLETE, p.state());
        assertEquals(1, p.activePhase());
        assertEquals(payment, p.snapshot().payment());
        p = p.recordCompletion(p.check(), completion(p));
        assertEquals(State.COMPLETE, p.state());
        assertEquals(1, p.snapshot().verified().size());
        assertEquals(1, p.snapshot().retired().size());
    }
    @Test void receiptsAreNeverInventedByPrepareOrRestore() {
        var p = prepare(assembly()); assertNull(p.snapshot().payment()); assertNull(p.snapshot().activeLease());
        assertTrue(p.snapshot().verified().isEmpty()); assertTrue(p.snapshot().retired().isEmpty()); assertNull(p.snapshot().completion());
        var restored = restore(p.contract(), p.snapshot()); assertEquals(p.snapshot(), restored.snapshot());
    }
    @Test void unpaidOrWrongPhaseActivationAndPrematureCompletionFail() {
        var p = prepare(assembly()); assertThrows(IllegalArgumentException.class, () -> p.recordActivation(p.check(), activation(p, 0)));
        var paid = paid(); assertThrows(IllegalArgumentException.class, () -> paid.recordActivation(paid.check(), activation(paid, 1)));
        assertThrows(IllegalArgumentException.class, () -> paid.recordCompletion(paid.check(), new CompletionReceipt(paid.contract().binding(), id(140), List.of(id(130), id(131)), "c".repeat(64))));
    }
    @Test void paymentNeedsFullQuoteIdentityAndRecordedAmount() {
        var p = prepare(assembly()); var good = payment(p);
        assertThrows(IllegalArgumentException.class, () -> p.recordPayment(p.check(), new PaymentReceipt(good.binding(), good.receiptId(), 1, 64, 0, PaymentMode.TREASURY_DEBIT)));
        assertThrows(IllegalArgumentException.class, () -> p.recordPayment(p.check(), new PaymentReceipt(good.binding(), good.receiptId(), 1, 32, 32, PaymentMode.TREASURY_DEBIT)));
        assertThrows(IllegalArgumentException.class, () -> p.recordPayment(p.check(), new PaymentReceipt(new Binding(id(9), 1, id(4), 1, p.contract().digest()), good.receiptId(), 1, 64, 64, PaymentMode.TREASURY_DEBIT)));
        assertThrows(IllegalArgumentException.class, () -> p.recordPayment(p.check(), new PaymentReceipt(new Binding(id(1), 1, id(4), 2, p.contract().digest()), good.receiptId(), 1, 64, 64, PaymentMode.TREASURY_DEBIT)));
        var creative = new PaymentReceipt(good.binding(), good.receiptId(), 1, 64, 0, PaymentMode.CREATIVE_EXEMPTION);
        assertEquals(creative, p.recordPayment(p.check(), creative).snapshot().payment());
    }
    @Test void staleChecksAlwaysFailEvenForIdenticalPaymentRetry() {
        var unpaid = prepare(assembly()); var paid = unpaid.recordPayment(unpaid.check(), payment(unpaid));
        assertThrows(IllegalArgumentException.class, () -> paid.recordPayment(unpaid.check(), paid.snapshot().payment()));
        assertSame(paid, paid.recordPayment(paid.check(), paid.snapshot().payment()));
    }
    @Test void conflictingPaymentsNeverChargePerPhase() {
        var p = waiting(); var first = p.snapshot().payment();
        assertSame(p, p.recordPayment(p.check(), first));
        assertThrows(IllegalArgumentException.class, () -> p.recordPayment(p.check(), new PaymentReceipt(first.binding(), id(999), 1, 64, 64, PaymentMode.TREASURY_DEBIT)));
    }
    @Test void phaseReceiptRequiresActualExactActivationAndCompleteTargetCount() {
        var p = running(); var exact = verification(p);
        assertThrows(IllegalArgumentException.class, () -> p.recordVerification(p.check(), new PhaseReceipt(exact.activation(), exact.receiptId(), 0)));
        assertThrows(IllegalArgumentException.class, () -> p.recordVerification(p.check(), new PhaseReceipt(exact.activation(), exact.receiptId(), 2)));
        var a = exact.activation(); var other = new ActivationReceipt(a.binding(), a.phase(), a.areaId(), a.phaseDigest(), id(999));
        assertThrows(IllegalArgumentException.class, () -> p.recordVerification(p.check(), new PhaseReceipt(other, exact.receiptId(), 1)));
        assertThrows(IllegalArgumentException.class, () -> p.recordVerification(p.check(), new PhaseReceipt(activation(p, 1), id(121), 1)));
    }
    @Test void alteredAreaDigestAndReusedReceiptIdsFail() {
        var p = paid(); var a = activation(p, 0);
        assertThrows(IllegalArgumentException.class, () -> p.recordActivation(p.check(), new ActivationReceipt(a.binding(), 0, id(999), a.phaseDigest(), a.receiptId())));
        assertThrows(IllegalArgumentException.class, () -> p.recordActivation(p.check(), new ActivationReceipt(a.binding(), 0, a.areaId(), "d".repeat(64), a.receiptId())));
        assertThrows(IllegalArgumentException.class, () -> p.recordActivation(p.check(), new ActivationReceipt(a.binding(), 0, a.areaId(), a.phaseDigest(), p.snapshot().payment().receiptId())));
        var running = running(); assertThrows(IllegalArgumentException.class, () -> running.recordVerification(running.check(), new PhaseReceipt(running.snapshot().activeLease(), running.snapshot().activeLease().receiptId(), 1)));
    }
    @Test void verificationNeverAdvancesBeforeActualRetirement() {
        var p = verified(); assertEquals(0, p.activePhase()); assertTrue(p.snapshot().retired().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> p.recordActivation(p.check(), activation(p, 1)));
        var actual = retirement(p); var alternate = new PhaseReceipt(actual.verified().activation(), id(999), 1);
        assertThrows(IllegalArgumentException.class, () -> p.recordRetirement(p.check(), new RetirementReceipt(alternate, actual.receiptId())));
        var r = running(); assertThrows(IllegalArgumentException.class, () -> r.recordRetirement(r.check(), new RetirementReceipt(verification(r), id(130))));
    }
    @Test void currentIdenticalRetriesAreIdempotentButConflictingReceiptsFail() {
        var p = verified(); var v = p.snapshot().verified().get(0);
        assertSame(p, p.recordVerification(p.check(), v));
        assertThrows(IllegalArgumentException.class, () -> p.recordVerification(p.check(), new PhaseReceipt(v.activation(), id(999), v.verifiedTargets())));
        var w = waiting(); var r = w.snapshot().retired().get(0); assertSame(w, w.recordRetirement(w.check(), r));
        assertThrows(IllegalArgumentException.class, () -> w.recordRetirement(w.check(), new RetirementReceipt(r.verified(), id(999))));
        var done = complete(); assertSame(done, done.recordCompletion(done.check(), done.snapshot().completion()));
        assertSame(done, done.recordVerification(done.check(), done.snapshot().verified().get(0)));
    }
    @Test void completionRequiresExactOrderedRetirementPrefixAndExternalWorldObservationDigest() {
        var p = verifyingComplete(); var ids = p.snapshot().retired().stream().map(RetirementReceipt::receiptId).toList();
        assertThrows(IllegalArgumentException.class, () -> p.recordCompletion(p.check(), new CompletionReceipt(p.contract().binding(), id(140), List.of(ids.get(1), ids.get(0)), "c".repeat(64))));
        assertThrows(IllegalArgumentException.class, () -> p.recordCompletion(p.check(), new CompletionReceipt(p.contract().binding(), id(140), ids, "")));
        assertThrows(IllegalArgumentException.class, () -> p.recordCompletion(p.check(), new CompletionReceipt(p.contract().binding(), id(130), ids, "c".repeat(64))));
    }
    @Test void uncertaintyIsStickyAndRetainsRealReceiptsAndReservation() {
        var running = running(); var p = running.uncertain(running.check(), "Native receipt durability is unknown");
        assertEquals(State.UNCERTAIN, p.state()); assertEquals(State.RUNNING, p.snapshot().priorState());
        assertEquals(running.snapshot().activeLease(), p.snapshot().activeLease()); assertEquals(running.contract().reservation(), p.contract().reservation());
        assertThrows(IllegalArgumentException.class, () -> p.recordVerification(p.check(), new PhaseReceipt(p.snapshot().activeLease(), id(120), 1)));
        assertSame(p, p.uncertain(p.check(), p.snapshot().blocker()));
    }
    @Test void cancellationCannotEraseAnUncertainRunningParentsFenceOrReason() {
        var running = running(); var p = running.uncertain(running.check(), "Native retirement outcome is unknown; original evidence must be retained");
        var original = p.snapshot();
        var refusal = assertThrows(IllegalArgumentException.class, () -> p.cancel(p.check(), "User canceled"));
        assertTrue(refusal.getMessage().contains("reconciliation fence"));
        assertEquals(State.UNCERTAIN, p.state()); assertEquals(State.RUNNING, p.snapshot().priorState());
        assertEquals(original, p.snapshot()); assertEquals(original.blocker(), p.snapshot().blocker());
        assertEquals(running.snapshot().activeLease(), p.snapshot().activeLease());
        assertTrue(p.snapshot().retired().isEmpty());
        assertEquals(original, restore(p.contract(), p.snapshot()).snapshot());
    }
    @Test void cancelDoesNotEraseOutstandingChildOrPretendCleanup() {
        var running = running(); var p = running.cancel(running.check(), "User canceled");
        assertEquals(State.CANCELED, p.state()); assertNotNull(p.snapshot().activeLease()); assertTrue(p.snapshot().retired().isEmpty());
        assertEquals(running.snapshot().payment(), p.snapshot().payment()); assertSame(p, p.cancel(p.check(), "User canceled"));
        assertThrows(IllegalArgumentException.class, () -> p.uncertain(p.check(), "Retry"));
        var done = complete(); assertThrows(IllegalArgumentException.class, () -> done.cancel(done.check(), "Too late"));
    }
    @ParameterizedTest @EnumSource(State.class)
    void everyValidStateRoundTripsThroughValidatedSnapshot(State state) {
        var p = switch (state) {
            case PREPARED_UNPAID -> prepare(assembly()); case PREPARED_PAID -> paid(); case RUNNING -> running();
            case PHASE_VERIFIED -> verified(); case WAITING_FOR_NEXT_PHASE -> waiting(); case VERIFYING_COMPLETE -> verifyingComplete();
            case COMPLETE -> complete(); case CANCELED -> { var r = running(); yield r.cancel(r.check(), "cancel"); }
            case UNCERTAIN -> { var r = running(); yield r.uncertain(r.check(), "unknown"); }
        };
        assertEquals(p.snapshot(), restore(p.contract(), p.snapshot()).snapshot());
    }
    @Test void forgedBooleanLikePaidStateAndSkippedPrefixesCannotRestore() {
        var p = prepare(assembly());
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), snapshot(p, State.PREPARED_PAID, null, 0, 1, null, null, List.of(), List.of(), null, "")));
        var w = waiting();
        assertThrows(IllegalArgumentException.class, () -> restore(w.contract(), snapshot(w, State.WAITING_FOR_NEXT_PHASE, null, 1, 4, w.snapshot().payment(), null, List.of(), List.of(), null, "")));
        var done = complete(); var s = done.snapshot();
        assertThrows(IllegalArgumentException.class, () -> restore(done.contract(), snapshot(done, State.COMPLETE, null, 2, 8, s.payment(), null, s.verified(), List.of(), s.completion(), "")));
    }
    @Test void reorderedConflictingAndFutureReceiptsCannotRestore() {
        var p = verifyingComplete(); var s = p.snapshot();
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), snapshot(p, s.state(), null, 2, 7, s.payment(), null, List.of(s.verified().get(1), s.verified().get(0)), s.retired(), null, "")));
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), snapshot(p, s.state(), null, 2, 7, s.payment(), null, s.verified(), List.of(s.retired().get(1), s.retired().get(0)), null, "")));
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), snapshot(p, State.RUNNING, null, 0, 7, s.payment(), s.verified().get(0).activation(), s.verified(), s.retired(), null, "")));
    }
    @Test void changedDigestMissingPriorStatePrematureRevisionAndExcessListsFail() {
        var p = running(); var s = p.snapshot();
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), new Snapshot("d".repeat(64), s.state(), null, 0, 2, s.payment(), s.activeLease(), List.of(), List.of(), null, "")));
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), snapshot(p, State.UNCERTAIN, null, 0, 3, s.payment(), s.activeLease(), List.of(), List.of(), null, "unknown")));
        assertThrows(IllegalArgumentException.class, () -> restore(p.contract(), snapshot(p, State.RUNNING, null, 0, 0, s.payment(), s.activeLease(), List.of(), List.of(), null, "")));
        var v = verification(p); assertThrows(IllegalArgumentException.class, () -> new Snapshot(p.contract().digest(), State.RUNNING, null, 0, 2, s.payment(), s.activeLease(), List.of(v, v, v), List.of(), null, ""));
    }
    @Test void snapshotListsAreDefensiveAndRevisionOverflowFailsClosed() {
        var p = verified(); var s = p.snapshot(); var values = new ArrayList<>(s.verified());
        var copy = new Snapshot(s.assemblyDigest(), s.state(), s.priorState(), s.activePhase(), s.revision(), s.payment(), s.activeLease(), values, s.retired(), s.completion(), s.blocker());
        values.clear(); assertEquals(1, copy.verified().size()); assertThrows(UnsupportedOperationException.class, () -> copy.verified().clear());
        var exhausted = restore(p.contract(), snapshot(p, s.state(), null, 0, Long.MAX_VALUE, s.payment(), s.activeLease(), s.verified(), s.retired(), null, ""));
        assertThrows(IllegalArgumentException.class, () -> exhausted.uncertain(exhausted.check(), "Cannot advance revision"));
    }
}
