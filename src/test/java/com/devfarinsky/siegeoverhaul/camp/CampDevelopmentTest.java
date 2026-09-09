package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CampDevelopmentTest extends MinecraftTestSupport {
    @Test void obstructedPreferredUpgradeRetriesAnotherSiteWithoutReplacingBlocks() {
        var level=mock(ServerLevel.class);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campPos=new BlockPos(0,64,0);raid.campClaimId=UUID.randomUUID();raid.campUpgradeTicks=2400;
        UUID workerId=UUID.randomUUID();raid.campWorkers.add(workerId);var worker=mock(Mob.class);
        when(level.getEntity(workerId)).thenReturn(worker);when(worker.isAlive()).thenReturn(true);
        when(level.hasChunkAt(any())).thenReturn(true);when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        var border=mock(net.minecraft.world.level.border.WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        var saved=new RaidSavedData();saved.anchors.put(raid.teamKey,new RaidSavedData.Anchor(raid.teamKey,"Test",UUID.randomUUID(),Set.of(),false,false,Map.of(),0));
        var claim=mock(RecruitsClaimsBridge.ClaimSnapshot.class);when(claim.claimId()).thenReturn(raid.campClaimId);when(claim.ownerFactionStringId()).thenReturn("enemy");
        var sites=new ArrayList<Set<Long>>();
        try(var gates=mockStatic(WarGate.class);var camps=mockStatic(CampClaims.class);var nativeJobs=mockStatic(NativeCampConstruction.class);
            var saves=mockStatic(RaidSavedData.class);var claims=mockStatic(RecruitsClaimsBridge.class);var external=mockStatic(ClaimBridge.class)) {
            gates.when(()->WarGate.ready(level,raid)).thenReturn(true);camps.when(()->CampClaims.owns(level,raid)).thenReturn(true);
            saves.when(()->RaidSavedData.get(null)).thenReturn(saved);
            claims.when(()->RecruitsClaimsBridge.getClaimAt(eq(level),any(BlockPos.class))).thenReturn(Optional.of(claim));
            nativeJobs.when(()->NativeCampConstruction.start(level,raid)).thenAnswer(call->{sites.add(Set.copyOf(raid.pendingCampBlocks.keySet()));return sites.size()==2;});
            CampDevelopment.tick(level,raid);
            assertEquals(2,sites.size());assertNotEquals(sites.get(0),sites.get(1));assertEquals(1,raid.campUpgradeStage);
            assertTrue(raid.pendingCampBlocks.size()<512);verify(level,never()).setBlock(any(),any(),anyInt());
        }
    }
}
