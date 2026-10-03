package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Re-evaluate live permissions without guessing offline player privileges. */
final class NativeConstructionPolicy {
    private NativeConstructionPolicy() {}

    static String problem(ServerLevel level, Mob worker, Entity area, UUID owner, String coreKey,
                          BlockPos corePos, Set<BlockPos> cells) {
        if (!level.dimension().equals(Level.OVERWORLD) || worker.level() != level || area.level() != level)
            return "Paused: construction dimension changed";
        if (!owner.equals(WorkersBridge.readOwner(area)) || !owner.equals(WorkersBridge.readWorkerOwner(worker)))
            return "Paused: construction ownership changed";
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) return "Paused: owner offline; return to resume protected construction";
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild() || player.serverLevel() != level
                || !coreKey.equals(SiegeCore.key(player))) return "Paused: owner building permission changed";
        var point = SiegeCore.point(level.getServer(), coreKey);
        if (point == null || !corePos.equals(point.pos())) return "Paused: original Siege Core is unavailable";
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(coreKey);
        var claim = RecruitsClaimsBridge.resolveDefendingClaim(level, anchor).orElse(null);
        if (claim == null) return "Paused: original faction claim is unavailable";
        var identity = anchor.withIdentity(claim.ownerFactionStringId(), anchor.teamDisplay());
        Map<ChunkPos, Boolean> allowed = new HashMap<>();
        for (BlockPos pos : cells) {
            if (pos.equals(corePos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight())
                return "Paused: a planned cell is protected or unloaded";
            if (!allowed.computeIfAbsent(new ChunkPos(pos), chunk -> claim.chunks().contains(chunk)
                    && !ClaimBridge.isForeignClaim(level, chunk, identity)) || !level.mayInteract(player, pos))
                return "Paused: claim or building permission changed";
        }
        return null;
    }
}
