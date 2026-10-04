package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativePerimeterProjects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerimeterHandoffTest extends MinecraftTestSupport {
    private final UUID owner=UUID.randomUUID(),builderId=UUID.randomUUID();
    private final BlockPos core=new BlockPos(8,64,8);
    private final ServerPlayer player=mock(ServerPlayer.class);
    private final Mob builder=mock(Mob.class);
    private PerimeterConstruction.Preparation prepared() {
        when(player.getUUID()).thenReturn(owner);when(builder.getUUID()).thenReturn(builderId);
        var plan=PerimeterBlueprint.create(Set.of(new ChunkPos(0,0)),(x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE);
        var layout=PerimeterStageLayout.partition(plan,p->p.targets().size()<=500?null:"Focused test section bound");
        Map<Long,BlockState> before=new HashMap<>(),clear=new HashMap<>();
        plan.blocks().keySet().forEach(p->before.put(p,Blocks.AIR.defaultBlockState()));
        plan.clearance().forEach(p->clear.put(p,Blocks.AIR.defaultBlockState()));
        String hash=PerimeterReviewFingerprint.create(plan,layout,before,clear,core,1,"team:blue:[0]",owner,builderId);
        var quote=new PerimeterConstruction.Quote(core,1,owner,builderId,layout,before,clear,hash);
        return new PerimeterConstruction.Preparation(builder,plan,"team:blue:[0]",null,
                new RecruitsClaimsBridge.TerritorySnapshot("blue",Set.of(new ChunkPos(0,0)),null),quote);
    }
    @Test void exactCompleteQuoteGoesOnlyToOneWholeProjectHandoffWithoutLegacyPaymentOrTeleport() {
        var p=prepared();var q=p.quote();
        try(var projects=mockStatic(NativePerimeterProjects.class);var payment=mockStatic(PaymentSource.class);var bridge=mockStatic(WorkersBridge.class)) {
            projects.when(()->NativePerimeterProjects.start(player,builder,core,1,p.plan(),q.layout(),q.before(),q.clearance(),p.territory(),q.fingerprint())).thenReturn(true);
            assertTrue(PerimeterConstruction.startJob(player,p,1));
            projects.verify(()->NativePerimeterProjects.start(player,builder,core,1,p.plan(),q.layout(),q.before(),q.clearance(),p.territory(),q.fingerprint()),times(1));
            payment.verifyNoInteractions();bridge.verifyNoInteractions();verify(builder,never()).setItemSlot(any(),any());
        }
    }
    @Test void LegacyOrIncompleteQuoteCannotStartAnUnstagedNewCommission() {
        var p=prepared();var missing=new PerimeterConstruction.Preparation(builder,p.plan(),p.claimIdentity(),null,p.territory());
        try(var projects=mockStatic(NativePerimeterProjects.class)) {
            assertFalse(PerimeterConstruction.startJob(player,missing,1));projects.verifyNoInteractions();
        }
    }
    @Test void changedOwnerBuilderMaterialOrOriginalStateNeverReachesNativeAdmission() {
        var p=prepared();var q=p.quote();
        try(var projects=mockStatic(NativePerimeterProjects.class)) {
            assertFalse(PerimeterConstruction.startJob(player,p,2));
            when(player.getUUID()).thenReturn(UUID.randomUUID());assertFalse(PerimeterConstruction.startJob(player,p,1));
            when(player.getUUID()).thenReturn(owner);when(builder.getUUID()).thenReturn(UUID.randomUUID());assertFalse(PerimeterConstruction.startJob(player,p,1));
            when(builder.getUUID()).thenReturn(builderId);var before=new HashMap<>(q.before());before.put(before.keySet().iterator().next(),Blocks.DANDELION.defaultBlockState());
            var changed=new PerimeterConstruction.Preparation(builder,p.plan(),p.claimIdentity(),null,p.territory(),
                    new PerimeterConstruction.Quote(core,1,owner,builderId,q.layout(),before,q.clearance(),q.fingerprint()));
            assertFalse(PerimeterConstruction.startJob(player,changed,1));projects.verifyNoInteractions();
        }
    }
    @Test void rejectedWholeProjectHandoffIsNotReportedAsSuccessful() {
        var p=prepared();try(var projects=mockStatic(NativePerimeterProjects.class)) {
            assertFalse(PerimeterConstruction.startJob(player,p,1));
        }
    }
}
