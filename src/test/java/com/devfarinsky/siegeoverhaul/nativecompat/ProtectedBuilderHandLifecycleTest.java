package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedBuilderHandLifecycleTest extends MinecraftTestSupport {
    @Test void stagedCancellationAndModelCompletionKeepHandProvenanceAcrossEveryRetirementAndCompaction() {
        for (boolean complete : new boolean[]{false, true}) {
            var p=ConstructionProjectLedgerTest.project();var core=new CompoundTag();var ledger=new ConstructionEditLedger();var data=new CompoundTag();
            com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.prepare(core,p,()->{});
            assertTrue(ledger.registerProject(p));assertTrue(ledger.leaseProjectStage(p));
            assertTrue(ledger.retainHandLifecycle(data,p.header().builder(),p.header().owner(),p.active().areaId()));
            var original=ledger.handLifecycle(p.header().builder());
            p=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.consumeOnce(core,p.header().projectId(),p.manifestHash(),64,true,()->{}).project();
            if (complete) {
                while(p.active()!=null) {
                    p=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.replace(core,p.check(),p.activate(p.check()),()->{});
                    p=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.replace(core,p.check(),p.verifyStage(p.check(),p.expectedStageReceipt()),()->{});
                    ledger.retire(p.active().areaId(),true);
                    p=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.replace(core,p.check(),p.retireVerifiedStage(p.check()),()->{});
                    ledger=ConstructionEditLedger.load(ledger.save(new CompoundTag()));
                    assertTrue(ProtectedBuilderHandLifecycle.matches(data,p.header().builder(),ledger));
                    if(p.active()!=null) assertTrue(ledger.leaseProjectStage(p));
                }
                p=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.replace(core,p.check(),p.complete(p.check(),p.manifestHash()),()->{});
            } else p=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.replace(core,p.check(),p.cancel(p.check(),"Canceled"),()->{});
            ledger.retire(p.header().projectId(),true);
            var proof=new com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt.CleanupProof(p.header().projectId(),p.header().generation(),p.manifestHash(),p.revision(),
                    p.state(),p.header().owner(),p.header().builder(),ledger.generation(),p.stages().stream().map(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage::areaId).toList(),
                    "1".repeat(64),"2".repeat(64),"3".repeat(64));
            var terminal=com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.compact(core,p.check(),proof,()->{});
            assertEquals(p.state(),terminal.state());assertEquals(original,ledger.handLifecycle(p.header().builder()));
            assertFalse(ledger.contains(p.header().projectId()));
            for(var stage:p.stages())assertFalse(ledger.completeReservation(stage.areaId()));
            ledger=ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            assertTrue(ProtectedBuilderHandLifecycle.matches(data,p.header().builder(),ledger));
        }
    }

    @Test void manualCompletionAndCancellationReleaseReservationsButRetainOnlyHandProvenance() {
        for (boolean cleaned : new boolean[]{true, false}) {
            var ledger = new ConstructionEditLedger(); var data = new CompoundTag();
            UUID area = UUID.randomUUID(), builder = UUID.randomUUID(), owner = UUID.randomUUID();
            assertTrue(ledger.canRetainHandLifecycle(data, builder));
            assertTrue(ledger.register(area, Set.of(BlockPos.ZERO)));
            assertTrue(ledger.retainHandLifecycle(data, builder, owner, area));
            var receipt = ledger.handLifecycle(builder);
            ledger.retire(area, cleaned); ledger.acknowledgeRetirement(area);
            ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            assertEquals(receipt, ledger.handLifecycle(builder));
            assertTrue(ProtectedBuilderHandLifecycle.matches(data.copy(), builder, ledger));
            assertFalse(ledger.contains(area)); assertFalse(ledger.retired(area));
            assertFalse(ledger.reserves(Set.of(BlockPos.ZERO))); assertFalse(ledger.completeReservation(area));
            assertEquals(owner, receipt.owner()); assertEquals(area, receipt.area());
        }
    }

    @Test void oneReceiptIsReusedAfterLaterJobsAndNativeOwnershipTransferWithoutNewAuthority() {
        var ledger = new ConstructionEditLedger(); var data = new CompoundTag();
        UUID area = UUID.randomUUID(), builder = UUID.randomUUID(), owner = UUID.randomUUID();
        assertTrue(ledger.register(area, Set.of(BlockPos.ZERO)));
        assertTrue(ledger.retainHandLifecycle(data, builder, owner, area));
        var receipt = ledger.handLifecycle(builder); ledger.retire(area, true);
        UUID later = UUID.randomUUID(), newOwner = UUID.randomUUID();
        assertTrue(ledger.register(later, Set.of(BlockPos.ZERO)));
        assertTrue(ledger.retainHandLifecycle(data, builder, newOwner, later));
        assertEquals(receipt, ledger.handLifecycle(builder)); assertEquals(owner, receipt.owner());
        assertEquals(1, ledger.save(new CompoundTag()).getList("HandLifecycles", Tag.TAG_COMPOUND).size());
        assertFalse(NativeConstructionGuard.validHandReceipt(data, ledger));
    }

    @Test void arbitraryTagsCopiedIdentitiesMissingHalvesAndChangedGenerationsNeverAuthorizeBinding() {
        var ledger = new ConstructionEditLedger(); var data = new CompoundTag();
        UUID area = UUID.randomUUID(), builder = UUID.randomUUID(), owner = UUID.randomUUID();
        assertTrue(ledger.register(area, Set.of(BlockPos.ZERO)));
        assertTrue(ledger.retainHandLifecycle(data, builder, owner, area));
        assertFalse(ProtectedBuilderHandLifecycle.matches(data, UUID.randomUUID(), ledger));
        assertFalse(ProtectedBuilderHandLifecycle.matches(data, builder, new ConstructionEditLedger()));
        assertFalse(ProtectedBuilderHandLifecycle.matches(new CompoundTag(), builder, ledger));
        assertFalse(ledger.retainHandLifecycle(new CompoundTag(), builder, owner, area), "No reconstruction from one-sided world history");
        for (String key : new String[]{"Receipt", "Builder", "Owner", "Area", "Generation"}) {
            var altered = data.copy(); altered.getCompound(ProtectedBuilderHandLifecycle.KEY).putUUID(key, UUID.randomUUID());
            assertFalse(ProtectedBuilderHandLifecycle.matches(altered, builder, ledger), key);
            assertFalse(ledger.retainHandLifecycle(altered, builder, owner, area), key);
        }
        var missingWorld = ledger.save(new CompoundTag()); missingWorld.remove("HandLifecycles");
        assertFalse(ProtectedBuilderHandLifecycle.matches(data, builder, ConstructionEditLedger.load(missingWorld)));
        var malformed = data.copy(); malformed.putString(ProtectedBuilderHandLifecycle.KEY, "untrusted");
        assertFalse(ProtectedBuilderHandLifecycle.matches(malformed, builder, ledger));
    }

    @Test void malformedWorldProvenanceInvalidatesAuthorityRatherThanPartiallyLoading() {
        var ledger = new ConstructionEditLedger(); var data = new CompoundTag();
        UUID area = UUID.randomUUID(), builder = UUID.randomUUID();
        assertTrue(ledger.register(area, Set.of(BlockPos.ZERO)));
        assertTrue(ledger.retainHandLifecycle(data, builder, UUID.randomUUID(), area));
        for (int kind = 0; kind < 5; kind++) {
            var root = ledger.save(new CompoundTag()); var hands = root.getList("HandLifecycles", Tag.TAG_COMPOUND);
            if (kind == 0) root.putString("HandLifecycles", "wrong type");
            if (kind == 1) hands.add(hands.getCompound(0).copy());
            if (kind == 2) hands.getCompound(0).putUUID("Generation", UUID.randomUUID());
            if (kind == 3) hands.getCompound(0).putString("Owner", "wrong type");
            if (kind == 4) hands.getCompound(0).putBoolean("Unknown", true);
            var loaded = ConstructionEditLedger.load(root);
            assertFalse(ProtectedBuilderHandLifecycle.matches(data, builder, loaded));
            assertFalse(loaded.sameGeneration(ledger.generation())); assertFalse(loaded.completeReservation(area));
        }
    }

    @Test void boundedHistoryNeverEvictsOrReplacesOldProvenance() {
        var ledger = new ConstructionEditLedger(); UUID area = UUID.randomUUID();
        assertTrue(ledger.register(area, Set.of(BlockPos.ZERO)));
        var first = new CompoundTag(); UUID builder = UUID.randomUUID();
        assertTrue(ledger.retainHandLifecycle(first, builder, UUID.randomUUID(), area));
        for (int i = 1; i < ProtectedBuilderHandLifecycle.MAX_BUILDERS; i++)
            assertTrue(ledger.retainHandLifecycle(new CompoundTag(), UUID.randomUUID(), UUID.randomUUID(), area));
        assertFalse(ledger.retainHandLifecycle(new CompoundTag(), UUID.randomUUID(), UUID.randomUUID(), area));
        assertTrue(ledger.retainHandLifecycle(first, builder, UUID.randomUUID(), area));
        var root = ledger.save(new CompoundTag());
        assertTrue(ProtectedBuilderHandLifecycle.matches(first, builder, ConstructionEditLedger.load(root)));
        var list = root.getList("HandLifecycles", Tag.TAG_COMPOUND);
        list.add(new ProtectedBuilderHandLifecycle.Receipt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), area, ledger.generation()).save());
        assertFalse(ConstructionEditLedger.load(root).sameGeneration(ledger.generation()));
    }
}
