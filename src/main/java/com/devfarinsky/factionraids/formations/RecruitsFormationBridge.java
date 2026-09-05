package com.devfarinsky.factionraids.formations;

import com.devfarinsky.factionraids.FactionLogger;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection bridge over {@code com.talhanation.recruits.util.FormationUtils}
 * for the formation styles Faction-Raids uses.
 *
 * <p>Recruits' formation API is bimodal: most public entries take a
 * {@link net.minecraft.server.level.ServerPlayer} (used to derive facing).
 * Enemy raiders have no player, so we call the {@code Vec3 forward}
 * overloads that {@code lineFormation} and {@code squareFormation} expose.
 *
 * <p>All calls degrade gracefully: if the method signatures drift or a
 * raider isn't an {@code AbstractRecruitEntity}, {@link #apply} logs at
 * debug and returns false without throwing.
 */
public final class RecruitsFormationBridge {

    private static final String RECRUIT_ENTITY = "com.talhanation.recruits.entities.AbstractRecruitEntity";
    private static final String FORMATION_UTILS = "com.talhanation.recruits.util.FormationUtils";

    private static Class<?> recruitEntityClass;
    private static Method lineFormationMethod;
    private static Method squareFormationMethod;
    private static boolean initialized;

    private RecruitsFormationBridge() {}

    /**
     * Command a group of raiders into the requested formation at
     * {@code target}, oriented along {@code forward}.
     *
     * @param formation shape to hold
     * @param forward   normalized advance vector (raider approach direction)
     * @param target    world-space point the formation should center on
     * @param raiders   mobs to command; non-recruit entries are ignored
     * @param hold      whether to make the recruits hold ground after arrival
     * @return true if the formation was dispatched to at least one recruit
     */
    public static boolean apply(Formation formation, Vec3 forward, Vec3 target,
                                 Iterable<? extends Mob> raiders, boolean hold) {
        if (formation == null || formation == Formation.NONE) return false;
        ensureInitialized();
        if (recruitEntityClass == null) return false;

        List<Object> recruitList = collectRecruits(raiders);
        if (recruitList.isEmpty()) return false;

        try {
            switch (formation) {
                case LINE -> {
                    if (lineFormationMethod == null) return false;
                    // signature: (Vec3 forward, List<AbstractRecruitEntity> recruits,
                    //             Vec3 targetPos, int maxInRow, double spacing, boolean hold)
                    lineFormationMethod.invoke(null, forward, recruitList, target, 8, 1.5D, hold);
                }
                case SQUARE -> {
                    if (squareFormationMethod == null) return false;
                    // signature: (Vec3 forward, List<AbstractRecruitEntity> recruits,
                    //             Vec3 targetPos, double spacing, boolean hold)
                    squareFormationMethod.invoke(null, forward, recruitList, target, 1.5D, hold);
                }
                default -> { return false; }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            FactionLogger.LOG.debug("Formation dispatch failed for {}: {}", formation, e.toString());
            return false;
        }
    }

    /** True when the bridge has resolved the reflection targets successfully. */
    public static boolean available() {
        ensureInitialized();
        return recruitEntityClass != null && (lineFormationMethod != null || squareFormationMethod != null);
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            recruitEntityClass = Class.forName(RECRUIT_ENTITY);
            Class<?> utilsClass = Class.forName(FORMATION_UTILS);
            // Vec3-forward overloads. If Recruits ever drops these we log and no-op.
            for (Method m : utilsClass.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("lineFormation") && p.length == 6 &&
                        p[0] == net.minecraft.world.phys.Vec3.class &&
                        p[1] == java.util.List.class &&
                        p[2] == net.minecraft.world.phys.Vec3.class &&
                        p[3] == int.class && p[4] == double.class && p[5] == boolean.class) {
                    lineFormationMethod = m;
                }
                if (m.getName().equals("squareFormation") && p.length == 5 &&
                        p[0] == net.minecraft.world.phys.Vec3.class &&
                        p[1] == java.util.List.class &&
                        p[2] == net.minecraft.world.phys.Vec3.class &&
                        p[3] == double.class && p[4] == boolean.class) {
                    squareFormationMethod = m;
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            FactionLogger.LOG.debug("Formations bridge unavailable: {}", e.toString());
            recruitEntityClass = null;
            lineFormationMethod = null;
            squareFormationMethod = null;
        }
    }

    /** Filters non-recruit entities out and casts to the raw list Recruits expects. */
    private static List<Object> collectRecruits(Iterable<? extends Mob> raiders) {
        List<Object> out = new ArrayList<>();
        for (Mob mob : raiders) {
            if (mob != null && mob.isAlive() && recruitEntityClass.isInstance(mob)) out.add(mob);
        }
        return out;
    }
}
