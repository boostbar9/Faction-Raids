package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.items.LootBoxItem;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rewards for fighting a siege: Treasury emeralds for each raider a player kills, multiplied by
 * kill streaks, rare emerald jackpots, and spoils dropped when an enemy camp building or war work
 * falls. Every source shares one per-raid cap stored on the raid's campaign tag.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class CombatRewards {
    static final String PAID = "CombatRewardPaid";
    private record Streak(int kills, long lastTick) {}
    // Streaks are short-lived combat state; they are deliberately not saved.
    private static final Map<UUID, Streak> STREAKS = new HashMap<>();
    private CombatRewards() {}

    /** x1, then x2 from the third kill in a streak, x3 from the sixth and x4 from the tenth. */
    static int multiplier(int streak) { return streak >= 10 ? 4 : streak >= 6 ? 3 : streak >= 3 ? 2 : 1; }

    static int nextStreak(int previous, long lastTick, long now, int windowTicks) {
        return previous > 0 && now >= lastTick && now - lastTick <= windowTicks ? previous + 1 : 1;
    }

    /** How much of {@code amount} the per-raid cap still allows; a cap of 0 is unlimited. */
    static int payable(CompoundTag campaign, int amount, int cap) {
        if (amount <= 0) return 0;
        if (cap <= 0) return amount;
        return Math.max(0, Math.min(amount, cap - campaign.getInt(PAID)));
    }

    static void spend(CompoundTag campaign, int paid) {
        if (paid > 0) campaign.putInt(PAID, (int) Math.min(Integer.MAX_VALUE, (long) campaign.getInt(PAID) + paid));
    }

    /** Between half of {@code max} (rounded up) and {@code max}. */
    static int roll(RandomSource random, int max) {
        if (max <= 0) return 0;
        int low = (max + 1) / 2;
        return low + random.nextInt(max - low + 1);
    }

    private static int cap() { return RaidConfig.MAX_COMBAT_REWARD_EMERALDS_PER_RAID.get(); }

    /** A faction player killed a raider of {@code state}; {@code core} is that faction's Siege Core. */
    public static void raiderKilled(ServerPlayer killer, RaidSavedData.RaidState state, CompoundTag core,
                                    Vec3 where, boolean commander) {
        if (!RaidConfig.COMBAT_REWARDS.get() || killer == null || state == null || !state.rewardEligible) return;
        ServerLevel level = killer.serverLevel();
        long now = level.getGameTime();
        Streak prior = STREAKS.get(killer.getUUID());
        int streak = nextStreak(prior == null ? 0 : prior.kills(), prior == null ? 0 : prior.lastTick(), now,
                RaidConfig.KILL_STREAK_SECONDS.get() * 20);
        STREAKS.put(killer.getUUID(), new Streak(streak, now));
        int multiplier = multiplier(streak);

        int earned = 0;
        int treasury = payable(state.campaign, RaidConfig.KILL_REWARD_EMERALDS.get() * multiplier, cap());
        if (core != null && treasury > 0) {
            earned = (int) FactionBank.depositQuietly(core, treasury);
            spend(state.campaign, earned);
        }
        boolean jackpot = commander || killer.getRandom().nextDouble() < RaidConfig.JACKPOT_CHANCE.get();
        int dropped = 0;
        if (jackpot) {
            dropped = payable(state.campaign, roll(killer.getRandom(), RaidConfig.JACKPOT_EMERALDS.get()), cap());
            if (dropped > 0) {
                burst(level, where, new ItemStack(Items.EMERALD, dropped));
                spend(state.campaign, dropped);
                celebrate(level, where, true);
            }
        }
        if (earned > 0 || dropped > 0 || streak >= 2)
            RaidNetwork.sendRewardPopup(killer, new RaidNetwork.RewardPopup(dropped > 0 ? RaidNetwork.RewardPopup.JACKPOT
                    : RaidNetwork.RewardPopup.KILL, earned + dropped, Math.min(streak, 10_000), multiplier));
    }

    /**
     * An enemy camp building or war work fell: spill emeralds, supplies and sometimes a Loot Box
     * at its centrepiece and tell nearby defenders what they won.
     */
    public static void campSpoils(ServerLevel level, RaidSavedData.RaidState state, BlockPos pos, boolean warWork) {
        if (!RaidConfig.COMBAT_REWARDS.get() || state == null || !state.rewardEligible || pos == null) return;
        RandomSource random = level.getRandom();
        Vec3 where = Vec3.atCenterOf(pos);
        int emeralds = payable(state.campaign, roll(random, RaidConfig.CAMP_SPOILS_EMERALDS.get()), cap());
        if (emeralds > 0) {
            burst(level, where, new ItemStack(Items.EMERALD, emeralds));
            spend(state.campaign, emeralds);
        }
        ItemStack[] supplies = {
                new ItemStack(Items.ARROW, 8 + random.nextInt(9)), new ItemStack(Items.BREAD, 3 + random.nextInt(4)),
                new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4)), new ItemStack(Items.GOLDEN_CARROT, 2 + random.nextInt(3)),
                new ItemStack(Items.GUNPOWDER, 3 + random.nextInt(4)), new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(3))};
        for (int i = 0; i < 2; i++) burst(level, where, supplies[random.nextInt(supplies.length)].copy());
        if (random.nextFloat() < (warWork ? 0.35F : 0.25F)) {
            LootBoxItem.Tier tier = random.nextFloat() < (warWork ? 0.3F : 0.15F) ? LootBoxItem.Tier.RARE : LootBoxItem.Tier.UNCOMMON;
            burst(level, where, new ItemStack(ModItems.lootBox(tier).get()));
        }
        celebrate(level, where, false);
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(state.teamKey);
        if (anchor == null) return;
        for (UUID id : anchor.members()) {
            ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
            if (member != null && member.serverLevel() == level && member.distanceToSqr(where) <= 96 * 96)
                RaidNetwork.sendRewardPopup(member, new RaidNetwork.RewardPopup(RaidNetwork.RewardPopup.SPOILS, emeralds, 0, 1));
        }
    }

    /** Pops {@code stack} out as a few separate items that spray upward, like a burst chest. */
    private static void burst(ServerLevel level, Vec3 where, ItemStack stack) {
        RandomSource random = level.getRandom();
        int pieces = Math.min(stack.getCount(), 6);
        int each = stack.getCount() / pieces, extra = stack.getCount() % pieces;
        for (int i = 0; i < pieces; i++) {
            ItemStack piece = stack.copy();
            piece.setCount(each + (i < extra ? 1 : 0));
            ItemEntity item = new ItemEntity(level, where.x, where.y + 0.6D, where.z, piece,
                    (random.nextDouble() - 0.5D) * 0.3D, 0.3D + random.nextDouble() * 0.2D, (random.nextDouble() - 0.5D) * 0.3D);
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
        }
    }

    private static void celebrate(ServerLevel level, Vec3 where, boolean jackpot) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, where.x, where.y + 1.0D, where.z, jackpot ? 30 : 16, 0.6D, 0.6D, 0.6D, 0.1D);
        level.sendParticles(ParticleTypes.FIREWORK, where.x, where.y + 1.2D, where.z, jackpot ? 24 : 10, 0.3D, 0.4D, 0.3D, 0.12D);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, jackpot ? 1.5F : 1.1F);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.2F);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { STREAKS.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { STREAKS.clear(); }
}
