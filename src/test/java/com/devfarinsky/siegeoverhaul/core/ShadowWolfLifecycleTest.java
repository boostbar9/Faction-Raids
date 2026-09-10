package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShadowWolfLifecycleTest extends MinecraftTestSupport {
    private Wolf wolf(ServerLevel level, CompoundTag tag) {
        Wolf wolf = mock(Wolf.class);
        when(wolf.level()).thenReturn(level);
        when(wolf.getPersistentData()).thenReturn(tag);
        when(wolf.isAlive()).thenReturn(true);
        return wolf;
    }

    @Test void freshSummonWithoutHeroFlagExpiresExactlyAtDeadline() {
        var level = mock(ServerLevel.class);
        var tag = new CompoundTag();
        tag.putLong("SiegeShadowDespawn", 600);
        var wolf = wolf(level, tag);
        when(level.getGameTime()).thenReturn(599L);
        HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
        verify(wolf, never()).discard();
        assertFalse(tag.contains("SiegeHeroRole"));
        when(level.getGameTime()).thenReturn(600L);
        HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
        verify(wolf).discard();
    }

    @Test void reloadedLegacySummonExpiresEvenWithInvalidHeroRoleAndDisabledAi() {
        var level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(1000L);
        var saved = new CompoundTag();
        saved.putLong("SiegeShadowDespawn", 600);
        saved.putBoolean("SiegeHiredHero", true);
        saved.putInt("SiegeHeroRole", -1);
        var loaded = saved.copy();
        var wolf = wolf(level, loaded);
        when(wolf.isNoAi()).thenReturn(true);
        HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
        verify(wolf).discard();
        assertFalse(loaded.contains("SiegeHiredHero"));
        assertFalse(loaded.contains("SiegeHeroRole"));
    }

    @Test void legacySummonKeepsOriginalDeadlineDuringMigration() {
        var level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(100L);
        var tag = new CompoundTag();
        tag.putLong("SiegeShadowDespawn", 600);
        tag.putBoolean("SiegeHiredHero", true);
        var wolf = wolf(level, tag);
        HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
        verify(wolf, never()).discard();
        assertEquals(600, tag.getLong("SiegeShadowDespawn"));
        assertFalse(tag.contains("SiegeHiredHero"));
    }

    @Test void ordinaryAndTamedWolvesWithoutDeadlineAreNeverRemoved() {
        for (boolean tamed : new boolean[]{false, true}) {
            var level = mock(ServerLevel.class);
            when(level.getGameTime()).thenReturn(Long.MAX_VALUE);
            var tag = new CompoundTag();
            var wolf = wolf(level, tag);
            when(wolf.isTame()).thenReturn(tamed);
            HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
            verify(wolf, never()).discard();
            assertTrue(tag.isEmpty());
        }
    }

    @Test void cleanupRemainsBoundedToOncePerSecond() {
        var level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(1000L);
        var tag = new CompoundTag();
        tag.putLong("SiegeShadowDespawn", 600);
        var wolf = wolf(level, tag);
        wolf.tickCount = 19;
        HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
        verify(wolf, never()).discard();
        wolf.tickCount = 20;
        HeroTraits.tick(new LivingEvent.LivingTickEvent(wolf));
        verify(wolf).discard();
    }
}
