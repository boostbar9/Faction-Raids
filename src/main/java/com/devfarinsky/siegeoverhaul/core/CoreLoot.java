package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.List;

/**
 * War chests: long, varied loot tables with balanced vanilla enchantments.
 *
 * <p>Each chest is themed and rolls independently on the same 50 / 30 / 15 / 5
 * odds. Every rarity has a deep pool of possible outcomes so opening the same
 * chest twice rarely produces the same reward. Common and Uncommon rolls
 * favor supplies, materials, potions and light gear. Rare rolls hand out
 * mid-tier trimmed armor and modest weapons. Epic rolls are named trophies
 * with moderate vanilla enchantment loadouts, kept below full-diamond raid
 * gear so a couple of lucky boxes do not trivialize the rest of the game.
 *
 * <p>Every reward is deterministic in the roll number so tests can pin
 * behavior, and every possible outcome must fit in the player's inventory
 * before the roll is taken.
 */
public final class CoreLoot {
    private CoreLoot() {}

    public static final int OPEN_TICKS = 60;
    public static final String[] NAMES = {"Field Supplies", "Veteran Armory", "Royal Treasury"};

    public static int price(int box) {
        return switch (box) { case 0 -> 16; case 1 -> 48; case 2 -> 96; default -> -1; };
    }

    public static String odds() { return "Common 50% | Uncommon 30% | Rare 15% | Epic 5%"; }

    public static String rarity(int tier) {
        return switch (tier) { case 0 -> "Common"; case 1 -> "Uncommon"; case 2 -> "Rare"; default -> "Epic"; };
    }

    /** Highest rarity a chest can roll. Every chest can reach Epic. */
    public static int topTier(int box) {
        if (price(box) < 0) throw new IllegalArgumentException("Invalid loot box");
        return 3;
    }

    /** Rarity tier for a 0-99 roll, matching the advertised odds exactly. */
    public static int tier(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Invalid loot roll");
        return roll < 50 ? 0 : roll < 80 ? 1 : roll < 95 ? 2 : 3;
    }

    public record Receipt(ItemStack prize, int tier) {}

    public static ItemStack reward(int box, int roll) {
        if (price(box) < 0) throw new IllegalArgumentException("Invalid loot box");
        int tier = tier(roll);
        // Convert the absolute roll into a within-tier index so each rarity
        // draws from its own deep pool of hand-picked outcomes.
        int idx = withinTier(roll);
        return switch (box) {
            case 0 -> fieldSupplies(tier, idx);
            case 1 -> veteranArmory(tier, idx);
            default -> royalTreasury(tier, idx);
        };
    }

    /** Zero-based index of a roll inside its rarity band. */
    private static int withinTier(int roll) {
        return switch (tier(roll)) {
            case 0 -> roll;            // 0..49
            case 1 -> roll - 50;       // 0..29
            case 2 -> roll - 80;       // 0..14
            default -> roll - 95;      // 0..4
        };
    }

    // -----------------------------------------------------------------
    // FIELD SUPPLIES (16 emeralds) - campaign consumables and scout kit
    // -----------------------------------------------------------------

    private static ItemStack fieldSupplies(int tier, int idx) {
        return switch (tier) {
            case 0 -> fieldSuppliesCommon(idx % 15);
            case 1 -> fieldSuppliesUncommon(idx % 12);
            case 2 -> fieldSuppliesRare(idx % 10);
            default -> fieldSuppliesEpic(idx % 5);
        };
    }

