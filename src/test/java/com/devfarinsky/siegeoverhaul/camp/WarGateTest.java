package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WarGateTest extends MinecraftTestSupport {
    @Test void blockedFrontSiteFallsBackToAnotherSideAndWaitSurvivesSave() {
        ServerLevel level=mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        var border=mock(net.minecraft.world.level.border.WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> world=new HashMap<>();
        when(level.getBlockState(any())).thenAnswer(a->world.getOrDefault(a.getArgument(0),((BlockPos)a.getArgument(0)).getY()<64?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState()));
        when(level.setBlock(any(),any(),anyInt())).thenAnswer(a->{world.put(((BlockPos)a.getArgument(0)).immutable(),a.getArgument(1));return true;});
        when(level.getFluidState(any())).thenAnswer(a->((BlockPos)a.getArgument(0)).getX()>18
                ?net.minecraft.world.level.material.Fluids.WATER.defaultFluidState():net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=new BlockPos(8,64,8);
        try(var saves=mockStatic(RaidSavedData.class)) {
            saves.when(()->RaidSavedData.get(null)).thenReturn(new RaidSavedData());
            assertTrue(WarGate.plan(level,raid,new BlockPos(100,64,8)));
        }
        assertTrue(WarGate.center(raid).getZ()>raid.campPos.getZ());
        raid.reinforcementStallTicks=1400;
        assertEquals(1400,RaidSavedData.RaidState.load(raid.save()).reinforcementStallTicks);
    }
    @Test void everyOrientationHasSupportedSpawnPadAndOpenArch() {
        BlockPos c=new BlockPos(8,64,8);
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var plan=WarGate.blueprint(c,front);
            for(int x=-1;x<=1;x++)for(int y=1;y<=5;y++)assertFalse(plan.containsKey(c.relative(front.getClockWise(),x).above(y).asLong()));
            for(int x=-1;x<=1;x++)assertTrue(plan.containsKey(c.relative(front,2).relative(front.getClockWise(),x).asLong()));
            assertTrue(plan.size()<128);
        }
    }
    @Test void spawnsAtPadHeightAndWaitsWhenBlockedOrIncomplete() {
        BlockPos c=new BlockPos(8,64,8);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var plan=WarGate.blueprint(c,Direction.NORTH);CompoundTag cells=new CompoundTag();plan.forEach((p,id)->cells.putString(Long.toString(p),id));
        raid.warGate.putLong("Center",c.asLong());raid.warGate.putInt("Facing",Direction.NORTH.get2DDataValue());raid.warGate.put("Blocks",cells);
        raid=RaidSavedData.RaidState.load(raid.save());assertEquals(c,WarGate.center(raid));
        ServerLevel level=mock(ServerLevel.class);when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(any())).thenAnswer(a->{String id=plan.get(((BlockPos)a.getArgument(0)).asLong());return id==null?Blocks.AIR.defaultBlockState():ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)).defaultBlockState();});
        Mob mob=mock(Mob.class);when(mob.position()).thenReturn(Vec3.ZERO);when(mob.getBoundingBox()).thenReturn(new AABB(-.3,0,-.3,.3,1.95,.3));
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(true);when(level.getEntities(eq(mob),any(AABB.class))).thenReturn(List.of());
        assertTrue(WarGate.ready(level,raid));BlockPos spawn=WarGate.spawn(level,raid,mob);assertNotNull(spawn);assertEquals(c.getY()+1,spawn.getY());
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(false);assertNull(WarGate.spawn(level,raid,mob));
        plan.remove(c.above(7).asLong());assertFalse(WarGate.ready(level,raid));assertNull(WarGate.spawn(level,raid,mob));
    }
    @Test void failedInstallRestoresQueuesAndTriesAnotherCandidate() {
        var level=mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=new BlockPos(8,64,8);
        long campJob=new BlockPos(8,64,8).asLong(), fortJob=new BlockPos(9,64,8).asLong();
        raid.pendingCampBlocks.put(campJob,"minecraft:oak_planks");raid.pendingFortifications.put(fortJob,"minecraft:oak_fence");
        var originalCamp=new LinkedHashMap<>(raid.pendingCampBlocks);var originalFort=new LinkedHashMap<>(raid.pendingFortifications);
        var road=new CampRoad.Plan(Map.of(),Map.of(),Set.of(campJob,fortJob));
        try(var roads=mockStatic(CampRoad.class);var assembly=mockStatic(GateAssembly.class)) {
            roads.when(()->CampRoad.plan(eq(level),eq(raid),any(),any())).thenAnswer(call->{
                assertEquals(originalCamp,raid.pendingCampBlocks);assertEquals(originalFort,raid.pendingFortifications);assertTrue(raid.warGate.isEmpty());
                return Optional.of(road);
            });
            roads.when(()->CampRoad.record(raid,road)).thenCallRealMethod();
            assembly.when(()->GateAssembly.install(level,raid)).thenReturn(false);
            assertFalse(WarGate.plan(level,raid,new BlockPos(100,64,8)));
            assertTrue(raid.warGate.isEmpty());assertEquals(originalCamp,raid.pendingCampBlocks);assertEquals(originalFort,raid.pendingFortifications);
            assembly.verify(()->GateAssembly.install(level,raid),times(16));
            assembly.when(()->GateAssembly.install(level,raid)).thenReturn(false,true);
            assertTrue(WarGate.plan(level,raid,new BlockPos(100,64,8)));
            assertEquals(raid.campPos.relative(Direction.EAST,17),WarGate.center(raid));
        }
    }
}
