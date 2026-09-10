package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
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

    /** Look-ahead in blocks along the mob's velocity heading. */
    private static final double LOOK_AHEAD = 1.4;
    /** A drop deeper than this many blocks is treated as lethal. */
    private static final int LETHAL_DROP = 3;
    /** Cool-down between hole backoffs so the goal cannot spam pathing. */
    private static final int BACKOFF_COOLDOWN_TICKS = 40;
    /** How many blocks below the surface counts as "trapped underground". */
    private static final int CAVE_DEPTH_THRESHOLD = 6;
    /** Cool-down between cave-escape repaths (heavier, so slower cadence). */
    private static final int CAVE_ESCAPE_COOLDOWN_TICKS = 100;

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
        Level level = mob.level();

        // ---------- Cave-escape check (runs even when standing still) --------
        if (caveCooldown > 0) caveCooldown--;
        else if (isTrappedUnderground(level)) {
            escapeToSurface(level);
            caveCooldown = CAVE_ESCAPE_COOLDOWN_TICKS;
            return;
        }

        // ---------- Cliff / hole look-ahead ---------------------------------
        if (holeCooldown > 0) { holeCooldown--; return; }

        Vec3 velocity = mob.getDeltaMovement();
        double horizontalSpeed = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeed < 0.001 || !mob.onGround()) return;

        double invMag = 1.0 / Math.sqrt(horizontalSpeed);
        double dx = velocity.x * invMag * LOOK_AHEAD;
        double dz = velocity.z * invMag * LOOK_AHEAD;
        BlockPos aheadFoot = BlockPos.containing(
                mob.getX() + dx, mob.getY(), mob.getZ() + dz);

        BlockPos aheadBelow = aheadFoot.below();
        if (level.getBlockState(aheadBelow).isFaceSturdy(level, aheadBelow, net.minecraft.core.Direction.UP)) {
            return;
        }
        int drop = 0;
        BlockPos probe = aheadFoot.below();
        while (drop <= LETHAL_DROP + 2) {
            BlockState state = level.getBlockState(probe);
            if (!state.getFluidState().isEmpty()) return; // Water below is safe.
            if (state.isFaceSturdy(level, probe, net.minecraft.core.Direction.UP)) break;
            probe = probe.below();
            drop++;
        }
        if (drop <= LETHAL_DROP) return;

        var nav = mob.getNavigation();
        nav.stop();
        double backX = mob.getX() - dx * 0.5;
        double backZ = mob.getZ() - dz * 0.5;
        nav.moveTo(backX, mob.getY(), backZ, 1.0);
        holeCooldown = BACKOFF_COOLDOWN_TICKS;
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
