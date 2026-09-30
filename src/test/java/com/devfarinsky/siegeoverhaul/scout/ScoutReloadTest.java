package com.devfarinsky.siegeoverhaul.scout;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidEvents;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoutReloadTest extends MinecraftTestSupport {
    final MinecraftServer server = mock(MinecraftServer.class);
    final ServerLevel level = mock(ServerLevel.class);
    final Pillager scout = mock(Pillager.class);
    final PathNavigation navigation = mock(PathNavigation.class);
    final CompoundTag tag = new CompoundTag();
    final RaidSavedData data = new RaidSavedData();
    final BlockPos lookout = new BlockPos(20, 64, 0);
    final BlockPos home = new BlockPos(100, 64, 0);
    final ScoutMission mission = new ScoutMission("team:test", 10, 200, null);

    ScoutReloadTest() throws ReflectiveOperationException {
        when(level.getServer()).thenReturn(server);
        when(level.getGameTime()).thenReturn(100L);
        when(scout.getUUID()).thenReturn(UUID.randomUUID());
        when(scout.getPersistentData()).thenReturn(tag);
        when(scout.getNavigation()).thenReturn(navigation);
        when(scout.isAlive()).thenReturn(true);
        when(scout.blockPosition()).thenReturn(lookout);
        when(scout.level()).thenReturn(level);
        when(scout.getBoundingBox()).thenReturn(new AABB(20,64,0,21,66,1));
        when(level.getEntitiesOfClass(eq(net.minecraft.world.entity.player.Player.class), any(AABB.class)))
                .thenReturn(List.of());
        scout.tickCount = 1; // Avoid cosmetic look-control work in the isolated AI test.
        for (String field : List.of("goalSelector", "targetSelector")) {
            var f = Mob.class.getDeclaredField(field); f.setAccessible(true);
            f.set(scout, new GoalSelector(() -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE));
        }
        tag.putBoolean(ModConstants.Tags.SCOUT, true);
        tag.putString(ModConstants.Tags.RAID_TEAM, mission.teamKey);
        mission.spawned = true;
        mission.scoutUuids.add(scout.getUUID());
        mission.bountyPaid = 7;
        data.scoutMissions.put(mission.teamKey, mission);
        data.anchors.put(mission.teamKey, new RaidSavedData.Anchor(mission.teamKey, "Test", UUID.randomUUID(),
                Set.of(), false, false, Map.of(), 10000));
    }

    RaiderScoutGoal newRoute() { return new RaiderScoutGoal(scout, lookout, home, 100, 1.0); }
    boolean reload() {
        try (var cores = mockStatic(SiegeCore.class)) {
            cores.when(() -> SiegeCore.point(server, mission.teamKey)).thenReturn(mock(RaidSavedData.DefensePoint.class));
            return ScoutManager.reloadScout(level, data, scout);
        }
    }
    RaiderScoutGoal installed() {
        return (RaiderScoutGoal) scout.goalSelector.getAvailableGoals().iterator().next().getGoal();
    }

    @Test void activeReloadRestoresObservationAndRemovesVanillaCombatGoals() {
        var route = newRoute(); route.tick(); route.tick();
        assertEquals(99, tag.getCompound("SiegeScoutRoute").getInt("Remaining"));
        scout.goalSelector.addGoal(1, mock(Goal.class));
        scout.targetSelector.addGoal(1, mock(Goal.class));
        assertFalse(reload());
        assertEquals(1, scout.goalSelector.getAvailableGoals().size());
        assertTrue(scout.targetSelector.getAvailableGoals().isEmpty());
        installed().start(); installed().tick();
        assertEquals("OBSERVE", tag.getCompound("SiegeScoutRoute").getString("Phase"));
        assertEquals(98, tag.getCompound("SiegeScoutRoute").getInt("Remaining"));
        verify(scout).setTarget(null);
        verify(scout, never()).discard();
        verify(navigation, never()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void fleeingScoutKeepsReturnDirectionAfterReload() {
        newRoute().triggerFlee();
        assertEquals("FLEE", tag.getCompound("SiegeScoutRoute").getString("Phase"));
        clearInvocations(navigation);
        assertFalse(reload()); installed().start();
        verify(navigation).moveTo(100.5, 64.0, 0.5, 1.2);
        verify(navigation, never()).moveTo(20.5, 64.0, 0.5, 1.0);
    }

    @Test void expiredJoinIsCanceledWithoutLosingMissionIntelOrBounty() {
        newRoute(); when(level.getGameTime()).thenReturn(200L);
        var event = new EntityJoinLevelEvent(scout, level, true);
        try (var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            RaidEvents.onEntityJoin(event);
        }
        assertTrue(event.isCanceled()); verify(scout).discard();
        assertTrue(mission.scoutUuids.isEmpty());
        assertSame(mission, data.scoutMissions.get(mission.teamKey));
        assertEquals(7, mission.bountyPaid);
        assertTrue(data.raids.isEmpty());
    }

    @Test void disabledScoutingAndStartedRaidsRecallReloadedScouts() {
        newRoute(); RaidConfig.SCOUTING_ENABLED.set(false);
        assertTrue(reload());
        RaidConfig.SCOUTING_ENABLED.set(true); mission.scoutUuids.add(scout.getUUID());
        data.raids.put(mission.teamKey, new RaidSavedData.RaidState(mission.teamKey, "siege_core", 0));
        assertTrue(reload()); verify(scout, times(2)).discard();
    }

    @Test void orphanUnregisteredAndLegacyScoutsAreRetiredWithoutReplacementParty() {
        // Legacy scouts have no route. Mission remains spawned so reload cannot create another party.
        assertTrue(reload()); assertTrue(mission.spawned); assertEquals(7, mission.bountyPaid);
        newRoute(); assertTrue(reload()); // UUID no longer registered
        data.scoutMissions.clear(); assertTrue(reload());
        verify(scout, times(3)).discard();
    }

    @Test void missingCoreOrAnchorRecallsActiveRoute() {
        newRoute();
        try (var cores = mockStatic(SiegeCore.class)) {
            assertTrue(ScoutManager.reloadScout(level, data, scout));
        }
        mission.scoutUuids.add(scout.getUUID()); data.anchors.clear(); assertTrue(reload());
        verify(scout, times(2)).discard();
    }

    @Test void corruptRouteIsRetiredAndUnrelatedMobIsUntouched() {
        newRoute(); tag.getCompound("SiegeScoutRoute").putString("Phase", "BAD");
        assertTrue(reload()); verify(scout).discard();
        clearInvocations(scout); tag.remove(ModConstants.Tags.SCOUT);
        assertFalse(reload()); verify(scout, never()).discard(); verify(scout, never()).setTarget(any());
    }

    @Test void freshSpawnDoesNotRequireMissionRegistrationBeforeAddFreshEntityCompletes() {
        data.scoutMissions.clear();
        var event = new EntityJoinLevelEvent(scout, level, false);
        try (var saves = mockStatic(RaidSavedData.class)) {
            RaidEvents.onEntityJoin(event);
            saves.verifyNoInteractions();
        }
        assertFalse(event.isCanceled()); verify(scout, never()).discard();
    }
}
