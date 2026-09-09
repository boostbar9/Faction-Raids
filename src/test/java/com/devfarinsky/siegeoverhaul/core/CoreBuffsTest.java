package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreBuffsTest extends MinecraftTestSupport {
    @Test void existingBuffAndInvalidRequestsNeverChargeOrApplyEffects(){
        var player=mock(ServerPlayer.class); when(player.hasEffect(MobEffects.MOVEMENT_SPEED)).thenReturn(true);
        assertFalse(CoreBuffs.purchase(player,0)); assertFalse(CoreBuffs.purchase(player,-1)); assertFalse(CoreBuffs.purchase(player,3));
        verify(player,never()).getInventory(); verify(player,never()).addEffect(any());
    }
    @Test void rejectedEffectDoesNotChargeAndSuccessfulEffectChargesExactlyOnce() throws Exception {
        var player=mock(ServerPlayer.class); when(player.isAlive()).thenReturn(true);
        var inventory=new Inventory(player); inventory.items.set(0,new ItemStack(Items.EMERALD,64));
        when(player.getInventory()).thenReturn(inventory); var field=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu"); field.setAccessible(true); field.set(player,mock(InventoryMenu.class));
        when(player.addEffect(any())).thenReturn(false);
        assertFalse(CoreBuffs.purchase(player,1)); assertEquals(64,inventory.items.get(0).getCount());
        when(player.addEffect(any())).thenReturn(true);
        assertTrue(CoreBuffs.purchase(player,1)); assertEquals(40,inventory.items.get(0).getCount());
        when(player.hasEffect(MobEffects.DAMAGE_BOOST)).thenReturn(true);
        assertFalse(CoreBuffs.purchase(player,1)); assertEquals(40,inventory.items.get(0).getCount());
    }
}
