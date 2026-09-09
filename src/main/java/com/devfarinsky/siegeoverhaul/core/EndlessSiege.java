package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Persistent per-wave payouts and one-voter-one-ballot checkpoint decisions. */
public final class EndlessSiege {
    public static final int CHECKPOINT = 5, VOTE_TICKS = 1200;
    public enum Decision { WAIT, RETREAT, CONTINUE }
    private EndlessSiege() {}
    public static boolean active(RaidSavedData.RaidState state) { return state != null && "siege_core".equals(state.defensePointName); }
    public static int chapterWave(int wave) { return Math.floorMod(Math.max(1, wave) - 1, CHECKPOINT) + 1; }
    public static int reward(int wave) {
        long base = Math.max(0, RaidConfig.VICTORY_EMERALDS_PER_WAVE.get());
        return (int) Math.min(1_000_000L, base * (2L + (Math.max(1, wave) - 1L) / CHECKPOINT) / 2);
    }
    public static int waveSize(int base, int wave, int activeLimit) {
        // Reinforcements are staged by the existing per-faction/global active caps.
        return (int) Math.min(Math.max(1L, activeLimit) * 8, Math.max(1L, base) + Math.max(0L, wave - 1L) * 2);
    }
    /** Settle a genuinely cleared wave before any competing victory path can remove its state. */
    public static long awardClearedWave(RaidSavedData data, RaidSavedData.RaidState state, long now, int rate) {
        if (state == null || state.preparationTicks > 0 || state.coreCaptured
                || state.pendingWaveSpawns > 0 || !state.raiders.isEmpty()) return 0;
        return award(data, state, now, rate);
    }
    public static long award(RaidSavedData data, RaidSavedData.RaidState state, long now, int rate) {
        if (!active(state) || state.wave <= 0 || state.campaign.getInt("PaidWave") >= state.wave) return 0;
        state.campaign.putInt("PaidWave", state.wave); data.setDirty();
        CompoundTag core = data.siegeCores.get(state.teamKey);
        if (!state.rewardEligible || core == null) return 0;
        FactionBank.settle(core, now, rate);
        long paid = FactionBank.credit(core, reward(state.wave));
        state.campaign.putLong("Deposited", Math.min(FactionBank.LIMIT, state.campaign.getLong("Deposited") + paid));
        return paid;
    }
    public static boolean voting(RaidSavedData.RaidState state) { return state.campaign.getInt("VoteTicks") > 0; }
    public static void begin(CompoundTag campaign, int wave, Collection<UUID> voters, String token) {
        CompoundTag electorate = new CompoundTag(); voters.forEach(id -> electorate.putBoolean(id.toString(), true));
        campaign.put("Electorate", electorate); campaign.put("Ballots", new CompoundTag());
        campaign.putString("VoteToken", token); campaign.putInt("VoteWave", wave); campaign.putInt("VoteTicks", VOTE_TICKS);
    }
    public static boolean cast(CompoundTag campaign, UUID voter, String token, boolean retreat) {
        if (campaign.getInt("VoteTicks") <= 0 || !campaign.getString("VoteToken").equals(token)
                || !campaign.getCompound("Electorate").getBoolean(voter.toString())) return false;
        CompoundTag ballots = campaign.getCompound("Ballots");
        if (ballots.contains(voter.toString())) return false;
        ballots.putBoolean(voter.toString(), retreat); campaign.put("Ballots", ballots); return true;
    }
    public static Decision decision(CompoundTag campaign) {
        int eligible = campaign.getCompound("Electorate").size();
        int yes = 0, no = 0;
        for (String voter : campaign.getCompound("Electorate").getAllKeys()) if (campaign.getCompound("Ballots").contains(voter)) {
            if (campaign.getCompound("Ballots").getBoolean(voter)) yes++; else no++;
        }
        if (yes > eligible / 2) return Decision.RETREAT;
        if (eligible == 0 || no >= (eligible + 1) / 2 || campaign.getInt("VoteTicks") <= 0) return Decision.CONTINUE;
        return Decision.WAIT;
    }
    public static Decision tick(CompoundTag campaign) {
        campaign.putInt("VoteTicks", Math.max(0, campaign.getInt("VoteTicks") - 20));
        Decision result = decision(campaign);
        if (result != Decision.WAIT) campaign.putInt("VoteTicks", 0);
        return result;
    }
    public static void offer(RaidSavedData.RaidState state, List<ServerPlayer> members) {
        String token = UUID.randomUUID().toString();
        begin(state.campaign, state.wave, members.stream().filter(p -> !p.isSpectator()).map(ServerPlayer::getUUID).toList(), token);
        for (var player : members) player.sendSystemMessage(Component.literal("Wave " + state.wave + " survived. Call it good? The enemy offers retreat. ")
                .append(choice("[Accept retreat]", token, true)).append(" ").append(choice("[Fight 5 more]", token, false))
                .append(Component.literal(" Strict majority of " + state.campaign.getCompound("Electorate").size() + " eligible members; 60 seconds. A tie/no majority continues. Next wave: " + reward(state.wave + 1) + " bank emeralds.")));
    }
    private static Component choice(String text, String token, boolean retreat) {
        return Component.literal(text).withStyle(s -> s.withColor(retreat ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/siegeoverhaul truce " + token + " " + (retreat ? "yes" : "no"))));
    }
    public static int vote(ServerPlayer player, String token, boolean retreat) {
        RaidSavedData data = RaidSavedData.get(player.server);
        var state = data.raids.get(SiegeCore.key(player));
        if (!active(state) || player.isSpectator() || !cast(state.campaign, player.getUUID(), token, retreat)) {
            player.sendSystemMessage(Component.literal("That vote is closed, already cast, or not open to you.")); return 0;
        }
        data.setDirty(); player.sendSystemMessage(Component.literal(retreat ? "Vote recorded: accept retreat." : "Vote recorded: continue the siege.")); return 1;
    }
}
