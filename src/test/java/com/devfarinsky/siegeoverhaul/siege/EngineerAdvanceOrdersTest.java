package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineerAdvanceOrdersTest extends com.devfarinsky.siegeoverhaul.MinecraftTestSupport {
    public static class Engineer {
        public boolean ranged = true, moving = true;
        public void setShouldMovePos(boolean value) { moving = value; }
        public Controller siegeController = new Controller();
        public boolean getShouldRanged() { return ranged; }
        public void setShouldRanged(boolean value) { ranged = value; }
    }
    public static class Controller {
        public boolean forward = true, left = true, right = true, backward = true;
        public int resets;
        public void reset() { resets++; }
    }
    @Test void repeatedTravelDoesNotLoseOriginalFireSettingAndReloadCanRestoreIt() throws Exception {
        var engineer = new Engineer(); var data = new CompoundTag();
        EngineerAdvanceOrders.travel(engineer, data);
        assertFalse(engineer.ranged);
        EngineerAdvanceOrders.travel(engineer, data);
        var reloaded = new Engineer(); reloaded.ranged = false;
        EngineerAdvanceOrders.restore(reloaded, data.copy());
        assertTrue(reloaded.ranged);
        EngineerAdvanceOrders.restore(engineer, data);
        assertTrue(engineer.ranged); assertTrue(data.isEmpty());
    }
    @Test void originallyDisabledFireStaysDisabledAndUnmanagedEngineersAreUntouched() throws Exception {
        var engineer = new Engineer(); engineer.ranged = false; var data = new CompoundTag();
        EngineerAdvanceOrders.travel(engineer, data);
        EngineerAdvanceOrders.restore(engineer, data);
        assertFalse(engineer.ranged);
        engineer.ranged = true;
        EngineerAdvanceOrders.restore(engineer, data);
        assertTrue(engineer.ranged);
    }
    @Test void nativeArrivalToleranceDoesNotLeaveCrewForeverTravellingOutsideExactRadius() {
        assertTrue(EngineerAdvanceOrders.arrived(SiegeEngineType.BALLISTA, 30));
        assertFalse(EngineerAdvanceOrders.arrived(SiegeEngineType.BALLISTA, 32));
        assertTrue(EngineerAdvanceOrders.arrived(SiegeEngineType.CATAPULT, 58));
        assertFalse(EngineerAdvanceOrders.arrived(SiegeEngineType.CATAPULT, 60));
        assertFalse(EngineerAdvanceOrders.arrived(SiegeEngineType.CATAPULT, 150));
    }
    @Test void dismountCancelsOwnedMovementAndRestoresFiringAcrossReload() throws Exception {
        var engineer = new Engineer(); var data = new CompoundTag();
        EngineerAdvanceOrders.travel(engineer, data);
        var loaded = new Engineer(); loaded.ranged = false;
        var saved = data.copy();
        EngineerAdvanceOrders.cancelTravel(loaded, saved);
        assertFalse(loaded.moving); assertTrue(loaded.ranged); assertTrue(saved.isEmpty());
        // A subsequent independent order is not ours to cancel.
        loaded.moving = true;
        EngineerAdvanceOrders.cancelTravel(loaded, saved);
        assertTrue(loaded.moving);
        assertEquals(0, loaded.siegeController.resets);
    }
    @Test void cancelDoesNotEnableOriginallyDisabledFireOrChangeUnmanagedOrders() throws Exception {
        var engineer = new Engineer(); var data = new CompoundTag();
        EngineerAdvanceOrders.cancelTravel(engineer, data);
        assertTrue(engineer.moving); assertTrue(engineer.ranged);
        engineer.ranged = false;
        EngineerAdvanceOrders.travel(engineer, data);
        EngineerAdvanceOrders.cancelTravel(engineer, data);
        assertFalse(engineer.moving); assertFalse(engineer.ranged);
    }
    public static class FailedMovementEngineer extends Engineer {
        public boolean fail = true;
        @Override public void setShouldMovePos(boolean value) {
            if (fail) throw new IllegalStateException("temporary API failure");
            super.setShouldMovePos(value);
        }
    }
    @Test void failedCancellationRetainsOwnershipForRetry() throws Exception {
        var engineer = new FailedMovementEngineer(); var data = new CompoundTag();
        EngineerAdvanceOrders.travel(engineer, data);
        assertThrows(ReflectiveOperationException.class, () -> EngineerAdvanceOrders.cancelTravel(engineer, data));
        assertFalse(data.isEmpty()); assertFalse(engineer.ranged);
        engineer.fail = false;
        EngineerAdvanceOrders.cancelTravel(engineer, data);
        assertFalse(engineer.moving); assertTrue(engineer.ranged); assertTrue(data.isEmpty());
    }
    public interface NativeOrders {
        boolean getShouldRanged();
        void setShouldRanged(boolean value);
        void setShouldMovePos(boolean value);
    }
    @Test void dismountedAdvanceActuallyCancelsNativeTravelBeforeReturning() throws Exception {
        var mob = org.mockito.Mockito.mock(net.minecraft.world.entity.Mob.class,
                org.mockito.Mockito.withSettings().extraInterfaces(NativeOrders.class));
        var orders = (NativeOrders) mob;
        var data = new CompoundTag();
        org.mockito.Mockito.when(mob.getPersistentData()).thenReturn(data);
        org.mockito.Mockito.when(orders.getShouldRanged()).thenReturn(true);
        EngineerAdvanceOrders.travel(mob, data);
        SiegeIntegration.advanceEngineer(mob, net.minecraft.core.BlockPos.ZERO);
        org.mockito.Mockito.verify(orders).setShouldMovePos(false);
        org.mockito.Mockito.verify(orders).setShouldRanged(true);
        assertTrue(data.isEmpty());
    }
    @Test void arrivalClearsLatchedSteeringAsWellAsVehicleControls() throws Exception {
        var engineer = new Engineer();
        EngineerAdvanceOrders.stop(engineer);
        assertFalse(engineer.siegeController.forward); assertFalse(engineer.siegeController.left);
        assertFalse(engineer.siegeController.right); assertFalse(engineer.siegeController.backward);
        assertEquals(1, engineer.siegeController.resets);
    }
}
