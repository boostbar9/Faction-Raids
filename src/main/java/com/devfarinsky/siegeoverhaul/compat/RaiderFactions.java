package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.items.FactionBanners;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native identities keep claim names, banners and map colors consistent across clients and saves. */
public final class RaiderFactions {
    private RaiderFactions() {}
    public static String id(String faction) { return "siegeoverhaul_" + FactionBanners.FactionId.byIdOrDefault(faction).id; }
    public static String name(String faction) {
        return switch(FactionBanners.FactionId.byIdOrDefault(faction)) {
            case BLACKBAY_REAVERS -> "Blackbay Reavers"; case HOLLOWFANG_CLAN -> "Hollowfang Clan";
            case EMBERCHANT_ZEALOTS -> "Emberchant Zealots"; case CROWNFALL_EXILES -> "Crownfall Exiles";
            case WILDS_MARAUDERS -> "Wilds Marauders";
        };
    }
    public static byte color(String faction) {
        return switch(FactionBanners.FactionId.byIdOrDefault(faction)) {
            case BLACKBAY_REAVERS -> 6; case HOLLOWFANG_CLAN -> 2; case EMBERCHANT_ZEALOTS -> 21;
            case CROWNFALL_EXILES -> 23; case WILDS_MARAUDERS -> 9;
        };
    }
    public static boolean enemy(String id) {
        if (RecruitsBridge.RAIDERS_FACTION_ID.equals(id)) return true;
        for (var faction : FactionBanners.FactionId.values()) if (id(faction.id).equals(id)) return true;
        return false;
    }
    public static UUID leader(String faction) { return UUID.nameUUIDFromBytes(id(faction).getBytes(StandardCharsets.UTF_8)); }
    public static boolean ensure(MinecraftServer server, String faction) {
        try {
            String id=id(faction), name=name(faction);
            var scoreboard=server.getScoreboard();
            var team=scoreboard.getPlayerTeam(id);
            if(team==null) {
                team=scoreboard.addPlayerTeam(id); team.setDisplayName(net.minecraft.network.chat.Component.literal(name));
                team.setAllowFriendlyFire(false); team.setColor(ChatFormatting.GOLD);
            }
            Object manager=Class.forName("com.talhanation.recruits.FactionEvents").getField("recruitsFactionManager").get(null);
            if(manager==null)return false;
            if(manager.getClass().getMethod("getFactionByStringID",String.class).invoke(manager,id)==null) {
                var banner=FactionBanners.itemStackFor(FactionBanners.FactionId.byIdOrDefault(faction)).save(new CompoundTag());
                manager.getClass().getMethod("addTeam",String.class,String.class,UUID.class,String.class,CompoundTag.class,byte.class,ChatFormatting.class)
                        .invoke(manager,id,name,leader(faction),name+" Warlord",banner,color(faction),ChatFormatting.GOLD);
                manager.getClass().getMethod("save",ServerLevel.class).invoke(manager,server.overworld());
            }
            return true;
        } catch(ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.warn("Native faction registration failed for {}",faction,ex); return false;
        }
    }
    public static String forMob(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level)) return RecruitsBridge.RAIDERS_FACTION_ID;
        var saved=RaidSavedData.get(level.getServer());
        for(String tag:List.of(ModConstants.Tags.RAID_TEAM,ModConstants.Tags.CAMP_WORKER_TEAM,com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG)) {
            var raid=saved.raids.get(mob.getPersistentData().getString(tag));
            if(raid!=null && ensure(level.getServer(),raid.factionId))return id(raid.factionId);
        }
        return RecruitsBridge.RAIDERS_FACTION_ID;
    }
    public static void sync(ServerLevel level,RaidSavedData.RaidState raid) {
        if(!ensure(level.getServer(),raid.factionId))return;
        if(raid.campClaimId != null) CampClaims.owns(level,raid);
        var team=level.getScoreboard().getPlayerTeam(id(raid.factionId));
        Set<UUID> units=new HashSet<>(raid.raiders); units.addAll(raid.campGuards); units.addAll(raid.campWorkers);
        for(UUID uuid:units) if(level.getEntity(uuid) instanceof Mob mob && mob.getTeam()!=team)
            level.getScoreboard().addPlayerToTeam(mob.getStringUUID(),team);
    }
}
