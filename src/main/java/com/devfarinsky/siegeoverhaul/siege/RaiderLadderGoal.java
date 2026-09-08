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
    private int ticks;
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
            if (goal == null) { goal = new RaiderLadderGoal(mob); mob.goalSelector.addGoal(0, goal); }
            users.merge(best, 1, Integer::sum);
            goal.route = best; goal.deadline = level.getGameTime() + 400; goal.ticks = 0;
            RecruitsFormationBridge.release(mob);
        }
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
    @Override public void start() { mob.getNavigation().stop(); }
    @Override public void stop() {
        route = null;
        retryAfter = mob.level().getGameTime() + 100;
        mob.getNavigation().stop();
    }
    @Override public void tick() {
        if (route == null) return;
        ticks++;
        Vec3 base = Vec3.atBottomCenterOf(route.base());
        Vec3 exit = Vec3.atBottomCenterOf(route.exit());
        if (mob.getY() >= exit.y && mob.position().distanceToSqr(exit) < .16) { stop(); return; }
        if (mob.onClimbable() && mob.blockPosition().getX() == route.base().getX()
                && mob.blockPosition().getZ() == route.base().getZ()) {
            mob.getNavigation().stop();
            // Upward movement only while touching climbable blocks. Collision still governs movement.
            Vec3 into = Vec3.atLowerCornerOf(route.intoWall().getNormal()).scale(.12);
            mob.setDeltaMovement(into.x, .2, into.z);
            mob.getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, 1.0);
        } else if (mob.getY() >= exit.y - .1) {
            mob.getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, 1.0);
        } else if (mob.position().multiply(1,0,1).distanceToSqr(base.multiply(1,0,1)) < 1.0) {
            // Press onto the bottom rung; ground navigators do not plan vertical ladder routes.
            mob.getMoveControl().setWantedPosition(base.x, base.y, base.z, 1.0);
        } else if (ticks % 10 == 1) {
            mob.getNavigation().moveTo(base.x, base.y, base.z, RaidConfig.RAIDER_ADVANCE_SPEED.get());
        }
    }
}
