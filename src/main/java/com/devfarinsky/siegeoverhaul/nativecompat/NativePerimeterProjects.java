package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** One paid manifest, one native section at a time. No direct block or inventory mutations. */
public final class NativePerimeterProjects {
    private NativePerimeterProjects() {}

    /** The original builder stays in the paid manifest; this resolves the current saved assignment. */
    public static boolean assignedBuilder(Mob worker, PerimeterProject project) {
        if (worker == null || project == null) return false;
        try {
            UUID expected = worker.level() instanceof ServerLevel level
                    ? ConstructionEditLedger.get(level).assignedBuilder(project) : project.header().builder();
            return expected.equals(worker.getUUID());
        } catch (RuntimeException unavailable) { return false; }
    }

    public static boolean projectLinkMatches(Mob worker,PerimeterProject project) {
        if (worker==null || !(worker.level() instanceof ServerLevel level)) return false;
        try { return PerimeterProjectLink.matches(worker,project,ConstructionEditLedger.get(level).assignedBuilder(project)); }
        catch (RuntimeException unavailable) { return false; }
    }

    /** Worker participation is separate from the coordinator used to seal native markers. */
    public static boolean participatingBuilder(Mob worker, PerimeterProject project) {
        if (assignedBuilder(worker, project)) return true;
        if (worker == null || project == null || !(worker.level() instanceof ServerLevel level)) return false;
        try { return ConstructionEditLedger.get(level).crewMember(project, worker.getUUID()); }
        catch (RuntimeException unavailable) { return false; }
    }

    public static boolean participantLinkMatches(Mob worker, PerimeterProject project) {
        return participatingBuilder(worker, project) && PerimeterProjectLink.matches(worker, project, worker.getUUID());
    }

    private static boolean knownParticipantLinkMatches(Mob worker, PerimeterProject project) {
        if (worker == null || !(worker.level() instanceof ServerLevel level)) return false;
        try {
            return (ConstructionEditLedger.get(level.getServer().overworld()).assignedBuilder(project).equals(worker.getUUID())
                    || ConstructionEditLedger.get(level.getServer().overworld()).knownCrewMember(project, worker.getUUID()))
                    && PerimeterProjectLink.matches(worker, project, worker.getUUID());
        } catch (RuntimeException unavailable) { return false; }
    }

    /** Fresh component-wide access authority, independent of the current native recipe's bounds. */
    public static Set<BlockPos> gateDetourPads(Mob builder, Entity area) {
        if (builder == null || area == null || !(builder.level() instanceof ServerLevel level)
                || area.level() != level || NativeConstructionGuard.currentArea(builder) != area) return Set.of();
        try {
            if (!PerimeterProjectAuthority.tracked(area)
                    || PerimeterProjectAuthority.problem(level, builder, area, false, false) != null) return Set.of();
            var project = PerimeterProjectAuthority.project(level, PerimeterProjectAuthority.read(area.getPersistentData()));
            if (project.gateContract() == null) return Set.of();
            int component = project.gateStageComponent(project.activeStage());
            Set<BlockPos> pads = new LinkedHashSet<>();
            for (var gate : project.gateContract().gates()) if (gate.componentId() == component)
                pads.add(gate.outerCenter().relative(gate.facing(), 2));
            return Set.copyOf(pads);
        } catch (RuntimeException | LinkageError unavailable) { return Set.of(); }
    }

    public static boolean start(ServerPlayer owner,Mob builder,BlockPos corePos,int material,
                                PerimeterBlueprint.Plan plan,PerimeterStageLayout.Layout layout,
                                Map<Long,BlockState> before,Map<Long,BlockState> clearance,
                                PerimeterGateContract gateContract,
                                RecruitsClaimsBridge.TerritorySnapshot territory,String reviewedHash) {
        if(owner==null || builder==null || territory==null || !territory.ready())return false;
        var data=RaidSavedData.get(owner.server);String key=SiegeCore.key(owner);CompoundTag core=data.siegeCores.get(key);
        if(core==null)return message(owner,"The authoritative faction Treasury is unavailable. No payment taken.");
        PerimeterProject project=null;boolean paymentAttempted=false;
        try {
            var header=PerimeterProject.Header.newCommission(UUID.randomUUID(),1,owner.getUUID(),builder.getUUID(),key,
                    corePos,territory.factionStringId(),material,reviewedHash,territory.chunks());
            project=gateContract==null?PerimeterProject.prepare(header,plan,layout,before,clearance)
                    :PerimeterProject.prepareWithGates(header,plan,layout,before,clearance,gateContract);
            String problem=context(owner.serverLevel(),owner,builder,project);
            if(problem!=null)throw new IllegalStateException(problem);
            problem=gateObservationProblem(owner.serverLevel(),owner,project,-1);
            if(problem!=null)throw new IllegalStateException(problem);
            if(PerimeterProjectLink.reserved(builder))throw new IllegalStateException("Builder is reserved by another whole perimeter.");
            if(NativeConstructionGuard.currentArea(builder)!=null || WorkersBridge.hasActiveBuildArea(builder)
                    || builder.getPersistentData().contains(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))
                throw new IllegalStateException("Builder already has a native construction assignment.");
            var ledger=ConstructionEditLedger.get(owner.serverLevel());
            if(!ledger.canRetainHandLifecycle(builder.getPersistentData(),builder.getUUID()))
                throw new IllegalStateException("Protected hand history is full or cannot be authenticated; no project was prepared.");
            if(!ledger.canRegisterProject(project))throw new IllegalStateException("The complete footprint or reservation capacity is unavailable.");
            if(PerimeterStageJournal.get(core,project)!=null)throw new IllegalStateException("The new perimeter identity already has recovery history.");
            // Construct an unregistered native entity first; it cannot tick, charge or mutate the world.
            Entity candidate=ProtectedConstructionAreas.createStage(owner,builder,project);
            project=PerimeterProjectStore.prepare(core,project,data::setDirty);
            PerimeterStageJournal.prepare(core,project,data::setDirty);
            if(!ledger.registerProject(project))throw new IllegalStateException("The complete footprint could not be reserved.");
            PerimeterProjectLink.set(builder,project,ConstructionEditLedger.get((ServerLevel)builder.level()).assignedBuilder(project));
            if(!admit(owner,builder,core,project,candidate,data::setDirty))throw new IllegalStateException("The first native section could not be accepted safely.");
            paymentAttempted=true;
            var payment=PerimeterProjectStore.consumeOnce(core,header.projectId(),project.manifestHash(),header.quotedPrice(),owner.isCreative(),data::setDirty);
            if(!payment.paid()) {paymentAttempted=false;throw new IllegalStateException("The faction Treasury no longer has the quoted 64 emeralds.");}
            project=payment.project();
            project=replace(core,project,project.activate(project.check()),data::setDirty);
            if(!NativeConstructionGuard.activate(candidate))pause(core,project,"Paid once; native activation needs recovery review.",data::setDirty);
            owner.sendSystemMessage(Component.literal("Whole-territory perimeter commissioned: "+plan.blocks().size()+" blocks in "+layout.stages().size()
                    +" native sections. One 64-emerald faction Treasury fee; supply the building materials separately."));
            return true;
        } catch(RuntimeException | LinkageError failure) {
            if(project!=null) {
                try {
                    PerimeterProject current=PerimeterProjectStore.get(core,project.header().projectId());
                    if(current!=null && current.payment()!=null) {
                        pause(core,current,"Paid once; commission recovery needs review.",data::setDirty);
                        message(owner,"The perimeter was paid once and retained for safe recovery. Building > Construction shows its status.");
                        return true;
                    }
                    if(current!=null) {
                        paymentAttempted=false; // The authoritative stored record positively proves it remains unpaid.
                        current=replace(core,current,current.cancel(current.check(),"Canceled before payment: native acceptance failed."),data::setDirty);
                        cleanup(owner.serverLevel(),core,current,data::setDirty);
                    }
                }catch(RuntimeException | LinkageError uncertain){ FactionLogger.LOG.warn("[SiegeOverhaul] Unpaid perimeter cleanup needs recovery review",uncertain); }
            }
            FactionLogger.LOG.warn("[SiegeOverhaul] Whole perimeter could not be commissioned",failure);
            if(paymentAttempted)return message(owner,"Commission payment could not be reconciled. The recovery records and plan were kept; do not start another commission until this project is reviewed.");
            return message(owner,"The perimeter could not be accepted safely. No new payment was taken; review the retained plan. "+bounded(failure.getMessage()));
        }
    }

