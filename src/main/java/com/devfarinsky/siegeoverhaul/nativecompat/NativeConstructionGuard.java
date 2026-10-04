package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.camp.CampVegetation;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.core.WallBuilderAccess;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Opt-in guard around the existing native BuilderWorkGoal tick, never a builder replacement.
 * Public upstream 29d26e1 has no mutation event: every path is checked before
 * dispatching its synchronous tick. No solid clearing or FREE_AREA is enabled.
 */
public final class NativeConstructionGuard {
    private static final String KEY = "SiegeProtectedConstructionV1", STATUS = "SiegeConstructionPause",
            PAID = "SiegeConstructionCommissionPaid", PROTECTED_LINK = "SiegeProtectedAreaReceipt",
            PROTECTED_GENERATION = "SiegeProtectedLedgerGeneration";
    private static final Map<Entity, Snapshot> CACHE = new WeakHashMap<>();
    private static final Map<Entity, Tag> CACHE_RECORDS = new WeakHashMap<>();
    private record StageBinding(com.devfarinsky.siegeoverhaul.core.PerimeterProject.Stage stage,
                                Snapshot snapshot, Tag receipt, Object nativeRecipe) {}
    private static final Map<Entity, StageBinding> STAGE_BINDINGS = new WeakHashMap<>();
    private static final Set<String> STATES = Set.of("SELECT_WORK_AREA", "MOVE_TO_WORK_AREA", "PREPARE_FREE_AREA",
            "FREE_AREA", "PREPARE_BREAK_BLOCKS", "BREAK_BLOCKS", "PREPARE_PLACE_BLOCKS", "PLACE_BLOCKS",
            "PREPARE_PLACE_MULTIBLOCK", "PLACE_MULTIBLOCK", "DONE", "ERROR");
    private record Snapshot(AcceptedConstructionPlan plan, AcceptedConstructionReservation reservation,
                            Map<BlockPos, BlockState> before,
                            Set<Long> completed, Set<Long> cleared, UUID owner, UUID builder,
                            String coreKey, BlockPos corePos) {}

    private NativeConstructionGuard() {}

    /** All new commission callers must use the sealed protected native subtype. */
    public static String availabilityProblem() { return WorkersConstructionRuntime.problem(); }

    /** Read-only hiring/selection predicate; review is never silently cleared by cancellation or reload. */
    public static boolean needsInventoryReview(Mob builder) {
        return builder != null && builder.getPersistentData() != null
                && ProtectedBuilderHandMirror.reviewNeeded(builder.getPersistentData());
    }

    public static String inventoryReviewProblem(Mob builder) {
        return needsInventoryReview(builder) ? ProtectedBuilderHandMirror.reviewReason() : null;
    }

    /** Selection-only checks: no stack serialization, native setter, item use interruption or mutation. */
    public static String commissionProblem(Mob builder) {
        if (builder == null) return "Builder is unavailable.";
        String review = inventoryReviewProblem(builder);
        if (review != null) return review;
        try {
            if (ProtectedBuilderHandMirror.activeUse(builder))
                return "Builder is using an item; wait for it to finish before commissioning. No payment taken.";
            return ProtectedStorageAccess.runningProblem(builder);
        } catch (RuntimeException | LinkageError unavailable) {
            return "Builder item-use state cannot be verified; try again when it is idle. No payment taken.";
        }
    }

    public static boolean commissionPaid(Entity area) {
        return area != null && area.getPersistentData() != null && area.getPersistentData().getBoolean(PAID);
    }

    /** Actual protected cells, including jobs whose physical marker is unloaded. */
    public static boolean reserves(ServerLevel level, java.util.Collection<BlockPos> cells) {
        return level == null || ConstructionEditLedger.get(level).reserves(cells);
    }

    public static boolean hasReservation(ServerLevel level, UUID areaId) {
        return level != null && areaId != null && ConstructionEditLedger.get(level).contains(areaId);
    }

    /**
     * Call after startBlueprint, before assignment/payment. Pass the exact preflight solid + clearance
     * cells (at most 65,536), including all native targets. Reservations never authorize excavation.
     * A false result must abort the handoff. Missing older draft reservation recipes pause on reload.
     */
    public static boolean protect(ServerPlayer owner, Mob builder, Entity area, Collection<BlockPos> reservedCells) {
        return protect(owner, builder, area, reservedCells, null);
    }

    /** A stage consumes an existing whole-project reservation, never a fresh payment or baseline. */
    static boolean protectStage(ServerPlayer owner, Mob builder, Entity area,
                                com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (project == null || project.active() == null || area == null || !(area.level() instanceof ServerLevel level)) return false;
        try {
            var authoritative = PerimeterProjectAuthority.project(level, PerimeterProjectAuthority.read(area.getPersistentData()));
            if (!authoritative.check().equals(project.check())) return false;
            Set<BlockPos> reserved = new HashSet<>(); project.active().layout().reservation().forEach(pos -> reserved.add(BlockPos.of(pos)));
            return protect(owner, builder, area, reserved, authoritative);
        } catch (RuntimeException unavailable) { return pause(area, "Paused: the whole-perimeter stage cannot be authenticated"); }
    }

