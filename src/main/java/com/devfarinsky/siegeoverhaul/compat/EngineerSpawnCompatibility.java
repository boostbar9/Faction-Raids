package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Recruits 1.15.2 still casts the engineer's async navigator to GroundPathNavigation during spawn. */
public final class EngineerSpawnCompatibility {
    private static Field navigationField;
    private EngineerSpawnCompatibility() {}
    public static void initialize(ServerLevel level, Mob mob) {
        Runnable initializer = () -> mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT, null, null);
        if (mob.getClass().getName().equals("com.talhanation.recruits.entities.SiegeEngineerEntity")
                && !(mob.getNavigation() instanceof GroundPathNavigation)) {
            // Run the complete native initializer exactly once, including equipment and initSpawn.
            // The temporary navigator satisfies its obsolete cast; restore native async movement
            // before registering the entity. Do not retry after failure and stack spawn bonuses.
            withNavigation(mob, new GroundPathNavigation(mob, level), initializer);
        } else initializer.run();
    }
    static void withNavigation(Mob mob, PathNavigation temporary, Runnable initializer) {
        try {
            Field field = navigationField();
            Object original = field.get(mob);
            field.set(mob, temporary);
            try { initializer.run(); }
            finally { field.set(mob, original); }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot adapt native siege engineer spawn navigation", ex);
        }
    }
    static synchronized Field navigationField() throws NoSuchFieldException {
        if (navigationField != null) return navigationField;
        // Resolve by its unique declared type; field names differ between dev and production mappings.
        Field found = null;
        for (Field field : Mob.class.getDeclaredFields()) {
            if (field.getType() == PathNavigation.class && !Modifier.isStatic(field.getModifiers())) {
                if (found != null) throw new NoSuchFieldException("Ambiguous Mob navigation field");
                found = field;
            }
        }
        if (found == null) throw new NoSuchFieldException("Mob navigation field");
        found.setAccessible(true);
        return navigationField = found;
    }
}
