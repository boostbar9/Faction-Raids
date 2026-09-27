package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-owned wind-up. Reloads cancel unfinished casts while retaining the existing cooldown. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class HeroCasting {
    public static final int WINDUP = 20;
    public static final int RELEASE = 12;
    private static final Map<Mob, Cast> ACTIVE = new WeakHashMap<>();
    private record Cast(int role, UUID target, long start) {}
    private HeroCasting() {}
    public static boolean supported(int role) { return role == 22 || role == 27 || role == 29; }
    public static int radius(int role) { return role == 22 ? 6 : role == 27 ? 8 : 10; }
    static int cooldown(int role) { return role == 22 ? 300 : role == 27 ? 400 : 600; }

    static boolean begin(ServerLevel level, Mob hero, int role) {
        if (!supported(role) || ACTIVE.containsKey(hero) || ACTIVE.size() >= 512
                || hero.isPassenger() || !hero.isAlive() || hero.isNoAi()) return false;
        var target=hero.getTarget();
        if (target == null || !HeroTraits.hostile(hero,target) || foes(level,hero,role).isEmpty()) return false;
        long now=level.getGameTime();
        if (!HeroTraits.ready(now,hero.getPersistentData().getLong("SiegeHeroNext"))) return false;
        ACTIVE.put(hero,new Cast(role,target.getUUID(),now));
        hero.getPersistentData().putLong("SiegeHeroNext",now+cooldown(role));
        send(hero,role,now,0);
        return true;
    }
    /** Return true while processing a cast, including its release/cancellation tick. */
    static boolean tick(ServerLevel level, Mob hero) {
        Cast cast=ACTIVE.get(hero);
        if (cast == null) return false;
        var target=hero.getTarget();
        long elapsed=level.getGameTime()-cast.start;
        if (!hero.isAlive() || hero.isRemoved() || hero.isNoAi() || hero.isPassenger()
                || HeroTraits.role(hero)!=cast.role || target==null || !cast.target.equals(target.getUUID())
                || !HeroTraits.hostile(hero,target) || elapsed<0 || elapsed>WINDUP+20) {
            ACTIVE.remove(hero);send(hero,cast.role,level.getGameTime(),2);return true;
        }
        hero.getLookControl().setLookAt(target,30,30);
        if (elapsed < WINDUP) return true;
        // Remove before damage events, so reentrant callbacks cannot release twice.
        ACTIVE.remove(hero);
        var foes=foes(level,hero,cast.role);
        if (foes.isEmpty()) { send(hero,cast.role,level.getGameTime(),2);return true; }
        for (var enemy:foes) {
            if (cast.role == 29) enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,80,4));
            else {
                enemy.hurt(level.damageSources().indirectMagic(hero,hero),cast.role==22?5:6);
                if (cast.role==27) enemy.setSecondsOnFire(4);
            }
        }
        hero.swing(InteractionHand.MAIN_HAND);
        send(hero,cast.role,level.getGameTime(),1);
        return true;
    }
    private static List<LivingEntity> foes(ServerLevel level,Mob hero,int role) {
        return level.getEntitiesOfClass(LivingEntity.class,hero.getBoundingBox().inflate(radius(role)),
                other -> HeroTraits.hostile(hero,other));
    }
    private static RaidNetwork.HeroCast packet(Mob hero,int role,long start,int phase) {
        return new RaidNetwork.HeroCast(hero.getId(),hero.getUUID(),role,start,phase,hero.getX(),hero.getY(),hero.getZ());
    }
    private static void send(Mob hero,int role,long start,int phase) {
        RaidNetwork.sendHeroCast(hero,packet(hero,role,start,phase));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && event.getTarget() instanceof Mob hero && ACTIVE.containsKey(hero)) {
            Cast cast=ACTIVE.get(hero);
            RaidNetwork.sendHeroCast(player,packet(hero,cast.role,cast.start,0));
        }
    }
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if(event.getEntity() instanceof Mob hero)ACTIVE.remove(hero);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
