package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Model-boundary checks only. Synthetic hash strings here are not evidence of real world/entity cleanup. */
class PerimeterTerminalReceiptTest extends MinecraftTestSupport {
    @Test void compactPaidCancellationDropsGeometryAndRetainsTruthfulHistoryAndNoRechargeEvidence() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); var canceled = canceled(core, fresh, true);
        CompoundTag full = canceled.save(); var proof = proof(canceled);
        var terminal = PerimeterProjectStore.compact(core, canceled.check(), proof, () -> {});
        assertNull(PerimeterProjectStore.get(core, fresh.header().projectId()));
        assertEquals(canceled.manifestHash(), terminal.manifestHash()); assertEquals(canceled.header().owner(), terminal.owner());
        assertEquals(canceled.header().builder(), terminal.builder()); assertEquals(64, terminal.payment().debited());
        assertEquals(canceled.targets().size(), terminal.totalTargetCount()); assertEquals(canceled.stages().size(), terminal.totalStageCount());
        assertEquals(canceled.header().territory().size(), terminal.claimChunkCount()); assertEquals(0, terminal.completedTargetCount());
        assertEquals(canceled.stages().stream().map(PerimeterProject.Stage::areaId).toList(), terminal.stages().stream().map(PerimeterTerminalReceipt.Stage::areaId).toList());
        assertFalse(terminal.save().contains("Targets")); assertFalse(terminal.save().contains("Palette")); assertFalse(terminal.save().contains("Clearance"));
        assertTrue(terminal.save().toString().length() < full.toString().length() / 4);
        assertEquals(terminal.save(), PerimeterTerminalReceipt.load(terminal.save()).save());
        var retry = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {});
        assertEquals(PerimeterProjectStore.PaymentStatus.ALREADY_PAID, retry.status()); assertNull(retry.project()); assertNotNull(retry.terminal());
        assertEquals(936, FactionBank.balance(core)); assertArrayEquals(new int[]{-64}, FactionBank.ledgerDeltas(core));
    }

    @Test void compactCompleteRetainsExactTotalAndVerifiedStageCounts() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, fresh, () -> {});
        var current = PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {}).project();
        while (current.activeStage() < current.stages().size()) {
            var running = current.activate(current.check()); PerimeterProjectStore.replace(core, current.check(), running, () -> {});
            var verified = running.verifyStage(running.check(), running.expectedStageReceipt()); PerimeterProjectStore.replace(core, running.check(), verified, () -> {});
            current = verified.retireVerifiedStage(verified.check()); PerimeterProjectStore.replace(core, verified.check(), current, () -> {});
        }
        var complete = current.complete(current.check(), current.manifestHash()); PerimeterProjectStore.replace(core, current.check(), complete, () -> {});
        var terminal = PerimeterProjectStore.compact(core, complete.check(), proof(complete), () -> {});
        assertEquals(PerimeterProject.State.COMPLETE, terminal.state()); assertEquals(terminal.totalStageCount(), terminal.verifiedStages());
        assertEquals(terminal.totalTargetCount(), terminal.completedTargetCount()); assertEquals(complete.save().getString("Hash"), terminal.manifestHash());
    }

    @Test void unknownCleanupRetainsFullRecoveryGeometryAndWrongOrStaleTuplesAreRejected() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); var canceled = canceled(core, fresh, true);
        var exact = core.copy(); var proof = proof(canceled);
        assertThrows(NullPointerException.class, () -> PerimeterProjectStore.compact(core, canceled.check(), null, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.compact(core, fresh.check(), proof, () -> {}));
        var wrongOwner = new PerimeterTerminalReceipt.CleanupProof(proof.projectId(), proof.generation(), proof.manifestHash(), proof.terminalRevision(),
                proof.terminalState(), UUID.randomUUID(), proof.builder(), proof.ledgerGeneration(), proof.cleanedStageIds(),
                proof.reservationRetirementReceipt(), proof.builderDetachmentReceipt(), proof.nativeStageCleanupReceipt());
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.compact(core, canceled.check(), wrongOwner, () -> {}));
        var reversed = new ArrayList<>(proof.cleanedStageIds()); Collections.reverse(reversed);
        var wrongOrder = new PerimeterTerminalReceipt.CleanupProof(proof.projectId(), proof.generation(), proof.manifestHash(), proof.terminalRevision(),
                proof.terminalState(), proof.owner(), proof.builder(), proof.ledgerGeneration(), reversed,
                proof.reservationRetirementReceipt(), proof.builderDetachmentReceipt(), proof.nativeStageCleanupReceipt());
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.compact(core, canceled.check(), wrongOrder, () -> {}));
        assertEquals(exact, core); assertEquals(canceled.targets(), PerimeterProjectStore.get(core, canceled.header().projectId()).targets());
    }

    @Test void compactionBeforeAndAfterCrashBoundaryAndRepeatedRequestsAreIdempotentAndRepairDirty() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); var canceled = canceled(core, fresh, false);
        var proof = proof(canceled); CompoundTag before = core.copy(); AtomicInteger dirty = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> PerimeterProjectStore.compact(core, canceled.check(), proof,
                () -> { throw new IllegalStateException("Dirty signal interrupted"); }));
        CompoundTag after = core.copy(); var first = PerimeterProjectStore.compact(before, canceled.check(), proof, dirty::incrementAndGet);
        var repeated = PerimeterProjectStore.compact(after, canceled.check(), proof, dirty::incrementAndGet);
        assertEquals(2, dirty.get()); assertEquals(first.save(), repeated.save()); assertEquals(1000, FactionBank.balance(after));
        assertEquals(1, PerimeterProjectStore.terminals(after).size()); assertTrue(PerimeterProjectStore.all(after).isEmpty());
        var wrongLedger = new PerimeterTerminalReceipt.CleanupProof(proof.projectId(), proof.generation(), proof.manifestHash(), proof.terminalRevision(),
                proof.terminalState(), proof.owner(), proof.builder(), UUID.randomUUID(), proof.cleanedStageIds(),
                proof.reservationRetirementReceipt(), proof.builderDetachmentReceipt(), proof.nativeStageCleanupReceipt());
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.compact(after, canceled.check(), wrongLedger, () -> {}));
    }

    @Test void reorderedProjectRequestsDoNotChangeReceiptsOrPermitOldFullRecordResurrection() {
        CompoundTag core = core(); var first = PerimeterProjectTest.project(); var second = PerimeterProjectTest.project();
        var firstCanceled = canceled(core, first, false); var secondCanceled = canceled(core, second, false);
        var a = proof(firstCanceled); var b = proof(secondCanceled);
        var secondReceipt = PerimeterProjectStore.compact(core, secondCanceled.check(), b, () -> {});
        PerimeterProjectStore.compact(core, firstCanceled.check(), a, () -> {});
        assertEquals(secondReceipt.save(), PerimeterProjectStore.compact(core, secondCanceled.check(), b, () -> {}).save());
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.prepare(core, first, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, firstCanceled.check(), firstCanceled, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.replace(core, first.check(), first.waitFor(first.check(), "Old callback"), () -> {}));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core, first.header().projectId(), first.manifestHash(), 64, false, () -> {}));
        var snapshot = PerimeterProjectStore.snapshot(core); assertTrue(snapshot.known(first.header().projectId()));
        assertNotNull(snapshot.terminal(first.header().projectId())); assertNull(snapshot.get(first.header().projectId()));
        assertEquals(1000, FactionBank.balance(core));
    }

    @Test void aRetiredAreaIdCannotBeReusedAsANewProjectIdentity() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); var canceled = canceled(core, fresh, false);
        var retired = PerimeterProjectStore.compact(core, canceled.check(), proof(canceled), () -> {});
        UUID oldArea = retired.stages().get(0).areaId(); var h = fresh.header();
        var forgedHeader = PerimeterProject.Header.newCommission(oldArea, h.generation(), h.owner(), h.builder(), h.coreKey(),
                h.originalCore(), h.faction(), h.material(), h.reviewedFingerprint(), h.territory());
        var forged = PerimeterProject.prepare(forgedHeader, fresh.plan(), fresh.layout(), fresh.before(), fresh.clearanceBefore());
        CompoundTag previous = core.copy();
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.prepare(core, forged, () -> fail("No write")));
        assertEquals(previous, core);
        assertEquals(retired.receiptHash(), PerimeterProjectStore.snapshot(core).terminalForStage(oldArea).receiptHash());
    }

    @Test void strictTerminalCodecRejectsMissingEvidenceReusedIdsWrongPaymentAndContradictoryCounts() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); var canceled = canceled(core, fresh, true);
        var receipt = PerimeterProjectStore.compact(core, canceled.check(), proof(canceled), () -> {});
        corrupt(receipt, tag -> tag.remove("BuilderDetached"));
        corrupt(receipt, tag -> tag.putUUID("LedgerGeneration", new UUID(0, 0)));
        corrupt(receipt, tag -> tag.putInt("Price", 900));
        corrupt(receipt, tag -> tag.getCompound("Payment").putInt("Debited", 0));
        corrupt(receipt, tag -> tag.putInt("ClaimChunks", 0));
        corrupt(receipt, tag -> tag.putInt("Verified", receipt.stages().size() + 1));
        corrupt(receipt, tag -> tag.putString("State", "RUNNING"));
        corrupt(receipt, tag -> tag.putString("State", "COMPLETE"));
        corrupt(receipt, tag -> tag.getList("Stages", Tag.TAG_COMPOUND).getCompound(0).putUUID("Area", receipt.projectId()));
        corrupt(receipt, tag -> tag.getList("Stages", Tag.TAG_COMPOUND).getCompound(1).putUUID("Area", receipt.stages().get(0).areaId()));
        corrupt(receipt, tag -> tag.putString("ReceiptHash", "0".repeat(64)));
    }

    @Test void legacyVersionOneStoresReloadAndMigrateWithoutDroppingEvidence() {
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); PerimeterProjectStore.prepare(core, fresh, () -> {});
        core.getCompound(PerimeterProjectStore.KEY).putInt("Version", 1); core.getCompound(PerimeterProjectStore.KEY).remove("Terminals");
        assertEquals(fresh.manifestHash(), PerimeterProjectStore.get(core, fresh.header().projectId()).manifestHash());
        var canceled = fresh.cancel(fresh.check(), "Canceled before assignment"); PerimeterProjectStore.replace(core, fresh.check(), canceled, () -> {});
        assertEquals(2, core.getCompound(PerimeterProjectStore.KEY).getInt("Version"));
        assertEquals(canceled.save(), PerimeterProjectStore.get(core, fresh.header().projectId()).save());
    }

    @Test void boundedHistoryRejectsOversizeCountsBeforeReconstructingMalformedEntriesAndNeverEvicts() {
        assertEquals(PerimeterProjectStore.MAX_TOTAL_RESERVED / 4, PerimeterProjectStore.MAX_HISTORY_STAGE_IDS);
        assertEquals(16 * PerimeterStageLayout.MAX_STAGES, PerimeterProjectStore.MAX_HISTORY_PROJECTS);
        CompoundTag core = core(); var fresh = PerimeterProjectTest.project(); var canceled = canceled(core, fresh, false);
        var receipt = PerimeterProjectStore.compact(core, canceled.check(), proof(canceled), () -> {});
        CompoundTag tooManyProjects = core.copy(); ListTag many = new ListTag();
        for (int i = 0; i <= PerimeterProjectStore.MAX_HISTORY_PROJECTS; i++) many.add(new CompoundTag());
        tooManyProjects.getCompound(PerimeterProjectStore.KEY).put("Terminals", many);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.terminals(tooManyProjects));
        CompoundTag tooManyStages = core.copy(); ListTag entries = new ListTag();
        for (int i = 0; i <= PerimeterProjectStore.MAX_HISTORY_STAGE_IDS / PerimeterStageLayout.MAX_STAGES; i++) {
            CompoundTag entry = new CompoundTag(); ListTag stages = new ListTag();
            for (int j = 0; j < PerimeterStageLayout.MAX_STAGES; j++) stages.add(new CompoundTag());
            entry.put("Stages", stages); entries.add(entry);
        }
        tooManyStages.getCompound(PerimeterProjectStore.KEY).put("Terminals", entries);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.terminals(tooManyStages));
        assertEquals(receipt.save(), PerimeterProjectStore.terminal(core, fresh.header().projectId()).save());
    }

    private static PerimeterProject canceled(CompoundTag core, PerimeterProject fresh, boolean paid) {
        PerimeterProjectStore.prepare(core, fresh, () -> {}); var current = paid
                ? PerimeterProjectStore.consumeOnce(core, fresh.header().projectId(), fresh.manifestHash(), 64, false, () -> {}).project() : fresh;
        var canceled = current.cancel(current.check(), "Owner canceled"); PerimeterProjectStore.replace(core, current.check(), canceled, () -> {}); return canceled;
    }
    // Test model witnesses only. Actual runtime must independently prove ledger/entity cleanup first.
    private static PerimeterTerminalReceipt.CleanupProof proof(PerimeterProject terminal) {
        var h = terminal.header(); return new PerimeterTerminalReceipt.CleanupProof(h.projectId(), h.generation(), terminal.manifestHash(), terminal.revision(),
                terminal.state(), h.owner(), h.builder(), UUID.randomUUID(), terminal.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                "1".repeat(64), "2".repeat(64), "3".repeat(64));
    }
    private static void corrupt(PerimeterTerminalReceipt receipt, java.util.function.Consumer<CompoundTag> edit) {
        CompoundTag saved = receipt.save(); edit.accept(saved); assertThrows(IllegalArgumentException.class, () -> PerimeterTerminalReceipt.load(saved));
    }
    private static CompoundTag core() { CompoundTag core = new CompoundTag(); core.putLong("BankEmeralds", 1000); return core; }
}
