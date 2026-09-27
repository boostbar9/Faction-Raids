package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.narrative.OlympianPresentation;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianHeroCastingTest extends MinecraftTestSupport {
    @AfterEach void clearCasts(){HeroCasting.stopped(null);}
    private static class Fixture {
        final ServerLevel level=mock(ServerLevel.class);
        final Mob hero=mock(Mob.class),target=mock(Mob.class);
        final CompoundTag tag=new CompoundTag(),enemy=new CompoundTag();
        final DamageSource damage=new DamageSource(Holder.direct(new DamageType("test",0)),hero);
        Fixture(int role,boolean hostileHero) {
            tag.putInt("SiegeHeroRole",role);tag.putBoolean(hostileHero?"SiegeEnemyHero":"SiegeHiredHero",true);
            if(hostileHero)tag.putString(ModConstants.Tags.RAID_TEAM,"team:test");
            enemy.putString(ModConstants.Tags.RAID_TEAM,"team:foe");
            when(hero.getPersistentData()).thenReturn(tag);when(target.getPersistentData()).thenReturn(enemy);
            when(hero.level()).thenReturn(level);when(level.getGameTime()).thenReturn(100L);
            when(hero.getUUID()).thenReturn(UUID.randomUUID());when(target.getUUID()).thenReturn(UUID.randomUUID());
            when(hero.getLookControl()).thenReturn(mock(net.minecraft.world.entity.ai.control.LookControl.class));
            when(hero.isAlive()).thenReturn(true);when(target.isAlive()).thenReturn(true);
            when(hero.getTarget()).thenReturn(target);when(hero.hasLineOfSight(target)).thenReturn(true);
            when(hero.getBoundingBox()).thenReturn(new AABB(0,64,0,1,66,1));hero.tickCount=20;
            when(level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any())).thenAnswer(call -> {
                Predicate<LivingEntity> filter=call.getArgument(2);
                return filter.test(target)?List.of(target):List.of();
            });
            var sources=mock(DamageSources.class);when(level.damageSources()).thenReturn(sources);
            when(sources.indirectMagic(hero,hero)).thenReturn(damage);
        }
        void tick(){HeroTraits.tick(new LivingEvent.LivingTickEvent(hero));}
    }
    @Test void staleFriendlyAndHiddenTargetsCannotConsumeSpellCooldownsOrSummonHounds() {
        for(int role:new int[]{22,27,28,29}) {
            var f=new Fixture(role,false);
            when(f.target.isAlive()).thenReturn(false);f.tick();
            when(f.target.isAlive()).thenReturn(true);when(f.hero.isAlliedTo(f.target)).thenReturn(true);f.tick();
            when(f.hero.isAlliedTo(f.target)).thenReturn(false);when(f.hero.hasLineOfSight(f.target)).thenReturn(false);f.tick();
            assertFalse(f.tag.contains("SiegeHeroNext"),"role "+role);
            verify(f.level,never()).getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any());
            verify(f.level,never()).addFreshEntity(any());
        }
    }
    @Test void areaSpellsWaitForAnEnemyInsideTheirReach() {
        for(int role:new int[]{22,27,29}) {
            var f=new Fixture(role,false);
            doReturn(List.of()).when(f.level).getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any());
            f.tick();assertFalse(f.tag.contains("SiegeHeroNext"));
            verify(f.target,never()).hurt(any(),anyFloat());
        }
    }
    @Test void hiredAndEnemyCastersKeepDamageAndTimersWithPatronSpecificCues() {
        for(boolean enemyHero:new boolean[]{false,true})for(int role:new int[]{22,27,29}) {
            var f=new Fixture(role,enemyHero);
            try(var enemy=mockStatic(EnemyHeroes.class,CALLS_REAL_METHODS);var network=mockStatic(HeroCastPackets.class)) {
                enemy.when(()->EnemyHeroes.defender(f.hero,f.target)).thenReturn(true);
                f.tick();f.tick();
                verify(f.target,never()).hurt(any(),anyFloat());verify(f.target,never()).addEffect(any());
                when(f.level.getGameTime()).thenReturn(119L);f.tick();
                verify(f.target,never()).hurt(any(),anyFloat());
                when(f.level.getGameTime()).thenReturn(120L);f.tick();f.tick();
                verify(f.level,times(2)).getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any());
                assertEquals(100+(role==22?300:role==27?400:600),f.tag.getLong("SiegeHeroNext"));
                if(role==29)verify(f.target,times(1)).addEffect(argThat(e->e.getEffect()==net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN && e.getDuration()==80 && e.getAmplifier()==4));
                else verify(f.target,times(1)).hurt(f.damage,role==22?5F:6F);
                if(role==27)verify(f.target,times(1)).setSecondsOnFire(4);
                network.verify(()->HeroCastPackets.send(eq(f.hero),eq(role),eq(100L),eq(0)),times(1));
                network.verify(()->HeroCastPackets.send(eq(f.hero),eq(role),eq(120L),eq(1)),times(1));
                assertFalse(HeroTraits.ready(100,f.tag.copy().getLong("SiegeHeroNext")));
            }
        }
    }
    @Test void pendingCastCancelsWhenItsTargetBecomesFriendlyHiddenOrChanges() {
        for(int reason=0;reason<5;reason++) {
            var f=new Fixture(22,false);
            try(var network=mockStatic(HeroCastPackets.class)) {
                f.tick();
                switch(reason) {
                    case 0 -> when(f.hero.isAlliedTo(f.target)).thenReturn(true);
                    case 1 -> when(f.hero.hasLineOfSight(f.target)).thenReturn(false);
                    case 2 -> when(f.hero.getTarget()).thenReturn(null);
                    case 3 -> when(f.hero.isPassenger()).thenReturn(true);
                    case 4 -> f.tag.putInt("SiegeHeroRole",27);
                }
                when(f.level.getGameTime()).thenReturn(120L);f.tick();f.tick();
                verify(f.target,never()).hurt(any(),anyFloat());
                network.verify(()->HeroCastPackets.send(eq(f.hero),anyInt(),eq(120L),eq(2)),times(1));
                assertEquals(400L,f.tag.getLong("SiegeHeroNext"));
            }
        }
    }
    @Test void reloadAndExpiredCastsDoNotReleaseOrResetCooldown() {
        for(boolean reload:new boolean[]{false,true}) {
            var f=new Fixture(27,false);
            try(var network=mockStatic(HeroCastPackets.class)) {
                f.tick();
                if(reload)HeroCasting.stopped(null);
                when(f.level.getGameTime()).thenReturn(160L);f.tick();
                verify(f.target,never()).hurt(any(),anyFloat());
                assertEquals(500L,f.tag.getLong("SiegeHeroNext"));
            }
        }
    }
    @Test void releaseRechecksEveryAreaVictim() {
        var f=new Fixture(27,false);
        try(var network=mockStatic(HeroCastPackets.class)) {
            f.tick();
            doReturn(List.of()).when(f.level).getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any());
            when(f.level.getGameTime()).thenReturn(120L);f.tick();
            verify(f.target,never()).hurt(any(),anyFloat());
            network.verify(()->HeroCastPackets.send(eq(f.hero),anyInt(),eq(120L),eq(2)));
        }
    }
    @Test void onlyServerUnloadsCancelServerCasts() {
        for(boolean serverUnload:new boolean[]{false,true}) {
            var f=new Fixture(22,false);
            try(var network=mockStatic(HeroCastPackets.class)) {
                f.tick();
                var level=serverUnload?f.level:mock(net.minecraft.world.level.Level.class);
                HeroCasting.unloaded(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(f.hero,level));
                when(f.level.getGameTime()).thenReturn(120L);f.tick();
                verify(f.target,times(serverUnload?0:1)).hurt(f.damage,5F);
                assertEquals(400L,f.tag.getLong("SiegeHeroNext"));
            }
        }
    }
}
