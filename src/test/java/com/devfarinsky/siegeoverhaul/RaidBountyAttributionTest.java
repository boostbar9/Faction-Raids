package com.devfarinsky.siegeoverhaul;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RaidBountyAttributionTest extends MinecraftTestSupport {

    @Test
    void onlyDefendingFactionMembersQualifyAsPlayerKillers() {
        UUID memberId = UUID.randomUUID();
        RaidSavedData.Anchor anchor = new RaidSavedData.Anchor(
                "team:test", "Test", memberId, Set.of(memberId),
                false, false, Map.of(), 0);

        ServerPlayer member = mock(ServerPlayer.class);
        when(member.getUUID()).thenReturn(memberId);
        ServerPlayer outsider = mock(ServerPlayer.class);
        when(outsider.getUUID()).thenReturn(UUID.randomUUID());

        assertTrue(RaidEvents.isFactionDefender(member, anchor));
        assertFalse(RaidEvents.isFactionDefender(outsider, anchor));
        assertFalse(RaidEvents.isFactionDefender(mock(Entity.class), anchor));
        assertFalse(RaidEvents.isFactionDefender(member, null));
        assertFalse(RaidEvents.isFactionDefender(null, anchor));
    }
}
