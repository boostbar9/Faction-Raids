package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnemyAbilitiesTest extends MinecraftTestSupport {

    private Mob newRaider(String role, double baseHp, double baseAtk, float currentHp) {
        Mob mob = mock(Mob.class);
        CompoundTag tag = new CompoundTag();
        if (role != null) tag.putString(ModConstants.Tags.RAID_ROLE, role);
        when(mob.getPersistentData()).thenReturn(tag);
        when(mob.isAlive()).thenReturn(true);
        AttributeInstance hp = mock(AttributeInstance.class);
        AttributeInstance atk = mock(AttributeInstance.class);
        when(mob.getAttribute(Attributes.MAX_HEALTH)).thenReturn(hp);
        when(mob.getAttribute(Attributes.ATTACK_DAMAGE)).thenReturn(atk);
        when(hp.getBaseValue()).thenReturn(baseHp);
        when(atk.getBaseValue()).thenReturn(baseAtk);
        when(mob.getMaxHealth()).thenReturn((float) baseHp);
        when(mob.getHealth()).thenReturn(currentHp);
        return mob;
    }

    private List<MobEffect> capturedEffects(Mob mob) {
        var captor = org.mockito.ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(mob, atLeastOnce()).addEffect(captor.capture());
        List<MobEffect> out = new ArrayList<>();
        for (MobEffectInstance eff : captor.getAllValues()) out.add(eff.getEffect());
        return out;
    }

    @Test void breacherGetsExtraHealthAndFireResistance() {
        Mob mob = newRaider("breacher", 20D, 3D, 20F);
        EnemyAbilities.apply(mob, "breacher");
        verify(mob.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(24D);
        assertTrue(capturedEffects(mob).contains(MobEffects.FIRE_RESISTANCE));
    }

    @Test void warcasterGetsExtraHealthAndFireResistance() {
        Mob mob = newRaider("warcaster", 20D, 4D, 20F);
        EnemyAbilities.apply(mob, "warcaster");
        verify(mob.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(28D);
        assertTrue(capturedEffects(mob).contains(MobEffects.FIRE_RESISTANCE));
    }

    @Test void flankerGetsSpeedJumpAndAttackBonus() {
        Mob mob = newRaider("flanker", 20D, 4D, 20F);
        EnemyAbilities.apply(mob, "flanker");
        verify(mob.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(5D);
        var effects = capturedEffects(mob);
        assertTrue(effects.contains(MobEffects.MOVEMENT_SPEED));
        assertTrue(effects.contains(MobEffects.JUMP));
    }

    @Test void scoutGetsNightVisionAndSpeed() {
        Mob mob = newRaider("scout", 20D, 4D, 20F);
        EnemyAbilities.apply(mob, "scout");
        var effects = capturedEffects(mob);
        assertTrue(effects.contains(MobEffects.MOVEMENT_SPEED));
        assertTrue(effects.contains(MobEffects.NIGHT_VISION));
    }

    @Test void marksmanGetsAttackAndSpeed() {
        Mob mob = newRaider("marksman", 20D, 4D, 20F);
        EnemyAbilities.apply(mob, "marksman");
        verify(mob.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(5D);
        assertTrue(capturedEffects(mob).contains(MobEffects.MOVEMENT_SPEED));
    }

    @Test void applyIsIdempotentPerRaider() {
        Mob mob = newRaider("breacher", 20D, 3D, 20F);
        EnemyAbilities.apply(mob, "breacher");
        EnemyAbilities.apply(mob, "breacher");
        // Base health was mutated exactly once, not twice.
        verify(mob.getAttribute(Attributes.MAX_HEALTH), times(1)).setBaseValue(any(Double.class));
    }

    @Test void unknownRolesDoNothing() {
        Mob mob = newRaider("captain", 20D, 4D, 20F);
        EnemyAbilities.apply(mob, "captain");
        // Captain is intentionally owned by RaidEvents pre-4.35 code; the
        // ability layer must not double-buff it.
        verify(mob.getAttribute(Attributes.MAX_HEALTH), never()).setBaseValue(any(Double.class));
        verify(mob.getAttribute(Attributes.ATTACK_DAMAGE), never()).setBaseValue(any(Double.class));
        verify(mob, never()).addEffect(any(MobEffectInstance.class));
    }

    @Test void flankerCloakFiresOnceAtLowHp() {
        Mob mob = newRaider("flanker", 20D, 4D, 5F); // 25% HP, below 50%
        EnemyAbilities.onRaiderHurt(mob);
        assertTrue(capturedEffects(mob).contains(MobEffects.INVISIBILITY));
        // Second hit at same HP must not re-cloak.
        reset(mob);
        CompoundTag tag = new CompoundTag();
        tag.putString(ModConstants.Tags.RAID_ROLE, "flanker");
        tag.putBoolean("SiegeOverhaulFlankerCloakUsed", true);
        when(mob.getPersistentData()).thenReturn(tag);
        when(mob.isAlive()).thenReturn(true);
        when(mob.getHealth()).thenReturn(4F);
        when(mob.getMaxHealth()).thenReturn(20F);
        EnemyAbilities.onRaiderHurt(mob);
        verify(mob, never()).addEffect(any(MobEffectInstance.class));
    }

    @Test void flankerCloakDoesNotFireAtHighHp() {
        Mob mob = newRaider("flanker", 20D, 4D, 18F); // 90% HP
        EnemyAbilities.onRaiderHurt(mob);
        verify(mob, never()).addEffect(any(MobEffectInstance.class));
    }
}
