package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageLayout;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionCrewLedgerTest extends MinecraftTestSupport {
    private record Fixture(ConstructionEditLedger ledger, CompoundTag core, PerimeterProject project) {}

    private Fixture running() { return running(new ConstructionEditLedger(), 0); }

    private Fixture running(ConstructionEditLedger ledger, int chunkX) {
        Set<ChunkPos> territory = Set.of(new ChunkPos(chunkX, 0));
        var plan = PerimeterBlueprint.create(territory, (x, z) -> PerimeterBlueprint.Surface.ready(65), PerimeterBlueprint.Palette.COBBLESTONE);
        var layout = PerimeterStageLayout.partition(plan, stage -> stage.targets().size() <= 500 ? null : "Fixture stage capacity");
        var header = PerimeterProject.Header.newCommission(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                "team:crew", new BlockPos(chunkX * 16 + 8, 65, 8), "crew", 1, "a".repeat(64), territory);
        Map<Long, BlockState> before = new HashMap<>(), clearance = new HashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        plan.clearance().forEach(cell -> clearance.put(cell, Blocks.AIR.defaultBlockState()));
        var prepared = PerimeterProject.prepare(header, plan, layout, before, clearance);
        var core = new CompoundTag(); core.putLong("BankEmeralds", 64);
        assertTrue(ledger.registerProject(prepared)); assertTrue(ledger.leaseProjectStage(prepared));
        PerimeterProjectStore.prepare(core, prepared, () -> {});
        var paid = PerimeterProjectStore.consumeOnce(core, prepared.header().projectId(), prepared.manifestHash(), 64, false, () -> {}).project();
        var project = PerimeterProjectStore.replace(core, paid.check(), paid.activate(paid.check()), () -> {});
        return new Fixture(ledger, core, project);
    }

    @Test void saveLoadRetainsActiveAndRetiredHelpersWithoutChangingPaidBlueprintOrCoordinator() {
        var f = running(); var ledger = f.ledger(); var p = f.project(); var frozen = p.save();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(enlist(ledger, p, a)); assertTrue(enlist(ledger, p, b)); assertTrue(ledger.retireCrew(p, a));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertEquals(ledger.generation(), loaded.generation()); assertEquals(Set.of(b), loaded.crewMembers(p));
        assertFalse(loaded.crewMember(p, a)); assertTrue(loaded.knownCrewMember(p, a)); assertTrue(loaded.crewMember(p, b));
        assertFalse(enlist(loaded, p, a)); assertFalse(loaded.retireCrew(p, a));
        assertEquals(p.header().builder(), loaded.assignedBuilder(p)); assertTrue(loaded.matchesProjectLease(p));
        assertEquals(frozen, p.save()); assertEquals(p.payment(), PerimeterProjectStore.get(f.core(), p.header().projectId()).payment());
        assertEquals(0, f.core().getLong("BankEmeralds"));
    }

    @Test void absentHistoricalCrewStartsEmptyBoundToTheRestoredLedgerGeneration() {
        var f = running(); CompoundTag old = f.ledger().save(new CompoundTag()); old.remove("BuilderCrew");
        var restored = ConstructionEditLedger.load(old); UUID helper = UUID.randomUUID();
        assertTrue(restored.sameGeneration(f.ledger().generation())); assertTrue(restored.crewMembers(f.project()).isEmpty());
        assertTrue(enlist(restored, f.project(), helper));
        var saved = restored.save(new CompoundTag());
        assertEquals(f.ledger().generation(), saved.getCompound("BuilderCrew").getUUID("LedgerGeneration"));
        assertTrue(ConstructionEditLedger.load(saved).crewMember(f.project(), helper));
    }

    @Test void admissionsRejectMissingLeaseUnpaidBlockedEditedDuplicateAndBothCoordinators() {
        var prepared = ConstructionProjectLedgerTest.project(); var bare = new ConstructionEditLedger();
        UUID helper = UUID.randomUUID();
        assertTrue(bare.registerProject(prepared)); assertFalse(enlist(bare, prepared, helper));
        assertTrue(bare.leaseProjectStage(prepared)); assertFalse(enlist(bare, prepared, helper));
        var f = running(); var p = f.project(); var ledger = f.ledger();
        assertFalse(enlist(new ConstructionEditLedger(), p, helper));
        assertFalse(enlist(ledger, p, p.header().builder())); assertFalse(enlist(ledger, p, null));
        assertFalse(enlist(ledger, p, new UUID(0, 0)));
        assertTrue(enlist(ledger, p, helper)); assertFalse(enlist(ledger, p, helper));
        assertFalse(enlist(ledger, p.blockRecovery(p.check(), "Blocked fixture"), UUID.randomUUID()));
        assertFalse(enlist(ledger, p.cancel(p.check(), "Cancel fixture"), UUID.randomUUID()));
        UUID replacement = UUID.randomUUID();
        assertTrue(ledger.projectBuilderDestroyed(p, p.header().builder(), p.active().areaId(), ledger.generation()));
        assertFalse(enlist(ledger, p, UUID.randomUUID()));
        assertTrue(ledger.replaceDeadBuilder(p, replacement));
        assertFalse(enlist(ledger, p, replacement)); assertFalse(enlist(ledger, p, p.header().builder()));
        assertTrue(enlist(ledger, p, UUID.randomUUID()));
        ledger.record(BlockPos.of(p.reservation().iterator().next()));
        assertFalse(enlist(ledger, p, UUID.randomUUID()));
    }

    @Test void malformedCrewInvalidatesLedgerInsteadOfRestoringAnEmptyCrew() {
        var f = running(); UUID worker = UUID.randomUUID(); assertTrue(enlist(f.ledger(), f.project(), worker));
        for (Consumer<CompoundTag> damage : java.util.List.<Consumer<CompoundTag>>of(
                root -> root.putString("BuilderCrew", "not a crew"),
                root -> root.getCompound("BuilderCrew").putUUID("LedgerGeneration", UUID.randomUUID()),
                root -> member(root).putByte("Active", (byte) 2),
                root -> member(root).putUUID("Worker", f.project().header().builder()),
                root -> member(root).remove("AdmittedStage"),
                root -> member(root).putInt("AdmittedStage", -1),
                root -> { ListTag members = crewProject(root).getList("Members", Tag.TAG_COMPOUND); members.add(members.getCompound(0).copy()); },
                root -> crewProject(root).put("Members", new ListTag()))) {
            CompoundTag damaged = f.ledger().save(new CompoundTag()); damage.accept(damaged);
            var loaded = ConstructionEditLedger.load(damaged);
            assertFalse(loaded.sameGeneration(f.ledger().generation())); assertFalse(loaded.matchesProjectReservation(f.project()));
            assertFalse(loaded.crewMember(f.project(), worker)); assertFalse(loaded.knownCrewMember(f.project(), worker));
            assertFalse(enlist(loaded, f.project(), UUID.randomUUID())); assertTrue(loaded.reserves(Set.of(BlockPos.ZERO)));
        }
    }

    @Test void crewEnumerationNeverMistakesCorruptionForAnEmptyCrew() {
        var f = running(); UUID helper = UUID.randomUUID(); assertTrue(enlist(f.ledger(), f.project(), helper));
        assertThrows(IllegalStateException.class, () -> ConstructionEditLedger.load(new CompoundTag()).crewMembers(f.project()));
        assertThrows(IllegalStateException.class, () -> f.ledger().crewMembers(null));
        CompoundTag conflicting = f.ledger().save(new CompoundTag()); crewProject(conflicting).putString("Hash", "b".repeat(64));
        var loaded = ConstructionEditLedger.load(conflicting);
        assertThrows(IllegalArgumentException.class, () -> loaded.crewMembers(f.project()));
        assertFalse(loaded.crewMember(f.project(), helper));
        assertTrue(new ConstructionEditLedger().crewMembers(f.project()).isEmpty(), "A valid absent membership remains empty");
    }

    @Test void copiedCurrentReplacementCoordinatorCannotLoadAsAnActiveHelper() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(), replacement = UUID.randomUUID();
        assertTrue(enlist(ledger, p, helper));
        assertTrue(ledger.projectBuilderDestroyed(p, p.header().builder(), p.active().areaId(), ledger.generation()));
        assertTrue(ledger.replaceDeadBuilder(p, replacement));
        CompoundTag changed = ledger.save(new CompoundTag()); member(changed).putUUID("Worker", replacement);
        var loaded = ConstructionEditLedger.load(changed);
        assertFalse(loaded.sameGeneration(ledger.generation())); assertFalse(loaded.crewMember(p, replacement));
        assertFalse(loaded.matchesProjectReservation(p));
    }

    @Test void authenticatedHelperDeathPersistsOnlyItsOwnCleanupProofAndDoesNotPauseTheProject() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); var original = p.save();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); assertTrue(enlist(ledger, p, a)); assertTrue(enlist(ledger, p, b));
        var other = running(ledger, 10); UUID otherHelper = UUID.randomUUID();
        assertTrue(enlist(ledger, other.project(), otherHelper)); var otherFrozen = other.project().save();
        UUID area = p.active().areaId();
        assertFalse(ledger.markCrewBuilderDestroyed(p, UUID.randomUUID(), area, ledger.generation()));
        assertFalse(ledger.markCrewBuilderDestroyed(p, p.header().builder(), area, ledger.generation()));
        assertFalse(ledger.markCrewBuilderDestroyed(p, a, UUID.randomUUID(), ledger.generation()));
        assertFalse(ledger.markCrewBuilderDestroyed(p, a, area, UUID.randomUUID()));
        assertFalse(ledger.markCrewBuilderDestroyed(ConstructionProjectLedgerTest.project(), a, area, ledger.generation()));
        assertNull(ledger.crewBuilderDestructionReceipt(p, a));
        assertTrue(ledger.markCrewBuilderDestroyed(p, a, area, ledger.generation()));
        String proof = ledger.crewBuilderDestructionReceipt(p, a); assertNotNull(proof);
        assertFalse(ledger.markCrewBuilderDestroyed(p, a, area, ledger.generation()));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertEquals(proof, loaded.crewBuilderDestructionReceipt(p, a)); assertTrue(loaded.crewBuilderDestroyed(p, a));
        assertFalse(loaded.crewBuilderDestroyed(p, b)); assertTrue(loaded.knownCrewMember(p, a));
        assertFalse(loaded.crewMember(p, a)); assertTrue(loaded.crewMember(p, b)); assertEquals(Set.of(b), loaded.crewMembers(p));
        assertTrue(loaded.matchesProjectLease(p)); assertTrue(loaded.matchesProjectReservation(p));
        assertNull(loaded.projectBuilderDestructionReceipt(p)); assertFalse(loaded.replaceDeadBuilder(p, UUID.randomUUID()));
        assertEquals(p.header().builder(), loaded.assignedBuilder(p)); assertEquals(original, p.save());
        assertTrue(loaded.matchesProjectLease(other.project())); assertTrue(loaded.crewMember(other.project(), otherHelper));
        assertFalse(loaded.crewBuilderDestroyed(other.project(), otherHelper)); assertFalse(loaded.crewBuilderDestroyed(other.project(), a));
        assertEquals(otherFrozen, other.project().save());
    }

    @Test void helperDeathBetweenStagesUsesOnlyTheExactRecentlyRetiredLease() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID();
        assertTrue(enlist(ledger, p, helper)); UUID previous = p.active().areaId();
        p = p.verifyStage(p.check(), p.expectedStageReceipt()); ledger.retire(previous, true); p = p.retireVerifiedStage(p.check());
        assertEquals(PerimeterProject.State.WAITING_FOR_NEXT_STAGE, p.state());
        assertFalse(ledger.markCrewBuilderDestroyed(p, helper, p.active().areaId(), ledger.generation()));
        assertTrue(ledger.markCrewBuilderDestroyed(p, helper, previous, ledger.generation()));
        assertTrue(ledger.matchesProjectReservation(p)); assertTrue(ledger.leaseProjectStage(p)); assertTrue(ledger.matchesProjectLease(p));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(loaded.crewBuilderDestroyed(p, helper)); assertTrue(loaded.matchesProjectLease(p));
    }

    @Test void revokedHelperCannotMintADeathReceiptFromAbsenceOrLateRemoval() {
        var f = running(); UUID helper = UUID.randomUUID(); assertTrue(enlist(f.ledger(), f.project(), helper));
        assertTrue(f.ledger().retireCrew(f.project(), helper));
        assertFalse(f.ledger().markCrewBuilderDestroyed(f.project(), helper, f.project().active().areaId(), f.ledger().generation()));
        assertFalse(f.ledger().crewBuilderDestroyed(f.project(), helper));
        assertTrue(f.ledger().knownCrewMember(f.project(), helper)); assertTrue(f.ledger().matchesProjectLease(f.project()));
    }

    @Test void terminalMembershipAndDeathProofAreBoundToExactProjectOriginalAndLedgerAfterReservationRelease() {
        var f = running(); var ledger = f.ledger(); var p = f.project(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(enlist(ledger, p, a)); assertTrue(enlist(ledger, p, b));
        assertTrue(ledger.markCrewBuilderDestroyed(p, a, p.active().areaId(), ledger.generation()));
        assertTrue(ledger.retireCrew(p, b)); String death = ledger.crewBuilderDestructionReceipt(p, a);
        var terminal = cancelAndCompact(f, ledger.generation());
        ledger.retire(p.header().projectId(), true);
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertFalse(loaded.matchesProjectReservation(p));
        assertTrue(loaded.knownTerminalCrewMember(terminal, a)); assertTrue(loaded.knownTerminalCrewMember(terminal, b));
        assertEquals(death, loaded.terminalCrewBuilderDestructionReceipt(terminal, a));
        assertNull(loaded.terminalCrewBuilderDestructionReceipt(terminal, b));
        assertFalse(loaded.knownTerminalCrewMember(terminal, p.header().builder()));
        assertFalse(loaded.knownTerminalCrewMember(terminal, UUID.randomUUID()));
        assertFalse(new ConstructionEditLedger().knownTerminalCrewMember(terminal, a));
        assertFalse(loaded.knownTerminalCrewMember(cancelAndCompact(running(), ledger.generation()), a));
        assertFalse(enlist(loaded, p, UUID.randomUUID()));
        assertFalse(loaded.markCrewBuilderDestroyed(p, b, p.active().areaId(), loaded.generation()));
    }

    @Test void terminalQueriesRejectStructurallyValidCopiedCrewIdentityAndForeignLedger() {
        var f = running(); UUID worker = UUID.randomUUID(); assertTrue(enlist(f.ledger(), f.project(), worker));
        var terminal = cancelAndCompact(f, f.ledger().generation());
        for (String field : new String[]{"Project", "Generation", "Hash", "Original", "AdmittedStage"}) {
            CompoundTag damaged = f.ledger().save(new CompoundTag()); CompoundTag identity = crewProject(damaged);
            if (field.equals("Generation")) identity.putLong(field, f.project().header().generation() + 1);
            else if (field.equals("Hash")) identity.putString(field, "b".repeat(64));
            else if (field.equals("AdmittedStage")) member(damaged).putInt(field, f.project().activeStage() + 1);
            else identity.putUUID(field, UUID.randomUUID());
            var loaded = ConstructionEditLedger.load(damaged);
            assertFalse(loaded.knownTerminalCrewMember(terminal, worker), field);
        }
        var other = running(); UUID otherHelper = UUID.randomUUID(); assertTrue(enlist(other.ledger(), other.project(), otherHelper));
        var wrongLedger = cancelAndCompact(other, UUID.randomUUID());
        assertFalse(other.ledger().knownTerminalCrewMember(wrongLedger, otherHelper));
    }

    @Test void pendingAdmissionIsDurableCapacityButNeverPlacementOrCoordinatorAuthority() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID pending = UUID.randomUUID();
        assertTrue(ledger.enlistCrew(p, pending)); assertTrue(ledger.pendingCrewMember(p, pending));
        assertEquals(Set.of(pending), ledger.pendingCrewMembers(p)); assertEquals(Set.of(pending), ledger.reservedCrewMembers(p));
        assertTrue(ledger.crewMembers(p).isEmpty()); assertFalse(ledger.crewMember(p, pending));
        assertFalse(ledger.markCrewBuilderDestroyed(p, pending, p.active().areaId(), UUID.randomUUID()));
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), pending, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag())); assertTrue(loaded.pendingCrewMember(p, pending));
        assertTrue(loaded.enlistCrew(p, UUID.randomUUID())); assertTrue(loaded.enlistCrew(p, UUID.randomUUID()));
        assertFalse(loaded.enlistCrew(p, UUID.randomUUID()));
        assertFalse(loaded.activateCrewMember(p.cancel(p.check(), "Cancel fixture"), pending));
        assertTrue(loaded.activateCrewMember(p, pending)); assertTrue(loaded.activateCrewMember(p, pending));
        assertTrue(loaded.crewMember(p, pending)); assertFalse(loaded.pendingCrewMember(p, pending));
        assertEquals(3, loaded.reservedCrewMembers(p).size()); assertFalse(loaded.cancelPendingCrewMember(p, pending));
    }

    @Test void pendingDestructionAuthenticatesGenerationAndKeepsCleanupOnlyProofAcrossReload() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID pending = UUID.randomUUID();
        assertTrue(ledger.enlistCrew(p, pending)); assertEquals(p.header().projectId(), ledger.pendingCrewProject(pending));
        var before = ledger.save(new CompoundTag()); ledger.setDirty(false);
        assertFalse(ledger.markCrewBuilderDestroyed(p, UUID.randomUUID(), p.active().areaId(), ledger.generation()));
        assertFalse(ledger.markCrewBuilderDestroyed(p, pending, UUID.randomUUID(), ledger.generation()));
        assertFalse(ledger.markCrewBuilderDestroyed(p, pending, p.active().areaId(), UUID.randomUUID()));
        assertFalse(ledger.markCrewBuilderDestroyed(running().project(), pending, p.active().areaId(), ledger.generation()));
        assertEquals(before, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty());
        assertTrue(ConstructionEditLedger.load(before).pendingCrewMember(p, pending));
        ledger.record(BlockPos.of(p.reservation().iterator().next()));
        var canceled = p.cancel(p.check(), "Cancellation after a player edit"); var coreBefore = f.core().copy();
        assertTrue(ledger.markCrewBuilderDestroyed(canceled, pending, p.active().areaId(), ledger.generation()));
        String proof = ledger.crewBuilderDestructionReceipt(canceled, pending); assertNotNull(proof);
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertEquals(proof, loaded.crewBuilderDestructionReceipt(canceled, pending));
        assertFalse(member(loaded.save(new CompoundTag())).getBoolean("Ready"));
        assertNull(loaded.pendingCrewProject(pending)); assertTrue(loaded.reservedCrewMembers(canceled).isEmpty());
        assertTrue(loaded.knownCrewMember(canceled, pending)); assertTrue(loaded.edited(p.header().projectId()));
        assertTrue(loaded.matchesProjectIdentity(canceled)); assertNull(loaded.projectBuilderDestructionReceipt(canceled));
        assertEquals(p.header().builder(), loaded.assignedBuilder(canceled)); assertEquals(coreBefore, f.core());
        assertFalse(loaded.activateCrewMember(p, pending)); assertFalse(loaded.cancelPendingCrewMember(canceled, pending));
        assertFalse(loaded.transferCrewCoordinator(p, p.header().builder(), pending, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(loaded.markCrewBuilderDestroyed(canceled, pending, p.active().areaId(), loaded.generation()));
    }

    @Test void withdrawnUnlinkedIntentCannotBeReactivatedOrAcquireCleanupScope() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID pending = UUID.randomUUID();
        assertTrue(ledger.enlistCrew(p, pending));
        // Caller-side native regression separately proves the worker is loaded with no links/hand/native association.
        assertTrue(ledger.cancelPendingCrewMember(p.cancel(p.check(), "Cancel fixture"), pending));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(loaded.reservedCrewMembers(p).isEmpty()); assertTrue(loaded.knownCrewMember(p, pending));
        assertFalse(loaded.crewMemberAreaKnown(p, pending, p.active().areaId())); assertNull(loaded.crewAdmissionArea(p, pending));
        assertFalse(loaded.activateCrewMember(p, pending)); assertFalse(loaded.enlistCrew(p, pending));
        assertFalse(loaded.cancelPendingCrewMember(p, pending)); assertTrue(loaded.matchesProjectLease(p));
    }

    @Test void unavailableThenDismissedCoordinatorTransfersAreAtomicAndNeverImplyDeath() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); var frozen = p.save();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(); assertTrue(enlist(ledger, p, first)); assertTrue(enlist(ledger, p, second));
        assertTrue(ledger.transferCrewCoordinator(p, p.header().builder(), first, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertEquals(first, ledger.assignedBuilder(p)); assertFalse(ledger.crewMember(p, first)); assertTrue(ledger.knownCrewMember(p, first));
        assertTrue(ledger.crewMember(p, second)); assertTrue(ledger.knownProjectBuilder(p, p.header().builder()));
        assertNull(ledger.projectBuilderDestructionReceipt(p)); assertNull(ledger.crewBuilderDestructionReceipt(p, first));
        assertTrue(ledger.matchesProjectLease(p)); assertFalse(ledger.enlistCrew(p, p.header().builder()));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(loaded.transferCrewCoordinator(p, first, second, PerimeterBuilderAssignments.Cause.DISMISSED));
        assertEquals(second, loaded.assignedBuilder(p)); assertTrue(loaded.crewMembers(p).isEmpty());
        assertTrue(loaded.knownProjectBuilder(p, first)); assertFalse(loaded.enlistCrew(p, first));
        assertTrue(loaded.canRebindBuilder(p, first, p.active().areaId())); assertTrue(loaded.canRebindBuilder(p, p.header().builder(), p.active().areaId()));
        assertEquals(second, loaded.builderForArea(p, p.active().areaId())); assertEquals(frozen, p.save());
        assertNull(loaded.projectBuilderDestructionReceipt(p)); assertFalse(loaded.replaceDeadBuilder(p, UUID.randomUUID()));
        var terminal = cancelAndCompact(f, loaded.generation()); loaded.retire(p.header().projectId(), true);
        var terminalLedger = ConstructionEditLedger.load(loaded.save(new CompoundTag()));
        for (UUID id : new UUID[]{p.header().builder(), first, second}) {
            assertTrue(terminalLedger.knownTerminalBuilder(terminal, id));
            assertTrue(terminalLedger.terminalBuilderAreaKnown(terminal, id, p.active().areaId()));
            assertEquals(p.active().areaId(), terminalLedger.builderAdmissionArea(terminal, id));
        }
        assertEquals(second, terminalLedger.assignedTerminalBuilder(terminal));
    }

    @Test void transferRejectsUnprovenDeathStaleRoleWrongHelperAndEditedReservationWithoutPartialMutation() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        var before = ledger.save(new CompoundTag());
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.DEATH));
        assertFalse(ledger.transferCrewCoordinator(p, UUID.randomUUID(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), UUID.randomUUID(), PerimeterBuilderAssignments.Cause.DISMISSED));
        assertFalse(ledger.transferCrewCoordinator(p.cancel(p.check(), "Cancel fixture"), p.header().builder(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertEquals(before, ledger.save(new CompoundTag())); assertTrue(ledger.crewMember(p, helper));
        ledger.record(BlockPos.of(p.reservation().iterator().next()));
        var edited = ledger.save(new CompoundTag());
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertEquals(edited, ledger.save(new CompoundTag()));
    }

    @Test void authenticatedCoordinatorDeathCanPromoteReadyHelperThroughExistingReplacementPath() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        assertTrue(ledger.projectBuilderDestroyed(p, p.header().builder(), p.active().areaId(), ledger.generation()));
        assertTrue(ledger.replaceDeadBuilder(p, helper)); assertEquals(helper, ledger.assignedBuilder(p));
        assertFalse(ledger.crewMember(p, helper)); assertTrue(ledger.knownCrewMember(p, helper)); assertTrue(ledger.matchesProjectLease(p));
        var saved = ledger.save(new CompoundTag());
        assertEquals("DEATH", saved.getCompound("BuilderAssignments").getList("Projects", Tag.TAG_COMPOUND).getCompound(0)
                .getList("History", Tag.TAG_COMPOUND).getCompound(0).getString("Cause"));
        assertEquals(helper, ConstructionEditLedger.load(saved).assignedBuilder(p));
    }

    @Test void transferAtRecentlyRetiredStagePreservesNextLeaseAndHelperRetirementScope() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        UUID previous = p.active().areaId(); p = p.verifyStage(p.check(), p.expectedStageReceipt()); ledger.retire(previous, true); p = p.retireVerifiedStage(p.check());
        assertTrue(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertTrue(ledger.matchesProjectReservation(p)); assertTrue(ledger.leaseProjectStage(p)); assertTrue(ledger.matchesProjectLease(p));
        assertEquals(previous, ledger.builderAdmissionArea(p, helper)); assertTrue(ledger.projectBuilderAreaKnown(p, p.header().builder(), previous));
        assertFalse(ledger.projectBuilderAreaKnown(p, p.header().builder(), p.active().areaId()));
        assertTrue(ledger.projectBuilderAreaKnown(p, helper, p.active().areaId()));
    }

    @Test void loadedNondeathTransferRejectsChangedLedgerGenerationAndCause() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        assertTrue(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        var changedCause = ledger.save(new CompoundTag());
        changedCause.getCompound("BuilderAssignments").getList("Projects", Tag.TAG_COMPOUND).getCompound(0).getList("History", Tag.TAG_COMPOUND)
                .getCompound(0).putString("Cause", "DISMISSED");
        assertFalse(ConstructionEditLedger.load(changedCause).sameGeneration(ledger.generation()));
        var changedGeneration = ledger.save(new CompoundTag()); UUID otherGeneration = UUID.randomUUID();
        changedGeneration.putUUID("Generation", otherGeneration); changedGeneration.getCompound("BuilderCrew").putUUID("LedgerGeneration", otherGeneration);
        assertFalse(ConstructionEditLedger.load(changedGeneration).sameGeneration(otherGeneration));
    }

    @Test void verifiedDoneBeforeControllerRetirementAllowsUnavailableOrDeadCoordinatorHandoffWithoutReopeningLease() {
        for (var cause : new PerimeterBuilderAssignments.Cause[]{PerimeterBuilderAssignments.Cause.UNAVAILABLE, PerimeterBuilderAssignments.Cause.DEATH}) {
            var f = running(); var running = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID();
            assertTrue(enlist(ledger, running, helper)); UUID area = running.active().areaId(); UUID original = running.header().builder();
            var verified = running.verifyStage(running.check(), running.expectedStageReceipt());
            // Model the durable DONE boundary: verified receipt saved, native lease retired, controller index not advanced.
            // Actual marker absence/returned-marker geometry and native journal are validated by separate controller tests.
            ledger.retire(area, true);
            if (cause == PerimeterBuilderAssignments.Cause.DEATH)
                assertTrue(ledger.projectBuilderDestroyed(verified, original, area, ledger.generation()));
            assertTrue(ledger.verifiedRetiredProjectStage(verified, area)); assertFalse(ledger.matchesProjectLease(verified));
            CompoundTag before = ledger.save(new CompoundTag()); ledger.setDirty(false);
            assertTrue(ledger.canTransferCrewCoordinator(verified, original, helper, cause));
            assertEquals(before, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty()); assertTrue(ledger.crewMember(verified, helper));
            assertTrue(ledger.transferCrewCoordinator(verified, original, helper, cause)); assertTrue(ledger.isDirty());
            assertEquals(helper, ledger.assignedBuilder(verified)); assertFalse(ledger.crewMember(verified, helper));
            assertTrue(ledger.retired(area)); assertFalse(ledger.contains(area)); assertFalse(ledger.completeReservation(area));
            assertFalse(ledger.matchesProjectLease(verified)); assertFalse(ledger.leaseProjectStage(verified));
            assertTrue(ledger.matchesProjectReservation(verified)); assertTrue(ledger.verifiedRetiredProjectStage(verified, area));
            assertNull(ledger.projectBuilderDestructionReceipt(verified));
            // A returned old completed marker may prove its own cleanup identity; a foreign marker/builder may not.
            assertTrue(ledger.projectBuilderAreaKnown(verified, original, area)); assertTrue(ledger.projectBuilderAreaKnown(verified, helper, area));
            assertFalse(ledger.projectBuilderAreaKnown(verified, UUID.randomUUID(), area));
            assertFalse(ledger.projectBuilderAreaKnown(verified, original, UUID.randomUUID()));
            var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            assertTrue(loaded.verifiedRetiredProjectStage(verified, area)); assertTrue(loaded.retired(area)); assertFalse(loaded.matchesProjectLease(verified));
            var waiting = verified.retireVerifiedStage(verified.check()); assertTrue(loaded.leaseProjectStage(waiting));
            assertTrue(loaded.matchesProjectLease(waiting)); assertEquals(helper, loaded.assignedBuilder(waiting));
            assertFalse(loaded.projectBuilderAreaKnown(waiting, original, waiting.active().areaId()));
            assertFalse(loaded.projectBuilderDestroyed(waiting, original, waiting.active().areaId(), loaded.generation()));
        }
    }

    @Test void retiredRunningLeaseWithoutExactVerifiedReceiptCannotAuthorizeCompletedStageTransfer() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        UUID area = p.active().areaId(); ledger.retire(area, true);
        assertFalse(ledger.verifiedRetiredProjectStage(p, area)); assertTrue(p.receipts().isEmpty());
        CompoundTag before = ledger.save(new CompoundTag()); ledger.setDirty(false);
        assertFalse(ledger.canTransferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertEquals(before, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty()); assertTrue(ledger.crewMember(p, helper));
        assertTrue(ledger.projectBuilderDestroyed(p, p.header().builder(), area, ledger.generation()));
        assertFalse(ledger.canTransferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.DEATH));
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.DEATH));
        assertEquals(p.header().builder(), ledger.assignedBuilder(p)); assertTrue(ledger.retired(area));
        var verified = p.verifyStage(p.check(), p.expectedStageReceipt());
        assertFalse(ledger.verifiedRetiredProjectStage(verified, UUID.randomUUID()));
        ledger.record(BlockPos.of(p.reservation().iterator().next()));
        assertFalse(ledger.verifiedRetiredProjectStage(verified, area));
        assertFalse(ledger.canTransferCrewCoordinator(verified, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.DEATH));
    }

    @Test void transferPreflightChecksHistoryCapacityWithoutRetiringOrPublishingTheHelper() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        for (int i = 0; i < PerimeterBuilderAssignments.MAX_REPLACEMENTS; i++) {
            UUID coordinator = ledger.assignedBuilder(p);
            assertTrue(ledger.projectBuilderDestroyed(p, coordinator, p.active().areaId(), ledger.generation()));
            assertTrue(ledger.replaceDeadBuilder(p, UUID.randomUUID()));
        }
        UUID current = ledger.assignedBuilder(p); var before = ledger.save(new CompoundTag()); ledger.setDirty(false);
        assertFalse(ledger.canTransferCrewCoordinator(p, current, helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(ledger.transferCrewCoordinator(p, current, helper, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertEquals(before, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty()); assertTrue(ledger.crewMember(p, helper));
    }

    @Test void transferCommitRechecksMembershipAfterReadOnlyPreflight() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID helper = UUID.randomUUID(); assertTrue(enlist(ledger, p, helper));
        var before = ledger.save(new CompoundTag()); ledger.setDirty(false);
        assertTrue(ledger.canTransferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.DISMISSED));
        assertEquals(before, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty());
        assertTrue(ledger.retireCrew(p, helper)); var revoked = ledger.save(new CompoundTag()); ledger.setDirty(false);
        assertFalse(ledger.transferCrewCoordinator(p, p.header().builder(), helper, PerimeterBuilderAssignments.Cause.DISMISSED));
        assertEquals(revoked, ledger.save(new CompoundTag())); assertFalse(ledger.isDirty()); assertEquals(p.header().builder(), ledger.assignedBuilder(p));
    }

    @Test void canceledVerifiedRetiredStageKeepsHistoricalCleanupProofButCannotTransferOrReopenLease() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); UUID promoted = UUID.randomUUID(), spare = UUID.randomUUID();
        assertTrue(enlist(ledger, p, promoted)); assertTrue(enlist(ledger, p, spare)); UUID area = p.active().areaId();
        var verified = p.verifyStage(p.check(), p.expectedStageReceipt()); ledger.retire(area, true);
        assertTrue(ledger.transferCrewCoordinator(verified, p.header().builder(), promoted, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        var canceled = verified.cancel(verified.check(), "Owner canceled before controller retirement");
        assertTrue(ledger.verifiedRetiredProjectStage(canceled, area));
        assertTrue(ledger.projectBuilderAreaKnown(canceled, p.header().builder(), area));
        assertTrue(ledger.projectBuilderAreaKnown(canceled, promoted, area));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(loaded.verifiedRetiredProjectStage(canceled, area)); assertTrue(loaded.crewMember(canceled, spare));
        var before = loaded.save(new CompoundTag()); loaded.setDirty(false);
        assertFalse(loaded.canTransferCrewCoordinator(canceled, promoted, spare, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(loaded.transferCrewCoordinator(canceled, promoted, spare, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(loaded.canTransferCrewCoordinator(canceled, promoted, spare, PerimeterBuilderAssignments.Cause.DISMISSED));
        assertFalse(loaded.transferCrewCoordinator(canceled, promoted, spare, PerimeterBuilderAssignments.Cause.DISMISSED));
        assertFalse(loaded.leaseProjectStage(canceled)); assertFalse(loaded.matchesProjectLease(canceled));
        assertTrue(loaded.retired(area)); assertEquals(promoted, loaded.assignedBuilder(canceled));
        assertEquals(before, loaded.save(new CompoundTag())); assertFalse(loaded.isDirty());
        assertFalse(loaded.verifiedRetiredProjectStage(p.cancel(p.check(), "Unverified cancellation"), area));
    }

    @Test void canceledEditedHistoricalMarkerProofIsReadOnlyAndNeverRestoresWorkAuthority() {
        var f = running(); var p = f.project(); var ledger = f.ledger();
        UUID promoted = UUID.randomUUID(), spare = UUID.randomUUID(), area = p.active().areaId();
        assertTrue(enlist(ledger, p, promoted)); assertTrue(enlist(ledger, p, spare));
        var verified = p.verifyStage(p.check(), p.expectedStageReceipt()); ledger.retire(area, true);
        assertTrue(ledger.transferCrewCoordinator(verified, p.header().builder(), promoted, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        BlockPos playerEdit = BlockPos.of(p.active().layout().targets().keySet().iterator().next());
        ledger.record(playerEdit);
        assertTrue(ledger.edited(p.header().projectId()));
        assertFalse(ledger.verifiedRetiredProjectStage(verified, area));
        assertFalse(ledger.canceledHistoricalMarkerCleanup(verified, area));
        var canceled = verified.cancel(verified.check(), "Owner canceled after a later player edit");
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        var before = loaded.save(new CompoundTag()); loaded.setDirty(false);
        assertTrue(loaded.canceledHistoricalMarkerCleanup(canceled, area));
        assertTrue(loaded.projectBuilderAreaKnown(canceled, p.header().builder(), area));
        assertFalse(loaded.canceledHistoricalMarkerCleanup(canceled, UUID.randomUUID()));
        assertFalse(loaded.canceledHistoricalMarkerCleanup(p.cancel(p.check(), "No completion proof"), area));
        assertFalse(loaded.canceledHistoricalMarkerCleanup(running().project(), area));
        assertFalse(loaded.canTransferCrewCoordinator(canceled, promoted, spare, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(loaded.transferCrewCoordinator(canceled, promoted, spare, PerimeterBuilderAssignments.Cause.UNAVAILABLE));
        assertFalse(loaded.leaseProjectStage(canceled)); assertFalse(loaded.matchesProjectLease(canceled));
        assertTrue(loaded.edited(p.header().projectId())); assertTrue(loaded.retired(area));
        assertEquals(before, loaded.save(new CompoundTag())); assertFalse(loaded.isDirty());
    }

    private boolean enlist(ConstructionEditLedger ledger, PerimeterProject project, UUID worker) {
        boolean accepted = ledger.enlistCrew(project, worker);
        if (accepted) assertTrue(ledger.activateCrewMember(project, worker));
        return accepted;
    }

    private PerimeterTerminalReceipt cancelAndCompact(Fixture f, UUID ledgerGeneration) {
        var p = f.project(); var canceled = p.cancel(p.check(), "Cancel fixture");
        PerimeterProjectStore.replace(f.core(), p.check(), canceled, () -> {});
        // Synthetic model proof only: these tests cover codec/authentication, not native cleanup.
        var proof = new PerimeterTerminalReceipt.CleanupProof(p.header().projectId(), p.header().generation(), p.manifestHash(), canceled.revision(),
                canceled.state(), p.header().owner(), p.header().builder(), ledgerGeneration,
                p.stages().stream().map(PerimeterProject.Stage::areaId).toList(), "1".repeat(64), "2".repeat(64), "3".repeat(64));
        return PerimeterProjectStore.compact(f.core(), canceled.check(), proof, () -> {});
    }

    private static CompoundTag crewProject(CompoundTag root) {
        return root.getCompound("BuilderCrew").getList("Projects", Tag.TAG_COMPOUND).getCompound(0);
    }
    private static CompoundTag member(CompoundTag root) {
        return crewProject(root).getList("Members", Tag.TAG_COMPOUND).getCompound(0);
    }
}
