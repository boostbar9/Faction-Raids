package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommanderTraitsTest extends MinecraftTestSupport {
    @Test void everyStrategosPowerAppliesItsOwnBoundedFiveSecondEffect() {
        var signatures=new HashSet<String>();
        for(var host:OlympianHostIdentity.hosts()) {
            Mob commander=mock(Mob.class);
            Mob ally=mock(Mob.class);
            CommanderTraits.applyPower(commander,java.util.List.of(ally),host.commanderPower(),"team:defender");
            var effects=ArgumentCaptor.forClass(MobEffectInstance.class);
            verify(ally,atLeastOnce()).addEffect(effects.capture());
            assertTrue(effects.getAllValues().stream().allMatch(effect->effect.getDuration()<=120));
            String signature=effects.getAllValues().stream().map(effect->effect.getEffect().getDescriptionId())
                    .sorted().collect(java.util.stream.Collectors.joining(","));
            assertTrue(signatures.add(signature),"duplicate commander effect plan for "+host.hostName());
        }
        assertEquals(5,signatures.size());
    }

    @Test void huntersMarkOnlyExposesAHostileTarget() {
        Mob commander=mock(Mob.class);Mob ally=mock(Mob.class);Mob target=mock(Mob.class);
        var targetTag=new net.minecraft.nbt.CompoundTag();
        when(commander.getTarget()).thenReturn(target);
        when(target.isAlive()).thenReturn(true);
        when(target.getPersistentData()).thenReturn(targetTag);

        CommanderTraits.applyPower(commander,java.util.List.of(ally),
                OlympianHostIdentity.CommanderPower.HUNTERS_MARK,"team:defender");

        verify(target).addEffect(argThat(effect->effect.getEffect()==MobEffects.GLOWING && effect.getDuration()==100));
        targetTag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM,"team:defender");
        clearInvocations(target,ally,commander);
        CommanderTraits.applyPower(commander,java.util.List.of(ally),
                OlympianHostIdentity.CommanderPower.HUNTERS_MARK,"team:defender");
        verify(target,never()).addEffect(any());
    }
}
