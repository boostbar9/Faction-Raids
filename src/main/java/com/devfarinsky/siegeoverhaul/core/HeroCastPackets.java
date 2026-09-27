package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** Tracking transport kept separate from the server casting state machine. */
final class HeroCastPackets {
    private HeroCastPackets() {}
    static RaidNetwork.HeroCast packet(Mob hero,int role,long start,int phase) {
        return new RaidNetwork.HeroCast(hero.getId(),hero.getUUID(),role,start,phase,hero.getX(),hero.getY(),hero.getZ());
    }
    static void send(Mob hero,int role,long start,int phase) {
        RaidNetwork.sendHeroCast(hero,packet(hero,role,start,phase));
    }
    static void sync(ServerPlayer player,Mob hero,int role,long start) {
        RaidNetwork.sendHeroCast(player,packet(hero,role,start,0));
    }
}
