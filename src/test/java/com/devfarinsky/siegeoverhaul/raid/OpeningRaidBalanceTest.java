package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpeningRaidBalanceTest extends MinecraftTestSupport {
    private Mob raider() {
        Mob mob = mock(Mob.class);
        CompoundTag tag = new CompoundTag();
        tag.putString(ModConstants.Tags.RAID_TEAM, "team:test");
        when(mob.getPersistentData()).thenReturn(tag);
        AttributeInstance health = new AttributeInstance(Attributes.MAX_HEALTH, ignored -> {});
        health.setBaseValue(40);
        when(mob.getAttribute(Attributes.MAX_HEALTH)).thenReturn(health);
        when(mob.getMaxHealth()).thenAnswer(ignored -> (float) health.getValue());
        return mob;
    }

    @Test void healthAndDamageRampIncludesAllFourOpeningWaves() {
        assertTrue(RaidConfig.GENTLE_OPENING_WAVES.get());
        double[] hp = {24, 28, 32, 36, 40};
        float[] damage = {6F, 7F, 8F, 9F, 10F};
        for (int wave = 1; wave <= 5; wave++) {
            Mob mob = raider();
            OpeningRaidBalance.apply(mob, wave, true);
            assertEquals(hp[wave - 1], mob.getMaxHealth(), .0001);
            assertEquals(damage[wave - 1], OpeningRaidBalance.outgoingDamage(mob, 10F), .0001);
        }
    }

    @Test void healthComposesWithExistingModifiersAndCannotStackAfterReload() {
        Mob mob = raider();
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        health.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "Other mod bonus", 20,
                AttributeModifier.Operation.ADDITION));
        OpeningRaidBalance.apply(mob, 1, true);
        assertEquals(36, health.getValue(), .0001);
        verify(mob).setHealth(36F);
        CompoundTag savedStats = health.save();
        CompoundTag savedData = mob.getPersistentData().copy();
        Mob loaded = raider();
        loaded.getAttribute(Attributes.MAX_HEALTH).load(savedStats);
        when(loaded.getPersistentData()).thenReturn(savedData);
        OpeningRaidBalance.apply(loaded, 1, true);
        assertEquals(36, loaded.getMaxHealth(), .0001);
        assertEquals(6F, OpeningRaidBalance.outgoingDamage(loaded, 10F), .0001);
        verify(loaded, never()).setHealth(anyFloat());
    }

    @Test void serverDamageHookReducesMeleeAndOwnerAttributedArrowsOnce() {
        Mob attacker = raider();
        OpeningRaidBalance.apply(attacker, 1, true);
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.level()).thenReturn(mock(ServerLevel.class));
        DamageSource melee = mock(DamageSource.class);
        when(melee.getEntity()).thenReturn(attacker);
        when(melee.getDirectEntity()).thenReturn(attacker);
        DamageSource arrow = mock(DamageSource.class);
        when(arrow.getEntity()).thenReturn(attacker);
        when(arrow.getDirectEntity()).thenReturn(mock(AbstractArrow.class));
        for (DamageSource source : new DamageSource[]{melee, arrow}) {
            LivingHurtEvent event = new LivingHurtEvent(victim, source, 10F);
            RaidEvents.onOpeningRaiderDamage(event);
            assertEquals(6F, event.getAmount(), .0001);
        }
        verify(attacker, never()).getAttribute(Attributes.ATTACK_DAMAGE);
    }

    @Test void unmarkedFriendlyLateWaveAndDisabledUnitsKeepTheirStrength() {
        for (int wave : new int[]{0, 5, 6, 11}) {
            Mob mob = raider();
            OpeningRaidBalance.apply(mob, wave, true);
            assertEquals(40, mob.getMaxHealth());
            assertEquals(10F, OpeningRaidBalance.outgoingDamage(mob, 10F));
        }
        Mob disabled = raider();
        OpeningRaidBalance.apply(disabled, 1, false);
        assertEquals(40, disabled.getMaxHealth());
        assertEquals(10F, OpeningRaidBalance.outgoingDamage(disabled, 10F));
        Mob friendly = raider();
        OpeningRaidBalance.apply(friendly, 1, true);
        friendly.getPersistentData().remove(ModConstants.Tags.RAID_TEAM);
        assertEquals(10F, OpeningRaidBalance.outgoingDamage(friendly, 10F));
        assertEquals(10F, OpeningRaidBalance.outgoingDamage(null, 10F));
    }

    @Test void heroesWaitUntilWaveFourOnlyWhenGentleOpeningIsEnabled() {
        for (int wave = 1; wave <= 6; wave++) {
            assertEquals(wave >= 4, OpeningRaidBalance.heroesAllowed(wave, true));
            assertTrue(OpeningRaidBalance.heroesAllowed(wave, false));
        }
    }
}
