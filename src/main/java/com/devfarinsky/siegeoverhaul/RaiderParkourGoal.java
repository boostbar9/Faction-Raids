package com.devfarinsky.siegeoverhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * v2.25.0 raider parkour: when a raider stops making progress because a
 * short solid obstacle (1-3 blocks forward) blocks its path, boost its
 * vertical velocity so it clears the obstacle instead of pathfinding
 * around it or waiting for stuck escalation.
 *
 * <p>Fires only when:
 * <ul>
 *   <li>The raider is on the ground and not a passenger,</li>
 *   <li>The raider has a target (this is a combat-adjacent goal),</li>
 *   <li>The raider has stopped moving for at least 6 ticks
 *       (short enough to feel snappy, long enough to avoid interfering
 *       with a normal path),</li>
 *   <li>A solid block is within parkourMaxForward blocks in the target's
 *       direction at foot height.</li>
 * </ul>
 *
 * <p>Adapted from Enhanced AI (LGPL-3.0) by Insane96
 * (<a href="https://github.com/Insane96/EnhancedAI">github.com/Insane96/EnhancedAI</a>).
 * We keep only the leap-over-obstacle geometry; our activation predicate
 * uses our own raider tag check and our own no-progress window instead of
 * Enhanced AI's isStuck heuristic so this composes cleanly with our
 * existing stuck-tracker in RaidEvents.
 */
public final class RaiderParkourGoal extends Goal {

    private static final double FORWARD_STEP_EPSILON = 0.1;
    private static final double SLIGHT_DOWNWARD_NUDGE = -0.01;
    private static final int NO_MOVE_TICKS_BEFORE_LEAP = 6;

    private final Mob owner;
    private Vec3 lastPosition;
    private int noMoveTicks;
    private int leapBlocks;
    private boolean waitingForLanding;

    public RaiderParkourGoal(Mob owner) {
        this.owner = owner;
        this.setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!RaidConfig.PARKOUR_ENABLED.get()) return false;
        if (!owner.onGround() || owner.isPassenger()) {
            resetTracker();
            return false;
        }
        // v4.17.0: we no longer require a combat target. A raider marching
        // to the objective without a target is the MAIN case we want to
        // unstick — the old requireTarget gate made this goal fire almost
        // never in practice, since marching raiders don't hold targets.
        Vec3 aimAt = aimPoint();
        if (aimAt == null) {
            resetTracker();
            return false;
        }

        Vec3 pos = owner.position();
        if (lastPosition == null) {
            lastPosition = pos;
            noMoveTicks = 0;
            return false;
        }
        double distSq = pos.distanceToSqr(lastPosition);
        if (distSq > 0.0025) {
            // Moved more than 0.05 blocks this tick -> not stuck.
            lastPosition = pos;
            noMoveTicks = 0;
            return false;
        }
        noMoveTicks++;
        if (noMoveTicks < NO_MOVE_TICKS_BEFORE_LEAP) return false;

        // Compute direction toward aim point and probe forward.
        Vec3 toAim = new Vec3(
                aimAt.x - owner.getX(),
                0,
                aimAt.z - owner.getZ()
        );
        if (toAim.lengthSqr() < 1.0e-4) return false;
        Vec3 dir = toAim.normalize();
        leapBlocks = findLeapBlocks(dir);
        return leapBlocks > 0;
    }

    /**
     * Point we want to be closer to. Prefers a melee target when we have one,
     * otherwise uses the raider's active navigation path end (which the
     * RaidEvents loop sets to the objective).
     */
    private Vec3 aimPoint() {
        if (owner.getTarget() != null) return owner.getTarget().position();
        var nav = owner.getNavigation();
        if (nav != null && nav.getPath() != null && nav.getPath().getEndNode() != null) {
            var end = nav.getPath().getEndNode();
            return new Vec3(end.x + 0.5, end.y, end.z + 0.5);
        }
        return null;
    }

    private int findLeapBlocks(Vec3 dir) {
        int maxForward = RaidConfig.PARKOUR_MAX_FORWARD.get();
        for (int forward = 1; forward <= maxForward; forward++) {
            Vec3 probe = owner.position()
                    .add(dir.scale(forward + FORWARD_STEP_EPSILON))
                    .add(0.0, SLIGHT_DOWNWARD_NUDGE, 0.0);
            BlockPos candidate = BlockPos.containing(probe);
            BlockState here = owner.level().getBlockState(candidate);
            if (here.isSolid()) {
                // Landing constraint: block ABOVE the obstacle must be clear
                // (open air for our head), block TWO ABOVE also clear (raider
                // hitbox is ~1.95 tall), and there must be a landable surface
                // to arrive on. "Landable" = top of the obstacle OR one down
                // on the far side, both must be non-solid air above them.
                BlockPos above = candidate.above();
                BlockPos above2 = candidate.above(2);
                if (owner.level().getBlockState(above).isSolid()) continue;
                if (owner.level().getBlockState(above2).isSolid()) continue;
                // Verify at least one landing candidate: standing on the
                // obstacle top, or standing on the block behind it at the
                // same height (so a 1-tall fence with grass past it counts).
                if (isLandable(candidate.above())
                        || isLandable(BlockPos.containing(probe.add(dir.scale(1.0))).above())) {
                    return forward;
                }
            }
        }
        return 0;
    }

    /** Is this position safe to land in? Needs air here and non-lava under. */
    private boolean isLandable(BlockPos pos) {
        BlockState here = owner.level().getBlockState(pos);
        if (here.isSolid()) return false;
        BlockState below = owner.level().getBlockState(pos.below());
        if (!below.isSolid()) {
            // Water is fine to land in; lava is not.
            return !below.getFluidState().isEmpty()
                    && below.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return waitingForLanding && !owner.onGround();
    }

    @Override
    public void start() {
        // Vertical impulse scaled by how far forward we need to clear.
        // 1 block -> ~0.48 (a bit above vanilla jump, so fences clear),
        // 3 blocks -> ~0.61 (higher arc for a longer horizontal reach).
        double jumpPower = 0.48 + Math.min(leapBlocks - 1, 2) * 0.065;
        owner.setDeltaMovement(
                owner.getDeltaMovement().x,
                jumpPower,
                owner.getDeltaMovement().z
        );
        // Also nudge horizontally toward the aim point so we actually clear
        // the obstacle instead of jumping straight up. Falls back to the
        // active nav path end when there is no combat target.
        Vec3 aimAt = aimPoint();
        if (aimAt != null) {
            Vec3 toAim = new Vec3(
                    aimAt.x - owner.getX(),
                    0,
                    aimAt.z - owner.getZ()
            );
            if (toAim.lengthSqr() > 1.0e-4) {
                // Push scaled to leap distance: 1-block leap = 0.15, 3-block leap = 0.30.
                double pushMag = 0.15 + Math.min(leapBlocks - 1, 2) * 0.075;
                Vec3 push = toAim.normalize().scale(pushMag);
                owner.setDeltaMovement(owner.getDeltaMovement().add(push.x, 0, push.z));
            }
        }
        owner.hasImpulse = true;
        waitingForLanding = true;
    }

    @Override
    public void stop() {
        resetTracker();
        waitingForLanding = false;
        leapBlocks = 0;
    }

    private void resetTracker() {
        lastPosition = null;
        noMoveTicks = 0;
    }
}
