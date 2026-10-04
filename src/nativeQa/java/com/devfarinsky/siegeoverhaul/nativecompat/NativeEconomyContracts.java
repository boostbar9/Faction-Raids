package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidEvents;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.EndlessSiege;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.core.TerritoryBuffs;
import com.devfarinsky.siegeoverhaul.core.WaveLootRewards;
import com.devfarinsky.siegeoverhaul.items.LootBoxItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Short real-server economy checks; cleared-wave state is seeded, never claimed as a fought raid. */
final class NativeEconomyContracts {
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static RaidSavedData beforeReloadData;
    private static RaidSavedData.RaidState beforeReloadRaid;
    private static CompoundTag checkpointReceipt;
    private static ListTag checkpointInventory;
    private static long checkpointTreasury;

    private NativeEconomyContracts() {}

    static void beforeReload(ServerLevel level, ServerPlayer owner, BlockPos corePos) throws Exception {
        require(!owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2),
                "Economy fixture requires the actual non-op Survival player");
        owner.teleportTo(corePos.getX() + 3.5, corePos.getY(), corePos.getZ() - 2.5);
        require(SiegeCore.canUse(owner, corePos), "Economy actor cannot use the real claimed core");
        RaidSavedData data = RaidSavedData.get(owner.server);
        String key = SiegeCore.key(owner);
        require(!data.raids.containsKey(key), "Economy fixture would overwrite an existing raid");
        CompoundTag core = data.siegeCores.get(key);
        require(core != null && TerritoryBuffs.mask(core) == 0, "Expected untouched territory ownership");
        long startingTreasury = FactionBank.balance(core);
        require(startingTreasury == 992, "Economy phase lost the exact manual commission balance");
        ListTag personalInventory = inventory(owner);
        unavailablePurchases(owner, corePos, core, 0);
        core.putInt("TerritoryBuffs", 3); // Explicit old-save ownership fixture; never an awarded effect.
        unavailablePurchases(owner, corePos, core, 3);
        require(TerritoryBuffs.activeCount(3) == 0 && TerritoryBuffs.retainedCount(3) == 2,
                "Unavailable ownership was counted as an active effect");
        require(TerritoryBuffs.price(2) == 900 && TerritoryBuffs.price(3) == 600,
                "Working upgrade prices changed");
        require(FactionBank.credit(core, 1500) == 1500, "Fixture Treasury funding was capped");
        require(TerritoryBuffs.purchase(owner, corePos, 2)
                        && FactionBank.balance(core) == startingTreasury + 600,
                "Real Provisioning purchase did not debit exactly 900 Treasury");
        require(TerritoryBuffs.purchase(owner, corePos, 3)
                        && FactionBank.balance(core) == startingTreasury,
                "Real Iron Levy purchase did not debit exactly 600 Treasury");
        require(inventory(owner).equals(personalInventory), "Territory purchases consumed personal inventory");
        require(TerritoryBuffs.mask(core) == 15 && TerritoryBuffs.activeCount(15) == 2
                        && TerritoryBuffs.retainedCount(15) == 2,
                "Working purchases lost retained ownership or counted unavailable effects");
        RESULT.put("territory", Map.of("unownedRejected", List.of(0, 1), "ownedRejected", List.of(0, 1),
                "unavailableTreasuryDebit", 0, "personalInventoryUnchanged", true,
                "fixtureFunding", 1500, "workingPrices", List.of(900, 600),
                "savedOwnershipMask", 15, "activeCount", 2, "retainedCount", 2));

        require(boxes(owner) == 0 && drops(level, owner).isEmpty(), "Wave fixture began with unrelated loot boxes");
        var practice = clearedCheckpoint(key, false);
        data.raids.put(key, practice);
        processRaid(owner.server, data, key);
        processRaid(owner.server, data, key);
        require(EndlessSiege.voting(practice) && boxes(owner) == 0 && drops(level, owner).isEmpty()
                        && practice.waveLootRewards.save().getList("Recipients", Tag.TAG_COMPOUND).isEmpty()
                        && FactionBank.balance(core) == startingTreasury,
                "Practice checkpoint gave loot or Treasury, or failed to reach the real checkpoint vote");
        RESULT.put("practice", Map.of("wave", 5, "handlerPasses", 2, "boxes", 0, "treasuryDebitOrCredit", 0));

