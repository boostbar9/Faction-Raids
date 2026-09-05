package com.devfarinsky.factionraids.naval;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Reflection-only bridge to the Small Ships mod. Mirrors the pattern used
 * in {@code SiegeIntegration}: no compile-time imports of external mod
 * classes and a silent fallback when the mod is absent.
 *
 * <p>Small Ships (Talhanation, mod id {@code smallships}) registers each
 * ship as a standard Forge {@link EntityType}. That's all we need: look
 * up the type by registry id and call
 * {@link EntityType#spawn(ServerLevel, BlockPos, MobSpawnType)}. The
 * ships derive from an internal {@code AbstractSailShip} but we never
 * touch that class \u2014 we treat the returned entity as a plain
 * {@link Entity} the rest of the naval pipeline already handles.</p>
 *
 * <p>The identifiers below cover the actively maintained 1.20.1 builds
 * (Cog + Brigg). Additional ship ids can be added later without any
 * change to callers.</p>
 */
public final class SmallShipsIntegration {

    public static final String MOD_ID = "smallships";

    /** Priority list of ship registry ids to try, largest first. */
    private static final String[] SHIP_IDS = new String[] {
            MOD_ID + ":brigg",     // large, multi-passenger warship
            MOD_ID + ":cog",       // smaller transport, safe fallback
    };

    private SmallShipsIntegration() {}

    public static boolean isPresent() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Attempt to spawn a Small Ships vessel at {@code pos}. Returns the
     * spawned entity, or empty when the mod is missing, no registered
     * ship type could be resolved, or the level rejected the spawn.
     *
     * @param preferLarge when true, try {@code brigg \u2192 cog}; when false,
     *                    {@code cog \u2192 brigg}.
     */
    public static Optional<Entity> spawnShip(ServerLevel level, BlockPos pos, boolean preferLarge) {
        if (!isPresent()) return Optional.empty();
        String[] order = preferLarge ? SHIP_IDS : new String[] { SHIP_IDS[1], SHIP_IDS[0] };
        for (String id : order) {
            EntityType<?> type = resolveType(id);
            if (type == null) continue;
            Entity ship = type.spawn(level, pos, MobSpawnType.EVENT);
            if (ship != null) return Optional.of(ship);
        }
        return Optional.empty();
    }

    /**
     * @return true when the mod is loaded AND at least one of our known
     * ship ids is registered. Used as a gate before announcing "warships
     * on the horizon" so we don't cry wolf on a bare install.
     */
    public static boolean hasAnyKnownShip() {
        if (!isPresent()) return false;
        for (String id : SHIP_IDS) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return true;
        }
        return false;
    }

    private static EntityType<?> resolveType(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            return rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
