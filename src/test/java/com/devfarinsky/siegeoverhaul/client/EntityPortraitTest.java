package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPortraitTest {
    @Test
    void ordinaryUnitsKeepTheirEntityRole() {
        for (int role = 0; role < 10; role++) {
            assertEquals(role, EntityPortrait.entityRole(role));
        }
    }

    @Test
    void everyOlympianHeroMapsToARealRecruitEntity() {
        for (int role = 10; role <= 29; role++) {
            int entityRole = EntityPortrait.entityRole(role);
            assertTrue(entityRole >= 0 && entityRole <= 3,
                    "hero " + role + " mapped outside the Recruits combat roster");
        }
    }

    @Test
    void lateRosterHeroesDoNotUseTheOldRoleMinusTenMapping() {
        assertEquals(0, EntityPortrait.entityRole(29));
    }
}
