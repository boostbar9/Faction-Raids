package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.IForgeRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Supplier;

/** Exact registry lookup and the audited Recruits 1.15.2 scout spawn-tail compatibility boundary. */
final class CampGuardSpawn {
    private static final String SCOUT = "com.talhanation.recruits.entities.ScoutEntity";
    private static final String RECRUIT = "com.talhanation.recruits.entities.AbstractRecruitEntity";
    private static final String NAVIGATION = "com.talhanation.recruits.entities.ai.navigation.RecruitPathNavigation";
    private static final String ASYNC_NAVIGATION = "com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation";
    private static final String GROUND_NAVIGATION = "net.minecraft.world.entity.ai.navigation.GroundPathNavigation";
    static final String SCOUT_TAIL_RECOVERED = "SiegeScoutSpawnTailRecovered";
    private CampGuardSpawn() {}

    static EntityType<?> exactType(IForgeRegistry<EntityType<?>> registry, ResourceLocation requested) {
        // Forge getValue returns its default entity for absent keys. Never instantiate that fallback.
        if (!registry.containsKey(requested)) return null;
        EntityType<?> type = registry.getValue(requested);
        return type != null && requested.equals(registry.getKey(type)) ? type : null;
    }

    static boolean nativeRecruit(Mob candidate, EntityType<?> expected) {
        try {
            return candidate.getType() == expected && Class.forName(RECRUIT, false,
                    CampGuardSpawn.class.getClassLoader()).isInstance(candidate);
        } catch (ClassNotFoundException | LinkageError unavailable) { return false; }
    }

    static void finalizeGuard(ServerLevel level, Mob guard) throws ReflectiveOperationException {
        var navigation = guard.getNavigation();
        BlockPos position = guard.blockPosition();
        try {
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(position), MobSpawnType.EVENT, null, null);
        } catch (ClassCastException failure) {
            String version = ModList.get().getModContainerById("recruits")
                    .map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
            if (guard.getNavigation() != navigation || !knownScoutCast(version, guard.getClass().getName(),
                    navigation.getClass().getName(), finalizeMethod(guard.getClass()), failure)
                    || guard.getClass() != Class.forName(SCOUT, false, CampGuardSpawn.class.getClassLoader())
                    || navigation.getClass() != Class.forName(NAVIGATION, false, CampGuardSpawn.class.getClassLoader())) throw failure;
            Tail tail = tail(guard, navigation);
            if (!tail.openDoors().getDeclaringClass().getName().equals(ASYNC_NAVIGATION)
                    || !tail.initSpawn().getDeclaringClass().getName().equals(SCOUT)) throw failure;
            // Bowman/AbstractRecruit initialization, including permanent random bonuses, has
            // already completed before Scout's bad cast. Resume only doors + its normal second
            // initSpawn call. Never replace navigation or repeat finalizeSpawn.
            finishTail(guard, navigation, tail, guard::getNavigation);
            guard.getPersistentData().putBoolean(SCOUT_TAIL_RECOVERED, true);
            FactionLogger.LOG.info("Completed native Recruits 1.15.2 scout spawn tail for {} with its existing navigator", guard.getUUID());
        }
    }

    static boolean knownScoutCast(String version, String entityClass, String navigationClass,
                                  String nativeFinalizeMethod, ClassCastException failure) {
        if (!"1.15.2".equals(version) || !SCOUT.equals(entityClass) || !NAVIGATION.equals(navigationClass)
                || failure.getCause() != null || failure.getStackTrace().length == 0) return false;
        StackTraceElement top = failure.getStackTrace()[0];
        String message = failure.getMessage();
        return SCOUT.equals(top.getClassName()) && nativeFinalizeMethod != null && !nativeFinalizeMethod.isBlank()
                && nativeFinalizeMethod.equals(top.getMethodName())
                && top.getLineNumber() == 94 && message != null
                && message.contains(NAVIGATION) && message.contains(GROUND_NAVIGATION);
    }

    /** Resolve the exact vanilla override signature in both development and reobfuscated runtimes. */
    static String finalizeMethod(Class<?> type) throws NoSuchMethodException {
        Class<?>[] parameters = {net.minecraft.world.level.ServerLevelAccessor.class,
                net.minecraft.world.DifficultyInstance.class, MobSpawnType.class,
                net.minecraft.world.entity.SpawnGroupData.class, net.minecraft.nbt.CompoundTag.class};
        var methods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == net.minecraft.world.entity.SpawnGroupData.class
                        && Arrays.equals(method.getParameterTypes(), parameters)).toList();
        if (methods.size() != 1) throw new NoSuchMethodException("Native scout finalizeSpawn signature changed");
        return methods.get(0).getName();
    }

    record Tail(Method openDoors, Method initSpawn) {}

    static Tail tail(Object scout, Object navigation) throws ReflectiveOperationException {
        Method doors = navigation.getClass().getMethod("setCanOpenDoors", boolean.class);
        Method init = scout.getClass().getMethod("initSpawn");
        if (Modifier.isStatic(doors.getModifiers()) || doors.getReturnType() != void.class
                || Modifier.isStatic(init.getModifiers()) || init.getReturnType() != void.class)
            throw new NoSuchMethodException("Native scout spawn-tail signature changed");
        return new Tail(doors, init);
    }

    static void finishTail(Object scout, Object navigation, Tail tail, Supplier<?> currentNavigation) throws ReflectiveOperationException {
        if (currentNavigation.get() != navigation) throw new IllegalStateException("Native scout navigator changed before spawn tail");
        tail.openDoors().invoke(navigation, true);
        tail.initSpawn().invoke(scout);
        if (currentNavigation.get() != navigation) throw new IllegalStateException("Native scout navigator changed during spawn tail");
    }
}
