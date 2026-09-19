package com.devfarinsky.siegeoverhaul.client.codex;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side lore registry for raider factions. Keyed by the same faction
 * id used by {@code RaiderFactionRegistry} so the codex can look up lore
 * for whichever factions are actually present at runtime.
 *
 * <p>This was extracted out of a hardcoded switch in SiegeCommandScreen in
 * v2.12.0 so datapacks that add new factions can drop a matching lore entry
 * in via {@link #register(String, List)} at client mod init. Missing ids
 * fall back to a graceful placeholder rather than "No additional lore recorded."
 *
 * <p>Lore is stored as a list of paragraph lines. Each line renders as one
 * row in the faction detail pane; keep individual lines under ~65 characters
 * or they will get trimmed by the panel.
 */
@OnlyIn(Dist.CLIENT)
public final class FactionLore {

    private static final Map<String, List<String>> LORE = new LinkedHashMap<>();

    static {
        // Stable legacy ids now resolve to the five Olympian war hosts. Keep
        // every tactical promise tied to behavior the mod actually ships.
        register("blackbay_reavers", List.of(
                "POSEIDON'S TIDE \u00b7 Trident-Bearers",
                "A sea-born war host. Naval staging is more likely",
                "against them \u2014 expect ships and beach landings when",
                "your stronghold stands near open water.",
                "",
                "Tactic: shore denial. A two-block wall in the beach",
                "shallows turns their landing into a killing ground."));
        register("hollowfang_clan", List.of(
                "WARHOST OF ARES \u00b7 Bronze-Blooded",
                "An aggressive melee host that drives its breachers",
                "straight toward gates and weak points.",
                "",
                "Tactic: killing its Strategos while your perimeter",
                "remains unbreached pays extra emeralds. Hold the wall."));
        register("emberchant_zealots", List.of(
                "FORGEGUARD OF HEPHAESTUS \u00b7 Flame-Smiths",
                "A siege-minded host with a heavier warcaster presence",
                "during final waves.",
                "",
                "Tactic: melee through illusioner clones (they die on",
                "one hit) before they thin out your arrow supply. Only",
                "the real illusioner takes damage."));
        register("crownfall_exiles", List.of(
                "AEGIS ORDER OF ATHENA \u00b7 Owl-Shields",
                "A disciplined, captain-heavy formation with more",
                "command-aura pulses per push than any other host.",
                "",
                "Tactic: prioritize captains and patrol leaders on",
                "sight. Breaking their command structure is the fastest",
                "way to unravel Athena's formation."));
        register("wilds_marauders", List.of(
                "SILVER HUNT OF ARTEMIS \u00b7 Moon-Arrows",
                "A roaming host of hunters and skirmishers. Their broad",
                "mandate lets them answer any omen or war pretext.",
                "",
                "Tactic: deny clear firing lanes and force the Hunt into",
                "the close quarters beneath your walls."));
    }

    private FactionLore() {}

    /**
     * Register or overwrite lore for a faction id. Safe to call from client
     * mod init hooks in downstream mods to add lore for datapack factions.
     */
    public static void register(String factionId, List<String> lines) {
        LORE.put(factionId, List.copyOf(lines));
    }

    /** Snapshot of the whole registry in insertion order, for the Intel HUD. */
    public static Map<String, List<String>> all() {
        return java.util.Collections.unmodifiableMap(LORE);
    }

    public static List<String> get(String factionId) {
        List<String> lines = LORE.get(factionId);
        if (lines != null) return lines;
        return List.of(
                "No lore recorded for this faction.",
                "Datapack authors: register lore via",
                "FactionLore.register(\"" + factionId + "\", ...) at",
                "client mod init.");
    }
}
