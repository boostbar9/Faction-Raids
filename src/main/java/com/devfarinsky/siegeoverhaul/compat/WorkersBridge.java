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
            com.devfarinsky.siegeoverhaul.camp.BuilderSupport.provision(worker);
            com.devfarinsky.siegeoverhaul.camp.BuilderWorkShift.install(worker);
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

    /**
     * Configure a raider-camp worker to run our night-shift job. This path is
     * ONLY for enemy raider construction. It installs the BuilderWorkShift
     * goal wrapper (which gates on CAMP_WORKER_TEAM + an active raid) and
     * optionally kits the worker in diamond raider gear.
     */
    public static void enableNative(Mob worker, java.util.UUID owner, boolean equip) throws ReflectiveOperationException {
        call(worker, "setOwnerUUID", Optional.class, Optional.of(owner));
        call(worker, "setFollowState", int.class, 0);
        if (equip) com.devfarinsky.siegeoverhaul.camp.BuilderSupport.provision(worker);
        com.devfarinsky.siegeoverhaul.camp.BuilderWorkShift.install(worker);
    }

    /**
     * Attach a player-owned Workers 2 builder to a job while leaving its
     * native AI goals untouched. Do NOT install the siege-only BuilderWorkShift
     * wrapper (that goal only fires for raider camps) and do NOT overwrite the
     * builder's inventory with raider gear.
     */
    public static void enablePlayerJob(Mob worker, java.util.UUID owner) throws ReflectiveOperationException {
        call(worker, "setOwnerUUID", Optional.class, Optional.of(owner));
        call(worker, "setIsOwned", boolean.class, true);
        call(worker, "setFollowState", int.class, 0);
        call(worker, "setListen", boolean.class, true);
    }

    /** Keep the camp crew visible after its job finishes without leaving native jobs running. */
    public static boolean parkBuilder(Mob worker) {
        try {
            Object needed = worker.getClass().getField("neededItems").get(worker);
            if (needed instanceof java.util.List<?> list) list.clear();
            worker.getClass().getField("forcedDeposit").setBoolean(worker, false);
            worker.getClass().getField("currentBuildArea").set(worker, null);
            call(worker, "setHoldPos", Vec3.class, worker.position());
            call(worker, "setFollowState", int.class, 3);
            worker.getNavigation().stop();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn("park", ex);
            return false;
        }
    }

    /**
     * Create a raider-owned work area under the RAIDERS faction. Used for the
     * enemy siege camp.
     */
    public static Entity createArea(ServerLevel level, String type, BlockPos origin, java.util.UUID owner,
                                    int width, int depth, int height) throws ReflectiveOperationException {
        return createAreaInternal(level, type, origin, owner, "Siege camp",
                RecruitsBridge.RAIDERS_FACTION_ID, false, width, depth, height);
    }

    /**
     * Create a player-owned work area with no team gating. Ownership is by
     * PlayerUUID, so this player's builder will pass canWorkHere the same way
     * it would on any manually placed buildarea.
     */
    public static Entity createPlayerArea(ServerLevel level, String type, BlockPos origin,
                                          java.util.UUID owner, String playerName,
                                          int width, int depth, int height) throws ReflectiveOperationException {
        String label = (playerName == null || playerName.isEmpty()) ? "Player" : playerName;
        return createAreaInternal(level, type, origin, owner, label, "", false, width, depth, height);
    }

    private static Entity createAreaInternal(ServerLevel level, String type, BlockPos origin,
                                             java.util.UUID owner, String playerName, String teamId,
                                             boolean teamAccess, int width, int depth, int height)
            throws ReflectiveOperationException {
        EntityType<?> entityType = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE)
                .getOptional(new ResourceLocation("workers", type)).orElseThrow();
        Entity area = entityType.create(level);
        if (area == null) throw new IllegalStateException("Workers area could not be created");
        // getOriginPos() delegates to Entity.getOnPos(), i.e. floor(y - 0.2).
        area.moveTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0, 0);
        call(area, "setPlayerUUID", java.util.UUID.class, owner);
        call(area, "setPlayerName", String.class, playerName);
        call(area, "setTeamStringID", String.class, teamId);
        call(area, "setTeamAccess", boolean.class, teamAccess);
        call(area, "setFacing", net.minecraft.core.Direction.class, net.minecraft.core.Direction.SOUTH);
        call(area, "setWidthSize", int.class, width);
        call(area, "setDepthSize", int.class, depth);
        call(area, "setHeightSize", int.class, height);
        if (type.equals("storagearea")) call(area, "setStorageTypes", int.class, 1 << 2);
        return area;
    }

    /**
     * Check whether a Workers 2 storagearea has the BUILDERS storage type
     * enabled. StorageArea.canWorkHere(builder) requires this bit before it
     * will accept a builder, and if it isn't set the builder silently reports
     * "No available storage found nearby" even though our area is right next
     * to it.
     *
     * @return true when the BUILDERS bit is set, false otherwise (including
     *         when the field cannot be read at all, so we stay permissive)
     */
    public static boolean hasBuilderStorage(Entity storageArea) {
        if (storageArea == null) return false;
        try {
            // StorageArea exposes getStorageTypes(): EnumSet<StorageType>.
            // Rather than depend on the enum class we read the raw mask off
            // the entity data via the getter and check bit 2 (BUILDERS).
            Object set = storageArea.getClass().getMethod("getStorageTypes").invoke(storageArea);
            if (set instanceof java.util.EnumSet<?> es) {
                for (Object v : es) {
                    if ("BUILDERS".equals(v.toString())) return true;
                }
                return false;
            }
            return false;
        } catch (ReflectiveOperationException ex) {
            warn("storage type read", ex);
            return true;
        }
    }

    /**
     * Read the PlayerUUID field from a Workers 2 area entity (buildarea,
     * storagearea, etc). Uses reflection and swallows failures so callers can
     * treat the result as optional.
     *
     * @return the owner UUID, or null when the field or the entity is missing
     */
    public static java.util.UUID readOwner(Entity area) {
        if (area == null) return null;
        try {
            Object result = area.getClass().getMethod("getPlayerUUID").invoke(area);
            if (result instanceof java.util.UUID uuid) return uuid;
            if (result instanceof java.util.Optional<?> opt && opt.isPresent()
                    && opt.get() instanceof java.util.UUID uuid) return uuid;
        } catch (ReflectiveOperationException ex) {
            warn("readOwner", ex);
        }
        return null;
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

    public static net.minecraft.world.item.Item buildMaterial(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.block.Block block) throws ReflectiveOperationException {
        Class<?> parser = Class.forName("com.talhanation.workers.world.BuildBlockParse");
        Object parsed;
        try {
            parsed = parser.getMethod("parseBlock", net.minecraft.world.level.block.Block.class,
                    net.minecraft.world.level.Level.class).invoke(null, block, level);
        } catch (NoSuchMethodException olderWorkers) {
            parsed = parser.getMethod("parseBlock", net.minecraft.world.level.block.Block.class).invoke(null, block);
        }
        return (net.minecraft.world.item.Item) parser.getMethod("getItem").invoke(parsed);
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
