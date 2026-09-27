package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** The sealed-box armory. All powers use ordinary, compatible vanilla enchantments. */
final class OlympianLoot {
    static final int ARMORY_SIZE = 12;
    static final int SUPPLY_TYPES = 8;
    private static final String PATRON = "SiegeOlympianPatron";

    private OlympianLoot() {}

    static ItemStack armory(LootBoxItem.Tier tier, int choice) {
        int rank = tier.ordinal();
        boolean rare = rank >= 2;
        boolean epic = rank == 3;
        ItemStack item = switch (choice) {
            case 0 -> rare
                    ? gear(Items.TRIDENT, "Zeus' Thunderbolt", "zeus", tier,
                            "A storm caught on a bronze point.",
                            "Returns when thrown. Calls lightning in a thunderstorm.",
                            ench(Enchantments.LOYALTY, 3), ench(Enchantments.CHANNELING, 1),
                            ench(Enchantments.IMPALING, epic ? 5 : 4))
                    : gear(Items.CROSSBOW, "Zeus' Stormstring", "zeus", tier,
                            "Three bolts. One command.", "A volley from the sky-lord's armory.",
                            ench(Enchantments.QUICK_CHARGE, rank + 1), ench(Enchantments.MULTISHOT, 1));
            case 1 -> rare
                    ? gear(Items.TRIDENT, "Poseidon's Trident", "poseidon", tier,
                            "The sea does not yield. It carries you.",
                            "Riptide launches you through water or rain.",
                            ench(Enchantments.RIPTIDE, epic ? 3 : 2), ench(Enchantments.IMPALING, epic ? 5 : 4))
                    : gear(Items.FISHING_ROD, "Poseidon's Tidehook", "poseidon", tier,
                            "Cast beyond the breakers.", "The tide brings its own tribute.",
                            ench(Enchantments.FISHING_SPEED, rank + 1), ench(Enchantments.FISHING_LUCK, rank + 1));
            case 2 -> gear(Items.SHIELD, "Athena's Aegis", "athena", tier,
                    "Hold the line. Let the charge break upon it.", "Wisdom is knowing when to stand firm.");
            case 3 -> gear(metal(rank, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD),
                    "Ares' Warblade", "ares", tier, "Its edge has never known a quiet age.",
                    "Made for the crush of the front line.",
                    ench(Enchantments.SHARPNESS, rank + 2), ench(Enchantments.SWEEPING_EDGE, Math.max(1, rank)),
                    ench(Enchantments.MOB_LOOTING, rank));
            case 4 -> gear(Items.BOW, "Apollo's Sunbow", "apollo", tier,
                    "Dawn reaches farther than any spear.", "Flaming arrows carry the sun into battle.",
                    ench(Enchantments.POWER_ARROWS, rank + 2), ench(Enchantments.FLAMING_ARROWS, 1),
                    ench(Enchantments.INFINITY_ARROWS, rare ? 1 : 0));
            case 5 -> gear(Items.BOW, "Artemis' Moonbow", "artemis", tier,
                    "Draw in silence. Loose without doubt.", "A hunter's answer to a charging foe.",
                    ench(Enchantments.POWER_ARROWS, rank + 2), ench(Enchantments.PUNCH_ARROWS, rare ? 2 : 1));
            case 6 -> gear(metal(rank, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS),
                    "Hermes' Winged Sandals", "hermes", tier,
                    "The messenger never waits for a road.", "Softens falls; higher tiers stride through water.",
                    ench(Enchantments.FALL_PROTECTION, Math.min(4, rank + 2)),
                    ench(Enchantments.DEPTH_STRIDER, rank), ench(Enchantments.ALL_DAMAGE_PROTECTION, rank + 1));
            case 7 -> gear(metal(rank, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE),
                    "Demeter's Harvest", "demeter", tier,
                    "An army marches on what the earth provides.", "Fortune favors the patient hand.",
                    ench(Enchantments.BLOCK_EFFICIENCY, rank + 2), ench(Enchantments.BLOCK_FORTUNE, Math.min(3, rank + 1)));
            case 8 -> gear(metal(rank, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE),
                    "Hephaestus' Forgepick", "hephaestus", tier,
                    "Every fortress begins with a spark and a stone.", "Bring the mountain back to the forge.",
                    ench(Enchantments.BLOCK_EFFICIENCY, rank + 2), ench(Enchantments.BLOCK_FORTUNE, Math.min(3, rank + 1)));
            case 9 -> gear(metal(rank, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE),
                    "Hera's Royal Mantle", "hera", tier,
                    "A crown is nothing without the strength to keep it.", "Stand beneath the queen's protection.",
                    ench(Enchantments.ALL_DAMAGE_PROTECTION, rank + 1), ench(Enchantments.THORNS, Math.min(3, rank + 1)));
            case 10 -> gear(metal(rank, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS),
                    "Aphrodite's Roseguard", "aphrodite", tier,
                    "Even a rose has its thorns.", "Beauty was never a promise of mercy.",
                    ench(Enchantments.ALL_DAMAGE_PROTECTION, rank + 1), ench(Enchantments.THORNS, Math.min(3, rank + 1)));
            case 11 -> gear(metal(rank, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET),
                    "Dionysus' Reveler's Crown", "dionysus", tier,
                    "The revel ends when you say it does.", "A deep breath before the next wild night.",
                    ench(Enchantments.ALL_DAMAGE_PROTECTION, rank + 1), ench(Enchantments.RESPIRATION, Math.min(3, rank + 1)),
                    ench(Enchantments.AQUA_AFFINITY, 1));
            default -> throw new IllegalArgumentException("Unknown Olympian armory entry: " + choice);
        };
        item.enchant(Enchantments.UNBREAKING, Math.min(3, rank + 1));
        // Infinity and Mending cannot coexist. Sunbows keep Infinity; other rare relics repair with XP.
        if (rare && choice != 4) item.enchant(Enchantments.MENDING, 1);
        if (item.getItem() instanceof net.minecraft.world.item.ArmorItem) {
            CompoundTag trim = new CompoundTag();
            trim.putString("pattern", "minecraft:" + (choice == 6 ? "tide" : choice == 11 ? "wild" : "spire"));
            trim.putString("material", "minecraft:" + (choice == 10 ? "redstone" : choice == 11 ? "amethyst" : "gold"));
            item.getOrCreateTag().put("Trim", trim);
        }
        return item;
    }