    /** Initial acceptance and later stages use exactly the same native assignment boundary; this never charges. */
    private static boolean admit(ServerPlayer owner,Mob builder,CompoundTag core,PerimeterProject project,Entity candidate,Runnable dirty) {
        ServerLevel level=owner.serverLevel();var ledger=ConstructionEditLedger.get(level);
        int previous=project.activeStage()-1;
        if(!ledger.matchesProjectReservation(project) || !creationHistoryMatches(project,PerimeterStageJournal.get(core,project),
                ledger.projectLeaseIndex(project.header().projectId()),previous<0||ledger.retired(project.stages().get(previous).areaId()))
                || !entitiesLoaded(level,candidate.blockPosition()) || level.getEntity(candidate.getUUID())!=null
                || !ledger.leaseProjectStage(project))return false;
        var permit=PerimeterStageJournal.begin(core,project,candidate.blockPosition(),dirty);
        if(permit==null || !permit.claim(core,project))return false;
        PerimeterProjectAuthority.stamp(candidate,project);
        PerimeterTerritory.remember(candidate,new RecruitsClaimsBridge.TerritorySnapshot(project.header().faction(),project.header().territory(),null));
        var part=project.active().layout();
        candidate.getPersistentData().putLong(PerimeterConstruction.SITE_MIN,part.min().asLong());
        candidate.getPersistentData().putLong(PerimeterConstruction.SITE_MAX,part.max().asLong());
        ConstructionReport.remember(candidate,"Perimeter section "+(part.index()+1)+" / "+project.stages().size(),part.targets().size());
        PlayerFortificationJobs.link(builder,candidate,owner.getUUID());
        if(!level.addFreshEntity(candidate))return false;
        try {
            WorkersBridge.startBlueprint(candidate,TerritoryFortification.blueprint(part.targets(),part.min(),part.max()));
            if(!NativeConstructionGuard.protectStage(owner,builder,candidate,project))return false;
            WorkersBridge.enableWallProjection(candidate,part.targets().size());WorkersBridge.enablePlayerJob(builder,owner.getUUID());
            if(!WallBuilderAccess.install(builder) || !WorkersBridge.assignBuildAreaDirectly(builder,candidate))return false;
            PerimeterStageJournal.live(core,project,dirty);return true;
        }catch(ReflectiveOperationException | RuntimeException unavailable){return false;}
    }

    /** Once a second, server-thread only. Reading unavailable chunks never loads them. */
    public static void tick(MinecraftServer server) {
        if(server==null || !server.isSameThread() || server.getTickCount()%20!=0 || !WorkersBridge.available())return;
        var data=RaidSavedData.get(server);var level=server.overworld();
        for(var entry:List.copyOf(data.siegeCores.entrySet())) {
            CompoundTag core=entry.getValue();if(!core.contains(PerimeterProjectStore.KEY))continue;
            try {
                var projects=PerimeterProjectAuthority.snapshot(level,entry.getKey()).projects();
                for(var project:projects) {
                    try { advance(level,core,project,data::setDirty); }
                    catch(RuntimeException | LinkageError unavailable) {
                        var current=PerimeterProjectStore.get(core,project.header().projectId());
                        if(current!=null && current.state()!=PerimeterProject.State.COMPLETE && current.state()!=PerimeterProject.State.CANCELED)
                            pause(core,current,"Paused: saved native section evidence needs recovery review.",data::setDirty);
                    }
                }
            }catch(RuntimeException | LinkageError malformed){ /* Unknown records never become absent/new work. */ }
        }
    }

