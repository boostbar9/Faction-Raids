package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.Fluids;

import java.util.Map;
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
        // v4.42.0: preferSmall (a vanilla boat) when the raider is going
        // to end up swimming otherwise. Callers still get a Small Ships
        // warship when the mod is installed and the site can fit one.
        return spawn(level, stagingPos, false);
    }

    /**
     * v4.42.0 - vessel spawn with an explicit staging position. The
     * scatter search verifies clear water AND clear air above so a
     * boat never spawns inside a cliff face or clipped through terrain.
     * If no clear staging can be found, the vessel is not spawned.
     * That is safer than dropping a boat inside solid rock where it
     * suffocates.
     */
    public static Optional<Entity> spawn(ServerLevel level, BlockPos stagingPos, boolean forceVanilla) {
        BlockPos scattered = findScatterPos(level, stagingPos, /*hullRadius=*/2);
        if (scattered == null) return Optional.empty();
        if (!forceVanilla && RaidConfig.PREFER_SMALL_SHIPS.get() && SmallShipsIntegration.hasAnyKnownShip()) {
            // Small Ships warships are ~4-5 wide, so check a wider hull radius
            // before we commit to spawning one. If the surface can't fit a
            // warship, still try a vanilla boat rather than skip.
            BlockPos warshipScatter = findScatterPos(level, stagingPos, /*hullRadius=*/3);
            if (warshipScatter != null) {
                Optional<Entity> ship = SmallShipsIntegration.spawnShip(level, warshipScatter,
                        RaidConfig.SMALL_SHIPS_PREFER_LARGE.get());
                if (ship.isPresent()) return ship;
            }
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
    /**
     * v4.42.0 - wider search ring, verified water surface, verified clear
     * hull footprint (checks {@code hullRadius} blocks in every direction
     * are also water and have open air above). Returns null when no
     * viable staging exists so the caller can skip rather than spawn a
     * ship inside a cliff face or on top of another vessel.
     */
    private static BlockPos findScatterPos(ServerLevel level, BlockPos stagingPos, int hullRadius) {
        // Track occupied scatter positions per level tick so successive
        // spawns in the same squad don't land on the same water tile.
        java.util.Set<Long> claimed = OCCUPIED.computeIfAbsent(level.dimension().location().toString(),
                k -> new java.util.HashSet<>());
        if (level.getGameTime() != lastPurgeTick) {
            OCCUPIED.clear();
            lastPurgeTick = level.getGameTime();
            claimed = OCCUPIED.computeIfAbsent(level.dimension().location().toString(),
                    k -> new java.util.HashSet<>());
        }
        for (int i = 0; i < SPAWN_SCATTER_TRIES; i++) {
            int radius = SPAWN_SCATTER_MIN_RADIUS + level.random.nextInt(
                    SPAWN_SCATTER_MAX_RADIUS - SPAWN_SCATTER_MIN_RADIUS + 1);
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockPos candidate = stagingPos.offset(dx, 0, dz);
            if (!isClearWaterFootprint(level, candidate, hullRadius)) continue;
            if (!claimed.add(candidate.asLong())) continue;
            return candidate;
        }
        // Fallback - accept the raw staging pos ONLY if it is safe.
        if (isClearWaterFootprint(level, stagingPos, hullRadius) && claimed.add(stagingPos.asLong())) {
            return stagingPos;
        }
        return null;
    }

    /**
     * v4.42.0 - centre tile must be surface water, and every tile within
     * the square {@code hullRadius} footprint must ALSO be water
     * with clear air above. Rejects cliff faces, jetties, and
     * partially-blocked spawn sites that used to slice ship models in
     * half.
     */
    static boolean isClearWaterFootprint(ServerLevel level, BlockPos pos, int hullRadius) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() + 3 > level.getMaxBuildHeight()) return false;
        for (int dx = -hullRadius; dx <= hullRadius; dx++) {
            for (int dz = -hullRadius; dz <= hullRadius; dz++) {
                BlockPos column = pos.offset(dx, 0, dz);
                if (!level.getWorldBorder().isWithinBounds(column) || !isSurfaceWater(level, column)) return false;
            }
        }
        // Reserve room for the hull, including diagonal corners and vessels spawned earlier.
        var hull = new net.minecraft.world.phys.AABB(pos.offset(-hullRadius, 0, -hullRadius),
                pos.offset(hullRadius + 1, 3, hullRadius + 1));
        var border = level.getWorldBorder();
        return hull.minX >= border.getMinX() && hull.maxX <= border.getMaxX()
                && hull.minZ >= border.getMinZ() && hull.maxZ <= border.getMaxZ()
                && level.getEntities((Entity) null, hull).isEmpty();
    }

    /**
     * Non-colliding water at {@code pos}, with two air blocks above.
     * This is minimum hull/crew clearance, not a multipart mast-envelope check.
     */
    private static boolean isSurfaceWater(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        if (level.getFluidState(pos).getType() != Fluids.WATER) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        // Ceiling clearance: no solid blocks in the two tiles above.
        var above1 = level.getBlockState(pos.above());
        var above2 = level.getBlockState(pos.above(2));
        if (!above1.isAir() || !above2.isAir()) return false;
        return true;
    }

    /** v4.42.0 - reservations that expire when the game tick advances. */
    private static final Map<String, java.util.Set<Long>> OCCUPIED = new java.util.HashMap<>();
    private static long lastPurgeTick = Long.MIN_VALUE;

    // Scatter tuning. Kept as constants (not config) because this is a
    // per-boat spread, not a gameplay dial — server owners should never
    // need to touch these. Values chosen so a squad of ~6 boats spreads
    // over roughly a 6-block-wide arc, wide enough for each boat to have
    // clear water on both sides without pulling any boat so far from the
    // beach heading that its steering fights the current.
    // v4.42.0: wider search, more tries. Squads of 6+ warships need
    // ~6-8 blocks of separation to avoid hull collision, and the
    // increased hull-footprint check makes some candidates fail so we
    // need more tries per boat.
    private static final int SPAWN_SCATTER_TRIES = 24;
    private static final int SPAWN_SCATTER_MIN_RADIUS = 4;
    private static final int SPAWN_SCATTER_MAX_RADIUS = 10;

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
            if (board(vessel, raider)) mounted++;
        }
        return mounted;
    }

    /** Let the vessel enforce seats, capacity, locks and mount restrictions. */
    public static boolean board(Entity vessel, Mob raider) {
        if (vessel == null || raider == null || !raider.isAlive() || raider.isPassenger()) return false;
        return raider.startRiding(vessel, false);
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
