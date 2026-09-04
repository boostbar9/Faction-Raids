package com.devfarinsky.factionraids;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Optional, class-link-free integrations for TalhaNation's companion mods.
 *
 * Keeping this boundary limited to registry namespaces, vanilla teams,
 * passengers and a reflected public ownership getter means Faction Raids can
 * still load when any optional mod is absent or changes its internal classes.
 */
public final class OptionalCompatBridge {
    public static final String WORKERS = "workers";
    public static final String SMALL_SHIPS = "smallships";
    public static final String SIEGE_WEAPONS = "siegeweapons";
    private static final Set<String> WORKER_ENTITY_PATHS = Set.of(
            "animal_farmer", "lumberjack", "farmer", "miner", "builder", "merchant",
            "fisherman", "cook", "courier");

    private static final Map<Class<?>, Optional<Method>> OWNER_METHODS = new HashMap<>();

    public static boolean isWorker(Entity entity) {
        ResourceLocation id = entityTypeId(entity);
        return RaidConfig.ENABLE_WORKERS_COMPAT.get() && loaded(WORKERS) && id != null &&
                WORKERS.equals(id.getNamespace()) && WORKER_ENTITY_PATHS.contains(id.getPath());
    }

    public static boolean isSmallShip(Entity entity) {
        return RaidConfig.ENABLE_SMALLSHIPS_COMPAT.get() && loaded(SMALL_SHIPS) &&
                hasNamespace(entity, SMALL_SHIPS);
    }

    public static boolean isSiegeWeapon(Entity entity) {
        return RaidConfig.ENABLE_SIEGEWEAPONS_COMPAT.get() && loaded(SIEGE_WEAPONS) &&
                hasNamespace(entity, SIEGE_WEAPONS);
    }

    public static CompatSnapshot scan(ServerLevel level, AABB area, String factionKey,
                                      Iterable<UUID> factionMembers) {
        Set<UUID> members = new HashSet<>();
        factionMembers.forEach(members::add);
        int workers = 0;
        int ships = 0;
        int siegeWeapons = 0;
        for (Entity entity : level.getEntities((Entity) null, area,
                candidate -> candidate.isAlive() && isSupported(candidate))) {
            if (isWorker(entity)) {
                if (belongsToFaction(entity, factionKey, members, true)) workers++;
            } else if (isSmallShip(entity)) {
                if (belongsToFaction(entity, factionKey, members, false)) ships++;
            } else if (isSiegeWeapon(entity) && belongsToFaction(entity, factionKey, members, false)) {
                siegeWeapons++;
            }
        }
        return new CompatSnapshot(workers, ships, siegeWeapons);
    }

    public static String diagnosticStatus() {
        return "Workers " + status(WORKERS, RaidConfig.ENABLE_WORKERS_COMPAT.get()) +
                ", Small Ships " + status(SMALL_SHIPS, RaidConfig.ENABLE_SMALLSHIPS_COMPAT.get()) +
                ", Siege Weapons " + status(SIEGE_WEAPONS, RaidConfig.ENABLE_SIEGEWEAPONS_COMPAT.get());
    }

    private static boolean isSupported(Entity entity) {
        return isWorker(entity) || isSmallShip(entity) || isSiegeWeapon(entity);
    }

    private static boolean belongsToFaction(Entity entity, String factionKey, Set<UUID> members,
                                            boolean allowUncrewedOwner) {
        if (matchesTeam(entity, factionKey)) return true;
        if (allowUncrewedOwner && ownerUuid(entity).filter(members::contains).isPresent()) return true;
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (members.contains(passenger.getUUID()) || matchesTeam(passenger, factionKey)) return true;
        }
        return false;
    }

    private static boolean matchesTeam(Entity entity, String factionKey) {
        return factionKey.startsWith("team:") && entity.getTeam() != null &&
                factionKey.substring(5).equals(entity.getTeam().getName());
    }

    private static Optional<UUID> ownerUuid(Entity entity) {
        Optional<Method> method;
        synchronized (OWNER_METHODS) {
            method = OWNER_METHODS.computeIfAbsent(entity.getClass(), OptionalCompatBridge::findOwnerMethod);
        }
        if (method.isEmpty()) return Optional.empty();
        try {
            Object value = method.get().invoke(entity);
            if (value instanceof UUID uuid) return Optional.of(uuid);
            if (value instanceof Entity owner) return Optional.of(owner.getUUID());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional compatibility must never interrupt a raid tick.
        }
        return Optional.empty();
    }

    private static Optional<Method> findOwnerMethod(Class<?> type) {
        for (String name : new String[]{"getOwnerUUID", "getOwnerUuid", "getOwner"}) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0) return Optional.of(method);
            } catch (ReflectiveOperationException ignored) {
                // Try the next stable ownership spelling.
            }
        }
        return Optional.empty();
    }

    private static boolean hasNamespace(Entity entity, String namespace) {
        ResourceLocation id = entityTypeId(entity);
        return id != null && namespace.equals(id.getNamespace());
    }

    private static ResourceLocation entityTypeId(Entity entity) {
        return ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
    }

    private static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static String status(String modId, boolean enabled) {
        if (!loaded(modId)) return "not installed";
        return enabled ? "active" : "disabled";
    }

    public record CompatSnapshot(int workers, int ships, int siegeWeapons) {
        public static final CompatSnapshot EMPTY = new CompatSnapshot(0, 0, 0);

        public int crewedAssets() {
            return ships + siegeWeapons;
        }
    }

    private OptionalCompatBridge() {}
}
