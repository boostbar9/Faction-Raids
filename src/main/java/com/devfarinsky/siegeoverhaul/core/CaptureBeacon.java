package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import net.minecraft.core.BlockPos;
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
    /** Called at the existing once-per-second capture cadence; no chunk loads or block edits. */
    public static void send(ServerLevel level, String team, BlockPos pos, int progress, int maximum, int allies) {
        int value = progress > 0 || allies > 0 ? percent(progress, maximum) : -1;
        RaidNetwork.CaptureBeam packet = null;
        for(var player:level.players()) {
            if(!team.equals(SiegeCore.key(player)) || player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE * RANGE) continue;
            if(packet==null) packet=new RaidNetwork.CaptureBeam(level.dimension().location(),pos,value,level.getGameTime());
            RaidNetwork.sendCaptureBeam(player,packet);
        }
    }

    /** Client cache has a hard bound and expires interrupted captures without a cleanup packet. */
    public static final class State {
        private final Map<BlockPos,RaidNetwork.CaptureBeam> beams = new LinkedHashMap<>();
        public void clear() { beams.clear(); }
        public void accept(RaidNetwork.CaptureBeam packet, long now) {
            if(packet.time()>now+5 || now-packet.time()>=LIFETIME) return;
            var previous=beams.get(packet.pos());
            if(previous!=null && previous.time()>packet.time()) return;
            if(packet.percent()<0) { beams.remove(packet.pos());return; }
            if(!beams.containsKey(packet.pos()) && beams.size()>=LIMIT) beams.remove(beams.keySet().iterator().next());
            beams.put(packet.pos(),packet);
        }
        public java.util.List<RaidNetwork.CaptureBeam> active(long now) {
            beams.values().removeIf(p -> p.time()>now+5 || now-p.time()>=LIFETIME);
            return java.util.List.copyOf(beams.values());
        }
    }
}
