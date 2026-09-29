package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreCiviliansTest extends MinecraftTestSupport {
    @Test void claimedCiviliansCannotTakePortalsButOrdinaryVillagersCan() {
        Villager v=mock(Villager.class);var tag=new CompoundTag();when(v.getPersistentData()).thenReturn(tag);
        var ordinary=new EntityTravelToDimensionEvent(v,Level.NETHER);CoreCivilians.dimension(ordinary);assertFalse(ordinary.isCanceled());
        tag.putString("SiegeCivilianFaction","team:test");
        var owned=new EntityTravelToDimensionEvent(v,Level.NETHER);CoreCivilians.dimension(owned);assertTrue(owned.isCanceled());
    }
    @Test void removalStopsTaxesButChunkUnloadingRetainsResidents() {
        ServerLevel level=mock(ServerLevel.class);MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        var data=new RaidSavedData();Villager v=mock(Villager.class);UUID id=UUID.randomUUID();when(v.getUUID()).thenReturn(id);
        var tag=new CompoundTag();tag.putString("SiegeCivilianFaction","team:test");when(v.getPersistentData()).thenReturn(tag);
        var ledger=CoreCivilians.ledger(data,"team:test");CivilianLedger.register(ledger,id,0);
        try(var saved=mockStatic(RaidSavedData.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(data);
            when(v.getRemovalReason()).thenReturn(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            CoreCivilians.removed(new EntityLeaveLevelEvent(v,level));assertEquals(1,CivilianLedger.count(ledger));
            when(v.getRemovalReason()).thenReturn(Entity.RemovalReason.KILLED);
            CoreCivilians.removed(new EntityLeaveLevelEvent(v,level));assertEquals(0,CivilianLedger.count(ledger));
        }
    }
    @Test void removingCorePersistsTaxSuspensionWithoutDeletingTreasuryOrGrants() {
        ServerLevel level=mock(ServerLevel.class);when(level.dimension()).thenReturn(Level.OVERWORLD);
        MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        var data=new RaidSavedData();var core=new CompoundTag();core.putLong("Position",BlockPos.ZERO.asLong());core.putLong("BankEmeralds",42);
        data.siegeCores.put("team:test",core);CoreCivilians.ledger(data,"team:test").putInt("Starters",2);
        try(var saved=mockStatic(RaidSavedData.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(data);
            CoreCivilians.coreRemoved(level,BlockPos.ZERO);
            assertTrue(core.getBoolean("CoreRemoved"));assertEquals(42,core.getLong("BankEmeralds"));
            var savedTag=data.save(new CompoundTag());
            assertTrue(savedTag.getCompound("SiegeCores").getCompound("team:test").getBoolean("CoreRemoved"));
            assertEquals(2,savedTag.getCompound("CivilianFactions").getCompound("team:test").getInt("Starters"));
        }
    }
    @Test void lostClaimRecoverySearchesOnlyValidatedSitesAroundCurrentCore() {
        ServerLevel level=mock(ServerLevel.class);MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        Villager v=mock(Villager.class);var data=new RaidSavedData();var core=new CompoundTag();core.putLong("Position",new BlockPos(100,64,100).asLong());
        data.siegeCores.put("team:test",core);BlockPos site=new BlockPos(102,64,100);
        try(var saved=mockStatic(RaidSavedData.class);var claims=mockStatic(SiegeCore.class);var placement=mockStatic(HirePlacement.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(data);
            assertNull(CoreCivilians.recovery(level,v,"team:test"));
            claims.when(()->SiegeCore.claimed(level,site,"team:test")).thenReturn(true);
            placement.when(()->HirePlacement.safe(eq(level),eq(v),eq(site),any())).thenReturn(true);
            assertEquals(site,CoreCivilians.recovery(level,v,"team:test"));
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @Test void crossingClaimBoundaryReturnsToValidatedLastSafeGround() {
        Villager v=mock(Villager.class);ServerLevel level=mock(ServerLevel.class);when(v.level()).thenReturn(level);
        var tag=new CompoundTag();tag.putString("SiegeCivilianFaction","team:test");tag.putLong("SiegeCivilianSafe",new BlockPos(1,64,1).asLong());
        when(v.getPersistentData()).thenReturn(tag);when(v.blockPosition()).thenReturn(new BlockPos(17,64,1));v.tickCount=1;
        when(v.getNavigation()).thenReturn(mock(PathNavigation.class));when(v.getBrain()).thenReturn(mock(Brain.class));
        try(var claims=mockStatic(SiegeCore.class);var placement=mockStatic(HirePlacement.class)) {
            claims.when(()->SiegeCore.claimed(level,new BlockPos(1,64,1),"team:test")).thenReturn(true);
            placement.when(()->HirePlacement.safe(eq(level),eq(v),eq(new BlockPos(1,64,1)),any())).thenReturn(true);
            CoreCivilians.tick(new LivingEvent.LivingTickEvent(v));
            verify(v).stopRiding();verify(v).teleportTo(1.5,64,1.5);verify(v.getNavigation()).stop();
        }
    }
}
