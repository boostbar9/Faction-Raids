package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.waves.WaveComposer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnemyHeroesTest extends MinecraftTestSupport {
    @Test void tenPercentWaveChanceAndDisabledOpeningWave() {
        for (int roll = 0; roll < 100; roll++) {
            var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
            var random = mock(RandomSource.class);
            when(random.nextInt(100)).thenReturn(roll, 0);
            EnemyHeroes.plan(state, 2, 5, 12, true, 10, true, true, random);
            assertEquals(roll < 10, state.enemyHeroRole >= 0);
            int count = 0;
            for (int i = 0; i < 12; i++) if (EnemyHeroes.roleAt(state, i) >= 0) count++;
            assertEquals(roll < 10 ? 1 : 0, count);
        }
        for (int mode = 0; mode < 3; mode++) {
            var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
            var random = mock(RandomSource.class);
            EnemyHeroes.plan(state, mode == 0 ? 1 : 2, 5, 12, mode != 1, mode == 2 ? 0 : 100, true, true, random);
            assertEquals(-1, state.enemyHeroRole);
            verifyNoInteractions(random);
        }
    }
    @Test void fullSharedRosterAndRarityWeightsAreReachable() {
        var counts = new int[CoreHiring.HERO_ID_MAX - CoreHiring.HERO_ID_MIN + 1];
        for (int roll = 0; roll < 100; roll++) {
            var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
            var random = mock(RandomSource.class);
            when(random.nextInt(100)).thenReturn(0, roll);
            EnemyHeroes.plan(state, 2, 5, 12, true, 100, true, true, random);
            assertEquals(CoreOffers.hero(roll), state.enemyHeroRole);
            counts[state.enemyHeroRole - CoreHiring.HERO_ID_MIN]++;
        }
        assertArrayEquals(CoreOffers.HERO_WEIGHTS, counts);
    }
    @Test void reservedBossSlotsAndTinyWavesRemainIntact() {
        for (int wave = 2; wave <= 5; wave++) for (int size = 0; size < 12; size++) {
            var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
            EnemyHeroes.plan(state, wave, 5, size, true, 100, true, true, mock(RandomSource.class));
            if (state.enemyHeroSlot >= 0) {
                assertTrue(state.enemyHeroSlot < size);
                assertFalse(WaveComposer.reserved(wave, 5, state.enemyHeroSlot, true, true));
            } else if (size > 0) {
                for (int i = 0; i < size; i++) assertTrue(WaveComposer.reserved(wave, 5, i, true, true));
            }
        }
    }
    @Test void savedSelectionSurvivesRetriesReloadAndOldSavesHaveNoHero() {
        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        EnemyHeroes.plan(state, 5, 5, 12, true, 100, true, true, mock(RandomSource.class));
        var loaded = RaidSavedData.RaidState.load(state.save());
        assertEquals(state.enemyHeroRole, loaded.enemyHeroRole);
        assertEquals(state.enemyHeroSlot, loaded.enemyHeroSlot);
        for (int retry = 0; retry < 10; retry++)
            assertEquals(state.enemyHeroRole, EnemyHeroes.roleAt(loaded, loaded.enemyHeroSlot));
        var old = state.save(); old.remove("EnemyHeroRole"); old.remove("EnemyHeroSlot");
        assertEquals(-1, RaidSavedData.RaidState.load(old).enemyHeroRole);
        loaded.enemyHeroRole = 999;
        assertEquals(-1, EnemyHeroes.roleAt(loaded, loaded.enemyHeroSlot));
    }
    @Test void enemyIdentityAndSupportAreLimitedToTheSameInvasion() {
        var hero = mock(Mob.class); var other = mock(Mob.class);
        var tag = new CompoundTag(); var otherTag = new CompoundTag();
        when(hero.getPersistentData()).thenReturn(tag);
        when(other.getPersistentData()).thenReturn(otherTag);
        tag.putBoolean("SiegeEnemyHero", true); tag.putInt("SiegeHeroRole", 15);
        assertFalse(EnemyHeroes.active(hero));
        tag.putString(ModConstants.Tags.RAID_TEAM, "team:test");
        assertTrue(EnemyHeroes.active(hero));
        otherTag.putString(ModConstants.Tags.CAMP_WORKER_TEAM, "team:test");
        assertTrue(EnemyHeroes.ally(hero, other));
        otherTag.putString(ModConstants.Tags.CAMP_WORKER_TEAM, "team:other");
        assertFalse(EnemyHeroes.ally(hero, other));
        otherTag.remove(ModConstants.Tags.CAMP_WORKER_TEAM);
        assertFalse(EnemyHeroes.ally(hero, other));
    }
    @Test void enemyAbilitiesIgnoreBystandersCreativeSpectatorsAndOtherRaiders() {
        var player = mock(Player.class); var tag = new CompoundTag();
        var id = UUID.randomUUID();
        when(player.getPersistentData()).thenReturn(tag);
        when(player.getUUID()).thenReturn(id);
        when(player.isAlive()).thenReturn(true);
        assertTrue(EnemyHeroes.defender(player, "team:test", Set.of(id)));
        assertFalse(EnemyHeroes.defender(player, "team:test", Set.of(UUID.randomUUID())));
        when(player.isCreative()).thenReturn(true);
        assertFalse(EnemyHeroes.defender(player, "team:test", Set.of(id)));
        when(player.isCreative()).thenReturn(false); when(player.isSpectator()).thenReturn(true);
        assertFalse(EnemyHeroes.defender(player, "team:test", Set.of(id)));
        when(player.isSpectator()).thenReturn(false); tag.putString(ModConstants.Tags.RAID_TEAM, "other");
        assertFalse(EnemyHeroes.defender(player, "team:test", Set.of(id)));
    }

    @Test void enemyIronoathActuallyHealsAgainstDefenderButNeverAgainstFriendlyUnits() {
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var hero = mock(Mob.class); var victim = mock(Player.class);
        var tag = new CompoundTag(); tag.putBoolean("SiegeEnemyHero", true);
        tag.putInt("SiegeHeroRole", 10); tag.putString(ModConstants.Tags.RAID_TEAM, "team:test");
        when(hero.getPersistentData()).thenReturn(tag);
        when(hero.level()).thenReturn(level); when(hero.isAlive()).thenReturn(true);
        when(hero.hasLineOfSight(victim)).thenReturn(true);
        when(hero.getHealth()).thenReturn(10F); when(hero.getMaxHealth()).thenReturn(60F);
        var victimTag = new CompoundTag(); when(victim.getPersistentData()).thenReturn(victimTag);
        var id = UUID.randomUUID(); when(victim.getUUID()).thenReturn(id); when(victim.isAlive()).thenReturn(true);
        var data = new RaidSavedData(); var anchor = mock(RaidSavedData.Anchor.class);
        when(anchor.members()).thenReturn(Set.of(id)); data.anchors.put("team:test", anchor);
        try (var saved = mockStatic(RaidSavedData.class)) {
            saved.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            var type = net.minecraft.core.Holder.direct(new net.minecraft.world.damagesource.DamageType("test", 0));
            var event = new net.minecraftforge.event.entity.living.LivingDamageEvent(victim,
                    new net.minecraft.world.damagesource.DamageSource(type, hero), 2);
            HeroTraits.hit(event); HeroTraits.hit(event); verify(hero, never()).heal(anyFloat());
            HeroTraits.hit(event); verify(hero).heal(2);
            victimTag.putString(ModConstants.Tags.RAID_TEAM, "team:test");
            tag.putInt("SiegeHeroHits", 0);
            HeroTraits.hit(event); assertEquals(0, tag.getInt("SiegeHeroHits"));
        }
    }
    @Test void enemyShadowHasRaidIdentityAndStillExpiresWhilePaused() {
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var hero = mock(Mob.class); var wolf = mock(net.minecraft.world.entity.animal.Wolf.class);
        var tag = new CompoundTag(); tag.putBoolean("SiegeEnemyHero", true);
        tag.putInt("SiegeHeroRole", 28); tag.putString(ModConstants.Tags.RAID_TEAM, "team:test");
        when(hero.getPersistentData()).thenReturn(tag);
        var wolfTag = new CompoundTag(); wolfTag.putLong("SiegeShadowDespawn", 600);
        when(wolf.getPersistentData()).thenReturn(wolfTag); when(wolf.level()).thenReturn(level);
        when(wolf.blockPosition()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        when(level.noCollision(wolf)).thenReturn(true);
        when(level.getFluidState(net.minecraft.core.BlockPos.ZERO)).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        var data = new RaidSavedData(); var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        data.raids.put("team:test", raid);
        try (var saved = mockStatic(RaidSavedData.class)) {
            saved.when(() -> RaidSavedData.get(level.getServer())).thenReturn(data);
            assertTrue(EnemyHeroes.prepareShadow(level, hero, wolf));
            assertEquals("team:test", wolfTag.getString(ModConstants.Tags.RAID_TEAM));
            for (int i = 0; i < RaidConfig.MAX_ACTIVE_RAIDERS.get(); i++) raid.raiders.add(UUID.randomUUID());
            assertFalse(EnemyHeroes.prepareShadow(level, hero, wolf));
        }
        when(wolf.isNoAi()).thenReturn(true); when(level.getGameTime()).thenReturn(600L);
        HeroTraits.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(wolf));
        verify(wolf).discard();
    }
}
