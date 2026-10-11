package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerritory;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.core.PerimeterBuilderCrew;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.UUID;

/** Bounded same-stage helpers. No payment, inventory transfer, terrain writes, or marker creation. */
final class NativePerimeterCrew {
    private NativePerimeterCrew() {}

    /** Called by the authenticated controller after the coordinator and exact paid stage are ready. */
    static void advance(ServerLevel level, ServerPlayer owner, PerimeterProject project, ProtectedBuildArea area) {
        if (owner == null || project.state() != PerimeterProject.State.RUNNING
                || project.payment() == null || project.active() == null) return;
        var ledger = ConstructionEditLedger.get(level);
        if (!ledger.matchesProjectLease(project) || !NativeConstructionGuard.matchesProjectSnapshot(area, project)) return;
        for (UUID id : ledger.reservedCrewMembers(project)) {
            Entity found = level.getEntity(id);
            if (!(found instanceof Mob helper)) {
                // Revoke work authority, retaining cleanup-only history. This is not a death receipt.
                if (!ledger.pendingCrewMember(project, id)) ledger.retireCrew(project, id);
                continue; // An incomplete admission remains replayable/withdrawable after the actor reloads.
            }
            if (!helper.isAlive() || !project.header().owner().equals(WorkersBridge.readWorkerOwner(helper))) {
                if (ledger.pendingCrewMember(project, id)) withdrawPending(level, project, helper);
                else ledger.retireCrew(project, id);
                continue;
            }
            if (!workCommand(helper)) {
                if (ledger.pendingCrewMember(project, id)) withdrawPending(level, project, helper);
                else ledger.retireCrew(project, id);
                continue;
            }
            NativeConstructionGuard.resumeCrewMember(owner, helper, area, project);
        }
        if (ledger.reservedCrewMembers(project).size() >= PerimeterBuilderCrew.MAX_HELPERS) return;
        var candidates = level.getEntitiesOfClass(Mob.class, new AABB(project.header().originalCore()).inflate(64),
                        worker -> worker.isAlive() && WorkersBridge.isBuilder(worker)
                                && project.header().owner().equals(WorkersBridge.readWorkerOwner(worker)))
                .stream().sorted(Comparator.comparingDouble(worker -> worker.distanceToSqr(area))).toList();
        for (Mob helper : candidates) {
            if (ledger.reservedCrewMembers(project).size() >= PerimeterBuilderCrew.MAX_HELPERS) break;
            if (NativePerimeterProjects.assignedBuilder(helper, project) || ledger.knownCrewMember(project, helper.getUUID())
                    || !NativePerimeterProjects.idleReplacement(helper) || PerimeterProjectLink.reserved(helper)
                    || NativeConstructionGuard.hasProtectedReceipt(helper) || WorkersBridge.hasActiveBuildArea(helper)
                    || helper.getPersistentData().contains(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || NativeConstructionGuard.commissionProblem(helper) != null
                    || !ledger.canRetainHandLifecycle(helper.getPersistentData(), helper.getUUID())) continue;
            // Saved membership precedes worker receipts. Interrupted linking replays this same membership.
            if (ledger.enlistCrew(project, helper.getUUID()))
                NativeConstructionGuard.resumeCrewMember(owner, helper, area, project);
        }
    }

    /** Transfer the controller role, never inventory, to an already authenticated surviving helper. */
    static boolean promote(ServerLevel level, ServerPlayer owner, PerimeterProject project,
                           PerimeterBuilderAssignments.Cause cause) {
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !owner.mayBuild()
                || owner.serverLevel() != level || !owner.getUUID().equals(project.header().owner())
                || !SiegeCore.key(owner).equals(project.header().coreKey()) || project.active() == null) return false;
        var point = SiegeCore.point(level.getServer(), project.header().coreKey());
        if (point == null || !point.pos().equals(project.header().originalCore())
                || !PerimeterProjectAuthority.territoryMatches(project, RecruitsClaimsBridge.getFactionTerritory(level,
                    project.header().faction(), PerimeterTerritory.MAX_CHUNKS))) return false;
        var ledger = ConstructionEditLedger.get(level); UUID prior = ledger.assignedBuilder(project);
        Entity old = level.getEntity(prior);
        if (cause == PerimeterBuilderAssignments.Cause.UNAVAILABLE && old != null) return false;
        if (cause == PerimeterBuilderAssignments.Cause.DISMISSED && (!(old instanceof Mob mob)
                || project.header().owner().equals(WorkersBridge.readWorkerOwner(mob)) && workCommand(mob))) return false;
        for (UUID id : ledger.crewMembers(project)) {
            if (!(level.getEntity(id) instanceof Mob helper) || !helper.isAlive() || !workCommand(helper)
                    || helper.getTarget() != null || helper.isPassenger() || helper.isLeashed()
                    || !project.header().owner().equals(WorkersBridge.readWorkerOwner(helper))
                    || !NativePerimeterProjects.participantLinkMatches(helper, project)
                    || !ledger.canTransferCrewCoordinator(project, prior, id, cause)
                    || NativeConstructionGuard.continuationProblem(helper, project) != null
                    || !NativeConstructionGuard.readyForRetirement(helper)) continue;
            Entity current = NativeConstructionGuard.currentArea(helper);
            if (current != null && project.stages().stream().noneMatch(stage -> stage.areaId().equals(current.getUUID()))) continue;
            boolean detached = true;
            for (var stage : project.stages())
                if (!NativeConstructionGuard.retireBuilderAssociation(helper, stage.areaId())) { detached = false; break; }
            if (detached && ledger.transferCrewCoordinator(project, prior, id, cause)) return true;
        }
        return false;
    }

