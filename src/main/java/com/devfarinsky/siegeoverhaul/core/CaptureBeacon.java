package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, cosmetic capture snapshots. Capture rules remain server-owned. */
public final class CaptureBeacon {
    public static final int RANGE = 192, HEIGHT = 96, LIFETIME = 60, LIMIT = 16;
    private CaptureBeacon() {}

    public static int percent(int progress, int maximum) {
        return maximum <= 0 ? 0 : (int)Math.max(0, Math.min(100, (long)progress * 100 / maximum));
    }
    public static float[] color(int percent) {
        float[][] stops = {{1F,.12F,.08F}, {1F,.48F,.05F}, {1F,.9F,.12F}, {.55F,1F,.12F}, {.12F,1F,.35F}};
        float at = Math.max(0, Math.min(100, percent)) / 25F;
        int lower = Math.min(3, (int)at);
        float blend = at - lower;
        float[] rgb = new float[3];
        for(int i=0;i<3;i++) rgb[i]=stops[lower][i]+(stops[lower+1][i]-stops[lower][i])*blend;
        return rgb;
    }
    /** Existing once-per-second cadence. Includes idle cores so the boundary is visible on approach. */
    public static void send(ServerLevel level, String team, BlockPos pos, int progress, int maximum, int allies, int enemies) {
        int radius = RaidConfig.CORE_CAPTURE_RADIUS.get(), vertical = RaidConfig.CORE_CAPTURE_VERTICAL.get();
        boolean sight = RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.get();
        for (var player : level.players()) {
            if (!team.equals(SiegeCore.key(player)) || player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE * RANGE) continue;
            var participation = participation(level, player, pos, radius, vertical, sight);
            RaidNetwork.sendCaptureBeam(player, new RaidNetwork.CaptureBeam(level.dimension().location(), pos,
                    percent(progress, maximum), level.getGameTime(), radius, vertical, sight, allies, enemies, participation));
        }
    }

    /** Remove known invalid targets immediately; interrupted connections still expire naturally. */
    public static void clear(ServerLevel level, String team, BlockPos pos) {
        if (pos == null) return;
        for (var player : level.players()) {
            if (!team.equals(SiegeCore.key(player)) || player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE * RANGE) continue;
            RaidNetwork.sendCaptureBeam(player, new RaidNetwork.CaptureBeam(level.dimension().location(), pos, -1,
                    level.getGameTime(), RaidConfig.CORE_CAPTURE_RADIUS.get(), RaidConfig.CORE_CAPTURE_VERTICAL.get(),
                    RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.get(), 0, 0, CaptureStatus.Participation.UNAVAILABLE));
        }
    }

    static CaptureStatus.Participation participation(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                                     BlockPos pos, int radius, int vertical, boolean sight) {
        var eligibility = CaptureStatus.eligibility(player.isAlive(), player.isCreative(), player.isSpectator());
        if (eligibility != CaptureStatus.Participation.COUNTED) return eligibility;
        Vec3 at = player.position();
        double dx = at.x - pos.getX() - .5, dy = at.y - pos.getY() - .5, dz = at.z - pos.getZ() - .5;
        boolean inside = CaptureRing.inside(dx, dy, dz, radius, vertical);
        boolean loaded = !sight || !inside || CaptureBoundary.loadedSight(level, pos, at);
        boolean visible = !inside || !sight || loaded && CaptureRing.visible(level, pos, at);
        return CaptureStatus.participation(dx, dy, dz, radius, vertical, true, loaded, visible);
    }

    /** Client cache includes clear tombstones so reordered packets cannot resurrect a removed marker. */
    public static final class State {
        private final Map<BlockPos,RaidNetwork.CaptureBeam> beams = new LinkedHashMap<>();
        private Object world;
        private ResourceLocation dimension;
        public void clear() { beams.clear(); }
        public void world(Object identity, ResourceLocation dimension) {
            if (world != identity || !java.util.Objects.equals(this.dimension, dimension)) clear();
            world = identity; this.dimension = dimension;
        }
        private static boolean stale(long time, long now) {
            return now < 0 || (time > now ? time - now > 5 : now - time >= LIFETIME);
        }
        public void accept(RaidNetwork.CaptureBeam packet, long now) {
            if (world == null || !packet.dimension().equals(dimension) || stale(packet.time(), now)) return;
            var previous = beams.get(packet.pos());
            if (previous != null && (previous.time() > packet.time()
                    || previous.percent() < 0 && previous.time() == packet.time())) return;
            beams.values().removeIf(p -> stale(p.time(), now));
            if (!beams.containsKey(packet.pos()) && beams.size() >= LIMIT) beams.remove(beams.keySet().iterator().next());
            beams.put(packet.pos(), packet);
        }
        public java.util.List<RaidNetwork.CaptureBeam> active(long now) {
            beams.values().removeIf(p -> stale(p.time(), now));
            return beams.values().stream().filter(p -> p.percent() >= 0).toList();
        }
    }
}
