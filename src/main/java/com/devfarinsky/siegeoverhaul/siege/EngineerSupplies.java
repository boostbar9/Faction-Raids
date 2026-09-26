package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Starting supplies for native Recruits repairs; never replenished automatically. */
public final class EngineerSupplies {
    private EngineerSupplies() {}

    public static void addRepairKit(SimpleContainer inventory) {
        inventory.addItem(new ItemStack(Items.IRON_NUGGET, 16));
        inventory.addItem(new ItemStack(Items.OAK_PLANKS, 16));
    }
}
