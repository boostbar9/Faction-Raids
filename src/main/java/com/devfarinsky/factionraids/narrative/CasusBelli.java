package com.devfarinsky.factionraids.narrative;

import java.util.Collections;
import java.util.Set;

/**
 * A "reason for war" — the narrative pretext under which a raid happens.
 *
 * <p>Each casus belli carries:
 * <ul>
 *     <li>{@code id} — stable identifier, used for persistence and history-driven
 *         selection in the future {@code RaidLedger}.</li>
 *     <li>{@code weight} — relative frequency when the registry rolls a random
 *         casus belli. Higher = more common.</li>
 *     <li>{@code tags} — free-form category tags (e.g. {@code raider},
 *         {@code religious}, {@code retaliation}). {@link RaiderFaction}
 *         entries match against these to gate what themes fit them.</li>
 *     <li>{@code opening} / {@code chant} / {@code victoryTaunt} —
 *         placeholder-templated flavor strings. Supported tokens:
 *         {@code {faction}}, {@code {epithet}}, {@code {defender}},
 *         {@code {point}}.</li>
 * </ul>
 *
 * <p>Templates are plain text. Rendering happens at raid start; the resulting
 * strings live on {@link RaidNarrative} and are what the announcement / boss
 * bar reads.
 */
public record CasusBelli(
        String id,
        int weight,
        Set<String> tags,
        String opening,
        String chant,
        String victoryTaunt) {

    public CasusBelli {
        tags = tags == null ? Set.of() : Collections.unmodifiableSet(tags);
        if (weight < 1) weight = 1;
    }
}
