package com.devfarinsky.factionraids.naval;

import com.devfarinsky.factionraids.RaidConfig;
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

        double speed = RaidConfig.NAVAL_BOAT_SPEED.get() / 100.0; // 0.10 default
        Iterator<Map.Entry<UUID, BlockPos>> it = boats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            Entity boat = level.getEntity(entry.getKey());
            if (boat == null || !boat.isAlive() || boat.isRemoved()) {
                it.remove();
                continue;
            }
            // If every mounted mob dismounted (either by choice, by beaching, or
            // because the boat sank) we're done steering this boat. Small Ships
            // warships can carry multiple passengers, and we only stop steering
            // when NONE of them is a live Mob \u2014 checking only the first
            // passenger would abandon the ship the moment the first crew died.
            if (boat.getPassengers().isEmpty() || !hasLiveMobPassenger(boat)) {
                it.remove();
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
                // Dismount every passenger so ground AI kicks in. A vanilla
                // boat has 1 passenger, but Small Ships vessels can carry a
                // full crew that all need to disembark.
                for (Entity passenger : new java.util.ArrayList<>(boat.getPassengers())) {
                    passenger.stopRiding();
                    if (passenger instanceof Mob mob) {
                        mob.teleportTo(beach.getX() + 0.5, beach.getY(), beach.getZ() + 0.5);
                    }
                }
                boat.discard();
                it.remove();
                continue;
            }

            // Only apply thrust when the boat is actually on water \u2014 avoids
            // pushing a stuck boat into a wall.
            boolean onWater = level.getFluidState(boat.blockPosition()).getType() == Fluids.WATER
                    || level.getFluidState(boat.blockPosition().below()).getType() == Fluids.WATER;
            if (!onWater) continue;

            double dist = Math.sqrt(distSq);
            double vx = (dx / dist) * speed;
            double vz = (dz / dist) * speed;
            // Blend with current velocity so the boat feels weighty, not RC-car.
            Vec3 v = boat.getDeltaMovement();
            boat.setDeltaMovement(v.x * 0.6 + vx * 0.4, v.y, v.z * 0.6 + vz * 0.4);
            boat.setYRot((float) (Math.toDegrees(Math.atan2(-dx, dz))));
            boat.hurtMarked = true;
        }

        if (boats.isEmpty()) TARGETS.remove(teamKey);
    }

    /** Drop all convoy tracking for a raid \u2014 called on raid end. */
    public static void forget(String teamKey) {
        TARGETS.remove(teamKey);
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
