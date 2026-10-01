package com.devfarinsky.siegeoverhaul.scout;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.narrative.RaidNarrative;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoutLifecycleTest extends MinecraftTestSupport {
    @Test void failedSpawnRetriesWithoutConsumingMissionAndPersistsNextAttempt() {
        var mission = new ScoutMission("team:test", 100, 1000, null);

        ScoutManager.recordSpawnAttempt(mission, false, 100);

        assertFalse(mission.spawned);
        assertEquals(100 + ScoutManager.SPAWN_RETRY_TICKS, mission.spawnGameTime);
        var restored = ScoutMission.load(mission.save());
        assertNotNull(restored);
        assertFalse(restored.spawned);
        assertEquals(mission.spawnGameTime, restored.spawnGameTime);
    }

    @Test void retryIsBoundedByExpiryAndSuccessfulAttemptClosesMission() {
        var mission = new ScoutMission("team:test", 100, 250, null);

        ScoutManager.recordSpawnAttempt(mission, false, 200);
        assertEquals(250, mission.spawnGameTime);
        assertFalse(mission.spawned);

        ScoutManager.recordSpawnAttempt(mission, true, 250);
        assertTrue(mission.spawned);
        assertEquals(250, mission.spawnGameTime);
    }

    @Test void expiredUnspawnedMissionDoesNotSpawnAndKeepsIntelAndBountyAfterReload() {
        var server = mock(MinecraftServer.class);
        var level = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(level);
        when(level.getGameTime()).thenReturn(200L);
        var data = new RaidSavedData();
        var narrative = new RaidNarrative("athena", "Owl-Shields", "", null, "war", "", "", "");
        var mission = new ScoutMission("team:test", 10, 100, narrative);
        mission.bountyPaid = 12;
        data.scoutMissions.put(mission.teamKey, mission);
        data.anchors.put(mission.teamKey, new RaidSavedData.Anchor(mission.teamKey, "Test", UUID.randomUUID(),
                Set.of(), false, false, Map.of(), 10000));
        try (var core = mockStatic(SiegeCore.class)) {
            core.when(() -> SiegeCore.point(server, mission.teamKey)).thenReturn(mock(RaidSavedData.DefensePoint.class));
            ScoutManager.tick(server, data);
            ScoutManager.tick(server, data);
        }
        assertSame(mission, data.scoutMissions.get(mission.teamKey));
        assertTrue(mission.spawned);
        assertTrue(mission.scoutUuids.isEmpty());
        verify(level, times(2)).getGameTime();
        verifyNoMoreInteractions(level);
        // Scheduling sees the retained entry, and the following raid consumes it once.
        var saved = new CompoundTag();
        ScoutManager.save(data, saved);
        var restored = new RaidSavedData();
        ScoutManager.load(restored, saved);
        var raid = new RaidSavedData.RaidState(mission.teamKey, "siege_core", 0);
        assertEquals("athena", ScoutManager.consumePreviewedNarrative(restored, mission.teamKey, raid).factionId);
        assertEquals(12, raid.campaign.getInt("BountyPaid"));
        assertNull(ScoutManager.consumePreviewedNarrative(restored, mission.teamKey, raid));
        assertEquals(12, raid.campaign.getInt("BountyPaid"));
    }

    @Test void closingWindowRecallsLoadedScoutsOnlyOnce() {
        var server = mock(MinecraftServer.class);
        var level = mock(ServerLevel.class);
        var scout = mock(Entity.class);
        when(server.getAllLevels()).thenReturn(java.util.List.of(level));
        UUID id = UUID.randomUUID();
        when(level.getEntity(id)).thenReturn(scout);
        var mission = new ScoutMission("team:test", 10, 100, null);
        mission.scoutUuids.add(id);
        assertTrue(ScoutManager.closeWindow(server, mission));
        assertFalse(ScoutManager.closeWindow(server, mission));
        verify(scout, times(1)).discard();
        assertTrue(mission.scoutUuids.isEmpty());
    }
}
