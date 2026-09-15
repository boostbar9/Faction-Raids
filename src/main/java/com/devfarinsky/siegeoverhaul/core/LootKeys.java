package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * War keys — loot crates earned by fighting instead of by paying.
 *
 * <p>Every enemy raider a player kills banks one point of progress. Once the
 * configured number of kills is reached the player is granted a war key, and
 * a key opens any Command Center crate for free. Keys and progress live in the
 * player's persisted NBT so they survive logout, death and dimension changes,
 * and every mutation is server-side only.
 *
 * <p>All state is namespaced under {@code SiegeOverhaul...} keys to avoid
 * collisions with other mods writing into the same persisted compound.
 */
public final class LootKeys {

    /** Banked, unspent crate keys. */
    public static final String KEYS = "SiegeOverhaulWarKeys";
    /** Kills counted toward the next key. */
    public static final String PROGRESS = "SiegeOverhaulWarKeyKills";

    /** Never let a stockpile grow without bound. */
    public static final int MAX_KEYS = 64;

    private LootKeys() {}

    private static CompoundTag data(Player player) {
        return player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag mutable(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        // getCompound returns a detached empty tag when the key is absent, so
        // it has to be written back before the first mutation is kept.
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    /** Unspent keys held by {@code player}. */
    public static int keys(Player player) {
        return Math.max(0, data(player).getInt(KEYS));
    }

    /** Kills banked toward the next key. */
    public static int progress(Player player) {
        return Math.max(0, data(player).getInt(PROGRESS));
    }

    /** Kills required per key; always at least one so progress cannot stall. */
    public static int killsPerKey() {
        return Math.max(1, RaidConfig.LOOT_KEY_KILLS.get());
    }

    /**
     * Credit one enemy kill. Returns the number of keys granted by this kill
     * (normally 0 or 1; a {@code bonus} above one key's worth can grant more).
     *
     * @param bonus extra progress on top of the kill itself, never negative
     */
    public static int credit(ServerPlayer player, int bonus) {
        if (!RaidConfig.ENABLE_KILL_LOOT_KEYS.get()) return 0;
        CompoundTag tag = mutable(player);
        int needed = killsPerKey();
        int progress = Math.max(0, tag.getInt(PROGRESS)) + 1 + Math.max(0, bonus);
        int earned = progress / needed;
        int held = Math.max(0, tag.getInt(KEYS));
        // Cap the stockpile without discarding leftover progress, so a player
        // at the cap simply stops banking new keys rather than losing kills.
        int granted = Math.max(0, Math.min(earned, MAX_KEYS - held));
        tag.putInt(PROGRESS, progress % needed);
        if (granted > 0) {
            tag.putInt(KEYS, held + granted);
            player.sendSystemMessage(Component.literal(granted == 1
                    ? "War key earned — open any war chest for free."
                    : granted + " war keys earned — open any war chest for free."));
        }
        return granted;
    }

    /** Spend one key. Returns false when the player has none. */
    public static boolean spend(ServerPlayer player) {
        CompoundTag tag = mutable(player);
        int held = Math.max(0, tag.getInt(KEYS));
        if (held <= 0) return false;
        tag.putInt(KEYS, held - 1);
        return true;
    }
}
