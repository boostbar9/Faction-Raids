package com.devfarinsky.siegeoverhaul.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;
import java.util.Set;

/** Route the final approach to reachable floor beside a solid core. No block changes. */
public final class CoreApproach {
    private CoreApproach() {}

    public static boolean moveTo(ServerLevel level, Mob mob, Vec3 objective, double speed) {
        if (mob.distanceToSqr(objective)>24*24) return false;
        Set<BlockPos> targets=targets(level,mob,BlockPos.containing(objective));
        if(targets.isEmpty())return false;
        var path=mob.getNavigation().createPath(targets,0);
        return path!=null && path.canReach() && mob.getNavigation().moveTo(path,speed);
    }

    static Set<BlockPos> targets(ServerLevel level, Mob mob, BlockPos core) {
        Set<BlockPos> targets=new LinkedHashSet<>();
        for(Direction side:Direction.Plane.HORIZONTAL) for(int dy:new int[]{0,1,-1}) {
            BlockPos feet=core.relative(side).above(dy),floor=feet.below();
            if(feet.getY()<=level.getMinBuildHeight() || feet.getY()+2>=level.getMaxBuildHeight()
                    || !level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet))continue;
            var support=level.getBlockState(floor);
            if(!support.isFaceSturdy(level,floor,Direction.UP) || !support.getFluidState().isEmpty()
                    || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                    || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS))continue;
            if(!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                    || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty())continue;
            // Use the actual unit's hitbox, including tall/wide companion mobs.
            Vec3 at=Vec3.atBottomCenterOf(feet);
            if(!level.noCollision(mob,mob.getBoundingBox().move(at.subtract(mob.position()))))continue;
            targets.add(feet);
        }
        return targets;
    }
}
