package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static com.devfarinsky.siegeoverhaul.camp.CampSearchDiagnostics.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class CampSearchDiagnosticsTest extends MinecraftTestSupport {
    @Test void bothPassesRetainObservedReasonsAcrossReloadWithoutClaimingGlobalImpossibility() {
        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        state.campSearchDiagnostics.record(SURFACE, 9);
        state.campSearchStep = CampScouting.MAX_CANDIDATES;
        assertEquals(CampScouting.Result.TERRAFORM, CampScouting.advance(null, state, true, 3600));
        assertFalse(state.campSearchDiagnostics.exhausted());
        assertFalse(CampScouting.noCampStatus(state).contains("Observed blockers"));

        var loaded = RaidSavedData.RaidState.load(state.save());
        loaded.campSearchDiagnostics.recordTerrain(CampTerrain.Rejection.NO_LAND_EXIT, 25);
        loaded.campSearchStep = CampScouting.MAX_CANDIDATES;
        assertEquals(CampScouting.Result.RECOVERING, CampScouting.advance(null, loaded, true, 3600));
        assertFalse(loaded.campSearchDiagnostics.exhausted());
        for (int second = 0; second < 60; second++) CampScouting.advance(null, loaded, true, 3600);
        loaded.campSearchStep = CampScouting.MAX_CANDIDATES;
        assertEquals(CampScouting.Result.ABANDONED, CampScouting.advance(null, loaded, true, 3600));
        String message = CampScouting.noCampStatus(loaded);
        assertEquals("No safe camp site found within the search limits. Observed blockers: "
                + "no safe dry land exit was found; surface suitability checks failed.", message);
        assertEquals(message, CampScouting.noCampStatus(RaidSavedData.RaidState.load(loaded.save())));
        assertNull(loaded.campPos);
        assertNull(loaded.campClaimId);
        assertEquals(3600, loaded.preparationTicks);
    }

    @Test void rejectedOuterCandidatesContributeOnlyAnAnonymousSafetyCategory() {
        var state = new RaidSavedData.RaidState("private-team", "private-core", 0);
        CampScouting.selectCandidate(state, new BlockPos(123456, 71, -987654), pos -> false);
        assertEquals(8, state.campSearchDiagnostics.save().getInt(CLAIM_SAFETY.name()));
        state.campSearchStep = CampScouting.MAX_CANDIDATES;
        CampScouting.advance(null, state, false, 3600);
        for (int second = 0; second < 60; second++) CampScouting.advance(null, state, false, 3600);
        state.campSearchStep = CampScouting.MAX_CANDIDATES;
        CampScouting.advance(null, state, false, 3600);
        String message = CampScouting.noCampStatus(state);
        assertTrue(message.contains("claim or world-border safety checks failed"));
        assertFalse(message.contains("private"));
        assertFalse(message.contains("123456"));
        assertFalse(message.contains("987654"));
    }

    @Test void allTerrainRejectionCategoriesHaveAPlayerSafeMapping() {
        for (var rejection : CampTerrain.Rejection.values()) {
            var diagnostics = new CampSearchDiagnostics();
            diagnostics.recordTerrain(rejection, 1);
            assertTrue(diagnostics.summary().contains("Observed blockers:"), rejection.name());
            assertFalse(diagnostics.summary().contains("No detailed blocker"), rejection.name());
        }
        var diagnostics = new CampSearchDiagnostics();
        diagnostics.recordTerrain(CampTerrain.Rejection.BLOCK_ENTITY, 2);
        diagnostics.recordTerrain(CampTerrain.Rejection.CLEARANCE, 3);
        assertEquals(5, diagnostics.save().getInt(PROTECTED_BLOCKS.name()));
    }

    @Test void outputIsLimitedToThreeDeterministicMostObservedReasons() {
        var diagnostics = new CampSearchDiagnostics();
        for (var reason : CampSearchDiagnostics.Reason.values()) diagnostics.record(reason, 2);
        diagnostics.record(NO_LAND_EXIT, 100);
        assertEquals("No safe camp site found within the search limits. Observed blockers: "
                + "no safe dry land exit was found; camp claiming was unavailable; "
                + "claim or world-border safety checks failed.", diagnostics.summary());
        assertTrue(diagnostics.summary().length() < 300);
    }

    @Test void malformedNbtCannotInjectMessagesOrUnboundCountersAndDoesNotAlias() {
        var tag = new CompoundTag();
        tag.putInt(SURFACE.name(), Integer.MAX_VALUE);
        tag.putInt(FLUID.name(), -1);
        tag.putString(CLAIM_SETUP.name(), "private owner at 123,64,456");
        tag.putString("UnknownError", "secret exception message");
        var diagnostics = new CampSearchDiagnostics();
        diagnostics.load(tag);
        tag.putInt(SURFACE.name(), 1);
        diagnostics.record(SURFACE, Integer.MAX_VALUE);
        diagnostics.record(FLUID, Integer.MIN_VALUE);
        var saved = diagnostics.save();
        assertEquals(10_000, saved.getInt(SURFACE.name()));
        assertFalse(saved.contains(FLUID.name()));
        assertFalse(saved.contains(CLAIM_SETUP.name()));
        assertFalse(saved.contains("UnknownError"));
        saved.putInt(SURFACE.name(), 0);
        assertTrue(diagnostics.summary().contains("surface suitability checks failed"));
        assertFalse(diagnostics.summary().contains("private"));
        assertFalse(diagnostics.summary().contains("secret"));
    }

    @Test void exhaustedEvidenceRemainsStableAndLegacyOrManualStopsAreNotReportedAsExhaustion() {
        var diagnostics = new CampSearchDiagnostics();
        diagnostics.record(CLAIM_CREATE);
        diagnostics.finish();
        String message = diagnostics.summary();
        diagnostics.record(FLUID, 100);
        assertEquals(message, diagnostics.summary());

        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        state.campSearchAbandoned = true;
        state.campSearchDiagnostics.record(SURFACE);
        assertEquals("Camp scouting stopped; no fortified camp established", CampScouting.noCampStatus(state));
        CompoundTag old = state.save();
        old.remove("CampSearchDiagnostics");
        assertEquals("Camp scouting stopped; no fortified camp established",
                CampScouting.noCampStatus(RaidSavedData.RaidState.load(old)));
        assertEquals("No safe camp site found within the search limits. No detailed blocker was recorded.",
                new CampSearchDiagnostics().summary());
    }
}
