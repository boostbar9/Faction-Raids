package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defensive ring around a war camp: four wall sides plus a corner guard tower
 * at each corner of the camp's own territory.
 *
 * <p>The side facing the War Gate keeps a wide, permanently open main gate so
 * the graded road, reinforcements and the camp's own builders keep moving.
 * Everything here is pure layout — {@link CampDevelopment} feeds the resulting
 * cells to the ordinary native Workers pipeline, which records restoration and
 * enforces the finite material budget.
 *
 * <p>Columns are skipped rather than failing the whole job when the ground is
 * too far from the camp floor, already occupied or outside the loaded/claimed
 * area. A wall with a gap at a cliff is far better than a camp that can never
 * finish a single wall segment.
 */
public final class CampPerimeter {

    /** Distance from the camp center to the wall line. */
    static final int RADIUS = 12;
    /** Wall height above its own ground column. */
    static final int WALL_HEIGHT = 3;
    /** Half-width of the main gate opening (so the gap is 2*N+1 wide). */
    static final int GATE_HALF_WIDTH = 2;
    /** Clear height under the main gate's lintel. */
    static final int GATE_CLEARANCE = 4;
    /** Guard tower height above its own ground column. */
    static final int TOWER_HEIGHT = 6;
    /** Vertical tolerance between a column's ground and the camp floor. */
    static final int MAX_STEP = 3;

    static final String WALL = "minecraft:cobblestone";
    static final String FRAME = "minecraft:stripped_spruce_log";
    static final String CROWN = "minecraft:cobblestone_wall";
    static final String TOWER = "minecraft:stone_bricks";

    /** First upgrade stage that belongs to the perimeter instead of an outbuilding. */
    static final int FIRST_STAGE = 3;
    /** One stage per wall side, then one per corner tower. */
    static final int STAGES = 8;
    static final int LAST_STAGE = FIRST_STAGE + STAGES - 1;

    private CampPerimeter() {}

    /** True when this upgrade stage is a perimeter wall side or corner tower. */
    static boolean perimeterStage(int stage) {
        return stage >= FIRST_STAGE && stage <= LAST_STAGE;
    }

    /**
     * Build the cells for one perimeter stage, or an empty map when nothing can
     * be placed there yet (unloaded chunks, foreign claim, blocked terrain).
     */
    static Map<Long, String> plan(ServerLevel level, RaidSavedData.RaidState raid, int stage) {
        if (!perimeterStage(stage) || raid.campPos == null) return Map.of();
        Direction gateSide = mainGateSide(raid);
        int index = stage - FIRST_STAGE;
        return index < 4
                ? wall(level, raid, side(gateSide, index), gateSide)
                : tower(level, raid, side(gateSide, index - 4));
    }

