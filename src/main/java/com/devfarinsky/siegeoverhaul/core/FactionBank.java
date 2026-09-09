package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Server-owned emerald ledger. Core relocation preserves the faction's existing compound. */
public final class FactionBank {
    public static final long LIMIT = 1_000_000_000L;
    public static final long DAY = 86_400_000L;
    private FactionBank() {}
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
        if (settle(core, System.currentTimeMillis(), RaidConfig.BANK_INTEREST_BASIS_POINTS.get())) data.setDirty();
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
        player.displayClientMessage(net.minecraft.network.chat.Component.literal((delta>0?"Deposited ":"Withdrew ")+Math.abs(delta)+" emeralds. Faction bank: "+balance(core)+"."),true);
        data.setDirty(); player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); return true;
    }
}
