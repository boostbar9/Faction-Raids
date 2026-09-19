package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small, finite native-builder upgrades. Local positive Z points toward the camp. */
final class CampUpgradeLayout {
    private CampUpgradeLayout() {}

    static Map<Long, String> structure(BlockPos center, Direction entrance, int stage) {
        return structure(center, entrance, stage, "");
    }

    static Map<Long, String> structure(BlockPos center, Direction entrance, int stage, String factionId) {
        if (entrance.getAxis().isVertical() || stage < 0 || stage > 2)
            throw new IllegalArgumentException("Invalid camp upgrade");
        Map<Long, String> plan = new LinkedHashMap<>();
        Palette palette=palette(factionId,stage);
        // Every host keeps the same finite, navigable footprint, but the wall,
        // columns, rails and roof communicate its patron before combat begins.
        for (int y=1;y<=3;y++) for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
            boolean edge=Math.abs(x)==3 || Math.abs(z)==3;
            boolean corner=Math.abs(x)==3 && Math.abs(z)==3;
            boolean doorway=z==3 && Math.abs(x)<=1 && y<=2;
            if (!edge || doorway) continue;
            String block=corner || y==3 ? palette.column
                    : y==1 ? palette.base : palette.rail;
            put(plan,center,entrance,x,y,z,block);
        }
        // A continuous eave supports each host's deliberately different skyline.
        for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++)
            put(plan,center,entrance,x,4,z,palette.roof);
        skyline(plan,center,entrance,factionId,palette);
        // Supplies stay against the rear wall, leaving the center aisle unobstructed.
        // A named centrepiece keystone at x=0,z=-2 gives each building its identity
        // and a single block whose loss (broken or burned) ends its camp effect.
        if(stage==0) {
            put(plan,center,entrance,0,1,-2,"minecraft:hay_block");
            for(int x:new int[]{-2,2}) for(int z=-2;z<=0;z++)
                put(plan,center,entrance,x,1,z,"minecraft:hay_block");
        } else if(stage==1) {
            put(plan,center,entrance,0,1,-2,"minecraft:anvil");
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

    private static Palette palette(String factionId,int stage) {
        return switch(factionId==null?"":factionId) {
            case "blackbay_reavers" -> new Palette("minecraft:prismarine_bricks","minecraft:dark_prismarine",
                    "minecraft:prismarine_wall",pick(stage,"minecraft:cyan_terracotta","minecraft:blue_terracotta","minecraft:light_blue_terracotta"));
            case "hollowfang_clan" -> new Palette("minecraft:polished_blackstone_bricks","minecraft:chiseled_polished_blackstone",
                    "minecraft:iron_bars",pick(stage,"minecraft:red_terracotta","minecraft:nether_bricks","minecraft:black_terracotta"));
            case "emberchant_zealots" -> new Palette("minecraft:bricks","minecraft:polished_blackstone_bricks",
                    "minecraft:nether_brick_fence",pick(stage,"minecraft:orange_terracotta","minecraft:cut_copper","minecraft:polished_blackstone"));
            case "crownfall_exiles" -> new Palette("minecraft:quartz_bricks","minecraft:quartz_pillar",
                    "minecraft:birch_fence",pick(stage,"minecraft:white_terracotta","minecraft:light_blue_terracotta","minecraft:quartz_bricks"));
            case "wilds_marauders" -> new Palette("minecraft:mossy_stone_bricks","minecraft:stripped_birch_log",
                    "minecraft:oak_fence",pick(stage,"minecraft:green_terracotta","minecraft:moss_block","minecraft:mossy_stone_bricks"));
            default -> new Palette(stage==2?"minecraft:quartz_bricks":"minecraft:polished_diorite",
                    "minecraft:quartz_pillar","minecraft:birch_fence",
                    pick(stage,"minecraft:yellow_terracotta","minecraft:orange_terracotta","minecraft:light_blue_terracotta"));
        };
    }

    private static String pick(int stage,String first,String second,String third) {
        return stage==0?first:stage==1?second:third;
    }

    private static void skyline(Map<Long,String> plan,BlockPos center,Direction entrance,String factionId,Palette palette) {
        switch(factionId==null?"":factionId) {
            case "blackbay_reavers" -> {
                // A broad crest reads like a breaking wave.
                for(int x=-2;x<=2;x++) for(int z=-3;z<=3;z++)
                    if(Math.abs(x)<=1 || Math.abs(z)==3) put(plan,center,entrance,x,5,z,palette.roof);
                put(plan,center,entrance,0,5,-3,"minecraft:sea_lantern");
            }
            case "hollowfang_clan" -> {
                // Low battlements and a central red standard keep the roof martial.
                for(int x=-3;x<=3;x+=2) for(int z:new int[]{-3,3})
                    put(plan,center,entrance,x,5,z,"minecraft:chiseled_polished_blackstone");
                for(int z=-2;z<=2;z++) put(plan,center,entrance,0,5,z,palette.roof);
            }
            case "emberchant_zealots" -> {
                // Twin copper chimney stacks distinguish the forge at range.
                for(int x:new int[]{-2,2}) for(int z=-2;z<=-1;z++)
                    put(plan,center,entrance,x,5,z,"minecraft:cut_copper");
                for(int x=-1;x<=1;x++) put(plan,center,entrance,x,5,0,palette.roof);
            }
            case "crownfall_exiles" -> {
                // Symmetrical front and rear pediments evoke an acropolis.
                for(int x=-2;x<=2;x++) for(int z:new int[]{-3,3})
                    put(plan,center,entrance,x,5,z,x==0?"minecraft:chiseled_quartz_block":palette.roof);
            }
            case "wilds_marauders" -> {
                // Four light canopy peaks leave the middle visually open.
                for(int x:new int[]{-2,2}) for(int z:new int[]{-2,2})
                    put(plan,center,entrance,x,5,z,"minecraft:stripped_birch_log");
                for(int x=-1;x<=1;x++) put(plan,center,entrance,x,5,-2,"minecraft:moss_block");
            }
            default -> {
                for(int x=-1;x<=1;x++) for(int z=-3;z<=3;z++)
                    put(plan,center,entrance,x,5,z,palette.roof);
            }
        }
    }

    private record Palette(String base,String column,String rail,String roof) {}

    private static void put(Map<Long,String> plan,BlockPos center,Direction front,
                            int x,int y,int z,String block) {
        plan.put(center.relative(front,z).relative(front.getClockWise(),x).above(y).asLong(),block);
    }
}
