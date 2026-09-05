package com.devfarinsky.factionraids.narrative;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in casus belli pool. Each entry is a self-contained pretext with a
 * three-line narrative arc (opening / chant / victory-taunt).
 *
 * <p>Weights are relative and roughly express "how often does this feel
 * fresh." Retaliation is common because it's the connective tissue with the
 * future {@code RaidLedger}; religious/insult pretexts are rarer because
 * they read louder.
 */
public final class CasusBelliRegistry {

    private static final Map<String, CasusBelli> CASUS_BELLI;

    static {
        Map<String, CasusBelli> m = new LinkedHashMap<>();
        m.put("plunder", new CasusBelli(
                "plunder", 10, Set.of("raider", "loot"),
                "{faction} — the {epithet} — have come for {defender}'s vaults at {point}.",
                "The {epithet} chant: \"Every chest, every ingot!\"",
                "{faction} vanish over the horizon with their spoils."));
        m.put("territory_claim", new CasusBelli(
                "territory_claim", 8, Set.of("territory", "raider"),
                "{faction} lay ancient claim to the ground under {point}. They march to reclaim it from {defender}.",
                "\"This land was ours before it was yours,\" cry the {epithet}.",
                "{faction} plant their banners on the ruins of {point}."));
        m.put("retaliation", new CasusBelli(
                "retaliation", 12, Set.of("retaliation", "raider"),
                "{faction} say {defender} spilled blood that must be answered. Their war-drums beat toward {point}.",
                "The {epithet} shout the names of their dead.",
                "The {epithet} have taken their measure of vengeance from {defender}."));
        m.put("false_omen", new CasusBelli(
                "false_omen", 4, Set.of("religious"),
                "The {epithet} of {faction} read the sky and saw {defender} as the offering. {point} is to be their altar.",
                "\"By fire and ash, {defender} shall answer!\"",
                "The {epithet} declare their omen fulfilled and depart chanting."));
        m.put("stolen_relic", new CasusBelli(
                "stolen_relic", 5, Set.of("religious", "retaliation"),
                "{faction} accuse {defender} of harbouring a stolen relic and have come to {point} to reclaim it.",
                "The {epithet} demand the relic returned — in chests, or in blood.",
                "{faction} withdraw, swearing the relic is now theirs again."));
        m.put("insult_to_the_banner", new CasusBelli(
                "insult_to_the_banner", 3, Set.of("insult", "retaliation"),
                "A slight against {faction}'s banners has reached {defender}. The {epithet} march on {point} to answer it.",
                "\"No banner insults ours and stands,\" the {epithet} vow.",
                "The {epithet} have washed the insult from their banners."));
        m.put("naval_raid", new CasusBelli(
                "naval_raid", 6, Set.of("naval", "loot", "raider"),
                "Sails on the horizon: {faction} — the {epithet} — have made landfall near {point}, hunting {defender}.",
                "The {epithet} beat their oars and cry: \"To the shore!\"",
                "The {epithet} slip back to sea, their hulls heavy with plunder."));
        m.put("unpaid_tribute", new CasusBelli(
                "unpaid_tribute", 5, Set.of("retaliation", "territory"),
                "{faction} claim {defender} owes them tribute long overdue. The {epithet} come to collect at {point}.",
                "\"Pay in ore or pay in bone!\" chant the {epithet}.",
                "{faction} count their tribute and march away satisfied."));
        CASUS_BELLI = Collections.unmodifiableMap(m);
    }

    private CasusBelliRegistry() {}

    public static Map<String, CasusBelli> all() { return CASUS_BELLI; }

    public static CasusBelli get(String id) { return CASUS_BELLI.get(id); }

    public static List<String> allIds() { return List.copyOf(CASUS_BELLI.keySet()); }
}
