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
    @Test void postsFollowSmallTerrainStepsWithoutMovingIntoTheCentralApproach() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campPos=new BlockPos(-20,70,-30);
        for(double angle:new double[]{0,Math.PI/2,Math.PI,3*Math.PI/2}) {
            raid.approachAngle=angle;
            for(int slot=0;slot<4;slot++) {
                var positions=CampGuards.candidates(raid,slot);
                assertEquals(45,positions.size());
                assertEquals(positions.size(),new HashSet<>(positions).size());
                BlockPos preferred=positions.get(0);
                assertEquals(preferred.above(),positions.get(1));
                assertEquals(preferred.below(),positions.get(2));
                assertTrue(positions.contains(preferred.above(2)));
                assertTrue(positions.contains(preferred.below(2)));
            }
        }
    }
    @Test void postsRejectQueuedFortificationsWaterAndWorldBorder() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var level=mock(ServerLevel.class);
        var border=mock(net.minecraft.world.level.border.WorldBorder.class);
        BlockPos post=new BlockPos(0,70,0);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        when(level.getBlockState(post.below())).thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        assertTrue(CampGuards.safePost(level,raid,post));
        raid.pendingFortifications.put(post.above().asLong(),"minecraft:oak_fence");
        assertFalse(CampGuards.safePost(level,raid,post));
        raid.pendingFortifications.clear();
        when(level.getFluidState(post.above())).thenReturn(net.minecraft.world.level.material.Fluids.WATER.defaultFluidState());
        assertFalse(CampGuards.safePost(level,raid,post));
        when(level.getFluidState(post.above())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        when(border.isWithinBounds(post)).thenReturn(false);
        assertFalse(CampGuards.safePost(level,raid,post));
    }

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
