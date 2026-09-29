package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreOfferEquipmentTest extends MinecraftTestSupport {
    @Test void savedOfferCopiesArmorTrimsWeaponsSuppliesAndNameIntoDeliveredUnit() {
        Mob source=mock(Mob.class), delivered=mock(Mob.class);
        when(source.hasCustomName()).thenReturn(true);when(source.getCustomName()).thenReturn(Component.literal("Bob"));
        var original=new SimpleContainer(8);
        ItemStack helm=new ItemStack(Items.IRON_HELMET);helm.getOrCreateTag().putString("TestTrim","gold");
        original.setItem(0,helm);original.setItem(5,new ItemStack(Items.BOW));original.setItem(7,new ItemStack(Items.ARROW,32));
        var kit=CoreOfferEquipment.capture(source,original).copy();
        original.clearContent();
        var inventory=new SimpleContainer(8);inventory.setItem(4,new ItemStack(Items.SHIELD));
        CoreOfferEquipment.apply(delivered,inventory,kit);
        assertTrue(inventory.getItem(0).is(Items.IRON_HELMET));assertEquals("gold",inventory.getItem(0).getTag().getString("TestTrim"));
        assertTrue(inventory.getItem(4).isEmpty());assertTrue(inventory.getItem(5).is(Items.BOW));assertEquals(32,inventory.getItem(7).getCount());
        for(int i=0;i<6;i++)verify(delivered).setItemSlot(CoreOfferEquipment.SLOTS[i],inventory.getItem(i));
        verify(delivered).setCustomName(Component.literal("Bob"));
        inventory.getItem(0).shrink(1);assertEquals(1,CoreOfferEquipment.item(kit,0).getCount());
    }
    @Test void staleRotationsWrongRolesAndMissingMetadataCannotBePurchased() {
        var kit=new CompoundTag();kit.put("Items",new net.minecraft.nbt.ListTag());
        assertFalse(CoreOfferEquipment.matches(kit,0,0));
        for(int i=0;i<6;i++)kit.getList("Items",10).add(new CompoundTag());
        kit.putInt("Role",2);kit.putLong("Rotation",18000L);
        assertTrue(CoreOfferEquipment.matches(kit,2,18000L));
        assertFalse(CoreOfferEquipment.matches(kit,3,18000L));
        assertFalse(CoreOfferEquipment.matches(kit,2,36000L));
    }
    @Test void missingOfferCannotSilentlyGenerateDifferentGear() {
        assertThrows(IllegalArgumentException.class,()->CoreOfferEquipment.apply(mock(Mob.class),new SimpleContainer(8),new CompoundTag()));
    }
}
