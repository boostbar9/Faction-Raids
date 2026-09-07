package com.devfarinsky.siegeoverhaul.effort;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StragglerTrackerTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final Mob mob = mock(Mob.class);
    private final RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);
    private final UUID id = UUID.randomUUID();

    private void stationaryRaider(double distanceSquared) {
        raid.raiders.add(id);
        raid.missingTicks.put(id, 0);
        raid.lastKnownChunks.put(id, 0L);
        when(level.getEntity(id)).thenReturn(mob);
        when(level.getGameTime()).thenReturn(0L, 300L, 600L, 900L, 1200L);
        when(mob.isAlive()).thenReturn(true);
        when(mob.position()).thenReturn(new Vec3(100, 0, 0));
        when(mob.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(mob.getNavigation()).thenReturn(mock(net.minecraft.world.entity.ai.navigation.PathNavigation.class));
        when(mob.distanceToSqr(any(Vec3.class))).thenReturn(distanceSquared);
    }

    @AfterEach
    void forgetRaid() {
        StragglerTracker.forget(raid.teamKey);
    }

    @Test
    void failedRescueDiscardsMobSoReconciliationCannotReAddIt() {
        stationaryRaider(10000.0);
        assertEquals(0, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        assertEquals(0, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        assertEquals(0, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        assertEquals(0, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        assertEquals(1, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        verify(mob).discard();
        verify(mob, never()).teleportTo(anyDouble(), anyDouble(), anyDouble());
        assertFalse(raid.raiders.contains(id));
        assertFalse(raid.missingTicks.containsKey(id));
        assertFalse(raid.lastKnownChunks.containsKey(id));
        assertEquals(1, raid.totalEscaped);
    }

    @Test
    void lateralMovementAroundWallsIsProgress() {
        stationaryRaider(10000.0);
        when(mob.position()).thenReturn(new Vec3(100, 0, 0), new Vec3(100, 0, 5),
                new Vec3(100, 0, 10), new Vec3(100, 0, 15), new Vec3(100, 0, 20));
        for (int i = 0; i < 5; i++) assertEquals(0, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        verify(mob, never()).discard();
        verify(mob, never()).teleportTo(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void raiderHoldingObjectiveIsNeverRescuedOrDiscarded() {
        stationaryRaider(1.0);
        for (int i = 0; i < 3; i++) assertEquals(0, StragglerTracker.tick(level, raid, BlockPos.ZERO));
        verify(mob, never()).discard();
        verify(mob, never()).teleportTo(anyDouble(), anyDouble(), anyDouble());
        assertTrue(raid.raiders.contains(id));
        assertEquals(0, raid.totalEscaped);
    }
}
