package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CampGuardRoleTest {
    private static final Set<ResourceLocation> RECRUITS_1_15_2 = Set.of(
            new ResourceLocation("recruits", "recruit"), new ResourceLocation("recruits", "bowman"),
            new ResourceLocation("recruits", "scout"), new ResourceLocation("recruits", "crossbowman"));

    @Test void registeredDoctrineRolesSpawnAsRequested() {
        for (String role : new String[]{"bowman", "scout", "crossbowman"})
            assertEquals(role, CampGuards.nativeRole(role, RECRUITS_1_15_2::contains));
    }

    @Test void unregisteredAssassinSlotIsFilledByABasicRecruit() {
        assertEquals(CampGuards.FALLBACK_ROLE, CampGuards.nativeRole("assassin", RECRUITS_1_15_2::contains));
        assertTrue(RECRUITS_1_15_2.contains(new ResourceLocation("recruits", CampGuards.FALLBACK_ROLE)));
    }
}
