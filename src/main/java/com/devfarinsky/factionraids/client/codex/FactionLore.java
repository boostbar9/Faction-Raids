package com.devfarinsky.factionraids.client.codex;

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
        // v2.28.0 rewrite: pre-v2.28 lore promised faction-specific mechanics
        // (drowning crews, chant auras, morale collapse) that no code path
        // ever implemented. Each faction now describes real behavior only:
        // approach direction, unit mix expectations, and tactics that work
        // with the raid systems the mod actually ships.
        register("blackbay_reavers", List.of(
                "Coastal raiders. Naval staging detection is more",
                "likely to trigger against them \u2014 expect boat spawns",
                "and beach landings when your stronghold is near open",
                "water.",
                "",
                "Tactic: shore denial. A two-block wall in the beach",
                "shallows turns their landing into a killing ground."));
        register("hollowfang_clan", List.of(
                "Highland warband. Aggressive melee mix with the",
                "standard breacher push toward gates.",
                "",
                "Tactic: v2.28.0 bonus applies here \u2014 killing the",
                "Faction Commander with your perimeter never breached",
                "pays extra emeralds. Hold your walls."));
        register("emberchant_zealots", List.of(
                "Ash-Prophets. Heavier illusioner presence on final",
                "waves \u2014 warcaster role tag more common.",
                "",
                "Tactic: melee through illusioner clones (they die on",
                "one hit) before they thin out your arrow supply. Only",
                "the real illusioner takes damage."));
        register("crownfall_exiles", List.of(
                "Ruined nobility. Captain-heavy composition \u2014",
                "more Captain-aura pulses per push than any other",
                "faction.",
                "",
                "Tactic: prioritize captains and patrol leaders on",
                "sight. Cutting the aura source is a bigger DPS swing",
                "against Crownfall than against any other faction."));
        register("wilds_marauders", List.of(
                "Generic raider fallback \u2014 no specific culture, no",
                "specific grudge. They show up when no other faction",
                "fits the tags for the current pretext.",
                "",
                "Tactic: baseline. Fight is straightforward, rewards",
                "are baseline. Good practice wave for new teams."));
    }

    private FactionLore() {}

    /**
     * Register or overwrite lore for a faction id. Safe to call from client
     * mod init hooks in downstream mods to add lore for datapack factions.
     */
    public static void register(String factionId, List<String> lines) {
        LORE.put(factionId, List.copyOf(lines));
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