    private static Item metal(int rank, Item iron, Item diamond, Item netherite) {
        return rank == 0 ? iron : rank == 3 ? netherite : diamond;
    }

    /** Guaranteed sustenance in addition to the equipment, never a replacement for it. */
    static ItemStack provisions(LootBoxItem.Tier tier) {
        return tier == LootBoxItem.Tier.EPIC
                ? named(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "Ambrosia of Olympus", tier,
                        "Saved for the battle you cannot afford to lose.")
                : tier == LootBoxItem.Tier.COMMON
                        ? named(new ItemStack(Items.GOLDEN_CARROT, 8), "Demeter's Golden Harvest", tier,
                                "Eat well. The walls will need you.")
                        : named(new ItemStack(Items.GOLDEN_APPLE, tier == LootBoxItem.Tier.RARE ? 3 : 2),
                                "Orchard of the Hesperides", tier, "A golden harvest, guarded no longer.");
    }

    static ItemStack supplies(LootBoxItem.Tier tier, int choice) {
        int rank = tier.ordinal();
        return switch (choice) {
            case 0 -> named(new ItemStack(Items.SPECTRAL_ARROW, 16 + rank * 8), "Artemis' Moonlit Arrows", tier,
                    "Mark your quarry with light.");
            case 1 -> named(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + rank * 4), "Athena's Lessons", tier,
                    "Wisdom earned by those who came before.");
            case 2 -> named(new ItemStack(rank >= 2 ? Items.IRON_BLOCK : Items.IRON_INGOT, rank >= 2 ? 3 + rank : 8 + rank * 4),
                    "Hephaestus' Forge Stock", tier, "For the next blade, the next wall, the next war.");
            case 3 -> named(new ItemStack(Items.COOKED_BEEF, 16 + rank * 8), "Dionysus' Victory Feast", tier,
                    "Share it with the ones who held the line.");
            case 4 -> named(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),
                            rank >= 2 ? Potions.STRONG_HEALING : Potions.HEALING),
                    "Apollo's Healing Draught", tier, "Sunlight, bottled for a darker hour.");
            case 5 -> named(PotionUtils.setPotion(new ItemStack(Items.POTION),
                            rank >= 2 ? Potions.LONG_SWIFTNESS : Potions.SWIFTNESS),
                    "Hermes' Road Draught", tier, "There is still time to reach the gate.");
            case 6 -> named(PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW, 12 + rank * 4),
                            rank >= 2 ? Potions.LONG_SLOWNESS : Potions.SLOWNESS),
                    "Artemis' Snaring Arrows", tier, "Let the quarry take the slower path.");
            case 7 -> named(new ItemStack(rank >= 2 ? Items.DIAMOND : Items.GOLD_INGOT, 3 + rank),
                    "Hera's Tribute", tier, "The queen remembers those who serve.");
            default -> throw new IllegalArgumentException("Unknown Olympian supply entry: " + choice);
        };
    }

    static SimpleParticleType revealParticle(ItemStack item) {
        String patron = item.hasTag() ? item.getTag().getString(PATRON) : "";
        return switch (patron) {
            case "zeus", "apollo" -> ParticleTypes.END_ROD;
            case "poseidon" -> ParticleTypes.SPLASH;
            case "ares" -> ParticleTypes.CRIT;
            case "demeter" -> ParticleTypes.COMPOSTER;
            case "hephaestus" -> ParticleTypes.FLAME;
            case "aphrodite" -> ParticleTypes.HEART;
            case "dionysus" -> ParticleTypes.NOTE;
            default -> ParticleTypes.ENCHANT;
        };
    }

    private record Enchant(Enchantment enchantment, int level) {}
    private static Enchant ench(Enchantment enchantment, int level) { return new Enchant(enchantment, level); }

    private static ItemStack gear(Item base, String name, String patron, LootBoxItem.Tier tier,
                                  String story, String detail, Enchant... enchantments) {
        ItemStack stack = named(new ItemStack(base), name, tier, story, detail);
        stack.getOrCreateTag().putString(PATRON, patron);
        for (Enchant enchant : enchantments) if (enchant.level() > 0) stack.enchant(enchant.enchantment(), enchant.level());
        return stack;
    }

    private static ItemStack named(ItemStack stack, String name, LootBoxItem.Tier tier, String... lore) {
        stack.setHoverName(Component.literal(name).withStyle(s -> s.withColor(tier.color).withItalic(false)));
        ListTag lines = new ListTag();
        for (String line : lore) lines.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal(line).withStyle(ChatFormatting.GRAY))));
        stack.getOrCreateTagElement("display").put("Lore", lines);
        return stack;
    }
}
