package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** Shared faction stock: two military offers and one civilian, on a fixed schedule. */
public final class CoreOffers {
    public static final long ROTATION_TICKS = 15 * 60 * 20;
    public static final int WORKER_START = 4;
    public static final int[] RECRUIT_WEIGHTS = {50, 25, 20, 5};
    public static final int[] WORKER_WEIGHTS = {25, 25, 20, 15, 10, 5};
    /**
     * v4.19.0 rarity spread across 20 heroes (roles 10-29):
     *   Common (2) at 15 each = 30
     *   Uncommon (4) at 8 each = 32
     *   Rare (6) at 4 each     = 24
     *   Epic (5) at 2 each     = 10
     *   Legendary (3) at ~1.3  = 4  (2 + 1 + 1)
     * Sums to 100. Legendaries stay genuinely rare so the tier feels earned.
     */
    public static final int[] HERO_WEIGHTS = {
        15, 15,              // Common
        8, 8, 8, 8,          // Uncommon
        4, 4, 4, 4, 4, 4,    // Rare
        2, 2, 2, 2, 2,       // Epic
        2, 1, 1              // Legendary
    };
    public static int hero(int roll) { return 10 + weighted(roll,HERO_WEIGHTS); }
    private CoreOffers() {}
    public static int role(int roll) { return weighted(roll, RECRUIT_WEIGHTS); }
    public static int worker(int roll) { return WORKER_START + weighted(roll, WORKER_WEIGHTS); }
    private static int weighted(int roll, int[] weights) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("roll");
        for (int i = 0; i < weights.length; i++) { roll -= weights[i]; if (roll < 0) return i; }
        throw new IllegalStateException("Weights must total 100");
    }
    public static boolean valid(int[] offers) {
        return offers.length == 3 && offers[0] >= 0 && offers[0] < WORKER_START
                && offers[1] >= 0 && offers[1] < WORKER_START
                && offers[2] >= WORKER_START && offers[2] < WORKER_START + WORKER_WEIGHTS.length;
    }
    public static boolean canPurchase(CompoundTag stock, int index, long rotation) {
        int hero = stock.getInt("HeroRole");
        return index >= 0 && index < 4 && (index < 3 || (hero >= 10 && hero <= 29)) && valid(stock.getIntArray("Offers"))
                && rotation == stock.getLong("RefreshAt") && (stock.getInt("Sold") & (1 << index)) == 0;
    }
    public static boolean refresh(CompoundTag stock, long now, RandomSource random) {
        int[] existing = stock.getIntArray("Offers");
        // Migrate only the civilian slot. Preserve both military offers, sold bits and deadline.
        if (!stock.contains("OfferSchema") && existing.length == 3
                && java.util.Arrays.stream(existing).allMatch(role -> role >= 0 && role < 3)
                && now < stock.getLong("RefreshAt")) {
            existing[2] = worker(random.nextInt(100));
            stock.putIntArray("Offers", existing);
            stock.putInt("OfferSchema", 2);
            stock.putInt("HeroRole",hero(random.nextInt(100)));
            return true;
        }
        if (valid(existing) && now < stock.getLong("RefreshAt")) {
            int hero = stock.getInt("HeroRole");
            if (hero < 10 || hero > 29) {
                stock.putInt("HeroRole",hero(random.nextInt(100))); return true;
            }
            return false;
        }
        stock.putIntArray("Offers", new int[]{role(random.nextInt(100)), role(random.nextInt(100)), worker(random.nextInt(100))});
        stock.putInt("OfferSchema", 2);
        stock.putInt("Sold", 0);
        stock.putInt("HeroRole",hero(random.nextInt(100)));
        long previous = stock.getLong("RefreshAt");
        long next = previous > 0 && previous <= now
                ? previous + ((now - previous) / ROTATION_TICKS + 1) * ROTATION_TICKS : now + ROTATION_TICKS;
        stock.putLong("RefreshAt", next);
        return true;
    }
}
