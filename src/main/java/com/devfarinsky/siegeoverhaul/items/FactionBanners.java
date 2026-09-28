package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Random;

/**
 * Centralized banner descriptors for the five Olympian war hosts.
 *
 * <p>A "banner" here is really a two-layer stack: a base dye color plus one
 * vanilla pattern composition (the faction emblem) tinted a contrasting overlay color.
 * Siege Overhaul composes this stack into an ItemStack (for loot drops) or
 * directly into a BlockEntity NBT chunk (for camp-planted banners) via
 * {@link #applyToBlockEntityTag(CompoundTag, FactionId)}.</p>
 *
 * <p>The enum constants and string ids intentionally retain their pre-4.44
 * names. They are persistence keys used by existing raids, claims, configs and
 * trophy items; only the player-facing identity changes.</p>
 *
 * <p>The five Olympian hosts and their color language:</p>
 * <ul>
 *   <li><b>Poseidon's Tide</b>: blue base + cyan wave</li>
 *   <li><b>Warhost of Ares</b>: red base + black war-fang</li>
 *   <li><b>Forgeguard of Hephaestus</b>: black base + orange forge flame</li>
 *   <li><b>Aegis Order of Athena</b>: white base + light-blue owl crown</li>
 *   <li><b>Silver Hunt of Artemis</b>: green base + light-gray crossed weapons</li>
 * </ul>
 *
 * <p>The corresponding standing/wall banner Blocks
 * ({@code BLACK_BANNER}, etc.) are what {@link #standingBlockFor} returns for
 * the camp banner pole. Vanilla banner blocks come in one Block per dye
 * color, so we map each faction's base color to its Block.</p>
 */
public final class FactionBanners {

    /** Enum-like descriptor. Uses the same string ids as FactionLore. */
    public enum FactionId {
        BLACKBAY_REAVERS("blackbay_reavers",
                "Poseidon's Tide", "Poseidon",
                DyeColor.BLUE, DyeColor.CYAN, ModBannerPatterns.BLACKBAY_WAVE),
        HOLLOWFANG_CLAN("hollowfang_clan",
                "Warhost of Ares", "Ares",
                DyeColor.RED, DyeColor.BLACK, ModBannerPatterns.HOLLOWFANG_TUSK),
        EMBERCHANT_ZEALOTS("emberchant_zealots",
                "Forgeguard of Hephaestus", "Hephaestus",
                DyeColor.BLACK, DyeColor.ORANGE, ModBannerPatterns.EMBERCHANT_FLAME),
        CROWNFALL_EXILES("crownfall_exiles",
                "Aegis Order of Athena", "Athena",
                DyeColor.WHITE, DyeColor.LIGHT_BLUE, ModBannerPatterns.CROWNFALL_CROWN),
        WILDS_MARAUDERS("wilds_marauders",
                "Silver Hunt of Artemis", "Artemis",
                DyeColor.GREEN, DyeColor.LIGHT_GRAY, ModBannerPatterns.WILDS_CLUBS);

        public final String id;
        public final String displayName;
        public final String patron;
        public final DyeColor baseColor;
        public final DyeColor patternColor;
        private final RegistryObject<BannerPattern> pattern;

        FactionId(String id, String displayName, String patron,
                  DyeColor baseColor, DyeColor patternColor,
                  RegistryObject<BannerPattern> pattern) {
            this.id = id;
            this.displayName = displayName;
            this.patron = patron;
            this.baseColor = baseColor;
            this.patternColor = patternColor;
            this.pattern = pattern;
        }

        public BannerPattern patternOrNull() {
            return pattern.isPresent() ? pattern.get() : null;
        }

        public static FactionId byIdOrDefault(String id) {
            if (id == null) return BLACKBAY_REAVERS;
            for (FactionId f : values()) if (f.id.equals(id)) return f;
            return BLACKBAY_REAVERS;
        }
    }

    private FactionBanners() {}

    /** Random 5-way pick using the given level's random source. */
    public static FactionId pickRandom(ServerLevel level) {
        FactionId[] all = FactionId.values();
        return all[level.getRandom().nextInt(all.length)];
    }

    /** Random 5-way pick using an explicit Random (for tests or seeded picks). */
    public static FactionId pickRandom(Random rng) {
        FactionId[] all = FactionId.values();
        return all[rng.nextInt(all.length)];
    }

