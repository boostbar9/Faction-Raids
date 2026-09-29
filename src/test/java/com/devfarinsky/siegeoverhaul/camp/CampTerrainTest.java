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

    @Test void centralMoundDoesNotForceARejectableCampElevation() {
        heights.put("0:0", 71);
        BlockPos peak = new BlockPos(0,71,0);
        assertTrue(CampTerrain.plan(level,peak,p->false).isEmpty());
        BlockPos balanced = CampTerrain.earthworksCenter(level,peak);
        assertEquals(new BlockPos(0,65,0),balanced);
        assertTrue(CampTerrain.plan(level,balanced,p->false).isPresent());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void chosenEarthworkPlaneStillRejectsWaterAndPlayerBlocks() {
        heights.put("0:0",68);
        BlockPos plane = CampTerrain.earthworksCenter(level,new BlockPos(0,68,0));
        for (var block : List.of(Blocks.CHEST,Blocks.OAK_PLANKS,Blocks.WATER,Blocks.LAVA)) {
            edits.put(new BlockPos(2,64,2),block.defaultBlockState());
            assertTrue(CampTerrain.plan(level,plane,p->false).isEmpty(),block.toString());
        }
        assertTrue(CampTerrain.plan(level,plane,p->true).isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void earthworkPlaneDoesNotReadUnloadedColumnsOrRelaxSteepTerrain() {
        when(level.hasChunkAt(any())).thenReturn(false);
        assertEquals(center,CampTerrain.earthworksCenter(level,center));
        verify(level,never()).getHeight(any(),anyInt(),anyInt());
        when(level.hasChunkAt(any())).thenReturn(true);
        heights.put("0:0",77);
        assertEquals(center,CampTerrain.earthworksCenter(level,center));
    }

    @Test void sixBlockHollowsGetSolidDirtAndMoundsAreCutWithSavedOriginals() {
        heights.put("1:0", 70);
        heights.put("-1:0", 58);
        var plan = CampTerrain.plan(level, center, p -> false).orElseThrow();
        assertEquals(12, plan.changes().size());
        assertTrue(CampTerrain.apply(level, raid, plan));
        var saved = RaidState.load(raid.save());
        for (int y = 58; y < 64; y++) {
            BlockPos fill = new BlockPos(-1,y,0);
            assertTrue(state(fill).is(Blocks.DIRT));
            assertEquals("minecraft:air", saved.campBlocks.get(fill.asLong())
                    .getCompound("Original").getString("Name"));
        }
        for (int y = 64; y < 70; y++) assertTrue(state(new BlockPos(1,y,0)).isAir());
        assertEquals(12, saved.campBlocks.size());
    }

    @Test void untouchedOuterBoundaryMustUseItsRealHeight() {
        for (int x=-13;x<=13;x++) for (int z=-13;z<=13;z++)
            if (Math.max(Math.abs(x),Math.abs(z))>=10) heights.put(x+":"+z,69);
        assertTrue(CampTerrain.plan(level,center,p->false).isEmpty(),
                "The planned edge at 67 must not pretend untouched ground at 69 was lowered");
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void deepFillRejectsHiddenWaterContainersAndExcessiveWork() {
        heights.put("-1:0",58);
        for (var block : List.of(Blocks.WATER,Blocks.LAVA,Blocks.CHEST,Blocks.OAK_PLANKS)) {
            edits.put(new BlockPos(-1,60,0),block.defaultBlockState());
            assertTrue(CampTerrain.plan(level,center,p->false).isEmpty());
        }
        edits.clear(); heights.clear();
        for (int x=-9;x<=9;x++) for (int z=-9;z<=9;z++) heights.put(x+":"+z,58);
        assertTrue(CampTerrain.plan(level,center,p->false).isEmpty(),"Keep the finite mutation budget");
        verify(level,never()).setBlock(any(),any(),anyInt());
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
    void excessiveExcavationIsRejectedButShoulderMoundsAreLeveled() {
        heights.put("1:0", 71);
        assertTrue(CampTerrain.plan(level, center, p -> false).isEmpty());
        heights.clear();
        heights.put("12:0", 67);
        var plan = CampTerrain.plan(level, center, p -> false).orElseThrow();
        assertEquals(3, plan.changes().size());
        assertTrue(CampTerrain.apply(level, raid, plan));
        assertTrue(state(new BlockPos(12,64,0)).isAir());
    }

    @Test void unevenUntouchedBoundaryGetsAContinuousShoulderWithoutChangingOutsideGround() {
        heights.put("13:0",66);
        var plan=CampTerrain.plan(level,center,p->false).orElseThrow();
        assertTrue(plan.changes().stream().noneMatch(c->Math.abs(c.pos().getX())>12||Math.abs(c.pos().getZ())>12));
        assertTrue(CampTerrain.apply(level,raid,plan));
        assertTrue(state(new BlockPos(12,64,0)).is(Blocks.DIRT));
        assertTrue(state(new BlockPos(13,65,0)).is(Blocks.GRASS_BLOCK));
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
            int height=plannedHeight(x,z);
            if(Math.max(Math.abs(x),Math.abs(z))<=9)assertEquals(64,height);
            for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                int adjacent=plannedHeight(x+direction.getStepX(),z+direction.getStepZ());
                assertTrue(Math.abs(height-adjacent)<=1,"Unwalkable shoulder at "+x+":"+z);
            }
        }
    }

    @Test void diagonalUntouchedCornersDoNotConstrainThePlaneOrItsWalkableEdge() {
        for(int x:new int[]{-13,13})for(int z:new int[]{-13,13})heights.put(x+":"+z,80);
        assertEquals(center,CampTerrain.earthworksCenter(level,center));
        var plan=CampTerrain.plan(level,center,p->false).orElseThrow();
        assertTrue(plan.changes().isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void smoothingCannotExceedTheSixBlockCutFillLimit() {
        heights.put("13:0",67);
        heights.put("12:0",58);
        var reasons=new ArrayList<CampTerrain.Rejection>();
        assertTrue(CampTerrain.plan(level,center,p->false,reasons::add).isEmpty());
        assertEquals(List.of(CampTerrain.Rejection.RELIEF),reasons);
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    private int plannedHeight(int x,int z) {
        for(int y=75;y>=50;y--)if(!state(new BlockPos(x,y,z)).isAir())return y+1;
        throw new AssertionError("Missing ground");
    }

    @Test void diagnosticsIdentifyTheFirstFailureWithoutMutatingTerrain() {
        var reasons=new ArrayList<CampTerrain.Rejection>();
        edits.put(new BlockPos(2,63,2),Blocks.WATER.defaultBlockState());
        assertTrue(CampTerrain.plan(level,center,p->false,reasons::add).isEmpty());
        assertEquals(List.of(CampTerrain.Rejection.FLUID),reasons);
        edits.clear();reasons.clear();heights.put("13:0",70);
        assertTrue(CampTerrain.plan(level,center,p->false,reasons::add).isEmpty());
        assertEquals(List.of(CampTerrain.Rejection.EDGE),reasons);
        verify(level,never()).setBlock(any(),any(),anyInt());
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
