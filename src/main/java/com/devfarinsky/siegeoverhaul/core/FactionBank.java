package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Server-owned emerald ledger. Core relocation preserves the faction's existing compound. */
public final class FactionBank {
    public static final long LIMIT = 1_000_000_000L;
    public static final long DAY = 86_400_000L;
    /**
     * v4.18.0 transaction ledger: we retain the last N deltas on the core
     * so the client can render a recent-activity graph on the Bank tab.
     * Kept small so it doesn't bloat save files; the graph only needs a
     * rolling window.
     */
    public static final int LEDGER_MAX = 32;
    private static final String LEDGER_TAG = "BankLedger";

    private FactionBank() {}

    /** Append a delta (signed emeralds) to the ledger, trimming to LEDGER_MAX. */
    public static void record(CompoundTag core, int delta) {
        if (delta == 0) return;
        long packed = ((long) System.currentTimeMillis() & 0xFFFFFFFFL) << 32 | (delta & 0xFFFFFFFFL);
        long[] cur = core.getLongArray(LEDGER_TAG);
        int len = Math.min(cur.length + 1, LEDGER_MAX);
        long[] next = new long[len];
        int keep = Math.min(cur.length, LEDGER_MAX - 1);
        // shift so the newest entry sits at index len-1
        System.arraycopy(cur, cur.length - keep, next, 0, keep);
        next[len - 1] = packed;
        core.putLongArray(LEDGER_TAG, next);
    }

    /** Return the ledger as signed int deltas, oldest first. */
    public static int[] ledgerDeltas(CompoundTag core) {
        long[] cur = core.getLongArray(LEDGER_TAG);
        int[] out = new int[cur.length];
        for (int i = 0; i < cur.length; i++) out[i] = (int) cur[i];
        return out;
    }
    public static long balance(CompoundTag core) { return Math.max(0, Math.min(LIMIT, core.getLong("BankEmeralds"))); }
    public static long credit(CompoundTag core, long amount) {
        long accepted = Math.min(Math.max(0, amount), LIMIT - balance(core));
        core.putLong("BankEmeralds", balance(core) + accepted);
        return accepted;
    }
    public static boolean debit(CompoundTag core, long amount) {
        if (amount <= 0 || amount > balance(core)) return false;
        core.putLong("BankEmeralds", balance(core) - amount); return true;
    }
    public static boolean settle(CompoundTag core, long now, int basisPoints) {
        if (!core.contains("BankInterestAt")) { core.putLong("BankInterestAt", now); return true; }
        long last = core.getLong("BankInterestAt");
        if (now <= last || now - last < DAY) return false;
        long days = (now - last) / DAY;
        int rate = Math.max(0, Math.min(1000, basisPoints));
        long remainder = Math.max(0, Math.min(9999, core.getLong("BankInterestRemainder")));
        // Bounded catch-up avoids loops proportional to untrusted or ancient timestamps.
        for (long i = 0; i < Math.min(365, days); i++) {
            long interest = balance(core) * rate + remainder;
            credit(core, interest / 10000); remainder = interest % 10000;
        }
        core.putLong("BankInterestRemainder", balance(core) == LIMIT ? 0 : remainder);
        core.putLong("BankInterestAt", now - (now - last) % DAY);
        return true;
    }
    public static void settle(RaidSavedData data, CompoundTag core) {
        // v4.18.0 Territory Provisioning buff boosts bank interest by +50%.
        int base = RaidConfig.BANK_INTEREST_BASIS_POINTS.get();
        int rate = TerritoryBuffs.has(core, 2) ? (int) Math.min(Integer.MAX_VALUE, Math.round(base * 1.5)) : base;
        if (settle(core, System.currentTimeMillis(), rate)) data.setDirty();
    }
    public static boolean canWithdraw(ServerPlayer player) {
        var anchor = RaidSavedData.get(player.server).anchors.get(SiegeCore.key(player));
        return RecruitsBridge.factionLeader(player).orElse(anchor == null ? RaidSavedData.UNKNOWN_OWNER : anchor.ownerUuid()).equals(player.getUUID());
    }
    /** Positive amount deposits, negative withdraws; requests are capped at 64 and fill partially when inventory or balance is limited. */
    public static boolean transact(ServerPlayer player, int amount) {
        String key = SiegeCore.key(player); var data = RaidSavedData.get(player.server);
        CompoundTag core = data.siegeCores.get(key);
        if (core == null || amount == 0 || Math.abs((long) amount) > 64 || amount < 0 && !canWithdraw(player)) return false;
        settle(data, core);
        long before = balance(core);
        if (amount > 0) {
            int count = player.getInventory().items.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
            int deposit = (int) Math.min(Math.min(count, amount), LIMIT - balance(core));
            if (deposit <= 0) return false;
            int left = deposit;
            for (ItemStack stack : player.getInventory().items) if (stack.is(Items.EMERALD)) {
                int take = Math.min(left, stack.getCount()); stack.shrink(take); left -= take; if (left == 0) break;
            }
            credit(core, deposit);
        } else {
            int withdraw = (int) Math.min(-amount, balance(core));
            int room = 0;
            for (ItemStack stack : player.getInventory().items)
                if (stack.isEmpty()) room += 64;
                else if (stack.is(Items.EMERALD) && !stack.hasTag()) room += 64 - stack.getCount();
            withdraw = Math.min(withdraw, room);
            if (withdraw <= 0) return false;
            ItemStack emeralds = new ItemStack(Items.EMERALD, withdraw);
            player.getInventory().add(emeralds);
            int delivered = withdraw - emeralds.getCount();
            if (delivered <= 0) return false;
            debit(core, delivered);
        }
        long delta=balance(core)-before;
        // v4.18.0: log the delta so the Bank tab graph can plot recent flow.
        record(core, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, delta)));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal((delta>0?"Deposited ":"Withdrew ")+Math.abs(delta)+" emeralds. Faction bank: "+balance(core)+"."),true);
        data.setDirty(); player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); return true;
    }
}
