package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageLayout;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt;
import com.devfarinsky.siegeoverhaul.core.PlayerFortificationJobs;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.devfarinsky.siegeoverhaul.core.WallBuilderAccess;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real crew controller, resume/reconciliation, ledger, project store, snapshot and hand-provenance
 * paths around mocked world/entity/native installation, assignment and storage-cleanup boundaries.
 * Stage receipts and the small partition are synthetic fixtures, not evidence of actual placement.
 * Reload copies persistent NBT/ledger records while retaining mock actors and inventory objects;
 * this is neither native save-order testing nor Minecraft restart/gameplay acceptance.
 */
class PerimeterSuspendedCrewReplayTest extends MinecraftTestSupport {
    private static final String AREA = "SiegeProtectedAreaReceipt", GENERATION = "SiegeProtectedLedgerGeneration",
            SNAPSHOT = "SiegeProtectedConstructionV1";

    private enum RejoinPrefix {
        SUSPENDED, OLD_DETACHED, INTENT, WHOLE_SELECTOR, LEDGER_BOUND,
        CHILD_RECEIPT, HAND_RESTORED, NATIVE_METADATA, NATIVE_ASSIGNMENT, ACTIVE;
        boolean includes(RejoinPrefix other) { return ordinal() >= other.ordinal(); }
    }

