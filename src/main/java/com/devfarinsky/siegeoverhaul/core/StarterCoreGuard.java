package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

/** One normal, free hired shieldman per faction; no replacement on death. */
public final class StarterCoreGuard {
    static final String PENDING_OWNER = "CoreGuardPendingOwner";
    private static final String OUTFITTED = "SiegeStarterCoreGuard";

    private StarterCoreGuard() {}

    /** Runs on the core's scheduled tick, after Forge can finish or cancel placement. */
    public static void onCoreTick(ServerLevel level, BlockPos pos) {
        RaidSavedData data = RaidSavedData.get(level.getServer());
        for (var core : data.siegeCores.values()) {
            if (!core.contains("Position") || core.getLong("Position") != pos.asLong()
                    || !core.hasUUID(PENDING_OWNER)) continue;
            ServerPlayer placer = level.getServer().getPlayerList().getPlayer(core.getUUID(PENDING_OWNER));
            if (placer != null && tryGrant(placer, pos)) return;
        }
    }

    /** Opening the owned core retries failed placement attempts and supports existing saves. */
    public static boolean tryGrant(ServerPlayer player, BlockPos pos) {
        if (!SiegeCore.canUse(player, pos)) return false;
        RaidSavedData data = RaidSavedData.get(player.server);
        String key = SiegeCore.key(player);
        if (!grantOnce(data, key, () -> CoreHiring.hireStarterGuard(player, pos))) return false;
        var core = data.siegeCores.get(key);
        if (core != null) core.remove(PENDING_OWNER);
        data.setDirty();
        player.sendSystemMessage(Component.literal(
                "Your Core Guard is ready. Recruit more soldiers and prepare your defenses."));
        return true;
    }

    /** Reserve before native callbacks; retain only a successful hire. Server-thread only. */
    static boolean grantOnce(RaidSavedData data, String key, BooleanSupplier hire) {
        if (key == null || !key.startsWith("team:") || key.length() <= 5
                || !data.coreGuardGrants.add(key)) return false;
        boolean success = false;
        try {
            success = hire.getAsBoolean();
            return success;
        } finally {
            if (!success) data.coreGuardGrants.remove(key);
            data.setDirty();
        }
    }

    /** Native equipment slots and finite food, with ordinary recruit attributes. */
    static void prepare(Mob recruit, SimpleContainer inventory) {
        if (recruit.getPersistentData().getBoolean(OUTFITTED)) return;
        if (inventory.getContainerSize() < 7) throw new IllegalStateException("Core Guard inventory too small");
        Item[] items = {Items.CHAINMAIL_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS,
                Items.LEATHER_BOOTS, Items.SHIELD, Items.IRON_SWORD};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND};
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = new ItemStack(items[i]);
            inventory.setItem(i, stack);
            recruit.setItemSlot(slots[i], stack);
        }
        inventory.setItem(6, new ItemStack(Items.BREAD, 8));
        recruit.setCustomName(Component.literal("Core Guard"));
        recruit.getPersistentData().putBoolean(OUTFITTED, true);
        inventory.setChanged();
    }
}