    private static boolean protect(ServerPlayer owner, Mob builder, Entity area, Collection<BlockPos> reservedCells,
                                   com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (owner == null || builder == null || area == null || !(area.level() instanceof ServerLevel level)
                || area.getPersistentData().contains(KEY)) return false;
        if (!(area instanceof ProtectedBuildArea))
            return pause(area, "Protected construction requires the sealed native marker type");
        String capabilityProblem = availabilityProblem();
        if (capabilityProblem != null) return pause(area, capabilityProblem);
        // Normal eating/use on an idle hire is temporary, not evidence of corrupt inventory.
        // Recheck before any receipt/ledger write or hand setter, and never charge or stop use.
        String commissionProblem = commissionProblem(builder);
        if (commissionProblem != null) return pause(area, commissionProblem);
        try {
            var ledger = ConstructionEditLedger.get(level);
            if (!ledger.canRetainHandLifecycle(builder.getPersistentData(), builder.getUUID()))
                return pause(area, "Paused: protected hand history is full or cannot be authenticated; no payment taken");
            if (!WallBuilderAccess.install(builder) || !ProtectedStorageAccess.install(builder))
                return pause(area, "Paused: native protection or storage persistence hook unavailable");
            var plan = AcceptedConstructionPlan.capture(area);
            var reservation = AcceptedConstructionReservation.capture(level, plan, reservedCells);
            if (project != null) {
                Map<BlockPos, BlockState> exact = new HashMap<>();
                project.active().layout().targets().keySet().forEach(pos -> exact.put(BlockPos.of(pos), project.targets().get(pos)));
                if (!plan.cells.equals(exact)) return pause(area, "Paused: native stage differs from the complete accepted plan");
                for (var entry : reservation.clearance.entrySet())
                    if (!entry.getValue().equals(project.clearanceBefore().get(entry.getKey().asLong())))
                        return pause(area, "Paused: a future section's original headroom changed");
            }
            var point = SiegeCore.point(owner.server, SiegeCore.key(owner));
            if (point == null || !owner.getUUID().equals(WorkersBridge.readOwner(area))
                    || !owner.getUUID().equals(WorkersBridge.readWorkerOwner(builder))) return false;
            Map<BlockPos, BlockState> before = new HashMap<>();
            for (BlockPos pos : plan.cells.keySet()) {
                if (!level.hasChunkAt(pos)) return pause(area, "Paused: the planned site is not fully loaded");
                BlockState state = level.getBlockState(pos);
                if (project != null && !state.equals(project.before().get(pos.asLong())))
                    return pause(area, "Paused: a future section changed after the whole-territory review");
                if (!initialCellSafe(state, plan.cells.get(pos)) || level.getBlockEntity(pos) != null)
                    return pause(area, "Paused: protected blocks or paired plants need manual clearance");
                before.put(pos, state);
            }
            Snapshot snapshot = new Snapshot(plan, reservation, Map.copyOf(before), new HashSet<>(), new HashSet<>(),
                    owner.getUUID(), builder.getUUID(), SiegeCore.key(owner), point.pos().immutable());
            // The unpaid authority check cannot read an entity snapshot until one
            // exists. Bind this local candidate before writing any handoff receipt
            // or letting the caller take the one project payment.
            if (project != null && !projectSnapshotMatches(project, plan, reservation, snapshot.before,
                    snapshot.owner, snapshot.builder, snapshot.coreKey, snapshot.corePos))
                return pause(area, "Paused: native stage geometry or recipe differs from the complete accepted plan");
            before.forEach((pos, state) -> { if (state.equals(plan.cells.get(pos))) snapshot.completed.add(pos.asLong()); });
            String problem = worldProblem(level, builder, area, snapshot, snapshot.plan.cells.keySet(), false);
            if (problem != null) return pause(area, problem);
            var workerData = builder.getPersistentData();
            if (workerData.hasUUID(PROTECTED_LINK)) {
                UUID previous = workerData.getUUID(PROTECTED_LINK);
                if (!ledger.retired(previous))
                    return pause(area, "Paused: the builder still has an active or unverified protected job");
                if (!retireBuilderAssociation(builder, previous))
                    return pause(area, "Paused: the builder's previous canceled job cannot be detached safely");
                ledger.acknowledgeRetirement(previous);
            }
            if (project == null) {
                if (PerimeterProjectAuthority.tracked(area)) return pause(area, "Paused: a project stage requires its original reservation");
                if (ledger.reserves(reservation.cells))
                    return pause(area, "Paused: another protected job reserves this footprint or headroom");
                if (!ledger.register(area.getUUID(), reservation.cells))
                    return pause(area, "Paused: protected-site ledger is full or unavailable");
            } else if (!ledger.matchesProjectLease(project)) {
                return pause(area, "Paused: the complete perimeter reservation or current stage differs");
            }
            if (project != null && !PerimeterProjectLink.bindLedgerGeneration(builder, ledger.generation()))
                return pause(area, "Paused: the whole-perimeter ledger identity differs");
            area.getPersistentData().put(KEY, save(snapshot));
            area.getPersistentData().putBoolean(PAID, false);
            builder.getPersistentData().putUUID(PROTECTED_LINK, area.getUUID());
            builder.getPersistentData().putUUID(PROTECTED_GENERATION, ledger.generation());
            CACHE.put(area, snapshot); CACHE_RECORDS.put(area, area.getPersistentData().get(KEY));
            // An idle worker may have split its mirror during an earlier unguarded reload.
            // Establish the same invariant now, inside the accepted protected handoff, before
            // protect can return success to assignment/payment. Failure stays unpaid for rollback.
            ProtectedBuilderHandMirror.arm(workerData);
            String handProblem = ProtectedBuilderHandMirror.restore(builder);
            if (handProblem != null) { pause(builder, handProblem); return pause(area, handProblem); }
            if (!ledger.retainHandLifecycle(workerData, builder.getUUID(), snapshot.owner, area.getUUID()))
                return pause(area, "Paused: protected hand provenance could not be retained; no payment taken");
            workerData.remove(STATUS);
            pause(area, "Paused: commission not completed");
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return pause(area, "Paused: native construction protection is unavailable");
        }
    }

    /** Call only after successful Treasury payment; retries never charge or consume materials. */
    public static boolean activate(Entity area) {
        if (!protectedArea(area)) return false;
        if (PerimeterProjectAuthority.tracked(area)) {
            try {
                if (!(area.level() instanceof ServerLevel level)
                        || !(level.getEntity(snapshot(area).builder) instanceof Mob builder)) return false;
                String project = PerimeterProjectAuthority.problem(level, builder, area, false, false);
                if (project != null) return pause(area, project);
            } catch (RuntimeException unavailable) { return pause(area, "Paused: project activation needs recovery review"); }
        }
        area.getPersistentData().putBoolean(PAID, true);
        area.getPersistentData().remove(STATUS);
        return true;
    }

    /**
     * Called before the living AI tick. Only an unavailable guard or unverified post-load hand mirror cancels
     * a living tick; a normally guarded pause leaves physics, food and sleep alone.
     * This also catches a second native builder discovering the protected area.
     */
    public static boolean beforeWorkerTick(Mob builder) {
        if (!WorkersBridge.isBuilder(builder) || !(builder.level() instanceof ServerLevel level)) return true;
        var data = builder.getPersistentData();
        var ledger = ConstructionEditLedger.get(level.getServer().overworld());
        boolean projectWorker = projectAssociation(builder, ledger);
        boolean handLifecycle = handLifecycleScope(builder, ledger);
        // Install the two narrow boundaries before allowing an active item-use
        // tick to finish while a post-load hand mirror is still pending.
        if (hasProtectedReceipt(builder) || projectWorker) {
            if (!ProtectedStorageAccess.install(builder) || !WallBuilderAccess.install(builder))
                return pauseStorage(builder, "Paused: native construction or inventory protection hook unavailable");
        }
        if (!handLifecycle && hasProtectedReceipt(builder) && !neverAcceptedCanceledProject(builder, ledger)
                && !ProtectedBuilderHandMirror.activeUse(builder)) {
            // Upgrade only while exact old job authority still exists. A normal active-use
            // state defers this read/alias proof; it is never stopped or marked corrupt.
            if (!retainHandLifecycle(builder, ledger)) return false;
            handLifecycle = true;
        }
        if (handLifecycle && !ProtectedBuilderHandLifecycle.matches(data, builder.getUUID(), ledger))
            return pause(builder, "Paused: protected builder hand provenance cannot be authenticated");
        // This is the LivingTick boundary before all native AI, including eating and tool switches.
        // A hand-only lifecycle survives all job cleanup, but never reserves a job or grants storage access.
        if ((handLifecycle || data.hasUUID(PROTECTED_LINK)) && (ProtectedBuilderHandMirror.pending(data)
                || ProtectedBuilderHandMirror.reviewNeeded(data))) {
            String handProblem = availabilityProblem();
            if (handProblem == null && !handLifecycle && !validHandReceipt(data, ledger) && !validRetirementHandReceipt(builder, ledger))
                handProblem = "Paused: protected builder inventory receipt cannot be verified";
            if (handProblem == null && ProtectedBuilderHandMirror.activeUse(builder)) {
                if (!hasProtectedReceipt(builder))
                    return pause(builder, "Paused: terminal builder hand verification during active item use is unsupported; use state is unchanged");
                pauseStorage(builder, "Waiting: finish the current item use before protected hand verification");
                return true; // Work/transfer guards below independently deny pending or reviewed hands.
            }
            if (handProblem == null) handProblem = ProtectedBuilderHandMirror.restore(builder);
            if (handProblem != null) {
                Entity related = data.hasUUID(PROTECTED_LINK) ? level.getServer().overworld().getEntity(data.getUUID(PROTECTED_LINK)) : null;
                pause(related, handProblem); return pause(builder, handProblem);
            }
            data.remove(STATUS);
        }
        if (handLifecycle && data.hasUUID(PROTECTED_LINK) && !projectWorker && ledger.retired(data.getUUID(PROTECTED_LINK))) {
            UUID receipt = data.getUUID(PROTECTED_LINK);
            if (!retireBuilderAssociation(builder, receipt)) return false;
            ledger.acknowledgeRetirement(receipt);
        }
        if (hasProtectedReceipt(builder)) {
            if (builder instanceof com.talhanation.workers.entities.BuilderEntity nativeBuilder) {
                String requests=ProtectedTransferCapacity.requestsProblem(nativeBuilder);
                if(requests!=null)return pauseStorage(builder,requests);
            }
        }
        if (projectWorker) {
            if (!PerimeterProjectLink.reserved(builder))
                return pauseStorage(builder, "Paused: the whole-perimeter builder identity needs recovery review");
            if (!NativePerimeterProjects.reconcileWorker(builder)) return false;
        }
        Entity area = currentArea(builder);
        if (!protectedArea(area)) return true;
        if (WallBuilderAccess.install(builder)) {
            if (area instanceof ProtectedBuildArea protectedArea && !protectedArea.nativeQueuesReady()
                    && prepareLoadedArea(area)) WallBuilderAccess.prepareProtectedHandoff(builder, area);
            return true;
        }
        return pause(area, "Paused: native protection hook unavailable");
    }

