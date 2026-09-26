package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent API fixtures reflecting Recruits 1.15.2's separate manager types. */
class RaiderDiplomacyApiTest extends MinecraftTestSupport {
    public enum Status { NEUTRAL, ALLY, ENEMY }
    public static class FactionManager {}
    public static class DiplomacyManager {
        final Map<String, Map<String, Status>> relations = new HashMap<>();
        int writes;
        public Status getRelation(String a, String b) {
            return relations.getOrDefault(a, Map.of()).getOrDefault(b, Status.NEUTRAL);
        }
        public void setRelation(String a, String b, Status status, ServerLevel level) {
            relations.computeIfAbsent(a, key -> new HashMap<>()).put(b, status);
            writes++;
        }
    }
    public static class Events {
        public static FactionManager recruitsFactionManager = new FactionManager();
        public static DiplomacyManager recruitsDiplomacyManager;
    }
    public static class WrongEvents {
        public static FactionManager recruitsDiplomacyManager;
    }

    /** Install fixtures through the real resolver and restore the cache after each test. */
    private static AutoCloseable bind() throws Exception {
        Map<Field, Object> previous = new LinkedHashMap<>();
        Map<String, Object> values = Map.of(
                "initialized", true, "available", true,
                "statusClass", Status.class,
                "diplomacyManagerField", RaiderDiplomacy.resolveManagerField(Events.class, DiplomacyManager.class),
                "getRelation", DiplomacyManager.class.getMethod("getRelation", String.class, String.class),
                "setRelation", DiplomacyManager.class.getMethod("setRelation", String.class, String.class, Status.class, ServerLevel.class));
        for (var entry : values.entrySet()) {
            Field field = RaiderDiplomacy.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            previous.put(field, field.get(null));
            field.set(null, entry.getValue());
        }
        Events.recruitsDiplomacyManager = new DiplomacyManager();
        return () -> {
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
            Events.recruitsDiplomacyManager = null;
        };
    }

    @Test void publicBridgeUpdatesAndRepairsBothDirectionsOnDiplomacyManager() throws Exception {
        try (var binding = bind()) {
            var server = mock(MinecraftServer.class);
            when(server.overworld()).thenReturn(mock(ServerLevel.class));
            String host = "wilds_marauders", enemy = RaiderFactions.id(host);
            RaiderDiplomacy.markEnemy(server, "team:blue", host);
            assertEquals("ENEMY", RaiderDiplomacy.currentRelation("team:blue", enemy));
            assertEquals("ENEMY", RaiderDiplomacy.currentRelation(enemy, "blue"));
            var manager = Events.recruitsDiplomacyManager;
            assertEquals(2, manager.writes);
            RaiderDiplomacy.maintainEnemy(server, "team:blue", host);
            assertEquals(2, manager.writes, "unchanged relations must not broadcast again");
            manager.setRelation(enemy, "blue", Status.ALLY, server.overworld());
            RaiderDiplomacy.maintainEnemy(server, "team:blue", host);
            assertEquals("ENEMY", RaiderDiplomacy.currentRelation(enemy, "blue"));
            RaiderDiplomacy.resetRelation(server, "team:blue", host);
            assertEquals("NEUTRAL", RaiderDiplomacy.currentRelation("blue", enemy));
            assertEquals("NEUTRAL", RaiderDiplomacy.currentRelation(enemy, "blue"));
            assertFalse(manager.relations.containsKey("team:blue"));
        }
    }

    @Test void serverRestartReadsReplacementManagerAndMissingManagerIsSafe() throws Exception {
        try (var binding = bind()) {
            var server = mock(MinecraftServer.class);
            when(server.overworld()).thenReturn(mock(ServerLevel.class));
            RaiderDiplomacy.setRelation(server, "blue", "red", "ENEMY");
            var old = Events.recruitsDiplomacyManager;
            Events.recruitsDiplomacyManager = new DiplomacyManager();
            assertEquals("NEUTRAL", RaiderDiplomacy.currentRelation("blue", "red"));
            RaiderDiplomacy.setRelation(server, "blue", "red", "ALLY");
            assertEquals(Status.ENEMY, old.getRelation("blue", "red"));
            assertEquals("ALLY", RaiderDiplomacy.currentRelation("blue", "red"));
            Events.recruitsDiplomacyManager = null;
            assertNull(RaiderDiplomacy.currentRelation("blue", "red"));
            assertDoesNotThrow(() -> RaiderDiplomacy.setRelation(server, "blue", "red", "ENEMY"));
        }
    }

    @Test void rejectsWrongManagerTypeAndIgnoresSoloFallbackIdentity() throws Exception {
        assertThrows(NoSuchFieldException.class,
                () -> RaiderDiplomacy.resolveManagerField(WrongEvents.class, DiplomacyManager.class));
        try (var binding = bind()) {
            var server = mock(MinecraftServer.class);
            RaiderDiplomacy.markEnemy(server, "player:solo", "wilds_marauders");
            assertNull(RaiderDiplomacy.currentRelation("player:solo", "red"));
            assertEquals(0, Events.recruitsDiplomacyManager.writes);
            verifyNoInteractions(server);
        }
    }
}
