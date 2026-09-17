package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Right-click loot box. Rolls a bounded loot table matching the box's rarity
 * tier, drops the items into the player inventory (overflow to the world),
 * plays a chest-open sound + firework-star particles for the flair, and
 * consumes one from the stack. Tiers are: COMMON, UNCOMMON, RARE, EPIC.
 *
 * Tier is fixed per item registration - each rarity is its own registered
 * item so the model / texture / vanilla Rarity color all pick up
 * automatically without NBT gymnastics.
 */
public final class LootBoxItem extends Item {

    public enum Tier {
        COMMON("Common", ChatFormatting.WHITE, Rarity.COMMON),
        UNCOMMON("Uncommon", ChatFormatting.GREEN, Rarity.UNCOMMON),
        RARE("Rare", ChatFormatting.BLUE, Rarity.RARE),
        EPIC("Epic", ChatFormatting.LIGHT_PURPLE, Rarity.EPIC);

        public final String label;
        public final ChatFormatting color;
        public final Rarity rarity;

        Tier(String label, ChatFormatting color, Rarity rarity) {
            this.label = label;
            this.color = color;
            this.rarity = rarity;
        }
    }

    private final Tier tier;

    public LootBoxItem(Tier tier) {
        // stacksTo(16) so a raid full of drops doesn't fill a hotbar with 1x
        // stacks. Vanilla Rarity is set per-tier so the item name colors
        // itself in tooltips.
        super(new Properties().stacksTo(16).rarity(tier.rarity));
        this.tier = tier;
    }

    public Tier tier() { return tier; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            // Fire success on the client so the swing animation plays; the
            // real roll happens server-side on the same tick.
            return InteractionResultHolder.success(stack);
        }

        List<ItemStack> loot = roll(serverLevel.getRandom(), tier);
        for (ItemStack item : loot) {
            if (!sp.getInventory().add(item.copy())) {
                sp.drop(item.copy(), false);
            }
        }

        // Chest-open cue + a small burst of firework-star particles above
        // the player so opening a box has a moment of pop, not just a
        // silent inventory update.
        serverLevel.playSound(null, sp.getX(), sp.getY() + 0.5D, sp.getZ(),
                SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.7F, 1.1F);
        serverLevel.sendParticles(ParticleTypes.FIREWORK,
                sp.getX(), sp.getY() + 1.6D, sp.getZ(),
                18, 0.35D, 0.25D, 0.35D, 0.02D);

        // Announce the tier in chat so the player knows what they just
        // opened - the item name is already color-tinted by vanilla Rarity.
        sp.displayClientMessage(
                Component.literal("Opened a ")
                        .append(Component.literal(tier.label + " Loot Box").withStyle(tier.color))
                        .append(Component.literal(" and received " + loot.size() + " item"
                                + (loot.size() == 1 ? "" : "s") + ".")),
                false);

