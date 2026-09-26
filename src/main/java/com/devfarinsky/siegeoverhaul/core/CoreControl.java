package com.devfarinsky.siegeoverhaul.core;

/** A fixed-time majority contest. Ties pause; an opposing majority or abandonment reverses progress. */
public final class CoreControl {
    private CoreControl() {}
    public static int advance(int progress, int maximum, int challengers, int owners) {
        int change = challengers > owners && challengers > 0 ? 20 : owners > challengers || challengers == 0 ? -20 : 0;
        return (int) Math.max(0L, Math.min((long) maximum, (long) progress + change));
    }

    /** Preserve legacy progress on first observation, but never carry it to a different owner. */
    static void bindOwner(net.minecraft.nbt.CompoundTag tag, String progressKey, String owner) {
        String ownerKey = progressKey + "Owner";
        if (tag.contains(ownerKey) && !owner.equals(tag.getString(ownerKey))) tag.putInt(progressKey, 0);
        tag.putString(ownerKey, owner);
    }
}
