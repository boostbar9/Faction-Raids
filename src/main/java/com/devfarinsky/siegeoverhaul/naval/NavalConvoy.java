package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side steering for raider boats.
 *
 * Vanilla {@code Boat} is normally piloted by a {@code LocalPlayer}'s WASD
 * input. On the server, with a raider as passenger, the boat is inert. We
 * make up the difference by nudging its velocity toward the beach point each
 * tick until either the boat is destroyed or the passenger dismounts onto
 * land.
 *
 * <h4>Sinking</h4>
 * The boat itself is a normal entity: it takes damage from players, defenders
 * and their recruits like any other. When it breaks, vanilla drops the
 * passenger into the water; the raider swims (or, in heavy armor, drowns
 * \u2014 vanilla behavior) toward the beach.
 *
 * <h4>State</h4>
 * We track {@code (boatUUID -> beach target)} per raid so cleanup is O(1) on
 * raid end. No NBT of our own \u2014 boats are vanilla entities and reload with
 * the world; their {@link net.minecraft.world.entity.player.Player#PERSISTED_NBT_TAG}
 * flag would be needed to re-attach steering after a restart, which we
 * deliberately don't bother with for a small feature. If the server restarts
 * mid-raid, boats drift with the current until the raid ends.
 */
public final class NavalConvoy {

    private NavalConvoy() {}

    /** Per-raid map of boat UUID to its assigned beach block. */
    private static final Map<String, Map<UUID, BlockPos>> TARGETS = new HashMap<>();

    /**
     * Per-raid map of boat UUID to number of consecutive ticks the boat has
     * been carrying live passengers but making no forward progress toward the
     * beach (either not-on-water or standing still). Used to force-dismount
     * passengers when a boat gets wedged on rocks, jammed against a Small
     * Ships mod hull, or otherwise can't complete its beach approach.
     */
    private static final Map<String, Map<UUID, Integer>> STUCK_TICKS = new HashMap<>();

    /**
     * Force-dismount raiders whose boat has been stuck-with-passengers for at
     * least this many server ticks (20 t = 1 s). 20 s of no progress is
     * generous enough to cover normal beach-approach maneuvering and short
     * combat pauses while still guaranteeing amphibious raids don't stall out
     * indefinitely when the boat wedges on shore geometry.
     */
    private static final int STUCK_TICK_LIMIT = 400;

    /**
     * Register a boat we just spawned with the convoy so it will be steered
     * toward {@code beach} on every subsequent {@link #tick} for this raid.
     */
    public static void enlist(String teamKey, Entity vessel, BlockPos beach) {
        TARGETS.computeIfAbsent(teamKey, k -> new HashMap<>()).put(vessel.getUUID(), beach);
    }

    /**
     * Steer every enlisted boat one tick toward its beach. Boats that have
     * beached, been destroyed, or lost their passenger are dropped from the
     * convoy map.
     */
    public static void tick(String teamKey, ServerLevel level) {
        Map<UUID, BlockPos> boats = TARGETS.get(teamKey);
        if (boats == null || boats.isEmpty()) return;
        Map<UUID, Integer> stuck = STUCK_TICKS.computeIfAbsent(teamKey, k -> new HashMap<>());

        double speed = RaidConfig.NAVAL_BOAT_SPEED.get() / 100.0; // 0.10 default
        Iterator<Map.Entry<UUID, BlockPos>> it = boats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            UUID boatId = entry.getKey();
            Entity boat = level.getEntity(boatId);
            if (boat == null || !boat.isAlive() || boat.isRemoved()) {
                it.remove();
                stuck.remove(boatId);
                continue;
            }
            // If every mounted mob dismounted (either by choice, by beaching, or
            // because the boat sank) we're done steering this boat. Small Ships
            // warships can carry multiple passengers, and we only stop steering
            // when NONE of them is a live Mob \u2014 checking only the first
            // passenger would abandon the ship the moment the first crew died.
            if (boat.getPassengers().isEmpty() || !hasLiveMobPassenger(boat)) {
                it.remove();
                stuck.remove(boatId);
                continue;
            }
            BlockPos beach = entry.getValue();
            Vec3 boatPos = boat.position();
            // Vessels that carry multiple raiders (Small Ships warships) are
            // treated identically to a vanilla boat here — same steering, same
            // beach behavior. The rest of this loop is unchanged.
            double dx = (beach.getX() + 0.5) - boatPos.x;
            double dz = (beach.getZ() + 0.5) - boatPos.z;
            double distSq = dx * dx + dz * dz;

            // Beached: dismount the passenger onto land so ground AI kicks in.
            if (distSq < 4.0) {
                dismountAll(boat, beach);
                boat.discard();
                it.remove();
                stuck.remove(boatId);
                continue;
            }

            // Only apply thrust when the boat is actually on water. If we
            // can't apply thrust for many consecutive ticks while the boat
            // still has live mob passengers, the boat is wedged on shore
            // geometry (Small Ships hulls in particular block their own
            // thrust once beached). Bail out and force-dismount the crew
            // onto the nearest walkable land near the boat so the amphibious
            // hand-off completes instead of stalling the raid forever.
            boolean onWater = level.getFluidState(boat.blockPosition()).getType() == Fluids.WATER
                    || level.getFluidState(boat.blockPosition().below()).getType() == Fluids.WATER;
            if (!onWater) {
                int ticks = stuck.getOrDefault(boatId, 0) + 1;
                if (ticks >= STUCK_TICK_LIMIT) {
                    BlockPos landing = nearestWalkableLand(level, boat.blockPosition(), beach);
                    dismountAll(boat, landing);
                    boat.discard();
                    it.remove();
                    stuck.remove(boatId);
                } else {
                    stuck.put(boatId, ticks);
                }
                continue;
            }
            // On water: reset stuck counter since we're making steering progress.
            stuck.remove(boatId);

            double dist = Math.sqrt(distSq);
            double vx = (dx / dist) * speed;
            double vz = (dz / dist) * speed;
            // Blend with current velocity so the boat feels weighty, not RC-car.
            Vec3 v = boat.getDeltaMovement();
            boat.setDeltaMovement(v.x * 0.6 + vx * 0.4, v.y, v.z * 0.6 + vz * 0.4);
            boat.setYRot((float) (Math.toDegrees(Math.atan2(-dx, dz))));
            boat.hurtMarked = true;
        }

        if (boats.isEmpty()) {
            TARGETS.remove(teamKey);
            STUCK_TICKS.remove(teamKey);
        }
    }

    /**
     * Dismount every passenger from {@code boat} and teleport any {@link Mob}
     * passengers onto {@code landing} so their ground AI takes over. Called
     * on both the normal beach path and the stuck-force-dismount fallback.
     */
    private static void dismountAll(Entity boat, BlockPos landing) {
        for (Entity passenger : new java.util.ArrayList<>(boat.getPassengers())) {
            passenger.stopRiding();
            if (passenger instanceof Mob mob) {
                mob.teleportTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
            }
        }
    }

    /**
     * Find the nearest walkable land block to {@code from}, biased toward
     * {@code preferred} (the assigned beach) so raiders still push in roughly
     * the intended direction after a force-dismount. Falls back to
     * {@code preferred} when no clear landing is found in a small search box.
     */
    private static BlockPos nearestWalkableLand(ServerLevel level, BlockPos from, BlockPos preferred) {
        int fx = from.getX();
        int fz = from.getZ();
        int fy = from.getY();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    BlockPos p = new BlockPos(fx + dx, fy + dy, fz + dz);
                    if (!level.getBlockState(p).isAir()) continue;
                    if (!level.getBlockState(p.above()).isAir()) continue;
                    var below = level.getBlockState(p.below());
                    if (below.isAir()) continue;
                    if (below.getFluidState().getType() == Fluids.WATER) continue;
                    double ddx = (preferred.getX() + 0.5) - (p.getX() + 0.5);
                    double ddz = (preferred.getZ() + 0.5) - (p.getZ() + 0.5);
                    double score = ddx * ddx + ddz * ddz;
                    if (score < bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        }
        return best != null ? best : preferred;
    }

    /** Drop all convoy tracking for a raid \u2014 called on raid end. */
    public static void forget(String teamKey) {
        TARGETS.remove(teamKey);
        STUCK_TICKS.remove(teamKey);
    }

    /** Live boat count in the convoy; used for the "raiders lost at sea" report. */
    public static int size(String teamKey) {
        Map<UUID, BlockPos> boats = TARGETS.get(teamKey);
        return boats == null ? 0 : boats.size();
    }

    /**
     * Sanity helper: is the entity a boat currently under our steering? Used
     * by defender-attribution code to properly credit "sank a raider boat".
     */
    public static boolean isRaiderBoat(String teamKey, Entity entity) {
        if (entity == null) return false;
        Map<UUID, BlockPos> boats = TARGETS.get(teamKey);
        return boats != null && boats.containsKey(entity.getUUID());
    }

    /**
     * Radius around the objective that counts as "sea approach" for wave
     * scoring. Not currently referenced but exposed for future casus belli
     * hooks (see the amphibious raids PR discussion).
     */
    @SuppressWarnings("unused")
    public static AABB approachBox(BlockPos beach, int radius) {
        return new AABB(beach).inflate(radius, 8, radius);
    }

    /**
     * @return true if at least one passenger on the vessel is a live Mob.
     * Vanilla oak boats have a single passenger, but Small Ships warships
     * can crew multiple mobs and we should keep steering until every last
     * crew member has fallen or dismounted.
     */
    private static boolean hasLiveMobPassenger(Entity vessel) {
        for (Entity passenger : vessel.getPassengers()) {
            if (passenger instanceof Mob mob && mob.isAlive()) return true;
        }
        return false;
    }
}
