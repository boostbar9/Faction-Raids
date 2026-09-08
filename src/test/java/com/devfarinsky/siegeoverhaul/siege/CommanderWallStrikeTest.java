package com.devfarinsky.siegeoverhaul.siege;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommanderWallStrikeTest extends MinecraftTestSupport {
    @Test void strikesOnlyCommonMasonryAndNeverStorageOrValuables() {
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE_BRICKS,Blocks.COBBLESTONE,Blocks.BRICKS})
            assertTrue(CommanderWallStrikeGoal.breakable(block.defaultBlockState()));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.BEDROCK,Blocks.OBSIDIAN,Blocks.CHEST,Blocks.BARREL,Blocks.DIAMOND_BLOCK,Blocks.IRON_ORE,Blocks.LAVA,Blocks.AIR})
            assertFalse(CommanderWallStrikeGoal.breakable(block.defaultBlockState()));
        // These full blocks remain outside the ordinary breacher/sapper whitelist.
        assertFalse(BlockRestoration.isBreachable(Blocks.STONE_BRICKS.defaultBlockState()));
    }
}
