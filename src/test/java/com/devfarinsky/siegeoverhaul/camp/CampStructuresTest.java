package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampStructuresTest extends MinecraftTestSupport {
    @Test void liveInstallationsAnnounceTheirPatronAndEmitOnlyWhileLoadedAndStanding() {
        RaidConfig.ANNOUNCE_GLOBALLY.set(false);
        for(var host:com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.hosts()) {
            var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.factionId=host.factionId();
            var center=new BlockPos(10,64,10);CampStructures.record(raid,0,center,Direction.NORTH);
            complete(raid, center, Direction.NORTH);
            var pos=CampStructures.keystone(center,Direction.NORTH);
            var level=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var players=mock(PlayerList.class);
            var player=mock(net.minecraft.server.level.ServerPlayer.class);
            when(level.getServer()).thenReturn(server);when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayers()).thenReturn(List.of(player));when(level.hasChunkAt(pos)).thenReturn(true);
            when(level.getBlockState(pos)).thenReturn(Blocks.HAY_BLOCK.defaultBlockState());when(level.getGameTime()).thenReturn(20L);
            try(var saves=mockStatic(RaidSavedData.class);var keys=mockStatic(com.devfarinsky.siegeoverhaul.core.SiegeCore.class)) {
                saves.when(()->RaidSavedData.get(server)).thenReturn(new RaidSavedData());
                keys.when(()->com.devfarinsky.siegeoverhaul.core.SiegeCore.key(player)).thenReturn("team:test");
                CampStructures.tick(level,raid);
                String title=CampStructures.title(raid,CampStructures.Kind.GRANARY);
                verify(player).sendSystemMessage(argThat(c->c.getString().contains(title+" raised") && c.getString().contains("recover health")));
                var particle=com.devfarinsky.siegeoverhaul.narrative.OlympianPresentation.forFaction(host.factionId()).particle();
                verify(level).sendParticles(eq(particle),anyDouble(),anyDouble(),anyDouble(),eq(3),anyDouble(),anyDouble(),anyDouble(),anyDouble());
                clearInvocations(level,player);
                when(level.hasChunkAt(pos)).thenReturn(false);CampStructures.tick(level,raid);
                verify(level,never()).sendParticles(any(),anyDouble(),anyDouble(),anyDouble(),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
                when(level.hasChunkAt(pos)).thenReturn(true);when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
                CampStructures.tick(level,raid);
                verify(player).sendSystemMessage(argThat(c->c.getString().contains(title+" destroyed")));
                assertFalse(CampStructures.standing(raid,CampStructures.Kind.GRANARY));
                verify(level,never()).sendParticles(any(),anyDouble(),anyDouble(),anyDouble(),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
            }
        }
    }
    @Test void stageMappingAndConfigurableEffectsHaveBackwardsCompatibleDefaults() {
        assertEquals(CampStructures.Kind.GRANARY, CampStructures.Kind.forStage(0));
        assertEquals(CampStructures.Kind.ARMOURY, CampStructures.Kind.forStage(1));
        assertEquals(CampStructures.Kind.COMMAND_POST, CampStructures.Kind.forStage(2));
        assertNull(CampStructures.Kind.forStage(3));

        int base = RaidConfig.TIME_BETWEEN_WAVES_SECONDS.get() * 20;
        assertEquals(base, CampStructures.waveIntervalTicks(base, false));
        int coordinated = CampStructures.waveIntervalTicks(base, true);
        assertTrue(coordinated < base && coordinated >= 20, "Command Post should shorten the wave delay");
        assertEquals(Math.max(20, base * RaidConfig.COMMAND_POST_WAVE_INTERVAL_PERCENT.get() / 100), coordinated);

        assertEquals(0, CampStructures.guardRegen(false));
        assertEquals(RaidConfig.GRANARY_GUARD_REGEN.get(), CampStructures.guardRegen(true));
        assertEquals(0.0D, CampStructures.armouryDamageBonus(false));
        assertEquals(RaidConfig.ARMOURY_GUARD_DAMAGE.get(), CampStructures.armouryDamageBonus(true));
    }

    @Test void keystoneLifecycleTracksStandingBurningAndSurvivesReload() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        BlockPos center = new BlockPos(10, 64, 10);
        CampStructures.record(raid, 0, center, Direction.NORTH);
        assertFalse(CampStructures.standing(raid, CampStructures.Kind.GRANARY));
        complete(raid, center, Direction.NORTH);

        BlockPos keystone = center.relative(Direction.NORTH, -2).above();
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        var players = mock(PlayerList.class);
        when(level.getServer()).thenReturn(server);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of());
        when(level.hasChunkAt(keystone)).thenReturn(true);
        when(level.getBlockState(keystone)).thenReturn(Blocks.HAY_BLOCK.defaultBlockState());

        try (var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(new RaidSavedData());

            CampStructures.tick(level, raid);
            assertTrue(CampStructures.standing(raid, CampStructures.Kind.GRANARY), "keystone present means the Granary stands");

            // Standing state survives a save/reload round-trip.
            var reloaded = RaidSavedData.RaidState.load(raid.save());
            assertTrue(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));

            // Burning or breaking the keystone removes the benefit.
            when(level.getBlockState(keystone)).thenReturn(Blocks.AIR.defaultBlockState());
            CampStructures.tick(level, reloaded);
            assertFalse(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));

            // An unloaded keystone must never flip a standing structure to destroyed.
            when(level.getBlockState(keystone)).thenReturn(Blocks.HAY_BLOCK.defaultBlockState());
            CampStructures.tick(level, reloaded);
            assertTrue(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));
            when(level.hasChunkAt(keystone)).thenReturn(false);
            when(level.getBlockState(keystone)).thenReturn(Blocks.AIR.defaultBlockState());
            CampStructures.tick(level, reloaded);
            assertTrue(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));
        }
    }

    @Test void legacyOutsidePavilionKeepsThreeWideDoorThroughLaterPalisade() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campPos=new BlockPos(0,64,0);
        BlockPos oldCenter=raid.campPos.east(15);
        // Simulate the old save shape, which had only the keystone position.
        var all=new net.minecraft.nbt.CompoundTag();
        var granary=new net.minecraft.nbt.CompoundTag();
        granary.putLong("Pos",oldCenter.east(2).above().asLong());
        all.put(CampStructures.Kind.GRANARY.key,granary);
        raid.campaign.put(ModConstants.Tags.CAMP_STRUCTURES,all);
        BlockPos doorway=raid.campPos.east(CampPerimeter.RADIUS);
        assertTrue(CampStructures.legacyPerimeterOpening(raid,doorway));
        assertTrue(CampStructures.legacyPerimeterOpening(raid,doorway.north()));
        assertTrue(CampStructures.legacyPerimeterOpening(raid,doorway.south()));
        assertFalse(CampStructures.legacyPerimeterOpening(raid,doorway.north(2)));
    }

    private static void complete(RaidSavedData.RaidState raid, BlockPos center, Direction entrance) {
        raid.pendingCampBlocks.put(CampStructures.keystone(center, entrance).asLong(), "minecraft:hay_block");
        CampStructures.constructionCompleted(raid);
        raid.pendingCampBlocks.clear();
    }

    @Test void unfinishedKeystoneStaysDormantAcrossReloadAndUnrelatedCompletedJobs() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        BlockPos center = new BlockPos(10, 64, 10);
        CampStructures.record(raid, 0, center, Direction.NORTH);
        var level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.HAY_BLOCK.defaultBlockState());
        CampStructures.tick(level, raid);
        assertFalse(CampStructures.standing(raid, CampStructures.Kind.GRANARY));
        var reloaded = RaidSavedData.RaidState.load(raid.save());
        // A stopped/abandoned job no longer has pending cells. Neither that nor
        // completion of a subsequent perimeter project unlocks this building.
        CampStructures.constructionCompleted(reloaded);
        reloaded.pendingCampBlocks.put(center.east(12).asLong(), "minecraft:stone_bricks");
        CampStructures.constructionCompleted(reloaded);
        CampStructures.tick(level, reloaded);
        assertFalse(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));
        verify(level, never()).sendParticles(any(), anyDouble(), anyDouble(), anyDouble(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(level, never()).getServer();
    }

    @Test void olderCompletedInstallationsKeepKeystoneBehavior() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        CampStructures.record(raid, 0, BlockPos.ZERO, Direction.NORTH);
        var entry = raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES).getCompound("granary");
        entry.remove("AwaitingBuild");
        entry.putBoolean("Active", true);
        entry.putBoolean("Announced", true);
        var reloaded = RaidSavedData.RaidState.load(raid.save());
        assertTrue(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));
        var level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.HAY_BLOCK.defaultBlockState());
        when(level.getGameTime()).thenReturn(1L);
        CampStructures.tick(level, reloaded);
        assertTrue(CampStructures.standing(reloaded, CampStructures.Kind.GRANARY));
    }

    @Test void nativeCompletionWaitsForLastBlockAndUnlocksBeforeClearingPlan() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        BlockPos center = new BlockPos(10, 64, 10);
        CampStructures.record(raid, 0, center, Direction.NORTH);
        BlockPos keystone = CampStructures.keystone(center, Direction.NORTH);
        BlockPos roof = center.above(4);
        raid.pendingCampBlocks.put(keystone.asLong(), "minecraft:hay_block");
        raid.pendingCampBlocks.put(roof.asLong(), "minecraft:quartz_block");
        var level = mock(ServerLevel.class);
        when(level.getBlockState(keystone)).thenReturn(Blocks.HAY_BLOCK.defaultBlockState());
        when(level.getBlockState(roof)).thenReturn(Blocks.AIR.defaultBlockState());
        try (var nativeJobs = mockStatic(NativeCampConstruction.class)) {
            nativeJobs.when(() -> NativeCampConstruction.tick(level, raid)).thenCallRealMethod();
            nativeJobs.when(() -> NativeCampConstruction.safeToTick(level, raid)).thenReturn(true);
            nativeJobs.when(() -> NativeCampConstruction.stop(level, raid)).thenAnswer(call -> {
                assertFalse(raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES)
                        .getCompound("granary").getBoolean("AwaitingBuild"));
                raid.pendingCampBlocks.clear();
                return null;
            });
            NativeCampConstruction.tick(level, raid);
            assertTrue(raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES)
                    .getCompound("granary").getBoolean("AwaitingBuild"));
            nativeJobs.verify(() -> NativeCampConstruction.stop(level, raid), never());
            when(level.getBlockState(roof)).thenReturn(Blocks.QUARTZ_BLOCK.defaultBlockState());
            NativeCampConstruction.tick(level, raid);
            assertTrue(raid.pendingCampBlocks.isEmpty());
            var reloaded = RaidSavedData.RaidState.load(raid.save());
            assertFalse(reloaded.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES)
                    .getCompound("granary").getBoolean("AwaitingBuild"));
        }
    }
}
