package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection;
import com.devfarinsky.siegeoverhaul.core.HeroCastPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Creative testing books for the three existing area casts. Never grants survival spell powers. */
public final class OlympianSpellBookItem extends Item {
    private static final String START = "SiegeCreativeSpellStart";
    private static final String ROLE = "SiegeCreativeSpellRole";
    private final int role;
    public OlympianSpellBookItem(int role) {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
        if (role != 22 && role != 27 && role != 29) throw new IllegalArgumentException("Unknown spell");
        this.role = role;
    }
    public int role() { return role; }
    private String cooldownKey() { return "SiegeCreativeSpellNext" + role; }
    private int cooldown() { return role == 22 ? 300 : role == 27 ? 400 : 600; }
    private int radius() { return role == 22 ? 6 : role == 27 ? 8 : 10; }
    @Override public int getUseDuration(ItemStack stack) { return 20; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild || player.isSpectator() || !player.isAlive()) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("This spellbook is for Creative mode."), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            long now = level.getGameTime(), next = player.getPersistentData().getLong(cooldownKey());
            if (next > now && next <= now + 1200) return InteractionResultHolder.fail(stack);
            player.getPersistentData().putLong(cooldownKey(), now + cooldown());
            player.getPersistentData().putLong(START, now);
            player.getPersistentData().putInt(ROLE, role);
            player.getCooldowns().addCooldown(this, cooldown());
            HeroCastPackets.send(player, role, now, 0);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) return stack;
        var tag = player.getPersistentData();
        long elapsed = server.getGameTime() - tag.getLong(START);
        boolean valid = tag.contains(START) && tag.getInt(ROLE) == role && elapsed >= 20 && elapsed <= 25
                && player.getAbilities().instabuild && !player.isSpectator() && player.isAlive();
        tag.remove(START); tag.remove(ROLE); // Remove before damage callbacks: never release twice.
        if (!valid) { HeroCastPackets.send(player, role, server.getGameTime(), 2); return stack; }
        var foes = server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius()),
                target -> eligible(player, target));
        for (var target : foes) {
            if (role == 29) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
            else if (target.hurt(server.damageSources().indirectMagic(player, player), role == 22 ? 5 : 6) && role == 27)
                target.setSecondsOnFire(4);
        }
        HeroCastPackets.send(player, role, server.getGameTime(), 1);
        return stack;
    }

    static boolean eligible(Player player, LivingEntity target) {
        return target != player && !(target instanceof Player) && target.isAlive()
                && !player.isAlliedTo(target) && player.hasLineOfSight(target) && EnemyHiringProtection.enemy(target);
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!level.isClientSide && user instanceof Player player) {
            player.getPersistentData().remove(START); player.getPersistentData().remove(ROLE);
            HeroCastPackets.send(player, role, level.getGameTime(), 2);
        }
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Creative spellbook").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Hold use for 1s to cast.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(role == 29 ? "Slowness V • 4 seconds" : role == 22 ? "5 magic damage" : "6 magic damage • Burns for 4s").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Visible siege enemies • Area extends " + radius() + " blocks").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Cooldown: " + cooldown() / 20 + "s • Release early to cancel").withStyle(ChatFormatting.DARK_GRAY));
    }
}
