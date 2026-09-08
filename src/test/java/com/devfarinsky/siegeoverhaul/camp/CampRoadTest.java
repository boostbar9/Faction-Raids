package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CampRoadTest extends MinecraftTestSupport {
    private ServerLevel level() {
        ServerLevel level=mock(ServerLevel.class);WorldBorder border=mock(WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.getBlockState(any())).thenAnswer(a->((BlockPos)a.getArgument(0)).getY()<64?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState());
        return level;
    }
    @Test void roadHasThreeWideSupportAndNoMoreThanOneBlockSteps() {
        ServerLevel level=level();var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=new BlockPos(0,64,0);
        var plan=CampRoad.plan(level,raid,new BlockPos(20,66,0),Direction.EAST).orElseThrow();
        for(int x=8;x<=17;x++)for(int z=-1;z<=1;z++) {
            int floor=CampRoad.floorHeight(64,66,x-8,9);
            assertTrue(plan.blocks().containsKey(new BlockPos(x,floor,z).asLong()));
            assertTrue(plan.clearance().contains(new BlockPos(x,floor+3,z).asLong()));
            if(x>8)assertTrue(Math.abs(floor-CampRoad.floorHeight(64,66,x-9,9))<=1);
        }
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void steepRoadAndPlayerStructuresAreRejectedWithoutMutation() {
        ServerLevel level=level();var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=new BlockPos(0,64,0);
        assertTrue(CampRoad.plan(level,raid,new BlockPos(14,69,0),Direction.EAST).isEmpty());
        when(level.getBlockState(new BlockPos(10,64,0))).thenReturn(Blocks.CHEST.defaultBlockState());
        assertTrue(CampRoad.plan(level,raid,new BlockPos(20,64,0),Direction.EAST).isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void roadSnapshotsAndOpenPalisadeEntranceSurviveSave() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=new BlockPos(0,64,0);
        var plan=CampRoad.plan(level(),raid,new BlockPos(20,64,0),Direction.EAST).orElseThrow();
        BlockPos wall=new BlockPos(9,64,0);raid.pendingFortifications.put(wall.asLong(),"minecraft:spruce_log");
        CampRoad.record(raid,plan);assertFalse(raid.pendingFortifications.containsKey(wall.asLong()));
        var restored=RaidSavedData.RaidState.load(raid.save());
        assertEquals(raid.warGate,restored.warGate);assertFalse(restored.warGate.getCompound("RoadBefore").isEmpty());
    }
}
