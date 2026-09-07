package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Steer invasion vessels, recover them after reload, and hand their crew to ground combat. */
public final class NavalConvoy {
    private static final Map<String, Map<UUID, BlockPos>> TARGETS = new HashMap<>();
    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();
    private static final int STUCK_TICK_LIMIT = ModConstants.secondsToTicks(20);
    private NavalConvoy() {}

    public static void enlist(String teamKey, Entity vessel, BlockPos beach) {
        if (teamKey == null || teamKey.isBlank() || beach == null) return;
        vessel.getPersistentData().putBoolean(ModConstants.Tags.NAVAL_DISPOSABLE, true);
        track(teamKey, vessel, beach);
    }

    private static void track(String teamKey, Entity vessel, BlockPos beach) {
        TARGETS.computeIfAbsent(teamKey, k -> new HashMap<>()).put(vessel.getUUID(), beach.immutable());
        vessel.getPersistentData().putString(ModConstants.Tags.NAVAL_TEAM, teamKey);
        vessel.getPersistentData().putLong(ModConstants.Tags.NAVAL_BEACH, beach.asLong());
    }

    /** Entity NBT is authoritative; loaded raiders reconnect to their vessel after chunk/server reload. */
    public static void recover(ServerLevel level, RaidState raid) {
        for (UUID id : raid.raiders) {
            Entity raider = level.getEntity(id);
            if (!(raider instanceof Mob) || !raider.isAlive() || !raider.isPassenger()) continue;
            Entity vessel = raider.getRootVehicle();
            var tag = vessel.getPersistentData();
            if (raid.teamKey.equals(tag.getString(ModConstants.Tags.NAVAL_TEAM))
                    && tag.contains(ModConstants.Tags.NAVAL_BEACH)) {
                track(raid.teamKey, vessel, BlockPos.of(tag.getLong(ModConstants.Tags.NAVAL_BEACH)));
            } else if (tag.getString(ModConstants.Tags.NAVAL_TEAM).isBlank() && raid.navalBeachPos != null) {
                var type = vessel.getType() == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(vessel.getType());
                if (vessel instanceof Boat || type != null && type.getNamespace().equals("smallships")) {
                    // Older releases did not tag their vessels. Recover the raiders' landing,
                    // but never assume ownership of this untagged boat or delete it afterward.
                    tag.putBoolean(ModConstants.Tags.NAVAL_DISPOSABLE, false);
                    track(raid.teamKey, vessel, raid.navalBeachPos);
                }
            }
        }
    }

