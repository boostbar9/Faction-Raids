package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Tracking transport kept separate from the server casting state machine. */
public final class HeroCastPackets {
    private HeroCastPackets() {}
    static RaidNetwork.HeroCast packet(LivingEntity hero,int role,long start,int phase) {
        return new RaidNetwork.HeroCast(hero.getId(),hero.getUUID(),role,start,phase,hero.getX(),hero.getY(),hero.getZ());
    }
    public static void send(LivingEntity hero,int role,long start,int phase) {
        var packet = packet(hero,role,start,phase);
        RaidNetwork.sendHeroCast(hero,packet);
        if (hero instanceof ServerPlayer player) RaidNetwork.sendHeroCast(player,packet);
    }
    static void sync(ServerPlayer player,LivingEntity hero,int role,long start) {
        RaidNetwork.sendHeroCast(player,packet(hero,role,start,0));
    }
}
