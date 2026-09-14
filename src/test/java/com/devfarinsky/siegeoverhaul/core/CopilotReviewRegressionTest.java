package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CopilotReviewRegressionTest extends MinecraftTestSupport {
    @Test void territoryDescriptionsNeverTouchPurchaseControlsOrFortificationStrip() {
        for (int w : new int[]{240,320,640,960,1920}) for (int h : new int[]{180,240,360,540,1080}) {
            var layout = CoreHireLayout.fit(w,h);
            for (int i=0; i<4; i++) {
                assertTrue(layout.territoryCardY(i)+19 < layout.territoryButtonY(i));
                if (layout.territoryDescriptionLines()>0)
                    assertTrue(layout.territoryCardY(i)+28+layout.territoryDescriptionLines()*10 <= layout.territoryButtonY(i)-6);
                assertTrue(layout.territoryButtonY(i)+18 <= layout.contentBottom()-32);
            }
        }
    }
    @Test void wildsongRestoresOneModifierAndExpiresEvenWithAiDisabled() {
        var hero=mock(Mob.class); var level=mock(ServerLevel.class); var tag=new CompoundTag();
        var speed=new AttributeInstance(Attributes.ATTACK_SPEED, a -> {});
        when(hero.level()).thenReturn(level); when(hero.getPersistentData()).thenReturn(tag);
        when(hero.getAttribute(Attributes.ATTACK_SPEED)).thenReturn(speed);
        double base=speed.getValue();
        HeroTraits.applyWildsongAttackSpeed(hero,100);
        HeroTraits.applyWildsongAttackSpeed(hero,100);
        assertEquals(base*1.2,speed.getValue(),0.00001);
        when(hero.isNoAi()).thenReturn(true); when(level.getGameTime()).thenReturn(220L);
        hero.tickCount=21;
        HeroTraits.tick(new LivingEvent.LivingTickEvent(hero));
        assertEquals(base,speed.getValue(),0.00001);
        assertFalse(tag.contains("SiegeWildsongAttackSpeedUntil"));
    }
    @Test void wildsongKillBonusWaitsForActualDeath() {
        var hero=mock(Mob.class); var target=mock(Mob.class); var level=mock(ServerLevel.class);
        var tag=new CompoundTag(); tag.putBoolean("SiegeHiredHero",true); tag.putInt("SiegeHeroRole",21);
        var enemy=new CompoundTag();enemy.putString(ModConstants.Tags.RAID_TEAM,"team:test");
        when(hero.getPersistentData()).thenReturn(tag);when(target.getPersistentData()).thenReturn(enemy);
        when(hero.level()).thenReturn(level);when(hero.isAlive()).thenReturn(true);when(target.isAlive()).thenReturn(true);
        when(hero.hasLineOfSight(target)).thenReturn(true);when(target.getHealth()).thenReturn(1F);
        var speed=new AttributeInstance(Attributes.ATTACK_SPEED,a -> {});
        when(hero.getAttribute(Attributes.ATTACK_SPEED)).thenReturn(speed);
        var type=net.minecraft.core.Holder.direct(new net.minecraft.world.damagesource.DamageType("test",0));
        var source=new net.minecraft.world.damagesource.DamageSource(type,hero);
        HeroTraits.hit(new LivingDamageEvent(target,source,2));
        assertFalse(tag.contains("SiegeWildsongAttackSpeedUntil"));
        when(target.isAlive()).thenReturn(false);
        HeroTraits.heroKill(new LivingDeathEvent(target,source));
        assertEquals(120,tag.getLong("SiegeWildsongAttackSpeedUntil"));
    }
    @Test void ashenheartUsesAttributedMeleeBurstWithCooldown() {
        var hero=mock(Mob.class);var target=mock(Mob.class);var level=mock(ServerLevel.class);
        var tag=new CompoundTag();tag.putBoolean("SiegeHiredHero",true);tag.putInt("SiegeHeroRole",24);
        var enemy=new CompoundTag();enemy.putString(ModConstants.Tags.RAID_TEAM,"team:test");
        when(hero.getPersistentData()).thenReturn(tag);when(target.getPersistentData()).thenReturn(enemy);
        when(hero.level()).thenReturn(level);when(hero.isAlive()).thenReturn(true);when(target.isAlive()).thenReturn(true);
        when(hero.hasLineOfSight(target)).thenReturn(true);when(target.getBoundingBox()).thenReturn(new AABB(0,0,0,1,2,1));
        when(level.getEntitiesOfClass(eq(net.minecraft.world.entity.LivingEntity.class),any(AABB.class),any())).thenReturn(java.util.List.of(target));
        var type=net.minecraft.core.Holder.direct(new net.minecraft.world.damagesource.DamageType("test",0));
        var source=new net.minecraft.world.damagesource.DamageSource(type,hero);
        var sources=mock(net.minecraft.world.damagesource.DamageSources.class);when(level.damageSources()).thenReturn(sources);
        when(sources.indirectMagic(hero,hero)).thenReturn(source);
        HeroTraits.hit(new LivingDamageEvent(target,source,2));
        HeroTraits.hit(new LivingDamageEvent(target,source,2));
        verify(target,times(1)).hurt(source,4);
        assertEquals(60,tag.getLong("SiegeHeroNext"));
        assertFalse(HeroTraits.description(24).contains("fireball"));
    }
}
