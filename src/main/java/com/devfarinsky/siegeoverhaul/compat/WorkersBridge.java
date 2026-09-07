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

    /** Configure native work ownership after assigning the raider combat faction. */
    public static void enableNative(Mob worker, java.util.UUID owner, boolean equip) throws ReflectiveOperationException {
        call(worker, "setOwnerUUID", Optional.class, Optional.of(owner));
        call(worker, "setFollowState", int.class, 0);
        if (equip) {
            net.minecraft.world.SimpleContainer inventory = (net.minecraft.world.SimpleContainer)
                    worker.getClass().getMethod("getInventory").invoke(worker);
            inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
            inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
            inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL));
            inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 16));
        }
    }

    /** Work areas remain unregistered until their ownership and blueprint are complete. */
    public static Entity createArea(ServerLevel level, String type, BlockPos origin, java.util.UUID owner,
                                    int width, int depth, int height) throws ReflectiveOperationException {
        EntityType<?> entityType = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE)
                .getOptional(new ResourceLocation("workers", type)).orElseThrow();
        Entity area = entityType.create(level);
        if (area == null) throw new IllegalStateException("Workers area could not be created");
        // getOriginPos() delegates to Entity.getOnPos(), i.e. floor(y - 0.2).
        area.moveTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0, 0);
        call(area, "setPlayerUUID", java.util.UUID.class, owner);
        call(area, "setPlayerName", String.class, "Siege camp");
        call(area, "setTeamStringID", String.class, RecruitsBridge.RAIDERS_FACTION_ID);
        call(area, "setTeamAccess", boolean.class, false);
        call(area, "setFacing", net.minecraft.core.Direction.class, net.minecraft.core.Direction.SOUTH);
        call(area, "setWidthSize", int.class, width);
        call(area, "setDepthSize", int.class, depth);
        call(area, "setHeightSize", int.class, height);
        if (type.equals("storagearea")) call(area, "setStorageTypes", int.class, 1 << 2);
        return area;
    }

    public static void startBlueprint(Entity area, net.minecraft.nbt.CompoundTag blueprint) throws ReflectiveOperationException {
        call(area, "setStructureNBT", net.minecraft.nbt.CompoundTag.class, blueprint);
        call(area, "setFreeArea", boolean.class, false);
        call(area, "setStartBuild", boolean.class, false);
    }

    public static java.util.List<net.minecraft.world.item.ItemStack> materials(Entity area) throws ReflectiveOperationException {
        Object result = area.getClass().getMethod("getRequiredMaterials").invoke(area);
        if (!(result instanceof java.util.List<?> list)) throw new IllegalStateException("Missing material list");
        java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof net.minecraft.world.item.ItemStack stack) || stack.isEmpty())
                throw new IllegalStateException("Unsupported camp material");
            stacks.add(stack.copy());
        }
        return stacks;
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
