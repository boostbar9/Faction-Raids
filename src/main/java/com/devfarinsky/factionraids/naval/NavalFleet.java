package com.devfarinsky.factionraids.naval;

import com.devfarinsky.factionraids.RaidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.Fluids;

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
     *
     * <p>Every caller previously passed the same block position for every
     * raider in a squad, which stacked all boats on one water column and
     * left them jammed against each other every tick. To fix that, this
     * method scatters the actual spawn to a jittered water tile within a
     * small ring around {@code stagingPos}. See {@link #findScatterPos}.
     */
    public static Optional<Entity> spawn(ServerLevel level, BlockPos stagingPos) {
        BlockPos scattered = findScatterPos(level, stagingPos);
        if (RaidConfig.PREFER_SMALL_SHIPS.get() && SmallShipsIntegration.hasAnyKnownShip()) {
            Optional<Entity> ship = SmallShipsIntegration.spawnShip(level, scattered,
                    RaidConfig.SMALL_SHIPS_PREFER_LARGE.get());
            if (ship.isPresent()) return ship;
        }
        return spawnVanillaBoat(level, scattered);
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
     * Find a nearby water tile to spawn a boat on. Prevents the entire
     * squad's boats from stacking on a single block, which was the root
     * cause of the "boats never move" bug — colliding boats can't build
     * forward velocity.
     *
     * <p>Strategy: try up to {@code SPAWN_SCATTER_TRIES} random offsets
     * within a ring of radius {@code SPAWN_SCATTER_MIN_RADIUS} to
     * {@code SPAWN_SCATTER_MAX_RADIUS} around {@code stagingPos}. Accept
     * the first candidate that is water (either at the tile itself or the
     * block below, matching the water-check in NavalConvoy). If every
     * attempt lands on solid ground or lava, fall back to the original
     * position — worst case we're no worse than pre-fix.
     */
    private static BlockPos findScatterPos(ServerLevel level, BlockPos stagingPos) {
        for (int i = 0; i < SPAWN_SCATTER_TRIES; i++) {
            int radius = SPAWN_SCATTER_MIN_RADIUS + level.random.nextInt(
                    SPAWN_SCATTER_MAX_RADIUS - SPAWN_SCATTER_MIN_RADIUS + 1);
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockPos candidate = stagingPos.offset(dx, 0, dz);
            if (isWater(level, candidate)) return candidate;
        }
        return stagingPos;
    }

    private static boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).getType() == Fluids.WATER
                || level.getFluidState(pos.below()).getType() == Fluids.WATER;
    }

    // Scatter tuning. Kept as constants (not config) because this is a
    // per-boat spread, not a gameplay dial — server owners should never
    // need to touch these. Values chosen so a squad of ~6 boats spreads
    // over roughly a 6-block-wide arc, wide enough for each boat to have
    // clear water on both sides without pulling any boat so far from the
    // beach heading that its steering fights the current.
    private static final int SPAWN_SCATTER_TRIES = 8;
    private static final int SPAWN_SCATTER_MIN_RADIUS = 2;
    private static final int SPAWN_SCATTER_MAX_RADIUS = 4;

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
