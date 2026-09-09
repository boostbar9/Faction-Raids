package com.devfarinsky.siegeoverhaul.siege;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SiegeFleetTest extends MinecraftTestSupport {
    @Test void reuseNeedsProvenDeadPreviousCrewAndNeverTouchesPlayerCapturedEquipment() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.wave=20;
        var engine=mock(Entity.class);var tag=new CompoundTag();when(engine.getPersistentData()).thenReturn(tag);
        when(engine.isAlive()).thenReturn(true);when(engine.getPassengers()).thenReturn(List.of());
        tag.putString(SiegeDeployment.TEAM_TAG,raid.teamKey);tag.putInt("SiegeSupportWave",19);
        UUID crew=UUID.randomUUID();tag.putUUID("SiegeOperatorUuid",crew);
        assertFalse(SiegeFleet.reusable(engine,raid)); // Missing entity alone cannot prove death.
        tag.putInt(SiegeFleet.DEFEATED,19);assertTrue(SiegeFleet.reusable(engine,raid));
        raid.raiders.add(crew);assertFalse(SiegeFleet.reusable(engine,raid));raid.raiders.clear();
        tag.putBoolean(SiegeFleet.CAPTURED,true);assertFalse(SiegeFleet.reusable(engine,raid));tag.remove(SiegeFleet.CAPTURED);
        when(engine.getPassengers()).thenReturn(List.of(mock(Entity.class)));assertFalse(SiegeFleet.reusable(engine,raid));
    }
    @Test void reusedVehicleIsAssignedOnlyOncePerNewWaveAndKeepsIdentityAndInventoryTags() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.wave=11;
        var engine=mock(Entity.class);var level=mock(ServerLevel.class);var tag=new CompoundTag();
        when(engine.getPersistentData()).thenReturn(tag);when(engine.isAlive()).thenReturn(true);when(engine.getPassengers()).thenReturn(List.of());
        tag.putString(SiegeDeployment.TEAM_TAG,raid.teamKey);tag.putInt("SiegeSupportWave",10);tag.putInt(SiegeFleet.DEFEATED,10);
        tag.putUUID("SiegeOperatorUuid",UUID.randomUUID());tag.putBoolean(SiegeDeployment.OPERATOR_ASSIGNED,true);tag.putString("InventorySentinel","preserved");
        try(var saves=mockStatic(RaidSavedData.class)) {
            saves.when(()->RaidSavedData.get(null)).thenReturn(new RaidSavedData());
            assertTrue(SiegeFleet.reuse(level,raid,engine));assertFalse(SiegeFleet.reuse(level,raid,engine));
            assertEquals(11,tag.getInt("SiegeSupportWave"));assertFalse(tag.getBoolean(SiegeDeployment.OPERATOR_ASSIGNED));
            assertTrue(tag.getBoolean("SiegeReplacementCrew"));assertEquals("preserved",tag.getString("InventorySentinel"));
            verify(engine,never()).discard();verify(engine,never()).moveTo(anyDouble(),anyDouble(),anyDouble());
        }
    }
}
