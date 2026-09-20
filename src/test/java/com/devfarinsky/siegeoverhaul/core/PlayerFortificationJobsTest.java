package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerFortificationJobsTest extends MinecraftTestSupport {
    @Test void commissionedWallUsesDurablePlayerLinkInsteadOfEnemyCampTag() {
        Mob builder=mock(Mob.class); Entity area=mock(Entity.class);
        CompoundTag workerTag=new CompoundTag(),areaTag=new CompoundTag();
        UUID builderId=UUID.randomUUID(),areaId=UUID.randomUUID(),owner=UUID.randomUUID();
        when(builder.getPersistentData()).thenReturn(workerTag);
        when(area.getPersistentData()).thenReturn(areaTag);
        when(builder.getUUID()).thenReturn(builderId); when(area.getUUID()).thenReturn(areaId);
        when(area.blockPosition()).thenReturn(new BlockPos(80,70,-16));
        areaTag.putString(ModConstants.Tags.CAMP_AREA_TEAM,"team:legacy");

        PlayerFortificationJobs.link(builder,area,owner);

        assertFalse(areaTag.contains(ModConstants.Tags.CAMP_AREA_TEAM));
        assertTrue(areaTag.getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA));
        assertEquals(builderId,areaTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER));
        assertEquals(areaId,workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        assertEquals(owner,workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER));
        assertEquals(area.blockPosition(),BlockPos.of(workerTag.getLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS)));
    }

    @Test void failedCommissionOnlyClearsItsOwnSavedAssociation() {
        Mob builder=mock(Mob.class); CompoundTag tag=new CompoundTag();
        when(builder.getPersistentData()).thenReturn(tag);
        UUID active=UUID.randomUUID(); tag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID,active);
        PlayerFortificationJobs.unlink(builder,UUID.randomUUID());
        assertEquals(active,tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        PlayerFortificationJobs.unlink(builder,active);
        assertFalse(tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
    }
}
