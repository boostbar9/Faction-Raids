package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuilderFeedbackTest extends MinecraftTestSupport {
    @Test void mixedBusyAndFleeingBuildersDoNotClaimEveryBuilderIsWorking() {
        var level = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        var busy = mock(Mob.class);
        var fleeing = mock(Mob.class);
        UUID owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        when(busy.getPersistentData()).thenReturn(new CompoundTag());
        when(fleeing.getPersistentData()).thenReturn(new CompoundTag());
        when(level.getEntitiesOfClass(eq(Mob.class), any(), any())).thenReturn(List.of(busy, fleeing));
        try (var bridge = mockStatic(WorkersBridge.class)) {
            for (var builder : List.of(busy, fleeing)) {
                bridge.when(() -> WorkersBridge.isBuilder(builder)).thenReturn(true);
                bridge.when(() -> WorkersBridge.readWorkerOwner(builder)).thenReturn(owner);
            }
            bridge.when(() -> WorkersBridge.hasActiveBuildArea(busy)).thenReturn(true);
            bridge.when(() -> WorkersBridge.isFleeing(fleeing)).thenReturn(true);
            var result = TerritoryFortification.findNearbyBuilder(level, player, new BlockPos(240, 64, 240), true);
            assertNull(result.builder());
            assertEquals("No idle builder is available nearby. Wait for the current job to finish or bring another builder.",
                    result.reason());
            assertFalse(result.reason().contains("Every"));
            assertFalse(result.reason().contains("core"));
        }
    }

    @Test void missingBuilderFeedbackUsesWorkersAndTheSelectedSite() {
        var level = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        when(level.getEntitiesOfClass(eq(Mob.class), any(), any())).thenReturn(List.of());
        var result = TerritoryFortification.findNearbyBuilder(level, player, new BlockPos(240, 64, 240), true);
        assertNull(result.builder());
        assertEquals("No available Workers 2 builder within 16 blocks of this site. Bring a builder closer.",
                result.reason());
    }
}
