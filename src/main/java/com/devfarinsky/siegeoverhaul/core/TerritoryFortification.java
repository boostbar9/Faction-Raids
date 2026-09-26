package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * v4.22.0 Fortify Perimeter job.
 *
 * <p>Commissioned from the Territory tab of the Core menu. Computes the outer
 * edge of the player's Recruits claim, generates a Workers 2 build-area
 * blueprint of walls (3 tall) with 5-tall corner pillars, and hands the job
 * to a nearby Workers 2 builder. The builder pulls material from a
 * storagearea the player has already set up in their claim, exactly like any
 * other Workers 2 job. When the storagearea runs dry the builder waits.</p>
 *
 * <p>Only the commission is charged in emeralds. Every block itself is
 * supplied by the player.</p>
 */
public final class TerritoryFortification {

    public static final int PRICE = 900;
    /** Height of the wall segments. */
    public static final int WALL_HEIGHT = 3;
    /** Additional pillar height above the wall at chunk corners. */
    public static final int CORNER_EXTRA = 2;
    /**
     * Maximum distance the wall extends downward through air to find solid
     * ground. Prevents wall columns from floating over pits or ledges, and
     * gives the Workers 2 builder ground to stand on while placing the next
     * column so a single pit does not stall the whole perimeter build.
     */
    public static final int FOUNDATION_DEPTH = 8;
    /** How far from the core we search for the commissioned builder. */
    public static final int BUILDER_SEARCH_RADIUS = 16;
    /** How far from the builder we look for a player-placed storagearea. */
    public static final int STORAGE_SEARCH_RADIUS = 64;

    /** Chunk-edge search cap so a runaway claim doesn't melt the server tick. */
    public static final int MAX_PERIMETER_BLOCKS = 4096;

    /** Player-facing material choices matching UI button indices 0/1/2. */
    public static final Material[] MATERIALS = {
            new Material(0, "Stone Bricks", "minecraft:stone_bricks"),
            new Material(1, "Cobblestone", "minecraft:cobblestone"),
            new Material(2, "Oak Planks", "minecraft:oak_planks")
    };

    public record Material(int index, String label, String blockId) {}

    private TerritoryFortification() {}

    public static Material material(int index) {
        if (index < 0 || index >= MATERIALS.length) return MATERIALS[0];
        return MATERIALS[index];
    }