    public static void tick(String teamKey, ServerLevel level, BlockPos objective) {
        Map<UUID, BlockPos> boats = TARGETS.get(teamKey);
        if (boats == null) return;
        var iterator = boats.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Entity boat = level.getEntity(entry.getKey());
            if (boat == null || !boat.isAlive() || boat.isRemoved()) {
                PROGRESS.remove(entry.getKey());
                iterator.remove(); // NBT recovery handles unloaded vessels when their crew returns.
                continue;
            }
            List<Mob> crew = raidCrew(boat, teamKey);
            if (crew.isEmpty()) {
                release(boat);
                iterator.remove();
                continue;
            }
            BlockPos beach = entry.getValue();
            double dx = beach.getX() + 0.5 - boat.getX(), dz = beach.getZ() + 0.5 - boat.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            Progress progress = PROGRESS.computeIfAbsent(boat.getUUID(), k -> new Progress(distance, level.getGameTime()));
            boolean stuck = progress.stalled(distance, level.getGameTime());
            double landingDistance = Math.max(2, Math.min(12, boat.getBbWidth() / 2.0 + 2));
            if (distance <= landingDistance || stuck) {
                disembark(level, boat, crew, beach, objective);
                if (raidCrew(boat, teamKey).isEmpty()) {
                    boolean disposable = boat.getPersistentData().getBoolean(ModConstants.Tags.NAVAL_DISPOSABLE);
                    release(boat);
                    // Do not remove legacy/unowned vessels or ships still carrying other passengers.
                    if (disposable && boat.getPassengers().isEmpty()) boat.discard();
                    iterator.remove();
                    continue;
                }
            }
            // No safe landing yet: retain the crew and keep steering, never teleport blindly to the beach.
            if (boat.isInWater() && distance > 0.01) {
                double speed = RaidConfig.NAVAL_BOAT_SPEED.get() / 100.0;
                Vec3 velocity = boat.getDeltaMovement();
                boat.setDeltaMovement(velocity.x * 0.6 + dx / distance * speed * 0.4,
                        velocity.y, velocity.z * 0.6 + dz / distance * speed * 0.4);
                boat.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                boat.hurtMarked = true;
            }
        }
        if (boats.isEmpty()) TARGETS.remove(teamKey);
    }

    static final class Progress {
        private double bestDistance;
        private long lastProgress;
        Progress(double distance, long now) { bestDistance = distance; lastProgress = now; }
        boolean stalled(double distance, long now) {
            if (now < lastProgress || bestDistance - distance >= 0.5) {
                bestDistance = distance;
                lastProgress = now;
            }
            return now - lastProgress >= STUCK_TICK_LIMIT;
        }
    }

    /** Recurse through Small Ships seat entities, but never eject players or unrelated passengers. */
    static List<Mob> raidCrew(Entity vessel, String teamKey) {
        List<Mob> crew = new ArrayList<>();
        for (Entity passenger : vessel.getPassengers()) {
            if (passenger instanceof Mob mob && mob.isAlive()
                    && teamKey.equals(mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM))) crew.add(mob);
            crew.addAll(raidCrew(passenger, teamKey));
        }
        return crew;
    }

    static int disembark(ServerLevel level, Entity boat, List<Mob> crew, BlockPos beach, BlockPos objective) {
        List<AABB> reserved = new ArrayList<>();
        int landed = 0;
        for (Mob mob : crew) {
            BlockPos landing = findLanding(level, boat, mob, beach, reserved);
            if (landing == null) continue;
            mob.stopRiding();
            if (mob.isPassenger()) continue; // Respect another mod canceling the dismount.
            mob.teleportTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0;
            RecruitsBridge.resumeRaidOnFoot(mob);
            mob.getNavigation().stop();
            if (objective != null) mob.getNavigation().moveTo(objective.getX() + 0.5, objective.getY(), objective.getZ() + 0.5, 1.1);
            reserved.add(landingBounds(mob, landing));
            landed++;
        }
        return landed;
    }

    static BlockPos findLanding(ServerLevel level, Entity boat, Mob mob, BlockPos preferred, List<AABB> reserved) {
        int radius = Math.max(8, Math.min(16, (int) Math.ceil(boat.getBbWidth() / 2.0) + 3));
        BlockPos from = boat.blockPosition(), best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos column = from.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) continue;
                BlockPos feet = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ()), column.getZ());
                if (Math.abs(feet.getY() - from.getY()) > 6 || !safeLanding(level, mob, feet, reserved)) continue;
                double score = feet.distSqr(preferred) + feet.distSqr(from) * 0.25;
                if (score < bestScore) { bestScore = score; best = feet; }
            }
        }
        return best;
    }

    static AABB landingBounds(Mob mob, BlockPos feet) {
        return mob.getBoundingBox().move(feet.getX() + 0.5 - mob.getX(), feet.getY() - mob.getY(), feet.getZ() + 0.5 - mob.getZ());
    }

    static boolean safeLanding(ServerLevel level, Mob mob, BlockPos feet, List<AABB> reserved) {
        AABB bounds = landingBounds(mob, feet);
        if (!level.hasChunkAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ))
                || !level.hasChunkAt(BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                || !level.getWorldBorder().isWithinBounds(bounds)
                || feet.getY() <= level.getMinBuildHeight() || bounds.maxY >= level.getMaxBuildHeight()) return false;
        var floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP) || !floor.getFluidState().isEmpty()
                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)) return false;
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                || !level.noCollision(mob, bounds)) return false;
        return reserved.stream().noneMatch(bounds::intersects);
    }

    private static void release(Entity boat) {
        PROGRESS.remove(boat.getUUID());
        boat.getPersistentData().remove(ModConstants.Tags.NAVAL_DISPOSABLE);
        boat.getPersistentData().remove(ModConstants.Tags.NAVAL_TEAM);
        boat.getPersistentData().remove(ModConstants.Tags.NAVAL_BEACH);
    }

    public static void forget(String teamKey) {
        Map<UUID, BlockPos> boats = TARGETS.remove(teamKey);
        if (boats != null) boats.keySet().forEach(PROGRESS::remove);
    }
    public static int size(String teamKey) {
        Map<UUID, BlockPos> boats = TARGETS.get(teamKey);
        return boats == null ? 0 : boats.size();
    }
    public static boolean isRaiderBoat(String teamKey, Entity entity) {
        Map<UUID, BlockPos> boats = TARGETS.get(teamKey);
        return entity != null && boats != null && boats.containsKey(entity.getUUID());
    }
    public static AABB approachBox(BlockPos beach, int radius) { return new AABB(beach).inflate(radius, 8, radius); }
}
