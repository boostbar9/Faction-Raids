package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampBlockStateTest extends MinecraftTestSupport {
    private List<BlockState> samples() {
        return List.of(Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.WEST).setValue(StairBlock.HALF,Half.TOP),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP),
                Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X),
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
    }
    @Test void fullStatesRoundTripAndLegacyIdsKeepTheirDefaults() {
        for(var state:samples()) assertEquals(state,CampBlockState.decode(CampBlockState.encode(state)).orElseThrow());
        assertEquals(Blocks.OAK_LOG.defaultBlockState(),CampBlockState.decode("minecraft:oak_log").orElseThrow());
    }
    @Test void malformedPlansNeverSilentlyBecomeDefaultBlocks() {
        for(String id:List.of("missing:block","minecraft:oak_log[axis=bad]","minecraft:oak_log[bogus=x]",
                "minecraft:oak_log[axis=x,axis=z]","minecraft:oak_log[axis=x", "minecraft:oak_log[]", "minecraft:oak_log[axis=x,]")) {
            assertTrue(CampBlockState.decode(id).isEmpty(),id);
            assertFalse(NativeCampConstruction.safeCell(Blocks.AIR.defaultBlockState(),id));
            assertThrows(IllegalArgumentException.class,()->NativeCampConstruction.blueprint(Map.of(0L,id)));
        }
    }
    @Test void savedPlansReachNativeBlueprintAndFallbackWithoutLosingProperties() {
        var raid=new RaidState("team:test","siege_core",0);
        int y=64; for(var state:samples()) raid.pendingCampBlocks.put(new BlockPos(-4,y++,-7).asLong(),CampBlockState.encode(state));
        var loaded=RaidState.load(raid.save());
        var blueprint=NativeCampConstruction.blueprint(loaded.pendingCampBlocks);
        var states=new ArrayList<BlockState>();
        for(Tag entry:blueprint.getList("blocks",Tag.TAG_COMPOUND))
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),((CompoundTag)entry).getCompound("state")));
        assertEquals(samples(),states);
        states.clear();
        assertEquals(4,CampBuilder.placeNearby(loaded,null,4,(pos,state)->states.add(state)));
        assertEquals(samples(),states);
    }
    @Test void safetyAcceptsPlannedOrientationButRejectsPlayerRotationsAndWaterlogging() {
        var state=samples().get(0);String encoded=CampBlockState.encode(state);
        assertTrue(NativeCampConstruction.safeCell(state,encoded));
        assertTrue(NativeCampConstruction.safeCell(state.setValue(StairBlock.SHAPE,StairsShape.OUTER_LEFT),encoded));
        assertFalse(NativeCampConstruction.safeCell(state.setValue(StairBlock.FACING,Direction.EAST),encoded));
        assertFalse(NativeCampConstruction.safeCell(state.setValue(StairBlock.WATERLOGGED,true),encoded));
        assertFalse(NativeCampConstruction.safeCell(Blocks.QUARTZ_STAIRS.defaultBlockState(),encoded));
    }
    @Test void restorationKeepsFirstOriginalAndPreservesRotatedReplacementsAfterReload() {
        var raid=new RaidState("team:test","siege_core",0);
        var original=NbtUtils.writeBlockState(Blocks.GRASS_BLOCK.defaultBlockState());
        var planned=samples().get(0);String encoded=CampBlockState.encode(planned);
        raid.recordCampBlock(0L,encoded,original);
        raid.recordCampBlock(0L,encoded,new CompoundTag());
        var record=RaidState.load(raid.save()).campBlocks.get(0L);
        assertEquals(original,record.getCompound("Original"));
        assertEquals("minecraft:quartz_stairs",record.getString("Placed"));
        assertTrue(CampBlockState.matchesRecord(planned,record));
        assertTrue(CampBlockState.matchesRecord(Blocks.AIR.defaultBlockState(),record));
        assertFalse(CampBlockState.matchesRecord(planned.setValue(StairBlock.HALF,Half.BOTTOM),record));
        raid.recordCampBlock(1L,"minecraft:quartz_stairs",original);
        assertTrue(CampBlockState.matchesRecord(planned,raid.campBlocks.get(1L))); // legacy ledger remains ID-based
    }
    @Test void supplyParsingReceivesBaseBlockAndRetainsFiniteCounts() throws Exception {
        var level=mock(ServerLevel.class);
        try(var bridge=mockStatic(WorkersBridge.class)) {
            bridge.when(()->WorkersBridge.buildMaterial(level,Blocks.QUARTZ_STAIRS)).thenReturn(Items.QUARTZ_BLOCK);
            var supplies=NativeCampConstruction.materials(level,Map.of(0L,CampBlockState.encode(samples().get(0)),1L,"minecraft:quartz_stairs[facing=east]"));
            assertEquals(1,supplies.size());assertTrue(supplies.get(0).is(Items.QUARTZ_BLOCK));assertEquals(2,supplies.get(0).getCount());
        }
    }
}
