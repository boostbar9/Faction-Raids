package com.devfarinsky.siegeoverhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaidMobSpawnerTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final DifficultyInstance difficulty = new DifficultyInstance(Difficulty.NORMAL, 0L, 0L, 0F);

    private Mob mob() {
        Mob mob = mock(Mob.class);
        doReturn(EntityType.PILLAGER).when(mob).getType();
        when(mob.blockPosition()).thenReturn(BlockPos.ZERO);
        when(level.getCurrentDifficultyAt(BlockPos.ZERO)).thenReturn(difficulty);
        return mob;
    }

    @Test
    void navigationCastFailureDiscardsEngineerAndInitializesFreshReplacement() {
        Mob engineer = mob();
        Mob fallback = mob();
        when(engineer.getX()).thenReturn(12.5);
        when(engineer.getY()).thenReturn(64.0);
        when(engineer.getZ()).thenReturn(-30.5);
        when(engineer.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null))
                .thenThrow(new ClassCastException("RecruitPathNavigation cannot be cast to GroundPathNavigation"));

        assertSame(fallback, RaidMobSpawner.initializeOrFallback(level, engineer, () -> fallback));
        verify(engineer).discard();
        verify(engineer, times(1)).finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null);
        verify(fallback).moveTo(12.5, 64.0, -30.5, 0F, 0F);
        verify(fallback).finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null);
        verify(fallback, never()).discard();
        verify(level, never()).addFreshEntity(any());
    }

    @Test
    void successfulNativeInitializerKeepsOriginalMob() {
        Mob original = mob();
        assertSame(original, RaidMobSpawner.initializeOrFallback(level, original,
                () -> { throw new AssertionError("Successful spawn must not create a replacement"); }));
        verify(original, never()).discard();
    }

    @Test
    void failedFallbackCannotEnterWaveLedger() {
        Mob original = mob();
        Mob fallback = mob();
        when(original.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null))
                .thenThrow(new ClassCastException());
        when(fallback.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null))
                .thenThrow(new IllegalStateException("Incompatible spawn hook"));
        assertNull(RaidMobSpawner.initializeOrFallback(level, original, () -> fallback));
        verify(original).discard();
        verify(fallback).discard();
    }
}
