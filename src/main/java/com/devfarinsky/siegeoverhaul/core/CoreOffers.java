package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** Shared faction stock; persisted independently of the placed block. */
public final class CoreOffers {
    public static final long ROTATION_TICKS = 15 * 60 * 20;
    private CoreOffers() {}
    public static int role(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("roll");
        return roll < 80 ? 0 : roll < 90 ? 1 : 2;
    }
    public static boolean refresh(CompoundTag stock, long now, RandomSource random) {
        if (stock.getIntArray("Offers").length == 3 && now < stock.getLong("RefreshAt")) return false;
        stock.putIntArray("Offers", new int[]{role(random.nextInt(100)), role(random.nextInt(100)), role(random.nextInt(100))});
        stock.putInt("Sold", 0);
        stock.putLong("RefreshAt", now + ROTATION_TICKS);
        return true;
    }
}
