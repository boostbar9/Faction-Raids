package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.naval.BridgeBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RaiderLabelsTest extends MinecraftTestSupport {
    @Test
    void bridgeSpecialistKeepsItsLabelWithoutChangingCombatRole() {
        Mob mob = mock(Mob.class);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BridgeBuilder.SPECIALIST_TAG, true);
        tag.putString(ModConstants.Tags.RAID_ROLE, "breacher");
        when(mob.getPersistentData()).thenReturn(tag);
        RaidConfig.RAIDER_GLOW.set(false);
        RaidConfig.RAIDER_LABEL_MODE.set(RaidConfig.LabelMode.ALWAYS);

        RaiderLabels.applyRole(mob, "breacher");
        verify(mob).setCustomName(argThat((Component name) -> name.getString().equals("Enemy Bridge Builder")));
        assertEquals("breacher", tag.getString(ModConstants.Tags.RAID_ROLE));
        clearInvocations(mob);
        RaiderLabels.tick(mob);
        verify(mob).setCustomNameVisible(true);

        when(mob.isCustomNameVisible()).thenReturn(true);
        RaidConfig.RAIDER_LABEL_MODE.set(RaidConfig.LabelMode.OFF);
        RaiderLabels.tick(mob);
        verify(mob).setCustomNameVisible(false);
    }
}
