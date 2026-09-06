package com.devfarinsky.factionraids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
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
        // Only leap when we have a reason to close distance.
        if (owner.getTarget() == null) {
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

        // Compute direction toward target and probe forward.
        Vec3 toTarget = new Vec3(
                owner.getTarget().getX() - owner.getX(),
                0,
                owner.getTarget().getZ() - owner.getZ()
        );
        if (toTarget.lengthSqr() < 1.0e-4) return false;
        Vec3 dir = toTarget.normalize();
        leapBlocks = findLeapBlocks(dir);
        return leapBlocks > 0;
    }

    private int findLeapBlocks(Vec3 dir) {
        int maxForward = RaidConfig.PARKOUR_MAX_FORWARD.get();
        for (int forward = 1; forward <= maxForward; forward++) {
            Vec3 probe = owner.position()
                    .add(dir.scale(forward + FORWARD_STEP_EPSILON))
                    .add(0.0, SLIGHT_DOWNWARD_NUDGE, 0.0);
            BlockPos candidate = BlockPos.containing(probe);
            if (owner.level().getBlockState(candidate).isSolid()) {
                // Also require the block ABOVE the obstacle to be clear so
                // the raider has somewhere to land. Prevents leaping into
                // an overhang or wedge.
                if (!owner.level().getBlockState(candidate.above()).isSolid()) {
                    return forward;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean canContinueToUse() {
        return waitingForLanding && !owner.onGround();
    }

    @Override
    public void start() {
        // Vertical impulse scaled by how far forward we need to clear.
        // 1 block -> ~0.42 (vanilla jump), 3 blocks -> ~0.55 (a bit more air).
        double jumpPower = 0.42 + Math.min(leapBlocks - 1, 2) * 0.065;
        owner.setDeltaMovement(
                owner.getDeltaMovement().x,
                jumpPower,
                owner.getDeltaMovement().z
        );
        // Also nudge horizontally toward the target so we actually clear
        // the obstacle instead of jumping straight up.
        if (owner.getTarget() != null) {
            Vec3 toTarget = new Vec3(
                    owner.getTarget().getX() - owner.getX(),
                    0,
                    owner.getTarget().getZ() - owner.getZ()
            );
            if (toTarget.lengthSqr() > 1.0e-4) {
                Vec3 push = toTarget.normalize().scale(0.15);
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
