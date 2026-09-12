package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
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
 * blueprint of walls (3 tall) with 5-tall corner pillars, hands the job to
 * a nearby Workers 2 builder, and drops a barrel at the builder's feet that
 * the player fills with the chosen material. The builder consumes the barrel
 * and walks the perimeter placing blocks. When it runs out the barrel just
 * sits waiting for the player to top it up.</p>
 *
 * <p>Only the commission is charged in emeralds. Every block itself is
 * supplied by the player.</p>
 */
public final class TerritoryFortification {

    public static final int PRICE = 1200;
    /** Height of the wall segments. */
    public static final int WALL_HEIGHT = 3;
    /** Additional pillar height above the wall at chunk corners. */
    public static final int CORNER_EXTRA = 2;
    /** How far from the core we search for the commissioned builder. */
    public static final int BUILDER_SEARCH_RADIUS = 16;

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

        // Bounds for the build area entity: expand vertically to cover pillar tops.
        BlockPos min = null, max = null;
        int wallTop = baseY + WALL_HEIGHT - 1;
        int pillarTop = baseY + WALL_HEIGHT + CORNER_EXTRA - 1;
        for (BlockPos base : wallColumns) {
            int top = cornerColumns.contains(base.asLong()) ? pillarTop : wallTop;
            for (int y = baseY; y <= top; y++) {
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
            for (int y = baseY; y <= top; y++) {
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

        // Place the supply barrel next to the builder.
        BlockPos supply = findSupplySpot(level, builder.blockPosition());
        if (supply == null) {
            player.sendSystemMessage(Component.literal(
                    "No open air block next to your builder to drop a supply barrel."));
            return false;
        }

        Entity build = null;
        Entity storage = null;
        UUID owner = UUID.randomUUID();
        try {
            build = WorkersBridge.createArea(level, "buildarea",
                    new BlockPos(max.getX(), min.getY(), min.getZ()), owner,
                    max.getX() - min.getX() + 1,
                    max.getZ() - min.getZ() + 1,
                    max.getY() - min.getY() + 1);
            CompoundTag blueprint = blueprint(blocks, min);
            WorkersBridge.startBlueprint(build, blueprint);

            // Storage area over the barrel.
            storage = WorkersBridge.createArea(level, "storagearea", supply, owner, 1, 1, 1);

            // Place the barrel. Leave it empty - the player fills it.
            if (!level.getBlockState(supply).isAir() && !level.getBlockState(supply).canBeReplaced()) {
                throw new IllegalStateException("Supply spot no longer air");
            }
            if (!level.setBlock(supply, Blocks.BARREL.defaultBlockState(), 3)) {
                throw new IllegalStateException("Cannot place supply barrel");
            }
            if (level.getBlockEntity(supply) != null) {
                level.getBlockEntity(supply).getPersistentData().putUUID(
                        com.devfarinsky.siegeoverhaul.ModConstants.Tags.CAMP_SUPPLY_OWNER, owner);
                level.getBlockEntity(supply).setChanged();
            }

            // Register both work areas.
            for (Entity area : List.of(build, storage)) {
                area.getPersistentData().putString(
                        com.devfarinsky.siegeoverhaul.ModConstants.Tags.CAMP_AREA_TEAM,
                        coreKey);
                if (!level.addFreshEntity(area)) {
                    throw new IllegalStateException("Cannot register work area entity");
                }
            }

            // Hand the job to the builder. enableNative already installs the work-shift helper.
            WorkersBridge.enableNative(builder, owner, false);

            // Charge only after every mutating step succeeded.
            if (!player.isCreative() && !PaymentSource.consume(player, PRICE)) {
                throw new IllegalStateException("Payment rejected");
            }

            player.sendSystemMessage(Component.literal(
                    "Fortify Perimeter commissioned: " + blocks.size() + " " + mat.label()
                            + " blocks queued. Fill the supply barrel and the builder starts work."));
            FactionLogger.LOG.info("[SiegeOverhaul] Fortify Perimeter: {} blocks, material {}, team {}",
                    blocks.size(), mat.blockId(), coreKey);
            saved.setDirty();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (build != null) build.discard();
            if (storage != null) storage.discard();
            if (level.getBlockState(supply).is(Blocks.BARREL)
                    && level.getBlockEntity(supply) != null
                    && level.getBlockEntity(supply).getPersistentData().hasUUID(
                            com.devfarinsky.siegeoverhaul.ModConstants.Tags.CAMP_SUPPLY_OWNER)) {
                if (level.getBlockEntity(supply) instanceof Container c) c.clearContent();
                level.setBlock(supply, Blocks.AIR.defaultBlockState(), 3);
            }
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

    private static BlockPos findSupplySpot(ServerLevel level, BlockPos anchor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos p = anchor.offset(dx, 0, dz);
                if (!level.hasChunkAt(p)) continue;
                BlockState above = level.getBlockState(p);
                BlockState below = level.getBlockState(p.below());
                if ((above.isAir() || above.canBeReplaced())
                        && below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                    return p;
                }
            }
        }
        return null;
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
