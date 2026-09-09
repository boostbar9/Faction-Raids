package com.devfarinsky.siegeoverhaul.formations;

import com.devfarinsky.siegeoverhaul.FactionLogger;
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
            for (Object recruit : recruitList) ((Mob) recruit).getPersistentData().putBoolean(
                    com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            FactionLogger.LOG.debug("Formation dispatch failed for {}: {}", formation, e.toString());
            return false;
        }
    }

    /** Native hold orders at reachable ground cells only; no surface snapping or teleporting. */
    public static boolean applyTactical(net.minecraft.server.level.ServerLevel level,Formation shape,Vec3 forward,Vec3 target,List<Mob> units,String defendingTeam) {
        ensureInitialized(); boolean applied=false;
        for(int i=0;i<units.size();i++) {
            Mob mob=units.get(i);
            if(recruitEntityClass==null || !recruitEntityClass.isInstance(mob))continue;
            // Renew near the waypoint, not in the middle of a useful walking leg.
            if(mob.getPersistentData().getBoolean(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH)) {
                try {
                    Object old=mob.getClass().getMethod("getHoldPos").invoke(mob);
                    if(old instanceof Vec3 hold && mob.distanceToSqr(hold)>9 && !mob.getNavigation().isDone()
                            && !mob.horizontalCollision && !com.devfarinsky.siegeoverhaul.raid.MarchProgress.shouldRepath(mob,hold,level.getGameTime())) {
                        applied=true;continue;
                    }
                } catch(ReflectiveOperationException ignored) { }
            }
            Vec3 offset=FormationTactics.offset(shape,i,units.size());
            Vec3 slot=target.add(-forward.z*offset.x+forward.x*offset.z,0,forward.x*offset.x+forward.z*offset.z);
            // A rear rank must never walk backwards to dress a moving formation.
            double ahead=slot.subtract(mob.position()).dot(forward);
            if(ahead<2)slot=slot.add(forward.scale(2-ahead));
            net.minecraft.core.BlockPos ground=null;
            for(int dy:new int[]{0,1,-1}) {
                var p=net.minecraft.core.BlockPos.containing(slot.x,mob.getY()+dy,slot.z);
                if(!level.hasChunkAt(p)
                        || !level.getFluidState(p).isEmpty() || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP))continue;
                if(!level.noCollision(mob,mob.getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(mob.position()))))continue;
                var path=mob.getNavigation().createPath(p,0);
                if(path!=null && path.canReach()) { ground=p;break; }
            }
            if(ground==null) { release(mob);continue; }
            try {
                mob.getClass().getMethod("setHoldPos",Vec3.class).invoke(mob,Vec3.atBottomCenterOf(ground));
                mob.getClass().getMethod("setFollowState",int.class).invoke(mob,3);
                mob.getClass().getField("isInFormation").setBoolean(mob,true);
                mob.getClass().getField("holdFormation").setBoolean(mob,false);
                mob.getPersistentData().putBoolean(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH,true);
                applied=true;
            } catch(ReflectiveOperationException | RuntimeException ex) { release(mob); }
        }
        return applied;
    }

    /** Release the hold-position order before combat or independent navigation takes over. */
    public static void release(Mob mob) {
        if (!mob.getPersistentData().getBoolean(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH)) return;
        // Vanilla auxiliaries have no native formation API. A legacy-load cleanup
        // marker must not make their objective navigator believe a formation owns it.
        ensureInitialized();
        if (recruitEntityClass == null || !recruitEntityClass.isInstance(mob)) {
            mob.getPersistentData().remove(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH);
            return;
        }
        try {
            mob.getClass().getMethod("setFollowState", int.class).invoke(mob, 0);
            mob.getClass().getField("isInFormation").setBoolean(mob, false);
            mob.getClass().getField("holdFormation").setBoolean(mob, false);
            mob.getPersistentData().remove(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.debug("Could not release formation order", ex);
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