    static boolean hasProtectedReceipt(Mob builder) {
        return builder != null && builder.getPersistentData() != null
                && (builder.getPersistentData().hasUUID(PROTECTED_LINK) || PerimeterProjectLink.reserved(builder));
    }

    private static boolean handLifecycleScope(Mob builder, ConstructionEditLedger ledger) {
        return ProtectedBuilderHandLifecycle.selected(builder.getPersistentData()) || ledger.handLifecycle(builder.getUUID()) != null;
    }

    /** Narrow migration before old cleanup discards its proof; never reconstructs an ordinary/terminal legacy job. */
    private static boolean retainHandLifecycle(Mob builder, ConstructionEditLedger ledger) {
        var data = builder.getPersistentData();
        if (handLifecycleScope(builder, ledger))
            return ProtectedBuilderHandLifecycle.matches(data, builder.getUUID(), ledger)
                    || pause(builder, "Paused: protected builder hand provenance cannot be authenticated");
        if (!ledger.canRetainHandLifecycle(data, builder.getUUID()))
            return pause(builder, "Paused: protected builder hand history is full or unavailable; original job evidence is retained");
        if (ProtectedBuilderHandMirror.activeUse(builder))
            return pause(builder, "Waiting: finish current item use before preserving protected hand provenance");
        String runtime = availabilityProblem();
        if (runtime != null) return pause(builder, runtime);
        try {
            HandProvenance proof = handProvenance(builder, ledger);
            if (proof == null) return pause(builder, "Paused: original protected builder hand provenance needs recovery review; job evidence is retained");
            ProtectedBuilderHandMirror.arm(data);
            String problem = ProtectedBuilderHandMirror.restore(builder);
            if (problem != null) return pause(builder, problem);
            return ledger.retainHandLifecycle(data, builder.getUUID(), proof.owner(), proof.area());
        } catch (RuntimeException | LinkageError unavailable) {
            return pause(builder, "Paused: original protected builder hand provenance cannot be verified");
        }
    }

    private record HandProvenance(UUID owner, UUID area) {}

    /** A canceled unpaid preparation that never reached the protected handoff has no hand lifecycle to retain. */
    private static boolean neverAcceptedCanceledProject(Mob builder, ConstructionEditLedger ledger) {
        var data=builder.getPersistentData();
        if (data.contains(PROTECTED_LINK) || data.contains(PROTECTED_GENERATION) || handLifecycleScope(builder,ledger)
                || !PerimeterProjectLink.reserved(builder) || !(builder.level() instanceof ServerLevel level)) return false;
        try {
            if (PerimeterProjectLink.ledgerGeneration(data)!=null) return false;
            var link=PerimeterProjectLink.read(data);
            var project=PerimeterProjectAuthority.snapshot(level.getServer().overworld(),link.core()).get(link.id());
            return project!=null && project.state()==com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.CANCELED
                    && project.payment()==null && project.receipts().isEmpty() && project.activeStage()==0
                    && PerimeterProjectLink.matches(builder,project) && ledger.matchesProjectIdentity(project);
        } catch(RuntimeException | LinkageError unverified) { return false; }
    }

    private static HandProvenance handProvenance(Mob builder, ConstructionEditLedger ledger) {
        if (!(builder.level() instanceof ServerLevel level)) return null;
        var data = builder.getPersistentData(); var overworld = level.getServer().overworld();
        if (validHandReceipt(data, ledger)) {
            Entity area = overworld.getEntity(data.getUUID(PROTECTED_LINK));
            if (area instanceof ProtectedBuildArea protectedArea) {
                Snapshot accepted = snapshot(area);
                if (builder.getUUID().equals(accepted.builder) && accepted.builder.equals(protectedArea.reservedBuilderId())
                        && accepted.owner.equals(WorkersBridge.readOwner(area))
                        && ledger.matches(area.getUUID(), accepted.reservation.cells))
                    return new HandProvenance(accepted.owner, area.getUUID());
            }
        }
        if (!PerimeterProjectLink.reserved(builder)) return null;
        var link = PerimeterProjectLink.read(data); var authority = PerimeterProjectAuthority.snapshot(overworld, link.core());
        if (validRetirementHandReceipt(data, builder.getUUID(), ledger, authority)) {
            var project = authority.get(link.id()); var terminal = authority.terminal(link.id());
            return new HandProvenance(project != null ? project.header().owner() : terminal.owner(), data.getUUID(PROTECTED_LINK));
        }
        UUID generation = PerimeterProjectLink.ledgerGeneration(data);
        if (!ledger.sameGeneration(generation) || data.contains(PROTECTED_LINK) || data.contains(PROTECTED_GENERATION)) return null;
        var project = authority.get(link.id());
        if (project != null && PerimeterProjectLink.matches(builder, project) && ledger.matchesProjectIdentity(project)
                && !project.receipts().isEmpty()) {
            var previous = project.receipts().get(project.receipts().size() - 1);
            if (ledger.retired(previous.areaId())) return new HandProvenance(project.header().owner(), previous.areaId());
        }
        var terminal = authority.terminal(link.id());
        if (terminal != null && terminal.builder().equals(builder.getUUID()) && terminal.generation() == link.generation()
                && terminal.manifestHash().equals(link.hash()) && terminal.coreKey().equals(link.core())
                && terminal.cleanup().ledgerGeneration().equals(generation))
            return new HandProvenance(terminal.owner(), terminal.stages().get(0).areaId());
        return null;
    }

    static boolean projectAssociation(Mob builder, ConstructionEditLedger ledger) {
        if (builder == null || builder.getPersistentData() == null) return false;
        if (PerimeterProjectLink.reserved(builder)) return true;
        var data=builder.getPersistentData();
        if (ledger!=null && data.hasUUID(PROTECTED_LINK) && ledger.projectForArea(data.getUUID(PROTECTED_LINK)) != null) return true;
        Entity area=currentArea(builder);
        return area!=null && area.getPersistentData()!=null && PerimeterProjectAuthority.tracked(area);
    }

