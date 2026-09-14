package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreInventorySyncTest extends MinecraftTestSupport {
    @Test void deliversStorageChangesAndClearedPaymentsWithoutDependingOnActiveMenu() {
        var player = mock(ServerPlayer.class);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        var inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        for (int i = 0; i < 9; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        inventory.setItem(10, new ItemStack(Items.EMERALD, 16));
        var sync = new CoreInventorySync();
        sync.broadcast(player);
        clearInvocations(player.connection);
        sync.broadcast(player);
        verifyNoInteractions(player.connection);

        inventory.getItem(10).shrink(16);
        inventory.setItem(35, new ItemStack(Items.CROSSBOW));
        sync.broadcast(player);
        var packets = ArgumentCaptor.forClass(ClientboundContainerSetSlotPacket.class);
        verify(player.connection, times(2)).send(packets.capture());
        var changes = packets.getAllValues();
        assertTrue(changes.stream().allMatch(p -> p.getContainerId() == -2));
        assertEquals(10, changes.get(0).getSlot());
        assertTrue(changes.get(0).getItem().isEmpty());
        assertEquals(35, changes.get(1).getSlot());
        assertTrue(changes.get(1).getItem().is(Items.CROSSBOW));
        clearInvocations(player.connection);
        sync.broadcast(player);
        verifyNoInteractions(player.connection);
    }
}
