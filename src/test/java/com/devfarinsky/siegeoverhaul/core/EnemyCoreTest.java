package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnemyCoreTest extends MinecraftTestSupport {
    @Test void captureRequiresAlliedMajorityAndProgressSurvivesReload() {
        var level=mock(ServerLevel.class); var data=new RaidSavedData();
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var anchor=new RaidSavedData.Anchor("team:test","Test",UUID.randomUUID(),Set.of(),false,false,Map.of(),0);
        BlockPos core=new BlockPos(32,65,48); raid.campaign.putLong("EnemyCore",core.asLong());
        when(level.getGameTime()).thenReturn(21L);
        try(var enemy=mockStatic(EnemyCore.class,CALLS_REAL_METHODS);var counts=mockStatic(CoreOccupation.class)) {
            enemy.when(()->EnemyCore.ensure(eq(level),any())).thenReturn(true);
            counts.when(()->CoreOccupation.counts(level,core,"team:test",anchor.members())).thenReturn(new int[]{1,2});
            assertFalse(EnemyCore.tick(level,data,raid,anchor));
            int progress=raid.campaign.getInt("EnemyCaptureTicks"); assertTrue(progress>0);
            raid=RaidSavedData.RaidState.load(raid.save()); assertEquals(core,EnemyCore.position(raid));
            assertEquals(progress,raid.campaign.getInt("EnemyCaptureTicks"));
            counts.when(()->CoreOccupation.counts(level,core,"team:test",anchor.members())).thenReturn(new int[]{2,2});
            assertFalse(EnemyCore.tick(level,data,raid,anchor)); assertEquals(progress,raid.campaign.getInt("EnemyCaptureTicks"));
            int maximum=RaidConfig.CORE_RECAPTURE_SECONDS.get()*20;
            raid.campaign.putInt("EnemyCaptureTicks",maximum);
            assertFalse(EnemyCore.tick(level,data,raid,anchor));
            counts.when(()->CoreOccupation.counts(level,core,"team:test",anchor.members())).thenReturn(new int[]{1,2});
            assertTrue(EnemyCore.tick(level,data,raid,anchor));
            raid.coreCaptured=true; assertFalse(EnemyCore.tick(level,data,raid,anchor));
        }
    }
}
