package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

/** Temporarily give travel priority over the native controller's stationary firing loop. */
final class EngineerAdvanceOrders {
    private static final String SAVED_RANGED = "SiegeAdvanceSavedRanged";
    private EngineerAdvanceOrders() {}

    // Native REACH is a squared distance: 50 for ballistas and 115 for catapults.
    // Accept that arrival tolerance instead of repeatedly issuing an already-reached final step.
    static boolean arrived(SiegeEngineType type, double distance) {
        return distance <= SiegeIntegration.standOff(type) + Math.sqrt(type == SiegeEngineType.CATAPULT ? 115 : 50);
    }

    static void travel(Object engineer, CompoundTag data) throws ReflectiveOperationException {
        var setter = engineer.getClass().getMethod("setShouldRanged", boolean.class);
        if (!data.contains(SAVED_RANGED)) {
            boolean previous = (boolean) engineer.getClass().getMethod("getShouldRanged").invoke(engineer);
            data.putBoolean(SAVED_RANGED, previous);
        }
        setter.invoke(engineer, false);
    }

    static void restore(Object engineer, CompoundTag data) throws ReflectiveOperationException {
        if (!data.contains(SAVED_RANGED)) return;
        engineer.getClass().getMethod("setShouldRanged", boolean.class).invoke(engineer, data.getBoolean(SAVED_RANGED));
        data.remove(SAVED_RANGED);
    }

    static void restore(Mob engineer) {
        try { restore(engineer, engineer.getPersistentData()); }
        catch (ReflectiveOperationException | RuntimeException ex) {
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.debug("Could not restore siege operator firing", ex);
        }
    }

    static void stop(Object engineer) throws ReflectiveOperationException {
        Object controller = engineer.getClass().getField("siegeController").get(engineer);
        if (controller == null) return;
        // reset() stops the vehicle, but does not clear the controller's latched steering fields.
        for (String field : new String[]{"forward", "left", "right"})
            controller.getClass().getField(field).setBoolean(controller, false);
        try { controller.getClass().getField("backward").setBoolean(controller, false); }
        catch (NoSuchFieldException ignored) { /* Ballistas only travel forwards. */ }
        controller.getClass().getMethod("reset").invoke(controller);
    }
}
