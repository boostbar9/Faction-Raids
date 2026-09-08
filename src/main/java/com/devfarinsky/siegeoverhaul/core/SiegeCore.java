package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;

public final class SiegeCore {
    private SiegeCore() {}
    public static String key(ServerPlayer player) {
        return player.getTeam() == null ? "" : "team:" + player.getTeam().getName();
    }
    public static boolean claimed(ServerLevel level, BlockPos pos, String key) {
        // Recruits' claim manager is keyed by chunk only, so never alias a claim into another dimension.
        return level.dimension().equals(Level.OVERWORLD) && key.startsWith("team:")
                && RecruitsClaimsBridge.getClaimAt(level, pos)
                .map(c -> c.ownerFactionStringId().equals(key.substring(5))).orElse(false);
    }
    public static RaidSavedData.DefensePoint point(net.minecraft.server.MinecraftServer server, String key) {
        CompoundTag core = RaidSavedData.get(server).siegeCores.get(key);
        if (core == null || !core.contains("Position")) return null;
        BlockPos pos = BlockPos.of(core.getLong("Position"));
        ServerLevel level = server.overworld();
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(CoreBlocks.CORE.get()) || !claimed(level, pos, key)) return null;
        return new RaidSavedData.DefensePoint("siege_core", Level.OVERWORLD.location(), pos);
    }
    public static boolean mayPlace(ServerPlayer player, BlockPos pos) {
        String key = key(player);
        if (!claimed(player.serverLevel(), pos, key)) return false;
        RaidSavedData data = RaidSavedData.get(player.server);
        if (data.raids.containsKey(key)) return false;
        CompoundTag old = data.siegeCores.get(key);
        if (old == null || !old.contains("Position")) return true;
        BlockPos oldPos = BlockPos.of(old.getLong("Position"));
        // Unloaded cores still reserve their faction's slot.
        return player.serverLevel().hasChunkAt(oldPos) && !player.serverLevel().getBlockState(oldPos).is(CoreBlocks.CORE.get());
    }
    public static void placed(ServerPlayer player, BlockPos pos) {
        RaidSavedData data = RaidSavedData.get(player.server);
        String key = key(player);
        CompoundTag core = data.siegeCores.computeIfAbsent(key, k -> new CompoundTag());
        core.putLong("Position", pos.asLong());
        CoreOffers.refresh(core, player.server.overworld().getGameTime(), player.serverLevel().random);
        RaidSavedData.Anchor old = data.anchors.get(key);
        long next = old == null ? player.server.overworld().getGameTime() + RaidConfig.MIN_COOLDOWN_MINUTES.get() * 1200L : old.nextRaidGameTime();
        var point = new RaidSavedData.DefensePoint("siege_core", Level.OVERWORLD.location(), pos.immutable());
        data.anchors.put(key, new RaidSavedData.Anchor(key, (player.getTeam() instanceof net.minecraft.world.scores.PlayerTeam team ? team.getDisplayName().getString() : player.getTeam().getName()),
                RecruitsBridge.factionLeader(player).orElse(player.getUUID()), Set.of(player.getUUID()), false, false,
                Map.of(point.name(), point), next));
        data.setDirty();
    }
    public static boolean canUse(ServerPlayer player, BlockPos pos) {
        var point = point(player.server, key(player));
        return point != null && point.pos().equals(pos) && player.serverLevel().dimension().equals(Level.OVERWORLD)
                && player.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) <= 64;
    }
    public static boolean canBreak(ServerPlayer player, BlockPos pos) {
        RaidSavedData data = RaidSavedData.get(player.server);
        for (var entry : data.siegeCores.entrySet()) {
            if (entry.getValue().contains("Position") && BlockPos.of(entry.getValue().getLong("Position")).equals(pos)) {
                return !data.raids.containsKey(entry.getKey()) && (entry.getKey().equals(key(player)) || player.hasPermissions(2));
            }
        }
        return true;
    }
}
