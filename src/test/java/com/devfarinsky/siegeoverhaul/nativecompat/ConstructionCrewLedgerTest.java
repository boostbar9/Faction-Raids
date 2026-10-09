package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
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
        assertTrue(ledger.enlistCrew(p, a)); assertTrue(ledger.enlistCrew(p, b)); assertTrue(ledger.retireCrew(p, a));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertEquals(ledger.generation(), loaded.generation()); assertEquals(Set.of(b), loaded.crewMembers(p));
        assertFalse(loaded.crewMember(p, a)); assertTrue(loaded.knownCrewMember(p, a)); assertTrue(loaded.crewMember(p, b));
        assertFalse(loaded.enlistCrew(p, a)); assertFalse(loaded.retireCrew(p, a));
        assertEquals(p.header().builder(), loaded.assignedBuilder(p)); assertTrue(loaded.matchesProjectLease(p));
        assertEquals(frozen, p.save()); assertEquals(p.payment(), PerimeterProjectStore.get(f.core(), p.header().projectId()).payment());
        assertEquals(0, f.core().getLong("BankEmeralds"));
    }

    @Test void absentHistoricalCrewStartsEmptyBoundToTheRestoredLedgerGeneration() {
        var f = running(); CompoundTag old = f.ledger().save(new CompoundTag()); old.remove("BuilderCrew");
        var restored = ConstructionEditLedger.load(old); UUID helper = UUID.randomUUID();
        assertTrue(restored.sameGeneration(f.ledger().generation())); assertTrue(restored.crewMembers(f.project()).isEmpty());
        assertTrue(restored.enlistCrew(f.project(), helper));
        var saved = restored.save(new CompoundTag());
        assertEquals(f.ledger().generation(), saved.getCompound("BuilderCrew").getUUID("LedgerGeneration"));
        assertTrue(ConstructionEditLedger.load(saved).crewMember(f.project(), helper));
    }

    @Test void admissionsRejectMissingLeaseUnpaidBlockedEditedDuplicateAndBothCoordinators() {
        var prepared = ConstructionProjectLedgerTest.project(); var bare = new ConstructionEditLedger();
        UUID helper = UUID.randomUUID();
        assertTrue(bare.registerProject(prepared)); assertFalse(bare.enlistCrew(prepared, helper));
        assertTrue(bare.leaseProjectStage(prepared)); assertFalse(bare.enlistCrew(prepared, helper));
        var f = running(); var p = f.project(); var ledger = f.ledger();
        assertFalse(new ConstructionEditLedger().enlistCrew(p, helper));
        assertFalse(ledger.enlistCrew(p, p.header().builder())); assertFalse(ledger.enlistCrew(p, null));
        assertFalse(ledger.enlistCrew(p, new UUID(0, 0)));
        assertTrue(ledger.enlistCrew(p, helper)); assertFalse(ledger.enlistCrew(p, helper));
        assertFalse(ledger.enlistCrew(p.blockRecovery(p.check(), "Blocked fixture"), UUID.randomUUID()));
        assertFalse(ledger.enlistCrew(p.cancel(p.check(), "Cancel fixture"), UUID.randomUUID()));
        UUID replacement = UUID.randomUUID();
        assertTrue(ledger.projectBuilderDestroyed(p, p.header().builder(), p.active().areaId(), ledger.generation()));
        assertFalse(ledger.enlistCrew(p, UUID.randomUUID()));
        assertTrue(ledger.replaceDeadBuilder(p, replacement));
        assertFalse(ledger.enlistCrew(p, replacement)); assertFalse(ledger.enlistCrew(p, p.header().builder()));
        assertTrue(ledger.enlistCrew(p, UUID.randomUUID()));
        ledger.record(BlockPos.of(p.reservation().iterator().next()));
        assertFalse(ledger.enlistCrew(p, UUID.randomUUID()));
    }

    @Test void malformedCrewInvalidatesLedgerInsteadOfRestoringAnEmptyCrew() {
        var f = running(); UUID worker = UUID.randomUUID(); assertTrue(f.ledger().enlistCrew(f.project(), worker));
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
            assertFalse(loaded.enlistCrew(f.project(), UUID.randomUUID())); assertTrue(loaded.reserves(Set.of(BlockPos.ZERO)));
        }
    }

    @Test void crewEnumerationNeverMistakesCorruptionForAnEmptyCrew() {
        var f = running(); UUID helper = UUID.randomUUID(); assertTrue(f.ledger().enlistCrew(f.project(), helper));
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
        assertTrue(ledger.enlistCrew(p, helper));
        assertTrue(ledger.projectBuilderDestroyed(p, p.header().builder(), p.active().areaId(), ledger.generation()));
        assertTrue(ledger.replaceDeadBuilder(p, replacement));
        CompoundTag changed = ledger.save(new CompoundTag()); member(changed).putUUID("Worker", replacement);
        var loaded = ConstructionEditLedger.load(changed);
        assertFalse(loaded.sameGeneration(ledger.generation())); assertFalse(loaded.crewMember(p, replacement));
        assertFalse(loaded.matchesProjectReservation(p));
    }

    @Test void authenticatedHelperDeathPersistsOnlyItsOwnCleanupProofAndDoesNotPauseTheProject() {
        var f = running(); var p = f.project(); var ledger = f.ledger(); var original = p.save();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); assertTrue(ledger.enlistCrew(p, a)); assertTrue(ledger.enlistCrew(p, b));
        var other = running(ledger, 10); UUID otherHelper = UUID.randomUUID();
        assertTrue(ledger.enlistCrew(other.project(), otherHelper)); var otherFrozen = other.project().save();
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
        assertTrue(ledger.enlistCrew(p, helper)); UUID previous = p.active().areaId();
        p = p.verifyStage(p.check(), p.expectedStageReceipt()); ledger.retire(previous, true); p = p.retireVerifiedStage(p.check());
        assertEquals(PerimeterProject.State.WAITING_FOR_NEXT_STAGE, p.state());
        assertFalse(ledger.markCrewBuilderDestroyed(p, helper, p.active().areaId(), ledger.generation()));
        assertTrue(ledger.markCrewBuilderDestroyed(p, helper, previous, ledger.generation()));
        assertTrue(ledger.matchesProjectReservation(p)); assertTrue(ledger.leaseProjectStage(p)); assertTrue(ledger.matchesProjectLease(p));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(loaded.crewBuilderDestroyed(p, helper)); assertTrue(loaded.matchesProjectLease(p));
    }

    @Test void revokedHelperCannotMintADeathReceiptFromAbsenceOrLateRemoval() {
        var f = running(); UUID helper = UUID.randomUUID(); assertTrue(f.ledger().enlistCrew(f.project(), helper));
        assertTrue(f.ledger().retireCrew(f.project(), helper));
        assertFalse(f.ledger().markCrewBuilderDestroyed(f.project(), helper, f.project().active().areaId(), f.ledger().generation()));
        assertFalse(f.ledger().crewBuilderDestroyed(f.project(), helper));
        assertTrue(f.ledger().knownCrewMember(f.project(), helper)); assertTrue(f.ledger().matchesProjectLease(f.project()));
    }

    @Test void terminalMembershipAndDeathProofAreBoundToExactProjectOriginalAndLedgerAfterReservationRelease() {
        var f = running(); var ledger = f.ledger(); var p = f.project(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(ledger.enlistCrew(p, a)); assertTrue(ledger.enlistCrew(p, b));
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
        assertFalse(loaded.enlistCrew(p, UUID.randomUUID()));
        assertFalse(loaded.markCrewBuilderDestroyed(p, b, p.active().areaId(), loaded.generation()));
    }

    @Test void terminalQueriesRejectStructurallyValidCopiedCrewIdentityAndForeignLedger() {
        var f = running(); UUID worker = UUID.randomUUID(); assertTrue(f.ledger().enlistCrew(f.project(), worker));
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
        var other = running(); UUID otherHelper = UUID.randomUUID(); assertTrue(other.ledger().enlistCrew(other.project(), otherHelper));
        var wrongLedger = cancelAndCompact(other, UUID.randomUUID());
        assertFalse(other.ledger().knownTerminalCrewMember(wrongLedger, otherHelper));
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