    private static ItemStack fieldSuppliesCommon(int i) {
        return switch (i) {
            case 0 -> named(new ItemStack(Items.BREAD, 12), "Campaign Rations", ChatFormatting.GRAY);
            case 1 -> named(new ItemStack(Items.COOKED_BEEF, 8), "Salted Rations", ChatFormatting.GRAY);
            case 2 -> named(new ItemStack(Items.ARROW, 32), "Scout Arrows", ChatFormatting.GRAY);
            case 3 -> named(new ItemStack(Items.TORCH, 16), "March Torches", ChatFormatting.GRAY);
            case 4 -> named(new ItemStack(Items.STICK, 16), "Fresh Shafts", ChatFormatting.GRAY);
            case 5 -> named(new ItemStack(Items.LEATHER, 6), "Cured Hide", ChatFormatting.GRAY);
            case 6 -> named(new ItemStack(Items.STRING, 12), "Bowstring Rope", ChatFormatting.GRAY);
            case 7 -> named(new ItemStack(Items.FEATHER, 12), "Fletcher's Bundle", ChatFormatting.GRAY);
            case 8 -> named(new ItemStack(Items.FLINT, 8), "Sharp Flint", ChatFormatting.GRAY);
            case 9 -> named(new ItemStack(Items.COAL, 16), "Camp Fuel", ChatFormatting.GRAY);
            case 10 -> named(new ItemStack(Items.APPLE, 8), "Orchard Ration", ChatFormatting.GRAY);
            case 11 -> named(new ItemStack(Items.BAKED_POTATO, 8), "Trench Potatoes", ChatFormatting.GRAY);
            case 12 -> named(new ItemStack(Items.MILK_BUCKET), "Battlefield Milk", ChatFormatting.GRAY);
            case 13 -> named(new ItemStack(Items.BONE, 8), "Bone Meal Pouch", ChatFormatting.GRAY);
            default -> named(new ItemStack(Items.WHEAT, 16), "Grain Sack", ChatFormatting.GRAY);
        };
    }

    private static ItemStack fieldSuppliesUncommon(int i) {
        return switch (i) {
            case 0 -> named(new ItemStack(Items.GOLDEN_APPLE, 2), "Field Physician's Draught", ChatFormatting.GREEN);
            case 1 -> named(new ItemStack(Items.SPECTRAL_ARROW, 16), "Signal Shafts", ChatFormatting.GREEN);
            case 2 -> named(new ItemStack(Items.TIPPED_ARROW, 8), "Poison-Dipped Arrows", ChatFormatting.GREEN);
            case 3 -> named(new ItemStack(Items.IRON_INGOT, 6), "Smelted Iron", ChatFormatting.GREEN);
            case 4 -> trophy(new ItemStack(Items.LEATHER_HELMET), "Scout Cap", ChatFormatting.GREEN,
                    List.of("Light on the head, quick on the march."),
                    entry(Enchantments.UNBREAKING, 1));
            case 5 -> trophy(new ItemStack(Items.LEATHER_BOOTS), "Runner's Boots", ChatFormatting.GREEN,
                    List.of("Made for the long chase."),
                    entry(Enchantments.FALL_PROTECTION, 2),
                    entry(Enchantments.UNBREAKING, 1));
            case 6 -> named(new ItemStack(Items.ENDER_PEARL, 2), "Scouting Pearls", ChatFormatting.GREEN);
            case 7 -> named(new ItemStack(Items.HONEY_BOTTLE, 3), "Field Salve", ChatFormatting.GREEN);
            case 8 -> named(new ItemStack(Items.EXPERIENCE_BOTTLE, 4), "Drill Grounds Experience", ChatFormatting.GREEN);
            case 9 -> named(new ItemStack(Items.COMPASS), "Scout's Compass", ChatFormatting.GREEN);
            case 10 -> named(new ItemStack(Items.LANTERN, 4), "Sentry Lanterns", ChatFormatting.GREEN);
            default -> named(new ItemStack(Items.MAP), "Field Map", ChatFormatting.GREEN);
        };
    }

