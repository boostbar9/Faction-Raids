package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Unified emerald payment path for Command Center purchases. Prefers the
 * faction bank so members can shop against the shared treasury, then falls
 * back to the player's own inventory.
 *
 * <p>The old code paths (hire, loot boxes, blessings) each did an
 * inventory-only pay in-place, which meant a full faction bank was useless
 * for HUD shopping. This helper centralises the logic so a single change
 * updates every purchase surface.
 *
 * <p>The bank debit runs against the same {@link RaidSavedData} that stores
 * offers and interest state, so deposits made through the HUD immediately
 * become spendable by everyone in the faction.
 */
public final class PaymentSource {

    private PaymentSource() {}

    /**
     * Peek at combined bank + inventory funds for display / affordability
     * checks. Does not mutate anything.
     */
    public static long available(ServerPlayer player, int price) {
        if (price <= 0) return 0;
        long inv = player.getInventory().items.stream()
                .filter(s -> s.is(Items.EMERALD))
                .mapToInt(ItemStack::getCount).sum();
        CompoundTag core = coreTag(player);
        long bank = core == null ? 0 : FactionBank.balance(core);
        return inv + bank;
    }

    /** True when the player has a server-side saved-data context (real world, not a unit-test mock). */
    private static boolean hasServerContext(ServerPlayer player) {
        try { return player.server != null; }
        catch (RuntimeException | NoClassDefFoundError e) { return false; }
    }

    /**
     * Attempt to consume the given number of emeralds. Bank is debited first
     * (up to `price`), then any remainder is taken from the player's
     * inventory. Returns true only if the full price was paid; on failure no
     * state is changed.
     *
     * <p>Creative-mode players pay nothing and always succeed.
     */
    public static boolean consume(ServerPlayer player, int price) {
        if (price <= 0) return true;
        if (player.isCreative()) return true;

        long inv = player.getInventory().items.stream()
                .filter(s -> s.is(Items.EMERALD))
                .mapToInt(ItemStack::getCount).sum();
        // Bank access requires a real server context; skip cleanly when running
        // under unit tests (mocked ServerPlayer without a server field).
        boolean hasContext = hasServerContext(player);
        RaidSavedData data = hasContext ? RaidSavedData.get(player.server) : null;
        CompoundTag core = hasContext ? coreTag(player) : null;
        long bank = core == null ? 0 : FactionBank.balance(core);
        if (inv + bank < price) return false;

        // Prefer to drain the bank first so shared treasury does the work.
        int fromBank = (int) Math.min(bank, price);
        int fromInv = price - fromBank;

        if (fromBank > 0) {
            if (!FactionBank.debit(core, fromBank)) {
                // Debit shouldn't fail given the balance() check, but be safe.
                return false;
            }
            if (data != null) data.setDirty();
        }
        if (fromInv > 0) {
            int remaining = fromInv;
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.EMERALD)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
            player.getInventory().setChanged();
        }
        // Toast the player so they see the split.
        if (fromBank > 0 && fromInv > 0) {
            player.displayClientMessage(Component.literal(
                    "Paid " + price + "e (" + fromBank + " from bank + " + fromInv + " from pack)"), true);
        } else if (fromBank > 0) {
            player.displayClientMessage(Component.literal(
                    "Paid " + price + "e from faction bank"), true);
        }
        return true;
    }

    private static CompoundTag coreTag(ServerPlayer player) {
        try {
            if (!hasServerContext(player)) return null;
            RaidSavedData data = RaidSavedData.get(player.server);
            return data.siegeCores.get(SiegeCore.key(player));
        } catch (RuntimeException | NoClassDefFoundError e) {
            return null;
        }
    }
}
