package com.devfarinsky.siegeoverhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.level.BlockEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RaidCampBarrelRewardsTest extends MinecraftTestSupport {
    private void setTeam(ServerPlayer player, String name) {
        PlayerTeam team = name == null ? null : mock(PlayerTeam.class);
        if (team != null) when(team.getName()).thenReturn(name);
        when(player.getTeam()).thenReturn(team);
    }

    @Test void currentScoreboardTeamOverridesIncompleteOrStaleSavedCoreRoster() {
        UUID placer = UUID.randomUUID();
        var anchor = new RaidSavedData.Anchor("team:test", "Test", placer, Set.of(placer),
                false, false, Map.of(), 0);
        var teammate = mock(ServerPlayer.class);
        when(teammate.getUUID()).thenReturn(UUID.randomUUID());
        setTeam(teammate, "test");
        assertFalse(anchor.members().contains(teammate.getUUID()));
        assertTrue(RaidEvents.isCurrentCampDefender(teammate, anchor));

        var formerMember = mock(ServerPlayer.class);
        when(formerMember.getUUID()).thenReturn(placer);
        setTeam(formerMember, "other");
        assertFalse(RaidEvents.isCurrentCampDefender(formerMember, anchor));
        setTeam(formerMember, null);
        assertFalse(RaidEvents.isCurrentCampDefender(formerMember, anchor));
        setTeam(formerMember, "test");
        assertTrue(RaidEvents.isCurrentCampDefender(formerMember, anchor));
    }

    @Test void onlyInternalNonTeamFactionsFallBackToTheExplicitSavedRoster() {
        UUID memberId = UUID.randomUUID();
        var internal = new RaidSavedData.Anchor("legacy:faction", "Legacy", memberId, Set.of(memberId),
                true, false, Map.of(), 0);
        var member = mock(ServerPlayer.class);
        when(member.getUUID()).thenReturn(memberId);
        setTeam(member, "other");
        assertTrue(RaidEvents.isCurrentCampDefender(member, internal));
        var outsider = mock(ServerPlayer.class);
        when(outsider.getUUID()).thenReturn(UUID.randomUUID());
        assertFalse(RaidEvents.isCurrentCampDefender(outsider, internal));
        var noRoster = new RaidSavedData.Anchor("legacy:faction", "Legacy", memberId, Set.of(memberId),
                false, false, Map.of(), 0);
        assertFalse(RaidEvents.isCurrentCampDefender(member, noRoster));
        var teamWithRoster = new RaidSavedData.Anchor("team:test", "Test", memberId, Set.of(memberId),
                true, false, Map.of(), 0);
        assertFalse(RaidEvents.isCurrentCampDefender(member, teamWithRoster));
    }

    @Test void onlyEligibleDefenderBreakPaysAndDestructionNeverReplaysAfterReload() {
        for (int mode = 0; mode < 8; mode++) {
            var data = new RaidSavedData();
            var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
            var pos = new BlockPos(32, 64, 48);
            state.wave = 1; state.barrelPos = pos; state.rewardEligible = mode != 1;
            var player = mock(ServerPlayer.class); var memberId = UUID.randomUUID();
            when(player.getUUID()).thenReturn(mode == 2 || mode == 5 ? UUID.randomUUID() : memberId);
            setTeam(player, mode == 7 ? null : (mode == 2 || mode == 6 ? "other" : "test"));
            when(player.isSpectator()).thenReturn(mode == 3);
            var anchor = new RaidSavedData.Anchor("team:test", "Test", memberId, Set.of(memberId),
                    false, false, Map.of(), 0);
            var server = mock(MinecraftServer.class); var level = mock(ServerLevel.class);
            var players = mock(PlayerList.class);
            when(level.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayers()).thenReturn(List.of());
            when(level.addFreshEntity(any())).thenReturn(true);
            List<ItemStack> drops = new ArrayList<>();
            try (var saved = mockStatic(RaidSavedData.class, CALLS_REAL_METHODS);
                 var entities = mockConstruction(ItemEntity.class,
                         (entity, context) -> drops.add(((ItemStack) context.arguments().get(4)).copy()))) {
                saved.when(() -> RaidSavedData.get(server)).thenReturn(data);
                RaidEvents.handleBarrelBroken(level, data, state, mode == 4 ? null : anchor, player);
                RaidEvents.handleBarrelBroken(level, data, state, anchor, player);
                state = RaidSavedData.RaidState.load(state.save());
                RaidEvents.handleBarrelBroken(level, data, state, anchor, player);
                assertNull(state.barrelPos);
                assertTrue(data.isDirty());
                boolean eligible = mode == 0 || mode == 5;
                assertEquals(eligible ? 1 : 0, drops.size());
                if (eligible) {
                    assertTrue(drops.get(0).is(Items.EMERALD));
                    assertEquals(RaidConfig.CAMP_BONUS_LOOT_EMERALDS.get(), drops.get(0).getCount());
                    verify(entities.constructed().get(0)).setDefaultPickUpDelay();
                }
                verify(level, times(eligible ? 1 : 0)).addFreshEntity(any());
            }
        }
    }

    @Test void canceledBreakAndPreparationNeverConsumeOrRewardTheTrackedBarrel() {
        var data = new RaidSavedData();
        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        var pos = new BlockPos(32, 64, 48); state.barrelPos = pos; state.wave = 1;
        var player = mock(ServerPlayer.class); var memberId = UUID.randomUUID();
        when(player.getUUID()).thenReturn(memberId);
        setTeam(player, "test");
        var point = new RaidSavedData.DefensePoint("siege_core", Level.OVERWORLD.location(), BlockPos.ZERO);
        var anchor = new RaidSavedData.Anchor("team:test", "Test", memberId, Set.of(memberId),
                false, false, Map.of("siege_core", point), 0);
        data.anchors.put(state.teamKey, anchor); data.raids.put(state.teamKey, state);
        var server = mock(MinecraftServer.class); var level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server); when(level.dimension()).thenReturn(Level.OVERWORLD);
        var event = mock(BlockEvent.BreakEvent.class);
        when(event.getLevel()).thenReturn(level); when(event.getPos()).thenReturn(pos);
        when(event.getPlayer()).thenReturn(player);
        try (var saved = mockStatic(RaidSavedData.class)) {
            saved.when(() -> RaidSavedData.get(server)).thenReturn(data);
            when(event.isCanceled()).thenReturn(true);
            RaidEvents.onCampBlockBroken(event);
            when(event.isCanceled()).thenReturn(false); state.wave = 0;
            RaidEvents.onCampBlockBroken(event);
            assertEquals(pos, state.barrelPos);
            assertFalse(data.isDirty());
            verify(level, never()).addFreshEntity(any());
        }
    }

    @Test void failedSpawnStillConsumesBarrelBeforeMutationRatherThanDuplicatingOnRetry() {
        var data = new RaidSavedData();
        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        state.wave = 1; state.barrelPos = new BlockPos(32, 64, 48);
        var player = mock(ServerPlayer.class); var id = UUID.randomUUID();
        when(player.getUUID()).thenReturn(id);
        setTeam(player, "test");
        var anchor = new RaidSavedData.Anchor("team:test", "Test", id, Set.of(id), false, false, Map.of(), 0);
        var level = mock(ServerLevel.class);
        when(level.addFreshEntity(any())).thenAnswer(call -> {
            assertNull(state.barrelPos); assertTrue(data.isDirty());
            throw new IllegalStateException("spawn listener failed");
        });
        try (var entities = mockConstruction(ItemEntity.class)) {
            assertThrows(IllegalStateException.class, () -> RaidEvents.handleBarrelBroken(level, data, state, anchor, player));
            RaidEvents.handleBarrelBroken(level, data, state, anchor, player);
            verify(level, times(1)).addFreshEntity(any());
        }
    }
}
