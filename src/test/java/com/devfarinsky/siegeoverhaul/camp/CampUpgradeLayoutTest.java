package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CampUpgradeLayoutTest extends MinecraftTestSupport {
    @Test void allStagesKeepEntrancesAndCenterAislesClearInEveryOrientation() {
        BlockPos center=new BlockPos(-31,70,-47);
        for(Direction front:Direction.Plane.HORIZONTAL) for(int stage=0;stage<3;stage++) {
            var plan=CampUpgradeLayout.structure(center,front,stage);
            // Include worst-case 7x7 floor plus two foundation layers in the budget.
            assertTrue(plan.size()+147<512);
            for(int z=0;z<=3;z++) for(int x=-1;x<=1;x++) for(int y=1;y<=2;y++)
                assertFalse(plan.containsKey(center.relative(front,z).relative(front.getClockWise(),x).above(y).asLong()));
            for(long key:plan.keySet()) {
                BlockPos p=BlockPos.of(key);
                assertTrue(Math.abs(p.getX()-center.getX())<=3 && Math.abs(p.getZ()-center.getZ())<=3);
                assertTrue(p.getY()>70 && p.getY()<=75);
            }
            assertNotNull(plan.get(center.relative(front,3).above(3).asLong()));
        }
    }

    @Test void everyRidgeCellHasRoofSupportAndStagesHaveDistinctFurnishings() {
        var center=BlockPos.ZERO;
        for(int stage=0;stage<3;stage++) {
            var plan=CampUpgradeLayout.structure(center,Direction.NORTH,stage);
            for(long key:plan.keySet()) {
                BlockPos p=BlockPos.of(key);
                if(p.getY()==5) assertEquals(plan.get(key),plan.get(p.below().asLong()));
            }
            var blueprint=NativeCampConstruction.blueprint(plan);
            assertEquals(plan.size(),blueprint.getList("blocks",10).size());
        }
        assertTrue(CampUpgradeLayout.structure(center,Direction.NORTH,0).containsValue("minecraft:hay_block"));
        assertTrue(CampUpgradeLayout.structure(center,Direction.NORTH,1).containsValue("minecraft:smithing_table"));
        assertTrue(CampUpgradeLayout.structure(center,Direction.NORTH,2).containsValue("minecraft:cartography_table"));
        for(int stage=0;stage<3;stage++) {
            var plan=CampUpgradeLayout.structure(center,Direction.NORTH,stage);
            assertTrue(plan.containsValue("minecraft:quartz_pillar"));
            assertTrue(plan.containsValue(stage==2?"minecraft:quartz_bricks":"minecraft:polished_diorite"));
        }
        assertThrows(IllegalArgumentException.class,()->CampUpgradeLayout.structure(center,Direction.UP,0));
    }
}
