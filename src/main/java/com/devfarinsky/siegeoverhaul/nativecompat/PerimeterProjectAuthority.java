package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Exact server manifest lookup; entity tags select an authority record but can never grant authority. */
final class PerimeterProjectAuthority {
    static final String KEY = "SiegePerimeterProject", REQUIRED = "SiegePerimeterProjectRequired";
    private static final Map<MinecraftServer, Map<String, PerimeterProjectStore.Snapshot>> CACHE = new WeakHashMap<>();
    record Scope(UUID projectId, long generation, String manifestHash, String coreKey, int stage, String stageDigest) {}
    private PerimeterProjectAuthority() {}

    static boolean tracked(Entity area) {
        if (area == null) return false;
        if (area.getPersistentData().contains(KEY) || area.getPersistentData().getBoolean(REQUIRED)) return true;
        return area.level() instanceof ServerLevel level && ConstructionEditLedger.get(level).projectForArea(area.getUUID()) != null;
    }
    static void stamp(Entity area, PerimeterProject project) {
        if (area == null || project == null || project.active() == null || area.getPersistentData().contains(KEY)
                || !area.getUUID().equals(project.active().areaId())) throw new IllegalArgumentException("Exact new stage identity required");
        CompoundTag tag = new CompoundTag(); tag.putInt("Version",1);
        tag.putUUID("Project",project.header().projectId()); tag.putLong("Generation",project.header().generation());
        tag.putString("Hash",project.manifestHash()); tag.putString("Core",project.header().coreKey());
        tag.putInt("Stage",project.activeStage()); tag.putString("StageHash",project.active().digest());
        area.getPersistentData().put(KEY,tag); area.getPersistentData().putBoolean(REQUIRED,true);
    }
    static Scope read(CompoundTag persistent) {
        if (!persistent.contains(KEY,Tag.TAG_COMPOUND) || !persistent.contains(REQUIRED,Tag.TAG_BYTE)
                || persistent.getByte(REQUIRED) != 1) throw new IllegalArgumentException("Stage authority selector is missing");
        CompoundTag tag=persistent.getCompound(KEY);
        if (!tag.getAllKeys().equals(java.util.Set.of("Version","Project","Generation","Hash","Core","Stage","StageHash"))
                || !tag.contains("Version",Tag.TAG_INT) || tag.getInt("Version")!=1 || !tag.hasUUID("Project")
                || !tag.contains("Generation",Tag.TAG_LONG) || tag.getLong("Generation")<1
                || !tag.contains("Hash",Tag.TAG_STRING) || !tag.getString("Hash").matches("[0-9a-f]{64}")
                || !tag.contains("Core",Tag.TAG_STRING) || !tag.getString("Core").startsWith("team:")
                || tag.getString("Core").length()<=5 || tag.getString("Core").length()>256
                || !tag.contains("Stage",Tag.TAG_INT) || tag.getInt("Stage")<0 || tag.getInt("Stage")>=256
                || !tag.contains("StageHash",Tag.TAG_STRING) || !tag.getString("StageHash").matches("[0-9a-f]{64}")
                || tag.getUUID("Project").equals(new UUID(0,0))) throw new IllegalArgumentException("Malformed stage authority selector");
        return new Scope(tag.getUUID("Project"),tag.getLong("Generation"),tag.getString("Hash"),
                tag.getString("Core"),tag.getInt("Stage"),tag.getString("StageHash"));
    }

