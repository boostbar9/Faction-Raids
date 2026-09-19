package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class SignatureAssaultPresentationTest extends MinecraftTestSupport {
    @Test
    void everyCheckpointExposesTheInvadingHostsUniqueTacticalWarning() {
        var titles=new HashSet<String>();
        for(var host:OlympianHostIdentity.hosts()) {
            var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
            raid.factionId=host.factionId();
            raid.wave=4;
            assertTrue(RaidEvents.signatureAssault(raid).isEmpty());
            raid.wave=5;
            var first=RaidEvents.signatureAssault(raid).orElseThrow();
            assertTrue(titles.add(first.title()));
            assertFalse(first.counterplay().isBlank());
            raid.wave=10;
            assertEquals(first,RaidEvents.signatureAssault(raid).orElseThrow());
        }
        assertEquals(5,titles.size());
    }
}
