package com.devfarinsky.siegeoverhaul.enchant;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Signature enchantments carried by Command Center loot rewards.
 *
 * <p>These are deliberately not obtainable from an enchanting table or from
 * vanilla loot: {@link Enchantment#isTreasureOnly()} plus
 * {@code isTradeable()} / {@code isDiscoverable()} returning {@code false}
 * keeps them exclusive to war chests, so opening a crate is the only way to
 * get one.
 *
 * <p>The behaviour of each enchantment lives in {@link EnchantEffects}.
 */
public final class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SiegeOverhaul.MOD_ID);

    /** Bonus damage against enemy raiders; scales with level. */
    public static final RegistryObject<Enchantment> SIEGEBREAKER =
            ENCHANTMENTS.register("siegebreaker",
                    () -> new SignatureEnchantment(Enchantment.Rarity.VERY_RARE,
                            EnchantmentCategory.WEAPON, 3,
                            EquipmentSlot.MAINHAND));

    /** Damage reduction that grows while you are outnumbered. */
    public static final RegistryObject<Enchantment> BULWARK =
            ENCHANTMENTS.register("bulwark",
                    () -> new SignatureEnchantment(Enchantment.Rarity.VERY_RARE,
                            EnchantmentCategory.ARMOR_CHEST, 3,
                            EquipmentSlot.CHEST));

    /** Enemy kills bank extra war-chest key progress. */
    public static final RegistryObject<Enchantment> PLUNDERER =
            ENCHANTMENTS.register("plunderer",
                    () -> new SignatureEnchantment(Enchantment.Rarity.VERY_RARE,
                            EnchantmentCategory.WEAPON, 3,
                            EquipmentSlot.MAINHAND));

    private ModEnchantments() {}

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }

    /**
     * Shared implementation. Levels are linear and the enchantment never
     * appears in the enchanting table, villager trades or vanilla loot.
     */
    private static final class SignatureEnchantment extends Enchantment {
        private final int maxLevel;

        private SignatureEnchantment(Rarity rarity, EnchantmentCategory category,
                                     int maxLevel, EquipmentSlot... slots) {
            super(rarity, category, slots);
            this.maxLevel = maxLevel;
        }

        @Override public int getMinLevel() { return 1; }
        @Override public int getMaxLevel() { return maxLevel; }
        @Override public boolean isTreasureOnly() { return true; }
        @Override public boolean isTradeable() { return false; }
        @Override public boolean isDiscoverable() { return false; }
    }
}
