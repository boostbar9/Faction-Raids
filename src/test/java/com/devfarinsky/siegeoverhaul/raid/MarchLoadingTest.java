package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MarchLoadingTest extends MinecraftTestSupport {
    @Test void corridorPrefersTheChunksClosestToTheObjectiveAndHonoursTheBudget() {
        Map<UUID, Long> tracked = new LinkedHashMap<>();
        tracked.put(UUID.randomUUID(), new ChunkPos(20, 0).toLong());
        tracked.put(UUID.randomUUID(), new ChunkPos(4, 0).toLong());
        tracked.put(UUID.randomUUID(), new ChunkPos(12, 0).toLong());
        List<Long> selection = MarchLoading.selection(tracked, BlockPos.ZERO, 2);
        assertEquals(List.of(new ChunkPos(4, 0).toLong(), new ChunkPos(12, 0).toLong()), selection);
        assertEquals(3, MarchLoading.selection(tracked, BlockPos.ZERO, 8).size());
        assertTrue(MarchLoading.selection(tracked, BlockPos.ZERO, 0).isEmpty());
        assertTrue(MarchLoading.selection(Map.of(), BlockPos.ZERO, 8).isEmpty());
    }

    @Test void raidersSharingAChunkOnlyCostOneTicket() {
        long shared = new ChunkPos(3, 3).toLong();
        Map<UUID, Long> tracked = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) tracked.put(UUID.randomUUID(), shared);
        assertEquals(List.of(shared), MarchLoading.selection(tracked, BlockPos.ZERO, 16));
    }

    @Test void lastKnownMarchChunksSurviveSaveAndReload() {
        var raid = new RaidSavedData.RaidState("team:blue", "siege_core", 0);
        UUID raider = UUID.randomUUID();
        raid.marchChunks.put(raider, new ChunkPos(-7, 11).toLong());
        var loaded = RaidSavedData.RaidState.load(raid.save());
        assertEquals(new ChunkPos(-7, 11).toLong(), loaded.marchChunks.get(raider));
        assertTrue(RaidSavedData.RaidState.load(new RaidSavedData.RaidState("team:blue", "siege_core", 0).save())
                .marchChunks.isEmpty());
    }
}