    static boolean dismissed(Mob worker, PerimeterProject project) {
        return worker != null && (!project.header().owner().equals(WorkersBridge.readWorkerOwner(worker)) || !workCommand(worker));
    }

    private static boolean workCommand(Mob worker) {
        try {
            int command = ((Number) worker.getClass().getMethod("getFollowState").invoke(worker)).intValue();
            return command == 0 || command == 6;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    static boolean clearNextSection(ServerLevel level, PerimeterProject project, net.minecraft.core.BlockPos marker) {
        var ledger = ConstructionEditLedger.get(level);
        for (UUID id : ledger.crewMembers(project)) {
            if (!(level.getEntity(id) instanceof com.talhanation.workers.entities.BuilderEntity helper)) {
                ledger.retireCrew(project, id); continue;
            }
            if (!NativePerimeterProjects.participantLinkMatches(helper, project)
                    || !project.header().owner().equals(WorkersBridge.readWorkerOwner(helper))) return false;
            boolean obstructs = project.active().layout().targets().keySet().stream().anyMatch(cell ->
                    helper.getBoundingBox().intersects(new AABB(net.minecraft.core.BlockPos.of(cell))));
            if (!obstructs) continue;
            if (helper.needsToSleep() || !helper.shouldWork()
                    || NativeConstructionGuard.continuationProblem(helper, project) != null) return false;
            if (!com.devfarinsky.siegeoverhaul.core.WallBuilderAccess.clearNextSection(helper,
                    project.active().layout().targets().keySet(), project.reservation(), marker)) return false;
        }
        return true;
    }

    /** Explicit withdrawal after inspecting the loaded actor; absence of an entity proves nothing. */
    private static boolean withdrawPending(ServerLevel level, PerimeterProject project, Mob helper) {
        var ledger = ConstructionEditLedger.get(level);
        if (!ledger.pendingCrewMember(project, helper.getUUID()) || helper.level() != level) return false;
        var stages = project.stages().stream().map(PerimeterProject.Stage::areaId).toList();
        UUID receipt = NativeConstructionGuard.protectedReceiptArea(helper);
        Entity current = NativeConstructionGuard.currentArea(helper);
        boolean ownReceipt = receipt != null && stages.contains(receipt);
        boolean ownArea = current != null && stages.contains(current.getUUID());
        boolean ownLink = PerimeterProjectLink.matches(helper, project, helper.getUUID());
        var tag = helper.getPersistentData();
        boolean ownNativeLink = tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && stages.contains(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        if (ownReceipt || ownArea || ownLink || ownNativeLink) {
            if (!ownLink || current != null && !ownArea || receipt != null && !ownReceipt) return false;
            if (ownReceipt && !NativeConstructionGuard.readyForRetirement(helper)) return false;
            for (UUID stage : stages) if (!NativeConstructionGuard.retireBuilderAssociation(helper, stage)) return false;
            PerimeterProjectLink.clear(helper, project.header().projectId(), project.header().generation(), project.manifestHash());
        }
        // All four exact association channels have been inspected/detached without touching another job.
        return ledger.cancelPendingCrewMember(project, helper.getUUID());
    }

    /** Every loaded member drains its own inventory lifecycle; missing members are not assumed cleaned. */
    static boolean retireStage(ServerLevel level, PerimeterProject project, UUID area) {
        var ledger = ConstructionEditLedger.get(level);
        for (UUID id : ledger.reservedCrewMembers(project)) {
            Entity found = level.getEntity(id);
            if (!(found instanceof Mob helper) || !helper.isAlive()) return false;
            if (ledger.pendingCrewMember(project, id)) {
                if (!withdrawPending(level, project, helper)) return false;
                continue;
            }
            if (!NativePerimeterProjects.participantLinkMatches(helper, project)) return false;
            if (!area.equals(NativeConstructionGuard.protectedReceiptArea(helper))) continue;
            Entity current = NativeConstructionGuard.currentArea(helper);
            if (current != null && !current.getUUID().equals(area)) return false;
            if (!NativeConstructionGuard.readyForRetirement(helper)
                    || !NativeConstructionGuard.retireBuilderAssociation(helper, area)) return false;
        }
        return true;
    }
}