    private static void advance(ServerLevel level,CompoundTag core,PerimeterProject project,Runnable dirty) {
        if(project.state()==PerimeterProject.State.CANCELED || project.state()==PerimeterProject.State.COMPLETE) {
            cleanup(level,core,project,dirty);return;
        }
        if(project.state()==PerimeterProject.State.PREPARED_UNPAID) {
            pause(core,project,"Unpaid interrupted commission: cancel it before reviewing another perimeter.",dirty);return;
        }
        if(project.state()==PerimeterProject.State.RECOVERY_BLOCKED)return;
        var ledger=ConstructionEditLedger.get(level);
        if(!ledger.matchesProjectIdentity(project)) {pause(core,project,"Paused: complete reservation or edit history differs from the reviewed perimeter.",dirty);return;}
        var owner=level.getServer().getPlayerList().getPlayer(project.header().owner());
        Entity found=level.getEntity(ledger.assignedBuilder(project));Mob builder=found instanceof Mob mob?mob:null;
        // Authenticated destruction differs from an unloaded worker. Only confirmed death opens an assignment.
        if (builder == null && ledger.projectBuilderDestructionReceipt(project) != null) {
            tryReplaceDeadBuilder(level, owner, project, ledger);
            found = level.getEntity(ledger.assignedBuilder(project)); builder = found instanceof Mob mob ? mob : null;
        }
        if (!ledger.matchesProjectReservation(project)) {
            pause(core,project,ledger.projectBuilderDestructionReceipt(project) != null
                    ? "Waiting: hire an idle builder near the original Siege Core to replace the dead builder. No extra commission is due."
                    : "Paused: complete reservation or edit history differs from the reviewed perimeter.",dirty);return;
        }
        if (builder != null && project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                && !builder.getUUID().equals(project.header().builder()) && !PerimeterProjectLink.reserved(builder)
                && idleReplacement(builder) && NativeConstructionGuard.currentArea(builder)==null
                && !NativeConstructionGuard.hasProtectedReceipt(builder)) {
            PerimeterProjectLink.set(builder,project,ConstructionEditLedger.get((ServerLevel)builder.level()).assignedBuilder(project));
            if (!PerimeterProjectLink.bindLedgerGeneration(builder,ledger.generation())) return;
        }
        if (builder != null && project.active() != null && level.getEntity(project.active().areaId()) instanceof ProtectedBuildArea live
                && !NativeConstructionGuard.resumeReplacement(owner,builder,live,project)) {
            pause(core,project,"Waiting: replacement builder handoff needs loaded, unchanged section evidence.",dirty);return;
        }
        String problem=context(level,owner,builder,project);
        if(problem!=null) {pause(core,project,problem,dirty);return;}
        if(!projectLinkMatches(builder,project)) {pause(core,project,"Paused: the reserved builder's whole-perimeter identity changed.",dirty);return;}
        var journal=PerimeterStageJournal.get(core,project);
        if(journal==null) {pause(core,project,"Paused: native marker creation history is unavailable.",dirty);return;}
        if(project.state()==PerimeterProject.State.VERIFYING_COMPLETE) {
            problem=wholeWorldProblem(level,owner,project);
            if(problem!=null) {pause(core,project,problem,dirty);return;}
            project=replace(core,project,project.complete(project.check(),project.manifestHash()),dirty);
            cleanup(level,core,project,dirty);return;
        }
        var attempt=journal.at(project.activeStage());
        if(project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE && attempt==null) {
            var first=journal.at(0);
            if(first==null || !entitiesLoaded(level,first.marker())) {
                pause(core,project,"Waiting: load the original shovel site's chunk before the next section.",dirty);return;
            }
            if(!creationHistoryMatches(project,journal,ledger.projectLeaseIndex(project.header().projectId()),
                    ledger.retired(project.stages().get(project.activeStage()-1).areaId()))
                    || level.getEntity(project.active().areaId())!=null) {
                pause(core,project,"Paused: saved native section history is ahead of its creation journal.",dirty);return;
            }
            if(NativeConstructionGuard.currentArea(builder)!=null || WorkersBridge.hasActiveBuildArea(builder)) {
                pause(core,project,"Waiting: reserved builder has another native assignment.",dirty);return;
            }
            problem=NativeConstructionGuard.commissionProblem(builder);
            if(problem!=null) {pause(core,project,"Waiting: builder is finishing native supplies or item use.",dirty);return;}
            problem=unstartedStageProblem(level,owner,project);
            if(problem!=null) {pause(core,project,problem,dirty);return;}
            if (!WallBuilderAccess.clearNextSection(builder,project.active().layout().targets().keySet(),project.reservation(),first.marker())) {
                pause(core,project,"Waiting: assigned builder is walking out of the next section before native admission.",dirty);return;
            }
            Entity area;
            try {area=ProtectedConstructionAreas.createStage(owner,builder,project,first.marker());}
            catch(RuntimeException unavailable) {pause(core,project,"Waiting: clear and load the original shovel site for the next native section.",dirty);return;}
            if(!admit(owner,builder,core,project,area,dirty)) {pause(core,project,"Paused: next native section acceptance needs recovery review.",dirty);return;}
            project=replace(core,project,project.activate(project.check()),dirty);
            if(!NativeConstructionGuard.activate(area))pause(core,project,"Paused: next section activation needs recovery review.",dirty);
            return;
        }
        if(attempt==null) {pause(core,project,"Paused: active native marker intent is missing.",dirty);return;}
        if(!entitiesLoaded(level,attempt.marker())) {pause(core,project,"Waiting: load the current native marker's chunk.",dirty);return;}
        Entity area=level.getEntity(attempt.area());
        if(project.state()==PerimeterProject.State.STAGE_VERIFIED) {
            retireVerified(level,core,project,builder,area,attempt,dirty);return;
        }
        if(!(area instanceof ProtectedBuildArea protectedArea) || !area.isAlive() || !area.blockPosition().equals(attempt.marker())) {
            pause(core,project,"Paused: recorded native marker is missing or moved. It will not be recreated.",dirty);return;
        }
        if (!NativeConstructionGuard.resumeReplacement(owner, builder, protectedArea, project)) {
            pause(core,project,"Waiting: replacement builder handoff needs loaded, unchanged section evidence.",dirty);return;
        }
        problem=PerimeterProjectAuthority.problem(level,builder,area,true,false);
        if(problem!=null) {pause(core,project,problem,dirty);return;}
        if(attempt.state()==PerimeterStageJournal.State.INTENT)PerimeterStageJournal.live(core,project,dirty);
        if(project.state()==PerimeterProject.State.PREPARED_PAID || project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE)
            project=replace(core,project,project.activate(project.check()),dirty);
        if(!NativeConstructionGuard.commissionPaid(area) && !NativeConstructionGuard.activate(area)) {
            pause(core,project,NativeConstructionGuard.status(area),dirty);return;
        }
        Entity current=NativeConstructionGuard.currentArea(builder);
        if(current!=null && current!=area) {pause(core,project,"Paused: reserved builder has another native assignment.",dirty);return;}
        if(current==null) {
            problem=NativeConstructionGuard.commissionProblem(builder);
            if(problem!=null) {pause(core,project,"Waiting: builder is finishing native supplies or item use.",dirty);return;}
            try {
                PlayerFortificationJobs.link(builder,area,owner.getUUID());WallBuilderAccess.install(builder);
                WorkersBridge.enablePlayerJob(builder,owner.getUUID());
                if(!WorkersBridge.assignBuildAreaDirectly(builder,area)) {pause(core,project,"Paused: native section reassignment could not be verified.",dirty);return;}
            }catch(ReflectiveOperationException unavailable){pause(core,project,"Paused: native builder API is unavailable.",dirty);return;}
        }
        NativePerimeterCrew.advance(level, owner, project, protectedArea);
        pause(core,project,"",dirty);
    }

