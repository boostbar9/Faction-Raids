package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ConstructionReservationsTest extends MinecraftTestSupport {
    @Test void farMarkerCannotHideAnOverlappingLoadedLegacyFootprint() {
        var level=mock(ServerLevel.class);var area=mock(Entity.class);var tag=new CompoundTag();
        tag.putLong(PerimeterConstruction.SITE_MIN,new BlockPos(0,64,0).asLong());
        tag.putLong(PerimeterConstruction.SITE_MAX,new BlockPos(47,69,47).asLong());
        when(area.getPersistentData()).thenReturn(tag);when(area.isAlive()).thenReturn(true);
        when(area.getUUID()).thenReturn(UUID.randomUUID());when(area.getBoundingBox()).thenReturn(new AABB(47,64,0,48,66,1));
        when(level.getAllEntities()).thenReturn(List.of(area));
        assertFalse(new AABB(0,64,0,16,70,16).inflate(16).intersects(area.getBoundingBox()),"Old marker query misses this area");
        try(var bridge=mockStatic(WorkersBridge.class);var guard=mockStatic(NativeConstructionGuard.class)) {
            bridge.when(()->WorkersBridge.isBuildArea(area)).thenReturn(true);
            assertNotNull(ConstructionReservations.problem(level,List.of(new BlockPos(2,64,2))));
            verify(level,never()).getEntitiesOfClass(any(),any(),any());
        }
    }
    @Test void durableIndexProtectsUnloadedJobsWithoutLoadingTheirMarkers() {
        var level=mock(ServerLevel.class);var cells=List.of(new BlockPos(2,64,2));
        try(var guard=mockStatic(NativeConstructionGuard.class)) {
            guard.when(()->NativeConstructionGuard.reserves(level,cells)).thenReturn(true);
            assertNotNull(ConstructionReservations.problem(level,cells));verify(level,never()).getAllEntities();
        }
    }
    @Test void indexedHollowRingDoesNotReserveItsEmptyInteriorBoundingBox() {
        var level=mock(ServerLevel.class);var area=mock(Entity.class);UUID id=UUID.randomUUID();
        when(area.getUUID()).thenReturn(id);when(area.isAlive()).thenReturn(true);when(level.getAllEntities()).thenReturn(List.of(area));
        try(var guard=mockStatic(NativeConstructionGuard.class);var bridge=mockStatic(WorkersBridge.class)) {
            bridge.when(()->WorkersBridge.isBuildArea(area)).thenReturn(true);
            guard.when(()->NativeConstructionGuard.hasReservation(level,id)).thenReturn(true);
            assertNull(ConstructionReservations.problem(level,List.of(new BlockPos(8,64,8))));
            verify(area,never()).getPersistentData();
        }
    }
}
