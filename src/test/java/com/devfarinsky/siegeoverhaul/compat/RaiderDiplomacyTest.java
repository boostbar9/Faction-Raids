package com.devfarinsky.siegeoverhaul.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaiderDiplomacyTest {
    @Test void activeSiegeRepairsEitherDirectionWithoutRetryingUnavailableApi() {
        assertFalse(RaiderDiplomacy.needsEnemyRepair(null, null));
        assertFalse(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.ENEMY, RaiderDiplomacy.ENEMY));
        assertTrue(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.ALLY, RaiderDiplomacy.ENEMY));
        assertTrue(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.ENEMY, RaiderDiplomacy.NEUTRAL));
        assertTrue(RaiderDiplomacy.needsEnemyRepair(RaiderDiplomacy.NEUTRAL, RaiderDiplomacy.ALLY));
    }
}