    private static void tryReplaceDeadBuilder(ServerLevel level, ServerPlayer owner, PerimeterProject project,
                                              ConstructionEditLedger ledger) {
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !owner.mayBuild()
                || owner.serverLevel() != level || !owner.getUUID().equals(project.header().owner())
                || !SiegeCore.key(owner).equals(project.header().coreKey())
                || project.active() == null || !(project.state() == PerimeterProject.State.RUNNING
                    || project.state() == PerimeterProject.State.STAGE_VERIFIED
                    || project.state() == PerimeterProject.State.WAITING_FOR_NEXT_STAGE)) return;
        var core = RaidSavedData.get(level.getServer()).siegeCores.get(project.header().coreKey());
        var journal = core == null ? null : PerimeterStageJournal.get(core,project);
        boolean between=project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE;
        var attempt = journal == null ? null : journal.at(between?project.activeStage()-1:project.activeStage());
        if (attempt == null || !entitiesLoaded(level,attempt.marker())
                || !ledger.matchesProjectIdentity(project) || ledger.edited(project.header().projectId())) return;
        Entity marker=level.getEntity(attempt.area());
        if (between ? attempt.state()!=PerimeterStageJournal.State.RETIRED || !ledger.retired(attempt.area()) || marker!=null
                : !(marker instanceof ProtectedBuildArea) || !marker.isAlive() || !marker.blockPosition().equals(attempt.marker())) return;
        var point=SiegeCore.point(level.getServer(),project.header().coreKey());
        if (point == null || !point.pos().equals(project.header().originalCore())
                || !PerimeterProjectAuthority.territoryMatches(project,RecruitsClaimsBridge.getFactionTerritory(level,
                    project.header().faction(),PerimeterTerritory.MAX_CHUNKS))) return;
        // Selection never loads chunks, revives workers, or moves materials out of death drops.
        var candidates = level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(project.header().originalCore()).inflate(64),
                mob -> mob.isAlive() && WorkersBridge.isBuilder(mob)
                        && project.header().owner().equals(WorkersBridge.readWorkerOwner(mob)))
                .stream().sorted(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(attempt.marker())))).toList();
        for (Mob candidate : candidates) {
            if (!idleReplacement(candidate) || PerimeterProjectLink.reserved(candidate) || NativeConstructionGuard.hasProtectedReceipt(candidate)
                    || WorkersBridge.hasActiveBuildArea(candidate) || NativeConstructionGuard.currentArea(candidate) != null
                    || candidate.getPersistentData().contains(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || NativeConstructionGuard.commissionProblem(candidate) != null
                    || !ledger.canRetainHandLifecycle(candidate.getPersistentData(),candidate.getUUID())) continue;
            if (ledger.replaceDeadBuilder(project,candidate.getUUID())) return;
        }
    }

    static boolean idleReplacement(Mob builder) {
        try {
            int command=((Number)builder.getClass().getMethod("getFollowState").invoke(builder)).intValue();
            // Workers 2 changes idle command 0 to work command 6 as its selection
            // goal starts, even before it owns a build area. Both are available
            // only while the native assignment is empty; following/holding is not.
            return builder.getTarget() == null && !builder.isPassenger()
                    && (command == 0 || command == 6)
                    && NativeConstructionGuard.currentArea(builder)==null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    private static void retireVerified(ServerLevel level,CompoundTag core,PerimeterProject project,Mob builder,
                                       Entity area,PerimeterStageJournal.Attempt attempt,Runnable dirty) {
        var ledger=ConstructionEditLedger.get(level);var journal=PerimeterStageJournal.get(core,project);
        var stored=PerimeterProjectStore.get(core,project.header().projectId());
        if(stored==null || !stored.check().equals(project.check()) || journal==null || journal.at(0)==null
                || !attempt.equals(journal.at(project.activeStage())) || !entitiesLoaded(level,attempt.marker())
                || !ledger.matchesProjectReservation(project)
                || ledger.projectLeaseIndex(project.header().projectId())!=project.activeStage()) {
            pause(core,project,"Paused: completed section and reservation history disagree.",dirty);return;
        }
        if(area!=null) {
            if(!retirementMarkerMatches(area,project,attempt,journal.at(0).marker())
                    || !NativeConstructionGuard.matchesProjectSnapshot(area,project)) {
                pause(core,project,"Paused: completed native marker differs from its verification receipt.",dirty);return;
            }
        }
        // The durable verification receipt precedes native DONE. Reload deliberately resets isDone;
        // re-prove those exact finished cells rather than replaying native placement or scanned entities.
        String world=finishedStageProblem(level,project);
        if(world!=null){pause(core,project,world,dirty);return;}
        if(!NativeConstructionGuard.retirementReceiptMatches(builder.getPersistentData(),
                project.stages().stream().map(PerimeterProject.Stage::areaId).toList(),ledger.generation())) {
            pause(core,project,"Paused: completed section's worker receipt needs recovery review.",dirty);return;
        }
        Entity current=NativeConstructionGuard.currentArea(builder);
        if(current!=null && !current.getUUID().equals(attempt.area())) {
            pause(core,project,"Paused: builder assignment changed before section cleanup.",dirty);return;
        }
        if(!NativeConstructionGuard.readyForRetirement(builder))return;
        if(!NativePerimeterCrew.retireStage(level, project, attempt.area())) return;
        if(!NativeConstructionGuard.retireBuilderAssociation(builder,attempt.area())) {
            pause(core,project,"Paused: completed section cannot be detached safely.",dirty);return;
        }
        if(area instanceof ProtectedBuildArea protectedArea)protectedArea.removeAuthorized();
        ledger.retire(attempt.area(),true);
        if(!ledger.retired(attempt.area()) || level.getEntity(attempt.area())!=null) {
            pause(core,project,"Waiting for the completed native marker to retire.",dirty);return;
        }
        PerimeterStageJournal.retired(core,project,dirty);
        replace(core,project,project.retireVerifiedStage(project.check()),dirty);
    }

    private static void cleanup(ServerLevel level,CompoundTag core,PerimeterProject project,Runnable dirty) {
        if(project.state()!=PerimeterProject.State.COMPLETE && project.state()!=PerimeterProject.State.CANCELED)return;
        var stored=PerimeterProjectStore.get(core,project.header().projectId());
        if(stored==null || !stored.check().equals(project.check()))return;
        var journal=PerimeterStageJournal.get(core,project);if(journal==null)return;
        var ledger=ConstructionEditLedger.get(level);
        if(!ledger.matchesProjectIdentity(project))return;
        if(!cleanupHistoryMatches(project,journal,ledger.projectLeaseIndex(project.header().projectId())))return;
        Mob builder=findBuilder(level.getServer(),ledger.assignedBuilder(project));
        String destruction=builder==null?ledger.projectBuilderDestructionReceipt(project):null;
        // A missing worker is never death evidence. Only the exact authenticated, durable destruction
        // receipt can replace live detachment; a restored loaded worker still needs ordinary safe cleanup.
        if(destruction==null) {
            if(builder==null || !builder.isAlive() || !WorkersBridge.isBuilder(builder) || !projectLinkMatches(builder,project))return;
            if(!NativeConstructionGuard.retirementReceiptMatches(builder.getPersistentData(),
                    project.stages().stream().map(PerimeterProject.Stage::areaId).toList(),ledger.generation())) {
                NativeConstructionGuard.pauseStorage(builder,"Paused: whole-perimeter cleanup worker receipt needs recovery review");return;
            }
            if(!currentAreaWithin(builder,journal.attempts().stream().map(PerimeterStageJournal.Attempt::area).toList())) {
                NativeConstructionGuard.pauseStorage(builder,"Paused: another native assignment must finish before whole-perimeter cleanup");return;
            }
        }
        for(var attempt:journal.attempts()) {
            if(!entitiesLoaded(level,attempt.marker()))return;
            Entity marker=level.getEntity(attempt.area());
            if(journal.at(0)==null || marker!=null && !retirementMarkerMatches(marker,project,attempt,journal.at(0).marker()))return;
        }
        if(destruction==null) {
            if(!NativeConstructionGuard.readyForRetirement(builder))return;
            // Detach only references whose immutable IDs belong to this commission, including a transferred worker.
            for(var stage:project.stages())if(!NativeConstructionGuard.retireBuilderAssociation(builder,stage.areaId()))return;
        }
        for (var stage : project.stages()) if (!NativePerimeterCrew.retireStage(level, project, stage.areaId())) return;
        for(var attempt:journal.attempts()) {
            Entity marker=level.getEntity(attempt.area());
            if(marker instanceof ProtectedBuildArea protectedArea)protectedArea.removeAuthorized();
            if(level.getEntity(attempt.area())!=null)return;
        }
        // Journal retirement is normally recorded one stage at a time. A terminal's active attempted section
        // may have been canceled halfway through, but all earlier entries must already be retired.
        var active=journal.at(project.activeStage());
        if(active!=null && active.state()!=PerimeterStageJournal.State.RETIRED) {
            PerimeterStageJournal.retired(core,project,dirty);journal=PerimeterStageJournal.get(core,project);
        }
        if(journal.attempts().stream().anyMatch(a->a.state()!=PerimeterStageJournal.State.RETIRED))return;
        // Missing reservation history is uncertain, not successful cleanup. A compact receipt is issued only
        // by the same call that positively retires this still-known exact global reservation.
        if(!ledger.canRetire(project.header().projectId()))return;
        UUID generation=ledger.generation();
        List<UUID> ids=project.stages().stream().map(PerimeterProject.Stage::areaId).toList();
        String markerEvidence=journal.attempts().stream().map(a->a.stage()+":"+a.area()+":"+a.marker().asLong()+":"+a.state())
                .reduce("",(a,b)->a+"\n"+b);
        String prefix=project.header().projectId()+":"+project.header().generation()+":"+project.manifestHash()+":"+generation+":"+ids;
        ledger.retire(project.header().projectId(),destruction==null);
        if(ledger.contains(project.header().projectId()) || ids.stream().anyMatch(ledger::contains))return;
        var proof=new PerimeterTerminalReceipt.CleanupProof(project.header().projectId(),project.header().generation(),project.manifestHash(),
                project.revision(),project.state(),project.header().owner(),project.header().builder(),generation,ids,
                digest("reservation-retired:"+prefix),destruction==null
                ?digest("exact-worker-detached:"+prefix+":"+builder.getUUID())
                :digest("exact-worker-destroyed:"+prefix+":"+project.header().builder()+":"+destruction),
                digest("loaded-markers-absent:"+prefix+":"+markerEvidence));
        var terminal=PerimeterProjectStore.compact(core,project.check(),proof,dirty);
        PerimeterStageJournal.compacted(core,terminal,dirty);
        if(builder!=null)PerimeterProjectLink.clear(builder,project.header().projectId(),project.header().generation(),project.manifestHash());
    }

    /** Neither an ahead-of-core lease nor an unattempted stage may serve as terminal cleanup evidence. */
    static boolean cleanupHistoryMatches(PerimeterProject project,PerimeterStageJournal.Entry journal,int leaseIndex) {
        if(project==null || journal==null || !journal.project().equals(project.header().projectId())
                || journal.generation()!=project.header().generation() || !journal.hash().equals(project.manifestHash())
                || leaseIndex!=journal.attempts().size()-1 || journal.attempts().size()<project.activeStage()
                || journal.attempts().size()>Math.min(project.stages().size(),project.activeStage()+1))return false;
        for(int index=0;index<journal.attempts().size();index++) {
            var attempt=journal.attempts().get(index);
            if(attempt.stage()!=index || !attempt.area().equals(project.stages().get(index).areaId())
                    || !attempt.marker().equals(journal.at(0).marker())
                    || index<project.activeStage() && attempt.state()!=PerimeterStageJournal.State.RETIRED)return false;
        }
        return true;
    }

    /** Terminal receipts deny stale worker saves before any native AI or inventory goal resumes. */
    public static boolean reconcileWorker(Mob worker) {
        if(!PerimeterProjectLink.reserved(worker))return true;
        if(!(worker.level() instanceof ServerLevel level))return true;
        try {
            var link=PerimeterProjectLink.read(worker.getPersistentData());
            var ledger=ConstructionEditLedger.get(level.getServer().overworld());
            var snapshot=PerimeterProjectAuthority.snapshot(level.getServer().overworld(),link.core());
            var project=snapshot.get(link.id());
            if(project!=null) {
                if(!knownParticipantLinkMatches(worker,project) || !ledger.matchesProjectIdentity(project)
                        || !NativeConstructionGuard.retirementReceiptMatches(worker.getPersistentData(),
                        project.stages().stream().map(PerimeterProject.Stage::areaId).toList(),ledger.generation()))
                    return NativeConstructionGuard.pauseStorage(worker,"Paused: whole-perimeter worker or ledger identity needs recovery review");
                if (!assignedBuilder(worker, project) && !ledger.crewMember(project, worker.getUUID())) {
                    if (!NativeConstructionGuard.readyForRetirement(worker)) return true;
                    for (var stage : project.stages())
                        if (!NativeConstructionGuard.retireBuilderAssociation(worker, stage.areaId())) return false;
                    PerimeterProjectLink.clear(worker, link.id(), link.generation(), link.hash());
                    return true;
                }
                if(project.state()==PerimeterProject.State.CANCELED || project.state()==PerimeterProject.State.COMPLETE) {
                    if(!currentAreaWithin(worker,project.stages().stream().map(PerimeterProject.Stage::areaId).toList())) {
                        NativeConstructionGuard.pauseStorage(worker,"Paused: another native assignment must finish before old perimeter cleanup");return true;
                    }
                    if(!NativeConstructionGuard.readyForRetirement(worker))return true;
                    for(var stage:project.stages())if(!NativeConstructionGuard.retireBuilderAssociation(worker,stage.areaId()))return false;
                } else {
                    // A later core save can outlive the worker's previous-section
                    // link. Detach only a durably verified and retired prefix.
                    UUID old=NativeConstructionGuard.protectedReceiptArea(worker);
                    var prior=project.receipts().stream().filter(r->r.stageIndex()<project.activeStage() && r.areaId().equals(old)).findFirst();
                    if(prior.isPresent()) {
                        if(!ledger.retired(old))return NativeConstructionGuard.pauseStorage(worker,"Paused: old native section retirement is not proven");
                        if(!currentAreaWithin(worker,List.of(old))) {
                            NativeConstructionGuard.pauseStorage(worker,"Paused: another native section is active; old cleanup evidence is retained");return true;
                        }
                        if(!NativeConstructionGuard.readyForRetirement(worker))return true;
                        if(!NativeConstructionGuard.retireBuilderAssociation(worker,old))return false;
                    }
                }
                return true;
            }
            var terminal=snapshot.terminal(link.id());
            if(terminal==null || terminal.generation()!=link.generation() || !terminal.manifestHash().equals(link.hash())
                    || !terminal.coreKey().equals(link.core()) || !(ledger.assignedTerminalBuilder(terminal).equals(worker.getUUID())
                        || ledger.knownTerminalCrewMember(terminal, worker.getUUID()))
                    || !ledger.sameGeneration(terminal.cleanup().ledgerGeneration())
                    || !NativeConstructionGuard.retirementReceiptMatches(worker.getPersistentData(),
                    terminal.stages().stream().map(PerimeterTerminalReceipt.Stage::areaId).toList(),terminal.cleanup().ledgerGeneration()))
                return NativeConstructionGuard.pauseStorage(worker,"Paused: compact perimeter worker cleanup evidence needs recovery review");
            if(!currentAreaWithin(worker,terminal.stages().stream().map(PerimeterTerminalReceipt.Stage::areaId).toList())) {
                NativeConstructionGuard.pauseStorage(worker,"Paused: another native assignment must finish before compact perimeter cleanup");return true;
            }
            if(!NativeConstructionGuard.readyForRetirement(worker))return true;
            for(var stage:terminal.stages())if(!NativeConstructionGuard.retireBuilderAssociation(worker,stage.areaId()))return false;
            PerimeterProjectLink.clear(worker,link.id(),link.generation(),link.hash());return true;
        }catch(RuntimeException | LinkageError unavailable){return NativeConstructionGuard.pauseStorage(worker,"Paused: whole-perimeter worker cleanup needs recovery review");}
    }

    private static boolean currentAreaWithin(Mob worker,Collection<UUID> allowed) {
        Entity current=NativeConstructionGuard.currentArea(worker);
        return current==null || allowed.contains(current.getUUID());
    }
    /** Identity-only cleanup proof, independent of the native reload-reset DONE flag. */
    static boolean retirementMarkerMatches(Entity marker,PerimeterProject project,
                                            PerimeterStageJournal.Attempt attempt,BlockPos fixedMarker) {
        try {
            if(!(marker instanceof ProtectedBuildArea area) || attempt==null || fixedMarker==null
                    || attempt.stage()<0 || attempt.stage()>=project.stages().size())return false;
            var stage=project.stages().get(attempt.stage());var h=project.header();
            if(!attempt.area().equals(stage.areaId()) || !marker.getUUID().equals(stage.areaId())
                    || !attempt.marker().equals(fixedMarker) || !marker.blockPosition().equals(fixedMarker)
                    || !ConstructionEditLedger.get((ServerLevel)marker.level()).builderForArea(project,attempt.area()).equals(area.reservedBuilderId()) || !h.owner().equals(WorkersBridge.readOwner(marker)))return false;
            var scope=PerimeterProjectAuthority.read(marker.getPersistentData());
            return scope.projectId().equals(h.projectId()) && scope.generation()==h.generation()
                    && scope.manifestHash().equals(project.manifestHash()) && scope.coreKey().equals(h.coreKey())
                    && scope.stage()==stage.index() && scope.stageDigest().equals(stage.digest());
        }catch(RuntimeException | LinkageError unavailable){return false;}
    }

private static String context(ServerLevel level,ServerPlayer owner,Mob builder,PerimeterProject project) {
        if(owner==null || owner.serverLevel()!=level || !owner.isAlive() || owner.isSpectator() || !owner.mayBuild())
            return "Paused: the perimeter owner must be online in the Overworld to authorize construction.";
        if(builder==null || !builder.isAlive() || builder.level()!=level || !WorkersBridge.isBuilder(builder)
                || !assignedBuilder(builder,project) || !project.header().owner().equals(WorkersBridge.readWorkerOwner(builder)))
            return "Paused: load the assigned owned builder to continue this perimeter.";
        if(!SiegeCore.key(owner).equals(project.header().coreKey()) || !level.dimension().equals(Level.OVERWORLD))
            return "Paused: the original faction authority changed.";
        var point=SiegeCore.point(level.getServer(),project.header().coreKey());
        if(point==null || !point.pos().equals(project.header().originalCore()))return "Paused: the original Siege Core must be loaded and unchanged.";
        var territory=RecruitsClaimsBridge.getFactionTerritory(level,project.header().faction(),PerimeterTerritory.MAX_CHUNKS);
        if(!PerimeterProjectAuthority.territoryMatches(project,territory))return "Paused: faction territory changed; cancel and review the complete perimeter again.";
        return null;
    }
    private static String unstartedStageProblem(ServerLevel level,ServerPlayer owner,PerimeterProject project) {
        var part=project.active().layout();
        // Native break scans include the upper endpoint, so validate the complete rectangular read envelope.
        for(int x=part.min().getX()>>4;x<=part.max().getX()>>4;x++)for(int z=part.min().getZ()>>4;z<=part.max().getZ()>>4;z++)
            if(!level.hasChunk(x,z))return "Waiting: load the next section's terrain. No extra fee is due.";
        for(long packed:part.reservation()) {
            BlockPos pos=BlockPos.of(packed);BlockState original=project.before().getOrDefault(packed,project.clearanceBefore().get(packed));
            if(!level.hasChunkAt(pos))return "Waiting: load the next section's terrain. No extra fee is due.";
            if(!level.mayInteract(owner,pos) || !level.getWorldBorder().isWithinBounds(pos))return "Paused: permission changed in the next section.";
            if(original==null || !level.getBlockState(pos).equals(original) || level.getBlockEntity(pos)!=null)
                return "Paused: a future section changed after the whole-territory review.";
        }
        String gates=gateObservationProblem(level,owner,project,project.gateContract()==null?-1:project.gateStageComponent(project.activeStage()));
        if(gates!=null)return gates;
        return null;
    }
    private static String wholeWorldProblem(ServerLevel level,ServerPlayer owner,PerimeterProject project) {
        for(var cell:project.targets().entrySet()) {
            BlockPos pos=BlockPos.of(cell.getKey());
            if(!level.hasChunkAt(pos))return "Awaiting final verification: load the whole perimeter.";
            if(!level.mayInteract(owner,pos) || !level.getBlockState(pos).equals(cell.getValue()) || level.getBlockEntity(pos)!=null)
                return "Paused: the completed perimeter differs from its verified targets.";
        }
        for(var cell:project.clearanceBefore().entrySet()) {
            BlockPos pos=BlockPos.of(cell.getKey());
            if(!level.hasChunkAt(pos))return "Awaiting final verification: load the whole perimeter.";
            if(!level.mayInteract(owner,pos) || !level.getBlockState(pos).equals(cell.getValue()) || level.getBlockEntity(pos)!=null)
                return "Paused: accepted perimeter headroom changed.";
        }
        String gates=gateObservationProblem(level,owner,project,-1);
        if(gates!=null)return gates;
        return null;
    }

    private static String finishedStageProblem(ServerLevel level,PerimeterProject project) {
        for(long packed:project.active().layout().reservation()) {
            BlockPos pos=BlockPos.of(packed);BlockState expected=project.targets().getOrDefault(packed,project.clearanceBefore().get(packed));
            if(!level.hasChunkAt(pos))return "Waiting: load the verified section before advancing.";
            if(expected==null || !level.getBlockState(pos).equals(expected) || level.getBlockEntity(pos)!=null)
                return "Paused: a verified section changed before native cleanup.";
        }
        var owner=level.getServer().getPlayerList().getPlayer(project.header().owner());
        String gates=gateObservationProblem(level,owner,project,project.gateContract()==null?-1:project.gateStageComponent(project.activeStage()));
        if(gates!=null)return gates;
        return null;
    }

    static String gateObservationProblem(ServerLevel level,ServerPlayer owner,PerimeterProject project,int component) {
        if(project.gateContract()==null)return null;
        java.util.function.Predicate<BlockPos> permitted=gatePermissions(level,project);
        return component<0
                ? PerimeterGateAccess.observationProblem(level,owner,project.gateContract(),permitted)
                : PerimeterGateAccess.componentObservationProblem(level,owner,project.gateContract(),component,permitted);
    }
    private static java.util.function.Predicate<BlockPos> gatePermissions(ServerLevel level,PerimeterProject project) {
        RaidSavedData.Anchor anchor=RaidSavedData.get(level.getServer()).anchors.get(project.header().coreKey());
        RaidSavedData.Anchor identity=anchor==null?null:anchor.withIdentity(project.header().faction(),anchor.teamDisplay());
        Map<ChunkPos,Boolean> permitted=new HashMap<>();
        return pos -> permitted.computeIfAbsent(new ChunkPos(pos), chunk -> {
            if(project.header().territory().contains(chunk))
                return RecruitsClaimsBridge.isChunkOwnedBy(level,chunk,project.header().faction())
                        && !ClaimBridge.isForeignClaim(level,chunk,identity);
            return !ClaimBridge.isForeignClaim(level,chunk,identity);
        });
    }

    /** Authenticated whole-project cancellation; native section buttons route here rather than releasing one lease. */
    public static boolean cancel(ServerPlayer sender,UUID id,long generation) {
        if(sender==null || id==null || !sender.isAlive() || sender.isSpectator() || !sender.serverLevel().dimension().equals(Level.OVERWORLD))return false;
        var data=RaidSavedData.get(sender.server);CompoundTag core=data.siegeCores.get(SiegeCore.key(sender));if(core==null)return false;
        try {
            var project=PerimeterProjectStore.get(core,id);
            if(project==null || project.header().generation()!=generation || !project.header().owner().equals(sender.getUUID()))return false;
            if(!SiegeCore.canUse(sender,BlockPos.of(core.getLong("Position")))) {
                Entity area=project.active()==null?null:sender.serverLevel().getEntity(project.active().areaId());
                if(area==null || sender.distanceToSqr(area)>16*16)return false;
            }
            return cancelVerified(sender,data,core,project);
        }catch(RuntimeException | LinkageError unknown){return message(sender,"Perimeter cancellation could not be reconciled safely; recovery evidence was kept.");}
    }

    /** Server-resolved marker authority survives an owner's later faction change. No client core selector is accepted. */
    static boolean cancelFromMarker(ServerPlayer sender,ProtectedBuildArea area) {
        if(sender==null || area==null || !sender.isAlive() || sender.isSpectator()
                || !sender.serverLevel().dimension().equals(Level.OVERWORLD) || area.level()!=sender.serverLevel()
                || sender.serverLevel().getEntity(area.getUUID())!=area || !area.isAlive()
                || !sender.getUUID().equals(area.getPlayerUUID()) || sender.distanceToSqr(area)>16*16)return false;
        try {
            var scope=PerimeterProjectAuthority.read(area.getPersistentData());
            var project=PerimeterProjectAuthority.project(sender.serverLevel(),scope);
            var stage=project.active();
            if(!project.header().owner().equals(sender.getUUID()) || stage==null || stage.index()!=scope.stage()
                    || !stage.areaId().equals(area.getUUID()) || !stage.digest().equals(scope.stageDigest()))return false;
            var data=RaidSavedData.get(sender.serverLevel().getServer());
            var core=data.siegeCores.get(scope.coreKey());
            if(core==null || !project.check().equals(PerimeterProjectStore.get(core,scope.projectId()).check()))return false;
            var journal=PerimeterStageJournal.get(core,project);
            var attempt=journal==null?null:journal.at(scope.stage());
            if(attempt==null || !attempt.area().equals(area.getUUID()) || !attempt.marker().equals(area.blockPosition())
                    || attempt.state()==PerimeterStageJournal.State.RETIRED)return false;
            return cancelVerified(sender,data,core,project);
        }catch(RuntimeException | LinkageError unavailable) {
            return message(sender,"Perimeter cancellation could not be reconciled safely; recovery evidence was kept.");
        }
    }
    private static boolean cancelVerified(ServerPlayer sender,RaidSavedData data,CompoundTag core,PerimeterProject project) {
        if(project.state()==PerimeterProject.State.COMPLETE)return false;
        project=replace(core,project,project.cancel(project.check(),"Canceled by owner; placed blocks and supplies are kept."),data::setDirty);
        cleanup(sender.serverLevel(),core,project,data::setDirty);
        sender.sendSystemMessage(Component.literal("Whole perimeter canceled. Placed blocks stay; commission and consumed materials are not refunded. Unloaded native records remain reserved until safe cleanup."));
        return true;
    }

    private static Mob findBuilder(MinecraftServer server,UUID id) {
        int checked=0;for(var dimension:server.getAllLevels()) {
            if(++checked>32)throw new IllegalStateException("Builder lookup is incomplete; cleanup remains reserved");
            Entity entity=dimension.getEntity(id);
            if(entity instanceof Mob mob)return mob;
            if(entity!=null)throw new IllegalStateException("Reserved builder identity belongs to another entity; cleanup remains reserved");
        }return null;
    }
    private static String digest(String value) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    /** No saved creation intent or ahead-of-core lease may be mistaken for a fresh section. */
    static boolean creationHistoryMatches(PerimeterProject project,PerimeterStageJournal.Entry journal,
                                          int leaseIndex,boolean previousRetired) {
        if(project==null || project.active()==null || journal==null
                || project.state()!=PerimeterProject.State.PREPARED_UNPAID && project.state()!=PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                || !journal.project().equals(project.header().projectId()) || journal.generation()!=project.header().generation()
                || !journal.hash().equals(project.manifestHash()) || journal.attempts().size()!=project.activeStage()
                || leaseIndex!=project.activeStage()-1 || project.activeStage()>0&&!previousRetired)return false;
        for(int index=0;index<journal.attempts().size();index++) {
            var attempt=journal.attempts().get(index);
            if(attempt.stage()!=index || !attempt.area().equals(project.stages().get(index).areaId())
                    || attempt.state()!=PerimeterStageJournal.State.RETIRED
                    || !attempt.marker().equals(journal.at(0).marker()))return false;
        }
        return true;
    }
    private static boolean entitiesLoaded(ServerLevel level,BlockPos marker) {
        long chunk = new ChunkPos(marker).toLong();
        // Public equivalent of ServerLevel's private entity-ready ticking predicate.
        return level.hasChunkAt(marker) && level.areEntitiesLoaded(chunk)
                && level.getChunkSource().isPositionTicking(chunk);
    }
    private static PerimeterProject replace(CompoundTag core,PerimeterProject previous,PerimeterProject next,Runnable dirty) {
        return PerimeterProjectStore.replace(core,previous.check(),next,dirty);
    }
    private static void pause(CompoundTag core,PerimeterProject project,String reason,Runnable dirty) {
        String safe=bounded(reason);if(project.blocker().equals(safe))return;
        replace(core,project,project.waitFor(project.check(),safe),dirty);
    }
    private static String bounded(String value) {return value==null?"":value.substring(0,Math.min(256,value.length()));}
    private static boolean message(ServerPlayer owner,String text) {owner.sendSystemMessage(Component.literal(text));return false;}
}
