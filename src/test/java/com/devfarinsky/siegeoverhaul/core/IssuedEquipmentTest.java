package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IssuedEquipmentTest extends MinecraftTestSupport {
    @Test void removesOnlyIssuedItemsAndKeepsPlayerReplacements() {
        var inventory=new SimpleContainer(3);
        inventory.setItem(0,IssuedEquipment.issue(new ItemStack(Items.NETHERITE_CHESTPLATE)));
        inventory.setItem(1,new ItemStack(Items.DIAMOND_CHESTPLATE));
        inventory.setItem(2,IssuedEquipment.issue(new ItemStack(Items.IRON_SWORD)));
        IssuedEquipment.clear(inventory);
        assertTrue(inventory.getItem(0).isEmpty());
        assertTrue(inventory.getItem(2).isEmpty());
        assertTrue(inventory.getItem(1).is(Items.DIAMOND_CHESTPLATE));
    }
}
