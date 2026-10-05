package com.devfarinsky.siegeoverhaul.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedAssembly.*;

/**
 * Pure journal-shape/state validation for one stepped parent. Supplied receipts record external
 * assertions; this model cannot establish their truth, perform a debit, or authorize world work.
 * There is deliberately no codec, controller, native child creation, or persistence acknowledgment.
 */
public final class PerimeterSteppedProject {
    public enum State { PREPARED_UNPAID, PREPARED_PAID, RUNNING, PHASE_VERIFIED,
        WAITING_FOR_NEXT_PHASE, VERIFYING_COMPLETE, COMPLETE, CANCELED, UNCERTAIN }
    public enum PaymentMode { TREASURY_DEBIT, CREATIVE_EXEMPTION }
    public record PaymentReceipt(Binding binding, UUID receiptId, int feeVersion, int quotedPrice,
                                 int debited, PaymentMode mode) {}
    public record ActivationReceipt(Binding binding, int phase, UUID areaId, String phaseDigest, UUID receiptId) {}
    public record PhaseReceipt(ActivationReceipt activation, UUID receiptId, int verifiedTargets) {}
    public record RetirementReceipt(PhaseReceipt verified, UUID receiptId) {}
    public record CompletionReceipt(Binding binding, UUID receiptId, List<UUID> retirementReceiptIds,
                                    String finalObservationDigest) {
        public CompletionReceipt {
            require(retirementReceiptIds != null && retirementReceiptIds.size() <= 2, "Invalid completion receipt prefix");
            retirementReceiptIds = List.copyOf(retirementReceiptIds);
        }
    }
    public record Check(Binding binding, long revision, State state, int activePhase) {}
    /** Values for a future strict codec. A valid Snapshot is not proof that any storage operation happened. */
    public record Snapshot(String assemblyDigest, State state, State priorState, int activePhase, long revision,
                           PaymentReceipt payment, ActivationReceipt activeLease, List<PhaseReceipt> verified,
                           List<RetirementReceipt> retired, CompletionReceipt completion, String blocker) {
        public Snapshot {
            require(verified != null && verified.size() <= 2 && retired != null && retired.size() <= 2,
                    "Receipt prefix exceeds the exact two phases");
            verified = List.copyOf(verified); retired = List.copyOf(retired);
        }
    }

    private final PerimeterSteppedAssembly contract;
    private final Snapshot snapshot;

    private PerimeterSteppedProject(PerimeterSteppedAssembly contract, Snapshot snapshot) {
        this.contract = Objects.requireNonNull(contract); this.snapshot = Objects.requireNonNull(snapshot); validate();
    }
    public static PerimeterSteppedProject prepare(PerimeterSteppedAssembly contract) {
        return new PerimeterSteppedProject(contract, new Snapshot(contract.digest(), State.PREPARED_UNPAID, null,
                0, 0, null, null, List.of(), List.of(), null, ""));
    }
    /** Reject malformed receipt/state combinations; external provenance and durable reconciliation remain mandatory. */
    public static PerimeterSteppedProject restore(PerimeterSteppedAssembly contract, Snapshot snapshot) {
        return new PerimeterSteppedProject(contract, snapshot);
    }
    public PerimeterSteppedAssembly contract() { return contract; }
    public Snapshot snapshot() { return snapshot; }
    public State state() { return snapshot.state(); }
    public int activePhase() { return snapshot.activePhase(); }
    public long revision() { return snapshot.revision(); }
    public Check check() { return new Check(contract.binding(), revision(), state(), activePhase()); }
    public boolean executionSupported() { return false; }
    public void expect(Check check) { require(check().equals(check), "Stale or foreign stepped parent transition"); }

