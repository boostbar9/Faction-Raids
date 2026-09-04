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
    public static final ForgeConfigSpec.IntValue TIME_BETWEEN_WAVES_SECONDS;
    public static final ForgeConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue DEFENSE_RADIUS;
    public static final ForgeConfigSpec.IntValue ABANDON_DEFEAT_MINUTES;
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
