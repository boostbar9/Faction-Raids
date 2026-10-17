package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaidCavalryTest extends MinecraftTestSupport {
    @Test
    void mountThatStopsGainingGroundIsDismountedAfterTheStallWindow() {
        Mob rider = mock(Mob.class);
        AbstractHorse horse = mock(AbstractHorse.class);
        Level level = mock(Level.class);
        when(rider.level()).thenReturn(level);
        when(horse.getPersistentData()).thenReturn(new CompoundTag());
        when(horse.distanceToSqr(any(Vec3.class))).thenReturn(10000.0);
        Vec3 objective = new Vec3(0, 64, 0);
        when(level.getGameTime()).thenReturn(100L);
        assertFalse(RaidCavalry.stalled(rider, horse, objective));
        when(level.getGameTime()).thenReturn(100L + RaidCavalry.STALL_TICKS - 20);
        assertFalse(RaidCavalry.stalled(rider, horse, objective));
        when(level.getGameTime()).thenReturn(100L + RaidCavalry.STALL_TICKS);
        assertTrue(RaidCavalry.stalled(rider, horse, objective), "a wedged horse must release its rider");
    }

    @Test
    void progressingOrFightingMountIsNotDismounted() {
        Mob rider = mock(Mob.class);
        AbstractHorse horse = mock(AbstractHorse.class);
        Level level = mock(Level.class);
        when(rider.level()).thenReturn(level);
        when(horse.getPersistentData()).thenReturn(new CompoundTag());
        Vec3 objective = new Vec3(0, 64, 0);
        long tick = 0;
        double dist = 10000;
        for (int i = 0; i < 30; i++, tick += 20, dist *= 0.9) {
            when(level.getGameTime()).thenReturn(tick);
            when(horse.distanceToSqr(objective)).thenReturn(dist);
            assertFalse(RaidCavalry.stalled(rider, horse, objective));
        }
        Mob enemy = mock(Mob.class);
        when(enemy.isAlive()).thenReturn(true);
        when(rider.getTarget()).thenReturn(enemy);
        for (int i = 0; i < 30; i++, tick += 20) {
            when(level.getGameTime()).thenReturn(tick);
            assertFalse(RaidCavalry.stalled(rider, horse, objective));
        }
    }
}
