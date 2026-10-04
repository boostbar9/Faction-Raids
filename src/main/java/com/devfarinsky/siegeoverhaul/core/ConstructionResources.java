package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Read-only selection of an idle hire and complete coverage by owned native storage areas. */
final class ConstructionResources {
    record Selection(Mob builder, Entity storage, String problem) {}
    private ConstructionResources() {}
    static Selection find(ServerLevel level, ServerPlayer player, BlockPos center, Set<ChunkPos> claim, List<BlockPos> footprint) {
        List<Mob> builders = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(16), Entity::isAlive));
        builders.removeIf(b -> !WorkersBridge.isBuilder(b) || !player.getUUID().equals(WorkersBridge.readWorkerOwner(b))
                || b.getPersistentData().contains(ModConstants.Tags.CAMP_WORKER_TEAM)
                || b.getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                || WorkersBridge.hasActiveBuildArea(b) || WorkersBridge.isFleeing(b));
        builders.sort(Comparator.comparingDouble((Mob b) -> b.distanceToSqr(center.getX()+.5,center.getY(),center.getZ()+.5))
                .thenComparing(Entity::getUUID));
        String inventoryProblem = builders.stream().map(NativeConstructionGuard::commissionProblem)
                .filter(Objects::nonNull).findFirst().orElse(null);
        builders.removeIf(builder -> NativeConstructionGuard.commissionProblem(builder) != null);
        if (builders.isEmpty()) return new Selection(null,null,inventoryProblem != null ? inventoryProblem
                : TerritoryFortification.findNearbyBuilder(level,player,center,true).reason());
        if (footprint.isEmpty()) return new Selection(null, null, "The complete footprint is unavailable.");
        List<Entity> storage = new ArrayList<>(level.getEntitiesOfClass(Entity.class, supplyBounds(center, footprint), Entity::isAlive));
        storage.removeIf(s -> !storageArea(s) || !player.getUUID().equals(WorkersBridge.readOwner(s))
                || !claim.contains(new ChunkPos(s.blockPosition())) || !WorkersBridge.hasBuilderStorage(s));
        if (storage.size() > 256) return new Selection(null, null, "Too many storage markers to verify the complete perimeter supply safely.");
        for (Mob builder : builders) {
            List<Entity> initial = storage.stream().filter(s -> nativeRange(builder.blockPosition(), s.blockPosition()))
                    .sorted(Comparator.comparingDouble((Entity s) -> s.distanceToSqr(builder)).thenComparing(Entity::getUUID)).toList();
            // Preserve the deterministic one-supplier choice when available.
            for (Entity area : initial) if (covers(footprint, area.blockPosition())) return new Selection(builder, area, null);
            if (!initial.isEmpty() && coversAll(footprint, storage.stream().map(Entity::blockPosition).toList()))
                return new Selection(builder, initial.get(0), null);
        }
        return new Selection(null, null, "Owned, Builders-enabled storage must cover the entire perimeter and an idle hired builder near the core. Place supply markers inside faction territory within 64 blocks of every planned section; no partial job is created.");
    }

    /** Native area scans are a 64-block AABB around the moving worker, not a single fixed core supplier. */
    private static boolean nativeRange(BlockPos worker, BlockPos storage) {
        return Math.abs((long) worker.getX() - storage.getX()) <= 64
                && Math.abs((long) worker.getY() - storage.getY()) <= 64
                && Math.abs((long) worker.getZ() - storage.getZ()) <= 64;
    }

    private static AABB supplyBounds(BlockPos center, List<BlockPos> footprint) {
        int minX = center.getX(), minY = center.getY(), minZ = center.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        for (BlockPos p : footprint) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0).inflate(64);
    }

    static boolean covers(List<BlockPos> footprint, BlockPos storage) {
        return coversAll(footprint, List.of(storage));
    }

    static boolean coversAll(List<BlockPos> footprint, List<BlockPos> storage) {
        // The conservative horizontal circle fits entirely within Workers' native scan AABB.
        return !footprint.isEmpty() && footprint.stream().allMatch(p -> storage.stream().anyMatch(s -> {
            long dx = (long) p.getX() - s.getX(), dz = (long) p.getZ() - s.getZ();
            return Math.abs(dx) <= 64 && Math.abs(dz) <= 64 && dx * dx + dz * dz <= 64L * 64
                    && Math.abs((long) p.getY() - s.getY()) <= 64;
        }));
    }
    static boolean storageArea(Entity entity) {
        return entity.getType()!=null && new ResourceLocation("workers","storagearea").equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }
}
