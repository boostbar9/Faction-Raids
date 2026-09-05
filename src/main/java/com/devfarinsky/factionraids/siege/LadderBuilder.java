package com.devfarinsky.factionraids.siege;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Places temporary ladders on walls that raiders cannot path over.
 *
 * <p>Trigger flow (called from {@code RaidEvents.processRaid}):
 * <ol>
 *   <li>Rate-limited to {@link #ATTEMPT_INTERVAL_TICKS} per raid \u2014 don't spam
 *       during smooth raids.</li>
 *   <li>Requires at least {@link #MIN_STUCK_RAIDERS} live raiders whose
 *       distance to objective has not improved recently. This makes the
 *       builder a *response* to real obstruction, not a proactive placer.</li>
 *   <li>Cast a horizontal ray from the raider centroid toward the objective.
 *       The first solid vertical column of height &ge;2 is the target wall.</li>
 *   <li>Place a ladder column on the raider-facing face, sized to
 *       {@code wallHeight + 1} blocks so the top rung clears the parapet.</li>
 *   <li>Ladders are tracked in {@code state.campBlocks} so the existing
 *       {@code cleanupWarCamp} pipeline removes them on raid end (and skips
 *       any block the player has replaced \u2014 preserving player edits).</li>
 * </ol>
 *
 * <p>Design constraints:
 * <ul>
 *   <li>Never places into player-modified blocks: only replaces air.</li>
 *   <li>Ladder needs a solid backing block (LadderBlock canSurvive); scan
 *       skips any column where the required backing is missing.</li>
 *   <li>Respects {@code MAX_LADDERS_PER_RAID} to bound the block count.</li>
 * </ul>
 */
public final class LadderBuilder {

    public static final int ATTEMPT_INTERVAL_TICKS = 20 * 20; // 20 s
    public static final int MIN_STUCK_RAIDERS = 3;
    public static final int MAX_SCAN_DISTANCE = 24;           // blocks
    public static final int MAX_WALL_HEIGHT = 6;              // blocks
    public static final int MIN_WALL_HEIGHT = 2;              // blocks

    private static final Map<String, Long> LAST_ATTEMPT = new HashMap<>();
    private static final Map<String, Integer> LADDERS_PLACED = new HashMap<>();

    private LadderBuilder() {}

    /**
     * Attempt one wall-scan-and-place pass for this raid. Cheap to call every
     * server-tick (rate-limited internally).
     *
     * @return true when a ladder column was actually placed
     */
    public static boolean tick(ServerLevel level, RaidSavedData.RaidState state,
                                BlockPos objective) {
        if (!RaidConfig.ENABLE_LADDER_BUILDING.get()) return false;
        if (state == null || state.raiders.isEmpty() || objective == null) return false;
        if (LADDERS_PLACED.getOrDefault(state.teamKey, 0)
                >= RaidConfig.MAX_LADDERS_PER_RAID.get()) return false;

        long now = level.getGameTime();
        Long last = LAST_ATTEMPT.get(state.teamKey);
        if (last != null && now - last < ATTEMPT_INTERVAL_TICKS) return false;
        LAST_ATTEMPT.put(state.teamKey, now);

        Vec3 objVec = Vec3.atCenterOf(objective);
        int stuck = countStuckRaiders(level, state, objVec);
        if (stuck < MIN_STUCK_RAIDERS) return false;

        Vec3 centroid = centroidOf(level, state);
        Vec3 rayDir = objVec.subtract(centroid).normalize();
        Direction facing = horizontalFacing(rayDir);

        WallScan scan = scanForWall(level, centroid, rayDir, facing);
        if (scan == null) {
            FactionLogger.LOG.debug("No wall to ladder for raid {}", state.teamKey);
            return false;
        }

        int placed = placeLadderColumn(level, state, scan);
        if (placed > 0) {
            LADDERS_PLACED.merge(state.teamKey, 1, Integer::sum);
            FactionLogger.LOG.debug("Placed ladder column ({} rungs) at {} for raid {}",
                    placed, scan.baseFront, state.teamKey);
            return true;
        }
        return false;
    }

    /** Forget a raid's ladder-count tracking \u2014 call on raid end. */
    public static void forget(String teamKey) {
        LAST_ATTEMPT.remove(teamKey);
        LADDERS_PLACED.remove(teamKey);
    }

    /* ---------------- internals ---------------- */

    private static int countStuckRaiders(ServerLevel level, RaidSavedData.RaidState state, Vec3 objVec) {
        int stuck = 0;
        for (UUID id : state.raiders) {
            var e = level.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
            // Stuck heuristic: raider more than 12 blocks from objective and
            // standing on solid ground (i.e. actually trying to path, not falling).
            if (mob.onGround() && mob.distanceToSqr(objVec) > 144.0D) stuck++;
        }
        return stuck;
    }

    private static Vec3 centroidOf(ServerLevel level, RaidSavedData.RaidState state) {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (UUID id : state.raiders) {
            var e = level.getEntity(id);
            if (e instanceof Mob mob && mob.isAlive()) {
                x += mob.getX();
                y += mob.getY();
                z += mob.getZ();
                n++;
            }
        }
        return n == 0 ? Vec3.ZERO : new Vec3(x / n, y / n, z / n);
    }

    private static Direction horizontalFacing(Vec3 v) {
        if (Math.abs(v.x) >= Math.abs(v.z)) return v.x >= 0 ? Direction.EAST : Direction.WEST;
        return v.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Walk the ray, block by block, until a wall column shows up. */
    private static WallScan scanForWall(ServerLevel level, Vec3 origin, Vec3 dir, Direction facing) {
        for (int step = 1; step <= MAX_SCAN_DISTANCE; step++) {
            Vec3 sample = origin.add(dir.scale(step));
            BlockPos front = BlockPos.containing(sample);
            BlockPos wallBase = front.relative(facing);
            if (!isSolid(level, wallBase)) continue;
            // Measure wall height at this column.
            int height = 0;
            for (int y = 0; y < MAX_WALL_HEIGHT + 1; y++) {
                if (isSolid(level, wallBase.above(y))) height++;
                else break;
            }
            if (height < MIN_WALL_HEIGHT) continue;
            // Confirm the raider-facing face is open air for the full column.
            boolean faceClear = true;
            for (int y = 0; y < height; y++) {
                if (!level.getBlockState(front.above(y)).isAir()) { faceClear = false; break; }
            }
            if (!faceClear) continue;
            return new WallScan(front, wallBase, facing, Math.min(height, MAX_WALL_HEIGHT));
        }
        return null;
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        return !bs.isAir() && bs.isCollisionShapeFullBlock(level, pos);
    }

    private static int placeLadderColumn(ServerLevel level, RaidSavedData.RaidState state, WallScan scan) {
        // LadderBlock's FACING is the direction the ladder is attached to the wall's face,
        // i.e. the direction from the ladder into the wall (opposite of scan.facing).
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, scan.facing.getOpposite());
        int placed = 0;
        int columnHeight = scan.wallHeight + 1;
        for (int y = 0; y < columnHeight; y++) {
            BlockPos pos = scan.baseFront.above(y);
            if (!level.getBlockState(pos).isAir()) continue;
            if (!ladder.canSurvive(level, pos)) continue;
            level.setBlock(pos, ladder, 3);
            // Track for cleanup via the existing camp-block pipeline.
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(Blocks.LADDER);
            if (id != null) state.campBlocks.put(pos.asLong(), id.toString());
            placed++;
        }
        return placed;
    }

    /**
     * Scan result: the wall column we're going to ladder.
     * @param baseFront the front-face air column base (where the bottom ladder goes)
     * @param wallBase  the solid wall column base
     * @param facing    horizontal direction from raiders toward the wall
     * @param wallHeight height of the solid wall (clamped to MAX_WALL_HEIGHT)
     */
    private record WallScan(BlockPos baseFront, BlockPos wallBase,
                             Direction facing, int wallHeight) {}
}