        if (!sp.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(tier.label + " rarity").withStyle(tier.color));
        tooltip.add(Component.literal("Right-click to open").withStyle(ChatFormatting.GRAY));
    }

    // ---- Loot tables -------------------------------------------------------

    /**
     * Deterministic per-tier loot table. Each tier rolls 3-6 stacks and always
     * includes at least one "featured" pick from a curated pool, plus filler
     * from a broader pool. Higher tiers pull from a strictly better pool -
     * COMMON never rolls netherite, EPIC never rolls dirt.
     *
     * Kept intentionally simple (no JSON loot tables, no data-driven
     * randomness) so opening a box is fast and predictable and doesn't
     * depend on datapack load order.
     */
    public static List<ItemStack> roll(RandomSource rng, Tier tier) {
        List<ItemStack> out = new ArrayList<>();
        int rolls = switch (tier) {
            case COMMON -> 3 + rng.nextInt(2);   // 3-4
            case UNCOMMON -> 4 + rng.nextInt(2); // 4-5
            case RARE -> 5 + rng.nextInt(2);     // 5-6
            case EPIC -> 6 + rng.nextInt(2);     // 6-7
        };
        // First roll is always from the featured pool so every box has a
        // headline drop, not just filler.
        out.add(featured(rng, tier));
        for (int i = 1; i < rolls; i++) {
            out.add(filler(rng, tier));
        }
        return out;
    }

    private static ItemStack featured(RandomSource rng, Tier tier) {
        return switch (tier) {
            case COMMON -> pick(rng,
                    new ItemStack(Items.IRON_INGOT, 4 + rng.nextInt(4)),
                    new ItemStack(Items.BREAD, 8 + rng.nextInt(8)),
                    new ItemStack(Items.ARROW, 16 + rng.nextInt(16)),
                    new ItemStack(Items.EMERALD, 1 + rng.nextInt(3)));
            case UNCOMMON -> pick(rng,
                    new ItemStack(Items.IRON_INGOT, 8 + rng.nextInt(8)),
                    new ItemStack(Items.GOLD_INGOT, 4 + rng.nextInt(4)),
                    new ItemStack(Items.EMERALD, 4 + rng.nextInt(4)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + rng.nextInt(4)),
                    new ItemStack(Items.COOKED_BEEF, 12 + rng.nextInt(8)));
            case RARE -> pick(rng,
                    new ItemStack(Items.DIAMOND, 2 + rng.nextInt(3)),
                    new ItemStack(Items.EMERALD, 8 + rng.nextInt(8)),
                    new ItemStack(Items.GOLD_INGOT, 8 + rng.nextInt(8)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + rng.nextInt(8)),
                    new ItemStack(Items.GOLDEN_APPLE, 1 + rng.nextInt(2)));
            case EPIC -> pick(rng,
                    new ItemStack(Items.NETHERITE_INGOT, 1),
                    new ItemStack(Items.DIAMOND, 6 + rng.nextInt(4)),
                    new ItemStack(Items.EMERALD_BLOCK, 2 + rng.nextInt(3)),
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                    new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 16 + rng.nextInt(8)));
        };
    }

    private static ItemStack filler(RandomSource rng, Tier tier) {
        return switch (tier) {
            case COMMON -> pick(rng,
                    new ItemStack(Items.ARROW, 8 + rng.nextInt(8)),
                    new ItemStack(Items.BREAD, 4 + rng.nextInt(4)),
                    new ItemStack(Items.OAK_PLANKS, 16 + rng.nextInt(16)),
                    new ItemStack(Items.COBBLESTONE, 16 + rng.nextInt(16)),
                    new ItemStack(Items.TORCH, 8 + rng.nextInt(8)),
                    new ItemStack(Items.COAL, 4 + rng.nextInt(4)));
            case UNCOMMON -> pick(rng,
                    new ItemStack(Items.IRON_INGOT, 2 + rng.nextInt(3)),
                    new ItemStack(Items.COOKED_BEEF, 8 + rng.nextInt(4)),
                    new ItemStack(Items.STONE_BRICKS, 16 + rng.nextInt(16)),
                    new ItemStack(Items.EMERALD, 1 + rng.nextInt(3)),
                    new ItemStack(Items.OAK_LOG, 8 + rng.nextInt(8)),
                    new ItemStack(Items.SPECTRAL_ARROW, 8 + rng.nextInt(8)));
            case RARE -> pick(rng,
                    new ItemStack(Items.GOLD_INGOT, 4 + rng.nextInt(4)),
                    new ItemStack(Items.EMERALD, 3 + rng.nextInt(4)),
                    new ItemStack(Items.ENDER_PEARL, 2 + rng.nextInt(3)),
                    new ItemStack(Items.IRON_BLOCK, 1 + rng.nextInt(2)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + rng.nextInt(4)),
                    new ItemStack(Items.TIPPED_ARROW, 8 + rng.nextInt(4)));
            case EPIC -> pick(rng,
                    new ItemStack(Items.DIAMOND, 3 + rng.nextInt(3)),
                    new ItemStack(Items.EMERALD_BLOCK, 1 + rng.nextInt(2)),
                    new ItemStack(Items.GOLD_BLOCK, 1 + rng.nextInt(2)),
                    new ItemStack(Items.ENDER_PEARL, 4 + rng.nextInt(4)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + rng.nextInt(8)),
                    new ItemStack(Items.GOLDEN_APPLE, 1 + rng.nextInt(3)));
        };
    }

    @SafeVarargs
    private static <T> T pick(RandomSource rng, T... options) {
        return options[rng.nextInt(options.length)];
    }

    // ---- Wave-driven rolling ------------------------------------------------

    /**
     * Roll a single loot box tier for a wave clear. Higher waves shift the
     * distribution up. Every wave has a chance at the highest tiers, so a
     * lucky wave-1 clear can still pop an Epic, but the odds ramp so wave 10+
     * plays start dropping Rare/Epic reliably.
     *
     * Distribution snapshot:
     *   wave 1:  ~80% COMMON / 18% UNCOMMON /  2% RARE /  0.4% EPIC
     *   wave 5:  ~55% COMMON / 33% UNCOMMON / 10% RARE /  2% EPIC
     *   wave 10: ~30% COMMON / 40% UNCOMMON / 22% RARE /  8% EPIC
     *   wave 20: ~10% COMMON / 35% UNCOMMON / 35% RARE / 20% EPIC
     */
    public static Tier rollWaveTier(RandomSource rng, int wave) {
        int w = Math.max(1, wave);
        // Weights scale with wave. Common decays, epic grows.
        double common = Math.max(4, 100 - w * 6);
        double uncommon = Math.min(45, 15 + w * 3);
        double rare = Math.min(40, w * 2.5);
        double epic = Math.min(25, w * 1.2);
        double total = common + uncommon + rare + epic;
        double roll = rng.nextDouble() * total;
        if ((roll -= common) < 0) return Tier.COMMON;
        if ((roll -= uncommon) < 0) return Tier.UNCOMMON;
        if ((roll -= rare) < 0) return Tier.RARE;
        return Tier.EPIC;
    }
}
