package com.devfarinsky.factionraids.naval;

import com.devfarinsky.factionraids.RaidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.Optional;

/**
 * Vessel factory for amphibious raids. Central spawn point so future
 * ship types (rafts, galleys, ...) only need to be plumbed here and
 * every caller \u2014 wave spawns, stragglers, PR #13+ paths \u2014 picks up
 * the new option automatically.
 *
 * <p>Selection order:
 * <ol>
 *   <li>Small Ships warship, when the mod is installed <b>and</b>
 *       {@link RaidConfig#PREFER_SMALL_SHIPS} is on.</li>
 *   <li>Vanilla oak {@link Boat} otherwise, or when Small Ships spawn
 *       fails for any reason.</li>
 * </ol></p>
 */
public final class NavalFleet {

    private NavalFleet() {}

    /**
     * Spawn a vessel at {@code stagingPos}. Returns the spawned entity
     * or empty when even the vanilla-boat fallback failed (e.g. the
     * block is solid).
     */
    public static Optional<Entity> spawn(ServerLevel level, BlockPos stagingPos) {
        if (RaidConfig.PREFER_SMALL_SHIPS.get() && SmallShipsIntegration.hasAnyKnownShip()) {
            Optional<Entity> ship = SmallShipsIntegration.spawnShip(level, stagingPos,
                    RaidConfig.SMALL_SHIPS_PREFER_LARGE.get());
            if (ship.isPresent()) return ship;
        }
        return spawnVanillaBoat(level, stagingPos);
    }

    /** Vanilla oak boat fallback \u2014 always available. */
    public static Optional<Entity> spawnVanillaBoat(ServerLevel level, BlockPos stagingPos) {
        Boat boat = new Boat(level,
                stagingPos.getX() + 0.5, stagingPos.getY() + 0.1, stagingPos.getZ() + 0.5);
        boat.setVariant(Boat.Type.OAK);
        boat.setYRot(level.random.nextFloat() * 360.0F);
        if (!level.addFreshEntity(boat)) return Optional.empty();
        return Optional.of(boat);
    }

    /**
     * Mount up to {@code max} raiders on a vessel. Returns the number
     * actually mounted. Vanilla boats hard-cap at 2 passengers no
     * matter what; Small Ships vessels honor {@code max}.
     */
    public static int mountCrew(Entity vessel, Iterable<Mob> raiders, int max) {
        if (vessel == null || max <= 0) return 0;
        int mounted = 0;
        for (Mob raider : raiders) {
            if (mounted >= max) break;
            if (raider == null || !raider.isAlive()) continue;
            if (raider.startRiding(vessel, true)) mounted++;
        }
        return mounted;
    }

    /**
     * @return true when the vessel is a Small Ships warship (as opposed
     * to a vanilla boat). Callers can use this for cosmetic
     * announcements or logging.
     */
    public static boolean isSmallShipsVessel(Entity vessel) {
        if (vessel == null || vessel.getType() == EntityType.BOAT
                || vessel.getType() == EntityType.CHEST_BOAT) return false;
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(vessel.getType());
        return key != null && SmallShipsIntegration.MOD_ID.equals(key.getNamespace());
    }
}
