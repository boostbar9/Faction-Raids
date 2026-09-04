package com.devfarinsky.factionraids;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RaidConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTOMATIC_RAIDS;
    public static final ForgeConfigSpec.IntValue MIN_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue MAX_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue WARNING_SECONDS;
    public static final ForgeConfigSpec.IntValue WAVES;
    public static final ForgeConfigSpec.IntValue BASE_ENEMIES_PER_WAVE;
    public static final ForgeConfigSpec.IntValue ENEMIES_PER_EXTRA_PLAYER;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_RAIDERS;
    public static final ForgeConfigSpec.IntValue MAX_GLOBAL_RAIDERS;
    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_RAIDS;
    public static final ForgeConfigSpec.IntValue TIME_BETWEEN_WAVES_SECONDS;
    public static final ForgeConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue DEFENSE_RADIUS;
    public static final ForgeConfigSpec.IntValue ABANDON_DEFEAT_MINUTES;
    public static final ForgeConfigSpec.IntValue MISSING_ENTITY_GRACE_SECONDS;
    public static final ForgeConfigSpec.IntValue SPAWN_RETRY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_TPS_TO_SPAWN;
    public static final ForgeConfigSpec.BooleanValue PAUSE_SPAWNING_BELOW_TPS;
    public static final ForgeConfigSpec.BooleanValue PAUSE_WHEN_FACTION_OFFLINE;
    public static final ForgeConfigSpec.BooleanValue OWNER_ONLY_MANAGEMENT;
    public static final ForgeConfigSpec.BooleanValue GLOW_FINAL_ENEMIES;
    public static final ForgeConfigSpec.IntValue VICTORY_EXPERIENCE;
    public static final ForgeConfigSpec.BooleanValue PROTECT_VILLAGERS;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_GLOBALLY;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PLAYER_NEAR_ANCHOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Player-focused faction invasion settings.").push("playerRaids");
        ENABLED = b.comment("Master switch.").define("enabled", true);
        AUTOMATIC_RAIDS = b.comment("Automatically schedule invasions for registered faction anchors.")
                .define("automaticRaids", true);
        MIN_COOLDOWN_MINUTES = b.comment("Minimum server-runtime minutes between automatic invasions for a faction.")
                .defineInRange("minimumCooldownMinutes", 60, 5, 1440);
        MAX_COOLDOWN_MINUTES = b.comment("Maximum server-runtime minutes between automatic invasions for a faction.")
                .defineInRange("maximumCooldownMinutes", 90, 5, 2880);
        WARNING_SECONDS = b.comment("Countdown before the first wave.")
                .defineInRange("warningSeconds", 120, 10, 600);
        WAVES = b.comment("Number of invasion waves.").defineInRange("waves", 5, 1, 12);
        BASE_ENEMIES_PER_WAVE = b.comment("Enemies per wave with one defending player.")
                .defineInRange("baseEnemiesPerWave", 10, 1, 40);
        ENEMIES_PER_EXTRA_PLAYER = b.comment("Additional enemies per wave for each extra online faction member.")
                .defineInRange("enemiesPerExtraPlayer", 3, 0, 20);
        MAX_ACTIVE_RAIDERS = b.comment("Hard performance cap for this mod's living raiders per faction invasion.")
                .defineInRange("maximumActiveRaiders", 30, 5, 100);
        MAX_GLOBAL_RAIDERS = b.comment("Hard cap shared by every simultaneous Faction Raids invasion.")
                .defineInRange("maximumGlobalRaiders", 35, 5, 200);
        MAX_CONCURRENT_RAIDS = b.comment("Maximum number of active faction invasions across the server.")
                .defineInRange("maximumConcurrentRaids", 1, 1, 20);
        TIME_BETWEEN_WAVES_SECONDS = b.comment("Rest period after clearing a wave.")
                .defineInRange("timeBetweenWavesSeconds", 45, 5, 300);
        MIN_SPAWN_DISTANCE = b.comment("Minimum horizontal distance from the territory anchor.")
                .defineInRange("minimumSpawnDistance", 48, 24, 160);
        MAX_SPAWN_DISTANCE = b.comment("Maximum horizontal distance from the territory anchor.")
                .defineInRange("maximumSpawnDistance", 72, 32, 256);
        DEFENSE_RADIUS = b.comment("Players inside this radius count as actively defending their territory.")
                .defineInRange("defenseRadius", 160, 48, 512);
        ABANDON_DEFEAT_MINUTES = b.comment("Defeat occurs only after no living online faction member remains in the defense radius for this long. Deaths by themselves never cause defeat.")
                .defineInRange("abandonDefeatMinutes", 5, 1, 30);
        MISSING_ENTITY_GRACE_SECONDS = b.comment("Keep temporarily unloaded raid mobs tracked for this long before treating them as missing.")
                .defineInRange("missingEntityGraceSeconds", 120, 20, 900);
        SPAWN_RETRY_SECONDS = b.comment("Delay before retrying a wave that could not find safe spawn positions.")
                .defineInRange("spawnRetrySeconds", 20, 5, 120);
        MINIMUM_TPS_TO_SPAWN = b.comment("Do not create a new wave below this approximate server TPS when TPS protection is enabled.")
                .defineInRange("minimumTpsToSpawn", 18.0, 10.0, 20.0);
        PAUSE_SPAWNING_BELOW_TPS = b.comment("Delay new waves while server performance is below minimumTpsToSpawn.")
                .define("pauseSpawningBelowTps", true);
        PAUSE_WHEN_FACTION_OFFLINE = b.comment("Freeze active invasions while every member of the targeted faction is offline.")
                .define("pauseWhenFactionOffline", true);
        OWNER_ONLY_MANAGEMENT = b.comment("Only the player who created an anchor, or an operator, may move it or manually start an invasion.")
                .define("ownerOnlyManagement", true);
        GLOW_FINAL_ENEMIES = b.comment("Briefly outline the final three enemies so waves cannot stall on hidden mobs.")
                .define("glowFinalEnemies", true);
        VICTORY_EXPERIENCE = b.comment("Experience points awarded to each online faction member after a victory. Set to zero to disable.")
                .defineInRange("victoryExperiencePerPlayer", 250, 0, 10000);
        PROTECT_VILLAGERS = b.comment("Prevent raiders spawned by this mod from damaging villagers and iron golems.")
                .define("protectVillagers", true);
        ANNOUNCE_GLOBALLY = b.comment("Send invasion messages to the whole server instead of only the affected faction.")
                .define("announceGlobally", false);
        REQUIRE_PLAYER_NEAR_ANCHOR = b.comment("Automatic invasions only begin while a faction member is near the anchor.")
                .define("requirePlayerNearAnchor", true);
        b.pop();
        SPEC = b.build();
    }

    private RaidConfig() {}
}
