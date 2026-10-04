package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.items.LootBoxItem;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A single wave's frozen recipients and rolled tiers, retained across ordinary saves/reloads.
 * Delivery is consumed before inventory/world mutation: an interrupted attempt is never replayed.
 * World SavedData, player inventories and dropped entities are not an atomic disk transaction;
 * this deliberately favors avoiding duplicate grants over retrying uncertain deliveries.
 */
public final class WaveLootRewards {
    private static final int VERSION = 1;
    static final int MAX_RECIPIENTS = 1024;
    private int wave;
    private final Map<UUID, Receipt> recipients = new LinkedHashMap<>();

    private static final class Receipt {
        LootBoxItem.Tier tier;
        boolean attempted;
    }

    /** The supplied list is the server's current, authoritative online faction roster. */
    public static void awardClearedWave(RaidSavedData data, RaidSavedData.RaidState state,
                                       List<ServerPlayer> members) {
        awardClearedWave(data, state, members,
                player -> LootBoxItem.rollWaveTier(player.getRandom(), state.wave),
                tier -> new ItemStack(ModItems.lootBox(tier).get()));
    }

    static void awardClearedWave(RaidSavedData data, RaidSavedData.RaidState state,
                                List<ServerPlayer> members,
                                Function<ServerPlayer, LootBoxItem.Tier> roll,
                                Function<LootBoxItem.Tier, ItemStack> boxFactory) {
        if (state == null || state.wave <= 0 || state.preparationTicks > 0 || state.coreCaptured
                || state.pendingWaveSpawns > 0 || !state.raiders.isEmpty()) return;
        WaveLootRewards rewards = state.waveLootRewards;
        if (rewards.wave > state.wave) return;
        if (rewards.wave < state.wave) {
            rewards.wave = state.wave;
            rewards.recipients.clear();
            if (state.rewardEligible) {
                for (ServerPlayer player : members) {
                    if (!player.isSpectator() && rewards.recipients.size() < MAX_RECIPIENTS)
                        rewards.recipients.putIfAbsent(player.getUUID(), new Receipt());
                }
            }
            data.setDirty();
        }
        if (!state.rewardEligible) {
            if (!rewards.recipients.isEmpty()) {
                rewards.recipients.clear();
                data.setDirty();
            }
            return;
        }
        for (ServerPlayer player : members) {
            Receipt receipt = rewards.recipients.get(player.getUUID());
            if (receipt == null || receipt.attempted || player.isSpectator()) continue;
            if (receipt.tier == null) {
                receipt.tier = java.util.Objects.requireNonNull(roll.apply(player));
                data.setDirty();
            }
            // Keep the saved tier if item creation fails before delivery begins.
            ItemStack box = boxFactory.apply(receipt.tier);
            if (box == null || box.isEmpty()) continue;
            receipt.attempted = true;
            data.setDirty();
            if (deliver(player, box)) {
                player.displayClientMessage(Component.literal("You received a ")
                        .append(Component.literal(receipt.tier.label + " Loot Box").withStyle(receipt.tier.color))
                        .append(Component.literal(" for surviving wave " + state.wave + ".")), false);
            } else {
                FactionLogger.LOG.warn("Wave {} loot overflow could not be dropped for {}; delivery is consumed to avoid duplicates",
                        state.wave, player.getUUID());
            }
        }
    }

    /** Inventory.add mutates its argument, including on partial insertion or a false return. */
    static boolean deliver(ServerPlayer player, ItemStack box) {
        ItemStack remaining = box.copy();
        player.getInventory().add(remaining);
        player.getInventory().setChanged();
        if (player.inventoryMenu != null) player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != null && player.containerMenu != player.inventoryMenu)
            player.containerMenu.broadcastChanges();
        return remaining.isEmpty() || player.drop(remaining, false) != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putInt("Wave", wave);
        ListTag list = new ListTag();
        recipients.forEach((id, receipt) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Tier", receipt.tier == null ? -1 : receipt.tier.ordinal());
            entry.putBoolean("Attempted", receipt.attempted);
            list.add(entry);
        });
        tag.put("Recipients", list);
        return tag;
    }

    /**
     * Missing/corrupt legacy receipts cannot prove whether a box was already delivered.
     * Suppress only the loaded current wave; a new raid starts with a valid wave-zero receipt.
     */
    public void load(Tag saved, int loadedWave) {
        wave = Math.max(0, loadedWave);
        recipients.clear();
        if (!(saved instanceof CompoundTag tag) || !tag.contains("Version", Tag.TAG_INT)
                || tag.getInt("Version") != VERSION || !tag.contains("Wave", Tag.TAG_INT)
                || tag.getInt("Wave") < 0 || tag.getInt("Wave") > wave
                || !(tag.get("Recipients") instanceof ListTag list)
                || list.size() > MAX_RECIPIENTS
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || (tag.getInt("Wave") == 0 && !list.isEmpty())) return;
        Map<UUID, Receipt> restored = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int tier = entry.getInt("Tier");
            if (!entry.hasUUID("Player") || !entry.contains("Tier", Tag.TAG_INT)
                    || tier < -1 || tier >= LootBoxItem.Tier.values().length
                    || !entry.contains("Attempted", Tag.TAG_BYTE)
                    || (entry.getByte("Attempted") != 0 && entry.getByte("Attempted") != 1)
                    || (tier == -1 && entry.getBoolean("Attempted"))) return;
            Receipt receipt = new Receipt();
            receipt.tier = tier < 0 ? null : LootBoxItem.Tier.values()[tier];
            receipt.attempted = entry.getBoolean("Attempted");
            if (restored.put(entry.getUUID("Player"), receipt) != null) return;
        }
        wave = tag.getInt("Wave");
        recipients.putAll(restored);
    }
}
