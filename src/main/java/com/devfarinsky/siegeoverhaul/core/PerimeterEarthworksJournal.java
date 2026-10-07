package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest.invalid;

/**
 * Unhooked immutable progress contract, not permission to mine, charge, refund or grant items.
 * The future server controller must authenticate admission/payment/native-accounting receipts and
 * reconcile world/ledger files before invoking transitions. Loading a pending intent never proves
 * that its native callback ran, and does not issue a new intent or repeat a callback automatically.
 */
public final class PerimeterEarthworksJournal {
    public static final int FORMAT_VERSION = 1;
    public enum State { READY, PENDING, STAGE_VERIFIED, VERIFYING, COMPLETE, CANCELED }
    public record Binding(UUID ledgerGeneration, String admissionReceipt, String paymentReceipt) {
        public Binding {
            if (ledgerGeneration == null || ledgerGeneration.equals(new UUID(0, 0))
                    || !digest(admissionReceipt) || !digest(paymentReceipt)) throw invalid("Incomplete earthworks journal binding");
        }
    }
    public record Check(String bindingHash, long revision, State state, int nextStep, int activeStage) {}
    public record Intent(int step, int stage, String hash) {}
    /** A native accounting digest references actual tool/hand/inventory/drop evidence, never predicted drops. */
    public record Evidence(BlockState before, BlockState after, long beforeEditRevision, long afterEditRevision,
                           int consumedMaterialItems, String nativeAccountingReceipt) {
        public Evidence {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (beforeEditRevision < 0 || afterEditRevision < 0 || consumedMaterialItems < 0 || consumedMaterialItems > 1
                    || !digest(nativeAccountingReceipt)) throw invalid("Invalid native earthworks evidence");
        }
    }
    public record Receipt(Intent intent, long editRevision, int consumedMaterialItems,
                          String nativeAccountingReceipt, String hash) {}
    public record Retirement(int stage, String nativeDetachReceipt, String hash) {}

    private final PerimeterEarthworksManifest manifest;
    private final Binding binding;
    private final String bindingHash;
    private final State state, canceledFrom;
    private final long revision;
    private final int nextStep, activeStage;
    private final Intent pending;
    private final Node<Receipt> receipts;
    private final Node<Retirement> retirements;
    private final String completionReceipt, cancelReason;
    private final int[] stageEnds;

    private PerimeterEarthworksJournal(PerimeterEarthworksManifest manifest, Binding binding, State state,
            State canceledFrom, long revision, int nextStep, int activeStage, Intent pending,
            Node<Receipt> receipts, Node<Retirement> retirements, String completionReceipt, String cancelReason, int[] stageEnds) {
        this.manifest = manifest; this.binding = binding;
        this.bindingHash = hash("earthworks-journal-binding-v1", manifest.hash(), binding.ledgerGeneration(),
                binding.admissionReceipt(), binding.paymentReceipt());
        this.state = state; this.canceledFrom = canceledFrom; this.revision = revision;
        this.nextStep = nextStep; this.activeStage = activeStage; this.pending = pending;
        this.receipts = receipts; this.retirements = retirements;
        this.completionReceipt = completionReceipt; this.cancelReason = cancelReason; this.stageEnds = stageEnds;
        validateProgress();
    }

    /** References an already authenticated whole-project payment. This method never performs payment. */
    public static PerimeterEarthworksJournal begin(PerimeterEarthworksManifest manifest, Binding binding) {
        Objects.requireNonNull(manifest); Objects.requireNonNull(binding);
        int[] ends = new int[manifest.stageDigests().size()];
        for (int i = 0; i < manifest.steps().size(); i++) ends[manifest.steps().get(i).stage()] = i + 1;
        return new PerimeterEarthworksJournal(manifest, binding, State.READY, null, 0, 0, 0,
                null, null, null, "", "", ends);
    }

