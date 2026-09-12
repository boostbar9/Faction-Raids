package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreHireMenuTest {

    @Test
    void markCoreHudIntroSeen_onlyMarksOnce() {
        CompoundTag flags = new CompoundTag();

        assertTrue(CoreHireMenu.markCoreHudIntroSeen(flags));
        assertTrue(flags.getBoolean("SiegeCoreHudIntroSeen"));
        assertFalse(CoreHireMenu.markCoreHudIntroSeen(flags));
    }
}

