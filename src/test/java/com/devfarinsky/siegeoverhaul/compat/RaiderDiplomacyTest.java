package com.devfarinsky.siegeoverhaul.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaiderDiplomacyTest {
    @Test void convertsSavedTeamKeysToNativeRecruitsFactionIds() {
        assertEquals("blue", RaiderDiplomacy.factionId("team:blue"));
        assertEquals("siegeoverhaul_wilds_marauders",
                RaiderDiplomacy.factionId("siegeoverhaul_wilds_marauders"));
        assertNull(RaiderDiplomacy.factionId("team:"));
        assertNull(RaiderDiplomacy.factionId("player:00000000-0000-0000-0000-000000000000"));
        assertNull(RaiderDiplomacy.factionId(" "));
    }

    @Test void activeSiegeRepairsEitherDirectionWithoutRetryingUnavailableApi() {
        assertFalse(RaiderDiplomacy.needsEnemyRepair(null, null));
        assertFalse(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.ENEMY, RaiderDiplomacy.ENEMY));
        assertTrue(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.ALLY, RaiderDiplomacy.ENEMY));
        assertTrue(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.ENEMY, RaiderDiplomacy.NEUTRAL));
        assertTrue(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.NEUTRAL, RaiderDiplomacy.ALLY));
    }
}
