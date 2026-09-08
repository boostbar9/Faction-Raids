package com.devfarinsky.siegeoverhaul.core;

/** A fixed-time majority contest. Ties and empty rings pause; the opposing majority reverses progress. */
public final class CoreControl {
    private CoreControl() {}
    public static int advance(int progress, int maximum, int challengers, int owners) {
        int change = challengers > owners && challengers > 0 ? 20 : owners > challengers && owners > 0 ? -20 : 0;
        return (int) Math.max(0L, Math.min((long) maximum, (long) progress + change));
    }
}