    public Binding binding() { return binding; }
    public String manifestHash() { return manifest.hash(); }
    public State state() { return state; }
    public State canceledFrom() { return canceledFrom; }
    public long revision() { return revision; }
    public int nextStep() { return nextStep; }
    public int activeStage() { return activeStage; }
    public Intent pending() { return pending; }
    public List<Receipt> receipts() { return values(receipts); }
    public List<Retirement> retirements() { return values(retirements); }
    public String completionReceipt() { return completionReceipt; }
    public String cancelReason() { return cancelReason; }
    public Check check() { return new Check(bindingHash, revision, state, nextStep, activeStage); }

    /** Persist this pending identity before exposure to native work; this call itself grants no authority. */
    public PerimeterEarthworksJournal arm(Check expected) {
        expect(expected);
        if (state != State.READY) throw invalid("Earthworks is not ready for a new intent");
        return changed(State.PENDING, null, nextStep, activeStage, intent(nextStep, lastReceiptHash()),
                receipts, retirements, "", "");
    }

    /** Exact repeat acknowledgments are idempotent; stale tokens and conflicting evidence never advance. */
    public PerimeterEarthworksJournal observe(Check expected, Intent observedIntent, Evidence evidence) {
        expect(expected); Objects.requireNonNull(observedIntent); Objects.requireNonNull(evidence);
        if (state == State.CANCELED || state == State.COMPLETE) throw invalid("Terminal earthworks cannot record work");
        if (observedIntent.step() < nextStep && observedIntent.step() >= 0) {
            Receipt old = receiptAt(observedIntent.step());
            if (old.equals(receipt(observedIntent, evidence))) return this;
            throw invalid("Conflicting earthworks acknowledgment");
        }
        if (state != State.PENDING || !Objects.equals(pending, observedIntent)) throw invalid("Wrong pending earthworks intent");
        Receipt receipt = receipt(observedIntent, evidence);
        int next = nextStep + 1;
        return changed(next == stageEnds[activeStage] ? State.STAGE_VERIFIED : State.READY, null,
                next, activeStage, null, new Node<>(receipt, receipts), retirements, "", "");
    }

    /** External native queue completion and exact lease detachment must be proved before this transition. */
    public PerimeterEarthworksJournal retireStage(Check expected, String nativeDetachReceipt) {
        expect(expected);
        if (state != State.STAGE_VERIFIED || !digest(nativeDetachReceipt)) throw invalid("Unverified earthworks stage retirement");
        Retirement receipt = retirement(activeStage, nativeDetachReceipt, lastReceiptHash(), lastRetirementHash());
        int next = activeStage + 1;
        return changed(next == stageEnds.length ? State.VERIFYING : State.READY, null, nextStep, next, null,
                receipts, new Node<>(receipt, retirements), "", "");
    }

    /** The receipt references fresh whole-world AND accounting reconciliation, not only queue emptiness. */
    public PerimeterEarthworksJournal complete(Check expected, String verifiedWorldAccountingReceipt) {
        expect(expected);
        if (state != State.VERIFYING || !digest(verifiedWorldAccountingReceipt)) throw invalid("Whole earthworks reconciliation is missing");
        return changed(State.COMPLETE, null, nextStep, activeStage, null, receipts, retirements,
                verifiedWorldAccountingReceipt, "");
    }

    /** Cancellation preserves pending ambiguity and every receipt; it never returns material or restores blocks. */
    public PerimeterEarthworksJournal cancel(Check expected, String reason) {
        expect(expected);
        if (state == State.CANCELED) return this;
        if (state == State.COMPLETE || !PerimeterEarthworksManifest.bounded(reason, 256)) throw invalid("Invalid earthworks cancellation");
        return changed(State.CANCELED, state, nextStep, activeStage, pending, receipts, retirements, "", reason);
    }