    static String storageProblem(Mob builder, Set<BlockPos> containers) {
        if (!(builder.level() instanceof ServerLevel level) || !hasProtectedReceipt(builder))
            return "Paused: protected storage context is unavailable";
        String runtime = availabilityProblem(); if (runtime != null) return runtime;
        if (needsInventoryReview(builder)) return inventoryReviewProblem(builder);
        var data = builder.getPersistentData();
        if (ProtectedBuilderHandMirror.pending(data)) return "Waiting: protected hand verification must finish before resupply";
        if (!validHandReceipt(data, ConstructionEditLedger.get(level.getServer().overworld())))
            return "Paused: protected storage receipt is unavailable";
        Entity area = level.getServer().overworld().getEntity(data.getUUID(PROTECTED_LINK));
        if (!(area instanceof ProtectedBuildArea) || !area.isAlive())
            return "Paused: load the protected construction marker before resupply";
        if (!commissionPaid(area)) return "Paused: commission not completed";
        String project = PerimeterProjectAuthority.problem(level.getServer().overworld(), builder, area, false, false);
        if (project != null) return project;
        try {
            Snapshot snapshot = snapshot(area);
            if (!ConstructionEditLedger.get(level.getServer().overworld()).matches(area.getUUID(), snapshot.reservation.cells))
                return "Paused: protected storage reservation history differs from the accepted job";
            if (!snapshot.builder.equals(builder.getUUID())) return "Paused: storage worker assignment changed";
            if (!snapshot.owner.equals(WorkersBridge.readOwner(area))) return "Paused: protected inventory owner changed";
            return NativeInventoryAuthority.problem(level, builder, snapshot.owner,
                    snapshot.coreKey, snapshot.corePos, containers);
        } catch (RuntimeException | LinkageError unavailable) {
            return "Paused: protected storage contract cannot be verified";
        }
    }

    static boolean sharedStorageFactionMatches(Mob builder) {
        if (!(builder.level() instanceof ServerLevel level) || !builder.getPersistentData().hasUUID(PROTECTED_LINK)) return false;
        Entity area=level.getServer().overworld().getEntity(builder.getPersistentData().getUUID(PROTECTED_LINK));
        if (!(area instanceof ProtectedBuildArea)) return false;
        try {
            String key=snapshot(area).coreKey;
            var team=level.getScoreboard().getPlayersTeam(builder.getScoreboardName());
            return key.startsWith("team:") && team!=null && key.substring(5).equals(team.getName());
        } catch(RuntimeException unavailable){return false;}
    }

    static boolean pauseStorage(Mob builder, String reason) {
        if (builder.level() instanceof ServerLevel level && builder.getPersistentData().hasUUID(PROTECTED_LINK))
            pause(level.getServer().overworld().getEntity(builder.getPersistentData().getUUID(PROTECTED_LINK)), reason);
        return pause(builder, reason);
    }

    static boolean validHandReceipt(CompoundTag data, ConstructionEditLedger ledger) {
        return data.hasUUID(PROTECTED_LINK) && data.hasUUID(PROTECTED_GENERATION)
                && ledger.sameGeneration(data.getUUID(PROTECTED_GENERATION))
                && ledger.completeReservation(data.getUUID(PROTECTED_LINK));
    }

