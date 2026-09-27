package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampTerrainTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final Map<BlockPos, BlockState> edits = new HashMap<>();
    private final Map<String, Integer> heights = new HashMap<>();
    private final BlockPos center = new BlockPos(0, 64, 0);
    private final RaidState raid = new RaidState("team:test", "home", 0);

    @BeforeEach
    void terrain() {
        WorldBorder border = new WorldBorder();
        when(level.getWorldBorder()).thenReturn(border);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(Heightmap.Types.class), anyInt(), anyInt())).thenAnswer(c ->
                heights.getOrDefault(c.getArgument(1) + ":" + c.getArgument(2), 64));
        when(level.getBlockState(any())).thenAnswer(c -> state(c.getArgument(0)));
        when(level.setBlock(any(), any(), anyInt())).thenAnswer(c -> {
            edits.put(((BlockPos)c.getArgument(0)).immutable(), c.getArgument(1));
            return true;
        });
    }

    private BlockState state(BlockPos pos) {
        if (edits.containsKey(pos)) return edits.get(pos);
        int ground = heights.getOrDefault(pos.getX() + ":" + pos.getZ(), 64);
        return (pos.getY() >= ground ? Blocks.AIR : pos.getY() == ground - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState();
    }

    @Test
    void gentleCutAndFillAreFullySnapshottedAndSurviveSaving() {
        heights.put("1:0", 65);
        heights.put("-1:0", 63);
        var plan = CampTerrain.plan(level, center, p -> false).orElseThrow();
        assertEquals(2, plan.changes().size());
        verify(level, never()).setBlock(any(), any(), anyInt());
        assertTrue(CampTerrain.apply(level, raid, plan));
        assertTrue(state(new BlockPos(1, 64, 0)).isAir());
        assertTrue(state(new BlockPos(-1, 63, 0)).is(Blocks.DIRT));
        RaidState saved = RaidState.load(raid.save());
        assertEquals("minecraft:grass_block", saved.campBlocks.get(new BlockPos(1,64,0).asLong())
                .getCompound("Original").getString("Name"));
        assertEquals("minecraft:air", saved.campBlocks.get(new BlockPos(-1,63,0).asLong())
                .getCompound("Original").getString("Name"));
    }

    @Test
    void ordinaryTallGrassDoesNotPreventAnOtherwiseSafeCamp() {
        edits.put(new BlockPos(2,64,2),Blocks.TALL_GRASS.defaultBlockState());
        edits.put(new BlockPos(2,65,2),Blocks.TALL_GRASS.defaultBlockState());
        assertTrue(CampTerrain.plan(level,center,p->false).isPresent());
        edits.put(new BlockPos(2,64,2),Blocks.WHEAT.defaultBlockState());
        assertTrue(CampTerrain.plan(level,center,p->false).isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test
    void everyBoundaryColumnIsCheckedForClaimsAndUnloadedChunks() {
        assertTrue(CampTerrain.plan(level, center, p -> p.getX() == 12 && p.getZ() == 1).isEmpty());
        when(level.hasChunkAt(new BlockPos(12,64,1))).thenReturn(false);
        assertTrue(CampTerrain.plan(level, center, p -> false).isEmpty());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    void waterContainersAndStoneFoundationsRejectWholeSite() {
        BlockPos corner = new BlockPos(8,63,8);
        for (var block : List.of(Blocks.WATER, Blocks.CHEST, Blocks.STONE, Blocks.OAK_PLANKS)) {
            edits.put(corner, block.defaultBlockState());
            assertTrue(CampTerrain.plan(level, center, p -> false).isEmpty(), block.toString());
        }
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    void excessiveExcavationAndSharpEdgeTransitionsAreRejected() {
        heights.put("1:0", 68);
        assertTrue(CampTerrain.plan(level, center, p -> false).isEmpty());
        heights.clear();
        heights.put("12:0", 67);
        assertTrue(CampTerrain.plan(level, center, p -> false).isEmpty());
    }

    @Test
    void edgeBlendsOneBlockPerRing() {
        assertEquals(64, CampTerrain.targetHeight(64, 67, 9));
        assertEquals(65, CampTerrain.targetHeight(64, 67, 10));
        assertEquals(66, CampTerrain.targetHeight(64, 67, 11));
        assertEquals(67, CampTerrain.targetHeight(64, 67, 12));
        assertEquals(63, CampTerrain.targetHeight(64, 61, 10));
    }

    @Test
    void changedSiteIsRejectedBeforeAnyMutation() {
        heights.put("1:0", 65);
        var plan = CampTerrain.plan(level, center, p -> false).orElseThrow();
        edits.put(new BlockPos(1,64,0), Blocks.CHEST.defaultBlockState());
        assertFalse(CampTerrain.apply(level, raid, plan));
        assertTrue(raid.campBlocks.isEmpty());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    void placementFailureRollsBackEarlierEarthworksAndLedger() {
        heights.put("1:0", 65);
        heights.put("-1:0", 63);
        var plan = CampTerrain.plan(level, center, p -> false).orElseThrow();
        doReturn(false).when(level).setBlock(eq(new BlockPos(-1,63,0)), eq(Blocks.DIRT.defaultBlockState()), anyInt());
        assertFalse(CampTerrain.apply(level, raid, plan));
        assertTrue(state(new BlockPos(1,64,0)).is(Blocks.GRASS_BLOCK));
        assertTrue(state(new BlockPos(-1,63,0)).isAir());
        assertTrue(raid.campBlocks.isEmpty());
    }

    @Test
    void naturalGrassChangesRemainRestorableButPlayerStructuresDoNot() {
        assertTrue(CampTerrain.matchesPlaced(Blocks.GRASS_BLOCK.defaultBlockState(), "minecraft:dirt"));
        assertTrue(CampTerrain.matchesPlaced(Blocks.DIRT.defaultBlockState(), "minecraft:grass_block"));
        assertFalse(CampTerrain.matchesPlaced(Blocks.CHEST.defaultBlockState(), "minecraft:dirt"));
    }
    @Test void dryBeachSedimentsSupportCampsAndGateRoads() {
        for(var material:List.of(Blocks.SAND,Blocks.RED_SAND,Blocks.GRAVEL,Blocks.CLAY)) {
            doAnswer(c -> ((BlockPos)c.getArgument(0)).getY()<64
                    ? material.defaultBlockState() : Blocks.AIR.defaultBlockState()).when(level).getBlockState(any());
            var plan=CampTerrain.plan(level,center,p->false).orElseThrow();
            assertTrue(plan.changes().isEmpty(),"A flat beach should not need earthworks");
            raid.campPos=center;
            for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL)
                assertTrue(CampRoad.plan(level,raid,center.relative(side,17),side).isPresent(),material.toString());
        }
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void gentleBeachCutsRetainOriginalSedimentAcrossReload() {
        heights.put("1:0",65);heights.put("-1:0",63);
        for(int y=62;y<=64;y++)edits.put(new BlockPos(1,y,0),Blocks.SAND.defaultBlockState());
        edits.put(new BlockPos(-1,62,0),Blocks.CLAY.defaultBlockState());
        var plan=CampTerrain.plan(level,center,p->false).orElseThrow();
        assertTrue(CampTerrain.apply(level,raid,plan));
        assertTrue(state(new BlockPos(1,64,0)).isAir());
        assertTrue(state(new BlockPos(-1,63,0)).is(Blocks.DIRT));
        var saved=RaidState.load(raid.save());
        assertEquals("minecraft:sand",saved.campBlocks.get(new BlockPos(1,64,0).asLong())
                .getCompound("Original").getString("Name"));
        assertTrue(state(new BlockPos(1,63,0)).is(Blocks.SAND));
    }

    @Test void waterBetweenCoarseSamplesStillRejectsBeachWithoutMutation() {
        for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++)
            edits.put(new BlockPos(x,63,z),Blocks.SAND.defaultBlockState());
        for(var fluid:List.of(Blocks.WATER,Blocks.LAVA)) {
            edits.put(new BlockPos(8,63,8),fluid.defaultBlockState());
            assertTrue(CampTerrain.plan(level,center,p->false).isEmpty());
        }
        verify(level,never()).setBlock(any(),any(),anyInt());assertTrue(raid.campBlocks.isEmpty());
    }

    @Test void dryShoreOnEitherSideCanBeFoundWithoutAcceptingWater() {
        BlockPos scout=new BlockPos(8,64,8);
        for(int sign:new int[]{-1,1}) {
            doAnswer(c->{
                BlockPos p=c.getArgument(0);
                return (p.getY()>=64?Blocks.AIR:sign*(p.getX()-8)>5?Blocks.WATER:Blocks.SAND).defaultBlockState();
            }).when(level).getBlockState(any());
            boolean found=false;
            for(int attempt=0;attempt<25;attempt++) {
                BlockPos site=CampLoading.localCandidate(scout,attempt,true);
                if(CampTerrain.plan(level,site,p->false).isPresent()) {
                    found=true;assertTrue(sign*(site.getX()-8)<=-7,"Accepted footprint touches water");
                }
            }
            assertTrue(found,"Search must find the inland side regardless of coast direction");
        }
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

}
