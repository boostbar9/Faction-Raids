package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** Shared faction stock: two military offers and one civilian, on a fixed schedule. */
public final class CoreOffers {
    public static final long ROTATION_TICKS = 15 * 60 * 20;
    public static final int WORKER_START = 4;
    public static final int[] RECRUIT_WEIGHTS = {50, 25, 20, 5};
    public static final int[] WORKER_WEIGHTS = {25, 25, 20, 15, 10, 5};
    public static final int[] HERO_WEIGHTS = {40,30,20,10};
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
        return index >= 0 && index < 4 && (index < 3 || stock.getInt("HeroRole") >= 10 && stock.getInt("HeroRole") <= 13) && valid(stock.getIntArray("Offers"))
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
            if (stock.getInt("HeroRole") < 10 || stock.getInt("HeroRole") > 13) {
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
