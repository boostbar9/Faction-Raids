package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampStarterPavilionTest extends MinecraftTestSupport {
    private ServerLevel ground() {
        var level=mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(any())).thenAnswer(i->((BlockPos)i.getArgument(0)).getY()<64
                ? Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        return level;
    }
    private RaidSavedData.RaidState raid() {
        var r=new RaidSavedData.RaidState("test","siege_core",0);r.campPos=new BlockPos(0,64,0);r.factionId="crownfall_exiles";return r;
    }
    @Test void bothPavilionsFitEveryOrientationWithWideDoorsAndNoOverlap() {
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var level=ground();var raid=raid();Set<Long> all=new HashSet<>();
            for(int side:new int[]{-1,1}) {
                BlockPos center=CampStarterPavilion.anchor(raid.campPos,front,side);
                var plan=CampStarterPavilion.plan(level,raid,center,front);
                assertTrue(plan.size()>70 && plan.size()<180);
                for(long key:plan.keySet())assertTrue(all.add(key));
                for(int x=-1;x<=1;x++)for(int y=1;y<=3;y++)
                    assertFalse(plan.containsKey(center.relative(front,2).relative(front.getClockWise(),x).above(y).asLong()));
                assertTrue(plan.containsKey(center.above(6).asLong()));
                raid.pendingCampBlocks.putAll(plan);CampStarterPavilion.reserveEntrance(raid,center,front);
                var loaded=RaidSavedData.RaidState.load(raid.save());
                assertTrue(CampStructures.accessColumn(loaded,center.relative(front,4)));
                assertFalse(CampStructures.accessColumn(loaded,center.relative(front,-4)));
            }
            verify(level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void deityPalettesRemainDistinctAndAllBlocksAreVanilla() {
        var signatures=new HashSet<Set<String>>();
        for(String faction:List.of("blackbay_reavers","hollowfang_clan","emberchant_zealots","crownfall_exiles","wilds_marauders")) {
            var plan=CampStarterPavilion.structure(BlockPos.ZERO,Direction.NORTH,faction);
            signatures.add(Set.copyOf(plan.values()));
            for(String id:plan.values()) {
                var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(id));
                assertNotNull(block,id);assertNotEquals(Blocks.AIR,block,id);
            }
        }
        assertEquals(5,signatures.size());
    }
    @Test void buriedInteriorWaterAndQueuedDoorObstructionsRejectWholeBuilding() {
        var level=ground();var raid=raid();BlockPos center=CampStarterPavilion.anchor(raid.campPos,Direction.NORTH,1);
        when(level.getBlockState(center.above())).thenReturn(Blocks.STONE.defaultBlockState());
        assertTrue(CampStarterPavilion.plan(level,raid,center,Direction.NORTH).isEmpty());
        when(level.getBlockState(center.above())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getFluidState(center.above())).thenReturn(Fluids.WATER.defaultFluidState());
        assertTrue(CampStarterPavilion.plan(level,raid,center,Direction.NORTH).isEmpty());
        when(level.getFluidState(center.above())).thenReturn(Fluids.EMPTY.defaultFluidState());
        raid.pendingCampBlocks.put(center.north(3).asLong(),"minecraft:anvil");
        assertTrue(CampStarterPavilion.plan(level,raid,center,Direction.NORTH).isEmpty());
    }
    @Test void unevenFootprintGetsSupportsAndExcessiveDropsRejectPlacement() {
        var level=ground();var raid=raid();BlockPos center=CampStarterPavilion.anchor(raid.campPos,Direction.NORTH,1);
        when(level.getHeight(any(),eq(center.getX()),eq(center.getZ()))).thenReturn(62);
        doAnswer(i->{BlockPos p=i.getArgument(0);int top=p.getX()==center.getX()&&p.getZ()==center.getZ()?62:64;
            return p.getY()<top?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();}).when(level).getBlockState(any());
        var plan=CampStarterPavilion.plan(level,raid,center,Direction.NORTH);
        assertEquals("minecraft:cobblestone",plan.get(center.below(2).asLong()));
        when(level.getHeight(any(),eq(center.getX()),eq(center.getZ()))).thenReturn(60);
        assertTrue(CampStarterPavilion.plan(level,raid,center,Direction.NORTH).isEmpty());
    }
}
