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
}
