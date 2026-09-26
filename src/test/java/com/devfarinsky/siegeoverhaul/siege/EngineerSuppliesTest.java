package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineerSuppliesTest extends MinecraftTestSupport {
    @Test void finiteRepairKitPreservesAmmunitionAndFood() {
        SimpleContainer inventory = new SimpleContainer(15);
        inventory.addItem(new ItemStack(Items.COBBLESTONE, 64));
        inventory.addItem(new ItemStack(Items.BREAD, 16));
        EngineerSupplies.addRepairKit(inventory);
        assertEquals(64, inventory.countItem(Items.COBBLESTONE));
        assertEquals(16, inventory.countItem(Items.BREAD));
        assertEquals(16, inventory.countItem(Items.IRON_NUGGET));
        assertEquals(16, inventory.countItem(Items.OAK_PLANKS));
    }
    @Test void fullInventoryIsNotOverwrittenToMakeRoomForRepairs() {
        SimpleContainer inventory = new SimpleContainer(1);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 64));
        EngineerSupplies.addRepairKit(inventory);
        assertEquals(64, inventory.countItem(Items.DIAMOND));
        assertEquals(0, inventory.countItem(Items.IRON_NUGGET));
        assertEquals(0, inventory.countItem(Items.OAK_PLANKS));
    }
}
