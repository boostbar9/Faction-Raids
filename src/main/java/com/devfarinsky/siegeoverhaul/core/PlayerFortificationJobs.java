package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.UUID;

/** Durable ownership link between a hired Workers 2 builder and a commissioned wall job. */
public final class PlayerFortificationJobs {
    private static final int RECOVERY_INTERVAL = 40;
    private static final int LOADED_MISS_LIMIT = 5;

    private PlayerFortificationJobs() {}

    /**
     * Link both entities before the area joins the world. Workers 2 does not
     * persist {@code currentBuildArea}, so this small NBT association is the
     * authoritative way to reconnect the same paid job after a chunk/server
     * reload without scanning or taking over another player job.
     */
    public static void link(Mob builder, Entity area, UUID owner) {
        if (builder == null || area == null || owner == null) return;
        CompoundTag workerTag = builder.getPersistentData();
        workerTag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID, area.getUUID());
        workerTag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
        workerTag.putLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS, area.blockPosition().asLong());
        workerTag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES);

        CompoundTag areaTag = area.getPersistentData();
        areaTag.putBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA, true);
        areaTag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER, builder.getUUID());
        areaTag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
        // A player job must never enter NativeCampConstruction's raid-owned
        // area reload path. Older releases accidentally put this tag on it.
        areaTag.remove(ModConstants.Tags.CAMP_AREA_TEAM);
    }

    public static void unlink(Mob builder, UUID expectedArea) {
        if (builder == null) return;
        CompoundTag tag = builder.getPersistentData();
        if (expectedArea != null && tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && !expectedArea.equals(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))) return;
        tag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
        tag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER);
        tag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_POS);
        tag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES);
    }

    /**
     * Keep marked player areas out of enemy-camp cleanup and migrate the
     * v4.22-v4.46 tag mistake when a saved job belongs to a still-known Siege
     * Core. Returns true when the caller must stop enemy-area handling.
     */
    public static boolean handleAreaJoin(ServerLevel level, Entity area, boolean loadedFromDisk) {
        CompoundTag tag = area.getPersistentData();
        if (tag.getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)) {
            reconnectLoadedBuilder(level, area);
            return true;
        }
        String legacyKey = tag.getString(ModConstants.Tags.CAMP_AREA_TEAM);
        if (!loadedFromDisk || legacyKey.isBlank()) return false;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        if (data.raids.containsKey(legacyKey) || !data.siegeCores.containsKey(legacyKey)) return false;
        UUID owner = WorkersBridge.readOwner(area);
        if (owner == null) return false;

        tag.putBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA, true);
        tag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
        tag.remove(ModConstants.Tags.CAMP_AREA_TEAM);
        Mob builder = nearestOwnedBuilder(level, area, owner);
        if (builder != null) link(builder, area, owner);
        return true;
    }

    /** Reattach a loaded player builder only when its own saved area link is missing. */
    private static void reconnectLoadedBuilder(ServerLevel level, Entity area) {
        CompoundTag tag = area.getPersistentData();
        UUID owner = tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                ? tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER) : WorkersBridge.readOwner(area);
        if (owner == null) return;
        Mob builder = null;
        if (tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)
                && level.getEntity(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)) instanceof Mob saved)
            builder = saved;
        if (builder == null) builder = nearestOwnedBuilder(level, area, owner);
        if (builder != null && !WorkersBridge.hasActiveBuildArea(builder)) link(builder, area, owner);
    }

    private static Mob nearestOwnedBuilder(ServerLevel level, Entity area, UUID owner) {
        return level.getEntitiesOfClass(Mob.class, area.getBoundingBox().inflate(128.0D), candidate ->
                        candidate.isAlive()
                                && !candidate.getPersistentData().contains(ModConstants.Tags.CAMP_WORKER_TEAM)
                                && owner.equals(WorkersBridge.readWorkerOwner(candidate))
                                && !WorkersBridge.hasActiveBuildArea(candidate))
                .stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(area))).orElse(null);
    }

    /** Called cheaply from the existing living-tick hook; meaningful work runs once every two seconds. */
    public static void tick(ServerLevel level, Mob builder) {
        CompoundTag tag = builder.getPersistentData();
        if (!tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                || builder.tickCount % RECOVERY_INTERVAL != 0
                || WorkersBridge.hasActiveBuildArea(builder)) return;
        UUID areaId = tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
        Entity area = level.getEntity(areaId);
        if (area != null && area.isAlive()
                && area.getPersistentData().getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)
                && (!area.getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)
                    || builder.getUUID().equals(area.getPersistentData().getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)))) {
            UUID owner = tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                    ? tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER) : WorkersBridge.readWorkerOwner(builder);
            if (owner == null) { unlink(builder, areaId); return; }
            try {
                WorkersBridge.enablePlayerJob(builder, owner);
                if (WorkersBridge.assignBuildAreaDirectly(builder, area)) {
                    tag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES);
                    return;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Retain the association and try again; optional-mod startup
                // order can make the first loaded tick temporarily unavailable.
            }
            return;
        }
        if (area != null) { unlink(builder, areaId); return; }

        if (!tag.contains(ModConstants.Tags.PLAYER_FORTIFICATION_POS)) return;
        BlockPos known = BlockPos.of(tag.getLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS));
        if (!level.hasChunkAt(known)) {
            tag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES);
            return;
        }
        int misses = tag.getInt(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES) + 1;
        if (misses >= LOADED_MISS_LIMIT) unlink(builder, areaId);
        else tag.putInt(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES, misses);
    }
}
