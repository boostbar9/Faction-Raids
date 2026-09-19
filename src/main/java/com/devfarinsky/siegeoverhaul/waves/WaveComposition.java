package com.devfarinsky.siegeoverhaul.waves;

import com.devfarinsky.siegeoverhaul.formations.Formation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable plan for one wave: how many of each Recruits role to spawn, and
 * which formation the survivors should hold while advancing.
 *
 * <p>Consumed by {@link WaveComposer#compose} (production) and by
 * {@link com.devfarinsky.siegeoverhaul.RaidEvents#createAttackerForWave}-style
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
    /** Exact spawn order; repeated roles stay in their configured positions. */
    public final List<String> roleSlots;
    public final Formation formation;
    /** Human-readable label used in the wave announcement, e.g. "Shield line". */
    public final String label;

    public WaveComposition(int total, Map<String, Integer> roleCounts,
                           Formation formation, String label) {
        this(total, expand(roleCounts), roleCounts, formation, label);
    }

    public WaveComposition(int total, List<String> roleSlots, Map<String, Integer> roleCounts,
                           Formation formation, String label) {
        this.total = Math.max(0, total);
        this.roleCounts = roleCounts == null ? Map.of() :
                Collections.unmodifiableMap(new LinkedHashMap<>(roleCounts));
        this.roleSlots = roleSlots == null ? List.of() : List.copyOf(roleSlots);
        this.formation = formation == null ? Formation.NONE : formation;
        this.label = label == null ? "" : label;
    }

    private static List<String> expand(Map<String, Integer> roleCounts) {
        if(roleCounts==null || roleCounts.isEmpty())return List.of();
        java.util.ArrayList<String> roles=new java.util.ArrayList<>();
        for(var entry:roleCounts.entrySet())
            for(int i=0;i<Math.max(0,entry.getValue());i++)roles.add(entry.getKey());
        return roles;
    }

    /**
     * Resolve the role at a given composition index. The explicit slot list
     * preserves interleaved or repeated doctrine entries; the count map is
     * retained for summaries and tests. Returns null past the composition.
     */
    public String roleAt(int index) {
        return index<0 || index>=roleSlots.size()?null:roleSlots.get(index);
    }
}