    /**
     * The side that carries the main gate. The War Gate (and therefore the
     * graded road into camp) always sits on this side, so the opening lines up
     * with the road instead of walling it off.
     */
    public static Direction mainGateSide(RaidSavedData.RaidState raid) {
        if (raid.warGate.contains("PerimeterGateFacing", net.minecraft.nbt.Tag.TAG_INT))
            return Direction.from2DDataValue(raid.warGate.getInt("PerimeterGateFacing"));
        if (raid.warGate.contains("Center", net.minecraft.nbt.Tag.TAG_LONG)) return WarGate.facing(raid);
        double x = -Math.cos(raid.approachAngle), z = -Math.sin(raid.approachAngle);
        return Math.abs(x) >= Math.abs(z)
                ? (x >= 0 ? Direction.EAST : Direction.WEST)
                : (z >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /** Stage order: gate side first, then clockwise around the camp. */
    static Direction side(Direction gateSide, int index) {
        Direction d = gateSide;
        for (int i = 0; i < index; i++) d = d.getClockWise();
        return d;
    }

    /** World position of the middle of the main gate opening, or null when unknown. */
    public static BlockPos mainGateCenter(RaidSavedData.RaidState raid) {
        if (raid.campPos == null) return null;
        // Prefer the gate recorded when the wall was commissioned: the War
        // Gate can be rebuilt elsewhere later, but the opening does not move.
        return raid.warGate.contains("PerimeterGate", net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(raid.warGate.getLong("PerimeterGate"))
                : raid.campPos.relative(mainGateSide(raid), RADIUS);
    }

    private static Map<Long, String> wall(ServerLevel level, RaidSavedData.RaidState raid,
                                          Direction facing, Direction gateSide) {
        Map<Long, String> plan = new LinkedHashMap<>();
        Direction along = facing.getClockWise();
        boolean gated = facing == gateSide;
        // The whole gateway threshold is levelled to the gate centre's ground
        // so troops, mounts and siege crews walk out over a flat, continuous
        // surface instead of stepping into a dip beside the road.
        Integer threshold = gated ? groundFor(level, raid, raid.campPos.relative(facing, RADIUS)) : null;
        for (int lateral = -RADIUS; lateral <= RADIUS; lateral++) {
            BlockPos column = raid.campPos.relative(facing, RADIUS).relative(along, lateral);
            Integer ground = groundFor(level, raid, column);
            if (ground == null) continue;
            boolean opening = gated && Math.abs(lateral) <= GATE_HALF_WIDTH;
            boolean gatePost = gated && Math.abs(lateral) == GATE_HALF_WIDTH + 1;
            if (opening) {
                // The gate stays open: only the lintel spans the gap, high
                // enough for mounted units and siege crews to ride through.
                if (threshold != null) pave(level, plan, raid, column, ground, threshold, along);
                add(level, plan, column, threshold == null ? ground : threshold, GATE_CLEARANCE, FRAME);
                continue;
            }
            int height = gatePost ? GATE_CLEARANCE : WALL_HEIGHT;
            for (int y = 0; y < height; y++) {
                add(level, plan, column, ground, y, gatePost ? FRAME : WALL);
            }
            // A crenellated crown reads as a wall rather than a fence line.
            add(level, plan, column, ground, height, gatePost ? FRAME : CROWN);
        }
        return plan;
    }

    /**
     * Fill a gateway column up to the threshold height, and carry that floor
     * one block either side of the wall line so the approach and the exit meet
     * the threshold without a step. Columns that already sit at or above the
     * threshold are left alone — native builders only place, never dig.
     */
    private static void pave(ServerLevel level, Map<Long, String> plan, RaidSavedData.RaidState raid,
                             BlockPos column, int ground, int threshold, Direction along) {
        Direction outward = along.getCounterClockWise();
        for (int depth = -1; depth <= 1; depth++) {
            BlockPos at = column.relative(outward, depth);
            Integer floor = depth == 0 ? ground : groundFor(level, raid, at);
            if (floor == null) continue;
            for (int y = floor; y < threshold; y++) {
                BlockPos pos = at.atY(y);
                if (buildable(level, pos)) plan.put(pos.asLong(), WALL);
            }
        }
    }

    /** True when {@code pos} is inside the perimeter wall ring of this camp. */
    static boolean inside(RaidSavedData.RaidState raid, net.minecraft.world.phys.Vec3 pos) {
        if (raid.campPos == null) return false;
        double dx = Math.abs(pos.x - (raid.campPos.getX() + 0.5));
        double dz = Math.abs(pos.z - (raid.campPos.getZ() + 0.5));
        return Math.max(dx, dz) <= RADIUS - 0.5 && Math.abs(pos.y - raid.campPos.getY()) <= TOWER_HEIGHT;
    }

    /** Aim point just outside the main gate: the first safe step out of camp. */
    public static net.minecraft.world.phys.Vec3 gateExit(RaidSavedData.RaidState raid) {
        BlockPos gate = mainGateCenter(raid);
        return gate == null ? null
                : net.minecraft.world.phys.Vec3.atBottomCenterOf(gate.relative(mainGateSide(raid), 3));
    }

    /**
     * Waypoint a unit standing inside the camp should head for next, or null
     * when it can go straight to its objective. Units inside the ring aim for
     * the gateway itself; units in the gateway aim for the step outside it, so
     * nobody tries to path through the wall and wedge against it.
     *
     * <p>Only applies when the objective is actually outside the ring: units
     * working, mustering or guarding inside the camp keep their own orders.
     */
    public static net.minecraft.world.phys.Vec3 routeOut(RaidSavedData.RaidState raid,
                                                         net.minecraft.world.phys.Vec3 from,
                                                         net.minecraft.world.phys.Vec3 objective) {
        if (!gateBuilt(raid) || inside(raid, objective)) return null;
        BlockPos gate = mainGateCenter(raid);
        net.minecraft.world.phys.Vec3 exit = gateExit(raid);
        if (gate == null || exit == null) return null;
        net.minecraft.world.phys.Vec3 mouth = net.minecraft.world.phys.Vec3.atBottomCenterOf(gate);
        // A unit standing in the gateway itself is not strictly inside the
        // ring, but it still has to be walked clear of the opening before it
        // picks its own route, or it turns straight back into a gate post.
        if (from.distanceToSqr(mouth) <= 4.0) return exit;
        return inside(raid, from) ? mouth : null;
    }

    /** True once a perimeter gate has actually been commissioned for this camp. */
    public static boolean gateBuilt(RaidSavedData.RaidState raid) {
        return raid.campPos != null
                && raid.warGate.contains("PerimeterGate", net.minecraft.nbt.Tag.TAG_LONG);
    }

    private static Map<Long, String> tower(ServerLevel level, RaidSavedData.RaidState raid, Direction facing) {
        Map<Long, String> plan = new LinkedHashMap<>();
        BlockPos corner = raid.campPos.relative(facing, RADIUS).relative(facing.getClockWise(), RADIUS);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = corner.offset(dx, 0, dz);
                Integer ground = groundFor(level, raid, column);
                if (ground == null) continue;
                boolean edge = dx != 0 || dz != 0;
                if (!edge) {
                    // Hollow shaft with a solid lookout floor near the top.
                    add(level, plan, column, ground, TOWER_HEIGHT - 1, "minecraft:spruce_planks");
                    continue;
                }
                for (int y = 0; y < TOWER_HEIGHT; y++) {
                    add(level, plan, column, ground, y, y == TOWER_HEIGHT - 1 ? FRAME : TOWER);
                }
                // Battlements: alternate merlons so archers keep firing lines.
                if (dx == 0 || dz == 0) add(level, plan, column, ground, TOWER_HEIGHT, CROWN);
            }
        }
        return plan;
    }

    private static void add(ServerLevel level, Map<Long, String> plan, BlockPos column,
                            int ground, int offset, String block) {
        BlockPos pos = column.atY(ground + offset);
        if (pos.getY() + 1 >= level.getMaxBuildHeight()) return;
        if (!buildable(level, pos)) return;
        plan.put(pos.asLong(), block);
    }

    /**
     * Ground level of a column, or null when the column cannot be built on.
     * Mirrors the checks {@link NativeCampConstruction#start} performs, so a
     * planned cell never fails the whole job.
     */
    private static Integer groundFor(ServerLevel level, RaidSavedData.RaidState raid, BlockPos column) {
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return null;
        if (!claimed(level, raid, column)) return null;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (Math.abs(ground - raid.campPos.getY()) > MAX_STEP) return null;
        BlockPos floor = column.atY(ground - 1);
        if (floor.getY() <= level.getMinBuildHeight()) return null;
        if (!level.getFluidState(floor).isEmpty()) return null;
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return null;
        return ground;
    }

    private static boolean buildable(ServerLevel level, BlockPos pos) {
        return CampVegetation.replaceable(level.getBlockState(pos))
                && level.getFluidState(pos).isEmpty()
                && level.getBlockEntity(pos) == null
                && !occupied(level, pos);
    }

    /**
     * True when something a builder cannot walk through is standing in the
     * cell. Siege engines are the usual culprit: a catapult parked on the wall
     * line leaves a cell that can never be filled, and a job that never
     * completes blocks every later camp project.
     */
    private static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(pos),
                entity -> !entity.isRemoved() && !entity.isSpectator() && entity.canBeCollidedWith()).isEmpty();
    }

    /**
     * True when a position sits on or inside the camp's wall line, with a
     * little margin. Siege equipment and other large props are kept out of
     * this footprint so the camp's builders always have room to work.
     */
    public static boolean blocksCamp(RaidSavedData.RaidState raid, net.minecraft.world.phys.Vec3 pos) {
        if (raid.campPos == null) return false;
        double dx = Math.abs(pos.x - (raid.campPos.getX() + 0.5));
        double dz = Math.abs(pos.z - (raid.campPos.getZ() + 0.5));
        return Math.max(dx, dz) <= RADIUS + 2;
    }

    private static boolean claimed(ServerLevel level, RaidSavedData.RaidState raid, BlockPos pos) {
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(raid.teamKey);
        if (anchor == null || raid.campClaimId == null) return false;
        var claim = com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.getClaimAt(level, pos).orElse(null);
        return claim != null && claim.claimId().equals(raid.campClaimId)
                && !com.devfarinsky.siegeoverhaul.compat.ClaimBridge.isForeignClaim(level, pos,
                        anchor.withIdentity(claim.ownerFactionStringId(), anchor.teamDisplay()));
    }
}
