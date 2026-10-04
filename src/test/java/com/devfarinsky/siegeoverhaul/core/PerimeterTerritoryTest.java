package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerimeterTerritoryTest extends MinecraftTestSupport {
    @Test void territorySnapshotIsStableAcrossOrderRecordSplitsAndSaveReload() {
        var a = new ChunkPos(-3, 2); var b = new ChunkPos(7, -8);
        var tag = PerimeterTerritory.encode("blue", Set.of(a, b));
        assertEquals(tag, PerimeterTerritory.encode("blue", Set.of(b, a)));
        assertEquals(Set.of(a, b), PerimeterTerritory.decode(tag.copy(), "blue"));
        assertNull(PerimeterTerritory.decode(tag, "red"));
        tag.putLongArray("Chunks", new long[]{a.toLong(), a.toLong()});
        assertNull(PerimeterTerritory.decode(tag, "blue"));
        assertNull(PerimeterTerritory.decode(new CompoundTag(), "blue"));
    }

    @Test void expansionShrinkAndUnavailableRegistryPauseTheWholeSavedJobWithoutReplacingItsScope() {
        var area = mock(Entity.class); var level = mock(ServerLevel.class); var data = new CompoundTag();
        when(area.getPersistentData()).thenReturn(data);
        var a = new ChunkPos(0, 0); var b = new ChunkPos(1, 0); var c = new ChunkPos(0, 1);
        var accepted = new RecruitsClaimsBridge.TerritorySnapshot("blue", Set.of(a, b), null);
        PerimeterTerritory.remember(area, accepted);
        CompoundTag persisted = data.copy();
        try (var claims = mockStatic(RecruitsClaimsBridge.class)) {
            for (var current : new RecruitsClaimsBridge.TerritorySnapshot[]{
                    new RecruitsClaimsBridge.TerritorySnapshot("blue", Set.of(a, b, c), null),
                    new RecruitsClaimsBridge.TerritorySnapshot("blue", Set.of(a), null),
                    new RecruitsClaimsBridge.TerritorySnapshot("", Set.of(), "unavailable")}) {
                claims.when(() -> RecruitsClaimsBridge.getFactionTerritory(level, "blue", 4096)).thenReturn(current);
                assertNotNull(PerimeterTerritory.problem(level, area, "blue")); assertEquals(persisted, data);
            }
            claims.when(() -> RecruitsClaimsBridge.getFactionTerritory(level, "blue", 4096)).thenReturn(accepted);
            assertNull(PerimeterTerritory.problem(level, area, "blue"));
            when(area.getPersistentData()).thenReturn(persisted.copy());
            assertNull(PerimeterTerritory.problem(level, area, "blue"));
            assertThrows(IllegalArgumentException.class, () -> PerimeterTerritory.remember(area, accepted));
        }
    }

    @Test void missingRequiredOrMalformedSavedTerritoryFailsClosed() {
        var area = mock(Entity.class); var data = new CompoundTag(); when(area.getPersistentData()).thenReturn(data);
        PerimeterTerritory.remember(area, new RecruitsClaimsBridge.TerritorySnapshot("blue", Set.of(new ChunkPos(0, 0)), null));
        data.remove("SiegePerimeterTerritory");
        assertTrue(PerimeterTerritory.tracked(area));
        assertNotNull(PerimeterTerritory.problem(null, area, "blue"));
        data.putString("SiegePerimeterTerritory", "invalid");
        assertNotNull(PerimeterTerritory.problem(null, area, "blue"));
    }

    @Test void legacyJobWithoutTerritoryScopeIsNotRewritten() {
        var area = mock(Entity.class); var data = new CompoundTag(); when(area.getPersistentData()).thenReturn(data);
        assertFalse(PerimeterTerritory.tracked(area));
        assertNull(PerimeterTerritory.problem(null, area, "blue")); assertTrue(data.isEmpty());
    }
}
