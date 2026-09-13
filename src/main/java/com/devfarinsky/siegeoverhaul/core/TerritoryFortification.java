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

        Mob builder = findNearbyBuilder(level, corePos);
        if (builder == null) {
            player.sendSystemMessage(Component.literal(
                    "No Villager Recruits builder found within " + BUILDER_SEARCH_RADIUS
                            + " blocks of the core. Bring a builder closer."));
            return false;
        }

        // The player must have set up a Workers 2 storagearea inside their
        // claim already, owned by them. We reuse it instead of creating one.
        // Anchor the search on the core, not the builder, because Workers 2
        // searches from the builder's current position (which moves) and the
        // core is the stable centre of the perimeter. This keeps the initial
        // check in sync with where the builder will spend most of its time.
        Entity playerStorage = findPlayerStorageArea(level, player, corePos, chunks);
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
                    "You need " + PRICE + " emeralds (bank + inventory) to commission the wall."));
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
        if (wallColumns.size() > MAX_PERIMETER_BLOCKS) {
            player.sendSystemMessage(Component.literal(
                    "Your claim perimeter is too long to fortify in one job ("
                            + wallColumns.size() + " > " + MAX_PERIMETER_BLOCKS + " blocks)."));
            return false;
        }

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
            int filled = 0;
            for (int dy = 1; dy <= FOUNDATION_DEPTH; dy++) {
                BlockPos p = new BlockPos(base.getX(), base.getY() - dy, base.getZ());
                if (!level.hasChunkAt(p)) break;
                BlockState state = level.getBlockState(p);
                if (state.isFaceSturdy(level, p, Direction.UP)) break;
                filled = dy;
            }
            if (filled > 0) foundationBelow.put(base, filled);
        }

        // Bounds for the build area entity: expand vertically to cover pillar tops.
        BlockPos min = null, max = null;
        int wallTop = baseY + WALL_HEIGHT - 1;
        int pillarTop = baseY + WALL_HEIGHT + CORNER_EXTRA - 1;
        for (BlockPos base : wallColumns) {
            int top = cornerColumns.contains(base.asLong()) ? pillarTop : wallTop;
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
            int top = cornerColumns.contains(base.asLong()) ? pillarTop : wallTop;
            int bottom = base.getY() - foundationBelow.getOrDefault(base, 0);
            for (int y = bottom; y <= top; y++) {
                BlockPos p = new BlockPos(base.getX(), y, base.getZ());
                if (!level.hasChunkAt(p)) continue;
                BlockState existing = level.getBlockState(p);
                // Never overwrite existing solid blocks the player placed.
                if (!existing.isAir() && !existing.canBeReplaced()) continue;
                blocks.put(p.asLong(), mat.blockId());
            }
        }
        if (blocks.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "The perimeter is already covered by existing blocks. Nothing to place."));
            return false;
        }

        Entity build = null;
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

            build.getPersistentData().putString(
                    com.devfarinsky.siegeoverhaul.ModConstants.Tags.CAMP_AREA_TEAM,
                    coreKey);

            // Spawn the buildarea into the level BEFORE calling setStartBuild.
            // setStartBuild reads world block state at each target position to
            // decide which blocks belong in stackToPlace, and Workers 2's
            // built-in flow always spawns the area first (via item placement)
            // and only then wires up the blueprint through its GUI.
            if (!level.addFreshEntity(build)) {
                throw new IllegalStateException("Cannot register buildarea entity");
            }
            CompoundTag blueprint = blueprint(blocks, min);
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

            // Charge only after every mutating step succeeded.
            if (!player.isCreative() && !PaymentSource.consume(player, PRICE)) {
                throw new IllegalStateException("Payment rejected");
            }

            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter commissioned: " + blocks.size() + " " + mat.label()
                            + " blocks queued. Put " + totalRequired + " x " + mat.label()
                            + " in your Workers 2 storage area and the builder starts work."));
            FactionLogger.LOG.info("[SiegeOverhaul] Fortify Perimeter: {} blocks, material {}, team {}",
                    blocks.size(), mat.blockId(), coreKey);
            saved.setDirty();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (build != null) build.discard();
            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter failed to start: " + ex.getMessage()));
            FactionLogger.LOG.warn("[SiegeOverhaul] Fortify Perimeter commission failed", ex);
            return false;
        }
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

    private static void addColumn(ServerLevel level, Set<Long> seen, List<BlockPos> out,
                                  int baseY, int x, int z) {
        // Match wall base to actual terrain surface so short cliffs don't leave floating walls.
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = Math.max(baseY - 4, Math.min(baseY + 4, surface));
        BlockPos base = new BlockPos(x, y, z);
        if (seen.add(base.asLong())) out.add(base);
    }

    private static void markCorner(ServerLevel level, Set<Long> cornerColumns,
                                   int baseY, int x, int z) {
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = Math.max(baseY - 4, Math.min(baseY + 4, surface));
        cornerColumns.add(new BlockPos(x, y, z).asLong());
    }

    private static Mob findNearbyBuilder(ServerLevel level, BlockPos center) {
        AABB area = new AABB(center).inflate(BUILDER_SEARCH_RADIUS);
        // Any Villager Recruits Builder counts. We identify by entity registry id.
        ResourceLocation wanted = new ResourceLocation("workers", "builder");
        for (Mob m : level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive())) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
            if (wanted.equals(id)) return m;
        }
        return null;
    }

    /**
     * Find a Workers 2 storagearea entity owned by this player, sitting inside
     * the claim.
     *
     * <p>The area must be alive, owned by the player, inside a claimed chunk
     * and within {@link #STORAGE_SEARCH_RADIUS} blocks of {@code anchor}
     * (typically the core so the storage sits near the middle of the
     * perimeter). We prefer areas that already have the BUILDERS bit toggled
     * on because Workers 2's StorageArea.canWorkHere rejects builders on any
     * other type mask.</p>
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
    private static CompoundTag blueprint(Map<Long, String> jobs, BlockPos min) {
        // Recompute bounds against the min we already computed for the buildarea.
        int maxX = min.getX(), maxZ = min.getZ();
        for (long key : jobs.keySet()) {
            BlockPos p = BlockPos.of(key);
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", maxX - min.getX() + 1);
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