    private static boolean validRetirementHandReceipt(Mob builder, ConstructionEditLedger ledger) {
        if (!(builder.level() instanceof ServerLevel level)) return false;
        try {
            var link=PerimeterProjectLink.read(builder.getPersistentData());
            return validRetirementHandReceipt(builder.getPersistentData(),builder.getUUID(),ledger,
                    PerimeterProjectAuthority.snapshot(level.getServer().overworld(),link.core()));
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    /** Cleanup-only read proof. Never substitute this for active storage or world-mutation authority. */
    static boolean validRetirementHandReceipt(CompoundTag data, UUID builder, ConstructionEditLedger ledger,
                                             PerimeterProjectStore.Snapshot authority) {
        try {
            if (data==null || builder==null || ledger==null || authority==null || !data.hasUUID(PROTECTED_LINK)
                    || !data.hasUUID(PROTECTED_GENERATION) || !ledger.sameGeneration(data.getUUID(PROTECTED_GENERATION))) return false;
            var link=PerimeterProjectLink.read(data);UUID area=data.getUUID(PROTECTED_LINK);
            var project=authority.get(link.id());
            if (project!=null) {
                var h=project.header();
                if (!builder.equals(h.builder()) || h.generation()!=link.generation() || !project.manifestHash().equals(link.hash())
                        || !h.coreKey().equals(link.core()) || !ledger.matchesProjectIdentity(project) || !ledger.retired(area)) return false;
                return project.receipts().stream().anyMatch(receipt -> receipt.areaId().equals(area)
                        && receipt.equals(new com.devfarinsky.siegeoverhaul.core.PerimeterProject.StageReceipt(h.projectId(),h.generation(),
                        project.manifestHash(),receipt.stageIndex(),project.stages().get(receipt.stageIndex()).areaId(),
                        project.stages().get(receipt.stageIndex()).digest(),project.stages().get(receipt.stageIndex()).layout().targets().size())));
            }
            var terminal=authority.terminal(link.id());
            return terminal!=null && builder.equals(terminal.builder()) && terminal.generation()==link.generation()
                    && terminal.manifestHash().equals(link.hash()) && terminal.coreKey().equals(link.core())
                    && terminal.cleanup().ledgerGeneration().equals(data.getUUID(PROTECTED_GENERATION))
                    && terminal.stages().stream().anyMatch(stage -> stage.areaId().equals(area));
        } catch (RuntimeException unavailable) { return false; }
    }

    /** Closes existing protected inventory work without resupplying or discarding dormant requests. */
    static boolean readyForRetirement(Mob builder) {
        try {
            if (hasProtectedReceipt(builder)) {
                if (!(builder.level() instanceof ServerLevel level)) return false;
                var ledger=ConstructionEditLedger.get(level.getServer().overworld());
                if (!neverAcceptedCanceledProject(builder,ledger) && !retainHandLifecycle(builder,ledger)) return false;
            }
            if (ProtectedBuilderHandMirror.activeUse(builder))
                return pauseStorage(builder,"Waiting: finish the current item use before native cleanup");
            if (ProtectedBuilderHandMirror.pending(builder.getPersistentData()) || needsInventoryReview(builder))
                return pauseStorage(builder,"Paused: protected hand verification or inventory review must finish before native cleanup");
            if (!ProtectedStorageAccess.drainCleanup(builder))
                return pauseStorage(builder,"Waiting: load the original native inventory cleanup sources; no new supplies are taken");
            return true;
        } catch (RuntimeException | LinkageError unavailable) {
            return pauseStorage(builder,"Paused: protected native inventory cleanup needs recovery review");
        }
    }

    /** Works for transferred builders too; only exact old references/metadata can be cleared. */
    static boolean retireBuilderAssociation(Mob builder, UUID areaId) {
        var data = builder.getPersistentData();
        if (data.hasUUID(PROTECTED_LINK) && areaId.equals(data.getUUID(PROTECTED_LINK))) {
            Entity current=currentArea(builder);
            if(current!=null && !areaId.equals(current.getUUID()))
                return pauseStorage(builder,"Paused: another native assignment must finish before old construction cleanup");
            var ledger=builder.level() instanceof ServerLevel level?ConstructionEditLedger.get(level.getServer().overworld()):null;
            if (ledger == null || !retainHandLifecycle(builder, ledger)) return false;
            if (!readyForRetirement(builder)) return false;
            // Upgrade an older active child receipt before clearing it; the whole-project identity must
            // retain the same ledger generation throughout the gap before the next native section.
            if (PerimeterProjectLink.reserved(builder) && (ledger==null || !data.hasUUID(PROTECTED_GENERATION)
                    || !ledger.sameGeneration(data.getUUID(PROTECTED_GENERATION))
                    || !PerimeterProjectLink.bindLedgerGeneration(builder,data.getUUID(PROTECTED_GENERATION))))
                return pauseStorage(builder,"Paused: whole-perimeter ledger identity needs recovery review");
        }
        if (!WorkersBridge.detachBuildAreaReference(builder, areaId)) return false;
        com.devfarinsky.siegeoverhaul.core.PlayerFortificationJobs.unlink(builder, areaId);
        if (data.hasUUID(PROTECTED_LINK) && areaId.equals(data.getUUID(PROTECTED_LINK))) {
            data.remove(PROTECTED_LINK); data.remove(PROTECTED_GENERATION);
        }
        return true;
    }

    static UUID protectedReceiptArea(Mob builder) {
        return builder.getPersistentData().hasUUID(PROTECTED_LINK)?builder.getPersistentData().getUUID(PROTECTED_LINK):null;
    }
    /** Exact optional child receipt for cleanup; a whole-project selector is never an active child lease. */
    static boolean retirementReceiptMatches(CompoundTag data, java.util.Collection<UUID> stages, UUID ledgerGeneration) {
        if (data.contains(PerimeterProjectLink.KEY)) {
            try {
                UUID wholeGeneration=PerimeterProjectLink.ledgerGeneration(data);
                if(wholeGeneration!=null&&!wholeGeneration.equals(ledgerGeneration))return false;
            }catch(RuntimeException malformed){return false;}
        }
        if (!data.contains(PROTECTED_LINK)) return !data.contains(PROTECTED_GENERATION);
        return data.hasUUID(PROTECTED_LINK) && data.hasUUID(PROTECTED_GENERATION)
                && ledgerGeneration.equals(data.getUUID(PROTECTED_GENERATION)) && stages.contains(data.getUUID(PROTECTED_LINK));
    }

    /** Called directly at the wrapper's native tick boundary, including after resupply/reload. */
    public static boolean beforeNativeTick(Mob builder, Goal nativeGoal) {
        if(builder.getPersistentData()!=null && ProtectedStorageAccess.recoveryBlocked(builder))
            return pauseStorage(builder,"Paused: interrupted native inventory cleanup needs review before construction");
        Entity area = currentArea(builder);
        if ((hasProtectedReceipt(builder) || builder.getPersistentData() != null && ProtectedBuilderHandLifecycle.selected(builder.getPersistentData()))
                && (ProtectedBuilderHandMirror.pending(builder.getPersistentData()) || needsInventoryReview(builder)))
            return pauseStorage(builder,"Paused: protected hand verification must finish before native construction");
        if (builder.getPersistentData()!=null && PerimeterProjectLink.reserved(builder) && !(area instanceof ProtectedBuildArea))
            return pauseStorage(builder,"Waiting: the whole-perimeter controller must assign its exact native section");
        if (!protectedArea(area)) return true;
        if (!(builder.level() instanceof ServerLevel level)) return false;
        if (!area.getPersistentData().getBoolean(PAID)) return pause(area, "Paused: commission not completed");
        if (hasProtectedReceipt(builder) && !retainHandLifecycle(builder, ConstructionEditLedger.get(level.getServer().overworld()))) return false;
        String capabilityProblem = availabilityProblem();
        if (capabilityProblem != null) return pause(area, capabilityProblem);
        if (area instanceof ProtectedBuildArea protectedArea && !protectedArea.nativeQueuesReady())
            return pause(area, "Paused: load the complete construction footprint to resume");
        try {
            Snapshot snapshot = snapshot(area);
            if (!snapshot.builder.equals(builder.getUUID())) return pause(area, "Paused: another builder selected this reserved job");
            if (builder.tickCount % 5 == 0 && !snapshot.plan.matches(area))
                return pause(area, "Paused: accepted blueprint or marker position changed");
            if (!Boolean.FALSE.equals(AcceptedConstructionPlan.call(area, "getFreeArea")))
                return pause(area, "Paused: whole-area clearing is not permitted");
            Object state = nativeGoal.getClass().getField("state").get(nativeGoal);
            if (state != null && (!(state instanceof Enum<?> e) || !STATES.contains(e.name())))
                return pause(area, "Paused: unsupported native construction state");
            if (state instanceof Enum<?> e && (e.name().equals("FREE_AREA") || e.name().equals("PREPARE_FREE_AREA")))
                return pause(area, "Paused: whole-area clearing is not permitted");
            String projectProblem = PerimeterProjectAuthority.problem(level, builder, area, false,
                    state instanceof Enum<?> e && e.name().equals("DONE"));
            if (projectProblem != null) return pause(area, projectProblem);
            // The audited native goal can mutate only every fifth tick. Other
            // ticks keep its look/availability updates without an O(plan) scan.
            if (builder.tickCount % 5 != 0) return true;
            String problem = nativeStacksProblem(area, nativeGoal, snapshot.plan);
            Set<BlockPos> candidates = state instanceof Enum<?> e && e.name().equals("DONE")
                    ? snapshot.plan.cells.keySet() : mutationCells(nativeGoal, state);
            if (problem == null) problem = worldProblem(level, builder, area, snapshot, candidates, true);
            if (problem == null && state instanceof Enum<?> e && e.name().equals("PREPARE_BREAK_BLOCKS")
                    && !scanChunksLoaded(level, snapshot.plan))
                problem = "Paused: native scan envelope contains unloaded terrain";
            // Native BREAK_BLOCKS ignores the desired state: a stale scan target
            // that has become the finished wall must never be mined.
            if (problem == null && state instanceof Enum<?> e && e.name().equals("BREAK_BLOCKS")) {
                Object target = nativeGoal.getClass().getField("blockPos").get(nativeGoal);
                if (target instanceof BlockPos pos && !level.getBlockState(pos).isAir()
                        && (!clearablePlant(level.getBlockState(pos))
                            || !level.getBlockState(pos).equals(snapshot.before.get(pos))
                            || snapshot.completed.contains(pos.asLong()) || snapshot.cleared.contains(pos.asLong())))
                    problem = "Paused: a mining target changed after the native scan";
            }
            if (problem == null && state instanceof Enum<?> e && e.name().equals("DONE")
                    && snapshot.plan.cells.entrySet().stream().anyMatch(cell -> !level.getBlockState(cell.getKey()).equals(cell.getValue())))
                problem = "Paused: native job ended before every accepted block was placed";
            if (problem != null) return pause(area, problem);
            if (state instanceof Enum<?> e && e.name().equals("DONE") && area instanceof ProtectedBuildArea protectedArea) {
                if (PerimeterProjectAuthority.tracked(area)) {
                    if (!protectedArea.stackToPlace.isEmpty() || !protectedArea.stackToPlaceMultiBlock.isEmpty()
                            || !protectedArea.stackToBreak.isEmpty() || !protectedArea.stackToFree.isEmpty()
                            || !nativeGoalQueuesComplete(nativeGoal))
                        return pause(area, "Paused: the native stage still has unfinished queues");
                    // World/queue proof above precedes the durable receipt and native DONE callback.
                    if (!PerimeterProjectAuthority.verified(area)) return pause(area, "Paused: stage completion receipt could not be saved");
                }
                protectedArea.verifyCompletion();
            }
            area.getPersistentData().remove(STATUS);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return pause(area, "Paused: native construction state cannot be verified");
        }
    }

    /** Record exact native progress so later player removal is never treated as fresh empty terrain. */
    public static void afterNativeTick(Mob builder, Entity priorArea, Set<BlockPos> mutationCells) {
        if (!protectedArea(priorArea) || !(builder.level() instanceof ServerLevel level)) return;
        try {
            Snapshot snapshot = snapshot(priorArea);
            boolean changed = false;
            for (BlockPos pos : mutationCells) {
                if (!level.hasChunkAt(pos) || !snapshot.plan.cells.containsKey(pos)) continue;
                BlockState now = level.getBlockState(pos);
                if (now.equals(snapshot.plan.cells.get(pos))) changed |= snapshot.completed.add(pos.asLong());
                else if (now.isAir() && !snapshot.before.get(pos).isAir()) changed |= snapshot.cleared.add(pos.asLong());
            }
            if (priorArea instanceof ProtectedBuildArea protectedArea && protectedArea.isDone() && !PerimeterProjectAuthority.tracked(priorArea))
                retireBuilderAssociation(builder, priorArea.getUUID());
            if (changed) {
                CompoundTag tag = priorArea.getPersistentData().getCompound(KEY);
                tag.putLongArray("Completed", snapshot.completed.stream().mapToLong(Long::longValue).toArray());
                tag.putLongArray("Cleared", snapshot.cleared.stream().mapToLong(Long::longValue).toArray());
            }
        } catch (RuntimeException unavailable) { pause(priorArea, "Paused: native progress cannot be verified"); }
    }

    public static String status(Entity area) {
        if (area == null) return "";
        String value = area.getPersistentData().getString(STATUS);
        return value.substring(0, Math.min(160, value.length()));
    }

    public static Entity currentArea(Mob builder) {
        try {
            Object value = builder.getClass().getField("currentBuildArea").get(builder);
            return value instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static boolean protectedArea(Entity area) {
        return area instanceof ProtectedBuildArea || area != null && area.getPersistentData() != null
                && area.getPersistentData().contains(KEY);
    }

    private static boolean pause(Entity area, String reason) {
        if (area != null && area.getPersistentData() != null) area.getPersistentData().putString(STATUS, reason);
        return false;
    }

    static boolean initialCellSafe(BlockState current, BlockState target) {
        return current != null && target != null && !current.hasBlockEntity() && current.getFluidState().isEmpty()
                && (current.isAir() || current.equals(target) || clearablePlant(current));
    }

    private static boolean clearablePlant(BlockState state) {
        // Paired plants can destroy another cell through neighbor updates. No such
        // indirect clearing is authorized by a one-cell construction snapshot.
        return CampVegetation.plant(state) && !(state.getBlock() instanceof DoublePlantBlock)
                && !state.is(Blocks.WITHER_ROSE)
                && "minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    static boolean currentCellSafe(BlockState initial, BlockState target, BlockState current,
                                   boolean completed, boolean cleared) {
        if (initial == null || !initialCellSafe(current, target)) return false;
        if (completed) return current.equals(target);
        if (current.equals(target) || current.isAir()) return true;
        return !cleared && clearablePlant(initial) && current.equals(initial);
    }

    private static String worldProblem(ServerLevel level, Mob builder, Entity area, Snapshot snapshot,
                                       Set<BlockPos> candidates, boolean requireLedger) {
        String project = PerimeterProjectAuthority.problem(level, builder, area, !requireLedger, true);
        if (project != null) return project;
        String permissions = NativeConstructionPolicy.problem(level, builder, area, snapshot.owner,
                snapshot.coreKey, snapshot.corePos, snapshot.reservation.cells);
        if (permissions != null) return permissions;
        String clearance = snapshot.reservation.problem(level);
        if (clearance != null) return clearance;
        if (requireLedger) {
            var ledger = ConstructionEditLedger.get(level);
            if (!ledger.matches(area.getUUID(), snapshot.reservation.cells)) return "Paused: protected-site history is unavailable";
            if (ledger.edited(area.getUUID())) return "Paused: this site was edited; commission a new reviewed plan";
        }
        for (BlockPos pos : candidates) {
            BlockState target = snapshot.plan.cells.get(pos);
            if (target == null) return "Paused: native target is outside the accepted plan";
            BlockState current = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null || !currentCellSafe(snapshot.before.get(pos), target, current,
                    snapshot.completed.contains(pos.asLong()), snapshot.cleared.contains(pos.asLong())))
                return "Paused: existing or changed blocks are protected at " + pos.toShortString();
            if (!current.equals(target)) {
                String neighborhood = neighborhoodProblem(level, pos);
                if (neighborhood != null) return neighborhood;
            }
            if (!current.equals(target) && !level.getEntities((Entity) null, new AABB(pos),
                    entity -> blocksPlacement(entity) && entity != area).isEmpty())
                return "Paused: move entities out of the planned blocks";
        }
        return null;
    }

    static String neighborhoodProblem(ServerLevel level, BlockPos target) {
        // Conductors can query a second neighbor ring while resolving power.
        // Validate that bounded envelope before any state/signal read.
        java.util.List<BlockPos> neighbors = new java.util.ArrayList<>(24);
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
            if (distance == 0 || distance > 2) continue;
            BlockPos pos = target.offset(x, y, z);
            if (!level.hasChunkAt(pos)) return "Paused: placement neighbors must be loaded";
            neighbors.add(pos);
        }
        for (BlockPos neighbor : neighbors) {
            if (!stableNeighbor(level.getBlockState(neighbor)) || level.getBlockEntity(neighbor) != null)
                return "Paused: a reactive or protected neighboring block needs manual review";
        }
        if (level.hasNeighborSignal(target)) return "Paused: powered construction sites need manual review";
        return null;
    }

    static boolean stableNeighbor(BlockState state) {
        if (state == null || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (state.isAir()) return true;
        // Closed vanilla allowlist. No material tag or generic modded block is
        // accepted as proof that neighbor updates are harmless.
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.BEDROCK) || clearablePlant(state);
    }

    static boolean blocksPlacement(Entity entity) {
        return entity.isAlive() && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)
                && !(entity instanceof net.minecraft.world.entity.ExperienceOrb);
    }

    /** The public source mutates at most one primary full-block cell in a tick. */
    public static Set<BlockPos> mutationCells(Goal nativeGoal) {
        try { return mutationCells(nativeGoal, nativeGoal.getClass().getField("state").get(nativeGoal)); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return Set.of(); }
    }

    private static Set<BlockPos> mutationCells(Object nativeGoal, Object state) throws ReflectiveOperationException {
        if (!(state instanceof Enum<?> e) || (!e.name().equals("BREAK_BLOCKS") && !e.name().equals("PLACE_BLOCKS")))
            return Set.of();
        Object target = nativeGoal.getClass().getField("blockPos").get(nativeGoal);
        if (target instanceof BlockPos pos) return Set.of(pos);
        if (e.name().equals("PLACE_BLOCKS")) {
            Object queue = nativeGoal.getClass().getField("stackToPlace").get(nativeGoal);
            if (queue instanceof java.util.Stack<?> stack && !stack.isEmpty()) {
                if (!(stack.peek() instanceof BlockPos pos)) throw new IllegalArgumentException("Unknown target");
                return Set.of(pos);
            }
        }
        return Set.of();
    }

    private static boolean scanChunksLoaded(ServerLevel level, AcceptedConstructionPlan plan) {
        BlockPos end = plan.origin.relative(plan.facing, plan.depth - 1)
                .relative(plan.facing.getClockWise(), plan.width - 1);
        for (int x = Math.min(plan.origin.getX(), end.getX()) >> 4; x <= Math.max(plan.origin.getX(), end.getX()) >> 4; x++)
            for (int z = Math.min(plan.origin.getZ(), end.getZ()) >> 4; z <= Math.max(plan.origin.getZ(), end.getZ()) >> 4; z++)
                if (!level.hasChunkAt(new BlockPos(x << 4, plan.origin.getY(), z << 4))) return false;
        return true;
    }

    /** Check every native target source, not just its first break scan. */
    static String nativeStacksProblem(Object area, Object nativeGoal, AcceptedConstructionPlan plan)
            throws ReflectiveOperationException {
        for (String name : new String[]{"stackToPlace", "stackToPlaceMultiBlock"}) {
            Object value = area.getClass().getField(name).get(area);
            if (!(value instanceof Collection<?> cells) || cells.size() > plan.cells.size())
                return "Paused: unsupported native placement queue";
            // Current approved full-block templates must never acquire a secondary write path.
            if (name.equals("stackToPlaceMultiBlock") && !cells.isEmpty()) return "Paused: multipart placement is unsupported";
            for (Object cell : cells) {
                Object pos = AcceptedConstructionPlan.call(cell, "getPos");
                Object state = AcceptedConstructionPlan.call(cell, "getState");
                if (!(pos instanceof BlockPos p) || !state.equals(plan.cells.get(p)))
                    return "Paused: native placement queue changed";
            }
        }
        for (String name : new String[]{"stackToBreak", "stackToFree"}) {
            Object value = area.getClass().getField(name).get(area);
            if (!(value instanceof Collection<?> cells) || cells.size() > plan.cells.size())
                return "Paused: unsupported native clearing queue";
            if (name.equals("stackToFree") && !cells.isEmpty()) return "Paused: whole-area clearing is not permitted";
            for (Object cell : cells) if (!(cell instanceof BlockPos pos) || !plan.cells.containsKey(pos))
                return "Paused: native clearing queue escaped the accepted plan";
        }
        for (String name : new String[]{"stackToPlace", "stackToBreak", "stackToFree"}) {
            Object value = nativeGoal.getClass().getField(name).get(nativeGoal);
            if (value == null) continue;
            if (!(value instanceof java.util.Stack<?> cells) || cells.size() > plan.cells.size())
                return "Paused: unsupported native job queue";
            if (name.equals("stackToFree") && !cells.isEmpty()) return "Paused: whole-area clearing is not permitted";
            for (Object cell : cells) if (!(cell instanceof BlockPos pos) || !plan.cells.containsKey(pos))
                return "Paused: native job target escaped the accepted plan";
        }
        Object target = nativeGoal.getClass().getField("blockPos").get(nativeGoal);
        if (target != null && (!(target instanceof BlockPos pos) || !plan.cells.containsKey(pos)))
            return "Paused: native job target escaped the accepted plan";
        return null;
    }

    /** DONE cannot retire a stage while an independently stored native goal still has work. */
    static boolean nativeGoalQueuesComplete(Object nativeGoal) throws ReflectiveOperationException {
        for (String name : new String[]{"stackToPlace", "stackToBreak", "stackToFree"}) {
            Object queue = nativeGoal.getClass().getField(name).get(nativeGoal);
            if (queue != null && (!(queue instanceof java.util.Stack<?> stack) || !stack.isEmpty())) return false;
        }
        return true;
    }

    static boolean hasAreaSnapshot(Entity area) { return area != null && area.getPersistentData().contains(KEY); }

    /** Snapshot identity is cached only after complete immutable geometry/context equality. */
    static boolean matchesProjectSnapshot(Entity area, com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (!(area instanceof ProtectedBuildArea protectedArea) || project == null || project.active() == null) return false;
        try {
            Snapshot snapshot = snapshot(area);
            var stage = project.active(); var layout = stage.layout();
            if (!layout.origin().equals(protectedArea.getOriginPos()) || protectedArea.getFacing() != Direction.SOUTH
                    || layout.width() != protectedArea.getWidthSize() || layout.depth() != protectedArea.getDepthSize()
                    || layout.height() != protectedArea.getHeightSize()) return false;
            Tag record = area.getPersistentData().get(KEY); Object nativeRecipe = protectedArea.nativeRecipeIdentity();
            StageBinding cached = STAGE_BINDINGS.get(area);
            if (cached != null && cached.stage() == stage && cached.snapshot() == snapshot
                    && cached.receipt() == record && cached.nativeRecipe() == nativeRecipe) return true;
            if (!projectSnapshotMatches(project, snapshot.plan, snapshot.reservation, snapshot.before,
                    snapshot.owner, snapshot.builder, snapshot.coreKey, snapshot.corePos) || !snapshot.plan.matches(area)) return false;
            STAGE_BINDINGS.put(area, new StageBinding(stage, snapshot, record, nativeRecipe));
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    static boolean projectSnapshotMatches(com.devfarinsky.siegeoverhaul.core.PerimeterProject project,
                                          AcceptedConstructionPlan plan, AcceptedConstructionReservation reservation,
                                          Map<BlockPos, BlockState> before, UUID owner, UUID builder,
                                          String coreKey, BlockPos corePos) {
        if (project == null || project.active() == null) return false;
        var stage = project.active().layout(); var header = project.header();
        if (!header.owner().equals(owner) || !header.builder().equals(builder) || !header.coreKey().equals(coreKey)
                || !header.originalCore().equals(corePos) || !stage.origin().equals(plan.origin)
                || plan.facing != Direction.SOUTH || stage.width() != plan.width || stage.depth() != plan.depth
                || stage.height() != plan.height || before.size() != stage.targets().size()
                || plan.cells.size() != stage.targets().size() || reservation.cells.size() != stage.reservation().size()
                || reservation.clearance.size() != stage.clearance().size()) return false;
        for (long cell : stage.targets().keySet()) {
            BlockPos pos = BlockPos.of(cell);
            if (!project.targets().get(cell).equals(plan.cells.get(pos)) || !project.before().get(cell).equals(before.get(pos))) return false;
        }
        for (long cell : stage.reservation()) if (!reservation.cells.contains(BlockPos.of(cell))) return false;
        for (long cell : stage.clearance())
            if (!project.clearanceBefore().get(cell).equals(reservation.clearance.get(BlockPos.of(cell)))) return false;
        // The stage uses the existing exact Workers converter. Unknown recipe/NBT paths are not adopted.
        return plan.save().getCompound("Structure").equals(com.devfarinsky.siegeoverhaul.core.TerritoryFortification
                .blueprint(stage.targets(), stage.min(), stage.max()));
    }

    public static void serverStopped(net.minecraft.server.MinecraftServer server) {
        ProtectedStorageAccess.drainOnShutdown(server);
        CACHE.clear(); CACHE_RECORDS.clear(); STAGE_BINDINGS.clear(); PerimeterProjectAuthority.stopped(server);
    }

    private static CompoundTag save(Snapshot snapshot) {
        CompoundTag tag = snapshot.plan.save();
        tag.putUUID("Owner", snapshot.owner); tag.putUUID("Builder", snapshot.builder);
        tag.putString("CoreKey", snapshot.coreKey); tag.putLong("CorePos", snapshot.corePos.asLong());
        ListTag initial = new ListTag();
        snapshot.before.forEach((pos, state) -> {
            CompoundTag cell = new CompoundTag(); cell.putLong("Pos", pos.asLong());
            cell.put("State", NbtUtils.writeBlockState(state)); initial.add(cell);
        });
        tag.put("Before", initial);
        tag.put("Reservation", snapshot.reservation.save());
        tag.putLongArray("Completed", snapshot.completed.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("Cleared", snapshot.cleared.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static Snapshot snapshot(Entity area) {
        Snapshot cached = CACHE.get(area);
        if (cached != null && CACHE_RECORDS.get(area) == area.getPersistentData().get(KEY)) return cached;
        CACHE.remove(area); CACHE_RECORDS.remove(area); STAGE_BINDINGS.remove(area);
        CompoundTag tag = area.getPersistentData().getCompound(KEY);
        var plan = AcceptedConstructionPlan.load(tag);
        var reservation = AcceptedConstructionReservation.load(plan, tag.getCompound("Reservation"));
        if (!tag.hasUUID("Owner") || !tag.hasUUID("Builder") || tag.getString("CoreKey").isBlank()
                || !tag.contains("Completed", Tag.TAG_LONG_ARRAY) || !tag.contains("Cleared", Tag.TAG_LONG_ARRAY))
            throw new IllegalArgumentException("Incomplete protection context");
        Map<BlockPos, BlockState> before = new HashMap<>();
        ListTag list = tag.getList("Before", Tag.TAG_COMPOUND);
        if (list.size() != plan.cells.size()) throw new IllegalArgumentException("Incomplete initial state");
        for (Tag entry : list) {
            CompoundTag cell = (CompoundTag) entry;
            BlockPos pos = BlockPos.of(cell.getLong("Pos"));
            BlockState state = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                    cell.getCompound("State"));
            if (!NbtUtils.writeBlockState(state).equals(cell.getCompound("State"))
                    || !initialCellSafe(state, plan.cells.get(pos)) || before.putIfAbsent(pos, state) != null)
                throw new IllegalArgumentException("Invalid initial state");
        }
        Set<Long> completed = readPositions(tag.getLongArray("Completed"), plan),
                cleared = readPositions(tag.getLongArray("Cleared"), plan);
        before.forEach((pos, state) -> { if (state.equals(plan.cells.get(pos))) completed.add(pos.asLong()); });
        Snapshot result = new Snapshot(plan, reservation, Map.copyOf(before), completed, cleared, tag.getUUID("Owner"),
                tag.getUUID("Builder"), tag.getString("CoreKey"), BlockPos.of(tag.getLong("CorePos")));
        CACHE.put(area, result); CACHE_RECORDS.put(area, area.getPersistentData().get(KEY));
        return result;
    }

    private static Set<Long> readPositions(long[] cells, AcceptedConstructionPlan plan) {
        if (cells.length > plan.cells.size()) throw new IllegalArgumentException("Oversized progress history");
        Set<Long> result = new HashSet<>();
        for (long cell : cells) {
            if (!plan.cells.containsKey(BlockPos.of(cell)) || !result.add(cell))
                throw new IllegalArgumentException("Invalid progress history");
        }
        return result;
    }

    /** Read-only readiness check, then noncreative queue rebuild with no chunk loading. */
    public static boolean prepareLoadedArea(Entity area) {
        if (!(area instanceof ProtectedBuildArea protectedArea) || !(area.level() instanceof ServerLevel level)) return false;
        String capabilityProblem = availabilityProblem();
        if (capabilityProblem != null) return pause(area, capabilityProblem);
        if (protectedArea.nativeQueuesReady()) return true;
        try {
            Snapshot snapshot = snapshot(area);
            if (PerimeterProjectAuthority.tracked(area)) {
                Entity worker = level.getEntity(snapshot.builder);
                String project = PerimeterProjectAuthority.problem(level, worker instanceof Mob mob ? mob : null, area, false, true);
                if (project != null) return pause(area, project);
            }
            if (!snapshot.plan.matches(area) || !ConstructionEditLedger.get(level).matches(area.getUUID(), snapshot.reservation.cells)
                    || !Boolean.FALSE.equals(AcceptedConstructionPlan.call(area, "getFreeArea")))
                return pause(area, "Paused: saved construction needs a new reviewed plan");
            String territory = com.devfarinsky.siegeoverhaul.core.PerimeterTerritory.problem(level, area,
                    snapshot.coreKey.startsWith("team:") ? snapshot.coreKey.substring(5) : null);
            if (territory != null) return pause(area, territory);
            if (!scanChunksLoaded(level, snapshot.plan)) return pause(area, "Paused: load the complete construction footprint to resume");
            String clearance = snapshot.reservation.problem(level);
            if (clearance != null) return pause(area, clearance);
            protectedArea.rebuildAcceptedQueues();
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return pause(area, "Paused: saved construction cannot be verified");
        }
    }

    public static void areaJoined(EntityJoinLevelEvent event) {
        Entity area = event.getEntity();
        if (event.getLevel() instanceof ServerLevel level) {
            // Dimension transfer also restores a new entity through NBT, but Forge marks that join
            // loadedFromDisk=false. An existing guarded receipt, not the join flag, scopes the check.
            // Fresh commissioned builders join before protection writes that receipt.
            if (area instanceof Mob worker && WorkersBridge.isBuilder(worker)
                    && (worker.getPersistentData().hasUUID(PROTECTED_LINK)
                    || handLifecycleScope(worker, ConstructionEditLedger.get(level.getServer().overworld()))))
                ProtectedBuilderHandMirror.arm(worker.getPersistentData());
            if (event.loadedFromDisk() && protectedArea(area)) prepareLoadedArea(area);
        }
    }

    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var ledger = ConstructionEditLedger.get(level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi)
            multi.getReplacedBlockSnapshots().forEach(snapshot -> ledger.record(snapshot.getPos()));
        else ledger.record(event.getPos());
    }

    public static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) ConstructionEditLedger.get(level).record(event.getPos());
    }

    public static void areaRemoved(EntityLeaveLevelEvent event) {
        Entity area = event.getEntity();
        CACHE.remove(area); CACHE_RECORDS.remove(area); STAGE_BINDINGS.remove(area);
        if (!(event.getLevel() instanceof ServerLevel level) || area.getRemovalReason() == null
                || !area.getRemovalReason().shouldDestroy()) return;
        if (area instanceof Mob worker && (worker.getPersistentData().hasUUID(PROTECTED_LINK) || PerimeterProjectLink.reserved(worker))) {
            var overworld = level.getServer().overworld(); var ledger = ConstructionEditLedger.get(overworld);
            var data = worker.getPersistentData(); UUID child = data.hasUUID(PROTECTED_LINK)?data.getUUID(PROTECTED_LINK):null;
            if (child != null && ledger.projectForArea(child) == null && !PerimeterProjectLink.reserved(worker)) {
                ledger.builderDestroyed(child);
            } else {
                // A copied child UUID or a stale ledger generation is not proof that the reserved builder died.
                // Keep this project-only boundary independent of native inventory/corpse cleanup.
                try {
                    var link = PerimeterProjectLink.read(data);
                    var project = PerimeterProjectAuthority.snapshot(overworld, link.core()).get(link.id());
                    if (project != null && WorkersBridge.isBuilder(worker) && PerimeterProjectLink.matches(worker, project)) {
                        var core = com.devfarinsky.siegeoverhaul.RaidSavedData.get(level.getServer()).siegeCores.get(link.core());
                        var journal = core == null ? null : com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal.get(core, project);
                        int last = ledger.projectLeaseIndex(link.id());
                        if (NativePerimeterProjects.cleanupHistoryMatches(project, journal, last)) {
                            UUID wholeGeneration = PerimeterProjectLink.ledgerGeneration(data);
                            if (child != null && data.hasUUID(PROTECTED_GENERATION) && journal.at(project.activeStage()) != null
                                    && (wholeGeneration == null || wholeGeneration.equals(data.getUUID(PROTECTED_GENERATION)))) {
                                ledger.projectBuilderDestroyed(project, worker.getUUID(), child, data.getUUID(PROTECTED_GENERATION));
                            } else if (!data.contains(PROTECTED_LINK) && !data.contains(PROTECTED_GENERATION)
                                    && wholeGeneration != null && last >= 0 && last == project.activeStage() - 1
                                    && journal.attempts().size() == project.activeStage()
                                    && journal.attempts().stream().allMatch(attempt ->
                                    attempt.state() == com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal.State.RETIRED
                                            && ledger.retired(attempt.area()))) {
                                ledger.projectBuilderDestroyed(project, worker.getUUID(), project.stages().get(last).areaId(), wholeGeneration);
                            }
                        }
                    }
                } catch (RuntimeException | LinkageError unavailable) { /* Unknown destruction identity never releases a project. */ }
            }
        }
        if (area instanceof ProtectedBuildArea protectedArea && protectedArea.retirementHandled()) return;
        if (protectedArea(area)) ConstructionEditLedger.get(level).retire(area.getUUID(),
                area instanceof ProtectedBuildArea protectedArea && protectedArea.isDone());
    }
}
