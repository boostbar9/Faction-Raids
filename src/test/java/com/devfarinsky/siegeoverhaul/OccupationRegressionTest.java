package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.effort.RaidEffortTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OccupationRegressionTest extends MinecraftTestSupport {
    @Test void realCaptureUpdateRecoversEmptyPointDespiteQueuedEnemyBonus() throws Exception {
        RaidConfig.ENABLE_BREACH_PHASE.set(false);
        RaidConfig.ENABLE_EFFORT_BONUS.set(true);
        RaidConfig.CAPTURE_DECAY_PER_SECOND.set(1);
        RaidConfig.CAPTURE_TIME_SECONDS.set(120);
        ServerLevel level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(1L);
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:regression", "home", 0);
        raid.wave = 1; raid.captureTicks = 100;
        RaidEffortTracker.onDefenderKilled(raid.teamKey);
        try {
            var method = RaidEvents.class.getDeclaredMethod("updateCaptureProgress", MinecraftServer.class,
                    RaidSavedData.Anchor.class, RaidSavedData.DefensePoint.class, RaidSavedData.RaidState.class,
                    ServerLevel.class, List.class, List.class);
            method.setAccessible(true);
            assertEquals(false, method.invoke(null, null, null,
                    new RaidSavedData.DefensePoint("home", Level.OVERWORLD.location(), BlockPos.ZERO),
                    raid, level, List.of(), List.of()));
            assertEquals(80, raid.captureTicks);
            assertEquals("Recovering | 0 attackers / 0 defenders in ring", raid.objectiveStatus);
        } finally { RaidEffortTracker.forget(raid.teamKey); }
    }
}
