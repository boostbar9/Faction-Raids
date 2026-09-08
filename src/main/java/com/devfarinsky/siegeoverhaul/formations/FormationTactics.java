package com.devfarinsky.siegeoverhaul.formations;

import net.minecraft.world.phys.Vec3;

/** Role and terrain policy, independent of entity AI. */
public final class FormationTactics {
    private FormationTactics() {}
    public static String group(String type) {
        return switch(type) {
            case "bowman","crossbowman","scout" -> "ranged";
            case "assassin","assassin_leader","horseman","nomad" -> "mobile";
            case "captain","patrol_leader","siege_engineer" -> "support";
            default -> "front";
        };
    }
    public static Formation choose(String group,boolean narrow,boolean underFire) {
        if(narrow)return Formation.COLUMN;
        if(group.equals("ranged"))return Formation.SKIRMISH;
        if(group.equals("mobile"))return underFire?Formation.SKIRMISH:Formation.WEDGE;
        return group.equals("support")?Formation.SQUARE:Formation.LINE;
    }
    public static Vec3 offset(Formation shape,int index,int count) {
        return switch(shape) {
            case COLUMN -> new Vec3(0,0,-index*1.8);
            case SKIRMISH -> new Vec3((index%4-(Math.min(4,count)-1)/2.0)*2.5,0,-(index/4)*2.5);
            case WEDGE -> new Vec3(index==0?0:((index%2==1?-1:1)*((index+1)/2)*1.7),0,-((index+1)/2)*1.7);
            case SQUARE -> new Vec3((index%3-(Math.min(3,count)-1)/2.0)*1.8,0,-(index/3)*1.8);
            default -> new Vec3((index%6-(Math.min(6,count)-1)/2.0)*1.5,0,-(index/6)*1.8);
        };
    }
}
