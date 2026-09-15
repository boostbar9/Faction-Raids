package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Recruits 1.15.2 still casts the engineer's async navigator to GroundPathNavigation during spawn. */
public final class EngineerSpawnCompatibility {
    /** Mojang mapped name and the 1.20.1 SRG name of {@code Mob.navigation}. */
    private static final Set<String> KNOWN_NAMES = Set.of("navigation", "f_21344_");
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
        Field field;
        try {
            field = navigationField(mob);
        } catch (ReflectiveOperationException ex) {
            // Losing the workaround must not cost us the spawn. Recruits only
            // needs it on versions carrying the obsolete cast, so run the
            // native initializer unchanged rather than refusing to spawn.
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.debug(
                    "Siege engineer navigation swap unavailable: {}", ex.toString());
            initializer.run();
            return;
        }
        try {
            Object original = field.get(mob);
            field.set(mob, temporary);
            try { initializer.run(); }
            finally { field.set(mob, original); }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot adapt native siege engineer spawn navigation", ex);
        }
    }

    static Field navigationField() throws NoSuchFieldException { return navigationField(null); }

    /**
     * Resolve {@code Mob}'s navigator field by its declared type, because the
     * field name differs between development and production mappings.
     *
     * <p>Other mods can mix extra {@link PathNavigation} fields into
     * {@code Mob}, so several candidates is a normal modpack situation. Prefer
     * the known field name, then the field actually holding this mob's live
     * navigator. Refusing outright on any ambiguity used to abort every siege
     * operator spawn on such packs.
     */
    static synchronized Field navigationField(Mob mob) throws NoSuchFieldException {
        if (navigationField != null) return navigationField;
        List<Field> candidates = new ArrayList<>();
        for (Field field : Mob.class.getDeclaredFields()) {
            if (field.getType() != PathNavigation.class) continue;
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            if (field.isSynthetic()) continue;
            candidates.add(field);
        }
        if (candidates.isEmpty()) throw new NoSuchFieldException("Mob navigation field");
        Field found = candidates.size() == 1 ? candidates.get(0) : disambiguate(candidates, mob);
        if (found == null) throw new NoSuchFieldException("Ambiguous Mob navigation field");
        found.setAccessible(true);
        // Only cache an answer that did not depend on one particular mob, so a
        // single identity match never becomes the permanent choice.
        if (candidates.size() == 1 || KNOWN_NAMES.contains(found.getName())) navigationField = found;
        return found;
    }

    private static Field disambiguate(List<Field> candidates, Mob mob) {
        for (Field field : candidates) if (KNOWN_NAMES.contains(field.getName())) return field;
        if (mob == null) return null;
        PathNavigation live = mob.getNavigation();
        if (live == null) return null;
        Field match = null;
        for (Field field : candidates) {
            field.setAccessible(true);
            try {
                if (field.get(mob) != live) continue;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                continue;
            }
            if (match != null) return null;
            match = field;
        }
        return match;
    }
}