    private static ItemStack fieldSuppliesRare(int i) {
        return switch (i) {
            case 0 -> trim(trophy(new ItemStack(Items.LEATHER_CHESTPLATE), "Trimmed Scout Tunic", ChatFormatting.AQUA,
                    List.of("Woven with a runner's crest."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2)), "sentry", "iron");
            case 1 -> trim(trophy(new ItemStack(Items.LEATHER_LEGGINGS), "Trimmed Scout Breeches", ChatFormatting.AQUA,
                    List.of("Reinforced at the knee."),
                    entry(Enchantments.FALL_PROTECTION, 3), entry(Enchantments.UNBREAKING, 2)), "dune", "copper");
            case 2 -> trophy(new ItemStack(Items.IRON_HELMET), "Sergeant's Kettle Helm", ChatFormatting.AQUA,
                    List.of("Standard issue for line command."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2));
            case 3 -> trophy(new ItemStack(Items.CROSSBOW), "Watch Crossbow", ChatFormatting.AQUA,
                    List.of("Kept ready for night watch."),
                    entry(Enchantments.QUICK_CHARGE, 2), entry(Enchantments.UNBREAKING, 2));
            case 4 -> named(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "Physician's Reserve", ChatFormatting.AQUA);
            case 5 -> named(new ItemStack(Items.DIAMOND, 2), "Battlefield Diamonds", ChatFormatting.AQUA);
            case 6 -> named(new ItemStack(Items.NETHERITE_SCRAP), "Salvaged Netherite Scrap", ChatFormatting.AQUA);
            case 7 -> trophy(new ItemStack(Items.SHIELD), "Watch Shield", ChatFormatting.AQUA,
                    List.of("Painted with the sentry crest."),
                    entry(Enchantments.UNBREAKING, 3));
            case 8 -> named(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2), "Sentry Trim Templates", ChatFormatting.AQUA);
            default -> named(new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 2), "Dune Trim Templates", ChatFormatting.AQUA);
        };
    }

    private static ItemStack fieldSuppliesEpic(int i) {
        return switch (i) {
            case 0 -> trim(trophy(new ItemStack(Items.LEATHER_BOOTS), "Scout's Stride", ChatFormatting.LIGHT_PURPLE,
                    List.of("Worn thin by a thousand night marches.", "Sure-footed on any ground."),
                    entry(Enchantments.FALL_PROTECTION, 3),
                    entry(Enchantments.SOUL_SPEED, 2),
                    entry(Enchantments.DEPTH_STRIDER, 2),
                    entry(Enchantments.UNBREAKING, 3)), "wild", "gold");
            case 1 -> trim(trophy(new ItemStack(Items.LEATHER_CHESTPLATE), "Quartermaster's Cloak", ChatFormatting.LIGHT_PURPLE,
                    List.of("Every patch is a battle it survived."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.THORNS, 2),
                    entry(Enchantments.UNBREAKING, 3)), "tide", "iron");
            case 2 -> trophy(new ItemStack(Items.CROSSBOW), "Ranger's Repeater", ChatFormatting.LIGHT_PURPLE,
                    List.of("Fires almost as fast as you can breathe."),
                    entry(Enchantments.QUICK_CHARGE, 3),
                    entry(Enchantments.MULTISHOT, 1),
                    entry(Enchantments.UNBREAKING, 3));
            case 3 -> trophy(new ItemStack(Items.BOW), "Signaller's Longbow", ChatFormatting.LIGHT_PURPLE,
                    List.of("Ranged too far for any common bow."),
                    entry(Enchantments.POWER_ARROWS, 3),
                    entry(Enchantments.PUNCH_ARROWS, 1),
                    entry(Enchantments.INFINITY_ARROWS, 1),
                    entry(Enchantments.UNBREAKING, 3));
            default -> named(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2), "Royal Physician's Draught", ChatFormatting.LIGHT_PURPLE);
        };
    }

    // -----------------------------------------------------------------
    // VETERAN ARMORY (48 emeralds) - line-infantry war gear
    // -----------------------------------------------------------------

    private static ItemStack veteranArmory(int tier, int idx) {
        return switch (tier) {
            case 0 -> veteranArmoryCommon(idx % 15);
            case 1 -> veteranArmoryUncommon(idx % 12);
            case 2 -> veteranArmoryRare(idx % 10);
            default -> veteranArmoryEpic(idx % 5);
        };
    }

    private static ItemStack veteranArmoryCommon(int i) {
        return switch (i) {
            case 0 -> named(new ItemStack(Items.IRON_INGOT, 4), "Forge Ingots", ChatFormatting.GRAY);
            case 1 -> named(new ItemStack(Items.IRON_NUGGET, 16), "Iron Filings", ChatFormatting.GRAY);
            case 2 -> named(new ItemStack(Items.ARROW, 48), "Line Arrows", ChatFormatting.GRAY);
            case 3 -> named(new ItemStack(Items.SHIELD), "Buckler", ChatFormatting.GRAY);
            case 4 -> named(new ItemStack(Items.IRON_SWORD), "Line Sword", ChatFormatting.GRAY);
            case 5 -> named(new ItemStack(Items.IRON_AXE), "Line Axe", ChatFormatting.GRAY);
            case 6 -> named(new ItemStack(Items.IRON_PICKAXE), "Sapper's Pick", ChatFormatting.GRAY);
            case 7 -> named(new ItemStack(Items.IRON_SHOVEL), "Trench Spade", ChatFormatting.GRAY);
            case 8 -> named(new ItemStack(Items.CHAINMAIL_HELMET), "Chain Coif", ChatFormatting.GRAY);
            case 9 -> named(new ItemStack(Items.CHAINMAIL_CHESTPLATE), "Chain Shirt", ChatFormatting.GRAY);
            case 10 -> named(new ItemStack(Items.CHAINMAIL_LEGGINGS), "Chain Chausses", ChatFormatting.GRAY);
            case 11 -> named(new ItemStack(Items.CHAINMAIL_BOOTS), "Chain Sabatons", ChatFormatting.GRAY);
            case 12 -> named(new ItemStack(Items.GOLDEN_APPLE, 3), "Front-Line Rations", ChatFormatting.GRAY);
            case 13 -> named(new ItemStack(Items.COOKED_BEEF, 16), "Barracks Ration", ChatFormatting.GRAY);
            default -> named(new ItemStack(Items.BUCKET, 2), "Water Buckets", ChatFormatting.GRAY);
        };
    }

    private static ItemStack veteranArmoryUncommon(int i) {
        return switch (i) {
            case 0 -> trophy(new ItemStack(Items.IRON_SWORD), "Drillmaster's Blade", ChatFormatting.GREEN,
                    List.of("Blunted on ten thousand training shields."),
                    entry(Enchantments.SHARPNESS, 2), entry(Enchantments.UNBREAKING, 2));
            case 1 -> trophy(new ItemStack(Items.IRON_AXE), "Sergeant's Axe", ChatFormatting.GREEN,
                    List.of("Splits shields and firewood alike."),
                    entry(Enchantments.SHARPNESS, 2), entry(Enchantments.UNBREAKING, 2));
            case 2 -> trophy(new ItemStack(Items.IRON_HELMET), "Line Kettle Helm", ChatFormatting.GREEN,
                    List.of("Standard issue for the front rank."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2));
            case 3 -> trophy(new ItemStack(Items.IRON_CHESTPLATE), "Line Cuirass", ChatFormatting.GREEN,
                    List.of("The mainstay of the shield wall."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2));
            case 4 -> trophy(new ItemStack(Items.IRON_LEGGINGS), "Line Chausses", ChatFormatting.GREEN,
                    List.of("Reinforced at the thigh."),
                    entry(Enchantments.PROJECTILE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2));
            case 5 -> trophy(new ItemStack(Items.IRON_BOOTS), "Line Sabatons", ChatFormatting.GREEN,
                    List.of("Built for a long day on foot."),
                    entry(Enchantments.FALL_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2));
            case 6 -> trophy(new ItemStack(Items.BOW), "Line Bow", ChatFormatting.GREEN,
                    List.of("Simple, accurate, well-kept."),
                    entry(Enchantments.POWER_ARROWS, 2), entry(Enchantments.UNBREAKING, 2));
            case 7 -> trophy(new ItemStack(Items.CROSSBOW), "Line Crossbow", ChatFormatting.GREEN,
                    List.of("Standard armory issue."),
                    entry(Enchantments.QUICK_CHARGE, 2), entry(Enchantments.UNBREAKING, 2));
            case 8 -> named(new ItemStack(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 2), "Eye Trim Templates", ChatFormatting.GREEN);
            case 9 -> named(new ItemStack(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE), "Ward Trim Template", ChatFormatting.GREEN);
            case 10 -> named(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "Company Physician's Reserve", ChatFormatting.GREEN);
            default -> named(new ItemStack(Items.SPECTRAL_ARROW, 32), "Signal Shafts", ChatFormatting.GREEN);
        };
    }

    private static ItemStack veteranArmoryRare(int i) {
        return switch (i) {
            case 0 -> trophy(new ItemStack(Items.DIAMOND_SWORD), "Captain's Blade", ChatFormatting.AQUA,
                    List.of("Awarded on a field promotion."),
                    entry(Enchantments.SHARPNESS, 3), entry(Enchantments.KNOCKBACK, 1), entry(Enchantments.UNBREAKING, 2));
            case 1 -> trophy(new ItemStack(Items.DIAMOND_AXE), "Siegebreaker Maul", ChatFormatting.AQUA,
                    List.of("Made for gates, not trees."),
                    entry(Enchantments.SHARPNESS, 3), entry(Enchantments.BLOCK_EFFICIENCY, 3), entry(Enchantments.UNBREAKING, 2));
            case 2 -> trim(trophy(new ItemStack(Items.DIAMOND_HELMET), "Captain's Helm", ChatFormatting.AQUA,
                    List.of("Crested to be seen through the smoke."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3), entry(Enchantments.UNBREAKING, 2)), "spire", "gold");
            case 3 -> trim(trophy(new ItemStack(Items.DIAMOND_CHESTPLATE), "Captain's Cuirass", ChatFormatting.AQUA,
                    List.of("Carried through more than one siege."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3), entry(Enchantments.THORNS, 1), entry(Enchantments.UNBREAKING, 2)), "silence", "iron");
            case 4 -> trim(trophy(new ItemStack(Items.DIAMOND_LEGGINGS), "Captain's Chausses", ChatFormatting.AQUA,
                    List.of("Reinforced with the family crest."),
                    entry(Enchantments.PROJECTILE_PROTECTION, 3), entry(Enchantments.UNBREAKING, 2)), "ward", "gold");
            case 5 -> trim(trophy(new ItemStack(Items.DIAMOND_BOOTS), "Captain's Sabatons", ChatFormatting.AQUA,
                    List.of("Cut for the long march."),
                    entry(Enchantments.FALL_PROTECTION, 3), entry(Enchantments.UNBREAKING, 2)), "vex", "netherite");
            case 6 -> trophy(new ItemStack(Items.BOW), "Marksman's Bow", ChatFormatting.AQUA,
                    List.of("Chosen shot, never wasted."),
                    entry(Enchantments.POWER_ARROWS, 3), entry(Enchantments.PUNCH_ARROWS, 1), entry(Enchantments.UNBREAKING, 2));
            case 7 -> named(new ItemStack(Items.NETHERITE_INGOT), "Salvaged Netherite Ingot", ChatFormatting.AQUA);
            case 8 -> named(new ItemStack(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE), "Rib Trim Template", ChatFormatting.AQUA);
            default -> named(new ItemStack(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE), "Spire Trim Template", ChatFormatting.AQUA);
        };
    }

    private static ItemStack veteranArmoryEpic(int i) {
        return switch (i) {
            case 0 -> trophy(new ItemStack(Items.DIAMOND_SWORD), "Ironwall Blade", ChatFormatting.LIGHT_PURPLE,
                    List.of("Named for the wall that never fell."),
                    entry(Enchantments.SHARPNESS, 4),
                    entry(Enchantments.MOB_LOOTING, 2),
                    entry(Enchantments.UNBREAKING, 3));
            case 1 -> trim(trophy(new ItemStack(Items.DIAMOND_CHESTPLATE), "Ironwall Cuirass", ChatFormatting.LIGHT_PURPLE,
                    List.of("Taken from a captain who never stepped back."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.THORNS, 2),
                    entry(Enchantments.UNBREAKING, 3)), "silence", "netherite");
            case 2 -> trophy(new ItemStack(Items.CROSSBOW), "Stormcaller Repeater", ChatFormatting.LIGHT_PURPLE,
                    List.of("Its string hums before the thunder does."),
                    entry(Enchantments.QUICK_CHARGE, 3),
                    entry(Enchantments.PIERCING, 2),
                    entry(Enchantments.UNBREAKING, 3));
            case 3 -> trophy(new ItemStack(Items.BOW), "Stormcaller Longbow", ChatFormatting.LIGHT_PURPLE,
                    List.of("Its string hums before the thunder does."),
                    entry(Enchantments.POWER_ARROWS, 4),
                    entry(Enchantments.FLAMING_ARROWS, 1),
                    entry(Enchantments.INFINITY_ARROWS, 1),
                    entry(Enchantments.UNBREAKING, 3));
            default -> trim(trophy(new ItemStack(Items.NETHERITE_HELMET), "Captain's Nethermask", ChatFormatting.LIGHT_PURPLE,
                    List.of("Recovered from a burning war camp."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.FIRE_PROTECTION, 3),
                    entry(Enchantments.UNBREAKING, 3)), "eye", "gold");
        };
    }

    // -----------------------------------------------------------------
    // ROYAL TREASURY (96 emeralds) - crown regalia and legendary trophies
    // -----------------------------------------------------------------

    private static ItemStack royalTreasury(int tier, int idx) {
        return switch (tier) {
            case 0 -> royalTreasuryCommon(idx % 15);
            case 1 -> royalTreasuryUncommon(idx % 12);
            case 2 -> royalTreasuryRare(idx % 10);
            default -> royalTreasuryEpic(idx % 5);
        };
    }

    private static ItemStack royalTreasuryCommon(int i) {
        return switch (i) {
            case 0 -> named(new ItemStack(Items.GOLD_INGOT, 8), "Royal Gold Bars", ChatFormatting.GRAY);
            case 1 -> named(new ItemStack(Items.GOLD_NUGGET, 32), "Royal Coin", ChatFormatting.GRAY);
            case 2 -> named(new ItemStack(Items.EMERALD, 4), "Tithe Emeralds", ChatFormatting.GRAY);
            case 3 -> named(new ItemStack(Items.LAPIS_LAZULI, 8), "Court Lapis", ChatFormatting.GRAY);
            case 4 -> named(new ItemStack(Items.QUARTZ, 8), "Court Quartz", ChatFormatting.GRAY);
            case 5 -> named(new ItemStack(Items.DIAMOND, 1), "Court Diamond", ChatFormatting.GRAY);
            case 6 -> named(new ItemStack(Items.GOLDEN_CARROT, 8), "Court Rations", ChatFormatting.GRAY);
            case 7 -> named(new ItemStack(Items.EXPERIENCE_BOTTLE, 6), "Royal Grant of Experience", ChatFormatting.GRAY);
            case 8 -> named(new ItemStack(Items.GLOWSTONE, 6), "Court Glowstone", ChatFormatting.GRAY);
            case 9 -> named(new ItemStack(Items.BOOK, 4), "Court Ledger", ChatFormatting.GRAY);
            case 10 -> named(new ItemStack(Items.PAPER, 16), "Court Paper", ChatFormatting.GRAY);
            case 11 -> named(new ItemStack(Items.CLOCK), "Court Clock", ChatFormatting.GRAY);
            case 12 -> named(new ItemStack(Items.COMPASS), "Court Compass", ChatFormatting.GRAY);
            case 13 -> named(new ItemStack(Items.HONEY_BOTTLE, 4), "Royal Honey", ChatFormatting.GRAY);
            default -> named(new ItemStack(Items.SUGAR, 16), "Court Sugar", ChatFormatting.GRAY);
        };
    }

    private static ItemStack royalTreasuryUncommon(int i) {
        return switch (i) {
            case 0 -> named(new ItemStack(Items.GOLDEN_APPLE, 4), "Royal Physician's Ration", ChatFormatting.GREEN);
            case 1 -> named(new ItemStack(Items.DIAMOND, 3), "Crown Diamonds", ChatFormatting.GREEN);
            case 2 -> named(new ItemStack(Items.EMERALD_BLOCK), "Sealed Emerald Block", ChatFormatting.GREEN);
            case 3 -> named(new ItemStack(Items.NETHERITE_SCRAP, 2), "Royal Netherite Scrap", ChatFormatting.GREEN);
            case 4 -> named(new ItemStack(Items.EXPERIENCE_BOTTLE, 12), "Royal Grant of Experience", ChatFormatting.GREEN);
            case 5 -> trim(trophy(new ItemStack(Items.GOLDEN_HELMET), "Court Circlet", ChatFormatting.GREEN,
                    List.of("Worn at court, useless in a fight."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2)), "ward", "gold");
            case 6 -> trim(trophy(new ItemStack(Items.GOLDEN_CHESTPLATE), "Court Cuirass", ChatFormatting.GREEN,
                    List.of("Ceremonial armor, still armor."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 2), entry(Enchantments.UNBREAKING, 2)), "vex", "gold");
            case 7 -> named(new ItemStack(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE), "Host Trim Template", ChatFormatting.GREEN);
            case 8 -> named(new ItemStack(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE), "Raiser Trim Template", ChatFormatting.GREEN);
            case 9 -> named(new ItemStack(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE), "Shaper Trim Template", ChatFormatting.GREEN);
            case 10 -> named(new ItemStack(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE), "Wayfinder Trim Template", ChatFormatting.GREEN);
            default -> named(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), "Netherite Upgrade Template", ChatFormatting.GREEN);
        };
    }

    private static ItemStack royalTreasuryRare(int i) {
        return switch (i) {
            case 0 -> trophy(new ItemStack(Items.DIAMOND_SWORD), "Warden's Blade", ChatFormatting.AQUA,
                    List.of("Forged for the watch that never sleeps."),
                    entry(Enchantments.SHARPNESS, 3),
                    entry(Enchantments.SWEEPING_EDGE, 1),
                    entry(Enchantments.UNBREAKING, 3));
            case 1 -> trim(trophy(new ItemStack(Items.DIAMOND_HELMET), "Warden's Coronet", ChatFormatting.AQUA,
                    List.of("A steel band around a burning eye."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.RESPIRATION, 2),
                    entry(Enchantments.UNBREAKING, 3)), "silence", "amethyst");
            case 2 -> trim(trophy(new ItemStack(Items.DIAMOND_CHESTPLATE), "Warden's Cuirass", ChatFormatting.AQUA,
                    List.of("The crest of a kingdom that has never fallen."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.THORNS, 2),
                    entry(Enchantments.UNBREAKING, 3)), "spire", "amethyst");
            case 3 -> trim(trophy(new ItemStack(Items.DIAMOND_LEGGINGS), "Warden's Greaves", ChatFormatting.AQUA,
                    List.of("Chased with the crest of the watch."),
                    entry(Enchantments.PROJECTILE_PROTECTION, 3),
                    entry(Enchantments.UNBREAKING, 3)), "ward", "amethyst");
            case 4 -> trim(trophy(new ItemStack(Items.DIAMOND_BOOTS), "Warden's Sabatons", ChatFormatting.AQUA,
                    List.of("Sole set with a strip of steel."),
                    entry(Enchantments.FALL_PROTECTION, 4),
                    entry(Enchantments.DEPTH_STRIDER, 2),
                    entry(Enchantments.UNBREAKING, 3)), "tide", "amethyst");
            case 5 -> trophy(new ItemStack(Items.SHIELD), "Warden's Aegis", ChatFormatting.AQUA,
                    List.of("Repainted for every siege it survives."),
                    entry(Enchantments.UNBREAKING, 3));
            case 6 -> trophy(new ItemStack(Items.BOW), "Warden's Longbow", ChatFormatting.AQUA,
                    List.of("Kept oiled through the long watch."),
                    entry(Enchantments.POWER_ARROWS, 3),
                    entry(Enchantments.PUNCH_ARROWS, 1),
                    entry(Enchantments.UNBREAKING, 3));
            case 7 -> named(new ItemStack(Items.TOTEM_OF_UNDYING), "Warden's Charm", ChatFormatting.AQUA);
            case 8 -> named(new ItemStack(Items.NETHERITE_INGOT, 2), "Crown Netherite Bars", ChatFormatting.AQUA);
            default -> named(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2), "Crown Netherite Upgrade Templates", ChatFormatting.AQUA);
        };
    }

    private static ItemStack royalTreasuryEpic(int i) {
        return switch (i) {
            case 0 -> trophy(new ItemStack(Items.NETHERITE_SWORD), "Kingsbane", ChatFormatting.LIGHT_PURPLE,
                    List.of("Named for the last king it met."),
                    entry(Enchantments.SHARPNESS, 4),
                    entry(Enchantments.SWEEPING_EDGE, 2),
                    entry(Enchantments.MOB_LOOTING, 2),
                    entry(Enchantments.UNBREAKING, 3));
            case 1 -> trim(trophy(new ItemStack(Items.NETHERITE_CHESTPLATE), "Crown Aegis", ChatFormatting.LIGHT_PURPLE,
                    List.of("The crest of a kingdom that has never fallen."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.THORNS, 2),
                    entry(Enchantments.UNBREAKING, 3)), "spire", "amethyst");
            case 2 -> trim(trophy(new ItemStack(Items.NETHERITE_LEGGINGS), "Crown Greaves", ChatFormatting.LIGHT_PURPLE,
                    List.of("Cut to move under a full harness."),
                    entry(Enchantments.PROJECTILE_PROTECTION, 3),
                    entry(Enchantments.UNBREAKING, 3)), "ward", "amethyst");
            case 3 -> trim(trophy(new ItemStack(Items.NETHERITE_HELMET), "Crown Circlet", ChatFormatting.LIGHT_PURPLE,
                    List.of("The steel band of the last watch."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                    entry(Enchantments.RESPIRATION, 3),
                    entry(Enchantments.AQUA_AFFINITY, 1),
                    entry(Enchantments.UNBREAKING, 3)), "silence", "amethyst");
            default -> trophy(new ItemStack(Items.NETHERITE_AXE), "Crownsplitter", ChatFormatting.LIGHT_PURPLE,
                    List.of("Named for what it did to the last one."),
                    entry(Enchantments.SHARPNESS, 4),
                    entry(Enchantments.BLOCK_EFFICIENCY, 4),
                    entry(Enchantments.UNBREAKING, 3));
        };
    }

    // -----------------------------------------------------------------
    // Item construction helpers
    // -----------------------------------------------------------------

    /** One enchantment to apply. */
    private record Entry(Enchantment enchantment, int level) {}
    private static Entry entry(Enchantment enchantment, int level) { return new Entry(enchantment, level); }

    private static ItemStack trophy(ItemStack stack, String name, ChatFormatting color,
                                    List<String> lore, Entry... enchantments) {
        for (Entry e : enchantments) if (e.level() > 0) stack.enchant(e.enchantment(), e.level());
        return decorate(stack, name, color, lore);
    }

    private static ItemStack named(ItemStack stack, String name, ChatFormatting color, String... lore) {
        return decorate(stack, name, color, List.of(lore));
    }

    /** Apply the display name and italic grey lore block used by every trophy. */
    private static ItemStack decorate(ItemStack stack, String name, ChatFormatting color, List<String> lore) {
        MutableComponent title = Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false));
        stack.setHoverName(title);
        if (lore.isEmpty()) return stack;
        ListTag lines = new ListTag();
        for (String line : lore) lines.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal(line).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(true)))));
        CompoundTag display = stack.getOrCreateTagElement("display");
        display.put("Lore", lines);
        return stack;
    }

    /**
     * Attach a vanilla armor trim so the piece renders with a visible pattern
     * overlay. The trim NBT is the vanilla contract, so this works whether
     * the piece is diamond, iron, gold, netherite, or leather.
     */
    private static ItemStack trim(ItemStack stack, String pattern, String material) {
        CompoundTag trim = new CompoundTag();
        trim.putString("pattern", "minecraft:" + pattern);
        trim.putString("material", "minecraft:" + material);
        stack.getOrCreateTag().put("Trim", trim);
        return stack;
    }

    static boolean fits(List<ItemStack> inventory, ItemStack reward) {
        int room = 0;
        for (var stack : inventory) {
            if (stack.isEmpty()) room += reward.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(stack, reward)) room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            if (room >= reward.getCount()) return true;
        }
        return false;
    }

    public static boolean purchase(ServerPlayer player, int box) { return purchaseWithReceipt(player, box) != null; }

    public static Receipt purchaseWithReceipt(ServerPlayer player, int box) {
        int price = price(box); if (price < 0) return null;
        long now = player.serverLevel().getGameTime();
        var data = player.getPersistentData();
        long next = data.getLong("SiegeLootNext");
        if (next > now && next <= now + OPEN_TICKS) return null;
        var inventory = player.getInventory();
        // Treasury-only affordability check via PaymentSource.
        long combined = PaymentSource.available(player, price);
        if (!player.isCreative() && combined < price) {
            player.sendSystemMessage(Component.literal("You need " + price + " emeralds in the faction Treasury."));
            return null;
        }
        // Require space for every possible outcome before rolling. Full
        // inventories cannot be used to filter unwanted rewards or lose a
        // paid reward. We only need to probe one roll per tier because
        // every reward inside a tier is capped at the same stack size (1
        // for gear, 64 for materials or arrows).
        for (int roll : new int[]{0, 50, 80, 95}) {
            if (!fits(inventory.items, reward(box, roll))) {
                player.sendSystemMessage(Component.literal("Make room in your inventory before opening a box."));
                return null;
            }
        }
        int roll = player.getRandom().nextInt(100);
        ItemStack prize = reward(box, roll);
        if (!PaymentSource.consume(player, price)) return null;
        inventory.add(prize.copy()); inventory.setChanged();
        data.putLong("SiegeLootNext", now + OPEN_TICKS);
        player.sendSystemMessage(Component.literal("Opening " + NAMES[box] + "... Reward secured in your inventory."));
        return new Receipt(prize.copy(), tier(roll));
    }
}
