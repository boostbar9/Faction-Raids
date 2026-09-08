package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CoreClaimsTest extends MinecraftTestSupport {
    @Test void onlyTheOwningFactionCanUseItsNativeClaim() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        var claim = new RecruitsClaimsBridge.ClaimSnapshot(UUID.randomUUID(), "Home", "blue", new ChunkPos(0,0), Set.of(), false, 100, 100);
        try (var claims = mockStatic(RecruitsClaimsBridge.class)) {
            claims.when(() -> RecruitsClaimsBridge.getClaimAt(level, BlockPos.ZERO)).thenReturn(Optional.of(claim));
            assertTrue(SiegeCore.claimed(level, BlockPos.ZERO, "team:blue"));
            assertFalse(SiegeCore.claimed(level, BlockPos.ZERO, "team:red"));
            assertFalse(SiegeCore.claimed(level, BlockPos.ZERO, "player:test"));
        }
    }
    @Test void identicalChunkCoordinatesInAnotherDimensionDoNotGrantOwnership() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.NETHER);
        try (var claims = mockStatic(RecruitsClaimsBridge.class)) {
            assertFalse(SiegeCore.claimed(level, BlockPos.ZERO, "team:blue"));
            claims.verifyNoInteractions();
        }
    }
    @Test void unavailableOrUnclaimedLandFailsClosed() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        try (var claims = mockStatic(RecruitsClaimsBridge.class)) {
            claims.when(() -> RecruitsClaimsBridge.getClaimAt(level, BlockPos.ZERO)).thenReturn(Optional.empty());
            assertFalse(SiegeCore.claimed(level, BlockPos.ZERO, "team:blue"));
        }
    }
}
