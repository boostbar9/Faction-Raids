package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative faction Treasury payments for every Command Center purchase. */
public final class PaymentSource {
    private PaymentSource() {}

    /** Spendable Treasury funds only; personal inventory is never a payment source. */
    public static long available(ServerPlayer player, int price) {
        CompoundTag core = coreTag(player);
        return core == null ? 0 : FactionBank.balance(core);
    }

    /** Atomically debit the entire price, or leave the Treasury unchanged. Creative stays free. */
    public static boolean consume(ServerPlayer player, int price) {
        if (price < 0 || player == null) return false;
        if (price == 0 || player.isCreative()) return true;
        CompoundTag core = coreTag(player);
        if (core == null || !FactionBank.debit(core, price)) return false;
        FactionBank.record(core, -price);
        RaidSavedData.get(player.server).setDirty();
        return true;
    }

    private static CompoundTag coreTag(ServerPlayer player) {
        if (player == null || player.server == null) return null;
        String key = SiegeCore.key(player);
        if (key.isEmpty()) return null;
        return RaidSavedData.get(player.server).siegeCores.get(key);
    }
}
