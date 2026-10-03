package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.Collection;
import java.util.Set;

/** Exact new-job index plus conservative loaded legacy areas; never confuses a marker with its footprint. */
final class ConstructionReservations {
    private static final int MAX_LOADED_ENTITIES = 16384, MAX_LEGACY_AREAS = 256;
    private ConstructionReservations() {}
    static String problem(ServerLevel level, Collection<BlockPos> cells) {
        if (cells.isEmpty()) return "The construction footprint is empty.";
        if (NativeConstructionGuard.reserves(level, cells)) return "Another protected construction job reserves part of this site.";
        AABB candidate = bounds(cells); int entities = 0, areas = 0;
        Iterable<Entity> loaded = level.getAllEntities();
        if (loaded == null) return "Loaded construction reservations are unavailable.";
        for (Entity area : loaded) {
            if (++entities > MAX_LOADED_ENTITIES) return "Too many loaded entities to verify construction reservations safely.";
            if (!area.isAlive() || !WorkersBridge.isBuildArea(area)) continue;
            if (NativeConstructionGuard.hasReservation(level, area.getUUID())) continue;
            if (++areas > MAX_LEGACY_AREAS) return "Too many loaded legacy build areas to verify this site safely.";
            AABB reserved = legacyBounds(area);
            if (reserved == null) return "A loaded native build area's bounds could not be verified.";
            if (reserved.intersects(candidate) && cells.stream().anyMatch(p -> reserved.intersects(new AABB(p))))
                return "Another native construction job reserves part of this site.";
        }
        return null;
    }
    private static AABB bounds(Collection<BlockPos> cells) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(BlockPos p:cells) {minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());}
        return new AABB(minX,minY,minZ,maxX+1.0,maxY+1.0,maxZ+1.0);
    }
    private static AABB legacyBounds(Entity area) {
        var tag=area.getPersistentData();
        if(tag.contains(PerimeterConstruction.SITE_MIN,Tag.TAG_LONG) && tag.contains(PerimeterConstruction.SITE_MAX,Tag.TAG_LONG))
            return new AABB(BlockPos.of(tag.getLong(PerimeterConstruction.SITE_MIN)),
                    BlockPos.of(tag.getLong(PerimeterConstruction.SITE_MAX)).offset(1,1,1));
        try {
            Object value=area.getClass().getMethod("getArea").invoke(area);
            if(value instanceof AABB box) return new AABB(box.minX,box.minY,box.minZ,box.maxX+1,box.maxY,box.maxZ+1);
        } catch(ReflectiveOperationException|RuntimeException unavailable) { return null; }
        return null;
    }
}