    /** Records an actual single parent payment/exemption assertion; never debits or synthesizes a receipt. */
    public PerimeterSteppedProject recordPayment(Check expected, PaymentReceipt receipt) {
        expect(expected); validatePayment(receipt);
        if (snapshot.payment() != null) { require(snapshot.payment().equals(receipt), "Conflicting parent payment receipt"); return this; }
        require(state() == State.PREPARED_UNPAID, "Parent is not awaiting payment");
        return changed(State.PREPARED_PAID, null, 0, receipt, null, snapshot.verified(), snapshot.retired(), null, "");
    }
    /** External controller must already have proved exact native activation and all safety/persistence gates. */
    public PerimeterSteppedProject recordActivation(Check expected, ActivationReceipt receipt) {
        expect(expected);
        if (snapshot.activeLease() != null && snapshot.activeLease().equals(receipt)) return this;
        require(state() == State.PREPARED_PAID || state() == State.WAITING_FOR_NEXT_PHASE,
                "Phase is not awaiting externally reconciled activation");
        validateActivation(receipt, activePhase());
        return changed(State.RUNNING, null, activePhase(), snapshot.payment(), receipt, snapshot.verified(), snapshot.retired(), null, "");
    }
    /** No expected/completed-receipt factory exists: the external verifier must supply the actual receipt. */
    public PerimeterSteppedProject recordVerification(Check expected, PhaseReceipt receipt) {
        expect(expected); require(receipt != null && receipt.activation() != null, "Missing phase verification receipt");
        int phase = receipt.activation().phase(); validateVerification(receipt, phase);
        if (phase < snapshot.verified().size()) {
            require(snapshot.verified().get(phase).equals(receipt), "Conflicting phase verification receipt"); return this;
        }
        require(state() == State.RUNNING && phase == activePhase() && snapshot.activeLease().equals(receipt.activation()),
                "Verification does not name the active native receipt");
        List<PhaseReceipt> verified = new ArrayList<>(snapshot.verified()); verified.add(receipt);
        return changed(State.PHASE_VERIFIED, null, activePhase(), snapshot.payment(), snapshot.activeLease(), verified, snapshot.retired(), null, "");
    }
    /** Retirement assertion must include the exact verified receipt; verifying alone never advances a child. */
    public PerimeterSteppedProject recordRetirement(Check expected, RetirementReceipt receipt) {
        expect(expected); require(receipt != null && receipt.verified() != null && receipt.verified().activation() != null,
                "Missing native retirement receipt");
        int phase = receipt.verified().activation().phase(); validateVerification(receipt.verified(), phase); requireId(receipt.receiptId());
        if (phase < snapshot.retired().size()) {
            require(snapshot.retired().get(phase).equals(receipt), "Conflicting native retirement receipt"); return this;
        }
        require(state() == State.PHASE_VERIFIED && phase == activePhase() && snapshot.verified().get(phase).equals(receipt.verified()),
                "Unverified or foreign native child cannot retire");
        List<RetirementReceipt> retired = new ArrayList<>(snapshot.retired()); retired.add(receipt);
        int next = activePhase() + 1;
        return changed(next == 2 ? State.VERIFYING_COMPLETE : State.WAITING_FOR_NEXT_PHASE, null, next,
                snapshot.payment(), null, snapshot.verified(), retired, null, "");
    }
    public PerimeterSteppedProject recordCompletion(Check expected, CompletionReceipt receipt) {
        expect(expected); validateCompletion(receipt);
        if (state() == State.COMPLETE) { require(snapshot.completion().equals(receipt), "Conflicting whole-parent completion"); return this; }
        require(state() == State.VERIFYING_COMPLETE, "Both real phases must verify and retire before final observation");
        return changed(State.COMPLETE, null, 2, snapshot.payment(), null, snapshot.verified(), snapshot.retired(), receipt, "");
    }
    /** Retains all receipts and reservations. Cancellation is not native detachment or cleanup evidence. */
    public PerimeterSteppedProject cancel(Check expected, String reason) {
        expect(expected); require(text(reason, 256), "Cancellation needs a bounded reason");
        require(state() != State.COMPLETE, "Completed parent cannot be canceled");
        require(state() != State.UNCERTAIN, "Uncertain parent must retain its reconciliation fence and original reason");
        if (state() == State.CANCELED) { require(snapshot.blocker().equals(reason), "Conflicting cancellation"); return this; }
        return changed(State.CANCELED, state(), activePhase(), snapshot.payment(), snapshot.activeLease(),
                snapshot.verified(), snapshot.retired(), null, reason);
    }
    /** Sticky fail-closed state. Recovery requires a future explicitly reviewed external reconciliation path. */
    public PerimeterSteppedProject uncertain(Check expected, String reason) {
        expect(expected); require(text(reason, 256), "Uncertainty needs a bounded reason");
        require(state() != State.COMPLETE && state() != State.CANCELED, "Terminal parent cannot resume");
        if (state() == State.UNCERTAIN) { require(snapshot.blocker().equals(reason), "Conflicting uncertainty"); return this; }
        return changed(State.UNCERTAIN, state(), activePhase(), snapshot.payment(), snapshot.activeLease(),
                snapshot.verified(), snapshot.retired(), null, reason);
    }
    private PerimeterSteppedProject changed(State state, State prior, int phase, PaymentReceipt payment, ActivationReceipt lease,
                                           List<PhaseReceipt> verified, List<RetirementReceipt> retired, CompletionReceipt completion, String blocker) {
        require(revision() < Long.MAX_VALUE, "Parent revision exhausted");
        return new PerimeterSteppedProject(contract, new Snapshot(contract.digest(), state, prior, phase,
                revision() + 1, payment, lease, verified, retired, completion, blocker));
    }

