package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Reflection-only bridge to Talhanation's Siege Weapons and to the Recruits
 * Siege Engineer profession. Everything degrades gracefully when either mod
 * is missing: {@link #isSiegeWeaponsPresent()} and
 * {@link #isSiegeEngineerAvailable()} let callers branch, and every hook
 * call returns a boolean success flag rather than throwing.
 *
 * <p>This mirrors the pattern in {@code RecruitsBridge}: no compile-time
 * imports of the target mod's classes, all access happens through cached
 * reflection handles resolved on first use.</p>
 *
 * @see <a href="https://github.com/talhanation/siegeweapons">Siege Weapons source</a>
 * @see <a href="https://github.com/talhanation/recruits">Recruits source</a>
 */
public final class SiegeIntegration {

    public static final String SIEGE_WEAPONS_MOD_ID = "siegeweapons";
    public static final String RECRUITS_MOD_ID = "recruits";
    public static final ResourceLocation SIEGE_ENGINEER_ID =
            new ResourceLocation(RECRUITS_MOD_ID, "siege_engineer");

    private static Boolean siegeWeaponsPresent;
    private static Boolean recruitsPresent;
    private static Boolean reflectionInitialised;

    // Recruits SiegeEngineerEntity handles
    private static java.lang.reflect.Field catapultControllerField;
    private static java.lang.reflect.Field ballistaControllerField;
    private static Method tryMountMethod;

    private SiegeIntegration() {}

    /** @return true when {@code siegeweapons} is on the mod list. */
    public static boolean isSiegeWeaponsPresent() {
        if (siegeWeaponsPresent == null) {
            siegeWeaponsPresent = ModList.get() != null && ModList.get().isLoaded(SIEGE_WEAPONS_MOD_ID);
        }
        return siegeWeaponsPresent;
    }

    /** @return true when {@code recruits} is on the mod list. */
    public static boolean isRecruitsPresent() {
        if (recruitsPresent == null) {
            recruitsPresent = ModList.get() != null && ModList.get().isLoaded(RECRUITS_MOD_ID);
        }
        return recruitsPresent;
    }

    /**
     * @return true when both mods are present, meaning we can spawn a
     * Recruits siege engineer that will operate a siege-weapon vehicle.
     */
    public static boolean isSiegeEngineerAvailable() {
        return isRecruitsPresent() && isSiegeWeaponsPresent();
    }

    /**
     * Locate a Siege Weapons entity type by registry id.
     * @return the entity type, or empty when the registry lookup fails.
     */
    public static Optional<EntityType<?>> siegeEntityType(SiegeEngineType type) {
        if (!type.requiresSiegeWeapons() || !isSiegeWeaponsPresent()) return Optional.empty();
        ResourceLocation rl = new ResourceLocation(type.registryId());
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return Optional.empty();
        return Optional.ofNullable(ForgeRegistries.ENTITY_TYPES.getValue(rl));
    }

    /**
     * Ground-clearance dimensions for a siege engine. Both fields are in
     * block units and always at least 1. The horizontal radius is the number
     * of columns to inspect on each side of the deployment center, so a
     * catapult (4-wide) reports radius 2, and a ballista (2-wide) reports
     * radius 1. Block height is the number of vertical block layers the
     * vehicle intersects after spawning at the selected block's center,
     * including the half-block vertical offset.
     */
    public record Footprint(int horizontalRadius, int blockHeight) {}

    /**
     * Read the actual {@link net.minecraft.world.entity.EntityDimensions} of
     * the registered siege vehicle and translate it into a block-space
     * clearance footprint. Falls back to a 3x3x3 default when the entity
     * type is unavailable (Siege Weapons not installed, or a non-vehicle
     * type like SAPPER_CHARGE).
     */
    public static Footprint footprintOf(SiegeEngineType type) {
        Optional<EntityType<?>> et = siegeEntityType(type);
        if (et.isEmpty()) return new Footprint(1, 3);
        var dims = et.get().getDimensions();
        return footprint(dims.width, dims.height);
    }

    /** Translate raw entity dimensions into the clearance grid used at deployment. */
    static Footprint footprint(float width, float height) {
        // The spawn is centered at x/z + 0.5 inside the selected block. The
        // central block already contributes one unit of width, so only the
        // remaining width must be split and rounded across both sides. A
        // 2.5-wide vehicle therefore occupies three columns (radius 1), while
        // a 4-wide catapult occupies five (radius 2).
        int radius = (int) Math.ceil((width - 1.0F) / 2.0F);
        // Deployment uses Vec3.atCenterOf, placing the bounding box bottom
        // at y + 0.5. Include that offset so an exactly 4-block-tall vehicle
        // checks layers y through y + 4 rather than stopping at y + 3.
        int blockHeight = (int) Math.ceil(height + 0.5F);
        return new Footprint(Math.max(1, radius), Math.max(1, blockHeight));
    }

    /**
     * Spawn a siege-weapons vehicle at {@code pos} facing the given yaw.
     * The entity is server-side ready but has no passenger.
     * @return the spawned entity, or empty on failure.
     */
    public static Optional<Entity> spawnSiegeVehicle(ServerLevel level, SiegeEngineType type,
                                                     Vec3 pos, float yaw) {
        Optional<EntityType<?>> et = siegeEntityType(type);
        if (et.isEmpty()) return Optional.empty();
        Entity vehicle = et.get().create(level);
        if (vehicle == null) return Optional.empty();
        vehicle.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        {
            var box = vehicle.getBoundingBox();
            for (BlockPos corner : occupiedGroundCorners(box, pos.y)) {
                if (!level.hasChunkAt(corner) || !level.getWorldBorder().isWithinBounds(corner)
                        || !level.getFluidState(corner).isEmpty()
                        || !level.getBlockState(corner.below()).isFaceSturdy(level, corner.below(), net.minecraft.core.Direction.UP)) {
                    vehicle.discard();
                    return Optional.empty();
                }
            }
            if (!level.noCollision(vehicle) || !level.getEntities(vehicle, box).isEmpty()) {
                vehicle.discard();
                return Optional.empty();
            }
        }
        if (!level.addFreshEntity(vehicle)) { vehicle.discard(); return Optional.empty(); }
        return Optional.of(vehicle);
    }

    /**
     * Ground corners touched by an entity AABB. Minecraft AABBs use an
     * exclusive maximum boundary, so move exact max coordinates one double
     * downward before converting them to block positions. This keeps the
     * native spawn check aligned with {@link #footprint(float, float)} for an
     * exact three-block-wide vehicle.
     */
    static List<BlockPos> occupiedGroundCorners(AABB box, double y) {
        double maxX = Math.nextDown(box.maxX);
        double maxZ = Math.nextDown(box.maxZ);
        return List.of(
                BlockPos.containing(box.minX, y, box.minZ),
                BlockPos.containing(maxX, y, maxZ),
                BlockPos.containing(box.minX, y, maxZ),
                BlockPos.containing(maxX, y, box.minZ));
    }

    /**
     * Mount a Recruits SiegeEngineer on a siege-weapons vehicle and hand
     * control off to the appropriate {@code SiegeWeaponXController}.
     *
     * @param engineer the {@code SiegeEngineerEntity} instance (typed as {@link Mob})
     * @param vehicle  the siege-weapons entity (catapult or ballista)
     * @return true when mount + controller attach succeeded.
     */
    public static boolean assignSiegeEngineer(Mob engineer, Entity vehicle) {
        if (!isSiegeEngineerAvailable() || engineer == null || vehicle == null) return false;
        if (!initReflection()) return false;
        try {
            // Pick catapult or ballista controller based on the vehicle's registry key.
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
            if (key == null) return false;
            java.lang.reflect.Field controllerField;
            if ("catapult".equals(key.getPath())) controllerField = catapultControllerField;
            else if ("ballista".equals(key.getPath())) controllerField = ballistaControllerField;
            else return engineer.startRiding(vehicle, true); // Non-ranged engines don't need a Recruits controller.
            Object controller = controllerField.get(engineer);
            return mountWithController(engineer, vehicle, controller, tryMountMethod);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Recruits API changed under us. Log at debug so server owners
            // running with debug logs enabled can see why siege engineers
            // stopped operating engines, without spamming production logs.
            FactionLogger.LOG.debug("assignSiegeEngineer reflection failed: {}", e.toString());
            return false;
        }
    }

    /** A failed native attach must leave the crew dismounted so deployment can retry. */
    static boolean mountWithController(Mob engineer, Entity vehicle, Object controller, Method mountMethod) {
        if (controller == null) return false;
        boolean attached = false;
        try {
            // Resolve the API before changing passenger state.
            var activeController = engineer.getClass().getField("siegeController");
            var getSiegeEntity = controller.getClass().getMethod("getSiegeEntity");
            if (!engineer.startRiding(vehicle, true)) return false;
            mountMethod.invoke(controller, vehicle);
            if (engineer.getVehicle() != vehicle || !vehicle.equals(getSiegeEntity.invoke(controller))) return false;
            activeController.set(engineer, controller);
            attached = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            FactionLogger.LOG.debug("Siege controller attach failed: {}", e.toString());
            return false;
        } finally {
            if (!attached && engineer.getVehicle() == vehicle) engineer.stopRiding();
        }
    }

    /**
     * Spawn a Recruits SiegeEngineer at {@code pos}. Caller is expected to
     * configure it as a raid participant (persistent-data tag, hostility)
     * before the engineer is mounted.
     * @return the spawned mob, or empty on failure.
     */
    public static Optional<Mob> spawnSiegeEngineer(ServerLevel level, Vec3 pos, String team, Entity vehicle, SiegeEngineType type) {
        return spawnSiegeEngineer(level,pos,team,vehicle,type,true);
    }
    public static Optional<Mob> spawnSiegeEngineer(ServerLevel level, Vec3 pos, String team, Entity vehicle, SiegeEngineType type, boolean mount) {
        if (!isRecruitsPresent()) return Optional.empty();
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(SIEGE_ENGINEER_ID)) return Optional.empty();
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(SIEGE_ENGINEER_ID);
        if (et == null) return Optional.empty();
        Entity entity = et.create(level);
        if (!(entity instanceof Mob mob)) return Optional.empty();
        try {
            mob.moveTo(pos.x, pos.y, pos.z, vehicle.getYRot(), 0F);
            if(!mount) {
                var raid=com.devfarinsky.siegeoverhaul.RaidSavedData.get(level.getServer()).raids.get(team);
                var safe=raid==null?null:com.devfarinsky.siegeoverhaul.camp.WarGate.spawn(level,raid,mob);
                if(safe==null){mob.discard();return Optional.empty();}
                mob.moveTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5,vehicle.getYRot(),0F);
            }
            com.devfarinsky.siegeoverhaul.compat.EngineerSpawnCompatibility.initialize(level, mob);
            com.devfarinsky.siegeoverhaul.RecruitsBridge.configureHostileRaidRecruit(mob);
            com.devfarinsky.siegeoverhaul.RecruitsBridge.assignToRaidersFaction(mob);
            mob.setPersistenceRequired();
            mob.setCanPickUpLoot(false);
            mob.getPersistentData().putString(SiegeDeployment.TEAM_TAG, team);
            mob.getPersistentData().putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM, team);
            mob.getClass().getMethod("setListen", boolean.class).invoke(mob, false);
            mob.getClass().getMethod("setFollowState", int.class).invoke(mob, 0);
            net.minecraft.world.SimpleContainer inventory = (net.minecraft.world.SimpleContainer)
                    mob.getClass().getMethod("getInventory").invoke(mob);
            var ammunition = type == SiegeEngineType.BALLISTA ? ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("siegeweapons", "ballista_projectile_item")) : net.minecraft.world.item.Items.COBBLESTONE;
            if (ammunition == null || ammunition == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("Missing siege ammunition");
            inventory.addItem(new net.minecraft.world.item.ItemStack(ammunition, 64));
            inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 16));
            if ((!mount && (!level.hasChunkAt(mob.blockPosition()) || !level.noCollision(mob)))
                    || (mount && !assignSiegeEngineer(mob, vehicle)) || !level.addFreshEntity(mob)) {
                mob.stopRiding(); mob.discard(); return Optional.empty();
            }
            return Optional.of(mob);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            mob.stopRiding(); mob.discard();
            FactionLogger.LOG.warn("Could not initialize a supplied siege operator for {}", team, ex);
            return Optional.empty();
        }
    }

    static double standOff(SiegeEngineType type) { return type==SiegeEngineType.CATAPULT?48:24; }

    /** Drive the native vehicle controller toward the core; it retains native combat targeting. */
    public static void advanceEngineer(Mob engineer, net.minecraft.core.BlockPos objective) {
        if (!engineer.isPassenger()) { EngineerAdvanceOrders.cancelTravel(engineer); return; }
        long now = engineer.level().getGameTime();
        Vec3 delta = Vec3.atCenterOf(objective).subtract(engineer.position()).multiply(1,0,1);
        double distance = delta.length();
        try {
            var vehicleKey=ForgeRegistries.ENTITY_TYPES.getKey(engineer.getVehicle().getType());
            SiegeEngineType type=vehicleKey!=null && vehicleKey.getPath().equals("catapult")?SiegeEngineType.CATAPULT:SiegeEngineType.BALLISTA;
            double standOff=standOff(type);
            if (EngineerAdvanceOrders.arrived(type, distance)) {
                if (Boolean.TRUE.equals(engineer.getClass().getMethod("getShouldMovePos").invoke(engineer))) {
                    try { EngineerAdvanceOrders.stop(engineer); }
                    finally { engineer.getClass().getMethod("setShouldMovePos", boolean.class).invoke(engineer, false); }
                }
                EngineerAdvanceOrders.restore(engineer);
                return;
            }
            if (engineer.getPersistentData().contains("SiegeAdvanceAt")
                    && now - engineer.getPersistentData().getLong("SiegeAdvanceAt") < 100) return;
            engineer.getPersistentData().putLong("SiegeAdvanceAt", now);
            // Engines are wide: a waypoint straight ahead wedges them against
            // terrain they cannot squeeze past. Track progress and sweep the
            // waypoint sideways until the column is moving again.
            var data = engineer.getPersistentData();
            int stalls = EngineRoute.stalls(data.getInt(EngineRoute.STALLS),
                    data.getDouble(EngineRoute.LAST_DISTANCE), distance);
            data.putInt(EngineRoute.STALLS, stalls);
            data.putDouble(EngineRoute.LAST_DISTANCE, distance);
            Vec3 forward = delta.normalize();
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            Vec3 step = engineer.position()
                    .add(forward.scale(Math.min(EngineRoute.step(stalls), distance - standOff)))
                    .add(side.scale(EngineRoute.detour(stalls)));
            net.minecraft.core.BlockPos ground = net.minecraft.core.BlockPos.containing(step);
            if (!(engineer.level() instanceof ServerLevel level) || !level.hasChunkAt(ground)) {
                EngineerAdvanceOrders.restore(engineer); return;
            }
            if (EngineRoute.shouldLift(stalls, com.devfarinsky.siegeoverhaul.RaidConfig.SIEGE_ENGINE_STALL_PASSES.get())
                    && lift(level, engineer.getVehicle(), forward)) {
                data.putInt(EngineRoute.STALLS, 0);
            }
            ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground);
            if (!Integer.valueOf(0).equals(engineer.getClass().getMethod("getFollowState").invoke(engineer)))
                engineer.getClass().getMethod("setFollowState", int.class).invoke(engineer, 0);
            engineer.getClass().getMethod("setMovePos", net.minecraft.core.BlockPos.class).invoke(engineer, ground);
            engineer.getClass().getMethod("setShouldMovePos", boolean.class).invoke(engineer, true);
            EngineerAdvanceOrders.travel(engineer, engineer.getPersistentData());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            EngineerAdvanceOrders.restore(engineer);
            FactionLogger.LOG.debug("Native siege advance unavailable: {}", ex.toString());
        }
    }

    /**
     * Last resort for an engine that repeated detours could not free: set it
     * down a short way further along its own march line. Only ever moves the
     * engine forward onto loaded, solid, unoccupied ground at roughly its own
     * height, so it can never drop a catapult through terrain or into a wall.
     */
    static boolean lift(ServerLevel level, Entity vehicle, Vec3 forward) {
        if (vehicle == null || !com.devfarinsky.siegeoverhaul.RaidConfig.SIEGE_ENGINE_UNSTICK.get()) return false;
        for (int distance = 12; distance >= 4; distance -= 4) {
            Vec3 ahead = vehicle.position().add(forward.scale(distance));
            BlockPos at = BlockPos.containing(ahead);
            if (!level.hasChunkAt(at) || !level.getWorldBorder().isWithinBounds(at)) continue;
            BlockPos ground = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            if (Math.abs(ground.getY() - vehicle.blockPosition().getY()) > 3) continue;
            BlockPos floor = ground.below();
            if (!level.getFluidState(ground).isEmpty()
                    || !level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)) continue;
            Vec3 target = new Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
            AABB box = vehicle.getBoundingBox().move(target.subtract(vehicle.position()));
            if (!level.noCollision(vehicle, box)) continue;
            vehicle.moveTo(target.x, target.y, target.z, vehicle.getYRot(), vehicle.getXRot());
            FactionLogger.LOG.debug("Freed a stuck siege engine at {}", ground);
            return true;
        }
        return false;
    }

    private static boolean initReflection() {        if (reflectionInitialised != null) return reflectionInitialised;
        try {
            Class<?> engineerClass = Class.forName(
                    "com.talhanation.recruits.entities.SiegeEngineerEntity");
            catapultControllerField = engineerClass.getField("catapultController");
            ballistaControllerField = engineerClass.getField("ballistaController");
            Class<?> controllerClass = Class.forName(
                    "com.talhanation.recruits.entities.ai.controller.siegeengineer.ISiegeController");
            tryMountMethod = controllerClass.getMethod("tryMount", Entity.class);
            reflectionInitialised = true;
        } catch (ReflectiveOperationException e) {
            FactionLogger.LOG.debug("SiegeIntegration reflection init failed: {}", e.toString());
            reflectionInitialised = false;
        }
        return reflectionInitialised;
    }
}