    /** The vanilla standing-banner Block matching this faction's base color. */
    public static Block standingBlockFor(FactionId faction) {
        return switch (faction.baseColor) {
            case WHITE -> Blocks.WHITE_BANNER;
            case ORANGE -> Blocks.ORANGE_BANNER;
            case MAGENTA -> Blocks.MAGENTA_BANNER;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_BANNER;
            case YELLOW -> Blocks.YELLOW_BANNER;
            case LIME -> Blocks.LIME_BANNER;
            case PINK -> Blocks.PINK_BANNER;
            case GRAY -> Blocks.GRAY_BANNER;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_BANNER;
            case CYAN -> Blocks.CYAN_BANNER;
            case PURPLE -> Blocks.PURPLE_BANNER;
            case BLUE -> Blocks.BLUE_BANNER;
            case BROWN -> Blocks.BROWN_BANNER;
            case GREEN -> Blocks.GREEN_BANNER;
            case RED -> Blocks.RED_BANNER;
            case BLACK -> Blocks.BLACK_BANNER;
        };
    }

    /** The matching banner Item (for loot drops) for a faction. */
    public static ItemStack itemStackFor(FactionId faction) {
        ItemStack stack = new ItemStack(bannerItemFor(faction));
        CompoundTag beTag = new CompoundTag();
        applyToBlockEntityTag(beTag, faction);
        // BlockEntityTag is the standard vanilla key for baking a BlockEntity's
        // NBT into the ItemStack so the pattern survives pickup / placement.
        stack.getOrCreateTag().put("BlockEntityTag", beTag);
        // Bake a display name using the block.minecraft.banner.<hashname>.<color> key
        // so the item tooltip identifies the Olympian host instead of a plain banner.
        return stack;
    }

    /**
     * Write the pattern stack for {@code faction} into the given BlockEntity
     * NBT tag. Used both by {@link #itemStackFor} and by direct camp banner
     * placement in {@code buildWarCamp}.
     *
     * <p>Adds one pattern layer: the faction sigil tinted with
     * {@link FactionId#patternColor}. The base color is carried by the
     * banner Block itself (e.g. {@code BLACK_BANNER}), not by an extra
     * "Base" NBT entry.</p>
     */
    public static void applyToBlockEntityTag(CompoundTag beTag, FactionId faction) {
        // Vanilla hash names: every faction banner, shield and map identity
        // uses the same layered design, with no custom symbol texture required.
        ListTag patterns = new ListTag();
        switch (faction) {
            case BLACKBAY_REAVERS -> { layer(patterns,"bs",DyeColor.CYAN); layer(patterns,"ts",DyeColor.LIGHT_BLUE); layer(patterns,"mc",DyeColor.WHITE); layer(patterns,"bo",DyeColor.BLUE); }
            case HOLLOWFANG_CLAN -> { layer(patterns,"sc",DyeColor.BLACK); layer(patterns,"mr",DyeColor.YELLOW); layer(patterns,"bo",DyeColor.BLACK); }
            case EMBERCHANT_ZEALOTS -> { layer(patterns,"bt",DyeColor.RED); layer(patterns,"bts",DyeColor.ORANGE); layer(patterns,"mc",DyeColor.YELLOW); layer(patterns,"bo",DyeColor.GRAY); }
            case CROWNFALL_EXILES -> { layer(patterns,"cs",DyeColor.LIGHT_BLUE); layer(patterns,"mr",DyeColor.BLUE); layer(patterns,"mc",DyeColor.YELLOW); layer(patterns,"bo",DyeColor.LIGHT_BLUE); }
            case WILDS_MARAUDERS -> { layer(patterns,"mc",DyeColor.WHITE); layer(patterns,"hh",DyeColor.GREEN); layer(patterns,"bo",DyeColor.LIGHT_GRAY); }
        }
        beTag.put("Patterns", patterns);
    }
    private static void layer(ListTag patterns, String pattern, DyeColor color) {
        CompoundTag layer = new CompoundTag();
        layer.putString("Pattern", pattern); layer.putInt("Color", color.getId()); patterns.add(layer);
    }

    private static net.minecraft.world.item.Item bannerItemFor(FactionId faction) {
        return switch (faction.baseColor) {
            case WHITE -> Items.WHITE_BANNER;
            case ORANGE -> Items.ORANGE_BANNER;
            case MAGENTA -> Items.MAGENTA_BANNER;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_BANNER;
            case YELLOW -> Items.YELLOW_BANNER;
            case LIME -> Items.LIME_BANNER;
            case PINK -> Items.PINK_BANNER;
            case GRAY -> Items.GRAY_BANNER;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_BANNER;
            case CYAN -> Items.CYAN_BANNER;
            case PURPLE -> Items.PURPLE_BANNER;
            case BLUE -> Items.BLUE_BANNER;
            case BROWN -> Items.BROWN_BANNER;
            case GREEN -> Items.GREEN_BANNER;
            case RED -> Items.RED_BANNER;
            case BLACK -> Items.BLACK_BANNER;
        };
    }

}
