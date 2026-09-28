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
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
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
    @Test void diagonalStarterGateAlignsWithTheSanctuaryAndLaterPerimeter() {
        for(int degrees=0;degrees<360;degrees+=15) {
            var level=flat();var raid=raid(Direction.NORTH);raid.warGate.getAllKeys().stream().toList().forEach(raid.warGate::remove);
            raid.approachAngle=Math.toRadians(degrees);
            var front=com.devfarinsky.siegeoverhaul.camp.CampPerimeter.mainGateSide(raid);
            for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
                if(Math.abs(x)!=9 && Math.abs(z)!=9)continue;
                BlockPos p=camp.offset(x,0,z);
                if(!com.devfarinsky.siegeoverhaul.camp.CampStarterLayout.gateCell(camp,p,front))
                    raid.pendingFortifications.put(p.asLong(),"minecraft:spruce_log");
            }
            assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isPresent(),"Angle "+degrees);
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
    @Test void legacyStarterCampStillHasAnAccessibleCoreSite() {
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var level=flat();var raid=raid(front);raid.factionId="crownfall_exiles";
            for(int side:new int[]{-1,1}) {
                var center=camp.relative(front,-5).relative(front.getClockWise(),side*4);
                var pavilion=com.devfarinsky.siegeoverhaul.camp.CampStarterPavilion.plan(level,raid,center,front);
                assertFalse(pavilion.isEmpty());raid.pendingCampBlocks.putAll(pavilion);
            }
            raid.pendingCampBlocks.put(camp.asLong(),"minecraft:campfire");
            raid.pendingCampBlocks.put(camp.west(3).asLong(),"minecraft:barrel");
            raid.pendingCampBlocks.put(camp.relative(front,4).asLong(),"minecraft:stone_bricks");
            raid.pendingCampBlocks.put(camp.relative(front,4).above().asLong(),"minecraft:white_banner");
            var forge=camp.relative(front,4).relative(front.getClockWise(),4);
            for(int z=-1;z<=1;z++)raid.pendingCampBlocks.put(forge.offset(0,0,z).asLong(),"minecraft:anvil");
            for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
                if(Math.abs(x)!=9 && Math.abs(z)!=9)continue;
                int depth=x*front.getStepX()+z*front.getStepZ();
                int side=x*front.getClockWise().getStepX()+z*front.getClockWise().getStepZ();
                if(depth==9 && Math.abs(side)<=1)continue;
                raid.pendingFortifications.put(new BlockPos(x,64,z).asLong(),"minecraft:spruce_log");
            }
            assertTrue(EnemyCoreSite.candidates(camp,front).stream().anyMatch(base->
                    EnemyCoreSite.clear(level,raid,base,p->true) && EnemyCoreApproach.plan(level,raid,base,p->true).isPresent()),front.toString());
        }
    }
    @Test void absentOuterAvenueDoesNotHideCoreButBlockedStarterThresholdsStillDo() {
        var level=flat();var raid=raid(Direction.NORTH);
        BlockPos outer=camp.north(13);
        when(level.getBlockState(outer)).thenReturn(Blocks.WATER.defaultBlockState());
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isPresent());
        for(int x=-8;x<=8;x++) {
            when(level.getBlockState(camp.north(9).east(x))).thenReturn(Blocks.WATER.defaultBlockState());
        }
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
        assertTrue(EnemyCoreApproach.plan(flat(),raid,camp,p->p.getZ()>camp.getZ()-9).isEmpty());
    }
    @Test void savedOffsetGateCanReachCoreWithoutDemolishingTheOldCamp() {
        var level=flat();var raid=raid(Direction.NORTH);
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
            if(Math.abs(x)!=9 && Math.abs(z)!=9)continue;
            if(z==-9 && x>=6 && x<=8)continue;
            raid.pendingFortifications.put(camp.offset(x,0,z).asLong(),"minecraft:spruce_log");
        }
        var saved=new java.util.LinkedHashMap<>(raid.pendingFortifications);
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isPresent());
        assertEquals(saved,raid.pendingFortifications);verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void completedPalisadeCannotBecomeARouteOverTheWall() {
        var level=flat();var raid=raid(Direction.NORTH);
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();
        for(int x=-11;x<=11;x++)for(int y=64;y<=65;y++) {
            BlockPos p=new BlockPos(x,y,-9);blocks.put(p,Blocks.SPRUCE_LOG.defaultBlockState());
            raid.recordCampBlock(p.asLong(),"minecraft:spruce_log",new net.minecraft.nbt.CompoundTag());
        }
        doAnswer(i->{int x=i.getArgument(1),z=i.getArgument(2);return z==-9?66:z<=-6?65:64;})
                .when(level).getHeight(any(),anyInt(),anyInt());
        doAnswer(i->{BlockPos p=i.getArgument(0);int floor=p.getZ()<=-6?65:64;
            return blocks.getOrDefault(p,(p.getY()<floor?Blocks.STONE:Blocks.AIR).defaultBlockState());})
                .when(level).getBlockState(any());
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty());
    }
    @Test void existingRoadFloorsRemainWalkableAndMayBeRepavedAtSameHeight() {
        var level=flat();var raid=raid(Direction.NORTH);
        doAnswer(i->((BlockPos)i.getArgument(0)).getY()<64?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState())
                .when(level).getBlockState(any());
        var approach=EnemyCoreApproach.plan(level,raid,camp,p->true).orElseThrow();EnemyCoreApproach.save(raid,approach);
        assertTrue(com.devfarinsky.siegeoverhaul.camp.CampRoad.plan(level,raid,camp.north(20).below(),Direction.NORTH).isPresent());
        assertTrue(com.devfarinsky.siegeoverhaul.camp.CampRoad.plan(level,raid,camp.north(20).above(),Direction.NORTH).isEmpty());
        BlockPos floor=camp.north(8).below();
        when(level.getBlockState(floor)).thenReturn(Blocks.STONE_BRICKS.defaultBlockState());
        raid.recordCampBlock(floor.asLong(),"minecraft:stone_bricks",new net.minecraft.nbt.CompoundTag());
        var road=new net.minecraft.nbt.CompoundTag();road.putString(Long.toString(floor.asLong()),"minecraft:stone_bricks");raid.warGate.put("RoadBlocks",road);
        assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isPresent());
    }
    @Test void nonCollidingHazardsDoNotCountAsClearWalkingSpace() {
        var level=flat();var raid=raid(Direction.NORTH);
        for(var block:List.of(Blocks.NETHER_PORTAL,Blocks.COBWEB,Blocks.FIRE,Blocks.WITHER_ROSE,Blocks.LAVA)) {
            when(level.getBlockState(camp.north(13))).thenReturn(block.defaultBlockState());
            for(int x=-8;x<=8;x++)when(level.getBlockState(camp.north(9).east(x))).thenReturn(block.defaultBlockState());
            assertTrue(EnemyCoreApproach.plan(level,raid,camp,p->true).isEmpty(),block.toString());
        }
    }
    @Test void bothPlantHalvesAreRecordedBeforeTransactionalStepPlacement() {
        var level=flat();var raid=raid(Direction.NORTH);BlockPos step=camp.north(3);
        var lower=Blocks.TALL_GRASS.defaultBlockState();
        var upper=lower.setValue(DoublePlantBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();blocks.put(step,lower);blocks.put(step.above(),upper);
        doAnswer(i->{BlockPos p=i.getArgument(0);return blocks.getOrDefault(p,(p.getY()<64?Blocks.STONE:Blocks.AIR).defaultBlockState());}).when(level).getBlockState(any());
        when(level.setBlock(any(),any(),anyInt())).thenAnswer(i->{blocks.put(i.getArgument(0),i.getArgument(1));return true;});
        var changes=new ArrayList<com.devfarinsky.siegeoverhaul.camp.CampTerrain.Change>();
        changes.add(new com.devfarinsky.siegeoverhaul.camp.CampTerrain.Change(step,lower,Blocks.STONE_BRICK_STAIRS.defaultBlockState()));
        EnemyCore.appendPlantPartners(level,changes);assertEquals(2,changes.size());
        assertTrue(com.devfarinsky.siegeoverhaul.camp.CampTerrain.apply(level,raid,new com.devfarinsky.siegeoverhaul.camp.CampTerrain.Plan(changes)));
        var loaded=RaidSavedData.RaidState.load(raid.save());
        assertEquals("lower",loaded.campBlocks.get(step.asLong()).getCompound("Original").getCompound("Properties").getString("half"));
        assertEquals("upper",loaded.campBlocks.get(step.above().asLong()).getCompound("Original").getCompound("Properties").getString("half"));
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
