package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** Native Recruits claim registration for NPC camps; never overwrite or delete a player's claim. */
public final class CampClaims {
    private static final String WORLD = "com.talhanation.recruits.world.";
    private CampClaims() {}
    private static Object manager() throws ReflectiveOperationException {
        return Class.forName("com.talhanation.recruits.ClaimEvents").getField("recruitsClaimManager").get(null);
    }
    private static Object config(String field) throws ReflectiveOperationException {
        return ((ForgeConfigSpec.ConfigValue<?>) Class.forName("com.talhanation.recruits.config.RecruitsServerConfig").getField(field).get(null)).get();
    }
    public static String unavailableReason(ServerLevel level) {
        if(!level.dimension().equals(Level.OVERWORLD))return "Camp claims require the Overworld";
        try {
            if(!Boolean.TRUE.equals(config("AllowClaiming")))return "Recruits AllowClaiming is disabled";
            if((Integer)config("MaxClaimChunks")<25)return "Recruits MaxClaimChunks must be at least 25";
            if(manager()==null)return "Waiting for Recruits claim manager";
            return "";
        } catch(ReflectiveOperationException | RuntimeException ex) {
            return "Recruits camp claim API unavailable: "+ex.getClass().getSimpleName();
        }
    }
    public static Set<ChunkPos> footprint(BlockPos center) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        ChunkPos c = new ChunkPos(center);
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) chunks.add(new ChunkPos(c.x+x,c.z+z));
        return chunks;
    }
    public static boolean canClaim(ServerLevel level, BlockPos center) {
        if (!level.dimension().equals(Level.OVERWORLD)) return false;
        try {
            if (!Boolean.TRUE.equals(config("AllowClaiming")) || (Integer) config("MaxClaimChunks") < 25) return false;
            Object manager = manager();
            if (manager == null) return false;
            var lookup = manager.getClass().getMethod("getClaim", ChunkPos.class);
            ChunkPos c = new ChunkPos(center);
            // Same initial 5x5 footprint and three-chunk foreign-claim buffer as the native player flow.
            for (int x=-5;x<=5;x++) for (int z=-5;z<=5;z++) {
                ChunkPos chunk = new ChunkPos(c.x+x,c.z+z);
                if (lookup.invoke(manager, chunk) != null) return false;
                if (Math.abs(x)<=2 && Math.abs(z)<=2 &&
                        (!level.getWorldBorder().isWithinBounds(new BlockPos(chunk.getMinBlockX(), center.getY(), chunk.getMinBlockZ()))
                        || !level.getWorldBorder().isWithinBounds(new BlockPos(chunk.getMaxBlockX(), center.getY(), chunk.getMaxBlockZ())))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }
    public static boolean create(ServerLevel level, RaidSavedData.RaidState raid, BlockPos center) {
        if (!canClaim(level, center) || !RaiderFactions.ensure(level.getServer(),raid.factionId)) return false;
        try {
            Object manager = manager();
            Object factions = Class.forName("com.talhanation.recruits.FactionEvents").getField("recruitsFactionManager").get(null);
            Object faction = factions.getClass().getMethod("getFactionByStringID", String.class).invoke(factions, RaiderFactions.id(raid.factionId));
            if (faction == null) return false;
            Class<?> factionType = Class.forName(WORLD + "RecruitsFaction"), claimType = Class.forName(WORLD + "RecruitsClaim");
            Object claim = claimType.getConstructor(String.class, factionType).newInstance(raid.narrative!=null && raid.narrative.factionName!=null ? raid.narrative.factionName+" War Camp" : "Raider War Camp", faction);
            claimType.getMethod("setCenter", ChunkPos.class).invoke(claim, new ChunkPos(center));
            for (ChunkPos chunk : footprint(center)) claimType.getMethod("addChunk", ChunkPos.class).invoke(claim, chunk);
            Class<?> infoType = Class.forName(WORLD + "RecruitsPlayerInfo");
            Object info = infoType.getConstructor(UUID.class, String.class, factionType).newInstance(RaiderFactions.leader(raid.factionId), RaiderFactions.name(raid.factionId), faction);
            claimType.getMethod("setPlayer", infoType).invoke(claim, info);
            // Keep camp sabotage and supply raids playable under the native claim permission system.
            claimType.getMethod("setBlockInteractionAllowed", boolean.class).invoke(claim, true);
            claimType.getMethod("setBlockBreakingAllowed", boolean.class).invoke(claim, true);
            UUID id = (UUID) claimType.getMethod("getUUID").invoke(claim);
            RaidSavedData data = RaidSavedData.get(level.getServer());
            // Record lease before calling external listeners so a partial update can be cleaned up.
            data.campClaimLeases.add(id); data.setDirty();
            manager.getClass().getMethod("addOrUpdateClaim", ServerLevel.class, claimType).invoke(manager, level, claim);
            if (manager.getClass().getMethod("getClaim", UUID.class).invoke(manager, id) == null) {
                data.campClaimLeases.remove(id); data.setDirty(); return false;
            }
            raid.campClaimId = id;
            manager.getClass().getMethod("save", ServerLevel.class).invoke(manager, level);
            FactionLogger.LOG.info("Registered native Recruits camp claim {} for {}", id, raid.teamKey);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            raid.campClaimId = null;
            FactionLogger.LOG.warn("Could not register native raider camp claim", ex); return false;
        }
    }
    public static boolean owns(ServerLevel level, RaidSavedData.RaidState raid) {
        if (raid.campClaimId == null) return true; // existing 4.0 raids are not retroactively claimed
        try {
            Object m = manager();
            Object claim = m.getClass().getMethod("getClaim", UUID.class).invoke(m, raid.campClaimId);
            if (claim == null) return false;
            String owner=(String)claim.getClass().getMethod("getOwnerFactionStringID").invoke(claim);
            if(RecruitsBridge.RAIDERS_FACTION_ID.equals(owner) && RaiderFactions.ensure(level.getServer(),raid.factionId))
                return CoreClaimTransfer.transfer(level,raid.campClaimId,owner,RaiderFactions.id(raid.factionId),RaiderFactions.name(raid.factionId)+" War Camp");
            return RaiderFactions.id(raid.factionId).equals(owner);
        } catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }
    public static void cleanOrphans(ServerLevel level, RaidSavedData data) {
        Set<UUID> active = new HashSet<>();
        for (var raid : data.raids.values()) if (raid.campClaimId != null) active.add(raid.campClaimId);
        for (UUID id : new ArrayList<>(data.campClaimLeases)) {
            if (active.contains(id)) continue;
            try {
                Object m = manager();
                if (m == null) return;
                Object claim = m.getClass().getMethod("getClaim", UUID.class).invoke(m, id);
                if (claim != null && RaiderFactions.enemy((String)claim.getClass().getMethod("getOwnerFactionStringID").invoke(claim))) {
                    m.getClass().getMethod("removeClaim", ServerLevel.class, UUID.class).invoke(m, level, id);
                    m.getClass().getMethod("save", ServerLevel.class).invoke(m, level);
                }
                // A captured claim belongs to its new owner. Leave it intact.
                data.campClaimLeases.remove(id); data.setDirty();
            } catch (ReflectiveOperationException | RuntimeException ex) {
                FactionLogger.LOG.warn("Camp claim cleanup deferred for {}", id, ex); return;
            }
        }
    }
}
