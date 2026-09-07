package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Wave retreat is not a kill and must survive unloaded entities and server restarts. */
public final class CampSabotage {
    private CampSabotage() {}

    public static void retreatWave(ServerLevel level, RaidState raid) {
        raid.retreatedRaiders.addAll(raid.raiders);
        raid.totalEscaped += raid.raiders.size();
        for (var id : raid.raiders) {
            Entity entity = level.getEntity(id);
            if (entity != null) entity.discard();
        }
        raid.raiders.clear();
        raid.missingTicks.clear();
        raid.lastKnownChunks.clear();
        raid.pendingWaveSpawns = 0;
        raid.bannerPos = null;
    }

    public static boolean discardRetreated(Entity entity, RaidState raid) {
        if (!raid.retreatedRaiders.contains(entity.getUUID())) return false;
        entity.discard();
        return true;
    }
}
