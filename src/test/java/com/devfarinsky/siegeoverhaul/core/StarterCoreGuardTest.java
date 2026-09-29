package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StarterCoreGuardTest extends MinecraftTestSupport {
    @Test void grantSurvivesReloadCoreRemovalRelocationAndGuardDeath() {
        RaidSavedData data = new RaidSavedData();
        AtomicInteger hires = new AtomicInteger();
        assertTrue(StarterCoreGuard.grantOnce(data, "team:blue", () -> hires.incrementAndGet() > 0));
        data.siegeCores.clear();
        data.anchors.clear();
        RaidSavedData loaded = RaidSavedData.load(data.save(new CompoundTag()));
        CompoundTag moved = new CompoundTag();
        moved.putLong("Position", new BlockPos(50, 80, 50).asLong());
        loaded.siegeCores.put("team:blue", moved);
        assertFalse(StarterCoreGuard.grantOnce(loaded, "team:blue", () -> hires.incrementAndGet() > 0));
        assertEquals(1, hires.get());
        assertTrue(loaded.coreGuardGrants.contains("team:blue"));
    }

    @Test void failedAndThrowingHiresPreserveEligibilityAndReentrantCallbacksCannotDuplicate() {
        RaidSavedData data = new RaidSavedData();
        assertFalse(StarterCoreGuard.grantOnce(data, "team:blue", () -> false));
        assertFalse(data.coreGuardGrants.contains("team:blue"));
        assertThrows(IllegalStateException.class, () -> StarterCoreGuard.grantOnce(data, "team:blue",
                () -> { throw new IllegalStateException("native rejection"); }));
        assertFalse(data.coreGuardGrants.contains("team:blue"));
        assertTrue(StarterCoreGuard.grantOnce(data, "team:blue", () -> {
            assertFalse(StarterCoreGuard.grantOnce(data, "team:blue",
                    () -> { fail("Native callbacks must not hire twice"); return true; }));
            return true;
        }));
    }

    @Test void oldSavesAndSeparateFactionsEachReceiveOneGrantButInvalidKeysCannot() {
        RaidSavedData data = RaidSavedData.load(new CompoundTag());
        for (String key : new String[]{null, "", "team:", "player:test"}) {
            assertFalse(StarterCoreGuard.grantOnce(data, key,
                    () -> { fail("Invalid identity"); return true; }));
        }
        assertTrue(StarterCoreGuard.grantOnce(data, "team:blue", () -> true));
        assertTrue(StarterCoreGuard.grantOnce(data, "team:red", () -> true));
        assertEquals(2, data.coreGuardGrants.size());
    }

    @Test void kitUsesNativeEquipmentSlotsWithoutHeroStatsOrInfiniteSupplies() {
        Mob recruit = mock(Mob.class);
        CompoundTag tag = new CompoundTag();
        when(recruit.getPersistentData()).thenReturn(tag);
        SimpleContainer inventory = new SimpleContainer(36);
        StarterCoreGuard.prepare(recruit, inventory);
        assertTrue(inventory.getItem(0).is(Items.CHAINMAIL_HELMET));
        assertTrue(inventory.getItem(1).is(Items.LEATHER_CHESTPLATE));
        assertTrue(inventory.getItem(2).is(Items.LEATHER_LEGGINGS));
        assertTrue(inventory.getItem(3).is(Items.LEATHER_BOOTS));
        assertTrue(inventory.getItem(4).is(Items.SHIELD));
        assertTrue(inventory.getItem(5).is(Items.IRON_SWORD));
        verify(recruit).setItemSlot(EquipmentSlot.OFFHAND, inventory.getItem(4));
        verify(recruit).setItemSlot(EquipmentSlot.MAINHAND, inventory.getItem(5));
        verify(recruit).setCustomName(argThat(name -> name.getString().equals("Core Guard")));
        verify(recruit, never()).getAttribute(any());
        assertEquals(8, inventory.countItem(Items.BREAD));
        ItemStack replacement = new ItemStack(Items.DIAMOND_SWORD);
        inventory.setItem(5, replacement);
        inventory.setItem(6, ItemStack.EMPTY);
        clearInvocations(recruit);
        when(recruit.getPersistentData()).thenReturn(tag.copy());
        StarterCoreGuard.prepare(recruit, inventory);
        assertSame(replacement, inventory.getItem(5));
        assertEquals(0, inventory.countItem(Items.BREAD));
        verify(recruit, never()).setCustomName(any());
        verify(recruit, never()).setItemSlot(any(), any());
    }

    @Test void undersizedInventoryCannotPartiallyOutfitTheGuard() {
        Mob recruit = mock(Mob.class);
        CompoundTag tag = new CompoundTag();
        when(recruit.getPersistentData()).thenReturn(tag);
        SimpleContainer inventory = new SimpleContainer(6);
        assertThrows(IllegalStateException.class, () -> StarterCoreGuard.prepare(recruit, inventory));
        assertTrue(inventory.isEmpty());
        assertTrue(tag.isEmpty());
        verify(recruit, never()).setItemSlot(any(), any());
    }

    @Test void validCoreUsesFreeNativeHireOnceAndPreservesTreasuryAndOtherCoreData() throws Exception {
        ServerPlayer player = mock(ServerPlayer.class);
        MinecraftServer server = mock(MinecraftServer.class);
        var field = ServerPlayer.class.getField("server");
        field.setAccessible(true); field.set(player, server);
        RaidSavedData data = new RaidSavedData();
        CompoundTag core = new CompoundTag();
        core.putLong("BankEmeralds", 75);
        core.putUUID(StarterCoreGuard.PENDING_OWNER, java.util.UUID.randomUUID());
        data.siegeCores.put("team:blue", core);
        try (var cores = mockStatic(SiegeCore.class); var storage = mockStatic(RaidSavedData.class);
             var hiring = mockStatic(CoreHiring.class); var payments = mockStatic(PaymentSource.class)) {
            cores.when(() -> SiegeCore.canUse(player, BlockPos.ZERO)).thenReturn(true);
            cores.when(() -> SiegeCore.key(player)).thenReturn("team:blue");
            storage.when(() -> RaidSavedData.get(server)).thenReturn(data);
            hiring.when(() -> CoreHiring.hireStarterGuard(player, BlockPos.ZERO)).thenReturn(true);
            assertTrue(StarterCoreGuard.tryGrant(player, BlockPos.ZERO));
            assertFalse(StarterCoreGuard.tryGrant(player, BlockPos.ZERO));
            hiring.verify(() -> CoreHiring.hireStarterGuard(player, BlockPos.ZERO), times(1));
            payments.verifyNoInteractions();
            assertEquals(75, core.getLong("BankEmeralds"));
            assertFalse(core.contains(StarterCoreGuard.PENDING_OWNER));
            verify(player, times(1)).sendSystemMessage(argThat(message -> message.getString().contains("Core Guard is ready")));
        }
    }

    @Test void unownedOccupiedRemovedOrCancelledCoreCannotGrantAUnit() {
        ServerPlayer player = mock(ServerPlayer.class);
        try (var cores = mockStatic(SiegeCore.class); var hiring = mockStatic(CoreHiring.class)) {
            cores.when(() -> SiegeCore.canUse(player, BlockPos.ZERO)).thenReturn(false);
            assertFalse(StarterCoreGuard.tryGrant(player, BlockPos.ZERO));
            hiring.verifyNoInteractions();
        }
    }

    @Test void placementSchedulesDeliveryInsteadOfSpawningBeforeForgeCanCancel() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        // The JUnit bootstrap freezes registries before this test; exercise the real
        // callbacks without constructing another registered Minecraft block.
        CoreBlocks.CoreBlock block = mock(CoreBlocks.CoreBlock.class);
        var state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var random = net.minecraft.util.RandomSource.create(1);
        doCallRealMethod().when(block).setPlacedBy(level, BlockPos.ZERO, state, player, ItemStack.EMPTY);
        doCallRealMethod().when(block).tick(state, level, BlockPos.ZERO, random);
        try (var cores = mockStatic(SiegeCore.class); var guard = mockStatic(StarterCoreGuard.class); var civilians = mockStatic(RaidSavedData.class)) {
            var civilianData = new RaidSavedData();
            civilians.when(() -> RaidSavedData.get(level.getServer())).thenReturn(civilianData);
            block.setPlacedBy(level, BlockPos.ZERO, state, player, ItemStack.EMPTY);
            cores.verify(() -> SiegeCore.placed(player, BlockPos.ZERO));
            verify(level).scheduleTick(BlockPos.ZERO, block, 1);
            guard.verifyNoInteractions();
            civilians.verifyNoInteractions();
            block.tick(state, level, BlockPos.ZERO, random);
            guard.verify(() -> StarterCoreGuard.onCoreTick(level, BlockPos.ZERO));
            civilians.verify(() -> RaidSavedData.get(level.getServer()));
        }
    }
    @Test void staleOfflineAndIneligiblePendingRecordsDoNotMaskValidDelivery() throws Exception {
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        var players = mock(net.minecraft.server.players.PlayerList.class);
        when(level.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(players);
        var data = new RaidSavedData();
        for (int i = 0; i < 3; i++) data.siegeCores.put("team:" + i, new CompoundTag());
        // Use the actual map iteration order: stale first, transferred second, eligible last.
        var records = new java.util.ArrayList<>(data.siegeCores.values());
        var formerOwner = mock(ServerPlayer.class); var owner = mock(ServerPlayer.class);
        for (int i = 0; i < records.size(); i++) {
            var id = java.util.UUID.randomUUID();
            records.get(i).putLong("Position", BlockPos.ZERO.asLong());
            records.get(i).putUUID(StarterCoreGuard.PENDING_OWNER, id);
            if (i == 1) when(players.getPlayer(id)).thenReturn(formerOwner);
            if (i == 2) when(players.getPlayer(id)).thenReturn(owner);
        }
        var field = ServerPlayer.class.getField("server");
        field.setAccessible(true); field.set(owner, server);
        String key = new java.util.ArrayList<>(data.siegeCores.keySet()).get(2);
        try (var saved = mockStatic(RaidSavedData.class); var cores = mockStatic(SiegeCore.class);
             var hiring = mockStatic(CoreHiring.class)) {
            saved.when(() -> RaidSavedData.get(server)).thenReturn(data);
            cores.when(() -> SiegeCore.canUse(owner, BlockPos.ZERO)).thenReturn(true);
            cores.when(() -> SiegeCore.key(owner)).thenReturn(key);
            hiring.when(() -> CoreHiring.hireStarterGuard(owner, BlockPos.ZERO)).thenReturn(true);
            StarterCoreGuard.onCoreTick(level, BlockPos.ZERO);
            cores.verify(() -> SiegeCore.canUse(formerOwner, BlockPos.ZERO));
            hiring.verify(() -> CoreHiring.hireStarterGuard(owner, BlockPos.ZERO));
            assertTrue(data.coreGuardGrants.contains(key));
            assertFalse(records.get(2).contains(StarterCoreGuard.PENDING_OWNER));
        }
    }

}
