package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EnemyCoreKeepTest extends MinecraftTestSupport {
    @Test void everyHostCoreKeepUsesItsOwnPaletteWithoutChangingCaptureSpace() {
        BlockPos base=new BlockPos(0,64,0);
        var expected=Map.of(
                "blackbay_reavers","minecraft:prismarine_bricks",
                "hollowfang_clan","minecraft:polished_blackstone_bricks",
                "emberchant_zealots","minecraft:cut_copper",
                "crownfall_exiles","minecraft:quartz_bricks",
                "wilds_marauders","minecraft:mossy_stone_bricks");
        var palettes=new java.util.HashSet<java.util.Collection<String>>();
        var shapes=new java.util.HashSet<java.util.Set<BlockPos>>();
        for(var entry:expected.entrySet()) {
            var plan=EnemyCore.keepBlueprint(base,entry.getKey());
            assertTrue(plan.size()>=41 && plan.size()<=49);
            assertTrue(plan.containsValue(entry.getValue()));
            assertFalse(plan.containsKey(EnemyCore.corePos(base)));
            assertTrue(palettes.add(new java.util.HashSet<>(plan.values())));
            assertTrue(shapes.add(new java.util.HashSet<>(plan.keySet())),
                    "duplicate core-keep silhouette for "+entry.getKey());
            for(BlockPos neighbour:new BlockPos[]{EnemyCore.corePos(base).north(),EnemyCore.corePos(base).south(),
                    EnemyCore.corePos(base).east(),EnemyCore.corePos(base).west(),EnemyCore.corePos(base).above()})
                assertFalse(plan.containsKey(neighbour));
        }
        assertEquals(5,palettes.size());
        assertEquals(5,shapes.size());
    }
    @Test void keepIsBoundedRaisedAndLeavesTheCoreVisibleForCapture() {
        BlockPos base = new BlockPos(120, 68, -240);
        Map<BlockPos, String> plan = EnemyCore.keepBlueprint(base);
        BlockPos core = EnemyCore.corePos(base);

        // Small, finite citadel, not a fortress.
        assertTrue(plan.size() <= 64, "keep must stay a small bounded structure: " + plan.size());
        assertTrue(plan.containsValue("minecraft:polished_diorite"));
        assertTrue(plan.containsValue("minecraft:quartz_pillar"));
        assertTrue(plan.containsValue("minecraft:sea_lantern"), "needs a beacon-like vertical marker");

        // The core rests one block above the plinth centre and is never a decoration cell.
        assertEquals(base.above(), core);
        assertFalse(plan.containsKey(core));

        for (BlockPos pos : plan.keySet()) {
            assertTrue(Math.abs(pos.getX() - base.getX()) <= 2 && Math.abs(pos.getZ() - base.getZ()) <= 2,
                    "keep must stay within a 5x5 footprint");
            assertTrue(pos.getY() >= base.getY() && pos.getY() <= base.getY() + 6);
        }

        // Line of sight to the core must stay open: nothing directly beside it on
        // its own level, nor stacked above the standing space, so raiders can hold it.
        for (BlockPos neighbour : new BlockPos[]{
                core.north(), core.south(), core.east(), core.west(),
                core.above(), core.above(2)}) {
            assertFalse(plan.containsKey(neighbour), "blocked line of sight at " + neighbour);
        }

        // A raised plinth: a full 5x5 walkable floor one block below the core.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            assertEquals("minecraft:polished_diorite", plan.get(base.offset(dx, 0, dz)));
    }
}
