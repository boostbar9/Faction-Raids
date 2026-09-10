package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * v4.18.0 Territory-level buffs.
 *
 * <p>Territory used to try to give tactical benefits at the block level,
 * which meant per-block iteration on every tick and stuttering claims.
 * This module replaces that with a flat, cheap set of persistent buffs
 * stored on the Siege Core's CompoundTag. Costs and effects are static;
 * checks are one NBT bit lookup.</p>
 *
 * <p>Buffs:</p>
 * <ul>
 *     <li>0 Fortified Walls: -25% raid damage against player structures near the core</li>
 *     <li>1 Watchtower: raid warnings arrive earlier + longer vote window</li>
 *     <li>2 Provisioning: +50% bank interest yield</li>
 *     <li>3 Iron Levy: recruits spawn with an extra 2 hearts of max health</li>
 * </ul>
 *
 * <p>Each buff is a one-time purchase per core. All storage lives in the
 * {@code TerritoryBuffs} bitmask on the core tag.</p>
 */
public final class TerritoryBuffs {
    public static final int[] PRICES = { 900, 600, 1200, 750 };
    public static final String[] LABELS = {
            "Fortified Walls",
            "Watchtower",
            "Provisioning",
            "Iron Levy"
    };
    public static final String[] DESCRIPTIONS = {
            "-25% raid damage to structures near your core",
            "Extra prep time and raid warnings",
            "+50% bank interest yield",
            "Recruits gain +2 hearts of max health"
    };
    public static final int COUNT = PRICES.length;
    private static final String TAG = "TerritoryBuffs";

    private TerritoryBuffs() {}

    public static int mask(CompoundTag core) {
        return core == null ? 0 : core.getInt(TAG);
    }

    public static boolean has(CompoundTag core, int index) {
        if (index < 0 || index >= COUNT) return false;
        return (mask(core) & (1 << index)) != 0;
    }

    /** Convenience for external code that only has the player's core key. */
    public static boolean has(MinecraftServer server, String coreKey, int index) {
        if (server == null || coreKey == null) return false;
        CompoundTag core = RaidSavedData.get(server).siegeCores.get(coreKey);
        return has(core, index);
    }

    public static int price(int index) {
        if (index < 0 || index >= COUNT) return -1;
        return PRICES[index];
    }

    public static String label(int index) {
        if (index < 0 || index >= COUNT) return "";
        return LABELS[index];
    }

    public static String description(int index) {
        if (index < 0 || index >= COUNT) return "";
        return DESCRIPTIONS[index];
    }

    /**
     * Charge the player and unlock the given buff on their current core.
     * Bank first, then inventory. No-op when already owned.
     */
    public static boolean purchase(ServerPlayer player, BlockPos corePos, int index) {
        if (player == null || index < 0 || index >= COUNT) return false;
        RaidSavedData saved = RaidSavedData.get(player.server);
        CompoundTag core = saved.siegeCores.get(SiegeCore.key(player));
        if (core == null) return false;
        if (has(core, index)) {
            player.sendSystemMessage(Component.literal(LABELS[index] + " already active."));
            return false;
        }
        int price = PRICES[index];
        long combined = PaymentSource.available(player, price);
        if (!player.isCreative() && combined < price) {
            player.sendSystemMessage(Component.literal(
                    "You need " + price + " emeralds for " + LABELS[index] + "."));
            return false;
        }
        if (!player.isCreative() && !PaymentSource.consume(player, price)) return false;
        core.putInt(TAG, mask(core) | (1 << index));
        saved.setDirty();
        player.sendSystemMessage(Component.literal(LABELS[index] + " activated for your faction."));
        return true;
    }
}
