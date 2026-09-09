package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reuse proven defeated crews' equipment; never infer death from an unloaded entity. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class SiegeFleet {
    public static final String CAPTURED="SiegePlayerCaptured", DEFEATED="SiegeCrewDefeatedWave";
    public static final String SUPPORT_WAVE="SiegeSupportWave", OPERATOR_UUID="SiegeOperatorUuid",
            REPLACEMENT_CREW="SiegeReplacementCrew", LAST_ATTEMPT="SiegeOperatorLastAttempt";
    private SiegeFleet() {}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level))return;
        var mob=event.getEntity();String team=mob.getPersistentData().getString(SiegeDeployment.TEAM_TAG);
        if(team.isBlank())return;
        var raid=RaidSavedData.get(level.getServer()).raids.get(team);if(raid==null)return;
        for(var id:raid.siegeEngines.keySet()) {
            Entity vehicle=level.getEntity(id);if(vehicle==null)continue;
            var tag=vehicle.getPersistentData();
            if(tag.hasUUID(OPERATOR_UUID) && tag.getUUID(OPERATOR_UUID).equals(mob.getUUID()))
                tag.putInt(DEFEATED,tag.getInt(SUPPORT_WAVE));
        }
    }
    @SubscribeEvent public static void mount(EntityMountEvent event) {
        if(!event.isMounting() || event.isCanceled() || !(event.getEntityBeingMounted().level() instanceof ServerLevel))return;
        Entity vehicle=event.getEntityBeingMounted(), rider=event.getEntityMounting();
        String team=vehicle.getPersistentData().getString(SiegeDeployment.TEAM_TAG);
        if(!team.isBlank() && !team.equals(rider.getPersistentData().getString(SiegeDeployment.TEAM_TAG)))
            vehicle.getPersistentData().putBoolean(CAPTURED,true);
    }
    static boolean reusable(Entity vehicle,RaidSavedData.RaidState raid) {
        var tag=vehicle.getPersistentData();int wave=tag.getInt(SUPPORT_WAVE);
        return vehicle.isAlive() && !vehicle.isRemoved() && vehicle.getPassengers().isEmpty()
                && raid.teamKey.equals(tag.getString(SiegeDeployment.TEAM_TAG)) && !tag.getBoolean(CAPTURED)
                && wave>0 && wave<raid.wave && tag.getInt(DEFEATED)==wave
                && tag.hasUUID(OPERATOR_UUID) && !raid.raiders.contains(tag.getUUID(OPERATOR_UUID));
    }
    static boolean reuse(ServerLevel level,RaidSavedData.RaidState raid,Entity vehicle) {
        if(!reusable(vehicle,raid))return false;
        var old=level.getEntity(vehicle.getPersistentData().getUUID(OPERATOR_UUID));
        if(old!=null && old.isAlive())return false;
        vehicle.getPersistentData().putInt(SUPPORT_WAVE,raid.wave);
        vehicle.getPersistentData().remove(SiegeDeployment.OPERATOR_ASSIGNED);
        vehicle.getPersistentData().remove(LAST_ATTEMPT);
        vehicle.getPersistentData().putBoolean(REPLACEMENT_CREW,true);
        RaidSavedData.get(level.getServer()).setDirty();return true;
    }
}
