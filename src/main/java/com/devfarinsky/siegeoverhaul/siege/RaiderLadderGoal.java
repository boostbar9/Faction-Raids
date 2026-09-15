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
    private int ticks, crestTicks, grip, stalledTicks;
    private double highest = Double.NEGATIVE_INFINITY;
    private BlockPos landing;
    /** Squared XZ range at which a raider stops pathing and walks into the rungs. */
    static final double PRESS_RANGE_SQ = 2.25;
    /** Ticks without gaining height before a raider gives up on a ladder. */
    static final int STALL_TICKS = 160;
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
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive() || mob.isPassenger() || assigned(mob)
                    || com.devfarinsky.siegeoverhaul.naval.BridgeBuilder.assigned(mob)) continue;
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
            goal.route = best; goal.deadline = level.getGameTime() + 400; goal.ticks = 0; goal.crestTicks=0; goal.landing=null; goal.grip=0;
            goal.stalledTicks=0; goal.highest=Double.NEGATIVE_INFINITY;
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
        grip = 0;
        stalledTicks = 0;
        highest = Double.NEGATIVE_INFINITY;
        retryAfter = mob.level().getGameTime() + 100;
        mob.getNavigation().stop();
    }

    /**
     * Square up with the wall and take the mob's own move control out of the
     * climb. Left running, it keeps feeding sideways walking input while the
     * body yaw swings, which is exactly what slides a climber off the rungs.
     */
    private void holdStill() {
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 1.0);
        float yaw = route.intoWall().toYRot();
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
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
        // Give up on a ladder nobody is making headway on, so the raider can be
        // reassigned instead of grinding against the same wall for the whole
        // siege. Only counted once it has actually reached the ladder, so a long
        // walk in never looks like a stall.
        if (!mob.onClimbable() && !LadderClimb.onColumn(mob.position(), base, PRESS_RANGE_SQ)) {
            stalledTicks = 0;
        } else if (mob.getY() > highest + .1) {
            highest = mob.getY();
            stalledTicks = 0;
        } else if (++stalledTicks > STALL_TICKS) {
            stop();
            return;
        }

        boolean gripping = mob.onClimbable() && LadderClimb.onColumn(mob.position(), base, LadderClimb.GRIP_RANGE_SQ);
        if (gripping) {
            grip = LadderClimb.GRIP_TICKS;
            crestTicks = 12;
            holdStill();
            // One velocity, set outright: press into the rungs, slide back onto
            // the column, and rise. Extra lift near the top carries the climber
            // over the wall lip, which the rungs alone cannot reach.
            mob.setDeltaMovement(LadderClimb.cresting(mob.getY(), exit.y)
                    ? LadderClimb.crest(mob.position(), base, route.intoWall())
                    : LadderClimb.climb(mob.position(), base, route.intoWall()));
            return;
        }
        if (grip > 0 && !LadderClimb.cresting(mob.getY(), exit.y) && mob.getY() > base.y + .5
                && LadderClimb.onColumn(mob.position(), base, LadderClimb.REGRIP_RANGE_SQ)) {
            // Slipped off partway up: recover the column instead of dropping.
            grip--;
            holdStill();
            mob.setDeltaMovement(LadderClimb.regrip(mob.position(), base, route.intoWall(), mob.getDeltaMovement()));
            return;
        }
        if (crestTicks > 0 && mob.getY() >= exit.y - .3 && mob.getY() < exit.y + .35) {
            crestTicks--;
            holdStill();
            mob.setDeltaMovement(LadderClimb.crest(mob.position(), base, route.intoWall()));
            return;
        }
        if (mob.getY() >= exit.y - .1) {
            mob.getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, 1.0);
            return;
        }
        if (LadderClimb.onColumn(mob.position(), base, PRESS_RANGE_SQ)) {
            // Close enough to mount. Walk straight at the ladder face, lined up
            // with the column, and hop if the wall is underfoot; a mob that only
            // brushes the corner of the block never gets a grip and just stands there.
            mob.getNavigation().stop();
            Vec3 press = LadderClimb.approach(base, route.intoWall(), -.3);
            mob.getMoveControl().setWantedPosition(press.x, base.y, press.z, 1.0);
            Vec3 along = LadderClimb.along(route.intoWall());
            double correction = net.minecraft.util.Mth.clamp(
                    -LadderClimb.drift(mob.position(), base, route.intoWall()), -.08, .08);
            mob.setDeltaMovement(mob.getDeltaMovement().add(along.x * correction, 0, along.z * correction));
            if (mob.horizontalCollision && mob.onGround()) mob.getJumpControl().jump();
            return;
        }
        if (ticks % 10 == 1) {
            // Aim for a solid stand-on block adjacent to the base, not the
            // air block itself. Ground navigators can actually reach it.
            BlockPos stand = standingPos(mob.level() instanceof ServerLevel sl ? sl : null, route);
            if (stand == null) stand = route.base();
            mob.getNavigation().moveTo(stand.getX() + .5, stand.getY(),
                    stand.getZ() + .5, RaidConfig.RAIDER_ADVANCE_SPEED.get());
        }
    }
}
