package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampSabotageTest extends MinecraftTestSupport {
    @Test void bannerRemovesLoadedWaveWithoutCreditingKillsAndRemembersUnloadedRaiders() {
        ServerLevel level = mock(ServerLevel.class);
        Mob raider = mock(Mob.class);
        UUID loaded = UUID.randomUUID(), unloaded = UUID.randomUUID();
        when(raider.getUUID()).thenReturn(loaded);
        when(level.getEntity(loaded)).thenReturn(raider);
        RaidState raid = new RaidState("team:test", "home", 0);
        raid.raiders.add(loaded); raid.raiders.add(unloaded); raid.pendingWaveSpawns = 4;
        CampSabotage.retreatWave(level, raid);
        verify(raider).discard();
        assertTrue(raid.raiders.isEmpty());
        assertEquals(0, raid.pendingWaveSpawns);
        assertEquals(0, raid.totalDefeated);
        assertEquals(2, raid.totalEscaped);
        RaidState restored = RaidState.load(raid.save());
        Mob returning = mock(Mob.class);
        when(returning.getUUID()).thenReturn(unloaded);
        assertTrue(CampSabotage.discardRetreated(returning, restored));
        verify(returning).discard();
        Mob nextWave = mock(Mob.class);
        when(nextWave.getUUID()).thenReturn(UUID.randomUUID());
        assertFalse(CampSabotage.discardRetreated(nextWave, restored));
        verify(nextWave, never()).discard();
    }
}
