package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Durable ownership link between a hired Workers 2 builder and a commissioned wall job. */
public final class PlayerFortificationJobs {
    private static final int RECOVERY_INTERVAL = 40;
    private static final int LOADED_MISS_LIMIT = 5;
    /**
     * Areas can join before their saved Workers 2 builder. Keep only the UUIDs
     * of loaded, unattached player jobs until the matching owner appears. A
     * weak level key prevents a stopped world from being retained in memory.
     */
    private static final Map<ServerLevel, Map<UUID, Set<UUID>>> PENDING_AREAS =
            Collections.synchronizedMap(new WeakHashMap<>());

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
     * v4.22-v4.46 tag mistake only when the entity is positively identified
     * as a player-owned Workers 2 build area. Returns true when the caller
     * must stop enemy-area handling.
     */
    public static boolean handleAreaJoin(ServerLevel level, Entity area, boolean loadedFromDisk) {
        CompoundTag tag = area.getPersistentData();
        if (tag.getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)) {
            reconnectLoadedBuilder(level, area);
            return true;
        }
        String legacyKey = tag.getString(ModConstants.Tags.CAMP_AREA_TEAM);
        UUID owner = WorkersBridge.readOwner(area);
        if (!legacyPlayerCommission(loadedFromDisk, legacyKey, owner, WorkersBridge.isPlayerBuildArea(area)))
            return false;

        tag.putBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA, true);
        tag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
        tag.remove(ModConstants.Tags.CAMP_AREA_TEAM);
        reconnectLoadedBuilder(level, area);
        return true;
    }

    /**
     * Positive legacy evidence. Active raids and deleted cores are deliberately
     * irrelevant: player commissions used a player-owned Workers buildarea,
     * while enemy camp areas use the raider leader as owner.
     */
    static boolean legacyPlayerCommission(boolean loadedFromDisk, String legacyKey,
                                          UUID owner, boolean playerWorkersBuildArea) {
        return loadedFromDisk && legacyKey != null && !legacyKey.isBlank()
                && playerWorkersBuildArea && owner != null
                && !RecruitsBridge.RAIDERS_LEADER_UUID.equals(owner);
    }

    /** Reattach a loaded player builder only when its own saved area link is missing. */
    private static void reconnectLoadedBuilder(ServerLevel level, Entity area) {
        CompoundTag tag = area.getPersistentData();
        UUID owner = tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                ? tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER) : WorkersBridge.readOwner(area);
        if (owner == null) return;
        Mob builder = null;
        if (tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)
                && level.getEntity(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)) instanceof Mob saved
                && pendingBuilderMatches(owner, WorkersBridge.readWorkerOwner(saved),
                        tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER), saved.getUUID(),
                        WorkersBridge.isBuilder(saved), WorkersBridge.hasActiveBuildArea(saved)))
            builder = saved;
        if (builder == null && !tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER))
            builder = nearestOwnedBuilder(level, area, owner);
        if (builder != null) {
            link(builder, area, owner);
            forgetPending(level, owner, area.getUUID());
        } else rememberPending(level, owner, area.getUUID());
    }

    private static Mob nearestOwnedBuilder(ServerLevel level, Entity area, UUID owner) {
        return level.getEntitiesOfClass(Mob.class, area.getBoundingBox().inflate(128.0D), candidate ->
                        candidate.isAlive()
                                && WorkersBridge.isBuilder(candidate)
                                && !candidate.getPersistentData().contains(ModConstants.Tags.CAMP_WORKER_TEAM)
                                && owner.equals(WorkersBridge.readWorkerOwner(candidate))
                                && !WorkersBridge.hasActiveBuildArea(candidate))
                .stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(area))).orElse(null);
    }

    private static void reconnectPendingBuilder(ServerLevel level, Mob builder) {
        if (level == null || builder == null || !WorkersBridge.isBuilder(builder)) return;
        CompoundTag workerTag = builder.getPersistentData();
        if (workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                || WorkersBridge.hasActiveBuildArea(builder)) return;
        UUID owner = WorkersBridge.readWorkerOwner(builder);
        if (owner == null) return;

        Set<UUID> candidates = pendingSnapshot(level, owner);
        Entity selected = null;
        for (UUID areaId : candidates) {
            Entity area = level.getEntity(areaId);
            if (area == null) continue; // The area may be temporarily unloaded.
            CompoundTag areaTag = area.getPersistentData();
            UUID areaOwner = areaTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                    ? areaTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                    : WorkersBridge.readOwner(area);
            UUID reservedBuilder = areaTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)
                    ? areaTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER) : null;
            if (!area.isAlive() || !areaTag.getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)
                    || areaOwner == null) {
                forgetPending(level, owner, areaId);
                continue;
            }
            if (!pendingBuilderMatches(areaOwner, owner, reservedBuilder, builder.getUUID(), true, false))
                continue;
            if (selected == null || builder.distanceToSqr(area) < builder.distanceToSqr(selected)) selected = area;
        }
        if (selected != null) {
            link(builder, selected, owner);
            forgetPending(level, owner, selected.getUUID());
        }
    }

    static boolean pendingBuilderMatches(UUID areaOwner, UUID workerOwner, UUID reservedBuilder,
                                         UUID workerId, boolean workersBuilder, boolean hasActiveArea) {
        return workersBuilder && !hasActiveArea && areaOwner != null && areaOwner.equals(workerOwner)
                && workerId != null && (reservedBuilder == null || reservedBuilder.equals(workerId));
    }

    private static void rememberPending(ServerLevel level, UUID owner, UUID areaId) {
        synchronized (PENDING_AREAS) {
            PENDING_AREAS.computeIfAbsent(level, ignored -> new HashMap<>())
                    .computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(areaId);
        }
    }

    private static Set<UUID> pendingSnapshot(ServerLevel level, UUID owner) {
        synchronized (PENDING_AREAS) {
            Map<UUID, Set<UUID>> byOwner = PENDING_AREAS.get(level);
            if (byOwner == null || !byOwner.containsKey(owner)) return Set.of();
            return Set.copyOf(byOwner.get(owner));
        }
    }

    private static void forgetPending(ServerLevel level, UUID owner, UUID areaId) {
        synchronized (PENDING_AREAS) {
            Map<UUID, Set<UUID>> byOwner = PENDING_AREAS.get(level);
            if (byOwner == null) return;
            Set<UUID> ids = byOwner.get(owner);
            if (ids == null) return;
            ids.remove(areaId);
            if (ids.isEmpty()) byOwner.remove(owner);
            if (byOwner.isEmpty()) PENDING_AREAS.remove(level);
        }
    }

    /** Called cheaply from the existing living-tick hook; meaningful work runs once every two seconds. */
    public static void tick(ServerLevel level, Mob builder) {
        if (!WorkersBridge.isBuilder(builder) || builder.tickCount % RECOVERY_INTERVAL != 0) return;
        CompoundTag tag = builder.getPersistentData();
        if (!tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)) {
            reconnectPendingBuilder(level, builder);
            return;
        }
        if (WorkersBridge.hasActiveBuildArea(builder)) return;
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
