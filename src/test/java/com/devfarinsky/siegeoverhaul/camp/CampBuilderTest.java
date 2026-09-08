package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampBuilderTest extends MinecraftTestSupport {
    @Test
    void stalledCampTimesOutAfterConfiguredSecondsOfPeriodicPasses() {
        RaidConfig.CAMP_MAX_BUILD_SECONDS.set(30);
        RaidState raid = raidWithJobs();
        for (int second = 1; second < 30; second++) assertFalse(CampBuilder.advanceTimeout(raid));
        assertFalse(raid.pendingCampBlocks.isEmpty());
        assertTrue(CampBuilder.advanceTimeout(raid));
        assertTrue(raid.pendingCampBlocks.isEmpty());
    }

    @Test
    void distantBuildersCannotConstructAndBudgetPreservesJobOrder() {
        RaidState raid = raidWithJobs();
        var placed = new ArrayList<BlockPos>();
        assertEquals(0, CampBuilder.placeNearby(raid, new Vec3(100, 64, 100), 4,
                (pos, block) -> placed.add(pos)));
        assertEquals(2, raid.pendingCampBlocks.size());
        assertEquals(1, CampBuilder.placeNearby(raid, new Vec3(0.5, 64, 0.5), 1,
                (pos, block) -> placed.add(pos)));
        assertEquals(new BlockPos(0, 64, 0), placed.get(0));
        assertEquals(1, raid.pendingCampBlocks.size());
    }

    @Test
    void missingCrewDoesNotMagicallyFinishConstruction() {
        RaidState raid = raidWithJobs();
        raid.campUsesWorkers = true;
        raid.campWorkers.add(UUID.randomUUID());
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        try (var bridge = mockStatic(WorkersBridge.class)) {
            bridge.when(WorkersBridge::available).thenReturn(true);
            CampBuilder.tick(level, raid, (pos, block) -> fail("No living builder"));
        }
        assertEquals(2, raid.pendingCampBlocks.size());
    }

    @Test
    void unloadedCampDoesNotLoseTimeOrJobs() {
        RaidState raid = raidWithJobs();
        CampBuilder.tick(mock(ServerLevel.class), raid, (pos, block) -> fail("Unloaded camp"));
        assertEquals(0, raid.campBuildTicks);
        assertEquals(2, raid.pendingCampBlocks.size());
    }

    @Test
    void absentWorkersUsesBoundedAutomaticConstruction() {
        RaidState raid = raidWithJobs();
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        var placed = new ArrayList<BlockPos>();
        CampBuilder.tick(level, raid, (pos, block) -> placed.add(pos));
        assertEquals(2, placed.size());
        assertTrue(raid.pendingCampBlocks.isEmpty());
    }

    @Test
    void restartPreservesCrewOrderedJobsAndTimeoutWithoutDuplicatingWork() {
        RaidState raid = raidWithJobs();
        UUID builder = UUID.randomUUID();
        raid.campWorkers.add(builder);
        raid.campUsesWorkers = true;
        raid.campBuildTicks = 140;
        CampBuilder.placeNearby(raid, null, 1, (pos, block) -> {});
        RaidState loaded = RaidState.load(raid.save());
        assertEquals(raid.campWorkers, loaded.campWorkers);
        assertEquals(raid.pendingCampBlocks, loaded.pendingCampBlocks);
        assertEquals(140, loaded.campBuildTicks);
        assertTrue(loaded.campUsesWorkers);
        assertFalse(loaded.planningCamp);
    }

    private static RaidState raidWithJobs() {
        RaidState raid = new RaidState("team:test", "home", 0);
        raid.campPos = new BlockPos(0, 64, 0);
        raid.pendingCampBlocks.put(raid.campPos.asLong(), "minecraft:oak_planks");
        raid.pendingCampBlocks.put(raid.campPos.above().asLong(), "minecraft:red_wool");
        return raid;
    }
}
