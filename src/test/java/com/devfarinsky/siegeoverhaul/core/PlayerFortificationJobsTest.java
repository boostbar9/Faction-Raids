package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
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

    @Test void legacyMigrationRequiresPositivePlayerBuildAreaEvidence() {
        UUID player=UUID.randomUUID();
        assertTrue(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",player,true));

        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                false,"team:claimed-core",player,true));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"",player,true));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",null,true));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",player,false));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",RecruitsBridge.RAIDERS_LEADER_UUID,true));
    }

    @Test void lateBuilderReconnectRequiresOwnerAndReservationMatch() {
        UUID owner=UUID.randomUUID(),builder=UUID.randomUUID(),other=UUID.randomUUID();
        assertTrue(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,null,builder,true,false));
        assertTrue(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,builder,builder,true,false));

        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,other,null,builder,true,false));
        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,other,builder,true,false));
        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,null,builder,false,false));
        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,null,builder,true,true));
    }
}
