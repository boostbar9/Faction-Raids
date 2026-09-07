package com.devfarinsky.siegeoverhaul;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.function.Supplier;

/** Keeps a dependency's failed spawn initialization out of the world and wave ledger. */
public final class RaidMobSpawner {
    private RaidMobSpawner() {}

    /**
     * Recruits 1.15.2 siege engineers can throw a ClassCastException here because
     * RecruitPathNavigation no longer extends GroundPathNavigation. Do not keep
     * the half-initialized engineer or retry its initializer: its superclass has
     * already applied attribute modifiers. Replace it with a fresh vanilla raider.
     * The caller registers only the returned, fully initialized mob.
     */
    public static Mob initializeOrFallback(ServerLevel level, Mob candidate) {
        return initializeOrFallback(level, candidate, () -> EntityType.PILLAGER.create(level));
    }

    static Mob initializeOrFallback(ServerLevel level, Mob candidate, Supplier<? extends Mob> fallbackFactory) {
        if (initialize(level, candidate)) return candidate;

        Mob fallback;
        try {
            fallback = fallbackFactory.get();
        } catch (RuntimeException failure) {
            FactionLogger.LOG.error("Could not create a fallback raid mob", failure);
            return null;
        }
        if (fallback == null) return null;
        fallback.moveTo(candidate.getX(), candidate.getY(), candidate.getZ(),
                candidate.getYRot(), candidate.getXRot());
        return initialize(level, fallback) ? fallback : null;
    }

    private static boolean initialize(ServerLevel level, Mob mob) {
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.EVENT, null, null);
            return true;
        } catch (RuntimeException failure) {
            FactionLogger.LOG.warn("Raid mob {} failed spawn initialization; discarding it before registration",
                    mob.getType(), failure);
            mob.discard();
            return false;
        }
    }
}
