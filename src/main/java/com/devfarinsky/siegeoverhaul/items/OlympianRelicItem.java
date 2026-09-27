package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small, server-owned utility actions. No passive ticks, terrain edits or custom render loop. */
public final class OlympianRelicItem extends Item {
    static final int COOLDOWN = 200;
    static final int WATCH_RADIUS = 16;
    static final int WATCH_LIMIT = 8;
    static final List<MobEffect> AFFLICTIONS = List.of(MobEffects.POISON, MobEffects.WITHER,
            MobEffects.BLINDNESS, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN);
    private final OlympianRelics.Kind kind;

    public OlympianRelicItem(OlympianRelics.Kind kind) {
        super(new Properties().stacksTo(16).rarity(Rarity.RARE));
        this.kind = kind;
    }
    private String cooldownKey() { return "SiegeRelicNext" + kind.name(); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.isEmpty() || player.isSpectator() || !player.isAlive()) return InteractionResultHolder.fail(stack);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        long now = server.getGameTime(), next = player.getPersistentData().getLong(cooldownKey());
        if (player.getCooldowns().isOnCooldown(this) || next > now && next <= now + COOLDOWN)
            return InteractionResultHolder.fail(stack);
        boolean applied = switch (kind) {
            case FORGE -> repair(player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
            case WATCH -> reveal(server, player);
            case CLEANSE -> cleanse(player);
        };
        if (!applied) {
            // Bound repeated area scans without spending a relic on an empty search.
            player.getCooldowns().addCooldown(this, 20);
            player.displayClientMessage(Component.literal(switch (kind) {
                case FORGE -> "Hold damaged equipment in your other hand.";
                case WATCH -> "No unmarked siege enemies within 16 blocks.";
                case CLEANSE -> "No poison, wither, blindness, weakness or slowness to cleanse.";
            }), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getPersistentData().putLong(cooldownKey(), now + COOLDOWN);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.getInventory().setChanged();
        var particle = switch (kind) {
            case FORGE -> ParticleTypes.FLAME;
            case WATCH -> ParticleTypes.ENCHANT;
            case CLEANSE -> ParticleTypes.END_ROD;
        };
        server.sendParticles(particle, player.getX(), player.getY() + 1, player.getZ(), 16, .45, .45, .45, .02);
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                kind == OlympianRelics.Kind.FORGE ? SoundEvents.ANVIL_USE : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, .55F, kind == OlympianRelics.Kind.WATCH ? .8F : 1.2F);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    static boolean repair(ItemStack target) {
        if (target.isEmpty() || !target.isDamageableItem() || !target.isDamaged()) return false;
        int amount = Math.min(80, Math.max(1, target.getMaxDamage() / 4));
        int before = target.getDamageValue();
        target.setDamageValue(Math.max(0, before - amount));
        return target.getDamageValue() < before;
    }
    static boolean cleanse(Player player) {
        boolean changed = false;
        for (var effect : AFFLICTIONS) changed |= player.removeEffect(effect);
        return changed;
    }
    static boolean eligible(Player player, LivingEntity target) {
        return target != player && !(target instanceof Player) && target.isAlive() && !target.isSpectator()
                && player.distanceToSqr(target) <= WATCH_RADIUS * WATCH_RADIUS
                && !player.isAlliedTo(target) && EnemyHiringProtection.enemy(target);
    }
    static boolean reveal(ServerLevel level, Player player) {
        var targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WATCH_RADIUS),
                target -> eligible(player, target)));
        // Nearest first, stable UUID tie-break; only loaded entities returned by the level are considered.
        targets.sort(Comparator.comparingDouble((LivingEntity target) -> player.distanceToSqr(target))
                .thenComparing(LivingEntity::getUUID));
        int marked = 0;
        for (var target : targets) {
            if (marked >= WATCH_LIMIT) break;
            var current = target.getEffect(MobEffects.GLOWING);
            if (current != null && current.getDuration() >= COOLDOWN) continue;
            if (target.addEffect(new MobEffectInstance(MobEffects.GLOWING, COOLDOWN))) marked++;
        }
        return marked > 0;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        switch (kind) {
            case FORGE -> {
                tooltip.add(Component.literal("Use with damaged gear in your other hand.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Repairs 25% of max durability, up to 80 points.").withStyle(ChatFormatting.GOLD));
            }
            case WATCH -> {
                tooltip.add(Component.literal("Use to outline up to 8 siege enemies within 16 blocks.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Visible through walls to nearby players for 10 seconds.").withStyle(ChatFormatting.GOLD));
            }
            case CLEANSE -> {
                tooltip.add(Component.literal("Use to remove poison, wither, blindness,").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("weakness and slowness. Keeps beneficial effects.").withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.literal("Optional Curios charm: " + switch (kind) {
            case FORGE -> "+1 armor toughness while equipped.";
            case WATCH -> "+2 armor while equipped.";
            case CLEANSE -> "+1 maximum heart while equipped.";
        }).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Equip in the Curios screen; wearing does not consume it.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Consumed on success · 10 second cooldown").withStyle(ChatFormatting.DARK_GRAY));
    }
}
