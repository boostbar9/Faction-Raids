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
        register("blackbay_reavers", List.of(
                "Coastal raiders. They arrive by ship, favouring beach",
                "landings over overland marches. They will pillage chests",
                "before killing you.",
                "",
                "Tactic: scuttle their ships \u2014 the crew cannot swim in",
                "heavy armour and drown outside their landing beach."));
        register("hollowfang_clan", List.of(
                "Highland warband. They hold grudges over stolen ground",
                "and will return to the same stronghold repeatedly until",
                "the grudge is settled.",
                "",
                "Tactic: killing the Faction Commander grants extra",
                "emeralds when breach was avoided \u2014 hold your walls."));
        register("emberchant_zealots", List.of(
                "Ash-Prophets. They chant during assaults, and the chant",
                "acts as a combat buff for their side. Their priests are",
                "the source of the aura.",
                "",
                "Tactic: silence the priest units first. Their reliquary",
                "drops carry rare enchantment books on victory."));
        register("crownfall_exiles", List.of(
                "Ruined nobility. They fight in banner formations and",
                "treat their captain as a monarch \u2014 kill the captain",
                "and morale shatters across the entire wave.",
                "",
                "Tactic: they drop coin more reliably than any other",
                "faction. Prioritize captured banners for extra loot."));
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
