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
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class PerimeterHandoffTest extends MinecraftTestSupport {
    @Test void verifiedNativeHandoffPrecedesSingleTreasuryChargeAndRemoteReviewNeverTeleportsBuilder() throws Exception {check(true,true,true,true);}
    @Test void builderRefusalDoesNotChargeAndUsesUnpaidCleanup() throws Exception {check(false,true,true,false);}
    @Test void rejectedPaymentNeverActivatesTheNativeJob() throws Exception {check(true,false,true,false);}
    @Test void failedDetachRetainsUnpaidAreaRatherThanLeavingDanglingNativePointer() throws Exception {check(true,false,false,false);}
    private void check(boolean accepts,boolean pays,boolean detaches,boolean expected) throws Exception {
        var level=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var builder=mock(Mob.class);var area=mock(Entity.class);
        UUID owner=UUID.randomUUID(),builderId=UUID.randomUUID(),areaId=UUID.randomUUID();
        when(player.serverLevel()).thenReturn(level);when(player.getUUID()).thenReturn(owner);
        when(builder.getUUID()).thenReturn(builderId);when(builder.getPersistentData()).thenReturn(new CompoundTag());
        when(area.getUUID()).thenReturn(areaId);when(area.getPersistentData()).thenReturn(new CompoundTag());
        when(area.blockPosition()).thenReturn(new BlockPos(7,64,7));when(level.addFreshEntity(area)).thenReturn(true);
        // This used to move the validated builder outside its supply range.
        when(builder.distanceToSqr(player)).thenReturn(40000D);
        var plan=PerimeterBlueprint.create(Set.of(new ChunkPos(0,0)),(x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE);
        var prepared=new PerimeterConstruction.Preparation(builder,plan,"claim",null);var order=new ArrayList<String>();
        try(var bridge=mockStatic(WorkersBridge.class);var guard=mockStatic(NativeConstructionGuard.class);
            var payment=mockStatic(PaymentSource.class);var access=mockStatic(WallBuilderAccess.class)) {
            bridge.when(()->WorkersBridge.createProtectedPlayerArea(eq(player),eq(builder),any(),anyInt(),anyInt(),anyInt(),any())).thenReturn(area);
            bridge.when(()->WorkersBridge.startBlueprint(eq(area),any())).thenAnswer(call->{order.add("blueprint");return null;});
            guard.when(()->NativeConstructionGuard.protect(player,builder,area)).thenAnswer(call->{order.add("protected");return true;});
            bridge.when(()->WorkersBridge.assignBuildAreaDirectly(builder,area)).thenAnswer(call->{order.add("assigned");return accepts;});
            payment.when(()->PaymentSource.consume(player,900)).thenAnswer(call->{assertEquals(List.of("blueprint","protected","assigned"),order);order.add("paid");return pays;});
            guard.when(()->NativeConstructionGuard.activate(area)).thenAnswer(call->{assertEquals("paid",order.get(order.size()-1));order.add("activated");return true;});
            bridge.when(()->WorkersBridge.releasePlayerJob(builder,area)).thenReturn(detaches);
            bridge.when(()->WorkersBridge.discardPlayerArea(area)).thenAnswer(call->{area.discard();return true;});
            assertEquals(expected,PerimeterConstruction.startJob(player,prepared,1));
            payment.verify(()->PaymentSource.consume(player,900),accepts?times(1):never());
            guard.verify(()->NativeConstructionGuard.activate(area),expected?times(1):never());
            bridge.verify(()->WorkersBridge.teleportBuilderNear(any(),any(),any()),never());
            bridge.verify(()->WorkersBridge.discardPlayerArea(area),!expected&&detaches?times(1):never());
            if(!expected&&!detaches)assertTrue(builder.getPersistentData().hasUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            verify(builder,never()).setItemSlot(any(),any());
        }
    }
}
