package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterBuilderAssignmentsTest extends MinecraftTestSupport {
    private PerimeterProject running() { var paid=PerimeterProjectTest.project().paid(false); return paid.activate(paid.check()); }
    @Test void replacementAndReloadPreservePaidManifestAndCompletedStageHistory() {
        var project=running();var original=project.save();var assignments=new PerimeterBuilderAssignments();
        UUID next=UUID.randomUUID();
        assignments.replace(project,project.header().builder(),next,project.active().areaId(),"a".repeat(64));
        assertEquals(next,assignments.builder(project));assertEquals(original,project.save());
        var loaded=PerimeterBuilderAssignments.load(assignments.save());
        assertEquals(next,loaded.builder(PerimeterProject.load(original)));
        assertTrue(loaded.canRebind(project,project.header().builder(),project.active().areaId()));
        assertFalse(loaded.canRebind(project,UUID.randomUUID(),project.active().areaId()));
        assertFalse(loaded.canRebind(project,project.header().builder(),UUID.randomUUID()));
        assertEquals(next,loaded.builder(project.header().projectId(),project.header().generation(),project.manifestHash(),project.header().builder()));
    }
    @Test void gatedPaidBlueprintAndGateContractRemainFrozenAcrossReplacement() {
        var paid=PerimeterGateProjectFixture.project().paid(false);var p=paid.activate(paid.check());
        var before=p.save();var a=new PerimeterBuilderAssignments();UUID replacement=UUID.randomUUID();
        a.replace(p,p.header().builder(),replacement,p.active().areaId(),"a".repeat(64));
        assertEquals(replacement,PerimeterBuilderAssignments.load(a.save()).builder(p));
        assertEquals(before,p.save());assertNotNull(p.gateContract());
    }
    @Test void repeatedDeathsRejectStaleAndPreviouslyRetiredWorkers() {
        var p=running();var a=new PerimeterBuilderAssignments();UUID b=UUID.randomUUID(),c=UUID.randomUUID();
        a.replace(p,p.header().builder(),b,p.active().areaId(),"a".repeat(64));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,p.header().builder(),c,p.active().areaId(),"b".repeat(64)));
        a.replace(p,b,c,p.active().areaId(),"b".repeat(64));
        assertEquals(c,PerimeterBuilderAssignments.load(a.save()).builder(p));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,c,b,p.active().areaId(),"c".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,c,p.header().builder(),p.active().areaId(),"c".repeat(64)));
    }
    @Test void unpaidCanceledWrongSectionAndMalformedDeathNeverGrantAssignment() {
        var p=running();var a=new PerimeterBuilderAssignments();UUID next=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->a.replace(PerimeterProjectTest.project(),p.header().builder(),next,p.active().areaId(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p.cancel(p.check(),"cancel"),p.header().builder(),next,p.active().areaId(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,p.header().builder(),next,UUID.randomUUID(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,p.header().builder(),next,p.active().areaId(),"missing"));
        assertEquals(p.header().builder(),a.builder(p));
    }
    @Test void corruptedChainAndConflictingPaidProjectFailClosed() {
        var p=running();var a=new PerimeterBuilderAssignments();a.replace(p,p.header().builder(),UUID.randomUUID(),p.active().areaId(),"a".repeat(64));
        var saved=a.save();saved.getList("Projects",Tag.TAG_COMPOUND).getCompound(0).getList("History",Tag.TAG_COMPOUND).getCompound(0).putUUID("From",UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->PerimeterBuilderAssignments.load(saved));
        assertThrows(IllegalArgumentException.class,()->a.builder(p.header().projectId(),p.header().generation()+1,p.manifestHash(),p.header().builder()));
    }
    @Test void legacyVersionOneDeathHistoryStillLoadsAndUpgradesWithoutChangingAssignment() {
        var p = running(); var assignments = new PerimeterBuilderAssignments(); UUID replacement = UUID.randomUUID();
        assignments.replace(p, p.header().builder(), replacement, p.active().areaId(), "a".repeat(64));
        var legacy = assignments.save(); legacy.putInt("Version", 1);
        var handoff = legacy.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("History", Tag.TAG_COMPOUND).getCompound(0);
        handoff.putString("Death", handoff.getString("Receipt")); handoff.remove("Receipt"); handoff.remove("Cause");
        var loaded = PerimeterBuilderAssignments.load(legacy);
        assertEquals(replacement, loaded.builder(p)); assertTrue(loaded.known(p, p.header().builder()));
        assertEquals(2, loaded.save().getInt("Version"));
        assertEquals("DEATH", loaded.save().getList("Projects", Tag.TAG_COMPOUND).getCompound(0)
                .getList("History", Tag.TAG_COMPOUND).getCompound(0).getString("Cause"));
        assertDoesNotThrow(() -> loaded.verifyTransferReceipts(UUID.randomUUID()));
    }

    @Test void nondeathTransfersFenceOldCoordinatorsWithoutMintingDestructionEvidence() {
        var p = running(); var frozen = p.save(); var a = new PerimeterBuilderAssignments(); UUID generation = UUID.randomUUID();
        UUID b = UUID.randomUUID(), c = UUID.randomUUID();
        a.transfer(p, p.header().builder(), b, p.active().areaId(), PerimeterBuilderAssignments.Cause.UNAVAILABLE, generation);
        a.transfer(p, b, c, p.active().areaId(), PerimeterBuilderAssignments.Cause.DISMISSED, generation);
        var loaded = PerimeterBuilderAssignments.load(a.save()); loaded.verifyTransferReceipts(generation);
        assertEquals(c, loaded.builder(p)); assertEquals(c, loaded.builderForArea(p, p.active().areaId())); assertEquals(frozen, p.save());
        for (UUID id : new UUID[]{p.header().builder(), b, c}) assertTrue(loaded.known(p, id));
        assertTrue(loaded.canRebind(p, p.header().builder(), p.active().areaId())); assertTrue(loaded.canRebind(p, b, p.active().areaId()));
        var history = loaded.save().getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("History", Tag.TAG_COMPOUND);
        assertEquals("UNAVAILABLE", history.getCompound(0).getString("Cause")); assertEquals("DISMISSED", history.getCompound(1).getString("Cause"));
        assertFalse(history.getCompound(0).contains("Death")); assertFalse(history.getCompound(1).contains("Death"));
        assertNull(new PerimeterBuilderAssignments.Handoff(p.header().builder(), b, p.active().areaId(),
                PerimeterBuilderAssignments.Cause.UNAVAILABLE, "a".repeat(64)).death());
        assertThrows(IllegalArgumentException.class, () -> loaded.replace(p, c, b, p.active().areaId(), "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> loaded.transfer(p, c, p.header().builder(), p.active().areaId(), PerimeterBuilderAssignments.Cause.UNAVAILABLE, generation));
        assertThrows(IllegalArgumentException.class, () -> loaded.transfer(p, c, UUID.randomUUID(), p.active().areaId(), PerimeterBuilderAssignments.Cause.DEATH, generation));
    }

    @Test void roleCleanupIncludesBothSidesOfHandoffButNeverBeforeAdmissionOrAfterDeparture() {
        var p = running(); var a = new PerimeterBuilderAssignments(); UUID generation = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID(); UUID zero = p.active().areaId();
        a.replace(p, p.header().builder(), first, zero, "a".repeat(64));
        p = p.verifyStage(p.check(), p.expectedStageReceipt()); p = p.retireVerifiedStage(p.check()); p = p.activate(p.check());
        UUID one = p.active().areaId();
        a.transfer(p, first, second, one, PerimeterBuilderAssignments.Cause.UNAVAILABLE, generation);
        a.transfer(p, second, third, one, PerimeterBuilderAssignments.Cause.DISMISSED, generation);
        assertTrue(a.areaKnown(p, p.header().builder(), zero)); assertFalse(a.areaKnown(p, p.header().builder(), one));
        assertTrue(a.areaKnown(p, first, zero)); assertTrue(a.areaKnown(p, first, one));
        assertFalse(a.areaKnown(p, second, zero)); assertTrue(a.areaKnown(p, second, one)); assertTrue(a.areaKnown(p, third, one));
        assertEquals(zero, a.admissionArea(p, p.header().builder())); assertEquals(zero, a.admissionArea(p, first));
        assertEquals(one, a.admissionArea(p, second)); assertEquals(one, a.admissionArea(p, third));
        assertEquals(first, a.builderForArea(p, zero)); assertEquals(third, a.builderForArea(p, one));
        var canceled = p.cancel(p.check(), "Cancel fixture");
        var proof = new PerimeterTerminalReceipt.CleanupProof(p.header().projectId(), p.header().generation(), p.manifestHash(), canceled.revision(), canceled.state(),
                p.header().owner(), p.header().builder(), generation, p.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                "1".repeat(64), "2".repeat(64), "3".repeat(64));
        var terminal = PerimeterTerminalReceipt.compact(canceled, proof);
        assertTrue(a.known(terminal, p.header().builder())); assertTrue(a.known(terminal, second));
        assertFalse(a.areaKnown(terminal, p.header().builder(), one)); assertFalse(a.areaKnown(terminal, second, zero));
        assertTrue(a.areaKnown(terminal, first, one)); assertTrue(a.areaKnown(terminal, second, one)); assertEquals(one, a.admissionArea(terminal, third));
    }

    @Test void changedTransferCauseAndCopiedLedgerIdentityCannotAuthenticateHandoff() {
        var p = running(); var a = new PerimeterBuilderAssignments(); UUID generation = UUID.randomUUID();
        a.transfer(p, p.header().builder(), UUID.randomUUID(), p.active().areaId(), PerimeterBuilderAssignments.Cause.UNAVAILABLE, generation);
        assertThrows(IllegalArgumentException.class, () -> a.verifyTransferReceipts(UUID.randomUUID()));
        var changed = a.save(); changed.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("History", Tag.TAG_COMPOUND)
                .getCompound(0).putString("Cause", "DISMISSED");
        var copied = PerimeterBuilderAssignments.load(changed);
        assertThrows(IllegalArgumentException.class, () -> copied.verifyTransferReceipts(generation));
        changed.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("History", Tag.TAG_COMPOUND).getCompound(0).putString("Cause", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderAssignments.load(changed));
        var missing = a.save(); missing.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("History", Tag.TAG_COMPOUND).getCompound(0).remove("Cause");
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderAssignments.load(missing));
    }

}
