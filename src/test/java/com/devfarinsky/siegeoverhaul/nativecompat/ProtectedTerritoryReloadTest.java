package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProtectedTerritoryReloadTest extends MinecraftTestSupport {
    private record Fixture(ServerLevel level,ProtectedBuildArea area,ConstructionEditLedger ledger) {}
    private Fixture fixture() {
        var level=mock(ServerLevel.class);var area=mock(ProtectedBuildArea.class);var data=new CompoundTag();
        when(area.level()).thenReturn(level);when(area.getPersistentData()).thenReturn(data);
        when(area.getUUID()).thenReturn(UUID.randomUUID());when(level.hasChunkAt(any())).thenReturn(true);
        var blueprint=AcceptedConstructionPlanTest.blueprint(1,0,0,0,Blocks.COBBLESTONE.defaultBlockState());
        var plan=AcceptedConstructionPlan.decode(BlockPos.ZERO,Direction.SOUTH,1,1,1,blueprint);
        when(area.getOriginPos()).thenReturn(BlockPos.ZERO);when(area.getFacing()).thenReturn(Direction.SOUTH);
        when(area.getWidthSize()).thenReturn(1);when(area.getHeightSize()).thenReturn(1);when(area.getDepthSize()).thenReturn(1);
        when(area.getStructureNBT()).thenReturn(blueprint);when(area.getFreeArea()).thenReturn(false);
        CompoundTag saved=plan.save();saved.putUUID("Owner",UUID.randomUUID());saved.putUUID("Builder",UUID.randomUUID());
        saved.putString("CoreKey","team:blue");saved.putLong("CorePos",0);
        saved.putLongArray("Completed",new long[0]);saved.putLongArray("Cleared",new long[0]);
        var before=new ListTag();
        for(BlockPos pos:plan.cells.keySet()) {
            var cell=new CompoundTag();cell.putLong("Pos",pos.asLong());cell.put("State",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));before.add(cell);
        }
        saved.put("Before",before);saved.put("Reservation",AcceptedConstructionReservation.capture(level,plan,plan.cells.keySet()).save());
        data.put("SiegeProtectedConstructionV1",saved);
        var ledger=new ConstructionEditLedger();assertTrue(ledger.register(area.getUUID(),plan.cells.keySet()));
        return new Fixture(level,area,ledger);
    }
    @Test void changedSavedTerritoryCannotRebuildQueuesWhileOwnerIsOffline() {
        var f=fixture();var a=new ChunkPos(0,0);var b=new ChunkPos(1,0);
        var accepted=new RecruitsClaimsBridge.TerritorySnapshot("blue",Set.of(a),null);
        PerimeterTerritory.remember(f.area,accepted);
        try(var runtime=mockStatic(WorkersConstructionRuntime.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var claims=mockStatic(RecruitsClaimsBridge.class)) {
            ledgers.when(()->ConstructionEditLedger.get(f.level)).thenReturn(f.ledger);
            claims.when(()->RecruitsClaimsBridge.getFactionTerritory(f.level,"blue",4096))
                    .thenReturn(new RecruitsClaimsBridge.TerritorySnapshot("blue",Set.of(a,b),null));
            assertFalse(NativeConstructionGuard.prepareLoadedArea(f.area));verify(f.area,never()).rebuildAcceptedQueues();
            assertTrue(NativeConstructionGuard.status(f.area).contains("territory changed"));
            claims.when(()->RecruitsClaimsBridge.getFactionTerritory(f.level,"blue",4096)).thenReturn(accepted);
            assertTrue(NativeConstructionGuard.prepareLoadedArea(f.area));verify(f.area).rebuildAcceptedQueues();
            verify(f.level,never()).getServer(); // No online-owner/player lookup is part of this load contract.
        }
    }
    @Test void untrackedManualLoadDoesNotAcquireANewTerritoryDependency() {
        var f=fixture();
        try(var runtime=mockStatic(WorkersConstructionRuntime.class);var ledgers=mockStatic(ConstructionEditLedger.class);
            var claims=mockStatic(RecruitsClaimsBridge.class)) {
            ledgers.when(()->ConstructionEditLedger.get(f.level)).thenReturn(f.ledger);
            assertTrue(NativeConstructionGuard.prepareLoadedArea(f.area));verify(f.area).rebuildAcceptedQueues();
            claims.verifyNoInteractions();verify(f.level,never()).getServer();
        }
    }
}
