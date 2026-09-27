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
    @Test void completeStarterLayoutLeavesCentralSanctuaryAndGateRouteClearInEveryOrientation() {
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var level=flat(); var raid=raid(); raid.campPos=center;
            raid.warGate.putInt("PerimeterGateFacing",front.get2DDataValue());
            when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
            when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
            for(int side:new int[]{-1,1}) {
                var site=com.devfarinsky.siegeoverhaul.camp.CampStarterPavilion.anchor(center,front,side);
                var plan=com.devfarinsky.siegeoverhaul.camp.CampStarterPavilion.plan(level,raid,site,front);
                assertFalse(plan.isEmpty(),front+" pavilion "+side);
                raid.pendingCampBlocks.putAll(plan);
                com.devfarinsky.siegeoverhaul.camp.CampStarterPavilion.reserveEntrance(raid,site,front);
            }
            var fire=com.devfarinsky.siegeoverhaul.camp.CampStarterLayout.campfire(center,front);
            var barrel=com.devfarinsky.siegeoverhaul.camp.CampStarterLayout.supplies(center,front);
            var banner=com.devfarinsky.siegeoverhaul.camp.CampStarterLayout.banner(center,front);
            when(level.getBlockState(fire)).thenReturn(Blocks.CAMPFIRE.defaultBlockState());
            when(level.getBlockState(barrel)).thenReturn(Blocks.BARREL.defaultBlockState());
            when(level.getBlockState(banner)).thenReturn(Blocks.STONE_BRICKS.defaultBlockState());
            when(level.getBlockState(banner.above())).thenReturn(Blocks.RED_BANNER.defaultBlockState());
            var forge=com.devfarinsky.siegeoverhaul.camp.CampStarterLayout.forge(center,front);
            for(int z=-1;z<=1;z++)raid.pendingCampBlocks.put(forge.offset(0,0,z).asLong(),"minecraft:anvil");
            for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
                if(Math.abs(x)!=9 && Math.abs(z)!=9)continue;
                int depth=x*front.getStepX()+z*front.getStepZ();
                int side=x*front.getClockWise().getStepX()+z*front.getClockWise().getStepZ();
                if(depth==9 && Math.abs(side)<=1)continue;
                for(int y=0;y<2;y++)raid.pendingFortifications.put(center.offset(x,y,z).asLong(),"minecraft:spruce_log");
            }
            assertTrue(EnemyCoreSite.clear(level,raid,center,p->true),front.toString());
            assertTrue(EnemyCoreApproach.plan(level,raid,center,p->true).isPresent(),front+" gate route");
            // The old central fire forced every camp to use a fallback core site.
            when(level.getBlockState(center)).thenReturn(Blocks.CAMPFIRE.defaultBlockState());
            assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
            verify(level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void rejectsAdjacentBuildingsAndWallClipping() {
        var level=flat(); var raid=raid(); raid.campPos=center;
        raid.pendingCampBlocks.put(center.east(3).above(6).asLong(),"minecraft:stone");
        assertFalse(EnemyCoreSite.clear(level,raid,center,p->true));
        raid.pendingCampBlocks.clear();
        assertTrue(EnemyCoreSite.clear(level,raid,center,p->true));
        assertFalse(EnemyCoreSite.clear(level,raid,center.east(6),p->true));
        when(level.getBlockState(center.west(3).above())).thenReturn(Blocks.STONE.defaultBlockState());
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
        assertTrue(EnemyCoreSite.reserved(loaded,center.east(3)));
        assertFalse(EnemyCoreSite.reserved(loaded,center.east(4)));
    }
}