        var eligible = clearedCheckpoint(key, true);
        data.raids.put(key, eligible);
        processRaid(owner.server, data, key);
        require(EndlessSiege.voting(eligible) && eligible.campaign.getInt("VoteWave") == 5
                        && eligible.campaign.getCompound("Ballots").isEmpty() && boxes(owner) == 1
                        && drops(level, owner).isEmpty(),
                "Eligible fifth-wave box was not delivered before any checkpoint ballot");
        checkpointReceipt = receipt(eligible, owner, 5);
        require(boxesOfTier(owner, checkpointReceipt) == 1, "Actual inventory box differs from the persisted roll");
        checkpointTreasury = FactionBank.balance(core);
        require(checkpointTreasury == startingTreasury + EndlessSiege.reward(5),
                "Eligible fifth-wave Treasury payout differs from its production reward");
        checkpointInventory = inventory(owner);
        processRaid(owner.server, data, key);
        require(inventory(owner).equals(checkpointInventory) && drops(level, owner).isEmpty()
                        && checkpointReceipt.equals(eligible.waveLootRewards.save())
                        && FactionBank.balance(core) == checkpointTreasury,
                "Checkpoint wait replay duplicated a box, changed its roll or paid Treasury again");
        beforeReloadData = data;
        beforeReloadRaid = eligible;
        data.setDirty();
        RESULT.put("checkpoint", Map.of("wave", 5, "boxes", 1, "beforeAnyBallot", true,
                "waitReplayNoDuplicate", true, "rolledTier", tier(checkpointReceipt),
                "treasury", checkpointTreasury, "treasuryReward", EndlessSiege.reward(5)));
        RESULT.put("scope", "Controlled cleared-wave states in the real Survival server; production processRaid, live registry/inventory/drop, and ordinary disk world reload. No raid combat was fought.");
        owner.server.saveEverything(false, true, true);
    }

    static Map<String, Object> afterReload(ServerLevel level, ServerPlayer owner, BlockPos corePos) throws Exception {
        RaidSavedData data = RaidSavedData.get(owner.server);
        String key = SiegeCore.key(owner);
        var state = data.raids.get(key);
        require(data != beforeReloadData && state != null && state != beforeReloadRaid,
                "Economy check reused in-memory SavedData instead of reopening the disk world");
        CompoundTag core = data.siegeCores.get(key);
        require(TerritoryBuffs.mask(core) == 15 && TerritoryBuffs.activeCount(15) == 2
                        && TerritoryBuffs.retainedCount(15) == 2 && FactionBank.balance(core) == checkpointTreasury,
                "Real disk reload lost retained ownership, working upgrades or exact Treasury");
        unavailablePurchases(owner, corePos, core, 15);
        require(checkpointReceipt.equals(receipt(state, owner, 5)) && inventory(owner).equals(checkpointInventory)
                        && boxesOfTier(owner, checkpointReceipt) == 1 && drops(level, owner).isEmpty(),
                "Real disk reload changed the current-wave receipt, tier or physical inventory reward");
        require(EndlessSiege.voting(state), "Checkpoint vote expired before bounded economy reload checks");
        processRaid(owner.server, data, key);
        require(checkpointReceipt.equals(state.waveLootRewards.save()) && inventory(owner).equals(checkpointInventory)
                        && drops(level, owner).isEmpty() && FactionBank.balance(core) == checkpointTreasury,
                "Reloaded checkpoint replay duplicated a reward or Treasury payout");
        RESULT.put("diskReload", Map.of("newSavedDataAndRaidInstances", true, "ownershipMask", 15,
                "activeCount", 2, "retainedCount", 2, "exactPlayerInventory", true,
                "exactCurrentWaveReceipt", true, "replayNoDuplicate", true));

        // Temporarily replace only the test player's inventory with explicitly seeded full stock.
        // The exact real inventory, including the fifth-wave box, is restored in this server action.
        ListTag preservedInventory = inventory(owner);
        try {
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
                owner.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            owner.getInventory().setChanged(); owner.inventoryMenu.broadcastChanges();
            require(owner.getInventory().getFreeSlot() == -1 && boxes(owner) == 0,
                    "Full-inventory fixture contains an empty insertion slot or existing compatible box");
            Set<UUID> previousDrops = drops(level, owner).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
            state.wave = 6; // Controlled cleared next-wave fixture; no spawn, kill or combat claim.
            state.campaign.putInt("VoteTicks", 0);
            WaveLootRewards.awardClearedWave(data, state, List.of(owner));
            List<ItemEntity> newDrops = drops(level, owner).stream()
                    .filter(entity -> !previousDrops.contains(entity.getUUID())).toList();
            CompoundTag fullReceipt = receipt(state, owner, 6);
            require(boxes(owner) == 0 && newDrops.size() == 1 && newDrops.get(0).getItem().getCount() == 1
                            && ((LootBoxItem) newDrops.get(0).getItem().getItem()).tier().ordinal() == tier(fullReceipt),
                    "Actual full inventory did not drop exactly one box matching the persisted roll");
            WaveLootRewards.awardClearedWave(data, state, List.of(owner));
            require(fullReceipt.equals(state.waveLootRewards.save()) && boxes(owner) == 0
                            && drops(level, owner).size() == previousDrops.size() + 1,
                    "Full-inventory replay duplicated the dropped box or rerolled its tier");
            RESULT.put("fullInventory", Map.of("wave", 6, "filledMainSlots", 36,
                    "droppedBoxes", 1, "inventoryBoxes", 0, "replayNoDuplicate", true,
                    "rolledTier", tier(fullReceipt), "dropEntity", newDrops.get(0).getUUID().toString(),
                    "item", String.valueOf(ForgeRegistries.ITEMS.getKey(newDrops.get(0).getItem().getItem()))));
        } finally {
            owner.getInventory().load(preservedInventory);
            owner.getInventory().setChanged(); owner.inventoryMenu.broadcastChanges();
            data.raids.remove(key); // Retire the controlled fixture before any next-wave server tick.
            data.setDirty();
        }
        require(inventory(owner).equals(preservedInventory), "Full-stock fixture did not restore exact real inventory");
        RESULT.put("status", "passed");
        return Map.copyOf(RESULT);
    }

    private static void unavailablePurchases(ServerPlayer owner, BlockPos corePos, CompoundTag core, int mask) {
        CompoundTag unchanged = core.copy();
        ListTag personal = inventory(owner);
        for (int index : new int[]{0, 1}) require(!TerritoryBuffs.purchase(owner, corePos, index)
                        && core.equals(unchanged) && TerritoryBuffs.mask(core) == mask
                        && inventory(owner).equals(personal),
                "Unavailable Territory buff changed Treasury, ownership or personal inventory: " + index);
    }

    private static RaidSavedData.RaidState clearedCheckpoint(String key, boolean eligible) {
        var state = new RaidSavedData.RaidState(key, "siege_core", 0);
        state.wave = 5; state.rewardEligible = eligible;
        state.campSearchAbandoned = true; // Camp-less cleared-wave boundary, no synthetic construction.
        state.factionId = "blackbay_reavers";
        return state;
    }

    private static void processRaid(MinecraftServer server, RaidSavedData data, String key) throws Exception {
        var process = RaidEvents.class.getDeclaredMethod("processRaid", MinecraftServer.class, RaidSavedData.class, String.class);
        process.setAccessible(true);
        process.invoke(null, server, data, key); // Invoke the unchanged production handler, never a QA reward imitation.
    }

    private static CompoundTag receipt(RaidSavedData.RaidState state, ServerPlayer owner, int wave) {
        CompoundTag saved = state.waveLootRewards.save();
        ListTag recipients = saved.getList("Recipients", Tag.TAG_COMPOUND);
        require(saved.getInt("Version") == 1 && saved.getInt("Wave") == wave && recipients.size() == 1
                        && recipients.getCompound(0).hasUUID("Player")
                        && recipients.getCompound(0).getUUID("Player").equals(owner.getUUID())
                        && recipients.getCompound(0).getBoolean("Attempted")
                        && tier(saved) >= 0 && tier(saved) < LootBoxItem.Tier.values().length,
                "Expected one persisted attempted reward for the real current-wave player");
        return saved;
    }
    private static int tier(CompoundTag receipt) { return receipt.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).getInt("Tier"); }
    private static ListTag inventory(ServerPlayer owner) { return owner.getInventory().save(new ListTag()); }
    private static int boxes(ServerPlayer owner) {
        int count = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            if (stack.getItem() instanceof LootBoxItem) count += stack.getCount();
        }
        return count;
    }
    private static int boxesOfTier(ServerPlayer owner, CompoundTag receipt) {
        int count = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            if (stack.getItem() instanceof LootBoxItem box && box.tier().ordinal() == tier(receipt)) count += stack.getCount();
        }
        return count;
    }
    private static List<ItemEntity> drops(ServerLevel level, ServerPlayer owner) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(owner.blockPosition()).inflate(16),
                entity -> entity.isAlive() && entity.getItem().getItem() instanceof LootBoxItem);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
