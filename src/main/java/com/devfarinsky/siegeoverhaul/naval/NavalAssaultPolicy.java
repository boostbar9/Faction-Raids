package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.EndlessSiege;
import com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity;

/** Chooses a bounded naval share without treating a coastal camp as landlocked. */
public final class NavalAssaultPolicy {
    private NavalAssaultPolicy() {}

    public static boolean available(RaidSavedData.RaidState state) {
        return state != null && state.preparationTicks <= 0
                && state.navalStagingPos != null && state.navalBeachPos != null;
    }

    public static int sharePercent(RaidSavedData.RaidState state, int configuredPercent,
                                   int finiteWaveLimit) {
        if (!available(state)) return 0;
        int share = Math.max(0, Math.min(100, configuredPercent));
        int wave = EndlessSiege.active(state) ? EndlessSiege.chapterWave(state.wave) : state.wave;
        int totalWaves = EndlessSiege.active(state) ? EndlessSiege.CHECKPOINT : Math.max(1, finiteWaveLimit);
        OlympianHostIdentity host = OlympianHostIdentity.forFaction(state.factionId);
        if (host.signatureWave(wave, totalWaves)) {
            share = Math.max(share, host.signatureAssault().navalShareFloor());
        }
        return share;
    }
}
