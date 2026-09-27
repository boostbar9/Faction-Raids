package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnemyCoreApproachTest extends MinecraftTestSupport {
    private final BlockPos camp=new BlockPos(0,64,0);
    private RaidSavedData.RaidState raid(Direction front) {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=camp;
        raid.warGate.putInt("PerimeterGateFacing",front.get2DDataValue());return raid;
    }
    private ServerLevel flat() {
        var level=mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.getBlockState(any())).thenAnswer(i->((BlockPos)i.getArgument(0)).getY()<64?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        return level;
    }
    @Test void everyOrientationGetsConnectedWideApproachAndInwardStairs() {
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var level=flat();var raid=raid(front);
            var plan=EnemyCoreApproach.plan(level,raid,camp,p->true).orElseThrow();
            assertEquals(3,plan.steps().size());
            for(var entry:plan.steps().entrySet()) {
                assertEquals(front.getOpposite(),entry.getValue().getValue(StairBlock.FACING));
                assertEquals(64,entry.getKey().getY());
            }
            EnemyCoreApproach.save(raid,plan);
            for(int depth=3;depth<=13;depth++)for(int side=-1;side<=1;side++) {
                BlockPos p=camp.relative(front,depth).relative(front.getClockWise(),side);
                assertTrue(EnemyCoreSite.reserved(raid,p));
                assertTrue(EnemyCoreApproach.protectedCell(raid,p.below()));
                assertFalse(EnemyCoreApproach.reserved(raid,p.below()));
            }
            verify(level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void routeFindsOffsetStarterGateButCannotUseFutureWallAsExit() {
        var level=flat();var raid=raid(Direction.NORTH);
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
            if(Math.abs(x)!=9 && Math.abs(z)!=9)continue;
            if(z==-9 && x>=0 && x<=2)continue;
            raid.pendingFortifications.put(new BlockPos(x,64,z).asLong(),"minecraft:spruce_log");
        }
        var plan=EnemyCoreApproach.plan(level,raid,camp,p->true).orElseThrow();
        EnemyCoreApproach.save(raid,plan);
        assertTrue(EnemyCoreApproach.reserved(raid,new BlockPos(1,64,-9)));
        for(int x=0;x<=2;x++)raid.pendingFortifications.put(new BlockPos(x,64,-9).asLong(),"minecraft:spruce_log");
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
    }
    @Test void waterCliffsLowCeilingsAndClaimGapsBlockTheWholeEntrance() {
        var level=flat();var raid=raid(Direction.NORTH);BlockPos threshold=camp.north(13);
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->!p.equals(threshold)).isEmpty());
        when(level.getBlockState(threshold)).thenReturn(Blocks.WATER.defaultBlockState());
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
        when(level.getBlockState(threshold)).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(threshold.above())).thenReturn(Blocks.STONE.defaultBlockState());
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
        when(level.getBlockState(threshold.above())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(threshold.below())).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
    }
    @Test void unloadedColumnsAreNeverHeightQueriedAndPlanningDoesNotMutate() {
        var level=flat();var raid=raid(Direction.NORTH);
        when(level.hasChunkAt(any())).thenReturn(false);
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
        verify(level,never()).getHeight(any(),anyInt(),anyInt());
        verify(level,never()).setBlock(any(),any(),anyInt());assertTrue(raid.campaign.isEmpty());
    }
    @Test void reservationsSurviveReloadWithoutMovingCoreOrCaptureProgress() {
        var raid=raid(Direction.NORTH);raid.campaign.putLong("EnemyCore",camp.above().asLong());
        raid.campaign.putInt("EnemyCaptureTicks",143);
        assertFalse(EnemyCoreApproach.reserved(raid,camp.north(5)));
        EnemyCoreApproach.save(raid,EnemyCoreApproach.plan(flat(),raid,camp,p->true).orElseThrow());
        var loaded=RaidSavedData.RaidState.load(raid.save());
        assertEquals(camp.above(),EnemyCore.position(loaded));assertEquals(143,loaded.campaign.getInt("EnemyCaptureTicks"));
        assertTrue(EnemyCoreApproach.reserved(loaded,camp.north(5).above(2)));
        assertFalse(EnemyCoreApproach.reserved(loaded,camp.north(5).above(3)));
    }
}
