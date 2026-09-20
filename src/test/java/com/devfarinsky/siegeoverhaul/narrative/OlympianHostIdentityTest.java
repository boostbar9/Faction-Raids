package com.devfarinsky.siegeoverhaul.narrative;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import com.devfarinsky.siegeoverhaul.items.FactionBanners;
import com.devfarinsky.siegeoverhaul.waves.WaveComposer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class OlympianHostIdentityTest extends MinecraftTestSupport {
    @Test void stableFactionIdsHaveUniqueDoctrinesPowersAndCompleteChampionRosters() {
        var hosts=OlympianHostIdentity.hosts();
        assertEquals(5,hosts.size());
        assertEquals(java.util.Arrays.stream(FactionBanners.FactionId.values()).map(f->f.id).collect(java.util.stream.Collectors.toSet()),
                hosts.stream().map(OlympianHostIdentity::factionId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(5,hosts.stream().map(OlympianHostIdentity::formation).distinct().count());
        assertEquals(5,hosts.stream().map(OlympianHostIdentity::commanderPower).distinct().count());
        assertEquals(5,hosts.stream().map(OlympianHostIdentity::waveLabel).distinct().count());
        assertEquals(5,hosts.stream().map(host->host.signatureAssault().title()).distinct().count());
        assertEquals(5,hosts.stream().map(host->host.campDoctrine().guardTitle()).distinct().count());
        assertEquals(5,hosts.stream().map(host->host.campDoctrine().leashBlocks()).distinct().count());

        var champions=new HashSet<Integer>();
        for(var host:hosts) {
            assertEquals(4,host.heroRoles().size());
            assertFalse(host.commanderMessage().isBlank());
            for(int role:host.heroRoles()) {
                assertTrue(champions.add(role),"hero assigned to two hosts: "+role);
                assertEquals(host.factionId(),CoreHiring.heroFaction(role));
            }
            var reachable=new HashSet<Integer>();
            for(int roll=0;roll<host.heroWeightTotal();roll++)reachable.add(host.heroForRoll(roll));
            assertEquals(new HashSet<>(host.heroRoles()),reachable);
            for(String role:host.assaultRoles()) {
                assertTrue(WaveComposer.COMBAT_TYPES.contains(role));
                assertNotEquals("siege_engineer",role);
                assertNotEquals("patrol_leader",role);
            }
            assertFalse(host.signatureWave(4,5));
            assertTrue(host.signatureWave(5,5));
            assertTrue(host.signatureWave(5,10));
            assertFalse(host.signatureWave(6,10));
            assertFalse(host.signatureWave(9,10));
            assertTrue(host.signatureWave(10,10));
            assertFalse(host.signatureWave(11,10));
            assertFalse(host.signatureAssault().counterplay().isBlank());
            assertFalse(host.signatureAssault().roles().isEmpty());
            for(String role:host.signatureAssault().roles()) {
                assertTrue(WaveComposer.COMBAT_TYPES.contains(role));
                assertNotEquals("siege_engineer",role);
                assertNotEquals("patrol_leader",role);
            }
            assertEquals(6,host.campDoctrine().guardRoles().size());
            for(String role:host.campDoctrine().guardRoles())
                assertTrue(WaveComposer.COMBAT_TYPES.contains(role));
        }
        assertEquals(java.util.stream.IntStream.rangeClosed(10,29).boxed().collect(java.util.stream.Collectors.toSet()),champions);
    }

    @Test void missingLegacyFactionFallsBackWithoutBecomingPoseidon() {
        var fallback=OlympianHostIdentity.forFaction("datapack:unknown");
        assertTrue(fallback.factionId().isBlank());
        assertEquals(OlympianHostIdentity.CommanderPower.LAST_STAND,fallback.commanderPower());
        assertEquals(20,fallback.heroRoles().size());
    }
}
