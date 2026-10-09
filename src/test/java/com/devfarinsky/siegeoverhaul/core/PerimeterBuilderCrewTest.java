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
        assertTrue(crew.enlist(project, a)); assertTrue(crew.enlist(project, b));
        var restored = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertEquals(Set.of(a, b), restored.active(PerimeterProject.load(original)));
        assertEquals(original, project.save());
        assertEquals(project.header().builder(), new PerimeterBuilderAssignments().builder(project));
    }

    @Test void retirementPreservesOnlyCleanupEvidenceAcrossReload() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID helper = UUID.randomUUID();
        assertTrue(crew.enlist(project, helper)); assertTrue(crew.retire(project, helper));
        var restored = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertTrue(restored.known(project, helper)); assertTrue(restored.active(project).isEmpty());
        assertFalse(restored.enlist(project, helper)); assertFalse(restored.retire(project, helper));
    }

    @Test void cappedHelpersRejectDuplicatesAndOriginalBuilder() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID();
        assertFalse(crew.enlist(project, project.header().builder()));
        assertTrue(crew.enlist(project, a)); assertFalse(crew.enlist(project, a));
        assertTrue(crew.enlist(project, UUID.randomUUID())); assertTrue(crew.enlist(project, UUID.randomUUID()));
        assertFalse(crew.enlist(project, UUID.randomUUID()));
        assertTrue(crew.retire(project, a)); assertTrue(crew.enlist(project, UUID.randomUUID()));
        assertEquals(PerimeterBuilderCrew.MAX_HELPERS, crew.active(project).size());
    }

    @Test void unpaidCanceledAndForeignProjectIdentityCannotGainMembers() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID();
        assertFalse(crew.enlist(PerimeterProjectTest.project(), a));
        assertFalse(crew.enlist(project.cancel(project.check(), "cancel"), a));
        assertTrue(crew.enlist(project, a));
        CompoundTag saved = crew.save(); saved.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).putString("Hash", "a".repeat(64));
        var conflict = PerimeterBuilderCrew.load(saved, ledgerGeneration);
        assertThrows(IllegalArgumentException.class, () -> conflict.active(project));
        assertThrows(IllegalArgumentException.class, () -> conflict.retire(project, a));
    }

    @Test void independentProjectCannotReserveAlreadyActiveHelper() {
        var first = running(); var second = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID a = UUID.randomUUID();
        assertNotEquals(first.header().projectId(), second.header().projectId());
        assertTrue(crew.enlist(first, a)); assertFalse(crew.enlist(second, a));
        assertTrue(crew.retire(first, a)); assertTrue(crew.enlist(second, a));
        assertTrue(crew.known(first, a)); assertEquals(Set.of(a), crew.active(second));
    }

    @Test void malformedDuplicateAndNonCanonicalMembershipFailsClosed() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); assertTrue(crew.enlist(project, UUID.randomUUID()));
        CompoundTag badBoolean = crew.save();
        badBoolean.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("Members", Tag.TAG_COMPOUND).getCompound(0).putByte("Active", (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(badBoolean, ledgerGeneration));
        CompoundTag duplicate = crew.save(); ListTag members = duplicate.getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("Members", Tag.TAG_COMPOUND);
        members.add(members.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(duplicate, ledgerGeneration));
        CompoundTag unknown = crew.save(); unknown.putBoolean("AuthorizeAll", true);
        assertThrows(IllegalArgumentException.class, () -> PerimeterBuilderCrew.load(unknown, ledgerGeneration));
        assertThrows(IllegalArgumentException.class, () -> crew.enlist(project, new UUID(0, 0)));
    }

    @Test void copiedLedgerAndAheadOfProjectAdmissionsNeverGrantAuthority() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration);
        assertTrue(crew.enlist(project, UUID.randomUUID()));
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
            UUID helper = UUID.randomUUID(); assertTrue(crew.enlist(project, helper)); assertTrue(crew.retire(project, helper));
        }
        assertFalse(crew.enlist(project, UUID.randomUUID()));
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
            assertTrue(crew.enlist(project, UUID.randomUUID()));
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
            assertTrue(crew.enlist(project, worker)); assertTrue(crew.retire(project, worker));
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
        UUID helper = UUID.randomUUID(); assertTrue(crew.enlist(project, helper));
        for (String field : new String[]{"Generation", "Original"}) {
            CompoundTag saved = crew.save();
            if (field.equals("Generation")) projectRecord(saved).putLong(field, project.header().generation() + 1);
            else projectRecord(saved).putUUID(field, UUID.randomUUID());
            // A codec has no live project authority. The later exact-project lookup must reject the conflict.
            var restored = PerimeterBuilderCrew.load(saved, ledgerGeneration);
            assertThrows(IllegalArgumentException.class, () -> restored.active(project), field);
            assertThrows(IllegalArgumentException.class, () -> restored.known(project, helper), field);
            assertThrows(IllegalArgumentException.class, () -> restored.retire(project, helper), field);
            assertThrows(IllegalArgumentException.class, () -> restored.enlist(project, UUID.randomUUID()), field);
        }
    }

    @Test void missingAndWrongTypeFieldsFailClosedAtEveryRecordLevel() {
        CompoundTag saved = oneMemberSnapshot();
        assertRequiredFields(saved, Function.identity(), Set.of("Version", "LedgerGeneration", "Projects"));
        assertRequiredFields(saved, PerimeterBuilderCrewTest::projectRecord,
                Set.of("Project", "Generation", "Hash", "Original", "Members"));
        assertRequiredFields(saved, PerimeterBuilderCrewTest::memberRecord, Set.of("Worker", "Active", "AdmittedStage"));
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
        assertTrue(crew.enlist(project, dead)); assertTrue(crew.enlist(project, active));
        assertFalse(crew.destroyed(project, dead, UUID.randomUUID(), project.activeStage()));
        assertFalse(crew.destroyed(project, dead, project.active().areaId(), -1));
        assertFalse(crew.destroyed(project, UUID.randomUUID(), project.active().areaId(), project.activeStage()));
        assertTrue(crew.destroyed(project, dead, project.active().areaId(), project.activeStage()));
        assertFalse(crew.destroyed(project, dead, project.active().areaId(), project.activeStage()));
        String proof = crew.destructionReceipt(project, dead); assertNotNull(proof);
        var loaded = PerimeterBuilderCrew.load(crew.save(), ledgerGeneration);
        assertEquals(proof, loaded.destructionReceipt(project, dead)); assertEquals(Set.of(active), loaded.active(project));
        assertTrue(loaded.known(project, dead)); assertFalse(loaded.enlist(project, dead)); assertNull(loaded.destructionReceipt(project, active));
    }

    @Test void changedDestructionScopeAndReactivationAreRejectedDuringLoading() {
        var project = running(); var crew = new PerimeterBuilderCrew(ledgerGeneration); UUID helper = UUID.randomUUID();
        assertTrue(crew.enlist(project, helper));
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
        assertTrue(crew.enlist(project, helper));
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
        assertTrue(otherGeneration.enlist(project, helper)); assertFalse(otherGeneration.known(terminal, helper));
    }

    private CompoundTag oneMemberSnapshot() {
        var crew = new PerimeterBuilderCrew(ledgerGeneration);
        assertTrue(crew.enlist(running(), UUID.randomUUID()));
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
