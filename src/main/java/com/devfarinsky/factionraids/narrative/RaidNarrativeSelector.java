package com.devfarinsky.factionraids.narrative;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.RaidConfig;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Selects a {@link RaiderFaction} and {@link CasusBelli} for a new raid.
 *
 * <p>Current strategy is purely random with weight bias, gated by config
 * allow-lists so an operator can disable factions or themes they don't want.
 * Every random pick honours the faction ↔ casus-belli tag compatibility
 * rules on {@link RaiderFaction#supportsAnyTag(Set)}.
 *
 * <p>PR #8 in the roadmap replaces the random pretext branch with a
 * ledger-driven selection so retaliation shows up when there actually is
 * something to retaliate for. The public surface here is deliberately narrow
 * so that upgrade is a drop-in change.
 */
public final class RaidNarrativeSelector {

    private RaidNarrativeSelector() {}

    /** Pick a narrative for a defender + defense point. Never returns null. */
    public static RaidNarrative select(RandomSource random, String defenderDisplay, String pointName) {
        if (!RaidConfig.ENABLE_NARRATIVE.get()) return neutralFallback(defenderDisplay, pointName);

        List<RaiderFaction> factions = allowedFactions();
        if (factions.isEmpty()) return neutralFallback(defenderDisplay, pointName);
        RaiderFaction faction = factions.get(random.nextInt(factions.size()));

        List<CasusBelli> casusBelli = allowedCasusBelli(faction);
        if (casusBelli.isEmpty()) return neutralFallback(defenderDisplay, pointName);
        CasusBelli cb = weightedPick(random, casusBelli);

        return render(faction, cb, defenderDisplay, pointName);
    }

    /** Rendered narrative from an explicit (faction, casus belli) pair — used by admin commands. */
    public static RaidNarrative render(RaiderFaction faction, CasusBelli cb,
                                        String defenderDisplay, String pointName) {
        String defender = defenderDisplay == null || defenderDisplay.isBlank() ? "the defenders" : defenderDisplay;
        String point = pointName == null || pointName.isBlank() ? "the stronghold" : pointName;
        return new RaidNarrative(
                faction.id(), faction.name(), faction.epithet(), faction.accent(),
                cb.id(),
                fill(cb.opening(), faction, defender, point),
                fill(cb.chant(), faction, defender, point),
                fill(cb.victoryTaunt(), faction, defender, point));
    }

    private static List<RaiderFaction> allowedFactions() {
        List<? extends String> allow = RaidConfig.ALLOWED_RAIDER_FACTIONS.get();
        List<RaiderFaction> out = new ArrayList<>();
        for (RaiderFaction f : RaiderFactionRegistry.all().values()) {
            if (allow.isEmpty() || allow.contains(f.id())) out.add(f);
        }
        if (out.isEmpty()) {
            FactionLogger.LOG.warn("No raider factions permitted by config; falling back to full pool.");
            out.addAll(RaiderFactionRegistry.all().values());
        }
        return out;
    }

    private static List<CasusBelli> allowedCasusBelli(RaiderFaction faction) {
        List<? extends String> allow = RaidConfig.ALLOWED_CASUS_BELLI.get();
        List<CasusBelli> out = new ArrayList<>();
        for (CasusBelli cb : CasusBelliRegistry.all().values()) {
            if (!allow.isEmpty() && !allow.contains(cb.id())) continue;
            if (!faction.supportsAnyTag(cb.tags())) continue;
            out.add(cb);
        }
        return out;
    }

    private static CasusBelli weightedPick(RandomSource random, List<CasusBelli> options) {
        int total = 0;
        for (CasusBelli cb : options) total += Math.max(1, cb.weight());
        int roll = random.nextInt(total);
        int acc = 0;
        for (CasusBelli cb : options) {
            acc += Math.max(1, cb.weight());
            if (roll < acc) return cb;
        }
        return options.get(options.size() - 1);
    }

    private static String fill(String template, RaiderFaction faction,
                                String defender, String point) {
        if (template == null || template.isEmpty()) return "";
        return template
                .replace("{faction}", faction.name())
                .replace("{epithet}", faction.epithet())
                .replace("{defender}", defender)
                .replace("{point}", point);
    }

    /** Guaranteed non-null narrative used when config disables the system. */
    private static RaidNarrative neutralFallback(String defender, String point) {
        return new RaidNarrative(
                null, null, null, null, null,
                "Enemy scouts have found " + (defender == null ? "the defenders" : defender) +
                        " at " + (point == null ? "the stronghold" : point) + ".",
                null, null);
    }
}