    private PerimeterEarthworksJournal changed(State next, State canceled, int step, int stage, Intent intent,
            Node<Receipt> rows, Node<Retirement> retired, String completion, String reason) {
        if (revision == Long.MAX_VALUE) throw invalid("Earthworks journal revision exhausted");
        return new PerimeterEarthworksJournal(manifest, binding, next, canceled, revision + 1, step, stage,
                intent, rows, retired, completion, reason, stageEnds);
    }
    private void expect(Check expected) { if (!check().equals(expected)) throw invalid("Stale or foreign earthworks journal transition"); }
    private Intent intent(int index, String previousReceipt) {
        var step = manifest.steps().get(index);
        return new Intent(index, step.stage(), hash("earthworks-intent-v1", bindingHash, index, step.stage(),
                manifest.stageDigests().get(step.stage()), previousReceipt));
    }
    private Receipt receipt(Intent intent, Evidence evidence) {
        if (intent.step() < 0 || intent.step() >= manifest.steps().size()) throw invalid("Unknown earthworks step");
        var step = manifest.steps().get(intent.step()); var cell = manifest.observations().get(step.pos());
        int consumed = step.kind() == PerimeterEarthworksManifest.Kind.CUT ? 0 : 1;
        if (intent.stage() != step.stage() || !digest(intent.hash()) || !step.before().equals(evidence.before())
                || !step.after().equals(evidence.after()) || evidence.beforeEditRevision() != cell.editRevision()
                || evidence.afterEditRevision() != cell.editRevision() || evidence.consumedMaterialItems() != consumed)
            throw invalid("Native change, player edit or material consumption differs from the pending step");
        return new Receipt(intent, evidence.afterEditRevision(), consumed, evidence.nativeAccountingReceipt(),
                hash("earthworks-observed-v1", intent.hash(), PerimeterProject.stateKey(evidence.before()),
                        PerimeterProject.stateKey(evidence.after()), evidence.afterEditRevision(), consumed, evidence.nativeAccountingReceipt()));
    }
    private Retirement retirement(int stage, String detach, String lastStep, String previousRetirement) {
        return new Retirement(stage, detach, hash("earthworks-retired-v1", bindingHash, stage,
                manifest.stageDigests().get(stage), lastStep, detach, previousRetirement));
    }
    private Receipt receiptAt(int index) {
        for (Node<Receipt> node = receipts; node != null; node = node.previous)
            if (node.value.intent().step() == index) return node.value;
        throw invalid("Missing earthworks receipt prefix");
    }
    private String lastReceiptHash() { return receipts == null ? bindingHash : receipts.value.hash(); }
    private String lastRetirementHash() { return retirements == null ? bindingHash : retirements.value.hash(); }

    private void validateProgress() {
        if (revision < 0 || nextStep != size(receipts) || nextStep < 0 || nextStep > manifest.steps().size()
                || activeStage != size(retirements) || activeStage < 0 || activeStage > stageEnds.length)
            throw invalid("Invalid earthworks progress prefix");
        State effective = state == State.CANCELED ? canceledFrom : state;
        if (effective == null || effective == State.CANCELED
                || state != State.CANCELED && (canceledFrom != null || !cancelReason.isEmpty())
                || state == State.CANCELED && (!PerimeterEarthworksManifest.bounded(cancelReason, 256) || effective == State.COMPLETE)
                || state != State.COMPLETE && !completionReceipt.isEmpty()
                || state == State.COMPLETE && !digest(completionReceipt)) throw invalid("Invalid earthworks terminal evidence");
        if (effective == State.VERIFYING || effective == State.COMPLETE) {
            if (activeStage != stageEnds.length || nextStep != manifest.steps().size() || pending != null)
                throw invalid("Incomplete earthworks completion prefix");
        } else {
            if (activeStage >= stageEnds.length || nextStep < (activeStage == 0 ? 0 : stageEnds[activeStage - 1])
                    || nextStep > stageEnds[activeStage]) throw invalid("Earthworks stage moved without its exact receipts");
            if (effective == State.STAGE_VERIFIED) {
                if (nextStep != stageEnds[activeStage] || pending != null) throw invalid("Stage verification is incomplete");
            } else if (nextStep == stageEnds[activeStage]
                    || effective == State.READY && pending != null
                    || effective == State.PENDING && !Objects.equals(pending, intent(nextStep, lastReceiptHash())))
                throw invalid("Missing or conflicting pending earthworks intent");
        }
        long minimum = 2L * nextStep + activeStage + (pending == null ? 0 : 1)
                + (state == State.CANCELED || state == State.COMPLETE ? 1 : 0);
        if (revision != minimum) throw invalid("Earthworks revision does not match exact transition history");
    }

