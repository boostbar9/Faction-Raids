package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampCleanupTest extends MinecraftTestSupport {
    @Test
    void cleanupRestoresGroundBeforePlantAndPreservesNewPlayerBlock() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos ground = new BlockPos(0,63,0), plant = ground.above(), player = ground.east();
        when(level.getBlockState(any())).thenAnswer(c -> world.getOrDefault(c.getArgument(0), Blocks.AIR.defaultBlockState()));
        when(level.setBlock(any(), any(), anyInt())).thenAnswer(c -> {
            BlockPos pos = c.getArgument(0);
            BlockState next = c.getArgument(1);
            if (pos.equals(plant) && next.is(Blocks.GRASS)) {
                assertTrue(world.getOrDefault(ground, Blocks.AIR.defaultBlockState()).is(Blocks.GRASS_BLOCK),
                        "Ground must exist before restoring its plant");
            }
            world.put(pos, next);
            return true;
        });
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);
        raid.recordCampBlock(ground.asLong(), "minecraft:air", BlockRestoration.serializeState(level, ground, Blocks.GRASS_BLOCK.defaultBlockState()));
        raid.recordCampBlock(plant.asLong(), "minecraft:air", BlockRestoration.serializeState(level, plant, Blocks.GRASS.defaultBlockState()));
        raid.recordCampBlock(player.asLong(), "minecraft:dirt", BlockRestoration.serializeState(level, player, Blocks.AIR.defaultBlockState()));
        world.put(player, Blocks.OAK_PLANKS.defaultBlockState());
        var cleanup = RaidEvents.class.getDeclaredMethod("cleanupWarCamp", ServerLevel.class, RaidSavedData.RaidState.class);
        cleanup.setAccessible(true);
        cleanup.invoke(null, level, raid);
        assertTrue(world.get(ground).is(Blocks.GRASS_BLOCK));
        assertTrue(world.get(plant).is(Blocks.GRASS));
        assertTrue(world.get(player).is(Blocks.OAK_PLANKS));
        assertTrue(raid.campBlocks.isEmpty());
    }
}
