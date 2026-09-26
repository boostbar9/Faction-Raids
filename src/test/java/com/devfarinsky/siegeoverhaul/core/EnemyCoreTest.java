package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnemyCoreTest extends MinecraftTestSupport {
    @Test void captureRequiresAlliedMajorityAndProgressSurvivesReload() {
        var level=mock(ServerLevel.class); var data=new RaidSavedData();
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campClaimId=UUID.randomUUID();
        String owner=RaiderFactions.id(raid.factionId);
        var anchor=new RaidSavedData.Anchor("team:test","Test",UUID.randomUUID(),Set.of(),false,false,Map.of(),0);
        BlockPos core=new BlockPos(32,65,48); raid.campaign.putLong("EnemyCore",core.asLong());
        when(level.getGameTime()).thenReturn(21L);
        try(var enemy=mockStatic(EnemyCore.class,CALLS_REAL_METHODS);var counts=mockStatic(CoreOccupation.class);var claims=mockStatic(RecruitsClaimsBridge.class)) {
            claims.when(()->RecruitsClaimsBridge.getClaimAt(level,core)).thenReturn(Optional.of(
                    new RecruitsClaimsBridge.ClaimSnapshot(raid.campClaimId,"Camp",owner,new ChunkPos(core),Set.of(),false,100,100)));
            enemy.when(()->EnemyCore.ensure(eq(level),any())).thenReturn(true);
            counts.when(()->CoreOccupation.counts(level,core,"team:test",anchor.members(),owner)).thenReturn(new int[]{1,2});
            assertFalse(EnemyCore.tick(level,data,raid,anchor));
            int progress=raid.campaign.getInt("EnemyCaptureTicks"); assertTrue(progress>0);
            raid=RaidSavedData.RaidState.load(raid.save()); assertEquals(core,EnemyCore.position(raid));
            assertEquals(progress,raid.campaign.getInt("EnemyCaptureTicks"));
            counts.when(()->CoreOccupation.counts(level,core,"team:test",anchor.members(),owner)).thenReturn(new int[]{2,2});
            assertFalse(EnemyCore.tick(level,data,raid,anchor)); assertEquals(progress,raid.campaign.getInt("EnemyCaptureTicks"));
            int maximum=RaidConfig.CORE_RECAPTURE_SECONDS.get()*20;
            raid.campaign.putInt("EnemyCaptureTicks",maximum);
            assertFalse(EnemyCore.tick(level,data,raid,anchor));
            counts.when(()->CoreOccupation.counts(level,core,"team:test",anchor.members(),owner)).thenReturn(new int[]{1,2});
            assertTrue(EnemyCore.tick(level,data,raid,anchor));
            raid.coreCaptured=true; assertFalse(EnemyCore.tick(level,data,raid,anchor));
        }
    }
    @Test void missingReplacedOrTransferredCampClaimCannotAwardVictory() {
        var level=mock(ServerLevel.class);var data=new RaidSavedData();
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campClaimId=UUID.randomUUID();
        var anchor=new RaidSavedData.Anchor("team:test","Test",UUID.randomUUID(),Set.of(),false,false,Map.of(),0);
        BlockPos pos=new BlockPos(32,65,48);raid.campaign.putLong("EnemyCore",pos.asLong());
        try(var enemy=mockStatic(EnemyCore.class,CALLS_REAL_METHODS);var counts=mockStatic(CoreOccupation.class);var claims=mockStatic(RecruitsClaimsBridge.class)) {
            enemy.when(()->EnemyCore.ensure(level,raid)).thenReturn(true);
            var foreign=new RecruitsClaimsBridge.ClaimSnapshot(raid.campClaimId,"Home","blue",new ChunkPos(pos),Set.of(),false,100,100);
            var replaced=new RecruitsClaimsBridge.ClaimSnapshot(UUID.randomUUID(),"Camp",RaiderFactions.id(raid.factionId),new ChunkPos(pos),Set.of(),false,100,100);
            for(Optional<RecruitsClaimsBridge.ClaimSnapshot> claim:List.of(Optional.<RecruitsClaimsBridge.ClaimSnapshot>empty(),Optional.of(foreign),Optional.of(replaced))) {
                raid.campaign.putInt("EnemyCaptureTicks",RaidConfig.CORE_RECAPTURE_SECONDS.get()*20);
                claims.when(()->RecruitsClaimsBridge.getClaimAt(level,pos)).thenReturn(claim);
                assertFalse(EnemyCore.tick(level,data,raid,anchor));
                assertEquals(0,raid.campaign.getInt("EnemyCaptureTicks"));
            }
            counts.verifyNoInteractions();
        }
    }
}