    /** Linked immutable prefixes make an ordinary acknowledgment O(1), without copying every earlier cell. */
    private static final class Node<T> {
        final T value; final Node<T> previous; final int size;
        Node(T value, Node<T> previous) { this.value = value; this.previous = previous; this.size = size(previous) + 1; }
    }
    private static int size(Node<?> node) { return node == null ? 0 : node.size; }
    private static <T> List<T> values(Node<T> head) {
        List<T> values = new ArrayList<>(size(head));
        for (Node<T> node = head; node != null; node = node.previous) values.add(node.value);
        Collections.reverse(values); return List.copyOf(values);
    }

    public CompoundTag save() {
        CompoundTag out = new CompoundTag(); out.putInt("EarthworksJournalVersion", FORMAT_VERSION);
        out.putString("Manifest", manifest.hash()); out.putUUID("LedgerGeneration", binding.ledgerGeneration());
        out.putString("AdmissionReceipt", binding.admissionReceipt()); out.putString("PaymentReceipt", binding.paymentReceipt());
        out.putString("State", state.name()); out.putLong("Revision", revision); out.putInt("Next", nextStep); out.putInt("Stage", activeStage);
        out.putString("Completion", completionReceipt); out.putString("CancelReason", cancelReason);
        if (canceledFrom != null) out.putString("CanceledFrom", canceledFrom.name());
        if (pending != null) out.put("Pending", saveIntent(pending));
        ListTag rows = new ListTag(), retired = new ListTag();
        for (Receipt receipt : receipts()) {
            CompoundTag row = saveIntent(receipt.intent()); row.putLong("EditRevision", receipt.editRevision());
            row.putInt("Consumed", receipt.consumedMaterialItems()); row.putString("NativeAccounting", receipt.nativeAccountingReceipt());
            row.putString("ReceiptHash", receipt.hash()); rows.add(row);
        }
        for (Retirement receipt : retirements()) {
            CompoundTag row = new CompoundTag(); row.putInt("Stage", receipt.stage());
            row.putString("NativeDetach", receipt.nativeDetachReceipt()); row.putString("ReceiptHash", receipt.hash()); retired.add(row);
        }
        out.put("Receipts", rows); out.put("Retirements", retired); out.putString("Hash", snapshotHash()); return out;
    }

