package com.devfarinsky.factionraids.waves;

import com.devfarinsky.factionraids.formations.Formation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable plan for one wave: how many of each Recruits role to spawn, and
 * which formation the survivors should hold while advancing.
 *
 * <p>Consumed by {@link WaveComposer#compose} (production) and by
 * {@link com.devfarinsky.factionraids.RaidEvents#createAttackerForWave}-style
 * code paths that ask "what should I spawn as index N of this wave?".
 *
 * <p>The role map is a small ordered map keyed by Recruits registry path
 * (e.g. {@code shieldman}, {@code bowman}, {@code crossbowman},
 * {@code captain}, {@code assassin}, {@code patrol_leader},
 * {@code siege_engineer}). Reserved slots (commander, ravager, illusioner)
 * are still handled by the RaidEvents fast-path; the composition covers the
 * "everyone else" pool.
 */
public final class WaveComposition {

    public final int total;
    public final Map<String, Integer> roleCounts;
    public final Formation formation;
    /** Human-readable label used in the wave announcement, e.g. "Shield line". */
    public final String label;

    public WaveComposition(int total, Map<String, Integer> roleCounts,
                           Formation formation, String label) {
        this.total = Math.max(0, total);
        this.roleCounts = roleCounts == null ? Map.of() :
                Collections.unmodifiableMap(new LinkedHashMap<>(roleCounts));
        this.formation = formation == null ? Formation.NONE : formation;
        this.label = label == null ? "" : label;
    }

    /**
     * Resolve the role at a given wave index. Iterates the ordered role map
     * so higher-priority roles (captains, engineers) fill first, then the
     * bulk of shieldmen and bowmen. Returns null when the index is past the
     * composition (caller falls back to a default).
     */
    public String roleAt(int index) {
        int cursor = 0;
        for (Map.Entry<String, Integer> entry : roleCounts.entrySet()) {
            cursor += entry.getValue();
            if (index < cursor) return entry.getKey();
        }
        return null;
    }
}
