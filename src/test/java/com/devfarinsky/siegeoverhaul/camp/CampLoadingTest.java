package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CampLoadingTest extends MinecraftTestSupport {
    @Test void searchCoversFartherTerrainInEveryDirectionWithoutDriftingInfinitely() {
        BlockPos core=new BlockPos(351,81,120);Set<ChunkPos> chunks=new HashSet<>();
        for(int i=0;i<168;i++) {
            BlockPos p=CampLoading.candidate(core,0,i);chunks.add(new ChunkPos(p));
            double distance=Math.sqrt(p.distSqr(core));
            assertTrue(distance>=145 && distance<=560);assertEquals(8,Math.floorMod(p.getX(),16));assertEquals(8,Math.floorMod(p.getZ(),16));
        }
        assertTrue(chunks.size()>150);
        assertEquals(CampLoading.candidate(core,0,0),CampLoading.candidate(core,0,168));
    }
    @Test void searchAndCrewStateSurviveReloadAndOldCampsDoNotDuplicateTheirCrew() {
        var raid=new RaidSavedData.RaidState("team:blue","siege_core",0);
        raid.campSearchPos=new BlockPos(-160,70,208);raid.campSearchStep=27;raid.campSearchTicks=40;
        var loaded=RaidSavedData.RaidState.load(raid.save());
        assertEquals(raid.campSearchPos,loaded.campSearchPos);assertEquals(27,loaded.campSearchStep);assertEquals(40,loaded.campSearchTicks);assertFalse(loaded.campCrewStarted);
        CompoundTag old=raid.save();old.remove("CampCrewStarted");old.putLong("CampPosition",BlockPos.ZERO.asLong());
        assertTrue(RaidSavedData.RaidState.load(old).campCrewStarted);
        old.remove("CampPosition");assertFalse(RaidSavedData.RaidState.load(old).campCrewStarted);
    }
    @Test void expandedLocalSearchIsSymmetricAndFitsLoadedNeighborhood() {
        for(BlockPos scout:List.of(new BlockPos(8,64,8),new BlockPos(-24,64,-40))) {
            Set<BlockPos> sites=new HashSet<>();int sumX=0,sumZ=0;ChunkPos chunk=new ChunkPos(scout);
            for(int i=0;i<25;i++) {
                BlockPos site=CampLoading.localCandidate(scout,i,true);assertTrue(sites.add(site));
                sumX+=site.getX()-scout.getX();sumZ+=site.getZ()-scout.getZ();
                for(int x:new int[]{-13,13})for(int z:new int[]{-13,13}) {
                    ChunkPos edge=new ChunkPos(site.offset(x,0,z));
                    assertTrue(Math.abs(edge.x-chunk.x)<=1 && Math.abs(edge.z-chunk.z)<=1);
                }
            }
            assertEquals(0,sumX);assertEquals(0,sumZ);assertTrue(sites.contains(scout));
            for(BlockPos site:sites)assertTrue(sites.contains(scout.offset(scout.getX()-site.getX(),0,scout.getZ()-site.getZ())));
        }
    }
    @Test void ordinaryLocalSearchKeepsItsExistingNineSites() {
        BlockPos scout=new BlockPos(8,64,8);
        for(int i=0;i<9;i++)assertEquals(scout.offset((i%3-1)*6,0,(i/3-1)*6),CampLoading.localCandidate(scout,i,false));
    }

}
