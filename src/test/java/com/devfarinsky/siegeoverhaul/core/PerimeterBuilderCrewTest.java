package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterBuilderCrewTest extends MinecraftTestSupport {
    private final UUID ledgerGeneration = UUID.randomUUID();
    private PerimeterProject running() {
        var paid = PerimeterProjectTest.project().paid(false);
        return paid.activate(paid.check());
    }

    @Test void membershipReloadPreservesPaidBlueprintAndCoordinatorHistory() {
        var project = running(); var original = project.save();
        var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(enlist(crew, project, a)); assertTrue(enlist(crew, project, b));
        var restored = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertEquals(Set.of(a, b), restored.active(PerimeterProject.load(original)));
        assertEquals(original, project.save());
        assertEquals(project.header().builder(), new PerimeterBuilderAssignments().builder(project));
    }

    @Test void retirementPreservesOnlyCleanupEvidenceAcrossReload() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID helper = UUID.randomUUID();
        assertTrue(enlist(crew, project, helper)); assertTrue(crew.retire(project, helper));
        var restored = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(restored.known(project, helper)); assertTrue(restored.active(project).isEmpty());
        assertFalse(enlist(restored, project, helper)); assertFalse(restored.retire(project, helper));
    }

    @Test void cappedHelpersRejectDuplicatesAndOriginalBuilder() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID();
        assertFalse(enlist(crew, project, project.header().builder()));
        assertTrue(enlist(crew, project, a)); assertFalse(enlist(crew, project, a));
        assertTrue(enlist(crew, project, UUID.randomUUID())); assertTrue(enlist(crew, project, UUID.randomUUID()));
        assertFalse(enlist(crew, project, UUID.randomUUID()));
        assertTrue(crew.retire(project, a)); assertTrue(enlist(crew, project, UUID.randomUUID()));
        assertEquals(PerimeterBuilderCrew.MAX_HELPERS, crew.active(project).size());
    }

    @Test void unpaidCanceledAndForeignProjectIdentityCannotGainMembers() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID();
        assertFalse(enlist(crew, PerimeterProjectTest.project(), a));
        assertFalse(enlist(crew, project.cancel(project.check(), "cancel"), a));
        assertTrue(enlist(crew, project, a));
        CompoundTag saved = crew.save(); saved.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).putString("Hash", "a".repeat(64));
        var conflict = PerimeterBuilderCrew.load(saved, ledgerGeneration);
        assertThrows(IllegalArgumentException.class, () -> conflict.active(project));
        assertThrows(IllegalArgumentException.class, () -> conflict.retire(project, a));
    }

    @Test void independentProjectCannotReserveAlreadyActiveHelper() {
        var first = running(); var second = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID();
        assertNotEquals(first.header().projectId(), second.header().projectId());
        assertTrue(enlist(crew, first, a)); assertFalse(enlist(crew, second, a));
        assertTrue(crew.retire(first, a)); assertTrue(enlist(crew, second, a));
        assertTrue(crew.known(first, a)); assertEquals(Set.of(a), crew.active(second));
    }

    @Test void malformedDuplicateAndNonCanonicalMembershipFailsClosed() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); assertTrue(enlist(crew, project, UUID.randomUUID()));
        CompoundTag badBoolean = crew.save();
        badBoolean.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("Members", Tag.TAG_COMPOUND).getCompound(0).putByte("Active", (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(badBoolean, ledgerGeneration));
        CompoundTag duplicate = crew.save(); ListTag members = duplicate.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("Members", Tag.TAG_COMPOUND);
        members.add(members.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(duplicate, ledgerGeneration));
        CompoundTag unknown = crew.save(); unknown.putBoolean("AuthorizeAll", true);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(unknown, ledgerGeneration));
        assertThrows(IllegalArgumentException.class, () -> enlist(crew, project, new UUID(0, 0)));
    }

    @Test void copiedLedgerAndAheadOfProjectAdmissionsNeverGrantAuthority() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        assertTrue(enlist(crew, project, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(crew.save(), UUID.randomUUID()));
        var ahead = crew.save();
        ahead.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("Members", Tag.TAG_COMPOUND)
                .getCompound(0).putInt("AdmittedStage", project.activeStage() + 1);
        var oversized = crew.save();
        oversized.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("Members", Tag.TAG_COMPOUND)
                .getCompound(0).putInt("AdmittedStage", PerimeterStageLayout.MAX_STAGES);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(oversized, ledgerGeneration));
        var restored = PerimeterBuilderCrew.load(ahead, ledgerGeneration);
        assertThrows(IllegalArgumentException.class, () -> restored.active(project));
    }

    @Test void boundedHistoryDoesNotForgetRetiredSelectors() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        for (int i = 0; i < PerimeterBuilderCrew.MAX_HISTORY; i++) {
            UUID helper = UUID.randomUUID(); assertTrue(enlist(crew, project, helper)); assertTrue(crew.retire(project, helper));
        }
        assertFalse(enlist(crew, project, UUID.randomUUID()));
        assertTrue(PerimeterBuilderCrew.load(crew.save(), ledgerGeneration).active(project).isEmpty());
    }

    @Test void duplicatePersistedProjectIdsFailEvenWithDifferentWorkers() {
        CompoundTag saved = oneMemberSnapshot();
        ListTag projects = saved.getList("Projects", Tag.TAG_COMPOUND);
        CompoundTag duplicate = projects.getCompound(0).copy();
        duplicate.getList("Members", Tag.TAG_COMPOUND).getCompound(0).putUUID("Worker", UUID.randomUUID());
        projects.add(duplicate);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
    }

    @Test void persistedActiveHelperCannotBelongToTwoProjects() {
        CompoundTag saved = oneMemberSnapshot();
        ListTag projects = saved.getList("Projects", Tag.TAG_COMPOUND);
        CompoundTag second = projects.getCompound(0).copy();
        second.putUUID("Project", UUID.randomUUID());
        CompoundTag secondMember = second.getList("Members", Tag.TAG_COMPOUND).getCompound(0);
        UUID firstWorker = memberRecord(saved).getUUID("Worker");
        secondMember.putUUID("Worker", UUID.randomUUID());
        projects.add(second);
        assertDoesNotThrow(() -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
        secondMember.putUUID("Worker", firstWorker);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
    }

    @Test void persistedFourthActiveHelperIsRejectedWithoutDiscardingMembers() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        for (int i = 0; i < PerimeterBuilderCrew.MAX_HELPERS; i++)
            assertTrue(enlist(crew, project, UUID.randomUUID()));
        CompoundTag saved = crew.save();
        assertEquals(PerimeterBuilderCrew.MAX_HELPERS, PerimeterBuilderCrew.load(saved, ledgerGeneration).active(project).size());
        ListTag members = projectRecord(saved).getList("Members", Tag.TAG_COMPOUND);
        CompoundTag excess = members.getCompound(0).copy();
        excess.putUUID("Worker", UUID.randomUUID()); members.add(excess);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
        assertEquals(PerimeterBuilderCrew.MAX_HELPERS + 1, members.size(), "Malformed loading must not truncate retained evidence");
    }

    @Test void persistedSixtyFifthHistoryMemberIsRejectedEvenWhenAllAreRetired() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        for (int i = 0; i < PerimeterBuilderCrew.MAX_HISTORY; i++) {
            UUID worker = UUID.randomUUID();
            assertTrue(enlist(crew, project, worker)); assertTrue(crew.retire(project, worker));
        }
        CompoundTag saved = crew.save();
        assertTrue(PerimeterBuilderCrew.load(saved, ledgerGeneration).active(project).isEmpty());
        ListTag members = projectRecord(saved).getList("Members", Tag.TAG_COMPOUND);
        CompoundTag excess = members.getCompound(0).copy();
        excess.putUUID("Worker", UUID.randomUUID()); members.add(excess);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
    }

    @Test void persistedProjectHistoryCannotExceedTheGlobalBound() {
        CompoundTag saved = oneMemberSnapshot();
        CompoundTag template = projectRecord(saved).copy();
        template.getList("Members", Tag.TAG_COMPOUND).getCompound(0).putBoolean("Active", false);
        ListTag projects = new ListTag(); saved.put("Projects", projects);
        for (int i = 0; i < PerimeterBuilderCrew.MAX_PROJECTS; i++) {
            CompoundTag entry = template.copy(); entry.putUUID("Project", UUID.randomUUID()); projects.add(entry);
        }
        assertDoesNotThrow(() -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
        CompoundTag excess = template.copy(); excess.putUUID("Project", UUID.randomUUID()); projects.add(excess);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(saved, ledgerGeneration));
    }

    @Test void structurallyValidGenerationAndOriginalConflictsFailEveryProjectQuery() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        UUID helper = UUID.randomUUID(); assertTrue(enlist(crew, project, helper));
        for (String field : new String[]{"Generation", "Original"}) {
            CompoundTag saved = crew.save();
            if (field.equals("Generation")) projectRecord(saved).putLong(field, project.header().generation() + 1);
            else projectRecord(saved).putUUID(field, UUID.randomUUID());
            // A codec has no live project authority. The later exact-project lookup must reject the conflict.
            var restored = PerimeterBuilderCrew.load(saved, ledgerGeneration);
            assertThrows(IllegalArgumentException.class, () -> restored.active(project), field);
            assertThrows(IllegalArgumentException.class, () -> restored.known(project, helper), field);
            assertThrows(IllegalArgumentException.class, () -> restored.retire(project, helper), field);
            assertThrows(IllegalArgumentException.class, () -> enlist(restored, project, UUID.randomUUID()), field);
        }
    }

    @Test void missingAndWrongTypeFieldsFailClosedAtEveryRecordLevel() {
        CompoundTag saved = oneMemberSnapshot();
        assertRequiredFields(saved, Function.identity(), Set.of("Version", "LedgerGeneration", "Projects"));
        assertRequiredFields(saved, PerimeterBuilderCrewTest::projectRecord,
                Set.of("Project", "Generation", "Hash", "Original", "Members"));
        assertRequiredFields(saved, PerimeterBuilderCrewTest::memberRecord, Set.of("Worker", "Active", "Ready", "Withdrawn", "RetiredStage", "AdmittedStage"));
    }

    @Test void wrongListElementTypesAndEmptyMemberHistoryAreRejected() {
        CompoundTag wrongProjects = oneMemberSnapshot();
        ListTag numbers = new ListTag(); numbers.add(IntTag.valueOf(1)); wrongProjects.put("Projects", numbers);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(wrongProjects, ledgerGeneration));

        CompoundTag wrongMembers = oneMemberSnapshot();
        ListTag strings = new ListTag(); strings.add(StringTag.valueOf("worker")); projectRecord(wrongMembers).put("Members", strings);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(wrongMembers, ledgerGeneration));

        CompoundTag emptyMembers = oneMemberSnapshot(); projectRecord(emptyMembers).put("Members", new ListTag());
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(emptyMembers, ledgerGeneration));
    }

    @Test void destructionProofRetiresOnlyTheAuthenticatedHelperAndSurvivesReload() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        UUID dead = UUID.randomUUID(), active = UUID.randomUUID();
        assertTrue(enlist(crew, project, dead)); assertTrue(enlist(crew, project, active));
        assertFalse(crew.destroyed(project, dead, UUID.randomUUID(), project.activeStage()));
        assertFalse(crew.destroyed(project, dead, project.active().areaId(), -1));
        assertFalse(crew.destroyed(project, UUID.randomUUID(), project.active().areaId(), project.activeStage()));
        assertTrue(crew.destroyed(project, dead, project.active().areaId(), project.activeStage()));
        assertFalse(crew.destroyed(project, dead, project.active().areaId(), project.activeStage()));
        String proof = crew.destructionReceipt(project, dead); assertNotNull(proof);
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertEquals(proof, loaded.destructionReceipt(project, dead)); assertEquals(Set.of(active), loaded.active(project));
        assertTrue(loaded.known(project, dead)); assertFalse(enlist(loaded, project, dead)); assertNull(loaded.destructionReceipt(project, active));
    }

    @Test void changedDestructionScopeAndReactivationAreRejectedDuringLoading() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID helper = UUID.randomUUID();
        assertTrue(enlist(crew, project, helper));
        assertTrue(crew.destroyed(project, helper, project.active().areaId(), project.activeStage()));
        for (String field : new String[]{"Worker", "Active", "Stage", "Area", "Receipt", "Generation", "Hash", "Original", "LedgerGeneration"}) {
            CompoundTag changed = crew.save(); CompoundTag member = memberRecord(changed), proof = member.getCompound("Destruction");
            switch (field) {
                case "Worker" -> member.putUUID(field, UUID.randomUUID());
                case "Active" -> member.putBoolean(field, true);
                case "Stage" -> proof.putInt(field, project.activeStage() + 1);
                case "Area" -> proof.putUUID(field, UUID.randomUUID());
                case "Receipt" -> proof.putString(field, "a".repeat(64));
                case "Generation" -> projectRecord(changed).putLong(field, project.header().generation() + 1);
                case "Hash" -> projectRecord(changed).putString(field, "b".repeat(64));
                case "Original" -> projectRecord(changed).putUUID(field, UUID.randomUUID());
                case "LedgerGeneration" -> changed.putUUID(field, UUID.randomUUID());
                default -> fail("Missing mutation fixture");
            }
            UUID expected = changed.getUUID("LedgerGeneration");
            assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(changed, expected), field);
        }
        CompoundTag wrongType = crew.save(); memberRecord(wrongType).putString("Destruction", "not a proof");
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(wrongType, ledgerGeneration));
        for (String field : new String[]{"Stage", "Area", "Receipt"}) {
            CompoundTag missing = crew.save(); memberRecord(missing).getCompound("Destruction").remove(field);
            assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(missing, ledgerGeneration), field);
        }
    }

    @Test void terminalLookupPreservesCleanupEvidenceWithExactLedgerIdentity() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID helper = UUID.randomUUID();
        assertTrue(enlist(crew, project, helper));
        assertTrue(crew.destroyed(project, helper, project.active().areaId(), project.activeStage()));
        var canceled = project.cancel(project.check(), "Cancel fixture");
        var proof = new PerimeterTerminalReceipt.CleanupProof(canceled.header().projectId(), canceled.header().generation(), canceled.manifestHash(),
                canceled.revision(), canceled.state(), canceled.header().owner(), canceled.header().builder(), ledgerGeneration,
                canceled.stages().stream().map(PerimeterProject.Stage::areaId).toList(), "1".repeat(64), "2".repeat(64), "3".repeat(64));
        var terminal = PerimeterTerminalReceipt.compact(canceled, proof);
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(loaded.known(terminal, helper)); assertFalse(loaded.known(terminal, project.header().builder()));
        assertEquals(loaded.destructionReceipt(project, helper), loaded.destructionReceipt(terminal, helper));
        var otherGeneration = new PerimeterBuilderCrew(UUID.randomUUID());
        assertTrue(enlist(otherGeneration, project, helper)); assertFalse(otherGeneration.known(terminal, helper));
    }

    @Test void pendingAdmissionUsesCapacityButCannotWorkBeforeFinalActivation() {
        var p = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID pending = UUID.randomUUID();
        assertTrue(crew.enlist(p, pending)); assertTrue(crew.pending(p, pending)); assertFalse(crew.active(p, pending));
        assertEquals(Set.of(pending), crew.pending(p)); assertEquals(Set.of(pending), crew.reserved(p)); assertTrue(crew.active(p).isEmpty());
        assertFalse(crew.destroyed(p, pending, UUID.randomUUID(), p.activeStage()));
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(loaded.pending(p, pending)); assertTrue(loaded.enlist(p, UUID.randomUUID())); assertTrue(loaded.enlist(p, UUID.randomUUID()));
        assertFalse(loaded.enlist(p, UUID.randomUUID())); assertTrue(loaded.active(p).isEmpty());
        assertTrue(loaded.activate(p, pending)); assertTrue(loaded.activate(p, pending));
        assertTrue(loaded.active(p, pending)); assertFalse(loaded.pending(p, pending)); assertFalse(loaded.cancelPending(p, pending));
    }

    @Test void exactPendingDestructionRetainsUnactivatedCleanupProofAndReleasesOnlyItsCapacity() {
        var p = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID pending = UUID.randomUUID();
        assertTrue(crew.enlist(p, pending)); assertEquals(p.header().projectId(), crew.pendingProject(pending));
        assertNull(crew.pendingProject(UUID.randomUUID())); assertNull(crew.pendingProject(null));
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(loaded.destroyed(p, pending, p.active().areaId(), p.activeStage()));
        assertFalse(memberRecord(loaded.save()).getBoolean("Ready"), "Destruction must never invent completed admission");
        String proof = loaded.destructionReceipt(p, pending); assertNotNull(proof);
        loaded = PerimeterBuilderCrew.load(loaded.save(), ledgerGeneration);
        assertEquals(proof, loaded.destructionReceipt(p, pending)); assertNull(loaded.pendingProject(pending));
        assertTrue(loaded.reserved(p).isEmpty()); assertTrue(loaded.active(p).isEmpty()); assertTrue(loaded.pending(p).isEmpty());
        assertTrue(loaded.known(p, pending)); assertTrue(loaded.areaKnown(p, pending, p.active().areaId()));
        assertFalse(loaded.activate(p, pending)); assertFalse(loaded.enlist(p, pending)); assertFalse(loaded.cancelPending(p, pending));
        assertFalse(loaded.destroyed(p, pending, p.active().areaId(), p.activeStage()));
    }

    @Test void pendingDestructionCannotClaimAStageAfterItsInterruptedAdmission() {
        var first = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID pending = UUID.randomUUID();
        assertTrue(crew.enlist(first, pending));
        var second = first.verifyStage(first.check(), first.expectedStageReceipt());
        second = second.retireVerifiedStage(second.check()); second = second.activate(second.check());
        assertFalse(crew.destroyed(second, pending, second.active().areaId(), second.activeStage()));
        assertTrue(crew.pending(first, pending)); assertNull(crew.destructionReceipt(first, pending));
    }

    @Test void explicitlyWithdrawnUnactivatedIntentRetainsNoPlacementOrAreaCleanupAuthority() {
        var p = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID pending = UUID.randomUUID();
        assertTrue(crew.enlist(p, pending)); assertTrue(crew.cancelPending(p, pending));
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(loaded.known(p, pending)); assertTrue(loaded.reserved(p).isEmpty()); assertTrue(loaded.pending(p).isEmpty());
        assertFalse(loaded.active(p, pending)); assertFalse(loaded.areaKnown(p, pending, p.active().areaId())); assertNull(loaded.admissionArea(p, pending));
        assertFalse(loaded.enlist(p, pending)); assertFalse(loaded.activate(p, pending)); assertFalse(loaded.cancelPending(p, pending));
    }

    @Test void legacyMembershipLoadsReadyAndNewReadinessFieldsAreStrict() {
        var p = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID worker = UUID.randomUUID(); assertTrue(enlist(crew, p, worker));
        var legacy = crew.save(); legacy.putInt("Version", 1); memberRecord(legacy).remove("Ready"); memberRecord(legacy).remove("Withdrawn"); memberRecord(legacy).remove("RetiredStage");
        assertTrue(PerimeterBuilderCrew.load(legacy, ledgerGeneration).active(p, worker));
        for (String field : new String[]{"Ready", "Withdrawn"}) {
            var malformed = crew.save(); memberRecord(malformed).putByte(field, (byte) 2);
            assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(malformed, ledgerGeneration));
        }
        var withdrawnActive = crew.save(); memberRecord(withdrawnActive).putBoolean("Withdrawn", true);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(withdrawnActive, ledgerGeneration));
    }

    @Test void retainedHelperCleanupRejectsPreAdmissionAndPostDestructionStages() {
        var first = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID early = UUID.randomUUID(); UUID zero = first.active().areaId();
        assertTrue(enlist(crew, first, early)); assertTrue(crew.destroyed(first, early, zero, first.activeStage()));
        var second = first.verifyStage(first.check(), first.expectedStageReceipt()); second = second.retireVerifiedStage(second.check()); second = second.activate(second.check());
        UUID late = UUID.randomUUID(), one = second.active().areaId(); assertTrue(enlist(crew, second, late));
        assertEquals(one, crew.admissionArea(second, late)); assertFalse(crew.areaKnown(second, late, zero)); assertTrue(crew.areaKnown(second, late, one));
        assertTrue(crew.areaKnown(second, early, zero)); assertFalse(crew.areaKnown(second, early, one)); assertFalse(crew.areaKnown(second, late, UUID.randomUUID()));
        var canceled = second.cancel(second.check(), "Cancel fixture");
        var proof = new PerimeterTerminalReceipt.CleanupProof(second.header().projectId(), second.header().generation(), second.manifestHash(), canceled.revision(), canceled.state(),
                second.header().owner(), second.header().builder(), ledgerGeneration, second.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                "1".repeat(64), "2".repeat(64), "3".repeat(64));
        var terminal = PerimeterTerminalReceipt.compact(canceled, proof);
        assertFalse(crew.areaKnown(terminal, late, zero)); assertTrue(crew.areaKnown(terminal, late, one)); assertFalse(crew.areaKnown(terminal, early, one));
        assertEquals(one, crew.admissionArea(terminal, late));
    }

    @Test void retainedRetiredHelperCannotAuthenticateAStageAfterItsLastParticipation() {
        var first = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID helper = UUID.randomUUID();
        assertTrue(enlist(crew, first, helper)); UUID zero = first.active().areaId(); assertTrue(crew.retire(first, helper));
        var second = first.verifyStage(first.check(), first.expectedStageReceipt()); second = second.retireVerifiedStage(second.check()); second = second.activate(second.check());
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(loaded.areaKnown(second, helper, zero)); assertFalse(loaded.areaKnown(second, helper, second.active().areaId()));
        assertTrue(loaded.known(second, helper));
    }

    private boolean enlist(PerimeterBuilderCrew crew, PerimeterProject project, UUID worker) {
        boolean accepted = crew.enlist(project, worker);
        if (accepted) assertTrue(crew.activate(project, worker));
        return accepted;
    }

    private CompoundTag oneMemberSnapshot() {
        var crew = new PerimeterBuilderCrew(ledgerGeneration);
        assertTrue(enlist(crew, running(), UUID.randomUUID()));
        return crew.save();
    }

    private static CompoundTag projectRecord(CompoundTag root) {
        return root.getList("Projects", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag memberRecord(CompoundTag root) {
        return projectRecord(root).getList("Members", Tag.TAG_COMPOUND).getCompound(0);
    }

    private void assertRequiredFields(CompoundTag snapshot, Function<CompoundTag, CompoundTag> record, Set<String> fields) {
        for (String field : fields) {
            CompoundTag missing = snapshot.copy(); record.apply(missing).remove(field);
            assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(missing, ledgerGeneration), "Missing " + field);
            CompoundTag wrongType = snapshot.copy(); CompoundTag target = record.apply(wrongType);
            target.put(field, target.contains(field, Tag.TAG_STRING) ? IntTag.valueOf(1) : StringTag.valueOf("wrong type"));
            assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(wrongType, ledgerGeneration), "Wrong type for " + field);
        }
    }
}
