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
        // Consume first so opening the last box also frees its inventory slot.
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        deliver(sp, loot);

        // Chest-open cue + a small burst of firework-star particles above
        // the player so opening a box has a moment of pop, not just a
        // silent inventory update.
        serverLevel.playSound(null, sp.getX(), sp.getY() + 0.5D, sp.getZ(),
                SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.7F, 1.1F);
        serverLevel.sendParticles(ParticleTypes.FIREWORK,
                sp.getX(), sp.getY() + 1.6D, sp.getZ(),
                18, 0.35D, 0.25D, 0.35D, 0.02D);

        serverLevel.sendParticles(OlympianLoot.revealParticle(loot.get(0)),
                sp.getX(), sp.getY() + 1.2D, sp.getZ(),
                12, 0.4D, 0.3D, 0.4D, 0.02D);
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.9F + tier.ordinal() * 0.15F);

        // Announce the tier in chat so the player knows what they just
        // opened - the item name is already color-tinted by vanilla Rarity.
        sp.displayClientMessage(
                Component.literal("Opened a ")
                        .append(Component.literal(tier.label + " Loot Box").withStyle(tier.color))
                        .append(Component.literal(": "))
                        .append(loot.get(0).getHoverName().copy())
                        .append(Component.literal(" and " + (loot.size() - 1) + " supply stacks.")),
                false);

        // Delivery may have filled the newly empty main-hand slot. Return its current contents
        // so ServerPlayerGameMode does not replace the delivered equipment with the spent box.
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(tier.label + " rarity").withStyle(tier.color));
        tooltip.add(Component.literal("Enchanted Olympian equipment and supplies").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click to open").withStyle(ChatFormatting.GRAY));
    }

    /** Inventory.add mutates its argument: only the remaining count may be dropped. */
    static void deliver(ServerPlayer player, List<ItemStack> loot) {
        for (ItemStack reward : loot) {
            ItemStack remaining = reward.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) player.drop(remaining, false);
        }
        player.getInventory().setChanged();
    }

    /** One armory piece, provisions, a usable relic, then distinct supplies (3-7 total stacks). */
    public static List<ItemStack> roll(RandomSource rng, Tier tier) {
        int stacks = 3 + tier.ordinal() + rng.nextInt(2);
        List<ItemStack> out = new ArrayList<>(stacks);
        int[] armory = OlympianLoot.availableArmory(tier);
        ItemStack equipment = OlympianLoot.armory(tier, armory[rng.nextInt(armory.length)]);
        out.add(equipment);
        out.add(OlympianLoot.provisions(tier));
        int support = OlympianLoot.RELIC_FIRST + rng.nextInt(OlympianRelics.Kind.values().length);
        out.add(OlympianLoot.supplies(tier, support, equipment));
        // Reserve a genuine utility relic, then sample remaining categories
        // without replacement. No extra stacks, duplicate categories or currency are added.
        int[] choices = new int[OlympianLoot.AVAILABLE_SUPPLIES.length - 1];
        for (int category = 0, at = 0; category < OlympianLoot.SUPPLY_TYPES; category++) {
            if (category != support && OlympianLoot.availableSupply(category)) choices[at++] = category;
        }
        for (int i = 0; i < stacks - 3; i++) {
            int selected = i + rng.nextInt(choices.length - i);
            int choice = choices[selected];
            choices[selected] = choices[i];
            choices[i] = choice;
            out.add(OlympianLoot.supplies(tier, choice, equipment));
        }
        return out;
    }

    /** Fresh read-only examples from the exact eligible equipment pool used by roll().
     * Does not advance an RNG or inspect any player's sealed reward. */
    public static List<ItemStack> armoryPreviews(Tier tier) {
        java.util.Objects.requireNonNull(tier, "tier");
        return java.util.Arrays.stream(OlympianLoot.availableArmory(tier))
                .mapToObj(choice -> OlympianLoot.armory(tier, choice)).toList();
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
