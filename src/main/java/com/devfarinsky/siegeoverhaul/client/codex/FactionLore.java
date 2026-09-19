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
                "Doctrine: a broad tidal line led by ranged scouts.",
                "Camp: prismarine halls beneath a wave-crested roof.",
                "Strategos: Tidal Advance speeds the nearby host.",
                "",
                "Counter: break sightlines and deny shoreline routes."));
        register("hollowfang_clan", List.of(
                "WARHOST OF ARES \u00b7 Bronze-Blooded",
                "Doctrine: a melee-heavy bronze spearhead.",
                "Camp: blackstone battlements under a blood-red roof.",
                "Strategos: War Cry strengthens the nearby vanguard.",
                "",
                "Counter: stagger the wedge before it reaches your gate."));
        register("emberchant_zealots", List.of(
                "FORGEGUARD OF HEPHAESTUS \u00b7 Flame-Smiths",
                "Doctrine: a supplied siege column with crossbow cover.",
                "Camp: brick forges marked by twin copper chimneys.",
                "Strategos: Forge Ward shields troops from harm and fire.",
                "",
                "Counter: isolate the engineers and destroy their works."));
        register("crownfall_exiles", List.of(
                "AEGIS ORDER OF ATHENA \u00b7 Owl-Shields",
                "Doctrine: a disciplined, captain-led shield square.",
                "Camp: a symmetrical white-and-blue acropolis.",
                "Strategos: Aegis Order hardens the phalanx.",
                "",
                "Counter: remove captains, then split the shield square."));
        register("wilds_marauders", List.of(
                "SILVER HUNT OF ARTEMIS \u00b7 Moon-Arrows",
                "Doctrine: a loose screen of archers and fast hunters.",
                "Camp: mossy shrines beneath a silver-green canopy.",
                "Strategos: Hunter's Mark exposes the Hunt's quarry.",
                "",
                "Counter: deny firing lanes and force close combat."));
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
