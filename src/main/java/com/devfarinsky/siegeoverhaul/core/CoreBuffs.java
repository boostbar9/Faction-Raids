package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;

/** Fixed, temporary personal blessings. Existing stronger/longer effects are never overwritten. */
public final class CoreBuffs {
    public static final String[] NAMES = {"Windstep", "Warborn", "Aegis"};
    public static final String[] DETAILS = {"Speed I", "Strength I", "Resistance I"};
    public static final int[] PRICES = {16,24,32};
    public static final int DURATION = 5 * 60 * 20;
    private CoreBuffs() {}
    public static MobEffect effect(int index) {
        return switch(index) { case 0 -> MobEffects.MOVEMENT_SPEED; case 1 -> MobEffects.DAMAGE_BOOST; case 2 -> MobEffects.DAMAGE_RESISTANCE; default -> null; };
    }
    public static boolean purchase(ServerPlayer player, int index) {
        MobEffect effect = effect(index);
        if (effect == null || player.hasEffect(effect) || !player.isAlive() || player.isSpectator()) return false;
        int price = PRICES[index];
        if (player.getInventory().items.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() < price) return false;
        if (!player.addEffect(new MobEffectInstance(effect, DURATION, 0, false, true, true))) return false;
        int left = price;
        for (ItemStack stack : player.getInventory().items) if (stack.is(Items.EMERALD)) {
            int take = Math.min(left, stack.getCount()); stack.shrink(take); left -= take; if (left == 0) break;
        }
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); return true;
    }
}
