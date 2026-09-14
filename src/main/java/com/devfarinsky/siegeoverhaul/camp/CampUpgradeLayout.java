package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small, finite native-builder upgrades. Local positive Z points toward the camp. */
final class CampUpgradeLayout {
    private CampUpgradeLayout() {}

    static Map<Long, String> structure(BlockPos center, Direction entrance, int stage) {
        if (entrance.getAxis().isVertical() || stage < 0 || stage > 2)
            throw new IllegalArgumentException("Invalid camp upgrade");
        Map<Long, String> plan = new LinkedHashMap<>();
        String base = stage == 2 ? "minecraft:stone_bricks" : "minecraft:cobblestone";
        // Solid knee walls and timber frames protect the interior. A three-wide,
        // two-high entrance keeps builders and troops moving through the frontage.
        for (int y=1;y<=3;y++) for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
            boolean edge=Math.abs(x)==3 || Math.abs(z)==3;
            boolean corner=Math.abs(x)==3 && Math.abs(z)==3;
            boolean doorway=z==3 && Math.abs(x)<=1 && y<=2;
            if (!edge || doorway) continue;
            String block=corner || y==3 ? "minecraft:stripped_spruce_log"
                    : y==1 ? base : "minecraft:spruce_fence";
            put(plan,center,entrance,x,y,z,block);
        }
        // A continuous eave layer supports the raised ridge; no floating roof cells.
        String roof=stage==0?"minecraft:gray_wool":stage==1?"minecraft:red_terracotta":"minecraft:dark_oak_planks";
        for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++)
            put(plan,center,entrance,x,4,z,roof);
        for(int x=-1;x<=1;x++) for(int z=-3;z<=3;z++)
            put(plan,center,entrance,x,5,z,roof);
        // Supplies stay against the rear wall, leaving the center aisle unobstructed.
        if(stage==0) {
            for(int x:new int[]{-2,2}) for(int z=-2;z<=0;z++)
                put(plan,center,entrance,x,1,z,"minecraft:hay_block");
        } else if(stage==1) {
            put(plan,center,entrance,-2,1,-2,"minecraft:crafting_table");
            put(plan,center,entrance,2,1,-2,"minecraft:smithing_table");
            put(plan,center,entrance,2,1,-1,"minecraft:hay_block");
        } else {
            for(int x=-1;x<=1;x++) put(plan,center,entrance,x,1,-2,"minecraft:cartography_table");
            put(plan,center,entrance,-2,1,-2,"minecraft:bookshelf");
            put(plan,center,entrance,2,1,-2,"minecraft:bookshelf");
        }
        return plan;
    }

    private static void put(Map<Long,String> plan,BlockPos center,Direction front,
                            int x,int y,int z,String block) {
        plan.put(center.relative(front,z).relative(front.getClockWise(),x).above(y).asLong(),block);
    }
}
