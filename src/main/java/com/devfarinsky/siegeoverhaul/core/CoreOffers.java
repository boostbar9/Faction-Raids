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
        int[] existing = stock.getIntArray("Offers");
        boolean valid = existing.length == 3 && java.util.Arrays.stream(existing).allMatch(role -> role >= 0 && role < 3);
        if (valid && now < stock.getLong("RefreshAt")) return false;
        stock.putIntArray("Offers", new int[]{role(random.nextInt(100)), role(random.nextInt(100)), role(random.nextInt(100))});
        stock.putInt("Sold", 0);
        long previous = stock.getLong("RefreshAt");
        long next = previous > 0 && previous <= now
                ? previous + ((now - previous) / ROTATION_TICKS + 1) * ROTATION_TICKS : now + ROTATION_TICKS;
        stock.putLong("RefreshAt", next);
        return true;
    }
}
