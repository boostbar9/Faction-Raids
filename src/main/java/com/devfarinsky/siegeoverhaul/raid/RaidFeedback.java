package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.RaidSavedData;

/** Read-only player-facing labels. An ended siege is not necessarily a defeat. */
public final class RaidFeedback {
    private RaidFeedback() {}

    public static String outcomeTitle(boolean victory) {
        return victory ? "Siege Broken" : "Siege Ended";
    }

    public static String outcomeSubtitle(boolean victory) {
        return victory ? "Your faction held the line" : "See chat for the siege outcome";
    }

    public static String preparation(RaidSavedData.RaidState state) {
        if (state.campPos == null) return "Preparing assault";
        int third = Math.max(1, state.preparationTotalTicks / 3);
        return state.preparationTicks > third * 2 ? "Establishing camp"
                : state.preparationTicks > third ? "Fortifying camp" : "Mustering army";
    }

    public static String phase(RaidSavedData.RaidState state, boolean paused,
                               boolean campsEnabled, boolean breachEnabled) {
        if (paused) return "Paused";
        if (state.coreCaptured) return "Reclaim core";
        if (campsEnabled && state.campPos == null && !state.campSearchAbandoned)
            return state.campSearchPos == null ? "Searching for camp land" : "Waiting for camp terrain";
        if (state.preparationTicks > 0)
            return preparation(state) + " • " + (state.preparationTicks + 1199) / 1200 + "m preparation left";
        if ("siege_core".equals(state.defensePointName) && state.wave > 0) return "Defend core";
        if (state.wave == 0) return "Rally";
        if (!state.breached && breachEnabled) return "Breach";
        if (state.captureTicks > 0) return "Occupation";
        return "March";
    }

    public static String codexDelivery(boolean stored, boolean dropped) {
        return stored ? "Warlord's Codex added to your inventory."
                : dropped ? "Your inventory is full. Your Warlord's Codex was dropped at your feet."
                : "The Codex could not be delivered. Make room in your inventory and try again in 60 seconds.";
    }
}
