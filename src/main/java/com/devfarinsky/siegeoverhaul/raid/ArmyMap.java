package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Feeds the world map with the live position of a marching enemy army.
 *
 * <p>Only the faction under attack is told where the army is, and only while
 * its own siege is running, so this never becomes a way to watch somebody
 * else's war. Positions are sent every siege pass and the map interpolates
 * between them, which is what makes the icons crawl across the map the way the
 * player's own icon does instead of blinking from place to place.
 */
public final class ArmyMap {

    private ArmyMap() {}

    /** Live marker list for one siege, soldiers first and siege equipment last. */
    public static List<RaidNetwork.ArmyMarkers.Marker> markers(ServerLevel level, RaidSavedData.RaidState state) {
        List<RaidNetwork.ArmyMarkers.Marker> markers = new ArrayList<>();
        collect(level, state.raiders, false, markers);
        collect(level, state.siegeEngines.keySet(), true, markers);
        return markers;
    }

    private static void collect(ServerLevel level, Iterable<UUID> ids, boolean equipment,
                                List<RaidNetwork.ArmyMarkers.Marker> markers) {
        for (UUID id : ids) {
            if (markers.size() >= RaidNetwork.ArmyMarkers.LIMIT) return;
            Entity entity = level.getEntity(id);
            if (entity == null || !entity.isAlive() || entity.isPassenger()) continue;
            markers.add(new RaidNetwork.ArmyMarkers.Marker(entity.getId(),
                    entity.blockPosition().getX(), entity.blockPosition().getZ(), equipment));
        }
    }

    /** Send the current army positions to the defending faction's online members. */
    public static void broadcast(ServerLevel level, RaidSavedData.RaidState state, List<ServerPlayer> members) {
        if (!RaidConfig.SHOW_ARMY_ON_MAP.get() || members.isEmpty()) return;
        var packet = new RaidNetwork.ArmyMarkers(state.teamKey, markers(level, state));
        for (ServerPlayer member : members) RaidNetwork.armyMarkers(member, packet);
    }
}
