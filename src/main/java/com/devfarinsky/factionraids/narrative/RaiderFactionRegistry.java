package com.devfarinsky.factionraids.narrative;

import net.minecraft.ChatFormatting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in raider faction pool. Small, hand-authored, and biased toward
 * feeling like "someone specific is showing up" rather than "generic raid."
 *
 * <p>All entries are exposed via {@link #all()} as an unmodifiable ordered
 * map, which keeps iteration deterministic in tests. Selection lives in
 * {@link RaidNarrativeSelector}.
 */
public final class RaiderFactionRegistry {

    private static final Map<String, RaiderFaction> FACTIONS;

    static {
        Map<String, RaiderFaction> m = new LinkedHashMap<>();
        // Coastal raiders — favour naval + loot pretexts.
        m.put("blackbay_reavers", new RaiderFaction(
                "blackbay_reavers",
                "the Blackbay Reavers",
                "Ship-Wolves",
                ChatFormatting.DARK_AQUA,
                Set.of("raider", "loot", "naval")));
        // Highland warband — territorial pretexts.
        m.put("hollowfang_clan", new RaiderFaction(
                "hollowfang_clan",
                "the Hollowfang Clan",
                "Stone-Reapers",
                ChatFormatting.DARK_RED,
                Set.of("raider", "territory", "retaliation")));
        // Zealots — creed pretexts and taunts about relics/omens.
        m.put("emberchant_zealots", new RaiderFaction(
                "emberchant_zealots",
                "the Emberchant Zealots",
                "Ash-Prophets",
                ChatFormatting.GOLD,
                Set.of("religious", "retaliation", "raider")));
        // Ruined nobility — old grievances, taxes, insults.
        m.put("crownfall_exiles", new RaiderFaction(
                "crownfall_exiles",
                "the Crownfall Exiles",
                "Lost-Banners",
                ChatFormatting.LIGHT_PURPLE,
                Set.of("retaliation", "territory", "insult")));
        // Generic marauders — the fallback that accepts anything.
        m.put("wilds_marauders", new RaiderFaction(
                "wilds_marauders",
                "the Wilds Marauders",
                "Green-Blades",
                ChatFormatting.DARK_GREEN,
                Set.of()));
        FACTIONS = Collections.unmodifiableMap(m);
    }

    private RaiderFactionRegistry() {}

    public static Map<String, RaiderFaction> all() { return FACTIONS; }

    public static RaiderFaction get(String id) { return FACTIONS.get(id); }

    /** Every faction id in canonical order. Used by config defaults. */
    public static List<String> allIds() { return List.copyOf(FACTIONS.keySet()); }
}
