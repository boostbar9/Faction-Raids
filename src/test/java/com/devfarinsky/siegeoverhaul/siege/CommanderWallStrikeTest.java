package com.devfarinsky.siegeoverhaul.siege;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CommanderWallStrikeTest extends MinecraftTestSupport {
    @Test void strikesOnlyCommonMasonryAndNeverStorageOrValuables() {
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE_BRICKS,Blocks.COBBLESTONE,Blocks.BRICKS})
            assertTrue(CommanderWallStrikeGoal.breakable(block.defaultBlockState()));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.BEDROCK,Blocks.OBSIDIAN,Blocks.CHEST,Blocks.BARREL,Blocks.DIAMOND_BLOCK,Blocks.IRON_ORE,Blocks.LAVA,Blocks.AIR})
            assertFalse(CommanderWallStrikeGoal.breakable(block.defaultBlockState()));
        // These full blocks remain outside the ordinary breacher/sapper whitelist.
        assertFalse(BlockRestoration.isBreachable(Blocks.STONE_BRICKS.defaultBlockState()));
        assertTrue(BlockRestoration.isCoreApproachBreachable(Blocks.STONE_BRICKS.defaultBlockState()));
        // Vanilla tag membership is populated by Minecraft's datapack loader,
        // which this pure unit-test bootstrap intentionally does not run.
        // CommanderWallStrikeGoal's existing BlockTags.PLANKS branch covers
        // planks in-game; use registry-independent masonry for this unit seam.
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.OBSIDIAN,Blocks.CHEST,Blocks.DIAMOND_BLOCK,Blocks.IRON_ORE})
            assertFalse(BlockRestoration.isCoreApproachBreachable(block.defaultBlockState()));
    }

    @Test void finalApproachMasonryIsSnapshottedWithinRestorationBudget() {
        ServerLevel level=mock(ServerLevel.class); BlockPos pos=new BlockPos(3,70,-4);
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE_BRICKS.defaultBlockState());
        var ledger=new LinkedHashMap<Long,net.minecraft.nbt.CompoundTag>();
        assertEquals(java.util.List.of(pos),BlockRestoration.snapshotCoreApproachBreach(level,ledger,pos,1));
        assertTrue(ledger.containsKey(pos.asLong()));

        BlockPos second=pos.east();
        when(level.getBlockState(second)).thenReturn(Blocks.BRICKS.defaultBlockState());
        assertTrue(BlockRestoration.snapshotCoreApproachBreach(level,ledger,second,1).isEmpty(),
                "restoration cap must still prevent additional block damage");
    }
}
