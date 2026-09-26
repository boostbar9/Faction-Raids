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
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianHeroCastingTest extends MinecraftTestSupport {
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
            try(var enemy=mockStatic(EnemyHeroes.class,CALLS_REAL_METHODS)) {
                enemy.when(()->EnemyHeroes.defender(f.hero,f.target)).thenReturn(true);
                f.tick();f.tick();
                assertEquals(100+(role==22?300:role==27?400:600),f.tag.getLong("SiegeHeroNext"));
                if(role==29)verify(f.target,times(1)).addEffect(argThat(e->e.getEffect()==net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN && e.getDuration()==80 && e.getAmplifier()==4));
                else verify(f.target,times(1)).hurt(f.damage,role==22?5F:6F);
                if(role==27)verify(f.target,times(1)).setSecondsOnFire(4);
                var style=OlympianPresentation.forFaction(CoreHiring.heroFaction(role));
                verify(f.level,times(1)).sendParticles(eq(style.particle()),anyDouble(),anyDouble(),anyDouble(),eq(role==22?36:16),anyDouble(),anyDouble(),anyDouble(),anyDouble());
                assertFalse(HeroTraits.ready(100,f.tag.copy().getLong("SiegeHeroNext")));
            }
        }
    }
}
