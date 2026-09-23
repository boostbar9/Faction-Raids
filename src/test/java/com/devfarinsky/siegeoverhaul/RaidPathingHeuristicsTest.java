package com.devfarinsky.siegeoverhaul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidPathingHeuristicsTest extends MinecraftTestSupport {

    @Test
    void objectivePusherAggroIsReducedOutsideObjective() {
        double base = 40.0 * 40.0;
        double scaled = RaidEvents.applyObjectivePusherAggroScale(base, "breacher", false, 0.65);
        assertTrue(scaled < base);
        assertEquals(base, RaidEvents.applyObjectivePusherAggroScale(base, "breacher", true, 0.65));
        assertEquals(base, RaidEvents.applyObjectivePusherAggroScale(base, "shieldman", false, 0.65));
    }

    @Test
    void advanceSpeedAppliesBurstEscalationAndFinalApproach() {
        double speed = RaidEvents.computeAdvanceSpeed(
                1.0, 8.0 * 8.0, 32.0 * 32.0, 1.30,
                true, true, 16.0 * 16.0, 1.12);
        assertEquals(1.30 * 1.15 * 1.12, speed, 1.0e-6);
    }

    @Test
    void advanceSpeedSkipsFinalApproachBoostWhenNotObjectiveFocused() {
        double speed = RaidEvents.computeAdvanceSpeed(
                1.0, 8.0 * 8.0, 32.0 * 32.0, 1.30,
                false, false, 16.0 * 16.0, 1.12);
        assertEquals(1.30, speed, 1.0e-6);
    }
    @Test void boundedBreachScanEventuallyCoversEverySideAndHeight() {
        var origin = new net.minecraft.core.BlockPos(-12, 64, 20);
        var seen = new java.util.HashSet<net.minecraft.core.BlockPos>();
        for (int second = 0; second < 4; second++) {
            var batch = RaidEvents.breachScanPositions(origin, second * 20L, 40);
            assertEquals(40, batch.size());
            assertEquals(40, new java.util.HashSet<>(batch).size());
            seen.addAll(batch);
        }
        assertEquals(147, seen.size());
        for (var p : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-3,-1,-3), origin.offset(3,1,3)))
            assertTrue(seen.contains(p));
    }

    @Test void fullBlockBreachingOnlyUnlocksDuringClaimedCoreApproachPhase() {
        var raid = new RaidSavedData.RaidState("team:test","siege_core",0);
        var core = new net.minecraft.core.BlockPos(0,64,0);
        raid.breached=false;
        assertFalse(RaidEvents.coreApproachBreachAllowed(raid,core.east(),core));
        raid.breached=true;
        assertTrue(RaidEvents.coreApproachBreachAllowed(raid,core.offset(20,0,0),core));
        assertFalse(RaidEvents.coreApproachBreachAllowed(raid,
                core.offset(RaidEvents.CORE_APPROACH_BREACH_RADIUS+1,0,0),core));
    }

    @Test void nonCoreDefensePointNeverUnlocksMasonryFallbackAfterBreach() {
        var legacyRaid = new RaidSavedData.RaidState("team:test", "claim:12,-7", 0);
        var stronghold = new net.minecraft.core.BlockPos(0,64,0);
        legacyRaid.breached = true;

        assertFalse(RaidEvents.coreApproachBreachAllowed(
                legacyRaid, stronghold.east(), stronghold));
    }

}
