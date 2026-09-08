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
    void legacyUnmannedChoicesUseAnEngineWithANativeController() {
        assertEquals(SiegeEngineType.BALLISTA, SiegeConstruction.automaticType(SiegeEngineType.BATTERING_RAM));
        assertEquals(SiegeEngineType.BALLISTA, SiegeConstruction.automaticType(SiegeEngineType.SIEGE_TOWER));
        assertEquals(SiegeEngineType.CATAPULT, SiegeConstruction.automaticType(SiegeEngineType.CATAPULT));
    }
}