    private void validate() {
        require(contract.digest().equals(snapshot.assemblyDigest()), "Changed assembly digest");
        require(snapshot.state() != null && snapshot.revision() >= 0 && activePhase() >= 0 && activePhase() <= 2,
                "Invalid parent state or revision");
        require(snapshot.blocker() != null && snapshot.blocker().length() <= 256, "Invalid bounded blocker");
        boolean suspended = state() == State.CANCELED || state() == State.UNCERTAIN;
        State effective = suspended ? snapshot.priorState() : state();
        require(suspended ? effective != null && effective != State.COMPLETE && effective != State.CANCELED && effective != State.UNCERTAIN
                && text(snapshot.blocker(), 256) : snapshot.priorState() == null && snapshot.blocker().isEmpty(), "Invalid retained prior state");
        require(snapshot.retired().size() <= snapshot.verified().size(), "Retirement has no actual verification prefix");
        Set<UUID> ids = new HashSet<>();
        if (snapshot.payment() != null) { validatePayment(snapshot.payment()); distinct(ids, snapshot.payment().receiptId()); }
        for (int i = 0; i < snapshot.verified().size(); i++) {
            PhaseReceipt receipt = snapshot.verified().get(i); validateVerification(receipt, i);
            distinct(ids, receipt.activation().receiptId()); distinct(ids, receipt.receiptId());
        }
        for (int i = 0; i < snapshot.retired().size(); i++) {
            RetirementReceipt receipt = snapshot.retired().get(i);
            require(receipt != null && receipt.verified() != null && receipt.verified().equals(snapshot.verified().get(i)), "Retirement prefix differs from actual verification prefix");
            distinct(ids, receipt.receiptId());
        }
        if (snapshot.activeLease() != null) {
            validateActivation(snapshot.activeLease(), activePhase());
            if (snapshot.verified().size() > activePhase()) require(snapshot.verified().get(activePhase()).activation().equals(snapshot.activeLease()), "Active lease differs from verified native lease");
            else distinct(ids, snapshot.activeLease().receiptId());
        }
        int verified, retired; boolean lease;
        switch (effective) {
            case PREPARED_UNPAID, PREPARED_PAID -> { require(activePhase() == 0, "Prepared parent phase changed"); verified = 0; retired = 0; lease = false; }
            case RUNNING -> { require(activePhase() < 2, "No active phase remains"); verified = activePhase(); retired = activePhase(); lease = true; }
            case PHASE_VERIFIED -> { require(activePhase() < 2, "No verified phase remains"); verified = activePhase() + 1; retired = activePhase(); lease = true; }
            case WAITING_FOR_NEXT_PHASE -> { require(activePhase() == 1, "Only structure can wait after retired fill"); verified = 1; retired = 1; lease = false; }
            case VERIFYING_COMPLETE, COMPLETE -> { require(activePhase() == 2, "Whole completion requires both phases"); verified = 2; retired = 2; lease = false; }
            default -> throw new IllegalArgumentException("Unsupported effective state");
        }
        require(snapshot.verified().size() == verified && snapshot.retired().size() == retired
                && (snapshot.activeLease() != null) == lease, "State differs from actual receipt prefix");
        require((snapshot.payment() == null) == (effective == State.PREPARED_UNPAID), "State has missing or unexpected parent payment");
        require((snapshot.completion() != null) == (effective == State.COMPLETE), "Unexpected or missing final observation");
        if (snapshot.completion() != null) { validateCompletion(snapshot.completion()); distinct(ids, snapshot.completion().receiptId()); }
        long minimumRevision = (snapshot.payment() == null ? 0 : 1) + 2L * verified + retired
                + (lease && verified == retired ? 1 : 0) + (snapshot.completion() == null ? 0 : 1) + (suspended ? 1 : 0);
        require(revision() >= minimumRevision, "Revision predates its actual receipt prefix");
        if (effective == State.PREPARED_UNPAID && !suspended) require(revision() == 0, "Fresh unpaid parent has a changed revision");
    }
    private void validatePayment(PaymentReceipt receipt) {
        require(receipt != null && contract.binding().equals(receipt.binding()) && receipt.feeVersion() == FEE_VERSION
                && receipt.quotedPrice() == PRICE && receipt.mode() != null, "Payment is not bound to the whole parent quote");
        requireId(receipt.receiptId());
        require(receipt.debited() == (receipt.mode() == PaymentMode.TREASURY_DEBIT ? PRICE : 0), "Payment amount differs from the single parent fee");
    }
    private void validateActivation(ActivationReceipt receipt, int phase) {
        require(phase >= 0 && phase < 2 && receipt != null && receipt.phase() == phase
                && contract.binding().equals(receipt.binding()), "Invalid native phase identity");
        Phase expected = contract.phases().get(phase);
        require(expected.areaId().equals(receipt.areaId()) && expected.digest().equals(receipt.phaseDigest()), "Changed native area or phase digest");
        requireId(receipt.receiptId());
    }
    private void validateVerification(PhaseReceipt receipt, int phase) {
        require(receipt != null, "Missing phase verification"); validateActivation(receipt.activation(), phase); requireId(receipt.receiptId());
        require(receipt.verifiedTargets() == contract.phases().get(phase).targets().size(), "Partial or excess target verification");
    }
    private void validateCompletion(CompletionReceipt receipt) {
        require(receipt != null && contract.binding().equals(receipt.binding()) && digest(receipt.finalObservationDigest()), "Invalid whole-parent final observation");
        requireId(receipt.receiptId());
        require(receipt.retirementReceiptIds().size() == 2 && receipt.retirementReceiptIds().equals(snapshot.retired().stream().map(RetirementReceipt::receiptId).toList()),
                "Final observation lacks the exact actual native retirement prefix");
    }
    private static void distinct(Set<UUID> ids, UUID id) { requireId(id); require(ids.add(id), "Receipt identity reused for another event"); }
}
