package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
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
    @Test void pendingArrivalsRetryAfterPlacerLogsOutWithoutLoadingChunks() {
        ServerLevel level=mock(ServerLevel.class);when(level.dimension()).thenReturn(Level.OVERWORLD);
        MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        var players=mock(PlayerList.class);when(server.getPlayerList()).thenReturn(players);
        var data=new RaidSavedData();var core=new CompoundTag();UUID owner=UUID.randomUUID();
        core.putLong("Position",BlockPos.ZERO.asLong());core.putUUID("CivilianPendingOwner",owner);
        data.siegeCores.put("team:test",core);
        try(var saved=mockStatic(RaidSavedData.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(data);
            CoreCivilians.onCoreTick(level,BlockPos.ZERO);
            verify(level).scheduleTick(BlockPos.ZERO,CoreBlocks.CORE.get(),100);
            verify(players).getPlayer(owner);
            assertTrue(core.hasUUID("CivilianPendingOwner"));assertEquals(0,CivilianLedger.count(CoreCivilians.ledger(data,"team:test")));
        }
    }
    @Test void fullPopulationRetryIsQuietAndPreservesUnfinishedLifetimeGrant() throws Exception {
        ServerLevel level=mock(ServerLevel.class);when(level.dimension()).thenReturn(Level.OVERWORLD);
        MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        var players=mock(PlayerList.class);when(server.getPlayerList()).thenReturn(players);
        var player=mock(ServerPlayer.class);
        var field=ServerPlayer.class.getField("server");field.setAccessible(true);field.set(player,server);
        when(player.serverLevel()).thenReturn(level);
        var data=new RaidSavedData();var core=new CompoundTag();UUID owner=UUID.randomUUID();
        core.putLong("Position",BlockPos.ZERO.asLong());core.putUUID("CivilianPendingOwner",owner);
        data.siegeCores.put("team:test",core);when(players.getPlayer(owner)).thenReturn(player);
        var ledger=CoreCivilians.ledger(data,"team:test");ledger.putInt("Starters",1);
        for(int i=0;i<64;i++)CivilianLedger.register(ledger,UUID.randomUUID(),0);
        try(var saved=mockStatic(RaidSavedData.class);var claims=mockStatic(SiegeCore.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(data);
            claims.when(()->SiegeCore.key(player)).thenReturn("team:test");
            claims.when(()->SiegeCore.canUse(player,BlockPos.ZERO)).thenReturn(true);
            CoreCivilians.onCoreTick(level,BlockPos.ZERO);
            verify(player,never()).sendSystemMessage(any());
            verify(level).scheduleTick(BlockPos.ZERO,CoreBlocks.CORE.get(),100);
            assertEquals(1,ledger.getInt("Starters"));assertTrue(core.hasUUID("CivilianPendingOwner"));
            CoreCivilians.tryStarters(player,BlockPos.ZERO);
            verify(player).sendSystemMessage(argThat(c->c.getString().contains("64-civilian limit")));
            verify(level,times(2)).scheduleTick(BlockPos.ZERO,CoreBlocks.CORE.get(),100);
            assertEquals(1,ledger.getInt("Starters"));assertEquals(64,CivilianLedger.count(ledger));
        }
    }
    @Test void savedCompletedGrantsClearPendingOwnerEvenAfterResidentsDie() {
        ServerLevel level=mock(ServerLevel.class);when(level.dimension()).thenReturn(Level.OVERWORLD);
        MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        var data=new RaidSavedData();var core=new CompoundTag();
        core.putLong("Position",BlockPos.ZERO.asLong());core.putUUID("CivilianPendingOwner",UUID.randomUUID());
        data.siegeCores.put("team:test",core);CoreCivilians.ledger(data,"team:test").putInt("Starters",2);
        var loaded=RaidSavedData.load(data.save(new CompoundTag()));
        try(var saved=mockStatic(RaidSavedData.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(loaded);
            CoreCivilians.onCoreTick(level,BlockPos.ZERO);
            assertFalse(loaded.siegeCores.get("team:test").hasUUID("CivilianPendingOwner"));
            assertEquals(2,CoreCivilians.ledger(loaded,"team:test").getInt("Starters"));
            verifyNoInteractions(server);
            verify(level,never()).scheduleTick(any(),eq(CoreBlocks.CORE.get()),anyInt());
        }
    }
    @Test void oldCoreTicksCannotSpawnOrRescheduleAtRelocatedPosition() {
        ServerLevel level=mock(ServerLevel.class);when(level.dimension()).thenReturn(Level.OVERWORLD);
        MinecraftServer server=mock(MinecraftServer.class);when(level.getServer()).thenReturn(server);
        var data=new RaidSavedData();var core=new CompoundTag();
        core.putLong("Position",new BlockPos(32,64,32).asLong());core.putUUID("CivilianPendingOwner",UUID.randomUUID());
        data.siegeCores.put("team:test",core);
        try(var saved=mockStatic(RaidSavedData.class)) {
            saved.when(()->RaidSavedData.get(server)).thenReturn(data);
            CoreCivilians.onCoreTick(level,BlockPos.ZERO);
            assertTrue(core.hasUUID("CivilianPendingOwner"));
            verifyNoInteractions(server);verify(level,never()).scheduleTick(any(),eq(CoreBlocks.CORE.get()),anyInt());
        }
    }
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
