package com.devfarinsky.siegeoverhaul.naval.prototype;

import com.talhanation.recruits.entities.CaptainEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** QA-only prototype: short-lived captain commands; the native ship owns all physics. */
public final class FinalShipsTurnCommands {
    public interface ShipHook {}
    public interface RecruitsHook {}
    private record Turn(UUID captain, long tick, boolean left, boolean right) {}
    private static final Map<Boat, Turn> COMMANDS = Collections.synchronizedMap(new WeakHashMap<>());
    private FinalShipsTurnCommands() {}

    public static CaptainEntity helmsman(Boat ship) {
        if (!(ship instanceof ShipHook) || !ship.isAlive() || ship.isRemoved()) return null;
        try {
            for (Object seat : (List<?>) call(ship, "getSeats")) {
                if (!"DRIVER".equals(String.valueOf(call(seat, "type")))) continue;
                int id = ((Number) call(seat, "id")).intValue();
                Object occupant = call(ship, "getSeatOccupant", new Class<?>[]{int.class}, id);
                if (occupant instanceof CaptainEntity captain && captain.isAlive() && captain.getVehicle() == ship
                        && captain.level() == ship.level()
                        && Boolean.TRUE.equals(call(ship, "canDrive", new Class<?>[]{Entity.class}, captain))) return captain;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static boolean motionAllowed(Boat ship) {
        if (ship.level().isClientSide || !ship.isInWater()) return false;
        try {
            for (String state : List.of("isLocked", "isShipLeashed", "isInDockyardWork", "isSinking", "isSunken"))
                if (Boolean.TRUE.equals(call(ship, state))) return false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    public static void issue(Boat ship, CaptainEntity captain, boolean left, boolean right) {
        if (ship.level().isClientSide) return;
        if (captain != helmsman(ship) || !motionAllowed(ship)) {
            Turn previous = COMMANDS.get(ship);
            if (previous != null && previous.captain.equals(captain.getUUID())) COMMANDS.remove(ship);
            return;
        }
        COMMANDS.put(ship, new Turn(captain.getUUID(), ship.level().getGameTime(), left, right));
    }

    /** Read without consumption: native speed and turning both query the flags in one tick. */
    public static boolean requested(Boat ship, boolean left) {
        if (ship.level().isClientSide) return false;
        Turn turn = COMMANDS.get(ship);
        if (turn == null) return false;
        CaptainEntity captain = helmsman(ship);
        long now = ship.level().getGameTime();
        if (captain == null || !turn.captain.equals(captain.getUUID()) || !motionAllowed(ship)
                || now < turn.tick || now - turn.tick > 1) {
            COMMANDS.remove(ship);
            return false;
        }
        return left ? turn.left : turn.right;
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return call(target, name, new Class<?>[0]);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
}