    @Test void rawV2PendingAdmissionReplaysEveryWorkerWritePrefixAfterMigrationRoundTrips() throws Exception {
        for (int prefix = 0; prefix <= 6; prefix++) for (boolean resaveBeforeResume : new boolean[]{false, true})
            try (var f = new Fixture()) {
                var pending = f.new Actor(UUID.randomUUID()); assertTrue(f.ledger.enlistCrew(f.project, pending.id));
                if (prefix >= 1) PerimeterProjectLink.set(pending.mob, f.project, pending.id);
                if (prefix >= 2) assertTrue(PerimeterProjectLink.bindLedgerGeneration(pending.mob, f.ledger.generation()));
                if (prefix >= 3) {
                    pending.data.putUUID(AREA, f.area().getUUID()); pending.data.putUUID(GENERATION, f.ledger.generation());
                    ProtectedBuilderHandMirror.arm(pending.data);
                }
                if (prefix >= 4) {
                    assertNull(ProtectedBuilderHandMirror.restore(pending.mob));
                    assertTrue(f.ledger.retainHandLifecycle(pending.data, pending.id, f.project.header().owner(), f.area().getUUID()));
                }
                if (prefix >= 5) PlayerFortificationJobs.linkWorker(pending.mob, f.area(), f.project.header().owner());
                if (prefix >= 6) pending.mob.currentBuildArea = f.area();
                var saved = f.ledger.save(new CompoundTag()); var crew = saved.getCompound("BuilderCrew"); crew.putInt("Version", 2);
                for (var projectTag : crew.getList("Projects", net.minecraft.nbt.Tag.TAG_COMPOUND))
                    for (var raw : ((CompoundTag) projectTag).getList("Members", net.minecraft.nbt.Tag.TAG_COMPOUND))
                        for (String key : new String[]{"Suspended", "RejoinStage", "GrantedStages", "LegacyEnd", "Resolved"})
                            ((CompoundTag) raw).remove(key);
                f.ledger = ConstructionEditLedger.load(saved); if (resaveBeforeResume) f.reload();
                assertTrue(f.ledger.pendingCrewMember(f.project, pending.id)); assertFalse(f.ledger.crewStageGranted(f.project, pending.id));
                assertFalse(f.ledger.activateCrewMember(f.project, pending.id));
                // Migration does not replace strict fresh commissioning with continuation permissions.
                pending.requests.add(pending.request);
                assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, pending.mob, f.area(), f.project));
                assertFalse(f.ledger.crewStageGranted(f.project, pending.id)); pending.requests.remove(pending.request);
                assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, pending.mob, f.area(), f.project),
                        "v2 prefix " + prefix + ", resaved=" + resaveBeforeResume);
                assertTrue(f.ledger.crewStageGranted(f.project, pending.id)); assertFalse(f.ledger.pendingCrewMember(f.project, pending.id));
                assertFalse(f.ledger.rejoiningCrewMember(f.project, pending.id));
                assertEquals(Set.of(f.area().getUUID()), f.ledger.workerCleanupAreas(f.project, pending.id));
                var accepted = f.ledger.save(new CompoundTag()); var actor = pending.data.copy(); int writes = pending.assignments;
                f.reload(); pending.data = pending.data.copy();
                assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, pending.mob, f.area(), f.project));
                assertEquals(accepted, f.ledger.save(new CompoundTag())); assertEquals(actor, pending.data);
                assertEquals(writes, pending.assignments); assertEquals(1, pending.handBindings); pending.assertCargo(); f.assertConserved();
            }
    }

    @Test void sameStageReturnRestoresTheSameMemberAndExactActiveReplayDoesNotDrainResupplyAgain() throws Exception {
        try (var f = new Fixture()) {
            var paid = f.core.copy(); var worker = f.helper.data.copy();
            var hand = f.ledger.handLifecycle(f.helper.id); int assignments = f.helper.assignments;
            f.suspendByController(); f.reload();
            assertEquals(worker, f.helper.data); assertEquals(paid, f.core);
            assertEquals(Set.of(f.helper.id), f.ledger.suspendedCrewMembers(f.project));
            f.returned(); f.advance(); f.assertActive();
            assertEquals(hand, f.ledger.handLifecycle(f.helper.id)); assertEquals(assignments, f.helper.assignments);
            assertEquals(Set.of(f.project.active().areaId()), f.ledger.workerCleanupAreas(f.project, f.helper.id));
            var accepted = f.ledger.save(new CompoundTag()); var linked = f.helper.data.copy();
            int drains = f.helper.drains; f.reload(); f.advance(); f.advance();
            assertEquals(accepted, f.ledger.save(new CompoundTag())); assertEquals(linked, f.helper.data);
            assertEquals(drains, f.helper.drains); assertEquals(assignments, f.helper.assignments);
            assertEquals(paid, f.core); f.assertConserved();
        }
    }

    @Test void everyLaterStageRejoinPrefixReplaysWithoutGrantingSkippedStagesOrReplacingCargo() throws Exception {
        for (RejoinPrefix prefix : RejoinPrefix.values()) try (var f = new Fixture()) {
            UUID first = f.project.active().areaId(); var hand = f.ledger.handLifecycle(f.helper.id);
            f.suspendByController(); f.advanceTo(3); f.returned();
            f.rejoinThrough(prefix); f.reload();
            assertEquals(hand, f.ledger.handLifecycle(f.helper.id));
            if (prefix != RejoinPrefix.ACTIVE) {
                assertFalse(f.ledger.crewStageGranted(f.project, f.helper.id), prefix.name());
                assertFalse(f.ledger.crewMember(f.project, f.helper.id), prefix.name());
            }
            var paid = f.core.copy(); var marker = f.markerData.get(f.area().getUUID()).copy();
            f.advance(); f.assertActive();
            assertEquals(Set.of(first, f.project.active().areaId()), f.ledger.workerCleanupAreas(f.project, f.helper.id), prefix.name());
            assertFalse(f.ledger.crewMemberAreaKnown(f.project, f.helper.id, f.project.stages().get(1).areaId()));
            assertFalse(f.ledger.crewMemberAreaKnown(f.project, f.helper.id, f.project.stages().get(2).areaId()));
            assertEquals(hand, f.ledger.handLifecycle(f.helper.id));
            assertEquals(paid, f.core); assertEquals(marker, f.markerData.get(f.area().getUUID())); f.assertConserved();
            var accepted = f.ledger.save(new CompoundTag()); var worker = f.helper.data.copy();
            int assignments = f.helper.assignments, drains = f.helper.drains; f.reload(); f.advance();
            assertEquals(accepted, f.ledger.save(new CompoundTag())); assertEquals(worker, f.helper.data);
            assertEquals(assignments, f.helper.assignments); assertEquals(drains, f.helper.drains); f.assertConserved();
        }
    }

    @Test void historicalActiveParticipationDoesNotGrantWorkOnANewStageBeforeTheExactIntent() throws Exception {
        try (var f = new Fixture()) {
            UUID first = f.project.active().areaId(); f.advanceTo(1);
            assertTrue(f.ledger.crewMember(f.project, f.helper.id));
            assertFalse(f.ledger.crewStageGranted(f.project, f.helper.id));
            assertEquals(Set.of(first), f.ledger.workerCleanupAreas(f.project, f.helper.id));
            assertNull(NativeConstructionGuard.continuationProblem(f.helper.mob, f.project));
            var detached = f.helper.data.copy();
            f.helper.data.putUUID(AREA, f.area().getUUID()); f.helper.data.putUUID(GENERATION, f.ledger.generation());
            f.helper.mob.currentBuildArea = f.area();
            PlayerFortificationJobs.linkWorker(f.helper.mob, f.area(), f.project.header().owner());
            assertFalse(NativeConstructionGuard.authorizedParticipant(f.helper.mob, f.area(), f.project.header().builder()),
                    "Current-looking worker receipts cannot invent a stage grant");
            f.helper.data = detached; f.helper.mob.currentBuildArea = null; f.reload(); f.advance(); f.assertActive();
            assertEquals(Set.of(first, f.project.active().areaId()), f.ledger.workerCleanupAreas(f.project, f.helper.id));
            f.assertConserved();
        }
    }

    @Test void permanentlyRetiredHelperCannotUseAStaleActorSaveToRejoin() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.ledger.retireCrew(f.project, f.helper.id)); var before = f.ledger.save(new CompoundTag());
            var actor = f.helper.data.copy(); f.reload();
            assertFalse(f.ledger.beginCrewStage(f.project, f.helper.id));
            assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, f.helper.mob, f.area(), f.project));
            f.advance(); assertEquals(before, f.ledger.save(new CompoundTag()));
            assertFalse(f.ledger.suspendedCrewMember(f.project, f.helper.id)); assertFalse(f.ledger.crewMember(f.project, f.helper.id));
            assertEquals(actor, f.helper.data); f.assertConserved();
        }
    }

    @Test void failedRejoinAssignmentKeepsItsExactIntentUntilCleanupBeforeALaterStage() throws Exception {
        try (var f = new Fixture()) {
            UUID first = f.project.active().areaId(); f.suspendByController(); f.advanceTo(3); f.returned();
            f.helper.assignmentReady = false; f.advance();
            assertTrue(f.ledger.rejoiningCrewMember(f.project, f.helper.id));
            assertEquals(3, f.ledger.crewRejoinStage(f.project, f.helper.id));
            assertFalse(f.ledger.crewStageGranted(f.project, f.helper.id));
            UUID attempted = f.project.active().areaId();
            assertEquals(attempted, NativeConstructionGuard.protectedReceiptArea(f.helper.mob));
            assertNull(NativeConstructionGuard.currentArea(f.helper.mob));
            f.absent(); f.advanceTo(4); f.reload();
            assertTrue(f.ledger.suspendedCrewMember(f.project, f.helper.id));
            assertEquals(Set.of(first, attempted), f.ledger.workerCleanupAreas(f.project, f.helper.id));
            assertEquals(attempted, NativeConstructionGuard.protectedReceiptArea(f.helper.mob));
            f.helper.assignmentReady = true; f.returned(); f.advance(); f.assertActive();
            assertEquals(Set.of(first, attempted, f.project.active().areaId()), f.ledger.workerCleanupAreas(f.project, f.helper.id));
            f.assertConserved();
        }
    }

    @Test void unfinishedOldStageAndTerminalCleanupKeepTheirEvidenceUntilTheOriginalCleanupCanFinish() throws Exception {
        try (var f = new Fixture()) {
            UUID first = f.project.active().areaId(); f.suspendByController(); f.advanceTo(3); f.returned();
            f.helper.cleanupReady = false;
            var ledgerBefore = f.ledger.save(new CompoundTag()); int assignments = f.helper.assignments;
            f.advance();
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(assignments, f.helper.assignments);
            assertEquals(first, NativeConstructionGuard.protectedReceiptArea(f.helper.mob));
            assertEquals(Set.of(first), f.ledger.workerCleanupAreas(f.project, f.helper.id));
            assertTrue(f.ledger.suspendedCrewMember(f.project, f.helper.id));
            f.helper.cleanupReady = true; f.reload(); f.advance(); f.assertActive(); f.assertConserved();
            f.cancelAbsentAndCompact(); f.returned(); f.helper.cleanupReady = false;
            ledgerBefore = f.ledger.save(new CompoundTag()); int detachments = f.helper.detachments;
            assertFalse(NativePerimeterProjects.reconcileWorker(f.helper.mob));
            assertEquals(ledgerBefore, f.ledger.save(new CompoundTag())); assertEquals(detachments, f.helper.detachments);
            assertEquals(f.project.header().projectId(), f.ledger.unresolvedCrewProject(f.helper.id));
            assertTrue(PerimeterProjectLink.reserved(f.helper.mob)); f.assertConserved();
            f.helper.cleanupReady = true; f.reload();
            assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob)); f.assertResolved(); f.assertConserved();
        }
    }

    @Test void returningHelperWaitsForOneOfThreeLiveSlotsWithoutDisplacingAnotherWorker() throws Exception {
        try (var f = new Fixture()) {
            f.suspendByController(); var replacements = new ArrayList<Fixture.Actor>();
            for (int slot = 0; slot < 3; slot++) {
                var replacement = f.new Actor(UUID.randomUUID()); replacements.add(replacement);
                assertTrue(f.ledger.enlistCrew(f.project, replacement.id));
                assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, replacement.mob, f.area(), f.project));
            }
            var before = f.ledger.save(new CompoundTag()); var worker = f.helper.data.copy();
            f.returned(); f.advance();
            assertEquals(before, f.ledger.save(new CompoundTag())); assertEquals(worker, f.helper.data);
            assertTrue(f.ledger.suspendedCrewMember(f.project, f.helper.id)); assertEquals(3, f.ledger.crewMembers(f.project).size());
            for (var replacement : replacements) assertTrue(f.ledger.crewMember(f.project, replacement.id));
            f.entities.remove(replacements.get(2).id); f.advance(); f.assertActive();
            assertTrue(f.ledger.suspendedCrewMember(f.project, replacements.get(2).id));
            assertEquals(3, f.ledger.crewMembers(f.project).size());
            assertTrue(f.ledger.crewMember(f.project, replacements.get(0).id));
            assertTrue(f.ledger.crewMember(f.project, replacements.get(1).id)); f.assertConserved();
        }
    }

    @Test void suspendedReturnAtCapacityRetiresExplicitDismissalButPreservesUnreadableOwnershipOrCommand() throws Exception {
        for (String change : new String[]{"OWNER_TRANSFER", "FOLLOW", "OWNER_UNKNOWN", "COMMAND_UNREADABLE"})
            try (var f = new Fixture()) {
                f.suspendByController();
                for (int slot = 0; slot < 3; slot++) {
                    var replacement = f.new Actor(UUID.randomUUID()); assertTrue(f.ledger.enlistCrew(f.project, replacement.id));
                    assertTrue(NativeConstructionGuard.resumeCrewMember(f.owner, replacement.mob, f.area(), f.project));
                }
                f.returned(); f.advance(); assertTrue(f.ledger.suspendedCrewMember(f.project, f.helper.id));
                switch (change) {
                    case "OWNER_TRANSFER" -> f.helper.ownerId = UUID.randomUUID();
                    case "FOLLOW" -> when(f.helper.mob.getFollowState()).thenReturn(1);
                    case "OWNER_UNKNOWN" -> f.helper.ownerId = null;
                    case "COMMAND_UNREADABLE" -> when(f.helper.mob.getFollowState()).thenThrow(new IllegalStateException("Unavailable command"));
                }
                var before = f.ledger.save(new CompoundTag()); f.advance();
                boolean explicit = change.equals("OWNER_TRANSFER") || change.equals("FOLLOW");
                assertEquals(!explicit, f.ledger.suspendedCrewMember(f.project, f.helper.id), change);
                assertFalse(f.ledger.crewStageGranted(f.project, f.helper.id)); assertEquals(3, f.ledger.crewMembers(f.project).size());
                assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helper.id));
                assertEquals(Set.of(f.area().getUUID()), f.ledger.workerCleanupAreas(f.project, f.helper.id));
                if (explicit) {
                    assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob));
                    assertNull(f.ledger.unresolvedCrewProject(f.helper.id)); assertFalse(PerimeterProjectLink.reserved(f.helper.mob));
                    assertTrue(NativePerimeterProjects.cleanupDetached(f.helper.mob));
                    assertTrue(f.ledger.knownCrewMember(f.project, f.helper.id)); assertFalse(f.ledger.beginCrewStage(f.project, f.helper.id));
                } else {
                    assertEquals(before, f.ledger.save(new CompoundTag()));
                    assertTrue(PerimeterProjectLink.reserved(f.helper.mob)); assertEquals(f.project.header().projectId(), f.ledger.unresolvedCrewProject(f.helper.id));
                }
                f.assertConserved();
            }
    }

    @Test void rejoinRejectsWrongOwnerLedgerGenerationCopiedActorAndConflictingAssignment() throws Exception {
        List<Consumer<Fixture>> conflicts = List.of(
                f -> f.helper.ownerId = UUID.randomUUID(),
                f -> when(f.owner.getUUID()).thenReturn(UUID.randomUUID()),
                f -> f.helper.data.getCompound(PerimeterProjectLink.KEY).putUUID("LedgerGeneration", UUID.randomUUID()),
                f -> f.helper.data.putUUID(GENERATION, UUID.randomUUID()),
                f -> f.helper.data.getCompound(ProtectedBuilderHandLifecycle.KEY).putUUID("Generation", UUID.randomUUID()),
                f -> when(f.helper.mob.getUUID()).thenReturn(UUID.randomUUID()),
                f -> f.helper.data.getCompound(PerimeterProjectLink.KEY).putString("Hash", "b".repeat(64)),
                f -> f.foreignAssignment());
        for (var conflict : conflicts) try (var f = new Fixture()) {
            f.suspendByController(); f.returned(); assertTrue(f.ledger.beginCrewStage(f.project, f.helper.id));
            conflict.accept(f); f.reload();
            var before = f.ledger.save(new CompoundTag()); var worker = f.helper.data.copy();
            int assignments = f.helper.assignments, detachments = f.helper.detachments;
            assertFalse(NativeConstructionGuard.resumeCrewMember(f.owner, f.helper.mob, f.area(), f.project));
            assertEquals(before, f.ledger.save(new CompoundTag()));
            var actual = f.helper.data.copy(); actual.remove("SiegeConstructionPause"); worker.remove("SiegeConstructionPause");
            assertEquals(worker, actual);
            assertFalse(f.ledger.crewStageGranted(f.project, f.helper.id));
            assertEquals(assignments, f.helper.assignments); assertEquals(detachments, f.helper.detachments); f.assertConserved();
        }
    }

    @Test void validHandProofNeverAuthorizesCleanupOfStagesSkippedWhileTheHelperWasAbsent() throws Exception {
        for (boolean compacted : new boolean[]{false, true}) for (int skipped : new int[]{1, 2}) try (var f = new Fixture()) {
            f.suspendByController(); f.advanceTo(3); f.returned(); f.advance(); f.assertActive();
            if (compacted) { f.cancelAbsentAndCompact(); f.returned(); }
            UUID ungranted = f.project.stages().get(skipped).areaId();
            f.helper.data.putUUID(AREA, ungranted); f.helper.data.putUUID(GENERATION, f.ledger.generation());
            PlayerFortificationJobs.linkWorker(f.helper.mob, f.markers.get(ungranted), f.project.header().owner());
            f.helper.mob.currentBuildArea = f.markers.get(ungranted); f.reload();
            assertTrue(ProtectedBuilderHandLifecycle.matches(f.helper.data, f.helper.id, f.ledger));
            var before = f.ledger.save(new CompoundTag()); var core = f.core.copy();
            int detachments = f.helper.detachments, drains = f.helper.drains;
            NativePerimeterProjects.reconcileWorker(f.helper.mob);
            assertEquals(before, f.ledger.save(new CompoundTag())); assertEquals(core, f.core);
            assertEquals(drains, f.helper.drains); assertEquals(detachments, f.helper.detachments);
            assertTrue(PerimeterProjectLink.reserved(f.helper.mob));
            assertEquals(ungranted, NativeConstructionGuard.protectedReceiptArea(f.helper.mob));
            assertSame(f.markers.get(ungranted), NativeConstructionGuard.currentArea(f.helper.mob));
            assertFalse(compacted ? f.ledger.workerCleanupAreas(f.terminal, f.helper.id).contains(ungranted)
                    : f.ledger.workerCleanupAreas(f.project, f.helper.id).contains(ungranted)); f.assertConserved();
        }
    }

    @Test void canceledAbsentHelperCompactsThenReturnsOnlyToResolveItsOriginalAssociation() throws Exception {
        try (var f = new Fixture()) {
            var hand = f.ledger.handLifecycle(f.helper.id); f.cancelAbsentAndCompact(); f.reload();
            assertEquals(f.original.payment(), f.terminal.payment());
            assertFalse(f.ledger.contains(f.project.header().projectId()));
            assertEquals(f.project.header().projectId(), f.ledger.unresolvedCrewProject(f.helper.id));
            f.returned(); assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob)); f.assertResolved();
            assertEquals(hand, f.ledger.handLifecycle(f.helper.id)); f.assertConserved();
            var before = f.ledger.save(new CompoundTag()); var core = f.core.copy(); int drains = f.helper.drains;
            f.reload(); assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob));
            assertEquals(before, f.ledger.save(new CompoundTag())); assertEquals(core, f.core); assertEquals(drains, f.helper.drains);
            assertFalse(f.ledger.beginCrewStage(f.project, f.helper.id)); assertFalse(f.ledger.enlistCrew(f.project, f.helper.id));
            f.assertConserved();
        }
    }

    @Test void terminalSplitSavesRecoverUnresolvedLedgerWithClearedSelectorAndResolvedLedgerWithOldActor() throws Exception {
        for (boolean ledgerIsResolved : new boolean[]{false, true}) try (var f = new Fixture()) {
            f.cancelAbsentAndCompact(); f.returned(); f.reload();
            var unresolved = f.ledger.save(new CompoundTag()); var oldActor = f.helper.data.copy();
            var oldArea = f.helper.mob.currentBuildArea; var hand = f.ledger.handLifecycle(f.helper.id);
            assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob)); f.assertResolved();
            var resolved = f.ledger.save(new CompoundTag()); var clearedActor = f.helper.data.copy();
            f.ledger = ConstructionEditLedger.load(ledgerIsResolved ? resolved : unresolved);
            f.helper.data = ledgerIsResolved ? oldActor : clearedActor;
            f.helper.mob.currentBuildArea = ledgerIsResolved ? oldArea : null; f.reload();
            assertEquals(ledgerIsResolved ? null : f.project.header().projectId(), f.ledger.unresolvedCrewProject(f.helper.id));
            assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob)); f.assertResolved();
            assertEquals(hand, f.ledger.handLifecycle(f.helper.id)); f.assertConserved();
            var accepted = f.ledger.save(new CompoundTag()); var worker = f.helper.data.copy();
            int drains = f.helper.drains, detachments = f.helper.detachments; f.reload();
            assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob));
            assertEquals(accepted, f.ledger.save(new CompoundTag())); assertEquals(worker, f.helper.data);
            assertEquals(drains, f.helper.drains); assertEquals(detachments, f.helper.detachments); f.assertConserved();
        }
    }

    @Test void historicalDestructionEventRequiresAnExactGrantedChildAndNeverConfusesUnloadWithDeath() throws Exception {
        for (boolean rejoining : new boolean[]{false, true})
            for (String eventCase : new String[]{"DEATH", "UNLOAD", "WRONG_GENERATION", "SKIPPED_STAGE", "CONFLICTING_SELECTOR"})
                try (var f = new Fixture()) {
                    UUID originalChild = f.area().getUUID(); f.suspendByController(); f.advanceTo(3); f.returned();
                    if (rejoining) assertTrue(f.ledger.beginCrewStage(f.project, f.helper.id));
                    switch (eventCase) {
                        case "WRONG_GENERATION" -> f.helper.data.putUUID(GENERATION, UUID.randomUUID());
                        case "SKIPPED_STAGE" -> {
                            UUID skipped = f.project.stages().get(1).areaId(); f.helper.data.putUUID(AREA, skipped);
                            f.helper.mob.currentBuildArea = f.markers.get(skipped);
                            PlayerFortificationJobs.linkWorker(f.helper.mob, f.markers.get(skipped), f.project.header().owner());
                        }
                        case "CONFLICTING_SELECTOR" -> f.helper.data.getCompound(PerimeterProjectLink.KEY).putString("Hash", "b".repeat(64));
                    }
                    var before = f.ledger.save(new CompoundTag()); var worker = f.helper.data.copy(); int detachments = f.helper.detachments;
                    when(f.helper.mob.getRemovalReason()).thenReturn(eventCase.equals("UNLOAD")
                            ? Entity.RemovalReason.UNLOADED_TO_CHUNK : Entity.RemovalReason.KILLED);
                    NativeConstructionGuard.areaRemoved(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(f.helper.mob, f.level));
                    if (eventCase.equals("DEATH")) {
                        assertNotNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helper.id));
                        assertFalse(f.ledger.suspendedCrewMember(f.project, f.helper.id)); assertFalse(f.ledger.rejoiningCrewMember(f.project, f.helper.id));
                        assertFalse(f.ledger.beginCrewStage(f.project, f.helper.id));
                        assertTrue(f.ledger.crewMemberAreaKnown(f.project, f.helper.id, originalChild));
                        var proof = f.ledger.crewBuilderDestructionReceipt(f.project, f.helper.id); f.reload();
                        assertEquals(proof, f.ledger.crewBuilderDestructionReceipt(f.project, f.helper.id));
                    } else {
                        assertEquals(before, f.ledger.save(new CompoundTag()), eventCase);
                        assertNull(f.ledger.crewBuilderDestructionReceipt(f.project, f.helper.id));
                    }
                    assertEquals(worker, f.helper.data); assertEquals(detachments, f.helper.detachments);
                    assertEquals(f.project.header().projectId(), f.ledger.unresolvedCrewProject(f.helper.id));
                    assertNull(f.ledger.projectBuilderDestructionReceipt(f.project)); assertTrue(f.ledger.matchesProjectLease(f.project));
                    f.assertConserved();
                }
    }

    @Test void ordinaryItemUseKeepsLivingTicksWhileHistoricalCleanupAndWorkRemainBlocked() throws Exception {
        for (String state : new String[]{"OLD_STAGE", "RETIRED", "CANCELED", "COMPACTED"}) try (var f = new Fixture();
                var requestBoundary = mockStatic(ProtectedTransferCapacity.class, CALLS_REAL_METHODS)) {
            // This fixture preserves the same dormant request object, but its test lambda is not
            // a native BuilderWorkGoal matcher. Isolate only that unrelated provenance boundary
            // so this event actually exercises real reconciliation and LivingTick cancellation.
            requestBoundary.when(() -> ProtectedTransferCapacity.requestsProblem(f.helper.mob)).thenReturn(null);
            switch (state) {
                case "OLD_STAGE" -> { f.suspendByController(); f.advanceTo(3); f.returned(); }
                case "RETIRED" -> assertTrue(f.ledger.retireCrew(f.project, f.helper.id));
                case "CANCELED" -> f.replace(f.project.cancel(f.project.check(), "Canceled during item use"));
                case "COMPACTED" -> { f.cancelAbsentAndCompact(); f.returned(); }
            }
            when(f.helper.mob.isUsingItem()).thenReturn(true);
            int detached = f.helper.detachments; var before = f.ledger.save(new CompoundTag());
            assertFalse(NativePerimeterProjects.reconcileWorker(f.helper.mob), "Cleanup completion still waits: " + state);
            var event = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(f.helper.mob);
            com.devfarinsky.siegeoverhaul.RaidEvents.onCampWorkerTick(event);
            assertFalse(event.isCanceled(), "Ordinary use must be able to finish: " + state);
            assertEquals(before, f.ledger.save(new CompoundTag())); assertEquals(detached, f.helper.detachments);
            assertTrue(PerimeterProjectLink.reserved(f.helper.mob));
            assertNotNull(NativeConstructionGuard.protectedReceiptArea(f.helper.mob));
            assertNotNull(NativeConstructionGuard.storageProblem(f.helper.mob, Set.of()));
            assertFalse(NativeConstructionGuard.beforeNativeTick(f.helper.mob, f.fixtureMock(net.minecraft.world.entity.ai.goal.Goal.class)));
            when(f.helper.mob.isUsingItem()).thenReturn(false);
            assertTrue(NativeConstructionGuard.beforeWorkerTick(f.helper.mob));
            assertTrue(NativePerimeterProjects.cleanupDetached(f.helper.mob));
            if (state.equals("OLD_STAGE")) {
                assertTrue(PerimeterProjectLink.reserved(f.helper.mob)); assertFalse(f.ledger.crewStageGranted(f.project, f.helper.id));
                f.advance(); f.assertActive();
            } else {
                assertFalse(PerimeterProjectLink.reserved(f.helper.mob)); assertNull(f.ledger.unresolvedCrewProject(f.helper.id));
            }
            f.assertConserved();
        }
    }

    @Test void terminalSplitSavesDoNotClearAConflictingNewNativeAssignment() throws Exception {
        for (boolean ledgerIsResolved : new boolean[]{false, true}) try (var f = new Fixture()) {
            f.cancelAbsentAndCompact(); f.returned(); var unresolved = f.ledger.save(new CompoundTag()); var oldActor = f.helper.data.copy();
            assertTrue(NativePerimeterProjects.reconcileWorker(f.helper.mob)); f.assertResolved();
            if (ledgerIsResolved) f.helper.data = oldActor;
            else f.ledger = ConstructionEditLedger.load(unresolved);
            Entity foreign = f.foreignAssignment(); f.reload();
            var before = f.ledger.save(new CompoundTag()); var core = f.core.copy(); int detachments = f.helper.detachments;
            NativePerimeterProjects.reconcileWorker(f.helper.mob);
            assertEquals(before, f.ledger.save(new CompoundTag())); assertEquals(core, f.core);
            assertSame(foreign, NativeConstructionGuard.currentArea(f.helper.mob)); assertEquals(detachments, f.helper.detachments);
            assertEquals(foreign.getUUID(), f.helper.data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)); f.assertConserved();
        }
    }

    private static PerimeterProject preparedProject() {
        Set<ChunkPos> territory = Set.of(new ChunkPos(0, 0));
        var plan = PerimeterBlueprint.create(territory, (x, z) -> PerimeterBlueprint.Surface.ready(65),
                PerimeterBlueprint.Palette.COBBLESTONE);
        var layout = PerimeterStageLayout.partition(plan, stage -> stage.targets().size() <= 100 ? null : "Synthetic continuity stage cap");
        var header = PerimeterProject.Header.newCommission(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                "team:rejoin", new BlockPos(8, 65, 8), "rejoin", 1, "a".repeat(64), territory);
        Map<Long, BlockState> before = new HashMap<>(), clearance = new HashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        plan.clearance().forEach(cell -> clearance.put(cell, Blocks.AIR.defaultBlockState()));
        return PerimeterProject.prepare(header, plan, layout, before, clearance);
    }

    private static final class Fixture implements AutoCloseable {
        final List<Object> ownedMocks = new ArrayList<>();
        final MinecraftServer server = fixtureMock(MinecraftServer.class);
        final ServerLevel level = fixtureMock(ServerLevel.class);
        final ServerChunkCache chunks = fixtureMock(ServerChunkCache.class);
        final ServerPlayer owner = fixtureMock(ServerPlayer.class);
        final RaidSavedData saved = new RaidSavedData();
        final Map<UUID, Entity> entities = new HashMap<>();
        final Map<UUID, ProtectedBuildArea> markers = new HashMap<>();
        final Map<UUID, CompoundTag> markerData = new HashMap<>();
        CompoundTag core = new CompoundTag();
        PerimeterProject project = preparedProject();
        final PerimeterProject original;
        ConstructionEditLedger ledger = new ConstructionEditLedger();
        Actor coordinator, helper;
        PerimeterTerminalReceipt terminal;
        final MockedStatic<RaidSavedData> saves = mockStatic(RaidSavedData.class);
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class,
                call -> call.getMethod().getName().equals("get") ? ledger : CALLS_REAL_METHODS.answer(call));
        final MockedStatic<WorkersBridge> workers = mockStatic(WorkersBridge.class);
        final MockedStatic<ProtectedStorageAccess> storage = mockStatic(ProtectedStorageAccess.class);
        final MockedStatic<WallBuilderAccess> hooks = mockStatic(WallBuilderAccess.class);
        final MockedStatic<WorkersConstructionRuntime> runtime = mockStatic(WorkersConstructionRuntime.class);

        Fixture() throws ReflectiveOperationException {
            try {
                assertTrue(project.stages().size() > 4, "Fixture needs independent stages 0 through 4");
                core.putLong("BankEmeralds", 1000); saved.siegeCores.put(project.header().coreKey(), core);
                PerimeterProjectStore.prepare(core, project, () -> {}); PerimeterStageJournal.prepare(core, project, () -> {});
                assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
                assertNotNull(PerimeterStageJournal.begin(core, project, project.header().originalCore(), () -> {}));
                PerimeterStageJournal.live(core, project, () -> {});
                project = PerimeterProjectStore.consumeOnce(core, project.header().projectId(), project.manifestHash(), 64, false, () -> {}).project();
                replace(project.activate(project.check())); original = project;
                when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
                when(server.isSameThread()).thenReturn(true); when(server.getTickCount()).thenReturn(20);
                when(server.getAllLevels()).thenReturn(List.of(level));
                when(level.hasChunkAt(any())).thenReturn(true); when(level.areEntitiesLoaded(anyLong())).thenReturn(true);
                when(level.getChunkSource()).thenReturn(chunks); when(chunks.isPositionTicking(anyLong())).thenReturn(true);
                when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
                when(level.getEntity(any(UUID.class))).thenAnswer(call -> entities.get(call.getArgument(0)));
                when(level.getEntitiesOfClass(eq(Mob.class), any(), any())).thenReturn(List.of());
                when(owner.getUUID()).thenReturn(project.header().owner()); when(owner.serverLevel()).thenReturn(level);
                when(owner.isAlive()).thenReturn(true); when(owner.mayBuild()).thenReturn(true);
                saves.when(() -> RaidSavedData.get(server)).thenReturn(saved);
                workers.when(WorkersBridge::available).thenReturn(true);
                runtime.when(WorkersConstructionRuntime::problem).thenReturn(null);
                coordinator = new Actor(project.header().builder()); helper = new Actor(UUID.randomUUID());
                createMarker(); attachCoordinator();
                assertTrue(ledger.enlistCrew(project, helper.id));
                assertTrue(NativeConstructionGuard.resumeCrewMember(owner, helper.mob, area(), project));
                helper.requests.add(helper.request); // Only established continuation may preserve this dormant native request.
                assertActive();
            } catch (ReflectiveOperationException | RuntimeException | AssertionError failure) {
                close(); throw failure;
            }
        }

        final class Actor {
            final UUID id;
            final BuilderEntity mob = fixtureMock(BuilderEntity.class);
            final SimpleContainer inventory = new SimpleContainer(12);
            CompoundTag data = new CompoundTag();
            UUID ownerId = project.header().owner();
            final NeededItem request = new NeededItem(stack -> stack.is(Items.COBBLESTONE), 17, true);
            final List<NeededItem> requests = new ArrayList<>();
            final List<CompoundTag> cargo;
            final List<ItemStack> cargoObjects;
            ItemStack hand;
            boolean cleanupReady = true, assignmentReady = true;
            int assignments, detachments, drains, handBindings;
            Actor(UUID id) throws ReflectiveOperationException {
                this.id = id;
                when(mob.getUUID()).thenReturn(id); when(mob.level()).thenReturn(level); when(mob.isAlive()).thenReturn(true);
                when(mob.getPersistentData()).thenAnswer(call -> data); when(mob.getUseItem()).thenReturn(ItemStack.EMPTY);
                when(mob.getInventory()).thenReturn(inventory); when(mob.getMainHandItem()).thenAnswer(call -> hand);
                var selector = fixtureMock(GoalSelector.class); when(selector.getAvailableGoals()).thenReturn(Set.of());
                var field = Mob.class.getDeclaredField("goalSelector"); field.setAccessible(true); field.set(mob, selector);
                mob.neededItems = requests;
                inventory.setItem(5, new ItemStack(Items.COBBLESTONE, 23));
                inventory.getItem(5).getOrCreateTag().putString("Origin", "suspended-helper-fixture");
                inventory.setItem(6, new ItemStack(Items.BREAD, 7));
                hand = inventory.getItem(5).copy(); cargo = cargoSnapshot(); cargoObjects = new ArrayList<>();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) cargoObjects.add(inventory.getItem(slot));
                doAnswer(call -> { assertEquals(InteractionHand.MAIN_HAND, call.getArgument(0));
                    hand = call.getArgument(1); handBindings++; return null;
                }).when(mob).setItemInHand(any(), any());
                workers.when(() -> WorkersBridge.isBuilder(mob)).thenReturn(true);
                workers.when(() -> WorkersBridge.readWorkerOwner(mob)).thenAnswer(call -> ownerId);
                workers.when(() -> WorkersBridge.assignBuildAreaDirectly(eq(mob), any())).thenAnswer(call -> {
                    assignments++; if (assignmentReady) mob.currentBuildArea = call.getArgument(1); return assignmentReady;
                });
                workers.when(() -> WorkersBridge.detachBuildAreaReference(eq(mob), any())).thenAnswer(call -> {
                    UUID expected = call.getArgument(1); Entity current = mob.currentBuildArea;
                    if (current != null && expected.equals(current.getUUID())) mob.currentBuildArea = null;
                    detachments++; return true;
                });
                hooks.when(() -> WallBuilderAccess.install(mob)).thenReturn(true);
                storage.when(() -> ProtectedStorageAccess.install(mob)).thenReturn(true);
                storage.when(() -> ProtectedStorageAccess.runningProblem(mob)).thenCallRealMethod();
                storage.when(() -> ProtectedStorageAccess.drainCleanup(mob)).thenAnswer(call -> { drains++; return cleanupReady; });
                entities.put(id, mob);
            }
            List<CompoundTag> cargoSnapshot() {
                var result = new ArrayList<CompoundTag>();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) result.add(inventory.getItem(slot).save(new CompoundTag()));
                return result;
            }
            void assertCargo() {
                assertEquals(cargo, cargoSnapshot()); assertEquals(cargo.get(5), hand.save(new CompoundTag()));
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) assertSame(cargoObjects.get(slot), inventory.getItem(slot));
                verify(mob, never()).setItemSlot(any(), any()); verify(mob, never()).stopUsingItem(); verify(mob, never()).remove(any());
            }
        }

        ProtectedBuildArea area() { return markers.get(project.active().areaId()); }
        void createMarker() {
            var stage = project.active(); var layout = stage.layout(); var id = stage.areaId();
            var area = fixtureMock(ProtectedBuildArea.class); markers.put(id, area);
            var data = new CompoundTag(); markerData.put(id, data);
            var recipe = TerritoryFortification.blueprint(layout.targets(), layout.min(), layout.max());
            var plan = AcceptedConstructionPlan.decode(layout.origin(), Direction.SOUTH, layout.width(), layout.depth(), layout.height(), recipe);
            var snapshot = plan.save(); snapshot.putUUID("Owner", project.header().owner()); snapshot.putUUID("Builder", project.header().builder());
            snapshot.putString("CoreKey", project.header().coreKey()); snapshot.putLong("CorePos", project.header().originalCore().asLong());
            var before = new ListTag(); layout.targets().keySet().forEach(pos -> {
                var cell = new CompoundTag(); cell.putLong("Pos", pos);
                cell.put("State", NbtUtils.writeBlockState(project.before().get(pos))); before.add(cell);
            });
            snapshot.put("Before", before); snapshot.putLongArray("Completed", new long[0]); snapshot.putLongArray("Cleared", new long[0]);
            snapshot.put("Reservation", AcceptedConstructionReservation.capture(level, plan,
                    layout.reservation().stream().map(BlockPos::of).toList()).save());
            data.put(SNAPSHOT, snapshot); data.putBoolean("SiegeConstructionCommissionPaid", true);
            when(area.getUUID()).thenReturn(id); when(area.level()).thenReturn(level); when(area.isAlive()).thenReturn(true);
            when(area.getPersistentData()).thenAnswer(call -> markerData.get(id)); when(area.blockPosition()).thenReturn(project.header().originalCore());
            when(area.reservedBuilderId()).thenReturn(project.header().builder()); when(area.getOriginPos()).thenReturn(layout.origin());
            when(area.getFacing()).thenReturn(Direction.SOUTH); when(area.getWidthSize()).thenReturn(layout.width());
            when(area.getDepthSize()).thenReturn(layout.depth()); when(area.getHeightSize()).thenReturn(layout.height());
            when(area.getStructureNBT()).thenAnswer(call -> recipe.copy());
            workers.when(() -> WorkersBridge.readOwner(area)).thenReturn(project.header().owner());
            doAnswer(call -> { entities.remove(id); return null; }).when(area).removeAuthorized();
            PerimeterProjectAuthority.stamp(area, project); entities.put(id, area);
            assertTrue(NativeConstructionGuard.matchesProjectSnapshot(area, project));
        }
        void attachCoordinator() {
            var area = area(); PerimeterProjectLink.set(coordinator.mob, project);
            assertTrue(PerimeterProjectLink.bindLedgerGeneration(coordinator.mob, ledger.generation()));
            coordinator.data.putUUID(AREA, area.getUUID()); coordinator.data.putUUID(GENERATION, ledger.generation());
            ProtectedBuilderHandMirror.arm(coordinator.data); assertNull(ProtectedBuilderHandMirror.restore(coordinator.mob));
            assertTrue(ledger.retainHandLifecycle(coordinator.data, coordinator.id, project.header().owner(), area.getUUID()));
            PlayerFortificationJobs.link(coordinator.mob, area, project.header().owner()); coordinator.mob.currentBuildArea = area;
        }
        void replace(PerimeterProject next) { project = PerimeterProjectStore.replace(core, project.check(), next, () -> {}); }
        void advance() { NativePerimeterCrew.advance(level, owner, project, area()); }
        void absent() { entities.remove(helper.id); }
        void returned() { entities.put(helper.id, helper.mob); }
        void suspendByController() {
            absent(); advance(); assertTrue(ledger.suspendedCrewMember(project, helper.id));
            assertFalse(ledger.crewMember(project, helper.id)); assertFalse(ledger.crewStageGranted(project, helper.id));
            assertNull(ledger.crewBuilderDestructionReceipt(project, helper.id));
        }
        void advanceTo(int index) {
            while (project.activeStage() < index) {
                assertTrue(ledger.sealCrewHistory(project));
                UUID old = project.active().areaId();
                replace(project.verifyStage(project.check(), project.expectedStageReceipt())); // Synthetic completion proof only.
                assertTrue(NativePerimeterCrew.retireStage(level, project, old));
                assertTrue(NativeConstructionGuard.retireBuilderAssociation(coordinator.mob, old));
                ledger.retire(old, true); entities.remove(old); PerimeterStageJournal.retired(core, project, () -> {});
                replace(project.retireVerifiedStage(project.check()));
                assertTrue(ledger.leaseProjectStage(project));
                assertNotNull(PerimeterStageJournal.begin(core, project, project.header().originalCore(), () -> {}));
                PerimeterStageJournal.live(core, project, () -> {}); replace(project.activate(project.check()));
                createMarker(); attachCoordinator();
            }
        }
        void rejoinThrough(RejoinPrefix prefix) {
            if (prefix.includes(RejoinPrefix.OLD_DETACHED)) {
                assertTrue(NativePerimeterProjects.reconcileWorker(helper.mob));
                assertNull(NativeConstructionGuard.protectedReceiptArea(helper.mob));
                assertNull(NativeConstructionGuard.currentArea(helper.mob));
            }
            if (prefix.includes(RejoinPrefix.INTENT)) assertTrue(ledger.beginCrewStage(project, helper.id));
            // The same whole-project selector/generation is retained through rejoin; these writes
            // are intentionally idempotent checkpoints, not a fabricated loss of proven identity.
            if (prefix.includes(RejoinPrefix.WHOLE_SELECTOR)) PerimeterProjectLink.set(helper.mob, project, helper.id);
            if (prefix.includes(RejoinPrefix.LEDGER_BOUND)) assertTrue(PerimeterProjectLink.bindLedgerGeneration(helper.mob, ledger.generation()));
            if (prefix.includes(RejoinPrefix.CHILD_RECEIPT)) {
                helper.data.putUUID(AREA, area().getUUID()); helper.data.putUUID(GENERATION, ledger.generation());
                ProtectedBuilderHandMirror.arm(helper.data);
            }
            if (prefix.includes(RejoinPrefix.HAND_RESTORED)) {
                assertNull(ProtectedBuilderHandMirror.restore(helper.mob));
                assertTrue(ledger.retainHandLifecycle(helper.data, helper.id, project.header().owner(), area().getUUID()));
            }
            if (prefix.includes(RejoinPrefix.NATIVE_METADATA)) PlayerFortificationJobs.linkWorker(helper.mob, area(), project.header().owner());
            if (prefix.includes(RejoinPrefix.NATIVE_ASSIGNMENT)) helper.mob.currentBuildArea = area();
            if (prefix == RejoinPrefix.ACTIVE) assertTrue(ledger.activateCrewMember(project, helper.id));
        }
        Entity foreignAssignment() {
            var foreign = fixtureMock(ProtectedBuildArea.class); UUID id = UUID.randomUUID();
            when(foreign.getUUID()).thenReturn(id); when(foreign.level()).thenReturn(level);
            when(foreign.getPersistentData()).thenReturn(new CompoundTag()); when(foreign.blockPosition()).thenReturn(BlockPos.ZERO);
            helper.mob.currentBuildArea = foreign;
            PlayerFortificationJobs.linkWorker(helper.mob, foreign, project.header().owner()); return foreign;
        }
        void assertResolved() {
            assertNull(ledger.unresolvedCrewProject(helper.id)); assertFalse(PerimeterProjectLink.reserved(helper.mob));
            assertNull(NativeConstructionGuard.protectedReceiptArea(helper.mob)); assertNull(NativeConstructionGuard.currentArea(helper.mob));
            assertFalse(helper.data.contains(GENERATION)); assertFalse(helper.data.contains(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            assertTrue(ledger.knownTerminalCrewMember(terminal, helper.id));
            assertTrue(ProtectedBuilderHandLifecycle.matches(helper.data, helper.id, ledger));
        }
        void cancelAbsentAndCompact() {
            absent(); replace(project.cancel(project.check(), "Owner canceled while helper was absent"));
            NativePerimeterProjects.tick(server);
            terminal = PerimeterProjectStore.terminal(core, project.header().projectId());
            assertNotNull(terminal, "An absent previously admitted helper must not pin terminal compaction");
            assertEquals(PerimeterProject.State.CANCELED, terminal.state());
            assertNull(PerimeterProjectStore.get(core, project.header().projectId()));
            assertEquals(project.header().projectId(), ledger.unresolvedCrewProject(helper.id));
        }
        void reload() {
            core = core.copy(); saved.siegeCores.put(project.header().coreKey(), core);
            var loaded = PerimeterProjectStore.get(core, project.header().projectId()); if (loaded != null) project = loaded;
            terminal = PerimeterProjectStore.terminal(core, project.header().projectId());
            ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            coordinator.data = coordinator.data.copy(); helper.data = helper.data.copy();
            markerData.replaceAll((id, tag) -> tag.copy());
        }
        void assertActive() {
            assertTrue(ledger.crewMember(project, helper.id)); assertTrue(ledger.crewStageGranted(project, helper.id));
            assertFalse(ledger.suspendedCrewMember(project, helper.id)); assertFalse(ledger.rejoiningCrewMember(project, helper.id));
            assertEquals(project.active().areaId(), NativeConstructionGuard.protectedReceiptArea(helper.mob));
            assertSame(area(), NativeConstructionGuard.currentArea(helper.mob));
            assertTrue(PerimeterProjectLink.matches(helper.mob, project, helper.id));
            assertEquals(ledger.generation(), PerimeterProjectLink.ledgerGeneration(helper.data));
            assertTrue(ProtectedBuilderHandLifecycle.matches(helper.data, helper.id, ledger));
            assertSame(helper.inventory.getItem(5), helper.hand);
        }
        void assertConserved() {
            assertEquals(936, core.getLong("BankEmeralds"));
            assertEquals(original.header(), project.header()); assertEquals(original.manifestHash(), project.manifestHash());
            assertEquals(original.payment(), project.payment()); assertEquals(original.targets(), project.targets());
            assertEquals(original.before(), project.before()); assertEquals(original.clearanceBefore(), project.clearanceBefore());
            assertEquals(original.reservation(), project.reservation());
            assertSame(helper.requests, helper.mob.neededItems); assertEquals(1, helper.requests.size());
            assertSame(helper.request, helper.requests.get(0)); assertEquals(17, helper.request.count); assertTrue(helper.request.required);
            assertNull(helper.request.sourceKey); helper.assertCargo(); coordinator.assertCargo();
            assertEquals(1, helper.handBindings); assertEquals(project.header().builder(), ledger.assignedBuilder(project));
            verify(level, never()).setBlock(any(), any(), anyInt()); verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
            verify(level, never()).destroyBlock(any(), anyBoolean());
        }
        private <T> T fixtureMock(Class<T> type) { T value = mock(type); ownedMocks.add(value); return value; }
        @Override public void close() {
            try { NativeConstructionGuard.serverStopped(server); }
            finally {
                try { runtime.close(); hooks.close(); storage.close(); workers.close(); ledgers.close(); saves.close(); }
                finally { ownedMocks.forEach(mock -> framework().clearInlineMock(mock)); ownedMocks.clear(); }
            }
        }
    }
}
