package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CoreCaptureRegressionTest extends MinecraftTestSupport {
    @Test void coreBypassesLegacyPerimeterAndKillBonusesAndRequiresPresenceToFinish() throws Exception {
        RaidConfig.ENABLE_BREACH_PHASE.set(true); RaidConfig.ENABLE_EFFORT_BONUS.set(true);
        RaidConfig.CAPTURE_TIME_SECONDS.set(120);
        var level=mock(ServerLevel.class); var anchor=mock(RaidSavedData.Anchor.class);
        when(anchor.members()).thenReturn(Set.of());
        var raid=new RaidSavedData.RaidState("team:blue","siege_core",0); raid.wave=1; raid.captureTicks=2380;
        var method=RaidEvents.class.getDeclaredMethod("updateCaptureProgress",MinecraftServer.class,RaidSavedData.Anchor.class,
                RaidSavedData.DefensePoint.class,RaidSavedData.RaidState.class,ServerLevel.class,List.class,List.class);
        method.setAccessible(true);
        var point=new RaidSavedData.DefensePoint("siege_core",Level.OVERWORLD.location(),BlockPos.ZERO);
        try(var presence=mockStatic(CoreOccupation.class)) {
            presence.when(()->CoreOccupation.counts(level,BlockPos.ZERO,raid.teamKey,Set.of())).thenReturn(new int[]{3,2});
            assertEquals(true,method.invoke(null,null,anchor,point,raid,level,List.of(),List.of()));
            assertTrue(raid.breached); assertEquals(0,raid.breachTicks); assertEquals(2400,raid.captureTicks);
            presence.when(()->CoreOccupation.counts(level,BlockPos.ZERO,raid.teamKey,Set.of())).thenReturn(new int[]{0,0});
            assertEquals(false,method.invoke(null,null,anchor,point,raid,level,List.of(),List.of()));
        }
    }
    @Test void rejectedNativeTransferDoesNotEndWavesOrMarkTheCoreOccupied() {
        var level=mock(ServerLevel.class); var server=mock(MinecraftServer.class); when(level.getServer()).thenReturn(server);
        var data=new RaidSavedData(); var core=new CompoundTag(); data.siegeCores.put("team:blue",core);
        var raid=new RaidSavedData.RaidState("team:blue","siege_core",0); raid.pendingWaveSpawns=8;
        var id=UUID.randomUUID();
        var claim=new RecruitsClaimsBridge.ClaimSnapshot(id,"Home","blue",new ChunkPos(0,0),Set.of(),false,100,100);
        try(var claims=mockStatic(RecruitsClaimsBridge.class);var recruits=mockStatic(RaiderFactions.class,CALLS_REAL_METHODS);var transfer=mockStatic(CoreClaimTransfer.class)) {
            claims.when(()->RecruitsClaimsBridge.getClaimAt(level,BlockPos.ZERO)).thenReturn(Optional.of(claim));
            recruits.when(()->RaiderFactions.ensure(server,raid.factionId)).thenReturn(true);
            assertFalse(CoreOccupation.capture(level,data,raid,BlockPos.ZERO));
            assertEquals("Home",core.getString("OriginalClaimName"));
            assertFalse(core.getBoolean("Occupied")); assertFalse(raid.coreCaptured); assertEquals(8,raid.pendingWaveSpawns);
            transfer.verify(()->CoreClaimTransfer.transfer(level,id,"blue",RaiderFactions.id(raid.factionId),RaiderFactions.name(raid.factionId)+" Occupied Territory"));
        }
    }
}
