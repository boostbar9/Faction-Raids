package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ConstructionResourcesTest extends MinecraftTestSupport {
    private Entity storage(int x,int z,int id) {var e=mock(Entity.class);when(e.blockPosition()).thenReturn(new BlockPos(x,64,z));when(e.getUUID()).thenReturn(new UUID(0,id));return e;}
    private Mob builder(int x,int id) {var b=mock(Mob.class);when(b.blockPosition()).thenReturn(new BlockPos(x,64,24));when(b.getUUID()).thenReturn(new UUID(0,id));when(b.getPersistentData()).thenReturn(new CompoundTag());when(b.getUseItem()).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);return b;}
    @Test void reviewFlaggedNearestBuilderCannotHideAHealthyIdleBuilder() {
        blockedSelection(true,"Builder inventory requires review before another guarded commission.");
    }
    @Test void allReviewFlaggedBuildersExposeTheActualBlockingReason() {
        blockedSelection(false,"Builder inventory requires review before another guarded commission.");
    }
    @Test void aBuilderTemporarilyUsingAnItemDoesNotHideAHealthyIdleBuilder() {
        blockedSelection(true,"Wait until the builder finishes using its item.");
    }
    @Test void activeUseIsReportedWithoutAnInventoryReviewFlag() {
        blockedSelection(false,"Wait until the builder finishes using its item.");
    }
    private void blockedSelection(boolean healthyAvailable,String reason) {
        var level=mock(ServerLevel.class);var player=mock(ServerPlayer.class);UUID owner=UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        var reviewed=builder(24,1);var healthy=builder(24,2);var supply=storage(24,24,3);
        var workers=healthyAvailable?List.of(reviewed,healthy):List.of(reviewed);
        when(level.getEntitiesOfClass(eq(Mob.class),any(),any())).thenReturn(workers);
        when(level.getEntitiesOfClass(eq(Entity.class),any(),any())).thenReturn(List.of(supply));
        try(var bridge=mockStatic(WorkersBridge.class);var guard=mockStatic(NativeConstructionGuard.class);
            var resources=mockStatic(ConstructionResources.class,CALLS_REAL_METHODS)) {
            for(var worker:workers) {
                bridge.when(()->WorkersBridge.isBuilder(worker)).thenReturn(true);
                bridge.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(owner);
            }
            guard.when(()->NativeConstructionGuard.commissionProblem(reviewed)).thenReturn(reason);
            resources.when(()->ConstructionResources.storageArea(supply)).thenReturn(true);
            bridge.when(()->WorkersBridge.readOwner(supply)).thenReturn(owner);
            bridge.when(()->WorkersBridge.hasBuilderStorage(supply)).thenReturn(true);
            var result=ConstructionResources.find(level,player,new BlockPos(24,64,24),
                    Set.of(new ChunkPos(1,1)),List.of(new BlockPos(24,64,24)));
            if(healthyAvailable) {
                assertNull(result.problem());assertSame(healthy,result.builder());assertSame(supply,result.storage());
            } else {
                assertEquals(reason,result.problem());assertNull(result.builder());assertNull(result.storage());
            }
        }
    }
    @Test void deepStorageCannotSupplyAHighSurfaceJobEvenAtIdenticalHorizontalCoordinates() {
        assertFalse(ConstructionResources.covers(List.of(new BlockPos(0,100,0)),new BlockPos(0,0,0)));
        assertTrue(ConstructionResources.covers(List.of(new BlockPos(0,64,0)),new BlockPos(0,0,0)));
        assertFalse(ConstructionResources.covers(List.of(new BlockPos(0,64,0),new BlockPos(0,69,0)),new BlockPos(0,0,0)));
    }
    @Test void firstInsufficientStorageNeverHidesAnotherCompleteSupplier() {
        var level=mock(ServerLevel.class);var player=mock(ServerPlayer.class);UUID owner=UUID.randomUUID();when(player.getUUID()).thenReturn(owner);
        var worker=builder(24,1);var a=storage(0,0,2);var b=storage(24,24,3);var claim=new HashSet<ChunkPos>();
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)claim.add(new ChunkPos(x,z));
        var footprint=List.of(new BlockPos(0,64,0),new BlockPos(47,64,47));
        when(level.getEntitiesOfClass(eq(Mob.class),any(),any())).thenReturn(List.of(worker));
        try(var bridge=mockStatic(WorkersBridge.class);var resources=mockStatic(ConstructionResources.class,CALLS_REAL_METHODS)) {
            bridge.when(()->WorkersBridge.isBuilder(worker)).thenReturn(true);bridge.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(owner);
            for(var s:List.of(a,b)) {resources.when(()->ConstructionResources.storageArea(s)).thenReturn(true);
                bridge.when(()->WorkersBridge.readOwner(s)).thenReturn(owner);bridge.when(()->WorkersBridge.hasBuilderStorage(s)).thenReturn(true);}
            for(var ordering:List.of(List.of(a,b),List.of(b,a))) {
                when(level.getEntitiesOfClass(eq(Entity.class),any(),any())).thenReturn(ordering);
                var result=ConstructionResources.find(level,player,new BlockPos(24,64,24),claim,footprint);
                assertNull(result.problem());assertSame(worker,result.builder());assertSame(b,result.storage());
            }
        }
    }
    @Test void alternateEligibleBuilderCanReachTheOnlyCompleteSupplier() {
        var level=mock(ServerLevel.class);var player=mock(ServerPlayer.class);UUID owner=UUID.randomUUID();when(player.getUUID()).thenReturn(owner);
        var near=builder(8,1);var other=builder(40,2);var supply=storage(75,24,3);
        var claim=new HashSet<ChunkPos>();for(int x=1;x<=4;x++)for(int z=0;z<3;z++)claim.add(new ChunkPos(x,z));
        when(level.getEntitiesOfClass(eq(Mob.class),any(),any())).thenReturn(List.of(near,other));
        when(level.getEntitiesOfClass(eq(Entity.class),any(),any())).thenAnswer(call->{AABB box=call.getArgument(1);
            return box.contains(75.5,64,24.5)?List.of(supply):List.of();});
        try(var bridge=mockStatic(WorkersBridge.class);var resources=mockStatic(ConstructionResources.class,CALLS_REAL_METHODS)) {
            for(var worker:List.of(near,other)) {bridge.when(()->WorkersBridge.isBuilder(worker)).thenReturn(true);bridge.when(()->WorkersBridge.readWorkerOwner(worker)).thenReturn(owner);}
            resources.when(()->ConstructionResources.storageArea(supply)).thenReturn(true);bridge.when(()->WorkersBridge.readOwner(supply)).thenReturn(owner);bridge.when(()->WorkersBridge.hasBuilderStorage(supply)).thenReturn(true);
            var result=ConstructionResources.find(level,player,new BlockPos(24,64,24),claim,List.of(new BlockPos(16,64,0),new BlockPos(79,64,47)));
            assertNull(result.problem());assertSame(other,result.builder());assertSame(supply,result.storage());
        }
    }
}