    /** Server entry point wired to CoreHireMenu buttons 64/65/66. */
    public static boolean commission(ServerPlayer player, BlockPos corePos, int materialIndex) {
        if (player == null || corePos == null) return false;
        if (materialIndex < 0 || materialIndex >= MATERIALS.length) return false;
        Material mat = MATERIALS[materialIndex];
        ServerLevel level = player.serverLevel();

        if (!WorkersBridge.available()) {
            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter requires the Villager Recruits + Workers 2 mods."));
            return false;
        }
        if (!RecruitsClaimsBridge.available()) {
            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter needs a Recruits claim covering your territory."));
            return false;
        }

        RaidSavedData saved = RaidSavedData.get(player.server);
        String coreKey = SiegeCore.key(player);
        RaidSavedData.Anchor anchor = saved.anchors.get(coreKey);
        if (anchor == null) {
            player.sendSystemMessage(Component.literal("Place a Siege Core and claim territory first."));
            return false;
        }

        var claimOpt = RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
        if (claimOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Your core is not standing inside a claim owned by your faction."));
            return false;
        }
        Set<ChunkPos> chunks = claimOpt.get().chunks();
        if (chunks.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your claim has no chunks. Nothing to fortify."));
            return false;
        }

        BuilderSearch search = findNearbyBuilder(level, player, corePos);
        Mob builder = search.builder();
        if (builder == null) {
            player.sendSystemMessage(Component.literal(search.reason()));
            return false;
        }

        // The player must have set up a Workers 2 storagearea inside their
        // claim already, owned by them. Workers 2 performs its live lookup
        // around the builder, so validate from that same position. Centering
        // this check on the core could accept a storagearea that was within
        // 64 blocks of the core but outside the builder's actual search box.
        Entity playerStorage = findPlayerStorageArea(level, player, builder.blockPosition(), chunks);
        if (playerStorage == null) {
            player.sendSystemMessage(Component.literal(
                    "Place a Workers 2 storage area inside your claim (within "
                            + STORAGE_SEARCH_RADIUS + " blocks of the builder) and fill it with "
                            + mat.label() + ". Then commission again."));
            return false;
        }
        // Workers 2 storageareas gate access by job type. If the player never
        // toggled BUILDERS on inside the storagearea GUI the builder will
        // silently report "No available storage found nearby" even though
        // ours is right there. Catch this early with a clear message.
        if (!WorkersBridge.hasBuilderStorage(playerStorage)) {
            player.sendSystemMessage(Component.literal(
                    "Your storage area does not have Builders enabled. Right-click the storage area and turn on the Builders job, then commission again."));
            return false;
        }

        long budget = PaymentSource.available(player, PRICE);
        if (!player.isCreative() && budget < PRICE) {
            player.sendSystemMessage(Component.literal(
                    "You need " + PRICE + " emeralds in the faction Treasury to commission the wall."));
            return false;
        }

        // Compute the perimeter.
        List<BlockPos> wallColumns = new ArrayList<>();
        Set<Long> cornerColumns = new HashSet<>();
        int baseY = corePos.getY();
        computePerimeter(level, chunks, baseY, wallColumns, cornerColumns);
        if (wallColumns.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No exposed perimeter found. Every edge already borders your own claim."));
            return false;
        }
        // Workers 2 builders only ever fetch material from a storage area near
        // them. Perimeter columns further away than that search box stall the
        // job with "No available storage found nearby", which is why a wall on
        // a large claim used to stop part-way through with no explanation.
        // Queue only what this storage area can actually supply and tell the
        // player what was left out.
        int outOfStorageRange = wallColumns.size();
        wallColumns = withinStorageRange(wallColumns, playerStorage.blockPosition());
        outOfStorageRange -= wallColumns.size();
        if (wallColumns.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Your storage area is more than " + STORAGE_SEARCH_RADIUS
                            + " blocks from every perimeter section. Move it closer to the wall line and commission again."));
            return false;
        }
        if (wallColumns.size() > MAX_PERIMETER_BLOCKS) {
            player.sendSystemMessage(Component.literal(
                    "Your claim perimeter is too long to fortify in one job ("
                            + wallColumns.size() + " > " + MAX_PERIMETER_BLOCKS + " blocks)."));
            return false;
        }

        final int skippedColumns = outOfStorageRange;
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(mat.blockId()));
        if (block == null || block == Blocks.AIR) {
            player.sendSystemMessage(Component.literal("Unknown wall material: " + mat.blockId()));
            return false;
        }

        // For every wall column, extend the build downward through air until
        // it hits solid ground (max FOUNDATION_DEPTH blocks). Without this,
        // any column that sits above a pit or ledge left the builder trying
        // to walk on missing ground: Workers 2's pathfinder either falls into
        // the pit or never converges, so the whole wall stalls behind the
        // gap. Filling the gap with wall material closes the perimeter and
        // gives the builder something to stand on for the next column.
        Map<BlockPos, Integer> foundationBelow = new HashMap<>();
        for (BlockPos base : wallColumns) {
            int filled = foundationDepth(level, base);
            if (filled > 0) foundationBelow.put(base, filled);
        }

        // Bounds for the build area entity: expand vertically to cover pillar tops.
        BlockPos min = null, max = null;
        for (BlockPos base : wallColumns) {
            int top = columnTopY(base, cornerColumns.contains(base.asLong()));
            int bottom = base.getY() - foundationBelow.getOrDefault(base, 0);
            for (int y = bottom; y <= top; y++) {
                BlockPos p = new BlockPos(base.getX(), y, base.getZ());
                if (min == null) { min = p; max = p; continue; }
                min = new BlockPos(Math.min(min.getX(), p.getX()),
                        Math.min(min.getY(), p.getY()),
                        Math.min(min.getZ(), p.getZ()));
                max = new BlockPos(Math.max(max.getX(), p.getX()),
                        Math.max(max.getY(), p.getY()),
                        Math.max(max.getZ(), p.getZ()));
            }
        }

        Map<Long, String> blocks = new LinkedHashMap<>();
        for (BlockPos base : wallColumns) {
            int top = columnTopY(base, cornerColumns.contains(base.asLong()));
            int bottom = base.getY() - foundationBelow.getOrDefault(base, 0);
            for (int y = bottom; y <= top; y++) {
                BlockPos p = new BlockPos(base.getX(), y, base.getZ());
                if (!level.hasChunkAt(p)) continue;
                BlockState existing = level.getBlockState(p);
                // A commission is not permission to mine an existing house.
                // Native Workers can clear cells in its blueprint; omit solid
                // obstructions, not only containers, before handing it the job.
                if (existing.getBlock() == block) continue;
                if (!safeWallReplacement(existing)) continue;
                blocks.put(p.asLong(), mat.blockId());
            }
        }
        if (blocks.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "The perimeter is already covered by existing blocks. Nothing to place."));
            return false;
        }

        Entity build = null;
        boolean committed = false;
        // Use the player's UUID as the buildarea owner so their existing
        // storagearea (owned by the same player) grants access naturally.
        UUID owner = player.getUUID();
        try {
            build = WorkersBridge.createPlayerArea(level, "buildarea",
                    new BlockPos(max.getX(), min.getY(), min.getZ()), owner,
                    player.getGameProfile().getName(),
                    max.getX() - min.getX() + 1,
                    max.getZ() - min.getZ() + 1,
                    max.getY() - min.getY() + 1);

            // Persist a player-job association on both sides. Do not use the
            // enemy CAMP_AREA_TEAM marker: the enemy reload hook discards
            // areas whose key has no active hostile raid, which used to erase
            // commissioned wall jobs after a server/chunk reload.
            PlayerFortificationJobs.link(builder, build, owner);

            // Spawn the buildarea into the level BEFORE calling setStartBuild.
            // setStartBuild reads world block state at each target position to
            // decide which blocks belong in stackToPlace, and Workers 2's
            // built-in flow always spawns the area first (via item placement)
            // and only then wires up the blueprint through its GUI.
            if (!level.addFreshEntity(build)) {
                throw new IllegalStateException("Cannot register buildarea entity");
            }
            CompoundTag blueprint = blueprint(blocks, min, max);
            WorkersBridge.startBlueprint(build, blueprint);

            // Report the exact material requirement to the player before we
            // charge, so an empty or wrong-material storage area produces an
            // actionable message instead of a silent stall.
            java.util.List<net.minecraft.world.item.ItemStack> required =
                    WorkersBridge.materials(build);
            int totalRequired = 0;
            for (net.minecraft.world.item.ItemStack s : required) totalRequired += s.getCount();

            // Hand the job to the builder under the player's UUID using the
            // player-safe path (does NOT install the raider night-shift goal
            // and does NOT overwrite the builder's inventory).
            WorkersBridge.enablePlayerJob(builder, owner);

            // Bypass Workers 2's 64-block auto-discovery: wire currentBuildArea
            // directly so the builder engages the goal on the next tick instead
            // of wandering. Teleport to the player's own position first if the
            // builder is far away, because the buildarea entity itself sits
            // over the top of the wall (in the air) so we cannot use its
            // position as a drop-point without risking a fall. The player just
            // stood at the core and clicked commission, so their feet are a
            // safe surface guaranteed to be inside the claim.
            WorkersBridge.teleportBuilderNear(builder, level, player.blockPosition());
            // A builder that never receives the area just wanders: fail the
            // whole commission instead of charging for a job nobody starts.
            if (!WorkersBridge.assignBuildAreaDirectly(builder, build)) {
                throw new IllegalStateException("the builder would not accept the blueprint");
            }

            // Charge only after every mutating step succeeded.
            if (!player.isCreative() && !PaymentSource.consume(player, PRICE)) {
                throw new IllegalStateException("Payment rejected");
            }
            committed = true;

            // v4.42.0 - clearer material breakdown so the player knows
            // exactly what to put in the storage area and roughly how
            // many stacks that is. Also flag when the required item is
            // not the display material (some vanilla blocks parse into
            // an item with a different name, e.g. stone -> cobblestone).
            int stacks = (totalRequired + 63) / 64;
            StringBuilder itemLine = new StringBuilder();
            String primaryItem = null;
            for (net.minecraft.world.item.ItemStack s : required) {
                if (itemLine.length() > 0) itemLine.append(", ");
                String itemName = s.getHoverName().getString();
                itemLine.append(s.getCount()).append(" x ").append(itemName);
                if (primaryItem == null) primaryItem = itemName;
            }
            boolean materialMismatch = primaryItem != null && !primaryItem.equalsIgnoreCase(mat.label());
            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter commissioned. " + blocks.size() + " " + mat.label()
                            + " blocks queued for the builder."));
            player.sendSystemMessage(Component.literal(
                    "Put in your Workers 2 storage area: " + itemLine.toString()
                            + "  (about " + stacks + " stack" + (stacks == 1 ? "" : "s") + ")."));
            if (materialMismatch) {
                player.sendSystemMessage(Component.literal(
                        "Note: the builder needs \"" + primaryItem + "\" for this material, not the block \"" + mat.label() + "\" itself."));
            }
            player.sendSystemMessage(Component.literal(
                    "The builder will wait at the storage area until it has enough. Every stack added lets it place more of the wall."));
            if (skippedColumns > 0) {
                player.sendSystemMessage(Component.literal(
                        skippedColumns + " perimeter columns are more than " + STORAGE_SEARCH_RADIUS
                                + " blocks from that storage area and were left out. Add another storage area closer to them and commission again to finish the wall."));
            }
            FactionLogger.LOG.info("[SiegeOverhaul] Fortify Perimeter: {} blocks, material {}, team {}",
                    blocks.size(), mat.blockId(), coreKey);
            saved.setDirty();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (committed) {
                // The job and payment already succeeded. A later feedback or
                // logging failure must not destroy paid work or report a false
                // purchase failure to the menu.
                saved.setDirty();
                FactionLogger.LOG.warn("[SiegeOverhaul] Fortify Perimeter started, but completion feedback failed", ex);
                return true;
            }
            if (build != null) {
                if (WorkersBridge.releasePlayerJob(builder, build)) {
                    PlayerFortificationJobs.unlink(builder, build.getUUID());
                    build.discard();
                } else {
                    // Workers 2 still points at this area. Preserve both sides
                    // of the association instead of creating a dangling job
                    // reference to a discarded entity.
                    FactionLogger.LOG.warn("[SiegeOverhaul] Could not detach failed Fortify Perimeter job; retaining its build area");
                }
            }
            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter failed to start: " + ex.getMessage()));
            FactionLogger.LOG.warn("[SiegeOverhaul] Fortify Perimeter commission failed", ex);
            return false;
        }
    }

    static boolean safeWallReplacement(BlockState state) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && (state.isAir() || com.devfarinsky.siegeoverhaul.camp.CampVegetation.plant(state));
    }

    /**
     * Keep only the perimeter columns a builder supplied by {@code storage}
     * can reach. Distance is measured horizontally so a storage area on a
     * hillside above or below the wall line still counts.
     */
    static List<BlockPos> withinStorageRange(List<BlockPos> columns, BlockPos storage) {
        List<BlockPos> reachable = new ArrayList<>();
        long limit = (long) STORAGE_SEARCH_RADIUS * STORAGE_SEARCH_RADIUS;
        for (BlockPos base : columns) {
            long dx = base.getX() - storage.getX(), dz = base.getZ() - storage.getZ();
            if (dx * dx + dz * dz <= limit) reachable.add(base);
        }
        return reachable;
    }

    /** Walk claimed chunks. For each boundary edge, drop wall columns at the outward-facing block line. */
    private static void computePerimeter(ServerLevel level, Set<ChunkPos> chunks, int baseY,
                                         List<BlockPos> wallColumns, Set<Long> cornerColumns) {
        Set<Long> seen = new HashSet<>();
        for (ChunkPos chunk : chunks) {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            int maxX = chunk.getMaxBlockX();
            int maxZ = chunk.getMaxBlockZ();
            // Four sides: check the neighbor chunk in each cardinal direction.
            boolean north = !chunks.contains(new ChunkPos(chunk.x, chunk.z - 1));
            boolean south = !chunks.contains(new ChunkPos(chunk.x, chunk.z + 1));
            boolean west  = !chunks.contains(new ChunkPos(chunk.x - 1, chunk.z));
            boolean east  = !chunks.contains(new ChunkPos(chunk.x + 1, chunk.z));

            if (north) for (int x = minX; x <= maxX; x++) addColumn(level, seen, wallColumns, baseY, x, minZ);
            if (south) for (int x = minX; x <= maxX; x++) addColumn(level, seen, wallColumns, baseY, x, maxZ);
            if (west)  for (int z = minZ; z <= maxZ; z++) addColumn(level, seen, wallColumns, baseY, minX, z);
            if (east)  for (int z = minZ; z <= maxZ; z++) addColumn(level, seen, wallColumns, baseY, maxX, z);

            // Corner pillars: only if BOTH adjacent sides are exterior.
            if (north && west)  markCorner(level, cornerColumns, baseY, minX, minZ);
            if (north && east)  markCorner(level, cornerColumns, baseY, maxX, minZ);
            if (south && west)  markCorner(level, cornerColumns, baseY, minX, maxZ);
            if (south && east)  markCorner(level, cornerColumns, baseY, maxX, maxZ);
        }
    }

    /**
     * Count the contiguous replaceable gap below a wall column. A thin but
     * non-replaceable obstruction such as a torch is a hard boundary even
     * when it is not sturdy on its upper face; searching through it would
     * queue wall blocks below the obstruction and create a disconnected
     * foundation that Workers 2 cannot build continuously.
     */
    static int foundationDepth(ServerLevel level, BlockPos base) {
        int filled = 0;
        for (int dy = 1; dy <= FOUNDATION_DEPTH; dy++) {
            BlockPos p = new BlockPos(base.getX(), base.getY() - dy, base.getZ());
            if (!level.hasChunkAt(p)) break;
            BlockState state = level.getBlockState(p);
            // v4.42.0: tree logs count as "sturdy" via isFaceSturdy but
            // are terrain the builder can and should chop through to
            // reach real ground. Otherwise the foundation lands on top
            // of a log with a 6-block gap between it and real dirt.
            boolean tree = state.is(net.minecraft.tags.BlockTags.LOGS)
                    || state.is(net.minecraft.tags.BlockTags.LEAVES)
                    || state.is(net.minecraft.tags.BlockTags.SAPLINGS);
            if (tree) { filled = dy; continue; }
            if (state.isFaceSturdy(level, p, Direction.UP)) break;
            if (!state.isAir() && !state.canBeReplaced()) break;
            filled = dy;
        }
        return filled;
    }

    /** Keep the configured wall height relative to each terrain-adjusted base. */
    static int columnTopY(BlockPos base, boolean corner) {
        return base.getY() + WALL_HEIGHT - 1 + (corner ? CORNER_EXTRA : 0);
    }

    private static void addColumn(ServerLevel level, Set<Long> seen, List<BlockPos> out,
                                  int baseY, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x,baseY,z))) return;
        // Match wall base to actual terrain surface so short cliffs don't leave floating walls.
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // v4.42.0: the vanilla heightmap sits on top of tree logs, so a
        // forested perimeter puts the wall base 6-8 blocks up in the
        // canopy where the foundation dangles in mid-air. Walk down
        // through logs / leaves / saplings to find real ground.
        surface = seeThroughTreesForWall(level, x, z, surface);
        // v4.42.0: expanded the vertical clamp from +/-4 to +/-12 so a
        // perimeter that crosses a real hill or ravine follows the
        // terrain instead of leaving stair-step gaps where the wall
        // hits the clamp ceiling / floor.
        int y = Math.max(baseY - 12, Math.min(baseY + 12, surface));
        BlockPos base = new BlockPos(x, y, z);
        if (seen.add(base.asLong())) out.add(base);
    }

    /**
     * v4.42.0 - walk down from {@code topY} past any log / leaf /
     * sapling blocks until we find real ground. Mirrors the same
     * fix that made camp acceptance forest-aware in v4.40.0.
     */
    private static int seeThroughTreesForWall(ServerLevel level, int x, int z, int topY) {
        for (int dy = 0; dy < 16; dy++) {
            int y = topY - dy;
            BlockPos p = new BlockPos(x, y - 1, z);
            if (!level.hasChunkAt(p)) return topY;
            BlockState state = level.getBlockState(p);
            if (state.is(net.minecraft.tags.BlockTags.LOGS)
                    || state.is(net.minecraft.tags.BlockTags.LEAVES)
                    || state.is(net.minecraft.tags.BlockTags.SAPLINGS)) continue;
            return y;
        }
        return topY;
    }

    private static void markCorner(ServerLevel level, Set<Long> cornerColumns,
                                   int baseY, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x,baseY,z))) return;
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // v4.42.0: same tree see-through + wider clamp as regular columns.
        surface = seeThroughTreesForWall(level, x, z, surface);
        int y = Math.max(baseY - 12, Math.min(baseY + 12, surface));
        cornerColumns.add(new BlockPos(x, y, z).asLong());
    }

    /** Outcome of the builder search: the chosen builder, or the reason there is none. */
    record BuilderSearch(Mob builder, String reason) {}

    /**
     * Pick the builder that will actually do the work.
     *
     * <p>The old search returned whichever builder the entity list happened to
     * yield first, so a second builder standing by the core, a raider camp
     * worker or a builder already halfway through another blueprint could win
     * the job. That is why a commission sometimes built a wall and sometimes
     * silently did nothing. We now take the closest builder that is free, ours
     * and able to work, and tell the player precisely what is in the way when
     * none qualifies.</p>
     */
    static BuilderSearch findNearbyBuilder(ServerLevel level, ServerPlayer player, BlockPos center) {
        AABB area = new AABB(center).inflate(BUILDER_SEARCH_RADIUS);
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean sawBusy = false, sawForeign = false, sawFleeing = false;
        for (Mob m : level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive())) {
            if (!WorkersBridge.isBuilder(m)) continue;
            // Never steal the enemy siege camp's construction crew.
            if (m.getPersistentData().contains(
                    com.devfarinsky.siegeoverhaul.ModConstants.Tags.CAMP_WORKER_TEAM)) {
                sawForeign = true;
                continue;
            }
            UUID owner = WorkersBridge.readWorkerOwner(m);
            if (owner != null && !owner.equals(player.getUUID())) { sawForeign = true; continue; }
            if (m.getPersistentData().hasUUID(
                    com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || WorkersBridge.hasActiveBuildArea(m)) { sawBusy = true; continue; }
            if (WorkersBridge.isFleeing(m)) { sawFleeing = true; continue; }
            double distance = m.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            if (distance < bestDistance) { bestDistance = distance; best = m; }
        }
        if (best != null) return new BuilderSearch(best, "");
        if (sawBusy) return new BuilderSearch(null,
                "Every builder near the core is already working on a build area. "
                        + "Wait for that job to finish or bring another builder.");
        if (sawFleeing) return new BuilderSearch(null,
                "Your builder is fleeing. Make the area safe and commission again.");
        if (sawForeign) return new BuilderSearch(null,
                "The builders near the core belong to someone else. Bring one of your own builders.");
        return new BuilderSearch(null, "No Villager Recruits builder found within "
                + BUILDER_SEARCH_RADIUS + " blocks of the core. Bring a builder closer.");
    }

    /**
     * Find a Workers 2 storagearea entity owned by this player, sitting inside
     * the claim.
     *
     * <p>The area must be alive, owned by the player, inside a claimed chunk
     * and within {@link #STORAGE_SEARCH_RADIUS} blocks of {@code anchor}.
     * The caller supplies the builder's current position to match Workers 2's
     * own runtime search. We prefer areas that already have the BUILDERS bit
     * toggled on because Workers 2's StorageArea.canWorkHere rejects builders
     * on any other type mask.</p>
     */
    private static Entity findPlayerStorageArea(ServerLevel level, ServerPlayer player,
                                                BlockPos anchor, Set<ChunkPos> claim) {
        AABB box = new AABB(anchor).inflate(STORAGE_SEARCH_RADIUS);
        ResourceLocation wanted = new ResourceLocation("workers", "storagearea");
        UUID playerId = player.getUUID();
        Entity fallback = null;
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, ent -> ent.isAlive())) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if (!wanted.equals(id)) continue;
            ChunkPos c = new ChunkPos(e.blockPosition());
            if (!claim.contains(c)) continue;
            UUID areaOwner = WorkersBridge.readOwner(e);
            if (areaOwner == null || !areaOwner.equals(playerId)) continue;
            if (WorkersBridge.hasBuilderStorage(e)) {
                return e;
            }
            if (fallback == null) fallback = e;
        }
        // Fall back to a storagearea without the BUILDERS bit so the caller
        // can emit a specific "turn on Builders" message instead of a generic
        // "nothing found" one.
        return fallback;
    }

    /** Same shape as NativeCampConstruction.blueprint. */
    static CompoundTag blueprint(Map<Long, String> jobs, BlockPos min, BlockPos max) {
        // Workers mirrors local X around the area's eastern origin using this
        // width. Keep the full area bounds even if its eastern cells are already
        // built or protected; shrinking to the remaining jobs shifts every cell.
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", max.getX() - min.getX() + 1);
        tag.putString("facing", "south");
        ListTag list = new ListTag();
        jobs.forEach((key, id) -> {
            BlockPos p = BlockPos.of(key);
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block == null || block == Blocks.AIR) return;
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", p.getX() - min.getX());
            entry.putInt("y", p.getY() - min.getY());
            entry.putInt("z", p.getZ() - min.getZ());
            entry.put("state", NbtUtils.writeBlockState(block.defaultBlockState()));
            list.add(entry);
        });
        tag.put("blocks", list);
        return tag;
    }
}
