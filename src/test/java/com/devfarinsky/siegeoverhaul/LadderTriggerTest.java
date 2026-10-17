package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.siege.LadderBuilder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LadderTriggerTest extends MinecraftTestSupport {
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void forget() { RaidEvents.STUCK_TRACKER.remove(id); }

    @Test
    void finishedPathOrBriefBumpAloneDoesNotTriggerLadders() {
        Mob mob = mock(Mob.class);
        PathNavigation nav = mock(PathNavigation.class);
        when(mob.getUUID()).thenReturn(id);
        when(mob.getNavigation()).thenReturn(nav);
        when(nav.isDone()).thenReturn(true);
        mob.horizontalCollision = true;
        assertFalse(LadderBuilder.blocked(mob), "a raider resting at its formation slot is not stuck");
        var entry = new RaidEvents.StuckEntry(100, 0);
        entry.escalationLevel = 1;
        RaidEvents.STUCK_TRACKER.put(id, entry);
        assertTrue(LadderBuilder.blocked(mob));
        assertEquals(1, RaidEvents.stuckEscalation(id));
    }
}
