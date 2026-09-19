package com.devfarinsky.siegeoverhaul.narrative;

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
        // Persistence ids stay unchanged so existing worlds/configs remain valid.
        // Poseidon's coastal host favours naval + loot pretexts.
        m.put("blackbay_reavers", new RaiderFaction(
                "blackbay_reavers",
                "Poseidon's Tide",
                "Trident-Bearers",
                ChatFormatting.DARK_AQUA,
                Set.of("raider", "loot", "naval")));
        // Ares favours direct conquest and retaliation.
        m.put("hollowfang_clan", new RaiderFaction(
                "hollowfang_clan",
                "the Warhost of Ares",
                "Bronze-Blooded",
                ChatFormatting.DARK_RED,
                Set.of("raider", "territory", "retaliation")));
        // Hephaestus gives the siege engineers a divine forge identity.
        m.put("emberchant_zealots", new RaiderFaction(
                "emberchant_zealots",
                "the Forgeguard of Hephaestus",
                "Flame-Smiths",
                ChatFormatting.GOLD,
                Set.of("religious", "retaliation", "raider")));
        // Athena's disciplined host favours territorial and political pretexts.
        m.put("crownfall_exiles", new RaiderFaction(
                "crownfall_exiles",
                "the Aegis Order of Athena",
                "Owl-Shields",
                ChatFormatting.AQUA,
                Set.of("retaliation", "territory", "insult")));
        // Artemis' roaming hunt remains the broad fallback that accepts anything.
        m.put("wilds_marauders", new RaiderFaction(
                "wilds_marauders",
                "the Silver Hunt of Artemis",
                "Moon-Arrows",
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
