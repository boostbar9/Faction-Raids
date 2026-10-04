package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.mojang.authlib.GameProfile;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Inventory authority for an already accepted protected job, not permission to build.
 * Native upkeep can continue while its owner is offline: the server's cached UUID
 * profile and persisted scoreboard prove its current faction. The native member list
 * is checked for conflicts, not completeness: Recruits' SavedData omits that list.
 * No online
 * player, spectator, mayBuild or mayInteract permission is invented here. The caller
 * must separately verify its exact native target, canWorkHere grant and live container.
 * This class never loads chunks, changes inventory, or changes native upkeep/payment.
 */
final class NativeInventoryAuthority {
    static final int MAX_SOURCES = 65_536, MAX_MEMBERS = 4_096;

    private NativeInventoryAuthority() {}

    static String problem(ServerLevel level, Mob worker, UUID owner, String coreKey,
                          BlockPos corePos, Set<BlockPos> sources) {
        if (level == null || worker == null || owner == null || corePos == null
                || coreKey == null || !coreKey.startsWith("team:") || coreKey.length() <= 5
                || coreKey.length() > 517 || sources == null || sources.size() > MAX_SOURCES)
            return "Paused: protected inventory context is invalid or exceeds its bound";
        try {
            if (!level.getServer().isSameThread() || !level.dimension().equals(Level.OVERWORLD)
                    || worker.level() != level || !worker.isAlive()
                    || !owner.equals(WorkersBridge.readWorkerOwner(worker)))
                return "Paused: protected inventory ownership or dimension changed";
            if (FactionEvents.recruitsFactionManager == null || ClaimEvents.recruitsClaimManager == null
                    || !RecruitsClaimsBridge.available())
                return "Paused: current inventory claim authority is unavailable";
            String factionId = coreKey.substring(5);
            var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
            var profiles = level.getServer().getProfileCache();
            // The UUID overload is a local cache read, not the name overload's network lookup.
            GameProfile profile = profiles == null ? null : profiles.get(owner).orElse(null);
            String membership = membershipProblem(faction, level.getScoreboard(), owner, factionId, profile);
            if (membership != null) return membership;

            var point = SiegeCore.point(level.getServer(), coreKey);
            var anchor = RaidSavedData.get(level.getServer()).anchors.get(coreKey);
            if (point == null || !corePos.equals(point.pos()) || anchor == null
                    || !coreKey.equals(anchor.teamKey()))
                return "Paused: the original protected inventory core is unavailable";

            // Optional claim providers receive only this actual owner, never an old
            // anchor's cached faction roster. Native team-sharing is checked separately.
            var identity = new RaidSavedData.Anchor(factionId, anchor.teamDisplay(), owner, Set.of(owner),
                    anchor.internalRoster(), anchor.automaticHome(), anchor.defensePoints(), anchor.nextRaidGameTime());
            Set<ChunkPos> checked = new HashSet<>();
            for (BlockPos pos : sources) {
                if (pos == null || pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                        || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos))
                    return "Paused: an inventory source is outside the loaded safe world";
                ChunkPos chunk = new ChunkPos(pos);
                if (!checked.add(chunk)) continue;
                var claim = RecruitsClaimsBridge.getClaimAt(level, chunk);
                if (!RecruitsClaimsBridge.available())
                    return "Paused: inventory claim authority could not be verified";
                if ((claim.isPresent() && !factionId.equals(claim.get().ownerFactionStringId()))
                        || ClaimBridge.isForeignClaim(level, chunk, identity))
                    return "Paused: a foreign claim protects an inventory source";
                // A failed optional-provider read is fail-closed in ClaimBridge. Recheck
                // the required provider too in case its second query became unavailable.
                if (!RecruitsClaimsBridge.available())
                    return "Paused: inventory claim authority could not be verified";
            }
            return null;
        } catch (RuntimeException | LinkageError unavailable) {
            return "Paused: protected inventory authority cannot be verified";
        }
    }

    /** No online-status field, player lookup, matcher or native mutator is consulted. */
    static String membershipProblem(RecruitsFaction faction, Scoreboard scoreboard, UUID owner, String factionId,
                                    GameProfile profile) {
        if (faction == null || scoreboard == null || owner == null || factionId == null
                || !factionId.equals(faction.getStringID()) || faction.getMembers() == null
                || faction.getMembers().size() > MAX_MEMBERS)
            return "Paused: current native faction membership is unavailable";
        if (profile == null || !owner.equals(profile.getId()) || profile.getName() == null
                || profile.getName().isBlank() || profile.getName().length() > 64)
            return "Paused: the owner's current cached UUID identity is unavailable";
        String name = profile.getName();
        boolean found = false;
        for (var member : faction.getMembers()) {
            if (member == null || member.getUUID() == null)
                return "Paused: current native faction roster is ambiguous";
            if (!owner.equals(member.getUUID())) {
                if (member.getName() != null && name.equalsIgnoreCase(member.getName()))
                    return "Paused: the cached owner name conflicts with another native UUID";
                continue;
            }
            if (found || !name.equals(member.getName()))
                return "Paused: current native owner membership is ambiguous";
            found = true;
        }
        var team = scoreboard.getPlayersTeam(name);
        return team != null && factionId.equals(team.getName()) ? null
                : "Paused: the owner's current scoreboard faction no longer matches";
    }
}
