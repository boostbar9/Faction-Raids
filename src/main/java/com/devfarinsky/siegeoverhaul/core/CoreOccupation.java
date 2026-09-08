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
        return dx*dx+dz*dz <= (double)RaidConfig.CORE_CAPTURE_RADIUS.get()*RaidConfig.CORE_CAPTURE_RADIUS.get()
                && Math.abs(entity.y-core.getY()-.5)<=3;
    }
    public static int[] counts(ServerLevel level, BlockPos pos, String key, Set<UUID> members) {
        int enemies=0, defenders=0;
        for (var player:level.players()) if (player.isAlive() && !player.isSpectator() && !player.isCreative()
                && key.equals(SiegeCore.key(player)) && inRing(player.position(),pos)) defenders++;
        int radius=RaidConfig.CORE_CAPTURE_RADIUS.get();
        for (Mob mob:level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(radius,3,radius),
                m -> m.isAlive() && !m.isPassenger() && inRing(m.position(),pos))) {
            if (RecruitsBridge.isRecruitSoldier(mob) && (key.equals(mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM))
                    || key.equals(mob.getPersistentData().getString(com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG))
                    || (mob.getTeam()!=null && RaiderFactions.enemy(mob.getTeam().getName())))) enemies++;
            else if (RecruitsBridge.belongsTo(mob,key,members)) defenders++;
        }
        return new int[]{enemies,defenders};
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
        core.putString("OccupyingFaction",raid.factionId);
        data.setDirty();
        if(!CoreClaimTransfer.transfer(level,claim.claimId(),raid.teamKey.substring(5),RaiderFactions.id(raid.factionId),RaiderFactions.name(raid.factionId)+" Occupied Territory")) return false;
        core.putBoolean("Occupied",true); core.putInt("RecaptureTicks",0);
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
            if(!RaiderFactions.enemy(claim.ownerFactionStringId())) continue;
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
            int[] counts=counts(level,pos,key,anchor==null?Set.of():anchor.members());
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
