package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Movement maths for a raider on a siege ladder.
 *
 * <p>A ladder is a thin plate on one face of its block, so a climber only keeps
 * its grip while it stays pressed into that face and lined up with the column.
 * Steering a climbing mob with an ordinary "walk to this position" order does
 * neither: the mob's own move control keeps feeding sideways input while its
 * body yaw swings around, so it slides along the wall, loses contact with the
 * rungs and drops. That is the "climbs sideways and falls off" report.
 *
 * <p>This keeps three things separate and testable without a live world: the
 * push into the ladder face, the sideways correction back onto the column, and
 * the extra lift needed to get over the wall lip at the top of the column.
 */
public final class LadderClimb {

    /** Upward speed while gripping the rungs. Vanilla climbing is 0.2. */
    public static final double RISE = 0.2;
    /** Steady press into the ladder face, so contact is never lost. */
    public static final double PRESS = 0.12;
    /** Stronger press and lift used to cross the lip at the top of the column. */
    public static final double CREST_PRESS = 0.16;
    public static final double CREST_RISE = 0.3;
    /** Largest sideways correction per tick; climbing motion is clamped to 0.15. */
    public static final double LATERAL = 0.1;
    /** Squared XZ distance to the column that still counts as being on the ladder. */
    public static final double GRIP_RANGE_SQ = 1.5;
    /** Squared XZ range in which a climber that slipped may still recover. */
    public static final double REGRIP_RANGE_SQ = 2.25;
    /** Ticks a slipped climber keeps trying to regain the rungs before giving up. */
    public static final int GRIP_TICKS = 10;
    /** Height above the top rung at which the climber starts crossing the lip. */
    public static final double CREST_BAND = 1.2;

    private LadderClimb() {}

    /** Unit vector pointing from the ladder into the wall behind it. */
    public static Vec3 into(Direction intoWall) {
        return new Vec3(intoWall.getStepX(), 0, intoWall.getStepZ());
    }

    /** Unit vector along the wall face, at right angles to the climb direction. */
    public static Vec3 along(Direction intoWall) {
        Vec3 into = into(intoWall);
        return new Vec3(-into.z, 0, into.x);
    }

    /**
     * Signed sideways offset from the ladder column: positive means the climber
     * has drifted toward {@link #along(Direction)}, negative the other way.
     */
    public static double drift(Vec3 position, Vec3 column, Direction intoWall) {
        return position.subtract(column).multiply(1, 0, 1).dot(along(intoWall));
    }

    /** Signed depth into the wall relative to the column centre. */
    public static double depth(Vec3 position, Vec3 column, Direction intoWall) {
        return position.subtract(column).multiply(1, 0, 1).dot(into(intoWall));
    }

    /**
     * Velocity for a climbing tick: press into the rungs, slide back onto the
     * column and rise. Replaces the mob's whole velocity, so nothing else can
     * add the sideways motion that walks a climber off the ladder.
     */
    public static Vec3 climb(Vec3 position, Vec3 column, Direction intoWall, double rise, double press) {
        Vec3 into = into(intoWall);
        Vec3 along = along(intoWall);
        double correction = Mth.clamp(-drift(position, column, intoWall), -LATERAL, LATERAL);
        return new Vec3(into.x * press + along.x * correction, rise, into.z * press + along.z * correction);
    }

    /** Ordinary climbing velocity. */
    public static Vec3 climb(Vec3 position, Vec3 column, Direction intoWall) {
        return climb(position, column, intoWall, RISE, PRESS);
    }

    /** Climbing velocity for the last stretch over the wall lip. */
    public static Vec3 crest(Vec3 position, Vec3 column, Direction intoWall) {
        return climb(position, column, intoWall, CREST_RISE, CREST_PRESS);
    }

    /**
     * Velocity for a climber that lost contact with the rungs partway up:
     * recover the column without pushing further upward, so it settles back
     * onto the ladder instead of falling the rest of the way down.
     */
    public static Vec3 regrip(Vec3 position, Vec3 column, Direction intoWall, Vec3 current) {
        Vec3 recover = climb(position, column, intoWall, 0.0, PRESS);
        return new Vec3(recover.x, Math.max(current.y, -0.08), recover.z);
    }

    /** True while the climber is over the ladder column and still below its exit. */
    public static boolean onColumn(Vec3 position, Vec3 column, double rangeSq) {
        return position.multiply(1, 0, 1).distanceToSqr(column.multiply(1, 0, 1)) < rangeSq;
    }

    /** True once the climber is high enough to start crossing the wall lip. */
    public static boolean cresting(double y, double exitY) {
        return y >= exitY - CREST_BAND;
    }

    /**
     * Where a raider should stand before stepping onto the ladder: directly in
     * front of the column, lined up with it, so it walks into the rungs
     * head-on instead of clipping a corner and scraping past.
     */
    public static Vec3 approach(Vec3 column, Direction intoWall, double distance) {
        return column.subtract(into(intoWall).scale(distance));
    }
}
