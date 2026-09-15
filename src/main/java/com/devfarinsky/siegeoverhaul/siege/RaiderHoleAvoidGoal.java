package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Prevents raiders from walking off cliffs and, once they have slipped into a
 * ravine or cave, forces them to escape back to the surface before they can
 * keep pathing toward the objective.
 *
 * <p>Two failure modes we saw in play:
 * <ol>
 *   <li>A raider running toward the core would path straight through a
 *       freshly-dug 4-block pit at the edge of the base and get stuck.</li>
 *   <li>Larger issue: a raider that stepped into a ravine 30+ blocks back
 *       would happily continue pathing to the core through underground caves,
 *       dragging most of the army underground.</li>
 * </ol>
 *
 * <p>This goal runs at high priority alongside movement; every tick it does
 * two cheap checks:
 * <ul>
 *   <li>Look one tile ahead along the mob's heading. If that tile is empty
 *       over a lethal drop with no water below, cancel the current path and
 *       step back onto solid ground.</li>
 *   <li>Compare the mob's Y to the world's motion-blocking surface Y at its
 *       XZ. If the mob is more than {@link #CAVE_DEPTH_THRESHOLD} blocks
 *       below the surface, treat it as trapped underground and repath to the
 *       nearest surface tile within a small radius so it climbs back up
 *       before resuming the march to the objective.</li>
 * </ul>
 *
 * <p>The goal never claims any AI flag, so other goals (attack, ladder, wall
 * strike, parkour) run unaffected.
 */
public class RaiderHoleAvoidGoal extends Goal {

    /** Look-ahead distances in blocks along the mob's heading. */
    private static final double[] LOOK_AHEAD = {1.0, 1.8, 2.6};
    /** A drop deeper than this many blocks is treated as lethal. */
    private static final int LETHAL_DROP = 3;
    /** Cool-down between hole backoffs so the goal cannot spam pathing. */
    private static final int BACKOFF_COOLDOWN_TICKS = 40;
    /** How many blocks below the surface counts as "trapped underground". */
    private static final int CAVE_DEPTH_THRESHOLD = 6;
    /** Cool-down between cave-escape repaths (heavier, so slower cadence). */
    private static final int CAVE_ESCAPE_COOLDOWN_TICKS = 100;
    /**
     * How long a detected edge keeps the march loop from handing out a fresh
     * order toward the objective. Short enough that a raider resumes the
     * assault as soon as it has stepped back onto safe ground.
     */
    private static final int EDGE_HOLD_TICKS = 30;
    /** Persistent-data key the raid march loop reads to honour an active edge hold. */
    public static final String EDGE_HOLD = "SiegeEdgeHoldUntil";

    private final PathfinderMob mob;
    private int holeCooldown;
    private int caveCooldown;

    public RaiderHoleAvoidGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override public boolean canUse() { return true; }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (com.devfarinsky.siegeoverhaul.naval.BridgeBuilder.assigned(mob)) return;
        Level level = mob.level();

        // ---------- Cave-escape check (runs even when standing still) --------
        if (caveCooldown > 0) caveCooldown--;
        else if (isTrappedUnderground(level)) {
            escapeToSurface(level);
            caveCooldown = CAVE_ESCAPE_COOLDOWN_TICKS;
            return;
        }

        // ---------- Cliff / hole look-ahead ---------------------------------
        if (holeCooldown > 0) holeCooldown--;

        if (!mob.onGround()) return;
        Vec3 heading = heading();
        if (heading == null) return;
        if (!lethalDropAhead(level, heading)) return;

        // Hold this raider at the edge. Stopping navigation alone is not
        // enough: the march loop re-issues an objective order as soon as the
        // path finishes, native move control keeps pushing, and the carried
        // momentum walks the raider over the lip anyway.
        var nav = mob.getNavigation();
        nav.stop();
        Vec3 velocity = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0, velocity.y, 0.0);
        double backX = mob.getX() - heading.x;
        double backZ = mob.getZ() - heading.z;
        mob.getMoveControl().setWantedPosition(backX, mob.getY(), backZ, 1.0);
        mob.getPersistentData().putLong(EDGE_HOLD, level.getGameTime() + EDGE_HOLD_TICKS);
        if (holeCooldown > 0) return;
        nav.moveTo(backX, mob.getY(), backZ, 1.0);
        holeCooldown = BACKOFF_COOLDOWN_TICKS;
    }

    /**
     * Is this raider currently being held back from a lethal edge? The raid
     * march loop uses this to avoid overwriting the backoff with a fresh
     * order straight toward the objective.
     */
    public static boolean holdingEdge(Mob mob) {
        if (mob == null || mob.level() == null) return false;
        return mob.getPersistentData().getLong(EDGE_HOLD) > mob.level().getGameTime();
    }

    /**
     * Horizontal unit heading: the raider's own movement when it is moving,
     * otherwise the direction of the next node on its active path. Checking
     * the path matters because a raider that just received an order is still
     * stationary on the tick it starts walking toward a ledge.
     */
    private Vec3 heading() {
        Vec3 velocity = mob.getDeltaMovement();
        double horizontalSpeed = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeed >= 0.001) {
            double invMag = 1.0 / Math.sqrt(horizontalSpeed);
            return new Vec3(velocity.x * invMag, 0, velocity.z * invMag);
        }
        var nav = mob.getNavigation();
        var path = nav == null ? null : nav.getPath();
        if (path == null || path.isDone()) return null;
        Vec3 next = path.getNextEntityPos(mob);
        Vec3 toNext = new Vec3(next.x - mob.getX(), 0, next.z - mob.getZ());
        if (toNext.lengthSqr() < 1.0e-4) return null;
        return toNext.normalize();
    }

    /** Is there a killing drop within the look-ahead window along {@code heading}? */
    private boolean lethalDropAhead(Level level, Vec3 heading) {
        for (double distance : LOOK_AHEAD) {
            BlockPos aheadFoot = BlockPos.containing(
                    mob.getX() + heading.x * distance, mob.getY(), mob.getZ() + heading.z * distance);
            BlockPos aheadBelow = aheadFoot.below();
            if (level.getBlockState(aheadBelow).isFaceSturdy(level, aheadBelow, net.minecraft.core.Direction.UP)) {
                continue;
            }
            int drop = 0;
            BlockPos probe = aheadBelow;
            boolean cushioned = false;
            while (drop <= LETHAL_DROP + 2) {
                BlockState state = level.getBlockState(probe);
                // Match the fluid checks used elsewhere in the mod: block id and
                // fluid state, so waterlogged and flowing columns both count.
                if (state.is(Blocks.LAVA) || state.getFluidState().is(FluidTags.LAVA)) {
                    return true; // Lava is never a safe landing.
                }
                if (state.is(Blocks.WATER) || !state.getFluidState().isEmpty()) {
                    cushioned = true; // Water below breaks the fall.
                    break;
                }
                if (state.isFaceSturdy(level, probe, net.minecraft.core.Direction.UP)) break;
                probe = probe.below();
                drop++;
            }
            if (!cushioned && drop > LETHAL_DROP) return true;
        }
        return false;
    }

    /**
     * The mob is underground when the top of the terrain column at its XZ is
     * meaningfully above its head. We use the MOTION_BLOCKING heightmap which
     * ignores leaves and other pass-through blocks so a mob under a forest
     * canopy is not falsely flagged as caved-in.
     */
    private boolean isTrappedUnderground(Level level) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                mob.getBlockX(), mob.getBlockZ());
        return mob.getBlockY() + CAVE_DEPTH_THRESHOLD < surfaceY;
    }

    /**
     * Ask the navigation system to walk to a surface tile within a small XZ
     * ring around the mob. We pick the tile whose surface Y is closest to the
     * mob so it prefers walking out a ravine rather than tunneling straight up.
     */
    private void escapeToSurface(Level level) {
        int mx = mob.getBlockX();
        int mz = mob.getBlockZ();
        int mobY = mob.getBlockY();
        int bestX = mx, bestZ = mz, bestY = Integer.MAX_VALUE;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) continue;
                int sx = mx + dx, sz = mz + dz;
                int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                if (sy <= mobY) continue; // Only consider tiles above mob.
                int score = (sy - mobY) * 2 + Math.abs(dx) + Math.abs(dz);
                if (score < bestScore) {
                    bestScore = score;
                    bestX = sx; bestZ = sz; bestY = sy;
                }
            }
        }
        if (bestY == Integer.MAX_VALUE) return; // Nothing higher: give up.
        var nav = mob.getNavigation();
        nav.stop();
        nav.moveTo(bestX + 0.5, bestY, bestZ + 0.5, 1.15);
    }
}
