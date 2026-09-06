package com.devfarinsky.factionraids.scout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * v2.26.0 scout behavior: walk to a lookout position near the defender's
 * anchor, observe for a configurable window, then flee back toward the
 * spawn direction and despawn. Never engages the anchor. If hurt or
 * pursued, aborts to the flee phase immediately.
 *
 * <p>Kept intentionally simple: a state machine with three phases
 * (APPROACH -> OBSERVE -> FLEE) and no combat AI. If a scout is hit, it
 * doesn't fight back — it runs. This makes the intel-letter loot
 * mechanic feel like a reward for a hunt, not a fight.
 */
public final class RaiderScoutGoal extends Goal {

    private enum Phase { APPROACH, OBSERVE, FLEE }

    private final PathfinderMob owner;
    /** Lookout position: a point some distance from the anchor but not on top of it. */
    private final BlockPos lookout;
    /** Spawn position — where to flee back toward before despawning. */
    private final BlockPos spawnPos;
    private final int observeTicks;
    private final double speed;
    private Phase phase = Phase.APPROACH;
    private int observeTicksRemaining;
    private int stuckCheckCooldown;
    private double lastDistSq = Double.MAX_VALUE;
    /**
     * If a hostile is within this radius during OBSERVE, the scout flees.
     * Undetected observation is the ideal outcome for the raider side,
     * so any close defender should abort the observation.
     */
    private static final double DETECTION_ABORT_RADIUS_SQ = 12.0 * 12.0;

    public RaiderScoutGoal(PathfinderMob owner, BlockPos lookout, BlockPos spawnPos,
                           int observeTicks, double speed) {
        this.owner = owner;
        this.lookout = lookout;
        this.spawnPos = spawnPos;
        this.observeTicks = observeTicks;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return owner.isAlive(); }

    @Override
    public boolean canContinueToUse() { return owner.isAlive() && phase != null; }

    @Override
    public void start() {
        owner.getNavigation().moveTo(lookout.getX() + 0.5, lookout.getY(), lookout.getZ() + 0.5, speed);
    }

    @Override
    public void tick() {
        switch (phase) {
            case APPROACH -> tickApproach();
            case OBSERVE -> tickObserve();
            case FLEE -> tickFlee();
        }
    }

    private void tickApproach() {
        double distSq = owner.blockPosition().distSqr(lookout);
        // Arrived — start observing. Threshold is generous because
        // pathfinding rarely lands the mob exactly on the target block.
        if (distSq < 4.0) {
            phase = Phase.OBSERVE;
            observeTicksRemaining = observeTicks;
            owner.getNavigation().stop();
            return;
        }
        // Stuck detection: if the scout hasn't gotten meaningfully closer
        // in the last 3 seconds, give up on approach and flee. Prevents
        // scouts marooned by terrain from standing there forever.
        stuckCheckCooldown++;
        if (stuckCheckCooldown >= 60) {
            stuckCheckCooldown = 0;
            if (distSq >= lastDistSq * 0.95) {
                phase = Phase.FLEE;
                startFlee();
                return;
            }
            lastDistSq = distSq;
        }
        // Refresh path periodically because the world may have changed.
        if (owner.getNavigation().isDone()) {
            owner.getNavigation().moveTo(lookout.getX() + 0.5, lookout.getY(),
                    lookout.getZ() + 0.5, speed);
        }
    }

    private void tickObserve() {
        // Look around during observation for flavor. Cheap head-turning.
        if (owner.tickCount % 20 == 0) {
            double bearing = (owner.tickCount / 20.0) * (Math.PI / 4.0);
            Vec3 gaze = owner.position().add(Math.cos(bearing) * 4.0, 0, Math.sin(bearing) * 4.0);
            owner.getLookControl().setLookAt(gaze.x, owner.getEyeY(), gaze.z);
        }
        // Abort if any player is close enough to have visibly detected us.
        if (owner.level() instanceof ServerLevel level) {
            for (Player p : level.getEntitiesOfClass(Player.class,
                    owner.getBoundingBox().inflate(Math.sqrt(DETECTION_ABORT_RADIUS_SQ)))) {
                if (!p.isSpectator() && !p.isCreative()) {
                    phase = Phase.FLEE;
                    startFlee();
                    return;
                }
            }
        }
        observeTicksRemaining--;
        if (observeTicksRemaining <= 0) {
            phase = Phase.FLEE;
            startFlee();
        }
    }

    private void startFlee() {
        owner.getNavigation().moveTo(spawnPos.getX() + 0.5, spawnPos.getY(),
                spawnPos.getZ() + 0.5, speed * 1.2);
    }

    private void tickFlee() {
        double distSq = owner.blockPosition().distSqr(spawnPos);
        // Close enough to spawn — despawn silently. Discard rather than
        // remove because raid bookkeeping shouldn't see this mob at all.
        if (distSq < 16.0) {
            owner.discard();
            return;
        }
        if (owner.getNavigation().isDone()) {
            owner.getNavigation().moveTo(spawnPos.getX() + 0.5, spawnPos.getY(),
                    spawnPos.getZ() + 0.5, speed * 1.2);
        }
    }

    /**
     * Called externally (from the LivingHurt handler) to force the scout
     * into flee mode. Public so the event handler can drive it without
     * peeking at internal state.
     */
    public void triggerFlee() {
        if (phase != Phase.FLEE) {
            phase = Phase.FLEE;
            startFlee();
        }
    }

    /** Utility for finding a nearby raised lookout position at world-surface height. */
    public static BlockPos findLookoutNear(Level level, BlockPos center, int searchRadius) {
        BlockPos.MutableBlockPos best = new BlockPos.MutableBlockPos(center.getX(), center.getY(), center.getZ());
        int bestY = Integer.MIN_VALUE;
        // Sample 12 points in a ring around the target and pick the highest surface.
        // A raised point makes the scout more visible to defenders (fair play)
        // and looks better narratively than crouching in the woods.
        for (int i = 0; i < 12; i++) {
            double angle = i * (Math.PI / 6.0);
            int dx = (int) Math.round(Math.cos(angle) * searchRadius);
            int dz = (int) Math.round(Math.sin(angle) * searchRadius);
            BlockPos sample = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(center.getX() + dx, 0, center.getZ() + dz));
            if (sample.getY() > bestY) {
                bestY = sample.getY();
                best.set(sample);
            }
        }
        return best.immutable();
    }
}
