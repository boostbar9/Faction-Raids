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
}
