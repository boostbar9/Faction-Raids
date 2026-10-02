package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WallSurfaceTest extends MinecraftTestSupport {
    static class Site {
        final ServerLevel level=mock(ServerLevel.class);
        final Set<ChunkPos> claim=Set.of(new ChunkPos(0,0));
        final Map<BlockPos,BlockState> edits=new HashMap<>();
        java.util.function.ToIntFunction<BlockPos> heights=p->64;
        Site() {
            when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getWorldBorder()).thenReturn(new WorldBorder());
            when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
            when(level.getHeight(any(),anyInt(),anyInt())).thenAnswer(c->heights.applyAsInt(new BlockPos((int)c.getArgument(1),0,(int)c.getArgument(2))));
            when(level.getBlockState(any())).thenAnswer(c->{BlockPos p=c.getArgument(0);return edits.getOrDefault(p,
                    (p.getY()<heights.applyAsInt(p)?Blocks.STONE:Blocks.AIR).defaultBlockState());});
        }
        BlockPos base(int x,int z) { return WallSurface.base(level,claim,new BlockPos(x,64,z)); }
    }
    @Test void followsActualSurfaceFarAboveAndBelowCoreWithoutClamping() {
        var s=new Site();for(int y:new int[]{40,90}) {
            s.heights=p->y;
            assertEquals(new BlockPos(0,y,5),s.base(0,5));
            assertEquals(0,TerritoryFortification.foundationDepth(s.level,s.base(0,5)));
        }
        verify(s.level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void isolatedPitAndCliffNeedAnInteriorWorkSpotAtSimilarHeight() {
        var s=new Site();s.heights=p->p.getX()==0?50:64;
        assertNull(s.base(0,5));
        s.heights=p->p.getX()==0?80:64;assertNull(s.base(0,5));
        s.heights=p->p.getX()==0?65:64;assertEquals(new BlockPos(0,65,5),s.base(0,5));
    }
    @Test void shallowDepressionsReceiveSupportedDirtFoundationsAtInteriorWorkHeight() {
        var s=new Site();
        for (int depth : new int[]{2,4,8}) {
            s.heights=p->p.getX()==0?64-depth:64;
            BlockPos base=s.base(0,5);
            assertEquals(new BlockPos(0,64,5),base);
            assertEquals(depth,TerritoryFortification.foundationDepth(s.level,base));
            for (int y=64-depth;y<64;y++)
                assertEquals("minecraft:dirt",TerritoryFortification.wallCellMaterial(base,new BlockPos(0,y,5),"minecraft:stone_bricks"));
            assertEquals("minecraft:stone_bricks",TerritoryFortification.wallCellMaterial(base,base,"minecraft:stone_bricks"));
        }
        verify(s.level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void foundationCannotFillThroughPlayerBlocksFluidsOrHazards() {
        var s=new Site();s.heights=p->p.getX()==0?60:64;
        for (var block:List.of(Blocks.OAK_PLANKS,Blocks.CHEST,Blocks.WATER,Blocks.LAVA,Blocks.OAK_LOG)) {
            s.edits.put(new BlockPos(0,62,5),block.defaultBlockState());
            assertNull(s.base(0,5),block.toString());
        }
        s.edits.clear();s.heights=p->p.getX()==0?55:64;
        assertNull(s.base(0,5),"Nine-block gap exceeds the foundation budget");
    }
    @Test void lowestAccessibleWorkSiteAvoidsUnnecessaryDirtFill() {
        var s=new Site();s.heights=p->p.getX()==0?60:p.getZ()==4?60:64;
        assertEquals(new BlockPos(0,60,5),s.base(0,5));
    }
    @Test void workSpaceOnAnotherFutureWallColumnDoesNotQualify() {
        var s=new Site();
        for(int z=4;z<=6;z++)s.edits.put(new BlockPos(1,65,z),Blocks.COBWEB.defaultBlockState());
        assertNull(s.base(0,5));
        s.edits.clear();assertNotNull(s.base(0,0),"Corner can use diagonal interior footing");
    }
    @Test void waterRoofsAndExistingWallsAreNotNewWallFoundations() {
        var s=new Site();
        for(var block:List.of(Blocks.WATER,Blocks.LAVA,Blocks.STONE_BRICKS,Blocks.COBBLESTONE,Blocks.OAK_PLANKS,Blocks.MAGMA_BLOCK)) {
            s.edits.put(new BlockPos(0,63,5),block.defaultBlockState());assertNull(s.base(0,5),block.toString());
        }
        s.edits.put(new BlockPos(0,63,5),Blocks.SAND.defaultBlockState());assertNotNull(s.base(0,5));
    }
    @Test void unloadedColumnsDoNotReadHeightAndWorldCeilingRejectsTallPillars() {
        var s=new Site();when(s.level.hasChunkAt(any())).thenReturn(false);
        assertNull(s.base(0,5));verify(s.level,never()).getHeight(any(),anyInt(),anyInt());
        when(s.level.hasChunkAt(any())).thenReturn(true);s.heights=p->318;assertNull(s.base(0,5));
    }
    @Test void perimeterAndCornerPillarsShareSurfaceAndCountUnsafeSectionsOnce() {
        var s=new Site();s.heights=p->90;
        var columns=new ArrayList<BlockPos>();var corners=new HashSet<Long>();
        assertEquals(0,TerritoryFortification.computePerimeter(s.level,s.claim,64,columns,corners));
        assertEquals(60,columns.size());assertEquals(4,corners.size());
        assertTrue(columns.stream().allMatch(p->p.getY()==90));
        assertTrue(corners.stream().allMatch(p->BlockPos.of(p).getY()==90));
        s.edits.put(new BlockPos(0,89,0),Blocks.WATER.defaultBlockState());columns.clear();corners.clear();
        assertEquals(1,TerritoryFortification.computePerimeter(s.level,s.claim,64,columns,corners));
        assertEquals(59,columns.size());assertEquals(3,corners.size());
    }
}
