package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;

/** Non-toggleable version/API fence for the audited new protected entity path. */
final class WorkersConstructionRuntime {
    static final String WORKERS = "2.0.3", RECRUITS = "1.15.2";
    private WorkersConstructionRuntime() {}

    static boolean supportedVersions(String workers, String recruits) {
        return WORKERS.equals(workers) && RECRUITS.equals(recruits);
    }

    static String problem() {
        try {
            String workers = version("workers"), recruits = version("recruits");
            if (!supportedVersions(workers, recruits))
                return "Protected construction requires audited Workers 2.0.3 and Recruits 1.15.2; legacy jobs are unchanged.";
            return Capabilities.PROBLEM;
        } catch (RuntimeException | LinkageError unavailable) {
            return "Protected construction paused: companion runtime versions cannot be verified.";
        }
    }

    private static String version(String mod) {
        return ModList.get().getModContainerById(mod).map(container -> container.getModInfo().getVersion().toString()).orElse("");
    }

    private static final class Capabilities {
        private static final String PROBLEM = inspect();
        private static String inspect() {
            try {
                Class<?> area = Class.forName("com.talhanation.workers.entities.workarea.BuildArea");
                Class<?> worker = Class.forName("com.talhanation.workers.entities.BuilderEntity");
                Class<?> goal = Class.forName("com.talhanation.workers.entities.ai.BuilderWorkGoal");
                area.getConstructor(net.minecraft.world.entity.EntityType.class, net.minecraft.world.level.Level.class);
                for (String name : new String[]{"setStartBuild", "setFreeArea", "setAlwaysShowProjection"})
                    requireOverridable(area, name, boolean.class);
                requireOverridable(area, "setStructureNBT", CompoundTag.class);
                for (String name : new String[]{"setWidthSize", "setDepthSize", "setHeightSize"})
                    requireOverridable(area, name, int.class);
                requireOverridable(area, "setFacing", Direction.class);
                requireOverridable(area, "setPlayerUUID", java.util.UUID.class);
                requireOverridable(area, "setPlayerName", String.class);
                requireOverridable(area, "setTeamStringID", String.class);
                requireOverridable(area, "setTeamAccess", boolean.class);
                requireOverridable(area, "getOriginPos"); requireOverridable(area, "createArea");
                requireOverridable(area, "getStructureNBT");
                requireOverridable(area, "scanBreakArea");
                requireOverridable(area, "getStateFromPos", BlockPos.class);
                requireOverridable(area, "canWorkHere", Class.forName("com.talhanation.workers.entities.AbstractWorkerEntity"));
                for (String name : new String[]{"stackToPlace", "stackToPlaceMultiBlock", "stackToBreak", "stackToFree"})
                    if (!java.util.Stack.class.isAssignableFrom(area.getField(name).getType())) throw new NoSuchFieldException(name);
                if (!area.isAssignableFrom(worker.getField("currentBuildArea").getType())) throw new NoSuchFieldException("currentBuildArea");
                for (String name : new String[]{"stackToPlace", "stackToBreak", "stackToFree"})
                    if (!java.util.Stack.class.isAssignableFrom(goal.getField(name).getType())) throw new NoSuchFieldException(name);
                if (!goal.getField("state").getType().isEnum() || goal.getField("blockPos").getType() != BlockPos.class)
                    throw new NoSuchFieldException("native state");
                goal.getDeclaredField("workDone");
                Class<?> storage = Class.forName("com.talhanation.workers.entities.ai.GetNeededItemsFromStorage");
                if (!storage.getField("state").getType().isEnum()
                        || storage.getField("container").getType() != net.minecraft.world.Container.class
                        || storage.getField("chestPos").getType() != BlockPos.class)
                    throw new NoSuchFieldException("native storage state");
                storage.getField("worker"); storage.getField("storageArea");
                // Minecraft overrides are verified by the pinned Forge compile.
                // Do not reflect Mojang method-name strings: production remaps them.
                return null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return "Protected construction paused: required native server protection APIs are unavailable.";
            }
        }
        private static void requireOverridable(Class<?> type, String name, Class<?>... arguments)
                throws ReflectiveOperationException {
            var method = type.getMethod(name, arguments);
            if (java.lang.reflect.Modifier.isFinal(method.getModifiers()) || java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                throw new NoSuchMethodException(name + " is not a virtual instance method");
        }
    }
}
