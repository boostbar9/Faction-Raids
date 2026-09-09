package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;

/** One-time starting identity and finite supplies for ordinary core-hired soldiers. */
public final class RecruitPersonality {
    private static final String MARKER = "SiegeCoreOutfitted";
    private static final String[] FIRST = {"Rowan", "Mira", "Alden", "Tessa", "Bram", "Elara", "Gareth", "Nora", "Finn", "Lyra", "Tobin", "Freya", "Dorian", "Wren", "Cedric", "Hazel", "Ronan", "Vera", "Jasper", "Maeve", "Owen", "Iris", "Silas", "Ada"};
    private static final String[] LAST = {"Ashford", "Reed", "Ironwood", "Hawthorne", "Brook", "Stonefield", "Vale", "Oakheart", "Thorne", "Hillcrest", "Wells", "Foxglove", "Greybank", "Alder", "Fairwind", "Briar", "Moss", "Ridgeway", "Wintermere", "Redfern", "Blackwell", "Dawson", "Greenhill", "Westbrook"};
    private static final String[] MATERIALS = {"copper", "iron", "gold", "redstone"};
    private RecruitPersonality() {}

    static String name(RandomSource random) {
        return FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
    }

    public static void prepare(Mob mob, int role, SimpleContainer inventory) {
        if (role < 0 || role >= CoreOffers.WORKER_START || mob.getPersistentData().getBoolean(MARKER)) return;
        if (inventory.getContainerSize() < 8) throw new IllegalStateException("Recruit equipment inventory too small");
        RandomSource random = mob.getRandom();
        String name = name(random);
        String material = MATERIALS[random.nextInt(MATERIALS.length)];
        Item[] armor = {random.nextBoolean() ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET,
                Items.IRON_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.IRON_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = new ItemStack(armor[i]);
            var trim = new net.minecraft.nbt.CompoundTag();
            trim.putString("material", "minecraft:" + material);
            trim.putString("pattern", "minecraft:sentry");
            stack.getOrCreateTag().put("Trim", trim);
            inventory.setItem(i, stack);
            mob.setItemSlot(slots[i], stack);
        }
        ItemStack weapon = new ItemStack(role == 2 ? Items.BOW : role == 3 ? Items.CROSSBOW : Items.IRON_SWORD);
        weapon.setHoverName(Component.literal(name + "'s " + (role == 2 ? "Bow" : role == 3 ? "Crossbow" : "Sword")));
        inventory.setItem(5, weapon);
        mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        if (role == 1) {
            ItemStack shield = new ItemStack(Items.SHIELD);
            inventory.setItem(4, shield);
            mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
        }
        inventory.setItem(6, new ItemStack(Items.BREAD, 8));
        if (role >= 2) inventory.setItem(7, new ItemStack(Items.ARROW, 32));
        mob.setCustomName(Component.literal(name));
        mob.getPersistentData().putBoolean(MARKER, true);
        inventory.setChanged();
    }
}
