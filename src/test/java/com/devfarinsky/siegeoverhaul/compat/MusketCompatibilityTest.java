package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusketCompatibilityTest extends MinecraftTestSupport {
    @Test void nativeBridgeMustBeReadyBeforeOfferingAnyMusket() {
        assertNull(MusketCompatibility.kit(key -> { fail("Must not select equipment without native AI"); return null; }, false));
    }
    @Test void completeKitPreservesMatchingWeaponAndAmmunition() {
        var kit = MusketCompatibility.kit(key -> key.getPath().equals("musket") ? Items.CROSSBOW : Items.PAPER, true);
        assertNotNull(kit); assertSame(Items.CROSSBOW, kit.weapon()); assertSame(Items.PAPER, kit.ammunition());
    }
    @Test void missingWeaponOrCartridgesCannotProduceUnusableHire() {
        assertNull(MusketCompatibility.kit(key -> null, true));
        assertNull(MusketCompatibility.kit(key -> key.getPath().equals("musket") ? Items.CROSSBOW : Items.AIR, true));
        assertNull(MusketCompatibility.kit(key -> key.getPath().equals("musket") ? Items.AIR : Items.PAPER, true));
    }
}
