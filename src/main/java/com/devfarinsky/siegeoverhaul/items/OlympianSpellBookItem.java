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

/** Creative casting books with aimed projectiles and a held water channel. */
public final class OlympianSpellBookItem extends Item {
    private static final String START = "SiegeCreativeSpellStart";
    private static final String ROLE = "SiegeCreativeSpellRole";
    private final int role;
    public OlympianSpellBookItem(int role) {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
        if (!com.devfarinsky.siegeoverhaul.spells.OlympianBolt.validKind(role)) throw new IllegalArgumentException("Unknown spell");
        this.role = role;
    }
    public int role() { return role; }
    private String cooldownKey() { return "SiegeCreativeSpellNext" + role; }
    private int cooldown() { return role == 22 ? 300 : role == 27 ? 400 : role == 30 ? 100 : 600; }
    @Override public int getUseDuration(ItemStack stack) { return role == 29 ? 60 : 20; }
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
        boolean valid = tag.contains(START) && tag.getInt(ROLE) == role && elapsed >= getUseDuration(stack) && elapsed <= getUseDuration(stack) + 5
                && player.getAbilities().instabuild && !player.isSpectator() && player.isAlive();
        tag.remove(START); tag.remove(ROLE); // Remove before damage callbacks: never release twice.
        if (!valid) { HeroCastPackets.send(player, role, server.getGameTime(), 2); return stack; }
        if (role != 29) com.devfarinsky.siegeoverhaul.spells.OlympianBolt.launch(server, player, role);
        HeroCastPackets.send(player, role, server.getGameTime(), 2); // Flight/impact effects replace the old caster-centered blast.
        return stack;
    }

    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (role != 29 || !(level instanceof ServerLevel) || !(user instanceof Player player)) return;
        var tag=player.getPersistentData();
        long age=level.getGameTime()-tag.getLong(START);
        if (tag.contains(START) && tag.getInt(ROLE)==role && age>=20 && age<60 && age%5==0
                && player.isAlive() && !player.isSpectator() && player.getAbilities().instabuild
                && tag.getLong("SiegeWaterPulse")!=level.getGameTime()) {
            tag.putLong("SiegeWaterPulse",level.getGameTime());
            com.devfarinsky.siegeoverhaul.spells.OlympianBolt.launch(level,player,role);
        }
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!level.isClientSide && user instanceof Player player) {
            player.getPersistentData().remove(START); player.getPersistentData().remove(ROLE);
            HeroCastPackets.send(player, role, level.getGameTime(), 2);
        }
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Creative spellbook").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(role == 29 ? "Hold use: charge for 1s, then channel for 2s." : "Hold use for 1s. Aim to cast.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(role == 29 ? "Water stream • Pushes and slows siege enemies" : role == 30 ? "Ice bolt • 6 damage • Slowness II for 4s" : role == 22 ? "Lightning bolt • 5 magic damage" : "Fireball • 6 damage • Burns for 4s").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Range: up to 48 blocks • Stops at walls • No terrain damage").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Cooldown: " + cooldown() / 20 + "s • Release early to cancel").withStyle(ChatFormatting.DARK_GRAY));
    }
}
