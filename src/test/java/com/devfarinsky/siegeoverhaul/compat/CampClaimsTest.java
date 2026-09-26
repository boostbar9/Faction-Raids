package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test void unleasedLegacyClaimIsDiscoveredRemovedAndSaved() {
        UUID stale = UUID.randomUUID(), protectedId = UUID.randomUUID();
        FakeClaims claims = new FakeClaims(List.of(
                new CampClaims.ClaimRef(stale, RaiderFactions.id("blackbay_reavers")),
                new CampClaims.ClaimRef(protectedId, RaiderFactions.id("hollowfang_clan"))));

        CampClaims.CleanupResult result = CampClaims.cleanupClaims(
                Set.of(), Set.of(protectedId), claims, true);

        assertEquals(Set.of(stale), result.removed());
        assertEquals(List.of(stale), claims.removed);
        assertEquals(1, claims.saves);
        assertNull(result.failure());
    }

    @Test void managerWithoutEnumerationStillCleansKnownLease() {
        UUID leased = UUID.randomUUID();
        FakeClaims claims = new FakeClaims(List.of(
                new CampClaims.ClaimRef(leased, RecruitsBridge.RAIDERS_FACTION_ID)));
        claims.enumerationAvailable = false;

        CampClaims.CleanupResult result = CampClaims.cleanupClaims(
                Set.of(leased), Set.of(), claims, true);

        assertTrue(result.discoveryFailed());
        assertEquals(Set.of(leased), result.completed());
        assertEquals(Set.of(leased), result.removed());
        assertEquals(1, claims.saves);
    }

    @Test void fullDiscoveryIsThrottledBetweenIntervals() {
        assertTrue(CampClaims.discoveryDue(100, Long.MIN_VALUE));
        assertFalse(CampClaims.discoveryDue(100, 101));
        assertTrue(CampClaims.discoveryDue(101, 101));
    }

    private static final class FakeClaims implements CampClaims.ClaimAccess {
        private final Map<UUID, CampClaims.ClaimRef> claims = new HashMap<>();
        private final List<UUID> removed = new ArrayList<>();
        private boolean enumerationAvailable = true;
        private int saves;

        private FakeClaims(Collection<CampClaims.ClaimRef> claims) {
            claims.forEach(claim -> this.claims.put(claim.id(), claim));
        }
        public Collection<CampClaims.ClaimRef> allClaims() throws ReflectiveOperationException {
            if (!enumerationAvailable) throw new NoSuchMethodException("getAllClaims");
            return List.copyOf(claims.values());
        }
        public CampClaims.ClaimRef get(UUID id) { return claims.get(id); }
        public void remove(UUID id) { removed.add(id); claims.remove(id); }
        public void save() { saves++; }
    }
}
