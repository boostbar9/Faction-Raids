package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.function.Predicate;

/** Finite active-time scouting; called once per second after the offline pause check. */
public final class CampScouting {
    static final int MAX_CANDIDATES = 200;
    static final int MAX_PASS_TICKS = 20 * 60 * 4;
    private CampScouting() {}

    public enum Result { SEARCHING, TERRAFORM, ABANDONED }

    public static Result advance(ServerLevel level, RaidSavedData.RaidState state,
                                 boolean allowTerraform, int preparationTicks) {
        if (state.campSearchAbandoned) return Result.ABANDONED;
        state.campSearchElapsedTicks = (int) Math.min(MAX_PASS_TICKS,
                (long) state.campSearchElapsedTicks + 20);
        // The final candidate may still be loading. Do not discard it just because
        // selecting it consumed slot 200. At the deadline, give already-loaded
        // terrain its site check; the caller then clears the candidate or establishes
        // the camp. This grants no new candidate and no extra chunk-loading wait.
        if (state.campSearchElapsedTicks < MAX_PASS_TICKS
                && (state.campSearchStep < MAX_CANDIDATES || state.campSearchPos != null))
            return Result.SEARCHING;
        if (state.campSearchPos != null && CampLoading.ready(level, state.campSearchPos))
            return Result.SEARCHING;

        if (state.campSearchPos != null) {
            state.campSearchDiagnostics.record(CampSearchDiagnostics.Reason.UNLOADED);
            CampLoading.release(level, state.campSearchPos);
        }
        state.campSearchPos = null;
        state.campSearchTicks = 0;
        if (allowTerraform && !state.campTerraformed) {
            state.campTerraformed = true;
            state.campSearchStep = 0;
            state.campSearchElapsedTicks = 0;
            return Result.TERRAFORM;
        }
        state.campSearchAbandoned = true;
        state.campSearchDiagnostics.finish();
        state.campBuildAttempted = true;
        state.preparationTotalTicks = preparationTicks;
        state.preparationTicks = preparationTicks;
        state.ticksToNextWave = preparationTicks;
        return Result.ABANDONED;
    }

    public static void selectCandidate(RaidSavedData.RaidState state, BlockPos core,
                                       Predicate<BlockPos> canClaim) {
        if (state.campSearchPos != null || state.campSearchAbandoned) return;
        for (int skipped = 0; skipped < 8 && state.campSearchStep < MAX_CANDIDATES; skipped++) {
            BlockPos candidate = CampLoading.candidate(core, state.approachAngle, state.campSearchStep++);
            if (canClaim.test(candidate)) {
                state.campSearchPos = candidate;
                break;
            }
            state.campSearchDiagnostics.record(CampSearchDiagnostics.Reason.CLAIM_SAFETY);
        }
    }

    // Chat-only memory. Weak keys follow the active raid lifetime and never enter saved gameplay state.
    private static final java.util.Map<RaidSavedData.RaidState, String> SEARCH_NOTICES = new java.util.WeakHashMap<>();

    /** Keep changed reasons/progress visible, without repeating an identical stalled notice every 30 seconds. */
    public static synchronized boolean shouldAnnounceSearch(RaidSavedData.RaidState state, String status) {
        return !java.util.Objects.equals(SEARCH_NOTICES.put(state, status), status);
    }

    public static String searchStatus(RaidSavedData.RaidState state, String unavailableReason) {
        if (unavailableReason != null && !unavailableReason.isBlank())
            return "Camp search unavailable: " + unavailableReason;
        String progress = state.campSearchStep + "/" + MAX_CANDIDATES + " sites checked";
        return state.campSearchPos == null ? "Searching for camp land: " + progress
                : "Waiting for camp terrain: " + progress;
    }

    public static String noCampStatus(RaidSavedData.RaidState state) {
        if (!state.campSearchAbandoned) return "No fortified camp established yet";
        return state.campSearchDiagnostics.exhausted() ? state.campSearchDiagnostics.summary()
                : "Camp scouting stopped; no fortified camp established";
    }
}
