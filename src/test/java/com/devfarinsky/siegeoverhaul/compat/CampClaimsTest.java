package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CampClaimsTest extends MinecraftTestSupport {
    @Test void staleLegacyAndOlympianCampClaimsAreCleanupCandidates() {
        UUID legacy = UUID.randomUUID();
        UUID olympian = UUID.randomUUID();

        assertTrue(CampClaims.orphanedRaidClaim(
                legacy, RecruitsBridge.RAIDERS_FACTION_ID, Set.of()));
        assertTrue(CampClaims.orphanedRaidClaim(
                olympian, RaiderFactions.id("blackbay_reavers"), Set.of()));
    }

    @Test void activeOccupiedAndPlayerCapturedClaimsArePreserved() {
        UUID active = UUID.randomUUID();
        UUID occupied = UUID.randomUUID();
        UUID captured = UUID.randomUUID();

        assertFalse(CampClaims.orphanedRaidClaim(
                active, RaiderFactions.id("hollowfang_clan"), Set.of(active)));
        assertFalse(CampClaims.orphanedRaidClaim(
                occupied, RaiderFactions.id("crownfall_exiles"), Set.of(occupied)));
        assertFalse(CampClaims.orphanedRaidClaim(captured, "player_faction", Set.of()));
        assertFalse(CampClaims.orphanedRaidClaim(null, RecruitsBridge.RAIDERS_FACTION_ID, Set.of()));
    }
}
