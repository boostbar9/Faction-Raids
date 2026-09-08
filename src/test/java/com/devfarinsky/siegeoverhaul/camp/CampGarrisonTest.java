package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.CampClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CampGarrisonTest extends MinecraftTestSupport {
    @Test void claimsUseTheNativeFiveByFiveFootprintAtNegativeCoordinates() {
        var chunks=CampClaims.footprint(new BlockPos(-1,70,-17));
        assertEquals(25,chunks.size());
        assertTrue(chunks.contains(new ChunkPos(-3,-4)));
        assertTrue(chunks.contains(new ChunkPos(1,0)));
        assertFalse(chunks.contains(new ChunkPos(2,0)));
    }
    @Test void leasesAndFiniteGuardIdentitySurviveRestart() {
        RaidSavedData data=new RaidSavedData();
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campClaimId=UUID.randomUUID();
        raid.campGuardsStarted=true;
        raid.campGuards.add(UUID.randomUUID());
        data.campClaimLeases.add(raid.campClaimId);
        data.raids.put(raid.teamKey,raid);
        var loaded=RaidSavedData.load(data.save(new CompoundTag()));
        var saved=loaded.raids.get(raid.teamKey);
        assertEquals(data.campClaimLeases,loaded.campClaimLeases);
        assertEquals(raid.campClaimId,saved.campClaimId);
        assertEquals(raid.campGuards,saved.campGuards);
        assertTrue(saved.campGuardsStarted);
        assertTrue(saved.raiders.isEmpty());
    }
    @Test void defeatedGarrisonIsNotReplenished() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campGuardsStarted=true;
        var level=mock(ServerLevel.class);
        CampGuards.start(level,new RaidSavedData(),raid);
        verifyNoInteractions(level);
        assertTrue(raid.campGuards.isEmpty());
    }
    @Test void unloadingGuardsRetainsTheirCleanupIdentity() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        UUID id=UUID.randomUUID(); raid.campGuards.add(id);
        CampGuards.tick(mock(ServerLevel.class),raid,false);
        assertTrue(raid.campGuards.contains(id));
    }
}
