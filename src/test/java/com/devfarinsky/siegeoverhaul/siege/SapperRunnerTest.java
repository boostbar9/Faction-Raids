package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SapperRunnerTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);

    private void doorAt(BlockPos lower) {
        blocks.put(lower, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        blocks.put(lower.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call ->
                blocks.getOrDefault(call.getArgument(0), Blocks.AIR.defaultBlockState()));
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            BlockState previous = blocks.remove(pos);
            // Reproduce the neighbor update that deletes the other half before
            // the old one-pass sapper sweep gets a chance to snapshot it.
            if (previous != null && previous.getBlock() instanceof DoorBlock) {
                blocks.remove(previous.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
            }
            return true;
        });
    }

    @Test
    void blastRecordsBothHalvesBeforeNeighborUpdatesDeleteThem() {
        BlockPos lower = new BlockPos(0, 64, 0);
        doorAt(lower);
        SapperRunner.breachBlocks(level, raid, lower, 3);
        assertEquals(2, raid.breachedBlocks.size());
        assertEquals("lower", raid.breachedBlocks.get(lower.asLong()).getCompound("Properties").getString("half"));
        assertEquals("upper", raid.breachedBlocks.get(lower.above().asLong()).getCompound("Properties").getString("half"));
        assertTrue(blocks.isEmpty());
    }

    @Test
    void doorHalfOutsideBlastBandIsStillRecorded() {
        BlockPos center = new BlockPos(0, 64, 0);
        BlockPos lower = center.above(4);
        doorAt(lower);
        SapperRunner.breachBlocks(level, raid, center, 3);
        assertTrue(raid.breachedBlocks.containsKey(lower.above().asLong()));
        assertEquals(2, raid.breachedBlocks.size());
    }

    @Test
    void capacityCannotLeaveHalfADoorUnrecorded() {
        BlockPos lower = new BlockPos(0, 64, 0);
        doorAt(lower);
        int limit = RaidConfig.MAX_RESTORABLE_BLOCKS.get();
        for (int i = 1; i < limit; i++) {
            raid.breachedBlocks.put(new BlockPos(i, 1, 20).asLong(), new net.minecraft.nbt.CompoundTag());
        }
        SapperRunner.breachBlocks(level, raid, lower, 3);
        assertEquals(limit - 1, raid.breachedBlocks.size());
        assertEquals(2, blocks.size());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }
}
