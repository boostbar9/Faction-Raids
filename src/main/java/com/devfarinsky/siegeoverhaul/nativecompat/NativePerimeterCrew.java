package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
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
        for (UUID id : ledger.crewMembers(project)) {
            Entity found = level.getEntity(id);
            if (!(found instanceof Mob helper)) {
                // Revoke work authority, retaining cleanup-only history. This is not a death receipt.
                ledger.retireCrew(project, id); continue;
            }
            if (!helper.isAlive() || !project.header().owner().equals(WorkersBridge.readWorkerOwner(helper))) {
                ledger.retireCrew(project, id); continue;
            }
            if (!workCommand(helper)) { ledger.retireCrew(project, id); continue; }
            NativeConstructionGuard.resumeCrewMember(owner, helper, area, project);
        }
        if (ledger.crewMembers(project).size() >= PerimeterBuilderCrew.MAX_HELPERS) return;
        var candidates = level.getEntitiesOfClass(Mob.class, new AABB(project.header().originalCore()).inflate(64),
                        worker -> worker.isAlive() && WorkersBridge.isBuilder(worker)
                                && project.header().owner().equals(WorkersBridge.readWorkerOwner(worker)))
                .stream().sorted(Comparator.comparingDouble(worker -> worker.distanceToSqr(area))).toList();
        for (Mob helper : candidates) {
            if (ledger.crewMembers(project).size() >= PerimeterBuilderCrew.MAX_HELPERS) break;
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

    private static boolean workCommand(Mob worker) {
        try {
            int command = ((Number) worker.getClass().getMethod("getFollowState").invoke(worker)).intValue();
            return command == 0 || command == 6;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    /** Every loaded member drains its own inventory lifecycle; missing members are not assumed cleaned. */
    static boolean retireStage(ServerLevel level, PerimeterProject project, UUID area) {
        var ledger = ConstructionEditLedger.get(level);
        for (UUID id : ledger.crewMembers(project)) {
            Entity found = level.getEntity(id);
            if (!(found instanceof Mob helper) || !helper.isAlive()
                    || !NativePerimeterProjects.participantLinkMatches(helper, project)) return false;
            if (!area.equals(NativeConstructionGuard.protectedReceiptArea(helper))) continue;
            Entity current = NativeConstructionGuard.currentArea(helper);
            if (current != null && !current.getUUID().equals(area)) return false;
            if (!NativeConstructionGuard.readyForRetirement(helper)
                    || !NativeConstructionGuard.retireBuilderAssociation(helper, area)) return false;
        }
        return true;
    }
}
