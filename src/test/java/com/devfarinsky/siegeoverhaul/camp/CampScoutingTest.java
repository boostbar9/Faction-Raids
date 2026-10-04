package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampScoutingTest extends MinecraftTestSupport {
    private RaidSavedData.RaidState raid() {
        return new RaidSavedData.RaidState("team:test", "siege_core", 0);
    }

    @Test void finalCandidateGetsItsLoadingAndSiteCheckBeforePassEnds() {
        var state = raid();
        state.campSearchStep = 199;
        CampScouting.selectCandidate(state, BlockPos.ZERO, pos -> true);
        assertEquals(200, state.campSearchStep);
        BlockPos finalSite = state.campSearchPos;
        assertNotNull(finalSite);
        for (int second = 0; second < 60; second++)
            assertEquals(CampScouting.Result.SEARCHING, CampScouting.advance(null, state, true, 3600));
        assertEquals(finalSite, state.campSearchPos);
        // The caller has now checked/rejected the loaded site (or timed out its load).
        state.campSearchPos = null;
        assertEquals(CampScouting.Result.TERRAFORM, CampScouting.advance(null, state, true, 3600));
        assertEquals(0, state.campSearchStep);
        assertEquals(0, state.campSearchElapsedTicks);
    }

    @Test void unclaimableCandidatesStopAtTheBudgetAndKeepPerTickWorkBounded() {
        var state = raid();
        AtomicInteger checks = new AtomicInteger();
        CampScouting.selectCandidate(state, BlockPos.ZERO, pos -> { checks.incrementAndGet(); return false; });
        assertEquals(8, checks.get());
        state.campSearchStep = 197;
        checks.set(0);
        CampScouting.selectCandidate(state, BlockPos.ZERO, pos -> { checks.incrementAndGet(); return false; });
        assertEquals(3, checks.get());
        assertEquals(200, state.campSearchStep);
        assertNull(state.campSearchPos);
        CampScouting.selectCandidate(state, BlockPos.ZERO, pos -> { fail("Exceeded search budget"); return true; });
    }

    @Test void stalledLoadingReleasesTicketsAndFallsBackWithinTwoTimedPasses() {
        var state = raid();
        ServerLevel level = mock(ServerLevel.class);
        BlockPos stalled = new BlockPos(168, 64, 8);
        try (var loading = mockStatic(CampLoading.class)) {
            for (int pass = 0; pass < 2; pass++) {
                state.campSearchPos = stalled;
                state.campSearchStep = 1;
                state.campSearchTicks = 120;
                for (int tick = 20; tick < CampScouting.MAX_PASS_TICKS; tick += 20)
                    assertEquals(CampScouting.Result.SEARCHING, CampScouting.advance(level, state, true, 3600));
                assertEquals(pass == 0 ? CampScouting.Result.TERRAFORM : CampScouting.Result.ABANDONED,
                        CampScouting.advance(level, state, true, 3600));
                assertNull(state.campSearchPos);
                assertEquals(0, state.campSearchTicks);
            }
            loading.verify(() -> CampLoading.release(level, stalled), times(2));
        }
        assertTrue(state.campSearchAbandoned);
        assertTrue(state.campBuildAttempted);
        assertEquals(3600, state.preparationTotalTicks);
        assertEquals(3600, state.preparationTicks);
        assertEquals(3600, state.ticksToNextWave);
        assertNull(state.campPos);
        assertNull(state.campClaimId);
        assertTrue(state.campSearchDiagnostics.exhausted());
        assertEquals(2, state.campSearchDiagnostics.save().getInt("UNLOADED"));
        assertTrue(CampScouting.noCampStatus(state).contains("terrain was not loaded in time"));
    }

    @Test void disabledTerraformFallsBackOnceWithAFullPreparationPeriod() {
        var state = raid();
        state.campSearchStep = 200;
        assertEquals(CampScouting.Result.ABANDONED, CampScouting.advance(null, state, false, 2400));
        assertFalse(state.campTerraformed);
        assertEquals(2400, state.preparationTicks);
        state.preparationTicks = 2380;
        CampScouting.advance(null, state, false, 2400);
        assertEquals(2380, state.preparationTicks);
        CampScouting.selectCandidate(state, BlockPos.ZERO, pos -> { fail("Abandoned search restarted"); return true; });
    }

    @Test void reloadKeepsDeadlineAndLegacySavesStartWithFreshTimeBudget() {
        var state = raid();
        state.campTerraformed = true;
        state.campSearchElapsedTicks = CampScouting.MAX_PASS_TICKS - 20;
        var loaded = RaidSavedData.RaidState.load(state.save());
        assertEquals(state.campSearchElapsedTicks, loaded.campSearchElapsedTicks);
        assertEquals(CampScouting.Result.ABANDONED, CampScouting.advance(null, loaded, true, 3600));
        CompoundTag old = state.save();
        old.remove("CampSearchElapsedTicks");
        assertEquals(0, RaidSavedData.RaidState.load(old).campSearchElapsedTicks);
        old.putInt("CampSearchElapsedTicks", -100);
        assertEquals(0, RaidSavedData.RaidState.load(old).campSearchElapsedTicks);
    }

    @Test void readyFinalCandidateIsCheckedEvenWhenTheDeadlineArrives() {
        var state = raid();
        ServerLevel level = mock(ServerLevel.class);
        BlockPos finalSite = new BlockPos(168, 64, 8);
        state.campSearchPos = finalSite;
        state.campSearchStep = 200;
        state.campSearchElapsedTicks = CampScouting.MAX_PASS_TICKS - 20;
        try (var loading = mockStatic(CampLoading.class)) {
            loading.when(() -> CampLoading.ready(level, finalSite)).thenReturn(true);
            assertEquals(CampScouting.Result.SEARCHING, CampScouting.advance(level, state, true, 3600));
            assertEquals(finalSite, state.campSearchPos);
            loading.verify(() -> CampLoading.release(level, finalSite), never());
            CampScouting.selectCandidate(state, BlockPos.ZERO, pos -> { fail("Selected a new site after deadline"); return true; });
            // Production checks the ready site and clears it on rejection.
            state.campSearchPos = null;
            assertEquals(CampScouting.Result.TERRAFORM, CampScouting.advance(level, state, true, 3600));
        }
    }

    @Test void corruptLargeElapsedValueCannotOverflowIntoAnotherLongSearch() {
        var state = raid();
        state.campSearchElapsedTicks = Integer.MAX_VALUE;
        assertEquals(CampScouting.Result.ABANDONED, CampScouting.advance(null, state, false, 3600));
    }

    @Test void feedbackSeparatesActiveSearchLoadingAndUnavailableClaims() {
        var state = raid();
        state.campSearchStep = 8;
        assertEquals("Searching for camp land: 8/200 sites checked", CampScouting.searchStatus(state, ""));
        state.campSearchPos = new BlockPos(160, 64, 0);
        assertEquals("Waiting for camp terrain: 8/200 sites checked", CampScouting.searchStatus(state, ""));
        String reason = "Recruits AllowClaiming is disabled";
        var before = state.save();
        assertEquals("Camp search unavailable: " + reason, CampScouting.searchStatus(state, reason));
        assertEquals(before, state.save(), "Feedback must not change the search or its safety checks");
    }

    @org.junit.jupiter.api.Test void identicalSearchChatIsQuietButChangedReasonsAndOtherRaidsRemainVisible() {
        var first = new com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState("team:first", "siege_core", 0);
        var second = new com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState("team:second", "siege_core", 0);
        var before = first.save();
        org.junit.jupiter.api.Assertions.assertTrue(CampScouting.shouldAnnounceSearch(first, "Waiting for camp terrain: 12/200 sites checked"));
        org.junit.jupiter.api.Assertions.assertFalse(CampScouting.shouldAnnounceSearch(first, "Waiting for camp terrain: 12/200 sites checked"));
        org.junit.jupiter.api.Assertions.assertTrue(CampScouting.shouldAnnounceSearch(first, "Camp search unavailable: claim service unavailable"));
        org.junit.jupiter.api.Assertions.assertTrue(CampScouting.shouldAnnounceSearch(second, "Waiting for camp terrain: 12/200 sites checked"));
        org.junit.jupiter.api.Assertions.assertEquals(before, first.save());
    }

}
