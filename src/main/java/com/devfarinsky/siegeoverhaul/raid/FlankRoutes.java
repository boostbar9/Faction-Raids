package com.devfarinsky.siegeoverhaul.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded ground-route search around corners; never edits terrain or teleports. */
public final class FlankRoutes {
    private FlankRoutes() {}
    public static List<Vec3> candidates(Vec3 from,Vec3 objective,boolean rightFirst) {
        Vec3 forward=objective.subtract(from).multiply(1,0,1).normalize();
        if(forward.lengthSqr()<0.01)return List.of();
        List<Vec3> points=new ArrayList<>();double sign=rightFirst?1:-1;
        for(int radius:new int[]{8,16})for(double angle:new double[]{45,-45,90,-90,135,-135}) {
            double a=Math.toRadians(angle*sign);
            points.add(from.add((forward.x*Math.cos(a)-forward.z*Math.sin(a))*radius,0,
                    (forward.x*Math.sin(a)+forward.z*Math.cos(a))*radius));
        }
        return points;
    }
    public static boolean active(Mob mob,long now) {
        var tag=mob.getPersistentData();long until=tag.getLong("FlankUntil");
        if(until<=now || until-now>200)return false;
        return mob.position().distanceToSqr(new Vec3(tag.getDouble("FlankX"),tag.getDouble("FlankY"),tag.getDouble("FlankZ")))>4;
    }
    public static Vec3 find(ServerLevel level,PathfinderMob mob,Vec3 objective) {
        var data=mob.getPersistentData();long now=level.getGameTime();
        long last=data.getLong("FlankSearchAt");
        if(data.contains("FlankSearchAt") && now>=last && now-last<80)return null;
        data.putLong("FlankSearchAt",now);
        int paths=0;
        boolean side=(mob.getUUID().getLeastSignificantBits()&1)==0;
        // Alternate initial side on subsequent searches to avoid repeatedly choosing
        // the same blocked corner. Candidates on both sides are always considered.
        if(data.getBoolean("FlankAlternate"))side=!side;
        data.putBoolean("FlankAlternate",!data.getBoolean("FlankAlternate"));
        for(Vec3 candidate:candidates(mob.position(),objective,side)) {
            for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4}) {
                BlockPos feet=BlockPos.containing(candidate.x,mob.getY()+dy,candidate.z);
                if(!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)
                        || feet.getY()<=level.getMinBuildHeight() || feet.getY()+2>=level.getMaxBuildHeight())continue;
                if(!safeGround(level,feet) || !level.noCollision(mob,mob.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(mob.position()))))continue;
                if(++paths>12)return null;
                var path=mob.getNavigation().createPath(feet,0);
                if(path==null || !path.canReach())continue;
                Vec3 target=Vec3.atBottomCenterOf(feet);
                data.putDouble("FlankX",target.x);data.putDouble("FlankY",target.y);data.putDouble("FlankZ",target.z);data.putLong("FlankUntil",now+200);
                return target;
            }
        }
        return null;
    }
    static boolean safeGround(ServerLevel level,BlockPos feet) {
        var floor=level.getBlockState(feet.below());
        return floor.isFaceSturdy(level,feet.below(),Direction.UP)
                && !floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                && !floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }
}
