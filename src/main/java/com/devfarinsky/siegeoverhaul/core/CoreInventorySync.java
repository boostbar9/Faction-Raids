package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Replicates player inventory changes while the Core's display-only menu is open. */
final class CoreInventorySync {
    private ItemStack[] sent;

    void broadcast(ServerPlayer player) {
        var inventory = player.getInventory();
        if (sent == null) sent = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < sent.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (sent[slot] == null || !ItemStack.matches(sent[slot], stack)) {
                // Container 0 updates outside the hotbar are ignored while another
                // menu is open. PLAYER_INVENTORY uses raw inventory indices and
                // applies regardless of the active menu, without exposing slots.
                player.connection.send(new ClientboundContainerSetSlotPacket(
                        ClientboundContainerSetSlotPacket.PLAYER_INVENTORY, 0, slot, stack));
                sent[slot] = stack.copy();
            }
        }
    }
}
