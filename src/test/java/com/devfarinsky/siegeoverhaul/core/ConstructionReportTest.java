package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConstructionReportTest extends MinecraftTestSupport {
    @Test void resubscribingRefreshesImmediatelyButRepeatedWatchDoesNot() throws Exception {
        var menu = mock(CoreHireMenu.class, CALLS_REAL_METHODS);
        var field = CoreHireMenu.class.getDeclaredField("constructionAt");
        field.setAccessible(true);
        field.setLong(menu, 100L);
        menu.watchConstruction(true);
        assertEquals(Long.MIN_VALUE, field.getLong(menu));
        field.setLong(menu, 120L);
        menu.watchConstruction(true);
        assertEquals(120L, field.getLong(menu));
        menu.watchConstruction(false);
        menu.watchConstruction(true);
        assertEquals(Long.MIN_VALUE, field.getLong(menu));
    }
    @Test void reportsOnlyTheRequestersOwnedJobsAndDoesNotMutateAreas() {
        var level = mock(ServerLevel.class); var player = mock(ServerPlayer.class);
        var ours = mock(Entity.class); var foreign = mock(Entity.class);
        UUID owner = UUID.randomUUID(); var saved = new CompoundTag();
        when(player.level()).thenReturn(level); when(player.serverLevel()).thenReturn(level);
        when(player.getUUID()).thenReturn(owner); when(player.getPersistentData()).thenReturn(new CompoundTag());
        when(player.getBoundingBox()).thenReturn(new AABB(BlockPos.ZERO)); when(level.getGameTime()).thenReturn(100L);
        for (Entity area : List.of(ours, foreign)) {
            var tag = new CompoundTag(); tag.putBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA, true);
            when(area.getPersistentData()).thenReturn(tag); when(area.isAlive()).thenReturn(true);
            when(area.getUUID()).thenReturn(UUID.randomUUID()); when(area.blockPosition()).thenReturn(BlockPos.ZERO);
        }
        ConstructionReport.remember(ours, "Our tower", 20);
        ConstructionReport.remember(foreign, "Private enemy project", 20);
        var before = ours.getPersistentData().copy();
        when(level.getEntitiesOfClass(eq(Entity.class), any(), any())).thenAnswer(call -> {
            Predicate<Entity> filter = call.getArgument(2);
            return List.of(ours, foreign).stream().filter(filter).toList();
        });
        try (var bridge = mockStatic(WorkersBridge.class)) {
            bridge.when(() -> WorkersBridge.isBuildArea(any())).thenReturn(true);
            bridge.when(() -> WorkersBridge.readOwner(ours)).thenReturn(owner);
            bridge.when(() -> WorkersBridge.readOwner(foreign)).thenReturn(UUID.randomUUID());
            var jobs = ConstructionReport.snapshot(player);
            assertEquals(1, jobs.size());
            assertEquals("Our tower", jobs.get(0).label());
            assertEquals(-1, jobs.get(0).percent());
            verify(player, never()).sendSystemMessage(any(Component.class));
            assertEquals(before, ours.getPersistentData());
            assertEquals(1, ConstructionReport.report(player));
            var messages = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendSystemMessage(messages.capture());
            String text = messages.getAllValues().stream().map(Component::getString).reduce("", (a, b) -> a + b);
            assertTrue(text.contains("Our tower")); assertTrue(text.contains("progress unavailable"));
            assertFalse(text.contains("Private enemy project")); assertFalse(text.contains("100%"));
            assertEquals(before, ours.getPersistentData());
            verify(ours, never()).discard(); verify(foreign, never()).discard();
            assertEquals(0, ConstructionReport.report(player));
        }
    }
}
