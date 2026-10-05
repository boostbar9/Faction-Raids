package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Server-owned new-job identity. Selectors never constitute authority without their exact world entry. */
public final class NativeEarthworksJobs {
    static final String KEY = "SiegeEarthworksJobV1";
    private static final Set<String> SELECTOR_KEYS = Set.of("Version", "Project", "Generation", "Area", "Manifest", "Binding");
    private NativeEarthworksJobs() {}
    public record InventoryLease(UUID project, long generation, UUID area, String manifestHash, String bindingHash,
                                 int nextStep, PerimeterEarthworksManifest.Step step, UUID owner, String faction, BlockPos core) {
        public InventoryLease { core = core.immutable(); }
    }
    public static boolean selected(Mob worker) {
        if (worker == null) return false;
        // canWorkHere blocks discovery, but a stale/forced native assignment is not an authenticated lease.
        // Select the guarded path even when this other builder has no job tag or recoverable world record.
        if (worker instanceof BuilderEntity builder && builder.currentBuildArea instanceof EarthworksBuildArea) return true;
        if (worker.getPersistentData() == null) return false;
        if (worker.getPersistentData().contains(KEY) || worker.getPersistentData().contains("SiegeEarthworksSupplyV1")) return true; // Includes malformed selectors and terminal recovery evidence.
        try {
            if (!(worker.level() instanceof ServerLevel level)) return false;
            var ledger = EarthworksJobLedger.get(level);
            return ledger.uncertain() || ledger.worker(worker.getUUID()) != null;
        } catch (RuntimeException | LinkageError unavailable) { return true; }
    }
    public static InventoryLease inventoryLease(BuilderEntity worker) {
        try {
            var job = authenticated(worker, true); if (job == null) return null;
            var journal = job.read().journal(); int next = journal.nextStep();
            if (next >= job.manifest.steps().size()) return null;
            var h = job.manifest.header();
            return new InventoryLease(h.project(), h.generation(), job.area, job.manifest.hash(), journal.check().bindingHash(),
                    next, job.manifest.steps().get(next), h.owner(), h.faction(), job.core);
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }
    public static String inventoryProblem(Mob worker, Set<BlockPos> sources) {
        try {
            if (!(worker instanceof BuilderEntity builder) || !(worker.level() instanceof ServerLevel level)) return "Paused: the new earthworks inventory lease cannot be authenticated";
            String runtime = WorkersConstructionRuntime.problem(); if (runtime != null) return runtime;
            var lease = inventoryLease(builder);
            if (lease == null) return "Paused: the new earthworks inventory lease cannot be authenticated";
            String hand = handLifecycleProblem(worker); if (hand != null) return hand;
            return NativeInventoryAuthority.problem(level, worker, lease.owner(), "team:" + lease.faction(), lease.core(), sources);
        } catch (RuntimeException | LinkageError unavailable) { return "Paused: earthworks inventory authority is unavailable"; }
    }
    public static boolean sharedStorageFactionMatches(Mob worker) {
        if (!(worker instanceof BuilderEntity builder)) return false;
        var lease = inventoryLease(builder); if (lease == null) return false;
        var team = worker.level().getScoreboard().getPlayersTeam(worker.getScoreboardName());
        return team != null && lease.faction().equals(team.getName());
    }
    public static String handLifecycleProblem(Mob worker) {
        try {
            if (worker == null || !(worker.level() instanceof ServerLevel level) || worker.getPersistentData() == null)
                return "Paused: earthworks hand provenance is unavailable";
            var ledger = ConstructionEditLedger.get(level);
            if (!ProtectedBuilderHandLifecycle.matches(worker.getPersistentData(), worker.getUUID(), ledger))
                return "Paused: earthworks hand provenance differs from the world ledger";
            if (ProtectedBuilderHandMirror.pending(worker.getPersistentData()) || ProtectedBuilderHandMirror.reviewNeeded(worker.getPersistentData()))
                return "Paused: native hand restoration requires review before earthworks resupply";
            return null;
        } catch (RuntimeException | LinkageError unavailable) { return "Paused: earthworks hand provenance is unavailable"; }
    }
    public static String supplyDigest(BuilderEntity worker) {
        try { var job = authenticated(worker, false); return job == null ? null : job.supplyDigest(); }
        catch (RuntimeException | LinkageError unavailable) { return null; }
    }
    public static boolean compareSupplyDigest(BuilderEntity worker, String expected, String next) {
        try { var job = authenticated(worker, true); return job != null && job.compareSupplyDigest(expected, next); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    /** Called before native AI; malformed new selectors cannot reach unguarded native work or storage. */
    static boolean beforeWorkerTick(BuilderEntity worker) {
        try {
            if (!ProtectedStorageAccess.install(worker) || !EarthworksWorkGoal.install(worker, () -> workGoal(worker))) return false;
            var job = authenticated(worker, true); if (job == null) return false;
            var data = worker.getPersistentData();
            var ledger = ConstructionEditLedger.get((ServerLevel)worker.level());
            if (!ProtectedBuilderHandLifecycle.matches(data, worker.getUUID(), ledger)) return false;
            if (ProtectedBuilderHandMirror.pending(data) || ProtectedBuilderHandMirror.reviewNeeded(data)) {
                if (ProtectedBuilderHandMirror.activeUse(worker) || ProtectedBuilderHandMirror.restore(worker) != null) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    static net.minecraft.world.entity.ai.goal.Goal workGoal(BuilderEntity worker) {
        var job = authenticated(worker, true); if (job == null) return null;
        if (job.runtimeWorker != worker || job.runtimeGoal == null) {
            var level = (ServerLevel)worker.level(); var area = (EarthworksBuildArea)worker.currentBuildArea;
            var port = new WorkersEarthworksPort(level, worker, area, job.core, new EarthworksExecution(level, job, worker, area));
            job.runtimeWorker = worker; job.runtimeGoal = new LocalEarthworksGoal(job.manifest, job, port);
        }
        return job.runtimeGoal;
    }
    static CompoundTag selector(EarthworksJobLedger.Job job) {
        var tag = new CompoundTag(); var h = job.manifest.header(); tag.putInt("Version", 1); tag.putUUID("Project", h.project());
        tag.putLong("Generation", h.generation()); tag.putUUID("Area", job.area); tag.putString("Manifest", job.manifest.hash());
        tag.putString("Binding", job.read().journal().check().bindingHash()); return tag;
    }
    static EarthworksJobLedger.Job authenticated(BuilderEntity worker, boolean active) {
        if (worker == null || !(worker.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !worker.isAlive() || worker.getPersistentData() == null || !worker.getPersistentData().contains(KEY, Tag.TAG_COMPOUND)) return null;
        var tag = worker.getPersistentData().getCompound(KEY);
        if (!tag.getAllKeys().equals(SELECTOR_KEYS) || !tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.hasUUID("Project") || !tag.hasUUID("Area") || !tag.contains("Generation", Tag.TAG_LONG)
                || !tag.contains("Manifest", Tag.TAG_STRING) || !tag.contains("Binding", Tag.TAG_STRING)) return null;
        var job = EarthworksJobLedger.get(level).job(tag.getUUID("Project"));
        if (job == null || !tag.equals(selector(job)) || !job.manifest.header().builder().equals(worker.getUUID())
                || !job.manifest.header().owner().equals(WorkersBridge.readWorkerOwner(worker))
                || !job.manifest.header().dimension().equals(level.dimension().location().toString())
                || !job.paid() || !EarthworksCommission.paidMatches(level, job) || active && !job.active()) return null;
        if (active) {
            var area = worker.currentBuildArea;
            if (!(area instanceof EarthworksBuildArea grading) || !grading.matches(job) || !area.getUUID().equals(job.area) || area.level() != level || area.isRemoved()
                    || !Objects.equals(WorkersBridge.readOwner(area), job.manifest.header().owner())) return null;
            var edits = ConstructionEditLedger.get(level);
            if (!edits.sameGeneration(job.read().journal().binding().ledgerGeneration()) || edits.edited(job.area)
                    || !edits.matches(job.area, job.manifest.observations().keySet().stream().map(BlockPos::of).collect(Collectors.toSet()))) return null;
        }
        return job;
    }
}
