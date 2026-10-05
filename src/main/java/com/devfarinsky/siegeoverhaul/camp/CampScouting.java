package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.function.Predicate;

/** Finite active-time scouting; called once per second after the offline pause check. */
public final class CampScouting {
    static final int MAX_CANDIDATES = 200;
    static final int MAX_PASS_TICKS = 20 * 60 * 4;
    static final int RECOVERY_COOLDOWN_TICKS = 20 * 60;
    private CampScouting() {}

    public enum Result { SEARCHING, TERRAFORM, RECOVERING, WAITING, ABANDONED }

    public static Result advance(ServerLevel level, RaidSavedData.RaidState state,
                                 boolean allowTerraform, int preparationTicks) {
        if (state.campSearchAbandoned) return Result.ABANDONED;
        if (state.campSearchRetryTicks > 0) {
            state.campSearchRetryTicks = Math.max(0, state.campSearchRetryTicks - 20);
            // A cooldown consumes no site, chunk ticket or per-pass search time.
            return Result.WAITING;
        }
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
        if (allowTerraform && !state.campTerraformed && !state.campSearchRecovery) {
            state.campTerraformed = true;
            state.campSearchStep = 0;
            state.campSearchElapsedTicks = 0;
            return Result.TERRAFORM;
        }
        if (!state.campSearchRecovery) {
            state.campSearchRecovery = true;
            state.campSearchRetryTicks = RECOVERY_COOLDOWN_TICKS;
            state.campSearchStep = 0;
            state.campSearchElapsedTicks = 0;
            state.campTerraformed = allowTerraform;
            return Result.RECOVERING;
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
        if (state.campSearchPos != null || state.campSearchAbandoned || state.campSearchRetryTicks > 0) return;
        for (int skipped = 0; skipped < 8 && state.campSearchStep < MAX_CANDIDATES; skipped++) {
            int attempt = state.campSearchStep++;
            BlockPos candidate = state.campSearchRecovery
                    ? CampLoading.recoveryCandidate(core, state.approachAngle, attempt)
                    : CampLoading.candidate(core, state.approachAngle, attempt);
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
        if (state.campSearchRetryTicks > 0)
            return "Nearby camp search exhausted; wider scouting in " + (state.campSearchRetryTicks + 19) / 20 + "s";
        String progress = state.campSearchStep + "/" + MAX_CANDIDATES + " sites checked";
        String range = state.campSearchRecovery ? "farther camp " : "camp ";
        return state.campSearchPos == null ? "Searching for " + range + "land: " + progress
                : "Waiting for " + range + "terrain: " + progress;
    }

    public static String noCampStatus(RaidSavedData.RaidState state) {
        if (!state.campSearchAbandoned) return "No fortified camp established yet";
        return state.campSearchDiagnostics.exhausted() ? state.campSearchDiagnostics.summary()
                : "Camp scouting stopped; no fortified camp established";
    }
}
