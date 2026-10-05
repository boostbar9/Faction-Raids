package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Absent/uninitialized API fallback only; mandatory mod metadata still governs real startup. */
class SmallShipsIntegrationTest extends MinecraftTestSupport {
    @Test void uninitializedModListDoesNotResolveOrSpawnACompanionEntity() {
        try (var mods = mockStatic(ModList.class)) {
            mods.when(ModList::get).thenReturn(null);
            ServerLevel level = mock(ServerLevel.class);
            assertFalse(SmallShipsIntegration.isPresent());
            assertFalse(SmallShipsIntegration.hasAnyKnownShip());
            assertTrue(SmallShipsIntegration.spawnShip(level, BlockPos.ZERO, true).isEmpty());
            verifyNoInteractions(level);
        }
    }
    @Test void missingSmallShipsKeepsHelperFallbackAvailable() {
        try (var mods = mockStatic(ModList.class)) {
            ModList list = mock(ModList.class); mods.when(ModList::get).thenReturn(list);
            when(list.isLoaded("smallships")).thenReturn(false);
            ServerLevel level = mock(ServerLevel.class);
            assertFalse(SmallShipsIntegration.isPresent());
            assertFalse(SmallShipsIntegration.hasAnyKnownShip());
            assertTrue(SmallShipsIntegration.spawnShip(level, BlockPos.ZERO, false).isEmpty());
            verifyNoInteractions(level);
        }
    }
}
