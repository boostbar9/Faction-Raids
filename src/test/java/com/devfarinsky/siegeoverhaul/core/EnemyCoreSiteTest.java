package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnemyCoreSiteTest extends MinecraftTestSupport {
    final BlockPos center=new BlockPos(32,64,32);
    RaidSavedData.RaidState raid() { return new RaidSavedData.RaidState("team:test","siege_core",0); }
    ServerLevel flat() {
        var level=mock(ServerLevel.class); var border=new WorldBorder();
        when(level.getWorldBorder()).thenReturn(border);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getBlockState(any())).thenAnswer(c -> ((BlockPos)c.getArgument(0)).getY()<64
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        return level;
    }
    @Test void allOrientationsStayInsideWallsAndAwayFromGateAxis() {
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var sites=EnemyCoreSite.candidates(center,front);
            assertEquals(105,sites.size()); assertEquals(105,new java.util.HashSet<>(sites).size());
            assertEquals(center.relative(front,-6).relative(front.getClockWise(),-5),sites.get(5));
            for(var p:sites) {
                assertTrue(Math.abs(p.getX()-center.getX())<=8);
                assertTrue(Math.abs(p.getZ()-center.getZ())<=8);
                int side=(p.getX()-center.getX())*front.getClockWise().getStepX()
                        +(p.getZ()-center.getZ())*front.getClockWise().getStepZ();
                assertTrue(Math.abs(side)>=5 || p.getX()==center.getX() && p.getZ()==center.getZ());
                assertTrue(Math.abs(side)<=6);
                assertTrue(Math.abs(p.getX()-center.getX())+2<=8);
                assertTrue(Math.abs(p.getZ()-center.getZ())+2<=8);
            }
        }
    }
    @Test void clearCourtyardAcceptedButWaterRoofAndMissingFloorRejected() {
        var level=flat(); var raid=raid();
        assertTrue(EnemyCoreSite.clear(level,raid,center,p->true));
        var roof=center.above(4);
        when(level.getBlockState(roof)).thenReturn(Blocks.OAK_PLANKS.defaultBlockState());
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
        when(level.getBlockState(roof)).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(center.east())).thenReturn(Blocks.WATER.defaultBlockState());
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
        when(level.getBlockState(center.east())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(center.below())).thenReturn(Blocks.AIR.defaultBlockState());
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
    }
    @Test void rejectsUnloadedOrUnclaimedWalkingRingWithoutChangingWorld() {
        var level=flat(); var raid=raid();
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->!p.equals(center.east())));
        when(level.hasChunkAt(center.west())).thenReturn(false);
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void excludesQueuedBuildingsFortificationsAndRoad() {
        var level=flat(); var raid=raid();
        raid.pendingCampBlocks.put(center.above(7).asLong(),"minecraft:stone");
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true)); raid.pendingCampBlocks.clear();
        raid.pendingFortifications.put(center.east().asLong(),"minecraft:stone");
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true)); raid.pendingFortifications.clear();
        var road=new net.minecraft.nbt.CompoundTag(); road.putString(Long.toString(center.below().asLong()),"minecraft:gravel");
        raid.warGate.put("RoadBlocks",road);
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
    }
    @Test void queuedCellsAreIndexedOncePerColumn() {
        var raid=raid();
        raid.pendingCampBlocks.put(center.asLong(),"minecraft:stone");
        raid.pendingCampBlocks.put(center.above(7).asLong(),"minecraft:stone");
        raid.pendingFortifications.put(center.east().asLong(),"minecraft:stone");
        var road=new net.minecraft.nbt.CompoundTag();
        road.putString(Long.toString(center.east().below().asLong()),"minecraft:gravel");
        road.putString("invalid","minecraft:gravel");
        raid.warGate.put("RoadBlocks",road);
        assertEquals(2,EnemyCoreSite.blockedColumns(raid).size());
    }
    @Test void reservationSurvivesSaveWithoutMovingLegacyCoreOrCaptureProgress() {
        var raid=raid(); raid.campaign.putLong("EnemyCore",center.asLong()); raid.campaign.putInt("EnemyCaptureTicks",123);
        assertFalse(EnemyCoreSite.reserved(raid,center));
        raid.campaign.putBoolean("EnemyCoreCourtyard",true);
        var loaded=RaidSavedData.RaidState.load(raid.save());
        assertEquals(center,EnemyCore.position(loaded)); assertEquals(123,loaded.campaign.getInt("EnemyCaptureTicks"));
        assertTrue(EnemyCoreSite.reserved(loaded,center.east().above(5)));
        assertTrue(EnemyCoreSite.reserved(loaded,center.below()));
        assertFalse(EnemyCoreSite.reserved(loaded,center.east(3)));
    }
}