    /** Decode against the exact separately saved manifest. No native callback, payment or item recovery occurs. */
    public static PerimeterEarthworksJournal load(PerimeterEarthworksManifest manifest, CompoundTag tag) {
        keys(tag, Set.of("EarthworksJournalVersion", "Manifest", "LedgerGeneration", "AdmissionReceipt", "PaymentReceipt",
                "State", "Revision", "Next", "Stage", "Completion", "CancelReason", "Receipts", "Retirements", "Hash"), Set.of("Pending", "CanceledFrom"));
        if (integer(tag, "EarthworksJournalVersion") != FORMAT_VERSION || !hash(tag, "Manifest").equals(manifest.hash()))
            throw invalid("Unknown journal format or changed earthworks manifest");
        require(tag, "LedgerGeneration", Tag.TAG_INT_ARRAY);
        if (!tag.hasUUID("LedgerGeneration")) throw invalid("Invalid journal ledger generation");
        var base = begin(manifest, new Binding(tag.getUUID("LedgerGeneration"), hash(tag, "AdmissionReceipt"), hash(tag, "PaymentReceipt")));
        Node<Receipt> receipts = null; List<Receipt> rows = new ArrayList<>(); String previous = base.bindingHash;
        for (Tag raw : list(tag, "Receipts", manifest.steps().size())) {
            CompoundTag row = (CompoundTag) raw;
            keys(row, Set.of("Step", "Stage", "IntentHash", "EditRevision", "Consumed", "NativeAccounting", "ReceiptHash"), Set.of());
            Intent intent = readIntent(row); int index = rows.size();
            if (!intent.equals(base.intent(index, previous))) throw invalid("Changed or non-prefix native receipt");
            var step = manifest.steps().get(index); long edit = number(row, "EditRevision");
            Receipt actual = base.receipt(intent, new Evidence(step.before(), step.after(), edit, edit,
                    integer(row, "Consumed"), hash(row, "NativeAccounting")));
            if (!actual.hash().equals(hash(row, "ReceiptHash"))) throw invalid("Changed native earthworks accounting receipt");
            receipts = new Node<>(actual, receipts); rows.add(actual); previous = actual.hash();
        }
        Node<Retirement> retired = null; previous = base.bindingHash;
        for (Tag raw : list(tag, "Retirements", manifest.stageDigests().size())) {
            CompoundTag row = (CompoundTag) raw; keys(row, Set.of("Stage", "NativeDetach", "ReceiptHash"), Set.of());
            int stage = size(retired), end = base.stageEnds[stage];
            if (integer(row, "Stage") != stage || rows.size() < end) throw invalid("Retired an incomplete earthworks stage");
            Retirement actual = base.retirement(stage, hash(row, "NativeDetach"), rows.get(end - 1).hash(), previous);
            if (!actual.hash().equals(hash(row, "ReceiptHash"))) throw invalid("Changed native stage detachment receipt");
            retired = new Node<>(actual, retired); previous = actual.hash();
        }
        Intent pending = null;
        if (tag.contains("Pending")) {
            require(tag, "Pending", Tag.TAG_COMPOUND); CompoundTag p = tag.getCompound("Pending");
            keys(p, Set.of("Step", "Stage", "IntentHash"), Set.of()); pending = readIntent(p);
        }
        var restored = new PerimeterEarthworksJournal(manifest, base.binding, state(tag, "State"),
                tag.contains("CanceledFrom") ? state(tag, "CanceledFrom") : null, number(tag, "Revision"), integer(tag, "Next"),
                integer(tag, "Stage"), pending, receipts, retired, string(tag, "Completion", 64), string(tag, "CancelReason", 256), base.stageEnds);
        if (!restored.snapshotHash().equals(hash(tag, "Hash"))) throw invalid("Changed earthworks journal snapshot");
        return restored;
    }
    private String snapshotHash() {
        return hash("earthworks-journal-snapshot-v1", bindingHash, state, canceledFrom == null ? "" : canceledFrom,
                revision, nextStep, activeStage, pending == null ? "" : pending.hash(), lastReceiptHash(), lastRetirementHash(),
                completionReceipt, cancelReason);
    }
    private static CompoundTag saveIntent(Intent intent) {
        CompoundTag out = new CompoundTag(); out.putInt("Step", intent.step()); out.putInt("Stage", intent.stage());
        out.putString("IntentHash", intent.hash()); return out;
    }
    private static Intent readIntent(CompoundTag tag) { return new Intent(integer(tag, "Step"), integer(tag, "Stage"), hash(tag, "IntentHash")); }
    private static State state(CompoundTag tag, String key) {
        try { return State.valueOf(string(tag, key, 32)); }
        catch (RuntimeException bad) { throw invalid("Unknown earthworks journal state"); }
    }
    private static boolean digest(String value) { return PerimeterEarthworksManifest.digest(value); }
    private static String hash(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object part : parts) {
                byte[] bytes = part.toString().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hash(CompoundTag tag, String key) {
        String value = string(tag, key, 64); if (!digest(value)) throw invalid("Invalid journal digest"); return value;
    }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String string(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_STRING); String value = tag.getString(key);
        if (value.length() > max) throw invalid("Oversize journal string"); return value;
    }
    private static ListTag list(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_LIST); ListTag list = (ListTag) tag.get(key);
        if (list.size() > max || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw invalid("Invalid journal list"); return list;
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw invalid("Missing or wrongly typed journal field: " + key);
    }
    private static void keys(CompoundTag tag, Set<String> required, Set<String> optional) {
        if (tag == null || !tag.getAllKeys().containsAll(required)) throw invalid("Incomplete journal record");
        for (String key : tag.getAllKeys()) if (!required.contains(key) && !optional.contains(key)) throw invalid("Unexpected journal field");
    }
}
