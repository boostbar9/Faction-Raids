package com.devfarinsky.factionraids.waves;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.formations.Formation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a {@link WaveComposition} for a given wave. The curve is deliberately
 * simple and readable — early waves are testing waves, middle waves add
 * command and long-range, late waves stage the command assault.
 *
 * <p>Reserved slots (ravager at wave-N index 1, illusioner at wave &ge;4
 * index 2, commander at final-wave index 0) stay driven by
 * {@code RaidEvents.createAttackerForWave}. This composer fills the rest of
 * the wave — the "everyone else" — and picks the formation the group should
 * hold in the field.
 *
 * <p>Formation choice sequence:
 * <ol>
 *     <li>Wave 1: LINE — teach the player what a formation looks like.</li>
 *     <li>Wave 2: LINE with wider spacing (still line).</li>
 *     <li>Wave 3+: SQUARE — deeper formation for the harder waves.</li>
 *     <li>Final wave: SQUARE — command assault packs in.</li>
 * </ol>
 */
public final class WaveComposer {

    private WaveComposer() {}

    /**
     * @param wave         current wave number, 1-indexed
     * @param totalWaves   total waves in this raid
     * @param total        how many attackers to compose in this wave
     * @return composition; guaranteed non-null even for wave == 0 pre-raid
     */
    public static WaveComposition compose(int wave, int totalWaves, int total) {
        if (!RaidConfig.ENABLE_WAVE_COMPOSITION.get() || total <= 0) {
            return new WaveComposition(total, Map.of(), Formation.NONE, "");
        }

        // Reserve slots the RaidEvents fast-path claims. If those types don't
        // exist we still allocate the slot — RaidEvents will just spawn a
        // generic filler in their place.
        int reserved = 0;
        boolean finalWave = wave >= totalWaves;
        if (finalWave) reserved += RaidConfig.ENABLE_COMMANDER.get() ? 1 : 0; // commander
        if (finalWave) reserved += 1; // ravager
        if (wave >= 4 && RaidConfig.ENABLE_ILLUSIONERS.get()) reserved += 1; // illusioner
        int available = Math.max(0, total - reserved);

        Map<String, Integer> mix = new LinkedHashMap<>();
        Formation formation;
        String label;

        if (wave == 1) {
            // Probing wave: mostly shieldmen with a handful of bowmen. LINE.
            int bowmen = Math.max(1, available / 4);
            int shieldmen = available - bowmen;
            put(mix, "recruit_shieldman", shieldmen);
            put(mix, "bowman", bowmen);
            formation = Formation.LINE;
            label = "Shield line";
        } else if (wave == 2) {
            // Add crossbowmen and one captain to give the wave a commander.
            int captains = Math.min(1, available);
            int rest = available - captains;
            int crossbows = rest / 4;
            int bowmen = rest / 4;
            int shieldmen = rest - crossbows - bowmen;
            put(mix, "captain", captains);
            put(mix, "recruit_shieldman", shieldmen);
            put(mix, "crossbowman", crossbows);
            put(mix, "bowman", bowmen);
            formation = Formation.LINE;
            label = "Skirmish line";
        } else if (wave == 3) {
            // Introduce siege engineers (if the mod has them) and go SQUARE.
            int engineers = registryHas("recruits:siege_engineer") ? Math.min(2, available / 5) : 0;
            int captains = Math.min(1, available - engineers);
            int rest = available - captains - engineers;
            int assassins = rest / 8;
            int crossbows = rest / 3;
            int shieldmen = rest - assassins - crossbows;
            put(mix, "captain", captains);
            if (engineers > 0) put(mix, "siege_engineer", engineers);
            put(mix, "recruit_shieldman", shieldmen);
            put(mix, "crossbowman", crossbows);
            put(mix, "assassin", assassins);
            formation = Formation.SQUARE;
            label = "Siege square";
        } else if (finalWave) {
            // Command assault: engineers + assassins + heavy shieldmen.
            int engineers = registryHas("recruits:siege_engineer") ? Math.min(3, Math.max(1, available / 5)) : 0;
            int assassins = Math.max(1, available / 6);
            int rest = available - engineers - assassins;
            int crossbows = rest / 3;
            int shieldmen = rest - crossbows;
            if (engineers > 0) put(mix, "siege_engineer", engineers);
            put(mix, "assassin", assassins);
            put(mix, "recruit_shieldman", shieldmen);
            put(mix, "crossbowman", crossbows);
            formation = Formation.SQUARE;
            label = "Command assault";
        } else {
            // Wave 4..N-1: heavier general-purpose wave.
            int captains = Math.min(2, Math.max(1, available / 8));
            int engineers = registryHas("recruits:siege_engineer") ? Math.min(2, available / 6) : 0;
            int rest = available - captains - engineers;
            int assassins = rest / 8;
            int crossbows = rest / 3;
            int shieldmen = rest - assassins - crossbows;
            put(mix, "captain", captains);
            if (engineers > 0) put(mix, "siege_engineer", engineers);
            put(mix, "recruit_shieldman", shieldmen);
            put(mix, "crossbowman", crossbows);
            put(mix, "assassin", assassins);
            formation = Formation.SQUARE;
            label = "Storm formation";
        }

        return new WaveComposition(total, mix, formation, label);
    }

    private static void put(Map<String, Integer> mix, String key, int count) {
        if (count > 0) mix.merge(key, count, Integer::sum);
    }

    private static boolean registryHas(String id) {
        try {
            return ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(id));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
