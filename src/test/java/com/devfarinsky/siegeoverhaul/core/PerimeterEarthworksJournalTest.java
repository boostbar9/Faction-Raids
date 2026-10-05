package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterEarthworksJournalTest extends MinecraftTestSupport {
    private static final long CUT_A = pos(0, 64, 0), CUT_B = pos(1, 64, 0), FILL = pos(2, 63, 0);
    private static final String AUDIT = "d".repeat(64), DETACH = "e".repeat(64), COMPLETE = "f".repeat(64);

    @Test void everyIntentObservationRetirementAndCompletionBoundaryRoundTripsExactly() {
        var manifest = manifest(); var journal = roundtrip(manifest, begin(manifest));
        while (journal.state() != PerimeterEarthworksJournal.State.VERIFYING) {
            journal = roundtrip(manifest, journal.arm(journal.check()));
            int index = journal.nextStep(); var prior = journal;
            journal = roundtrip(manifest, journal.observe(journal.check(), journal.pending(), evidence(manifest, index)));
            assertEquals(index, prior.nextStep()); assertNotNull(prior.pending());
            assertEquals(index + 1, journal.nextStep()); assertNull(journal.pending());
            if (journal.state() == PerimeterEarthworksJournal.State.STAGE_VERIFIED) {
                var verified = journal;
                assertThrows(IllegalArgumentException.class, () -> verified.arm(verified.check()));
                journal = roundtrip(manifest, journal.retireStage(journal.check(), DETACH));
            }
        }
        assertEquals(5, journal.receipts().size()); assertEquals(3, journal.retirements().size());
        assertEquals(3, journal.receipts().stream().mapToInt(PerimeterEarthworksJournal.Receipt::consumedMaterialItems).sum());
        journal = roundtrip(manifest, journal.complete(journal.check(), COMPLETE));
        assertEquals(PerimeterEarthworksJournal.State.COMPLETE, journal.state()); assertEquals(COMPLETE, journal.completionReceipt());
        assertEquals(begin(manifest).binding(), journal.binding());
        assertThrows(UnsupportedOperationException.class, () -> journalReceipts(manifest).clear());
    }

    @Test void pendingReloadRetainsAmbiguityAndNeverGeneratesASecondIntentOrInventsAReceipt() {
        var manifest = manifest(); var ready = begin(manifest); var pending = ready.arm(ready.check());
        var restored = roundtrip(manifest, pending);
        assertEquals(pending.pending(), restored.pending()); assertEquals(0, restored.nextStep()); assertTrue(restored.receipts().isEmpty());
        assertEquals(PerimeterEarthworksJournal.State.PENDING, restored.state());
        assertThrows(IllegalArgumentException.class, () -> restored.arm(restored.check()));
        assertThrows(IllegalArgumentException.class, () -> restored.retireStage(restored.check(), DETACH));
        assertThrows(IllegalArgumentException.class, () -> restored.complete(restored.check(), COMPLETE));
        var air = Blocks.AIR.defaultBlockState();
        assertThrows(IllegalArgumentException.class, () -> restored.observe(restored.check(), restored.pending(),
                new PerimeterEarthworksJournal.Evidence(air, air, 7, 7, 0, AUDIT)));
        assertTrue(restored.receipts().isEmpty());
    }

    @Test void exactAcknowledgmentRetryIsIdempotentButOldCheckAndConflictingAccountingAreRejected() {
        var manifest = manifest(); var journal = begin(manifest); var readyCheck = journal.check();
        var pending = journal.arm(journal.check()); var intent = pending.pending();
        assertThrows(IllegalArgumentException.class, () -> pending.observe(readyCheck, intent, evidence(manifest, 0)));
        var observed = pending.observe(pending.check(), intent, evidence(manifest, 0));
        assertSame(observed, observed.observe(observed.check(), intent, evidence(manifest, 0)));
        assertEquals(1, observed.receipts().size());
        assertThrows(IllegalArgumentException.class, () -> observed.observe(pending.check(), intent, evidence(manifest, 0)));
        var evidence = evidence(manifest, 0);
        assertThrows(IllegalArgumentException.class, () -> observed.observe(observed.check(), intent,
                new PerimeterEarthworksJournal.Evidence(evidence.before(), evidence.after(), 7, 7, 0, "a".repeat(64))));
    }

    @Test void anotherLedgerOrPaymentBindingCannotReplayAnIntentOrProgressToken() {
        var manifest = manifest(); var first = begin(manifest); first = first.arm(first.check());
        var second = PerimeterEarthworksJournal.begin(manifest, new PerimeterEarthworksJournal.Binding(
                UUID.fromString("99999999-9999-9999-9999-999999999999"), "b".repeat(64), "c".repeat(64)));
        var pendingSecond = second.arm(second.check()); var pendingFirst = first;
        assertNotEquals(first.pending(), pendingSecond.pending());
        assertThrows(IllegalArgumentException.class, () -> pendingSecond.observe(pendingSecond.check(), pendingFirst.pending(), evidence(manifest, 0)));
        assertThrows(IllegalArgumentException.class, () -> pendingSecond.observe(pendingFirst.check(), pendingSecond.pending(), evidence(manifest, 0)));
        var otherPayment = PerimeterEarthworksJournal.begin(manifest,
                new PerimeterEarthworksJournal.Binding(second.binding().ledgerGeneration(), "b".repeat(64), "f".repeat(64)));
        assertNotEquals(second.check(), otherPayment.check());
    }

    @Test void sameStatePlayerEditsAndWrongBeforeAfterStatesCannotBeAcknowledged() {
        var manifest = manifest(); var journal = begin(manifest); var pending = journal.arm(journal.check());
        var expected = evidence(manifest, 0);
        for (long[] edits : List.of(new long[]{8, 7}, new long[]{7, 8}, new long[]{8, 8}))
            assertThrows(IllegalArgumentException.class, () -> pending.observe(pending.check(), pending.pending(),
                    new PerimeterEarthworksJournal.Evidence(expected.before(), expected.after(), edits[0], edits[1], 0, AUDIT)));
        assertThrows(IllegalArgumentException.class, () -> pending.observe(pending.check(), pending.pending(),
                new PerimeterEarthworksJournal.Evidence(Blocks.DIRT.defaultBlockState(), expected.after(), 7, 7, 0, AUDIT)));
        assertThrows(IllegalArgumentException.class, () -> pending.observe(pending.check(), pending.pending(),
                new PerimeterEarthworksJournal.Evidence(expected.before(), Blocks.DIRT.defaultBlockState(), 7, 7, 0, AUDIT)));
    }

    @Test void cutsConsumeNoConstructionStockAndEveryFillOrBuildConsumesExactlyOneItem() {
        var manifest = manifest(); var current = begin(manifest);
        while (current.state() != PerimeterEarthworksJournal.State.VERIFYING) {
            var pending = current.arm(current.check()); var evidence = evidence(manifest, pending.nextStep());
            int wrong = evidence.consumedMaterialItems() == 0 ? 1 : 0;
            assertThrows(IllegalArgumentException.class, () -> pending.observe(pending.check(), pending.pending(),
                    new PerimeterEarthworksJournal.Evidence(evidence.before(), evidence.after(), 7, 7, wrong, AUDIT)));
            current = pending.observe(pending.check(), pending.pending(), evidence);
            if (current.state() == PerimeterEarthworksJournal.State.STAGE_VERIFIED) current = current.retireStage(current.check(), DETACH);
        }
        assertEquals(3, current.receipts().stream().mapToInt(PerimeterEarthworksJournal.Receipt::consumedMaterialItems).sum());
        assertEquals(2, current.receipts().stream().filter(receipt -> receipt.consumedMaterialItems() == 0).count());
    }

    @Test void cancellationPreservesEveryReceiptAndPendingIntentWithoutRefundOrRevival() {
        var manifest = manifest(); var current = begin(manifest);
        List<PerimeterEarthworksJournal> boundaries = new ArrayList<>(); boundaries.add(current);
        current = current.arm(current.check()); boundaries.add(current);
        current = current.observe(current.check(), current.pending(), evidence(manifest, 0)); boundaries.add(current);
        current = current.arm(current.check()); boundaries.add(current);
        current = current.observe(current.check(), current.pending(), evidence(manifest, 1)); boundaries.add(current);
        current = current.retireStage(current.check(), DETACH); boundaries.add(current);
        for (var prior : boundaries) {
            var canceled = roundtrip(manifest, prior.cancel(prior.check(), "Owner canceled"));
            assertEquals(prior.receipts(), canceled.receipts()); assertEquals(prior.retirements(), canceled.retirements());
            assertEquals(prior.pending(), canceled.pending()); assertEquals(prior.binding(), canceled.binding());
            assertEquals(prior.state(), canceled.canceledFrom()); assertEquals(prior.nextStep(), canceled.nextStep());
            assertSame(canceled, canceled.cancel(canceled.check(), "Repeat"));
            assertThrows(IllegalArgumentException.class, () -> canceled.arm(canceled.check()));
            assertThrows(IllegalArgumentException.class, () -> canceled.retireStage(canceled.check(), DETACH));
            assertThrows(IllegalArgumentException.class, () -> canceled.complete(canceled.check(), COMPLETE));
            if (prior.pending() != null) assertThrows(IllegalArgumentException.class,
                    () -> canceled.observe(canceled.check(), prior.pending(), evidence(manifest, prior.nextStep())));
        }
    }

    @Test void wholeVerificationAndCancellationAfterAllWorkDoNotAlterPriorAccounting() {
        var manifest = manifest(); var verifying = finishStages(manifest);
        var canceled = roundtrip(manifest, verifying.cancel(verifying.check(), "Canceled during final verification"));
        assertEquals(verifying.receipts(), canceled.receipts()); assertEquals(verifying.retirements(), canceled.retirements());
        var complete = roundtrip(manifest, verifying.complete(verifying.check(), COMPLETE));
        assertThrows(IllegalArgumentException.class, () -> complete.cancel(complete.check(), "Too late"));
        assertThrows(IllegalArgumentException.class, () -> complete.complete(complete.check(), COMPLETE));
        assertThrows(IllegalArgumentException.class, () -> verifying.complete(verifying.check(), ""));
    }

    @Test void malformedTypesUnknownFieldsVersionsAndInventedProgressAreRejected() {
        var manifest = manifest(); var ready = begin(manifest);
        corrupt(manifest, ready, tag -> tag.putInt("EarthworksJournalVersion", 2));
        corrupt(manifest, ready, tag -> tag.putString("Revision", "0"));
        corrupt(manifest, ready, tag -> tag.putLong("Revision", 1));
        corrupt(manifest, ready, tag -> tag.putInt("Next", 1));
        corrupt(manifest, ready, tag -> tag.putString("State", "COMPLETE"));
        corrupt(manifest, ready, tag -> tag.putString("Refund", "64"));
        corrupt(manifest, ready, tag -> tag.putString("PaymentReceipt", "a".repeat(64)));
        corrupt(manifest, ready, tag -> tag.putString("Hash", "0".repeat(64)));
        corrupt(manifest, ready, tag -> { ListTag rows = new ListTag(); rows.add(StringTag.valueOf("fake")); tag.put("Receipts", rows); });
        var pending = ready.arm(ready.check());
        corrupt(manifest, pending, tag -> tag.remove("Pending"));
        corrupt(manifest, pending, tag -> tag.getCompound("Pending").putInt("Step", 1));
        corrupt(manifest, pending, tag -> tag.getCompound("Pending").putString("IntentHash", "0".repeat(64)));
    }

    @Test void truncatedDuplicateChangedNativeAndNonPrefixReceiptsFailClosed() {
        var manifest = manifest(); var verified = finishStages(manifest);
        corrupt(manifest, verified, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).remove(0));
        corrupt(manifest, verified, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putInt("Step", 1));
        corrupt(manifest, verified, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putLong("EditRevision", 8));
        corrupt(manifest, verified, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putInt("Consumed", 1));
        corrupt(manifest, verified, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putString("NativeAccounting", "0".repeat(64)));
        corrupt(manifest, verified, tag -> { var rows = tag.getList("Receipts", Tag.TAG_COMPOUND); rows.add(rows.get(0).copy()); });
        corrupt(manifest, verified, tag -> tag.getList("Retirements", Tag.TAG_COMPOUND).remove(0));
        corrupt(manifest, verified, tag -> tag.getList("Retirements", Tag.TAG_COMPOUND).getCompound(0).putString("NativeDetach", "0".repeat(64)));
        corrupt(manifest, verified, tag -> tag.getList("Retirements", Tag.TAG_COMPOUND).getCompound(0).putInt("Stage", 1));
    }

    @Test void changedManifestAndLegacyProjectNbtNeverLoadAsThisJournal() {
        var manifest = manifest(); var journal = begin(manifest); var observations = new ArrayList<>(manifest.observations().values());
        observations.replaceAll(cell -> new Observation(cell.pos(), cell.original(), cell.role(), 8));
        var changed = new PerimeterEarthworksManifest(manifest.header(), observations, manifest.steps());
        assertThrows(IllegalArgumentException.class, () -> PerimeterEarthworksJournal.load(changed, journal.save()));
        assertThrows(IllegalArgumentException.class, () -> PerimeterEarthworksJournal.load(manifest, PerimeterProjectTest.project().save()));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.load(journal.save()));
    }

    @Test void cancellationMetadataAndPendingWorkCannotBeDroppedOrRewrittenOnReload() {
        var manifest = manifest(); var ready = begin(manifest); var pending = ready.arm(ready.check());
        var canceled = pending.cancel(pending.check(), "Owner canceled");
        corrupt(manifest, canceled, tag -> tag.remove("Pending"));
        corrupt(manifest, canceled, tag -> tag.remove("CanceledFrom"));
        corrupt(manifest, canceled, tag -> tag.putString("CanceledFrom", "READY"));
        corrupt(manifest, canceled, tag -> tag.putString("State", "PENDING"));
        corrupt(manifest, canceled, tag -> tag.putString("CancelReason", ""));
        assertEquals(1, canceled.revision() - pending.revision());
    }

    private static List<PerimeterEarthworksJournal.Receipt> journalReceipts(PerimeterEarthworksManifest manifest) { return finishStages(manifest).receipts(); }
    private static PerimeterEarthworksJournal finishStages(PerimeterEarthworksManifest manifest) {
        var journal = begin(manifest);
        while (journal.state() != PerimeterEarthworksJournal.State.VERIFYING) {
            journal = journal.arm(journal.check()); journal = journal.observe(journal.check(), journal.pending(), evidence(manifest, journal.nextStep()));
            if (journal.state() == PerimeterEarthworksJournal.State.STAGE_VERIFIED) journal = journal.retireStage(journal.check(), DETACH);
        }
        return journal;
    }
    private static PerimeterEarthworksJournal.Evidence evidence(PerimeterEarthworksManifest manifest, int index) {
        var step = manifest.steps().get(index);
        return new PerimeterEarthworksJournal.Evidence(step.before(), step.after(), 7, 7, step.kind() == Kind.CUT ? 0 : 1, AUDIT);
    }
    private static PerimeterEarthworksJournal begin(PerimeterEarthworksManifest manifest) {
        return PerimeterEarthworksJournal.begin(manifest, new PerimeterEarthworksJournal.Binding(
                UUID.fromString("44444444-4444-4444-4444-444444444444"), "b".repeat(64), "c".repeat(64)));
    }
    private static PerimeterEarthworksJournal roundtrip(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal) {
        var restored = PerimeterEarthworksJournal.load(manifest, journal.save());
        assertEquals(journal.save(), restored.save()); assertEquals(journal.check(), restored.check()); return restored;
    }
    private static void corrupt(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal, Consumer<CompoundTag> edit) {
        CompoundTag tag = journal.save(); edit.accept(tag);
        assertThrows(IllegalArgumentException.class, () -> PerimeterEarthworksJournal.load(manifest, tag));
    }
    private static PerimeterEarthworksManifest manifest() {
        var header = new Header(UUID.fromString("11111111-1111-1111-1111-111111111111"), 1,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "minecraft:overworld", "test", "a".repeat(64), "b".repeat(64), "local-pad-cut-fill-v1", 0, 64, -64, 320, 1, 64);
        BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        var removal = new Removal(Origin.UNKNOWN, Family.STONE, "fixture:single_cell", "1", "a".repeat(64));
        return new PerimeterEarthworksManifest(header, List.of(new Observation(CUT_A, stone, Role.WORK, 7),
                new Observation(CUT_B, stone, Role.WORK, 7), new Observation(FILL, air, Role.WORK, 7)), List.of(
                new Step(0, Kind.CUT, CUT_A, stone, air, removal), new Step(0, Kind.CUT, CUT_B, stone, air, removal),
                new Step(1, Kind.FILL, FILL, air, Blocks.DIRT.defaultBlockState(), null),
                new Step(2, Kind.BUILD, CUT_A, air, Blocks.COBBLESTONE.defaultBlockState(), null),
                new Step(2, Kind.BUILD, CUT_B, air, Blocks.COBBLESTONE.defaultBlockState(), null)));
    }
    private static long pos(int x, int y, int z) { return new BlockPos(x, y, z).asLong(); }
}
