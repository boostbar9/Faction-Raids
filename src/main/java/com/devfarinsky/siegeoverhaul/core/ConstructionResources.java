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

/** Read-only deterministic selection of a hired idle builder and a storage area that can supply the whole plan. */
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
        for (Mob builder : builders) {
            List<Entity> storage = new ArrayList<>(level.getEntitiesOfClass(Entity.class, new AABB(builder.blockPosition()).inflate(64), Entity::isAlive));
            storage.removeIf(s -> !storageArea(s) || !player.getUUID().equals(WorkersBridge.readOwner(s))
                    || !claim.contains(new ChunkPos(s.blockPosition())) || !WorkersBridge.hasBuilderStorage(s));
            storage.sort(Comparator.comparingDouble((Entity s) -> s.distanceToSqr(builder)).thenComparing(Entity::getUUID));
            for (Entity area : storage) if (covers(footprint,area.blockPosition())) return new Selection(builder,area,null);
        }
        return new Selection(null,null,"No owned, Builders-enabled storage can supply this entire plan from an idle hired builder near the core. Keep storage inside the claim and within 64 blocks of the whole footprint and builder.");
    }
    static boolean covers(List<BlockPos> footprint, BlockPos storage) {
        return !footprint.isEmpty() && TerritoryFortification.withinStorageRange(footprint,storage).size()==footprint.size()
                && footprint.stream().allMatch(p -> Math.abs((long)p.getY()-storage.getY())<=64);
    }
    static boolean storageArea(Entity entity) {
        return entity.getType()!=null && new ResourceLocation("workers","storagearea").equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }
}
