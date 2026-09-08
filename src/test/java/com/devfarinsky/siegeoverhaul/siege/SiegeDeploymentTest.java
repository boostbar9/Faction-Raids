package com.devfarinsky.siegeoverhaul.siege;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiegeDeploymentTest extends MinecraftTestSupport {
    @Test
    void unloadedEngineKeepsItsCleanupIdentity() {
        var raid = new RaidSavedData.RaidState("team:test", "home", 0);
        UUID id = UUID.randomUUID();
        raid.siegeEngines.put(id, "BALLISTA");
        assertEquals(0, SiegeDeployment.tick(mock(ServerLevel.class), raid, BlockPos.ZERO));
        assertTrue(raid.siegeEngines.containsKey(id));
    }
    @Test
    void operatorsWaitForWaveAndAreProvisionedOnlyOnce() {
        ServerLevel level = mock(ServerLevel.class);
        Entity engine = mock(Entity.class);
        Mob operator = mock(Mob.class);
        UUID engineId = UUID.randomUUID(), operatorId = UUID.randomUUID();
        var raid = new RaidSavedData.RaidState("team:test", "home", 0);
        raid.siegeEngines.put(engineId, "BALLISTA");
        when(level.getEntity(engineId)).thenReturn(engine);
        when(level.getGameTime()).thenReturn(113L);
        when(engine.isAlive()).thenReturn(true);
        when(engine.getPersistentData()).thenReturn(new CompoundTag());
        when(engine.getPassengers()).thenReturn(java.util.List.of());
        when(engine.position()).thenReturn(Vec3.ZERO);
        when(operator.getUUID()).thenReturn(operatorId);
        try (var integration = mockStatic(SiegeIntegration.class); var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(null)).thenReturn(new RaidSavedData());
            integration.when(() -> SiegeIntegration.spawnSiegeEngineer(level, Vec3.ZERO, raid.teamKey, engine, SiegeEngineType.BALLISTA)).thenReturn(Optional.of(operator));
            SiegeDeployment.tick(level, raid, BlockPos.ZERO);
            integration.verifyNoInteractions();
            raid.wave = 1;
            SiegeDeployment.tick(level, raid, BlockPos.ZERO);
            SiegeDeployment.tick(level, raid, BlockPos.ZERO);
            integration.verify(() -> SiegeIntegration.spawnSiegeEngineer(level, Vec3.ZERO, raid.teamKey, engine, SiegeEngineType.BALLISTA), times(1));
            assertEquals(1, raid.totalSpawned);
            assertTrue(raid.raiders.contains(operatorId));
            assertTrue(engine.getPersistentData().getBoolean(SiegeDeployment.OPERATOR_ASSIGNED));
        }
    }
    @Test
    void everyWaveRequestsSupportAndCompletedSupportSurvivesReload() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.campPos = BlockPos.ZERO;
        for (int wave = 1; wave <= 5; wave++) {
            raid.wave = wave;
            assertTrue(SiegeDeployment.needsWaveSupport(raid));
            raid.lastSiegeSupportWave = wave;
            raid = RaidSavedData.RaidState.load(raid.save());
            assertFalse(SiegeDeployment.needsWaveSupport(raid));
        }
    }
    @Test
    void supportRetriesExistingUncrewedEngineRatherThanSpawningDuplicates() {
        var level = mock(ServerLevel.class);
        var engine = mock(Entity.class);
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.campPos = BlockPos.ZERO; raid.wave = 2;
        UUID id = UUID.randomUUID(); raid.siegeEngines.put(id, "BALLISTA");
        var tag = new CompoundTag(); tag.putInt("SiegeSupportWave",2);
        when(engine.getPersistentData()).thenReturn(tag);
        when(engine.getPassengers()).thenReturn(java.util.List.of());
        when(engine.isAlive()).thenReturn(true);
        when(engine.position()).thenReturn(Vec3.ZERO);
        when(level.getEntity(id)).thenReturn(engine);
        when(level.getGameTime()).thenReturn(100L,200L,300L);
        try (var construction = mockStatic(SiegeConstruction.class); var integration = mockStatic(SiegeIntegration.class);
                var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(null)).thenReturn(new RaidSavedData());
            integration.when(() -> SiegeIntegration.spawnSiegeEngineer(level, Vec3.ZERO, raid.teamKey, engine, SiegeEngineType.BALLISTA)).thenReturn(Optional.empty());
            SiegeDeployment.tick(level, raid, BlockPos.ZERO);
            SiegeDeployment.tick(level, raid, BlockPos.ZERO);
            construction.verifyNoInteractions();
            assertEquals(1, raid.siegeEngines.size());
            assertEquals(0, raid.lastSiegeSupportWave);
        }
    }
    @Test
    void operatorProvisioningRespectsGlobalPopulationCap() {
        var level = mock(ServerLevel.class);
        var engine = mock(Entity.class);
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.wave = 1;
        UUID id = UUID.randomUUID(); raid.siegeEngines.put(id,"BALLISTA");
        when(level.getEntity(id)).thenReturn(engine);
        when(engine.isAlive()).thenReturn(true);
        when(engine.getPersistentData()).thenReturn(new CompoundTag());
        when(engine.getPassengers()).thenReturn(java.util.List.of());
        var saved = new RaidSavedData();
        var other = new RaidSavedData.RaidState("team:other", "siege_core",0);
        for (int i=0;i<com.devfarinsky.siegeoverhaul.RaidConfig.MAX_GLOBAL_RAIDERS.get();i++) other.raiders.add(UUID.randomUUID());
        saved.raids.put(other.teamKey,other);
        try (var integration = mockStatic(SiegeIntegration.class); var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(null)).thenReturn(saved);
            SiegeDeployment.tick(level,raid,BlockPos.ZERO);
            integration.verifyNoInteractions();
            assertEquals(0,raid.totalSpawned);
        }
    }
    @Test
    void legacyUnmannedChoicesUseAnEngineWithANativeController() {
        assertEquals(SiegeEngineType.BALLISTA, SiegeConstruction.automaticType(SiegeEngineType.BATTERING_RAM));
        assertEquals(SiegeEngineType.BALLISTA, SiegeConstruction.automaticType(SiegeEngineType.SIEGE_TOWER));
        assertEquals(SiegeEngineType.CATAPULT, SiegeConstruction.automaticType(SiegeEngineType.CATAPULT));
    }
}
