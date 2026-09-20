package com.devfarinsky.siegeoverhaul.narrative;

import com.devfarinsky.siegeoverhaul.formations.Formation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stable gameplay identity for the five Olympian war hosts.
 *
 * <p>The persisted faction ids deliberately remain the pre-Olympian ids. This
 * registry layers doctrine, champions and commander powers over those ids so
 * old raids and diplomacy records keep loading while each host behaves
 * differently in play.</p>
 */
public record OlympianHostIdentity(
        String factionId,
        String hostName,
        String patron,
        Formation formation,
        String waveLabel,
        CommanderPower commanderPower,
        String commanderMessage,
        List<String> openingRoles,
        List<String> middleRoles,
        List<String> assaultRoles,
        List<Integer> heroRoles,
        SignatureAssault signatureAssault,
        CampDoctrine campDoctrine) {

    public enum CommanderPower {
        LAST_STAND,
        TIDAL_ADVANCE,
        WAR_CRY,
        FORGE_WARD,
        AEGIS_ORDER,
        HUNTERS_MARK
    }

    /** A checkpoint-wave identity that reuses bounded, already-supported units. */
    public record SignatureAssault(String title, String counterplay,
                                   List<String> roles, int navalShareFloor) {
        public SignatureAssault {
            title = title == null ? "" : title;
            counterplay = counterplay == null ? "" : counterplay;
            roles = List.copyOf(roles);
            navalShareFloor = Math.max(0, Math.min(100, navalShareFloor));
        }
    }

    /** Stable camp-garrison flavor without adding entities or save fields. */
    public record CampDoctrine(String guardTitle, List<String> guardRoles, int leashBlocks) {
        public CampDoctrine {
            guardTitle = guardTitle == null || guardTitle.isBlank() ? "Camp Guard" : guardTitle;
            guardRoles = List.copyOf(guardRoles);
            leashBlocks = Math.max(6, Math.min(24, leashBlocks));
        }
    }

    private static final List<String> GENERIC_OPENING =
            List.of("recruit_shieldman", "bowman", "recruit", "scout");
    private static final List<String> GENERIC_MIDDLE =
            List.of("captain", "crossbowman", "horseman", "nomad", "recruit_shieldman", "recruit", "bowman");
    private static final List<String> GENERIC_ASSAULT =
            List.of("recruit_shieldman", "assassin", "assassin_leader", "captain", "horseman", "nomad",
                    "crossbowman", "scout", "recruit", "recruit_shieldman", "bowman");

    private static final OlympianHostIdentity GENERIC = new OlympianHostIdentity(
            "", "Olympian War Host", "", Formation.LINE, "Combined arms assault",
            CommanderPower.LAST_STAND,
            "Last Stand! Nearby troops gain strength for 5 seconds.",
            GENERIC_OPENING, GENERIC_MIDDLE, GENERIC_ASSAULT,
            java.util.stream.IntStream.rangeClosed(10, 29).boxed().toList(),
            new SignatureAssault("Command Assault", "Break the commander and hold the stronghold",
                    GENERIC_ASSAULT, 0),
            new CampDoctrine("Camp Guard",
                    List.of("recruit_shieldman", "recruit_shieldman", "bowman", "recruit",
                            "recruit_shieldman", "bowman"), 12));

    private static final List<OlympianHostIdentity> HOSTS = List.of(
            new OlympianHostIdentity(
                    "blackbay_reavers", "Poseidon's Tide", "Poseidon", Formation.LINE, "Tidal battle line",
                    CommanderPower.TIDAL_ADVANCE,
                    "Tidal Advance! The host surges forward for 5 seconds.",
                    List.of("bowman", "scout", "recruit_shieldman", "crossbowman"),
                    List.of("crossbowman", "scout", "nomad", "bowman", "captain", "recruit_shieldman", "recruit"),
                    List.of("crossbowman", "scout", "assassin", "nomad", "bowman", "captain",
                            "recruit_shieldman", "recruit", "horseman", "assassin_leader"),
                    List.of(12, 17, 22, 29),
                    new SignatureAssault("Tidal Onslaught",
                            "Brace the shore and break the fast outer line",
                            List.of("crossbowman", "scout", "nomad", "recruit_shieldman",
                                    "crossbowman", "scout", "bowman"), 75),
                    new CampDoctrine("Tidewatch Sentry",
                            List.of("crossbowman", "scout", "recruit_shieldman", "bowman",
                                    "crossbowman", "scout"), 14)),
            new OlympianHostIdentity(
                    "hollowfang_clan", "Warhost of Ares", "Ares", Formation.WEDGE, "Bronze spearhead",
                    CommanderPower.WAR_CRY,
                    "War Cry! The spearhead gains strength for 5 seconds.",
                    List.of("recruit_shieldman", "recruit", "recruit_shieldman", "bowman"),
                    List.of("captain", "recruit_shieldman", "recruit", "horseman", "assassin", "bowman", "crossbowman"),
                    List.of("captain", "recruit_shieldman", "recruit", "assassin", "assassin_leader",
                            "horseman", "nomad", "crossbowman", "bowman", "scout"),
                    List.of(10, 14, 18, 27),
                    new SignatureAssault("Bronze Spearhead",
                            "Hold the breach and eliminate its captains",
                            List.of("captain", "recruit_shieldman", "recruit", "assassin",
                                    "recruit_shieldman", "recruit", "assassin_leader"), 0),
                    new CampDoctrine("Bronze Gateguard",
                            List.of("recruit_shieldman", "recruit", "captain", "recruit_shieldman",
                                    "recruit", "captain"), 18)),
            new OlympianHostIdentity(
                    "emberchant_zealots", "Forgeguard of Hephaestus", "Hephaestus", Formation.COLUMN, "Forge siege column",
                    CommanderPower.FORGE_WARD,
                    "Forge Ward! The column is shielded for 5 seconds.",
                    List.of("recruit_shieldman", "crossbowman", "recruit", "bowman"),
                    List.of("crossbowman", "captain", "recruit_shieldman", "recruit", "nomad", "bowman", "scout"),
                    List.of("crossbowman", "recruit_shieldman", "captain", "assassin_leader", "nomad",
                            "recruit", "bowman", "scout", "horseman", "assassin"),
                    List.of(15, 16, 20, 24),
                    new SignatureAssault("Forge Engine Advance",
                            "Destroy the engineer escort before it reaches your walls",
                            List.of("recruit_shieldman", "crossbowman", "captain", "recruit_shieldman",
                                    "crossbowman", "recruit"), 0),
                    new CampDoctrine("Forgeward Sentry",
                            List.of("recruit_shieldman", "crossbowman", "recruit_shieldman", "crossbowman",
                                    "captain", "recruit"), 10)),
            new OlympianHostIdentity(
                    "crownfall_exiles", "Aegis Order of Athena", "Athena", Formation.SQUARE, "Aegis phalanx",
                    CommanderPower.AEGIS_ORDER,
                    "Aegis Order! The phalanx braces for 5 seconds.",
                    List.of("recruit_shieldman", "bowman", "recruit", "scout"),
                    List.of("captain", "recruit_shieldman", "crossbowman", "recruit", "bowman", "scout", "nomad"),
                    List.of("captain", "recruit_shieldman", "crossbowman", "bowman", "recruit", "scout",
                            "nomad", "assassin", "horseman", "assassin_leader"),
                    List.of(11, 21, 23, 25),
                    new SignatureAssault("Aegis Phalanx",
                            "Split the square and isolate its captains",
                            List.of("captain", "recruit_shieldman", "recruit_shieldman", "crossbowman",
                                    "recruit", "captain"), 0),
                    new CampDoctrine("Aegis Sentinel",
                            List.of("recruit_shieldman", "captain", "recruit_shieldman", "bowman",
                                    "captain", "crossbowman"), 9)),
            new OlympianHostIdentity(
                    "wilds_marauders", "Silver Hunt of Artemis", "Artemis", Formation.SKIRMISH, "Moonlit hunt",
                    CommanderPower.HUNTERS_MARK,
                    "Hunter's Mark! The Hunt marks its quarry for 5 seconds.",
                    List.of("bowman", "scout", "crossbowman", "recruit"),
                    List.of("scout", "bowman", "crossbowman", "nomad", "assassin", "recruit", "recruit_shieldman"),
                    List.of("bowman", "scout", "assassin", "crossbowman", "nomad", "horseman",
                            "assassin_leader", "captain", "recruit_shieldman", "recruit"),
                    List.of(13, 19, 26, 28),
                    new SignatureAssault("Moonlit Hunt",
                            "Watch the flanks and deny clear firing lanes",
                            List.of("scout", "bowman", "crossbowman", "assassin", "scout", "bowman"), 0),
                    new CampDoctrine("Silver Hunt Warden",
                            List.of("bowman", "scout", "crossbowman", "bowman", "scout", "assassin"), 16))
    );

    private static final Map<String, OlympianHostIdentity> BY_ID = HOSTS.stream()
            .collect(Collectors.toUnmodifiableMap(OlympianHostIdentity::factionId, Function.identity()));

    public OlympianHostIdentity {
        openingRoles = List.copyOf(openingRoles);
        middleRoles = List.copyOf(middleRoles);
        assaultRoles = List.copyOf(assaultRoles);
        heroRoles = List.copyOf(heroRoles);
        signatureAssault = java.util.Objects.requireNonNull(signatureAssault, "signatureAssault");
        campDoctrine = java.util.Objects.requireNonNull(campDoctrine, "campDoctrine");
    }

    public static OlympianHostIdentity forFaction(String factionId) {
        if (factionId == null || factionId.isBlank()) return GENERIC;
        return BY_ID.getOrDefault(factionId, GENERIC);
    }

    public static List<OlympianHostIdentity> hosts() {
        return HOSTS;
    }

    public List<String> rolesForWave(int wave) {
        return wave <= 1 ? openingRoles : wave == 2 ? middleRoles : assaultRoles;
    }

    public List<String> rolesForWave(int wave, int totalWaves) {
        return signatureWave(wave, totalWaves) ? signatureAssault.roles() : rolesForWave(wave);
    }

    public String labelForWave(int wave) {
        if (this == GENERIC) return wave <= 1 ? "Infantry and scouts"
                : wave == 2 ? "Mobile support" : waveLabel;
        return waveLabel;
    }

    public String labelForWave(int wave, int totalWaves) {
        return signatureWave(wave, totalWaves) ? signatureAssault.title() : labelForWave(wave);
    }

    public boolean signatureWave(int wave, int totalWaves) {
        return wave > 0 && wave <= Math.max(1, totalWaves) && wave % 5 == 0;
    }

    /** Weighted selection within this host's four champions. */
    public int heroForRoll(int roll) {
        if (roll < 0) throw new IllegalArgumentException("roll");
        int total = heroWeightTotal();
        int cursor = Math.floorMod(roll, total);
        for (int role : heroRoles) {
            cursor -= com.devfarinsky.siegeoverhaul.core.CoreOffers.HERO_WEIGHTS[role - 10];
            if (cursor < 0) return role;
        }
        throw new IllegalStateException("Olympian champion weights are empty");
    }

    public int heroWeightTotal() {
        return heroRoles.stream()
                .mapToInt(role -> com.devfarinsky.siegeoverhaul.core.CoreOffers.HERO_WEIGHTS[role - 10])
                .sum();
    }
}
