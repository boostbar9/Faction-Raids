package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CampBuilderTest extends MinecraftTestSupport {
    @Test
    void stalledCampTimesOutAfterConfiguredSecondsOfPeriodicPasses() {
        RaidConfig.CAMP_MAX_BUILD_SECONDS.set(30);
        CampBuilder.CampState camp = new CampBuilder.CampState(BlockPos.ZERO, "test",
                new ArrayList<>(), new ArrayList<>());
        for (int second = 1; second < 30; second++) assertFalse(camp.tick(null));
        assertEquals(CampBuilder.Phase.IN_PROGRESS, camp.phase);
        assertTrue(camp.tick(null));
        assertEquals(CampBuilder.Phase.FAILED, camp.phase);
    }
}
