package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GateAssemblyTest extends MinecraftTestSupport {
    ServerLevel level=mock(ServerLevel.class);
    RaidSavedData.RaidState raid=new RaidSavedData.RaidState("team:test","siege_core",0);
    BlockPos center=new BlockPos(0,64,0);
    Map<BlockPos,BlockState> world=new HashMap<>();
    RaidSavedData saved;
    org.mockito.MockedStatic<RaidSavedData> saves;
    @AfterEach void closeSaves() { saves.close(); }
    @BeforeEach void setup() {
        saved=new RaidSavedData();saves=mockStatic(RaidSavedData.class);
        saves.when(()->RaidSavedData.get(null)).thenReturn(saved);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getBlockState(any())).thenAnswer(a->world.getOrDefault(a.getArgument(0),Blocks.AIR.defaultBlockState()));
        when(level.setBlock(any(),any(),anyInt())).thenAnswer(a->{world.put(((BlockPos)a.getArgument(0)).immutable(),a.getArgument(1));return true;});
        raid.warGate.putLong("Center",center.asLong());raid.warGate.putInt("Facing",Direction.NORTH.get2DDataValue());
        var cells=new CompoundTag();WarGate.blueprint(center,Direction.NORTH).forEach((p,id)->cells.putString(Long.toString(p),id));raid.warGate.put("Blocks",cells);
        raid.pendingCampBlocks.putAll(WarGate.blueprint(center,Direction.NORTH));
    }
    @Test void assemblesCrestClearsFlowersAndLeavesOnlyCampJobs() {
        BlockPos job=center.offset(10,0,0);raid.pendingCampBlocks.put(job.asLong(),"minecraft:stone_bricks");
        world.put(center.above(),Blocks.DANDELION.defaultBlockState());
        assertFalse(saved.isDirty());
        assertTrue(GateAssembly.install(level,raid));assertTrue(saved.isDirty());assertTrue(WarGate.ready(level,raid));
        assertEquals(Blocks.LODESTONE.defaultBlockState(),world.get(center.above(7)));
        assertTrue(world.get(center.above()).isAir());
        assertEquals(Set.of(job.asLong()),raid.pendingCampBlocks.keySet());
        assertTrue(raid.warGate.getCompound("RoadBefore").contains(Long.toString(center.above().asLong())));
        var saved=RaidSavedData.RaidState.load(raid.save());assertTrue(saved.warGate.getBoolean("Assembled493"));
        int ledger=raid.campBlocks.size();assertTrue(GateAssembly.install(level,raid));assertEquals(ledger,raid.campBlocks.size());
    }
    @Test void foreignReplacementRejectsWholeAssembly() {
        world.put(center.above(7),Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertFalse(GateAssembly.install(level,raid));verify(level,never()).setBlock(any(),any(),anyInt());
        assertFalse(raid.warGate.getBoolean("Assembled493"));assertTrue(raid.campBlocks.isEmpty());
    }
    @Test void rejectedPlacementRollsBackWithoutCompletingGateOrRemovingJobs() {
        doReturn(false).when(level).setBlock(eq(center),any(),anyInt());
        assertFalse(GateAssembly.install(level,raid));assertFalse(raid.warGate.getBoolean("Assembled493"));
        assertFalse(raid.pendingCampBlocks.isEmpty());assertTrue(raid.campBlocks.isEmpty());
        assertTrue(world.values().stream().allMatch(BlockState::isAir));
    }
    @Test void invalidSavedCoordinatesAndBlockIdsRejectWithoutMutations() {
        var original=raid.warGate.copy();
        for(String invalid:new String[]{"not-a-position","999999999999999999999999999"}) {
            raid.warGate=original.copy();raid.warGate.getCompound("Blocks").putString(invalid,"minecraft:stone");
            assertFalse(GateAssembly.install(level,raid));assertFalse(WarGate.ready(level,raid));
            assertEquals("War Gate: invalid saved blueprint",WarGate.status(level,raid));
        }
        for(String invalid:new String[]{"BAD ID!","missing_mod:unknown_block"}) {
            raid.warGate=original.copy();raid.warGate.getCompound("Blocks").putString(Long.toString(center.asLong()),invalid);
            assertFalse(GateAssembly.install(level,raid));assertFalse(WarGate.ready(level,raid));
            assertEquals("War Gate: invalid saved blueprint",WarGate.status(level,raid));
        }
        raid.warGate=original.copy();raid.warGate.getCompound("Blocks").putInt(Long.toString(center.asLong()),12);
        assertFalse(GateAssembly.install(level,raid));
        raid.warGate=original.copy();var before=new CompoundTag();before.put("broken",new CompoundTag());raid.warGate.put("RoadBefore",before);
        assertFalse(GateAssembly.install(level,raid));assertFalse(CampRoad.prepare(level,raid));
        verify(level,never()).setBlock(any(),any(),anyInt());assertTrue(raid.campBlocks.isEmpty());assertFalse(saved.isDirty());
    }
    @Test void missingCenterCannotAssembleAtWorldOrigin() {
        raid.warGate.remove("Center");assertFalse(GateAssembly.install(level,raid));
        verify(level,never()).setBlock(any(),any(),anyInt());assertFalse(saved.isDirty());
    }
    @Test void malformedKeysDoNotCrashRepairOrCleanup() {
        raid.warGate.getCompound("Blocks").putString("broken","minecraft:stone");
        raid.warGate.getCompound("Blocks").putString("123","BAD ID!");
        assertDoesNotThrow(()->NativeCampConstruction.recoverMissingGateCells(level,raid));
        try(var loading=mockStatic(CampLoading.class)) {
            assertDoesNotThrow(()->WarGate.cleanup(level,raid));
        }
    }
    @Test void cleanupPreservesForeignReplacementInsteadOfRestoringOverIt() {
        var original=com.devfarinsky.siegeoverhaul.siege.BlockRestoration.serializeState(level,center,Blocks.DIRT.defaultBlockState());
        raid.recordCampBlock(center.asLong(),"minecraft:polished_blackstone_bricks",original);
        world.put(center,Blocks.DIAMOND_BLOCK.defaultBlockState());
        try(var loading=mockStatic(CampLoading.class)) { WarGate.cleanup(level,raid); }
        assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(),world.get(center));
        verify(level,never()).setBlock(eq(center),any(),anyInt());
    }
    @Test void cleanupStillRestoresOriginalSoilAfterRemovingGate() {
        var original=com.devfarinsky.siegeoverhaul.siege.BlockRestoration.serializeState(level,center,Blocks.DIRT.defaultBlockState());
        raid.recordCampBlock(center.asLong(),"minecraft:polished_blackstone_bricks",original);
        world.put(center,Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        try(var loading=mockStatic(CampLoading.class)) { WarGate.cleanup(level,raid); }
        assertEquals(Blocks.DIRT.defaultBlockState(),world.get(center));
    }
}
