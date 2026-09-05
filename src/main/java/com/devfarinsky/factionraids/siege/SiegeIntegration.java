package com.devfarinsky.factionraids.siege;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.devfarinsky.factionraids.FactionLogger;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
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
        if (!level.addFreshEntity(vehicle)) return Optional.empty();
        return Optional.of(vehicle);
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
            if (!engineer.startRiding(vehicle, true)) return false;
            // Pick catapult or ballista controller based on the vehicle's registry key.
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
            if (key == null) return true;
            java.lang.reflect.Field controllerField;
            if ("catapult".equals(key.getPath())) controllerField = catapultControllerField;
            else if ("ballista".equals(key.getPath())) controllerField = ballistaControllerField;
            else return true; // Non-ranged engines don't need a Recruits controller.
            Object controller = controllerField.get(engineer);
            if (controller == null) return true;
            tryMountMethod.invoke(controller, vehicle);
            return true;
        } catch (ReflectiveOperationException e) {
            // Recruits API changed under us. Log at debug so server owners
            // running with debug logs enabled can see why siege engineers
            // stopped operating engines, without spamming production logs.
            FactionLogger.LOG.debug("assignSiegeEngineer reflection failed: {}", e.toString());
            return false;
        }
    }

    /**
     * Spawn a Recruits SiegeEngineer at {@code pos}. Caller is expected to
     * configure it as a raid participant (persistent-data tag, hostility)
     * before the engineer is mounted.
     * @return the spawned mob, or empty on failure.
     */
    public static Optional<Mob> spawnSiegeEngineer(ServerLevel level, Vec3 pos) {
        if (!isRecruitsPresent()) return Optional.empty();
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(SIEGE_ENGINEER_ID)) return Optional.empty();
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(SIEGE_ENGINEER_ID);
        if (et == null) return Optional.empty();
        Entity entity = et.create(level);
        if (!(entity instanceof Mob mob)) return Optional.empty();
        mob.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        if (!level.addFreshEntity(mob)) return Optional.empty();
        return Optional.of(mob);
    }

    private static boolean initReflection() {
        if (reflectionInitialised != null) return reflectionInitialised;
        try {
            Class<?> engineerClass = Class.forName(
                    "com.talhanation.recruits.entities.SiegeEngineerEntity");
            catapultControllerField = engineerClass.getField("catapultController");
            ballistaControllerField = engineerClass.getField("ballistaController");
            Class<?> controllerClass = Class.forName(
                    "com.talhanation.recruits.entities.ai.controller.siegeengineer.SiegeWeaponCatapultController");
            tryMountMethod = controllerClass.getMethod("tryMount", Entity.class);
            reflectionInitialised = true;
        } catch (ReflectiveOperationException e) {
            FactionLogger.LOG.debug("SiegeIntegration reflection init failed: {}", e.toString());
            reflectionInitialised = false;
        }
        return reflectionInitialised;
    }
}
