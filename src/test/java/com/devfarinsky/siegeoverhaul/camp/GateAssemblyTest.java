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
    @BeforeEach void setup() {
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
        assertTrue(GateAssembly.install(level,raid));assertTrue(WarGate.ready(level,raid));
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
}
