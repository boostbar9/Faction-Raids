package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineerAdvanceOrdersTest {
    public static class Engineer {
        public boolean ranged = true;
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
    @Test void arrivalClearsLatchedSteeringAsWellAsVehicleControls() throws Exception {
        var engineer = new Engineer();
        EngineerAdvanceOrders.stop(engineer);
        assertFalse(engineer.siegeController.forward); assertFalse(engineer.siegeController.left);
        assertFalse(engineer.siegeController.right); assertFalse(engineer.siegeController.backward);
        assertEquals(1, engineer.siegeController.resets);
    }
}
