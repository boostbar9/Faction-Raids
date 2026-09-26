package com.devfarinsky.siegeoverhaul.narrative;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.camp.CampStructures;
import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OlympianPresentationTest extends MinecraftTestSupport {
    @Test void allFiveHostsAndTwentyHeroesResolveDistinctConsistentSignatures() {
        var particles=new HashSet<>();var names=new HashSet<String>();
        for(var host:OlympianHostIdentity.hosts()) {
            var style=OlympianPresentation.forFaction(host.factionId());
            assertTrue(particles.add(style.particle()));
            assertNotNull(style.sound());assertEquals(3,style.installations().size());
            var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.factionId=host.factionId();
            var loaded=RaidSavedData.RaidState.load(raid.save());
            for(var kind:CampStructures.Kind.values()) {
                assertTrue(names.add(CampStructures.title(raid,kind)));
                assertEquals(CampStructures.title(raid,kind),CampStructures.title(loaded,kind));
            }
            for(int role:host.heroRoles())assertSame(style,OlympianPresentation.forFaction(CoreHiring.heroFaction(role)));
            assertThrows(UnsupportedOperationException.class,()->style.installations().add("bad"));
        }
        assertEquals(5,particles.size());assertEquals(15,names.size());
    }
    @Test void unknownDatapackFactionsUseNeutralCampNames() {
        assertEquals(List.of("Camp Stores","Camp Armoury","Command Post"),OlympianPresentation.forFaction("custom:host").installations());
        assertSame(OlympianPresentation.forFaction(null),OlympianPresentation.forFaction(""));
    }
}