    static PerimeterProjectStore.Snapshot snapshot(ServerLevel level, String coreKey) {
        MinecraftServer server=level.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Project authority requires the server thread");
        CompoundTag core=RaidSavedData.get(server).siegeCores.get(coreKey);
        if (core==null) throw new IllegalStateException("Authoritative project core is unavailable");
        var byCore=CACHE.computeIfAbsent(server, ignored->new HashMap<>());
        var cached=byCore.get(coreKey);
        if (cached==null || !cached.matches(core)) {
            cached=PerimeterProjectStore.snapshot(core); byCore.put(coreKey,cached);
        }
        return cached;
    }
    static PerimeterProject project(ServerLevel level, Scope scope) {
        var project=snapshot(level,scope.coreKey()).get(scope.projectId());
        if (project==null || project.header().generation()!=scope.generation()
                || !project.manifestHash().equals(scope.manifestHash()) || !project.header().coreKey().equals(scope.coreKey()))
            throw new IllegalStateException("Authoritative project is missing, terminal or conflicts with the saved stage");
        return project;
    }
    static String problem(ServerLevel level, Mob builder, Entity area, boolean allowUnpaid, boolean allowVerified) {
        if (!tracked(area)) return null;
        try {
            Scope scope=read(area.getPersistentData()); PerimeterProject project=project(level,scope);
            if (!project.executionSupported()) return PerimeterProject.GATE_EXECUTION_BLOCKER;
            var stage=project.active();
            if (stage==null || stage.index()!=scope.stage() || !stage.digest().equals(scope.stageDigest())
                    || !stage.areaId().equals(area.getUUID()) || builder==null
                    || !NativePerimeterProjects.assignedBuilder(builder,project)
                    || !project.header().owner().equals(WorkersBridge.readWorkerOwner(builder))
                    || !project.header().owner().equals(WorkersBridge.readOwner(area)))
                return "Paused: whole-perimeter stage ownership or identity changed";
            if (!workState(project,allowUnpaid,allowVerified))
                return "Paused: the whole-perimeter project is not authorized to run this stage";
            var core=RaidSavedData.get(level.getServer()).siegeCores.get(scope.coreKey());
            var journal=com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal.get(core,project);
            var attempt=journal==null?null:journal.at(scope.stage());
            if(attempt==null || journal.at(0)==null || !attempt.area().equals(area.getUUID())
                    || !attempt.marker().equals(journal.at(0).marker()) || !area.blockPosition().equals(attempt.marker())
                    || attempt.state()==com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal.State.RETIRED)
                return "Paused: native shovel position or creation history differs from the whole-perimeter record";
            var territory = com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.getFactionTerritory(level,
                    project.header().faction(), com.devfarinsky.siegeoverhaul.core.PerimeterTerritory.MAX_CHUNKS);
            if (!territoryMatches(project, territory))
                return "Paused: the complete faction territory differs from the reviewed perimeter";
            var owner=level.getServer().getPlayerList().getPlayer(project.header().owner());
            var gates=activeGateObservationProblem(level,owner,project,scope.stage());
            if (gates!=null) return gates;
            if ((!allowUnpaid || NativeConstructionGuard.hasAreaSnapshot(area))
                    && !NativeConstructionGuard.matchesProjectSnapshot(area, project))
                return "Paused: the saved native stage differs from the authoritative whole-perimeter plan";
            if (!ConstructionEditLedger.get(level).matchesProjectLease(project))
                return "Paused: whole-perimeter reservation or stage history is unavailable";
            return null;
        } catch (RuntimeException | LinkageError unavailable) {
            return "Paused: authoritative whole-perimeter project needs recovery review";
        }
    }
    static boolean territoryMatches(PerimeterProject project,
            com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.TerritorySnapshot current) {
        return project != null && current != null && current.ready()
                && project.header().faction().equals(current.factionStringId())
                && project.header().territory().equals(current.chunks());
    }
    static boolean workState(PerimeterProject project, boolean allowUnpaid, boolean allowVerified) {
        if (project == null || !project.executionSupported()) return false;
        if (project.state()==PerimeterProject.State.RUNNING) return project.payment()!=null;
        if (allowVerified && project.state()==PerimeterProject.State.STAGE_VERIFIED) return project.payment()!=null;
        return allowUnpaid && (project.state()==PerimeterProject.State.PREPARED_UNPAID && project.payment()==null
                || project.state()==PerimeterProject.State.PREPARED_PAID && project.payment()!=null
                || project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE && project.payment()!=null);
    }
    static String activeGateObservationProblem(ServerLevel level, ServerPlayer owner, PerimeterProject project, int stage) {
        if (project==null || project.gateContract()==null) return null;
        return NativePerimeterProjects.gateObservationProblem(level,owner,project,project.gateStageComponent(stage));
    }
    /** Only NativeConstructionGuard calls this after exact loaded-world and empty native-queue proof. */
    static boolean verified(Entity area) {
        if (!(area.level() instanceof ServerLevel level)) return false;
        try {
            Scope scope=read(area.getPersistentData()); PerimeterProject project=project(level,scope);
            if (!project.executionSupported() || project.active()==null || project.activeStage()!=scope.stage()
                    || !project.active().digest().equals(scope.stageDigest()) || !project.active().areaId().equals(area.getUUID())
                    || !NativeConstructionGuard.matchesProjectSnapshot(area,project)
                    || !ConstructionEditLedger.get(level).matchesProjectLease(project)
                    || ConstructionEditLedger.get(level).edited(area.getUUID())) return false;
            var next=project.verifyStage(project.check(),project.expectedStageReceipt());
            var data=RaidSavedData.get(level.getServer()); CompoundTag core=data.siegeCores.get(scope.coreKey());
            PerimeterProjectStore.replace(core,project.check(),next,data::setDirty);
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
    static void stopped(MinecraftServer server) { CACHE.remove(server); }
}
