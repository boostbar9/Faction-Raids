package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.world.item.ItemStack;

final class EnemyLootBoxes {
    private EnemyLootBoxes() {}
    static ItemStack stack(LootBoxItem.Tier tier) { return new ItemStack(ModItems.lootBox(tier).get()); }
}
