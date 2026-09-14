package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.formations.RecruitsFormationBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Walk to a siege ladder, climb its actual rungs, and step onto the wall without teleporting. */
public final class RaiderLadderGoal extends Goal {
    private final Mob mob;
    private Route route;
    private long deadline, retryAfter;
    private int ticks, crestTicks;
    private BlockPos landing;
    public record Route(BlockPos base, Direction intoWall, int height) {
        public BlockPos exit() { return base.relative(intoWall).above(height); }
    }
    public RaiderLadderGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
    private static RaiderLadderGoal find(Mob mob) {
        for (var wrapped : mob.goalSelector.getAvailableGoals())
            if (wrapped.getGoal() instanceof RaiderLadderGoal goal) return goal;
        return null;
    }
    public static boolean assigned(Mob mob) {
        if (mob.goalSelector == null) return false;
        var goal = find(mob);
        return goal != null && goal.route != null && goal.valid();
    }
    public static Route readRoute(ServerLevel level, BlockPos base) {
        if (!level.hasChunkAt(base)) return null;
        var bottom = level.getBlockState(base);
        if (!bottom.is(Blocks.LADDER) || level.getBlockState(base.below()).is(Blocks.LADDER)) return null;
        Direction face = bottom.getValue(LadderBlock.FACING);
        int height = 0;
        while (height <= LadderBuilder.MAX_WALL_HEIGHT) {
            BlockPos rung = base.above(height);
            if (!level.hasChunkAt(rung)) return null;
            var block = level.getBlockState(rung);
            if (!block.is(Blocks.LADDER) || block.getValue(LadderBlock.FACING) != face) break;
            if (!block.canSurvive(level, rung)) return null;
            height++;
        }
        if (height < 2 || height > LadderBuilder.MAX_WALL_HEIGHT) return null;
        Route route = new Route(base.immutable(), face.getOpposite(), height);
        BlockPos exit = route.exit();
        if (!level.hasChunkAt(exit) || !level.getBlockState(exit).isAir() || !level.getBlockState(exit.above()).isAir()
                || !level.getBlockState(exit.below()).isFaceSturdy(level, exit.below(), Direction.UP)) return null;
        return route;
    }
    public static void assignNearby(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (!RaidConfig.ENABLE_LADDER_BUILDING.get()) return;
        List<Route> routes = new ArrayList<>();
        for (var entry : state.campBlocks.entrySet()) {
            if (!"minecraft:ladder".equals(entry.getValue().getString("Placed"))) continue;
            Route route = readRoute(level, BlockPos.of(entry.getKey()));
            if (route != null) routes.add(route);
        }
        if (routes.isEmpty()) return;
        Map<Route, Integer> users = new HashMap<>();
        for (UUID id : state.raiders) if (level.getEntity(id) instanceof Mob mob) {
            var active = find(mob);
            if (active != null && active.route != null) users.merge(active.route, 1, Integer::sum);
        }
        for (UUID id : state.raiders) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive() || mob.isPassenger() || assigned(mob)) continue;
            var goal = find(mob);
            if (goal != null && level.getGameTime() < goal.retryAfter) continue;
            Route best = null; double nearest = 256;
            for (Route route : routes) {
                Vec3 base = Vec3.atBottomCenterOf(route.base());
                double distance = mob.distanceToSqr(base);
                // Only ascend from the approach side, toward the objective, while below the exit.
                Vec3 into = Vec3.atLowerCornerOf(route.intoWall().getNormal());
                if (users.getOrDefault(route, 0) >= 3 || distance >= nearest || mob.getY() >= route.exit().getY()
                        || mob.position().subtract(base).dot(into) > .5
                        || Vec3.atCenterOf(objective).subtract(base).dot(into) <= 0) continue;
                best = route; nearest = distance;
            }
            if (best == null) continue;
            // Path to solid ground at the ladder base, not the air block itself.
            // Vanilla ground navigation refuses to end a path on air, so aiming
            // directly at base() often produces no path even when a raider is
            // standing three blocks away with a clear approach.
            var approach = pathToBase(mob, best);
            if (approach == null || !approach.canReach()) continue;
            if (goal == null) { goal = new RaiderLadderGoal(mob); mob.goalSelector.addGoal(0, goal); }
            users.merge(best, 1, Integer::sum);
            goal.route = best; goal.deadline = level.getGameTime() + 400; goal.ticks = 0; goal.crestTicks=0; goal.landing=null;
            RecruitsFormationBridge.release(mob);
        }
    }
    /** Continue beyond the outside wall lip to supported ground on the inside.
     * Cross at most eight blocks and drop at most six; normal fall damage applies. */
    static BlockPos findLanding(ServerLevel level,Route route) {
        BlockPos exit=route.exit();
        for(int forward=1;forward<=8;forward++) {
            BlockPos top=exit.relative(route.intoWall(),forward);
            if(!level.hasChunkAt(top) || !level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir())return null;
            // A level, solid wall-top cell is a crossing surface, not the inside landing.
            if(level.getBlockState(top.below()).isFaceSturdy(level,top.below(),Direction.UP))continue;
            for(int down=1;down<=6;down++) {
                BlockPos feet=top.below(down);
                if(!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty())break;
                var floor=level.getBlockState(feet.below());
                if(!level.getBlockState(feet).isAir())break;
                if(floor.isFaceSturdy(level,feet.below(),Direction.UP)) {
                    if(!level.getBlockState(feet.above()).isAir() || floor.is(Blocks.MAGMA_BLOCK)
                            || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.CACTUS))return null;
                    return feet;
                }
            }
            return null; // Do not walk over an unverified deep drop searching for another landing.
        }
        return null;
    }

    @Override public boolean canUse() { return valid(); }
    @Override public boolean canContinueToUse() { return valid(); }
    private boolean valid() {
        if (route == null) return false;
        if (!mob.isAlive() || mob.isPassenger() || !RaidConfig.ENABLE_LADDER_BUILDING.get()
                || !(mob.level() instanceof ServerLevel level) || level.getGameTime() >= deadline
                || !route.equals(readRoute(level, route.base()))) {
            stop(); return false;
        }
        return true;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    /** Path to a solid stand-on square adjacent to the ladder base, since
     *  ground navigators cannot end a path on the ladder's air block. */
    private static net.minecraft.world.level.pathfinder.Path pathToBase(Mob mob, Route route) {
        if (!(mob.level() instanceof ServerLevel level)) return null;
        BlockPos stand = standingPos(level, route);
        if (stand == null) return mob.getNavigation().createPath(route.base(), 1);
        // Accuracy 0 works because the target is now a solid navigable block.
        return mob.getNavigation().createPath(stand, 0);
    }

    /** Find a solid block on the approach side of the ladder base to stand on. */
    private static BlockPos standingPos(ServerLevel level, Route route) {
        if (level == null) return null;
        BlockPos front = route.base().relative(route.intoWall().getOpposite());
        if (level.hasChunkAt(front)
                && level.getBlockState(front).isAir()
                && level.getBlockState(front.below()).isFaceSturdy(level, front.below(), Direction.UP)) {
            return front;
        }
        // Fall back to the block directly below the ladder base if that is standable.
        BlockPos under = route.base();
        if (level.hasChunkAt(under)
                && level.getBlockState(under).isAir()
                && level.getBlockState(under.below()).isFaceSturdy(level, under.below(), Direction.UP)) {
            return under;
        }
        return null;
    }
    @Override public void start() { mob.getNavigation().stop(); }
    @Override public void stop() {
        route = null;
        landing = null;
        retryAfter = mob.level().getGameTime() + 100;
        mob.getNavigation().stop();
    }
    @Override public void tick() {
        if (route == null) return;
        ticks++;
        Vec3 base = Vec3.atBottomCenterOf(route.base());
        Vec3 exit = Vec3.atBottomCenterOf(route.exit());
        if (landing != null) {
            Vec3 destination=Vec3.atBottomCenterOf(landing);
            if(mob.onGround() && mob.position().distanceToSqr(destination)<1) { stop();return; }
            // Walk across the wall and let normal gravity/fall damage handle descent.
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(destination.x,destination.y,destination.z,1.0);
            return;
        }
        if (mob.getY() >= exit.y && mob.position().distanceToSqr(exit) < .64) {
            landing=findLanding((ServerLevel)mob.level(),route);
            if(landing==null) { stop();return; }
            return;
        }
        // Distance in XZ to the ladder column, ignoring vertical position.
        double xzDistSq = mob.position().multiply(1, 0, 1).distanceToSqr(base.multiply(1, 0, 1));
        if (mob.onClimbable() && xzDistSq < 1.5) {
            crestTicks = 12;
            mob.getNavigation().stop();
            // Upward movement only while touching climbable blocks. Collision still governs movement.
            Vec3 into = Vec3.atLowerCornerOf(route.intoWall().getNormal()).scale(.12);
            mob.setDeltaMovement(into.x, .2, into.z);
            mob.getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, 1.0);
        } else if (crestTicks>0 && mob.getY()>=exit.y-.3 && mob.getY()<exit.y+.35) {
            crestTicks--;
            Vec3 toward=exit.subtract(mob.position()).multiply(1,0,1).normalize().scale(.16);
            mob.setDeltaMovement(toward.x,.12,toward.z);
            mob.getMoveControl().setWantedPosition(exit.x,exit.y+.1,exit.z,1.0);
        } else if (mob.getY() >= exit.y - .1) {
            mob.getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, 1.0);
        } else if (xzDistSq < 2.25) {
            // Close to the column but not yet touching. Press straight into
            // the ladder face so collision pushes the mob onto the rungs.
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(base.x, base.y, base.z, 1.0);
        } else if (ticks % 10 == 1) {
            // Aim for a solid stand-on block adjacent to the base, not the
            // air block itself. Ground navigators can actually reach it.
            BlockPos stand = standingPos(mob.level() instanceof ServerLevel sl ? sl : null, route);
            if (stand == null) stand = route.base();
            mob.getNavigation().moveTo(stand.getX() + .5, stand.getY(),
                    stand.getZ() + .5, RaidConfig.RAIDER_ADVANCE_SPEED.get());
        }
    }
}
