package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Native cavalry mounts march with their riders and cannot remain as abandoned raid entities. */
public final class RaidCavalry {
    private static final String TEAM="SiegeCavalryTeam";
    private RaidCavalry() {}
    public static void join(EntityJoinLevelEvent event,ServerLevel level) {
        if(!(event.getEntity() instanceof AbstractHorse horse))return;
        for(var passenger:horse.getPassengers()) {
            String team=passenger.getPersistentData().getString(ModConstants.Tags.RAID_TEAM);
            if(!team.isBlank()) { horse.getPersistentData().putString(TEAM,team);horse.setPersistenceRequired(); }
        }
        String team=horse.getPersistentData().getString(TEAM);
        if(!team.isBlank() && event.loadedFromDisk() && !RaidSavedData.get(level.getServer()).raids.containsKey(team) && horse.getOwnerUUID()==null) {
            horse.discard();event.setCanceled(true);
        }
    }
    public static void advance(Mob rider,Vec3 objective,double speed) {
        if(!(rider.getVehicle() instanceof AbstractHorse horse))return;
        if(rider.distanceToSqr(objective)<18*18 || horse.horizontalCollision) {
            rider.stopRiding(); horse.getNavigation().stop();
            try { rider.getClass().getMethod("setShouldMount",boolean.class).invoke(rider,false); }
            catch(ReflectiveOperationException ignored) { }
            return;
        }
        if(rider.getTarget()==null || !rider.getTarget().isAlive())horse.getNavigation().moveTo(objective.x,objective.y,objective.z,speed);
    }
    public static void cleanup(ServerLevel level,String team) {
        for(var entity:level.getAllEntities())if(entity instanceof AbstractHorse horse && team.equals(horse.getPersistentData().getString(TEAM))) {
            if(horse.getOwnerUUID()==null) horse.discard(); else horse.getPersistentData().remove(TEAM);
        }
    }
}
