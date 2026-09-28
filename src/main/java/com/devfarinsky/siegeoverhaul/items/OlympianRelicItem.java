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
import net.minecraft.world.entity.EquipmentSlot;
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
            case FORGE -> repairEquipment(player, hand);
            case WATCH -> reveal(server, player);
            case CLEANSE -> cleanse(player);
        };
        if (!applied) {
            // Bound repeated area scans without spending a relic on an empty search.
            player.getCooldowns().addCooldown(this, 20);
            player.displayClientMessage(Component.literal(switch (kind) {
                case FORGE -> "Hold damaged gear in your other hand or wear damaged armor.";
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

    static boolean repairEquipment(Player player, InteractionHand hand) {
        var other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (repair(other)) return true;
        ItemStack worn = ItemStack.EMPTY;
        double mostDamage = 0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            var armor = player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem() && armor.isDamaged()) {
                double fraction = (double) armor.getDamageValue() / armor.getMaxDamage();
                if (fraction > mostDamage) { worn = armor; mostDamage = fraction; }
            }
        }
        return repair(worn);
    }

    static boolean repair(ItemStack target) {
        if (target.isEmpty() || !target.isDamageableItem() || !target.isDamaged()) return false;
        int amount = Math.min(400, Math.max(1, target.getMaxDamage() / 4));
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
            if (current != null && (current.isInfiniteDuration() || current.getDuration() >= COOLDOWN)) continue;
            if (target.addEffect(new MobEffectInstance(MobEffects.GLOWING, COOLDOWN))) marked++;
        }
        return marked > 0;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Olympian relic").withStyle(ChatFormatting.GOLD));
        switch (kind) {
            case FORGE -> {
                tooltip.add(Component.literal("Use: repair gear in your other hand.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Otherwise repairs your most worn armor piece.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Restores 25% max durability • maximum 400 points").withStyle(ChatFormatting.AQUA));
            }
            case WATCH -> {
                tooltip.add(Component.literal("Use: reveal nearby siege enemies.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Up to 8 targets • 16 blocks • 10 seconds").withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.literal("Outlines are visible through walls.").withStyle(ChatFormatting.GRAY));
            }
            case CLEANSE -> {
                tooltip.add(Component.literal("Use: cleanse harmful effects.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Poison, wither, blindness, weakness, slowness").withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.literal("Keeps your beneficial effects.").withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.literal("Consumed on success • Cooldown: 10s").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Curios charm (optional)").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(switch (kind) {
            case FORGE -> "+1 armor toughness";
            case WATCH -> "+2 armor";
            case CLEANSE -> "+1 maximum heart";
        }).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Equip in a charm slot. Not consumed while worn.").withStyle(ChatFormatting.GRAY));
    }
}
