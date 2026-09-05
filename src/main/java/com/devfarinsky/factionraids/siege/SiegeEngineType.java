package com.devfarinsky.factionraids.siege;

/**
 * Catalog of siege units Faction-Raids can deploy. The first four map to
 * Talhanation's Siege Weapons mod entities and only spawn when that mod is
 * present. The sapper is always available because it uses vanilla TNT.
 *
 * @see <a href="https://www.curseforge.com/minecraft/mc-mods/siegeweapons">Siege Weapons</a>
 */
public enum SiegeEngineType {
    /** Ranged, area-of-effect. Registry id: {@code siegeweapons:catapult}. */
    CATAPULT("siegeweapons:catapult", true),
    /** Ranged, high-precision bolts. Registry id: {@code siegeweapons:ballista}. */
    BALLISTA("siegeweapons:ballista", true),
    /** Melee wall-breaker. Registry id: {@code siegeweapons:battering_ram}. */
    BATTERING_RAM("siegeweapons:battering_ram", false),
    /** Wall-height bypass with a 5-slot passenger platform per floor. Registry id: {@code siegeweapons:siege_tower}. */
    SIEGE_TOWER("siegeweapons:siege_tower", false),
    /** Vanilla-TNT delivery raider. Available without the Siege Weapons mod. */
    SAPPER_CHARGE(null, false);

    private final String registryId;
    private final boolean ranged;

    SiegeEngineType(String registryId, boolean ranged) {
        this.registryId = registryId;
        this.ranged = ranged;
    }

    /** {@code modid:path} registry id for the backing entity, or {@code null} for the sapper. */
    public String registryId() {
        return registryId;
    }

    /** Whether the engine benefits from a Recruits siege-engineer operator. */
    public boolean ranged() {
        return ranged;
    }

    /** Whether the engine depends on the Siege Weapons mod being installed. */
    public boolean requiresSiegeWeapons() {
        return registryId != null;
    }

    /** Case-insensitive lookup used by the config list. Returns {@code null} for unknown values. */
    public static SiegeEngineType parse(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (SiegeEngineType type : values()) if (type.name().equals(cleaned)) return type;
        return null;
    }
}
