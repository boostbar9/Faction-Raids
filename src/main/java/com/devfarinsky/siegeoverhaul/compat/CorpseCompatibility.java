package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Collection;

/** Optional Corpse integration: protect recovery space without touching death inventories. */
public final class CorpseCompatibility {
    private static final ResourceLocation CORPSE = new ResourceLocation("corpse", "corpse");
    private CorpseCompatibility() {}
    public static boolean available() { return ForgeRegistries.ENTITY_TYPES.containsKey(CORPSE); }
    public static boolean isCorpse(Entity entity) {
        return !entity.isRemoved() && CORPSE.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }
    public static boolean blocksAt(ServerLevel level, BlockPos pos) {
        return available() && !level.getEntities((Entity)null, new AABB(pos), CorpseCompatibility::isCorpse).isEmpty();
    }
    /** One entity query per blueprint check, not one query per job per worker tick. */
    public static boolean blocksAny(ServerLevel level, Collection<Long> positions) {
        if (!available() || positions.isEmpty()) return false;
        AABB bounds=null;
        for (long packed:positions) {
            AABB cell=new AABB(BlockPos.of(packed));
            bounds=bounds==null?cell:bounds.minmax(cell);
        }
        var corpses=level.getEntities((Entity)null,bounds,CorpseCompatibility::isCorpse);
        if(corpses.isEmpty())return false;
        for(long packed:positions)for(Entity corpse:corpses)
            if(corpse.getBoundingBox().intersects(new AABB(BlockPos.of(packed))))return true;
        return false;
    }
}
