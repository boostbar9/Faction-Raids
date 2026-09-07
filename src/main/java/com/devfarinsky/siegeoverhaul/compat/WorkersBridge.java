package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.OptionalCompatBridge;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Workers 2 bridge. No Workers classes are linked when the mod is absent. */
public final class WorkersBridge {
    private static final ResourceLocation BUILDER_ID = new ResourceLocation("workers", "builder");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private WorkersBridge() {}

    public static boolean available() {
        return RaidConfig.ENABLE_WORKERS_COMPAT.get()
                && OptionalCompatBridge.isLoaded(OptionalCompatBridge.WORKERS);
    }

    /** Configure before registration: an incompatible API must never leave a half-owned NPC in the world. */
    public static Optional<Mob> spawnBuilder(ServerLevel level, BlockPos pos, String defendingTeam) {
        if (!available()) return Optional.empty();
        EntityType<?> type = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE)
                .getOptional(BUILDER_ID).orElse(null);
        if (type == null) return Optional.empty();
        Entity candidate = null;
        try {
            candidate = type.create(level);
            if (!(candidate instanceof Mob worker)) {
                if (candidate != null) candidate.discard();
                return Optional.empty();
            }
            worker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            worker.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            configureBuilder(worker, worker.position());
            worker.setPersistenceRequired();
            worker.setCanPickUpLoot(false);
            worker.getPersistentData().putString(ModConstants.Tags.CAMP_WORKER_TEAM, defendingTeam);
            OptionalCompatBridge.tagAssetOwnership(worker, RecruitsBridge.RAIDERS_FACTION_ID,
                    RecruitsBridge.RAIDERS_LEADER_UUID);
            if (!level.addFreshEntity(worker)) {
                worker.discard();
                return Optional.empty();
            }
            RecruitsBridge.assignToRaidersFaction(worker);
            return Optional.of(worker);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (candidate != null) candidate.discard();
            warn("spawn", ex);
            return Optional.empty();
        }
    }

    // Workers 2 inherits these methods from Recruits. Hold mode prevents native
    // builder/storage jobs from touching player work areas or consuming supplies.
    static void configureBuilder(Object worker, Vec3 position) throws ReflectiveOperationException {
        call(worker, "setOwnerUUID", Optional.class, Optional.of(RecruitsBridge.RAIDERS_LEADER_UUID));
        call(worker, "setIsOwned", boolean.class, true);
        call(worker, "setListen", boolean.class, false);
        call(worker, "setHoldPos", Vec3.class, position);
        call(worker, "setFollowState", int.class, 3);
    }

    /** Uses the worker's own hold-position navigation, without replacing its AI goals. */
    public static boolean moveBuilder(Mob worker, BlockPos destination) {
        try {
            // Workers' flee goal takes priority over hold-position navigation.
            if (worker.getClass().getField("isFleeing").getBoolean(worker)) return false;
            call(worker, "setHoldPos", Vec3.class, Vec3.atBottomCenterOf(destination));
            call(worker, "setFollowState", int.class, 3);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn("move", ex);
            return false;
        }
    }

    private static void call(Object target, String name, Class<?> type, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(name, type).invoke(target, value);
    }

    private static void warn(String operation, Exception ex) {
        if (WARNED.add(operation)) FactionLogger.LOG.warn(
                "Workers 2 camp {} unavailable; skipping this integration safely", operation, ex);
    }
}
