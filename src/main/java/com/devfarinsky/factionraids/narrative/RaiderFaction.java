package com.devfarinsky.factionraids.narrative;

import net.minecraft.ChatFormatting;

import java.util.Collections;
import java.util.Set;

/**
 * One themed raiding faction. This is <em>who</em> is attacking — the story
 * layer above the enemy mob composition, which stays driven by
 * {@link com.devfarinsky.factionraids.RaidConfig} + Recruits.
 *
 * <p>Factions provide:
 * <ul>
 *     <li>a stable {@code id} used for persistence + config allow-lists;</li>
 *     <li>a display {@code name} and short {@code epithet} used in
 *         announcements and boss-bar labels;</li>
 *     <li>a {@link ChatFormatting} accent color that boss bar and title
 *         rendering can pick up; and</li>
 *     <li>a set of {@code casusBelliTags} — {@link CasusBelli} entries
 *         tagged with any of these are eligible for this faction.</li>
 * </ul>
 *
 * <p>The set of factions ships as code today via
 * {@link RaiderFactionRegistry}; a follow-up PR can move them to datapack
 * JSON so servers can add their own without a rebuild.
 */
public record RaiderFaction(
        String id,
        String name,
        String epithet,
        ChatFormatting accent,
        Set<String> casusBelliTags) {

    public RaiderFaction {
        casusBelliTags = casusBelliTags == null ? Set.of() : Collections.unmodifiableSet(casusBelliTags);
    }

    /** True if this faction may use a casus belli tagged with any of {@code tags}. */
    public boolean supportsAnyTag(Set<String> tags) {
        if (casusBelliTags.isEmpty()) return true; // untagged faction accepts anything
        for (String tag : tags) {
            if (casusBelliTags.contains(tag)) return true;
        }
        return false;
    }
}
