package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Occupation lives with the core, so stopping a raid or restarting cannot erase the right to recapture. */
public final class CoreOccupation {
    private CoreOccupation() {}
    public static boolean occupied(RaidSavedData data, String key) {
        CompoundTag core = data.siegeCores.get(key);
        return core != null && core.getBoolean("Occupied");
    }
    public static boolean inRing(Vec3 entity, BlockPos core) {
        double dx=entity.x-core.getX()-.5, dz=entity.z-core.getZ()-.5;
        return CaptureRing.inside(dx,entity.y-core.getY()-.5,dz,
                RaidConfig.CORE_CAPTURE_RADIUS.get(),RaidConfig.CORE_CAPTURE_VERTICAL.get());
    }
    /** Ring membership plus, when configured, an unobstructed view of the core. */
    public static boolean contesting(ServerLevel level, Vec3 entity, BlockPos core) {
        return inRing(entity,core)
                && (!RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.get() || CaptureRing.visible(level,core,entity));
    }
    public static int[] counts(ServerLevel level, BlockPos pos, String key, Set<UUID> members) {
        return counts(level, pos, key, members, null);
    }
    /**
     * Count the core's original defenders and hostile occupiers. During
     * recovery the native claim may have changed hands again; counting that
     * faction prevents a third party from standing uncontested while also
     * avoiding a permanent occupation lock.
     */
    public static int[] counts(ServerLevel level, BlockPos pos, String key, Set<UUID> members, String occupyingFaction) {
        int enemies=0, defenders=0;
        for (var player:level.players()) if (player.isAlive() && !player.isSpectator() && !player.isCreative()
                && contesting(level,player.position(),pos)) {
            int side=side(SiegeCore.key(player),key,occupyingFaction);
            if(side>0)defenders++; else if(side<0)enemies++;
        }
        int radius=RaidConfig.CORE_CAPTURE_RADIUS.get();
        int vertical=RaidConfig.CORE_CAPTURE_VERTICAL.get();
        for (Mob mob:level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(radius,vertical,radius),
                m -> m.isAlive() && !m.isPassenger() && contesting(level,m.position(),pos))) {
            String raidTeam = mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM);
            String guardTeam = mob.getPersistentData().getString(com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG);
            // Siege tags bind a unit to one contest. Luring another camp's
            // soldiers here must not contribute capture pressure or defense.
            if ((!raidTeam.isBlank() && !key.equals(raidTeam))
                    || (!guardTeam.isBlank() && !key.equals(guardTeam))) continue;
            if (RecruitsBridge.isRecruitSoldier(mob) && (key.equals(raidTeam) || key.equals(guardTeam))) enemies++;
            else if (RecruitsBridge.belongsTo(mob,key,members)) defenders++;
            else if (occupyingFaction!=null && !occupyingFaction.isBlank()
                    && RecruitsBridge.belongsTo(mob,"team:"+occupyingFaction,Set.of())) enemies++;
        }
        return new int[]{enemies,defenders};
    }
    static int side(String actorKey,String defenderKey,String occupyingFaction) {
        if(defenderKey.equals(actorKey))return 1;
        if(occupyingFaction!=null && !occupyingFaction.isBlank()
                && ("team:"+occupyingFaction).equals(actorKey))return -1;
        return 0;
    }
    public static boolean capture(ServerLevel level, RaidSavedData data, RaidSavedData.RaidState raid, BlockPos pos) {
        CompoundTag core=data.siegeCores.get(raid.teamKey);
        if(core==null || !raid.teamKey.startsWith("team:")) return false;
        var claim=RecruitsClaimsBridge.getClaimAt(level,pos).orElse(null);
        if(claim==null || !claim.ownerFactionStringId().equals(raid.teamKey.substring(5))) return false;
        if(!RaiderFactions.ensure(level.getServer(),raid.factionId)) return false;
        // Persist identity before handing off to native listeners; a failed transfer never marks occupation.
        core.putUUID("OccupiedClaim",claim.claimId());
        core.putString("OriginalClaimName",claim.claimName());
        core.putString("OccupyingFaction",com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId.byIdOrDefault(raid.factionId).id);
        data.setDirty();
        if(!CoreClaimTransfer.transfer(level,claim.claimId(),raid.teamKey.substring(5),RaiderFactions.id(raid.factionId),RaiderFactions.name(raid.factionId)+" Occupied Territory")) return false;
        core.putBoolean("Occupied",true); core.putInt("RecaptureTicks",0);
        CoreControl.bindOwner(core,"RecaptureTicks",RaiderFactions.id(raid.factionId));
        raid.coreCaptured=true; raid.pendingWaveSpawns=0; raid.ticksToNextWave=0;
        data.setDirty();
        notify(level.getServer(),raid.teamKey,"Your Siege Core was captured. Its territory now belongs to "+RaiderFactions.name(raid.factionId)+". Outnumber them within "
                +RaidConfig.CORE_CAPTURE_RADIUS.get()+" blocks of the core for "+RaidConfig.CORE_RECAPTURE_SECONDS.get()+" seconds to reclaim it.");
        return true;
    }
    public static void tick(MinecraftServer server, RaidSavedData data) {
        ServerLevel level=server.overworld();
        for(var entry:data.siegeCores.entrySet()) {
            CompoundTag core=entry.getValue();
            if(!core.getBoolean("Occupied") || !core.hasUUID("OccupiedClaim")) continue;
            BlockPos pos=BlockPos.of(core.getLong("Position"));
            if(!level.hasChunkAt(pos) || !level.getBlockState(pos).is(CoreBlocks.CORE.get())) continue;
            var claim=RecruitsClaimsBridge.getClaimAt(level,pos).orElse(null);
            if(claim==null || !claim.claimId().equals(core.getUUID("OccupiedClaim"))) continue;
            String key=entry.getKey();
            if(!key.startsWith("team:")) continue;
            if(claim.ownerFactionStringId().equals(key.substring(5))) {
                core.putBoolean("Occupied",false); core.putInt("RecaptureTicks",0); data.setDirty(); continue;
            }
            var activeRaid=data.raids.get(key);
            String faction=core.contains("OccupyingFaction")?core.getString("OccupyingFaction"):activeRaid==null?null:activeRaid.factionId;
            if(!core.contains("OriginalClaimName"))core.putString("OriginalClaimName",claim.claimName());
            if(faction!=null && claim.ownerFactionStringId().equals(RecruitsBridge.RAIDERS_FACTION_ID) && RaiderFactions.ensure(server,faction)) {
                if(CoreClaimTransfer.transfer(level,claim.claimId(),claim.ownerFactionStringId(),RaiderFactions.id(faction),RaiderFactions.name(faction)+" Occupied Territory")) {
                    core.putString("OccupyingFaction",faction); data.setDirty();
                    continue;
                }
            }
            var anchor=data.anchors.get(key);
            CoreControl.bindOwner(core,"RecaptureTicks",claim.ownerFactionStringId());
            int[] counts=counts(level,pos,key,anchor==null?Set.of():anchor.members(),claim.ownerFactionStringId());
            int max=RaidConfig.CORE_RECAPTURE_SECONDS.get()*20;
            int progress=CoreControl.advance(core.getInt("RecaptureTicks"),max,counts[1],counts[0]);
            core.putInt("RecaptureTicks",progress); data.setDirty();
            String status="Recapture "+progress*100/max+"% | "+counts[1]+" defenders / "+counts[0]+" enemies at core";
            var raid=data.raids.get(key);
            if(raid!=null) raid.objectiveStatus=status;
            if(level.getGameTime()%100==0) for(var player:level.players())
                if(key.equals(SiegeCore.key(player))) player.displayClientMessage(Component.literal(status),true);
            if(progress>=max && counts[1]>counts[0] && CoreClaimTransfer.transfer(level,claim.claimId(),claim.ownerFactionStringId(),key.substring(5),core.getString("OriginalClaimName"))) {
                core.putBoolean("Occupied",false); core.putInt("RecaptureTicks",0); data.setDirty();
                notify(server,key,"Siege Core recaptured. Your faction owns its territory again.");
            }
        }
    }
    private static void notify(MinecraftServer server,String key,String text) {
        for(var player:server.getPlayerList().getPlayers()) if(key.equals(SiegeCore.key(player)))
            player.sendSystemMessage(Component.literal(text));
    }
}
