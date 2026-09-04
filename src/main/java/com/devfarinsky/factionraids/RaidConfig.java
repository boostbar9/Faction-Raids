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
    public static final ForgeConfigSpec.BooleanValue VICTORY_LOOT_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> VICTORY_LOOT_TABLE;
    public static final ForgeConfigSpec.IntValue MAX_ROSTER_MEMBERS;
    public static final ForgeConfigSpec.IntValue MAX_DEFENSE_POINTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ILLUSIONERS;
    public static final ForgeConfigSpec.BooleanValue PROTECT_VILLAGERS;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_GLOBALLY;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PLAYER_NEAR_ANCHOR;
    public static final ForgeConfigSpec.BooleanValue AUTOMATIC_PLAYER_HOMES;
    public static final ForgeConfigSpec.BooleanValue FOLLOW_RESPAWN_POINT;
    public static final ForgeConfigSpec.BooleanValue MOBILIZE_RECRUITS;
    public static final ForgeConfigSpec.IntValue RECRUIT_MOBILIZATION_RADIUS;
    public static final ForgeConfigSpec.IntValue CAPTURE_RADIUS;
    public static final ForgeConfigSpec.IntValue CAPTURE_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue RAIDER_ADVANCE_SPEED;
    public static final ForgeConfigSpec.BooleanValue ALLOW_WORLD_SPAWN_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue STAGED_SQUADS;
    public static final ForgeConfigSpec.IntValue SQUAD_SIZE;
    public static final ForgeConfigSpec.IntValue SQUAD_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue RECRUITS_PER_EXTRA_ENEMY;
    public static final ForgeConfigSpec.IntValue MAX_RECRUIT_SCALING_ENEMIES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_COMMANDER;
    public static final ForgeConfigSpec.DoubleValue COMMANDER_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue SHOW_RAID_TITLES;
    public static final ForgeConfigSpec.BooleanValue SHOW_ACTION_BAR_UPDATES;
    public static final ForgeConfigSpec.BooleanValue SPAWN_ARRIVAL_EFFECTS;
    public static final ForgeConfigSpec.IntValue VICTORY_EMERALDS_BASE;
    public static final ForgeConfigSpec.IntValue VICTORY_EMERALDS_PER_WAVE;
    public static final ForgeConfigSpec.IntValue COMMANDER_EMERALD_BONUS;
    public static final ForgeConfigSpec.BooleanValue MANUAL_RAIDS_GRANT_REWARDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORKERS_COMPAT;
    public static final ForgeConfigSpec.BooleanValue PROTECT_WORKERS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SMALLSHIPS_COMPAT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SIEGEWEAPONS_COMPAT;
    public static final ForgeConfigSpec.IntValue COMPAT_ASSET_RADIUS;
    public static final ForgeConfigSpec.IntValue CREWED_ASSETS_PER_EXTRA_ENEMY;
    public static final ForgeConfigSpec.IntValue MAX_ASSET_SCALING_ENEMIES;

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
        ABANDON_DEFEAT_MINUTES = b.comment("Optional legacy abandonment defeat timer. Zero disables it so defeat requires actual stronghold occupation.")
                .defineInRange("abandonDefeatMinutes", 0, 0, 30);
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
        VICTORY_LOOT_ENABLED = b.comment("Give each online faction member items from victoryLootTable after a real victory.")
                .define("victoryLootEnabled", true);
        VICTORY_LOOT_TABLE = b.comment("Loot table used for each online faction member after victory. Datapacks may replace it.")
                .define("victoryLootTable", "factionraids:gameplay/invasion_victory");
        MAX_ROSTER_MEMBERS = b.comment("Maximum players in one Faction Raids internal roster.")
                .defineInRange("maximumRosterMembers", 16, 1, 100);
        MAX_DEFENSE_POINTS = b.comment("Maximum named invasion targets registered to one faction.")
                .defineInRange("maximumDefensePoints", 4, 1, 16);
        ENABLE_ILLUSIONERS = b.comment("Include vanilla illusioners in late invasion waves.")
                .define("enableIllusioners", true);
        PROTECT_VILLAGERS = b.comment("Prevent raiders spawned by this mod from damaging villagers and iron golems.")
                .define("protectVillagers", true);
        ANNOUNCE_GLOBALLY = b.comment("Send invasion messages to the whole server instead of only the affected faction.")
                .define("announceGlobally", false);
        REQUIRE_PLAYER_NEAR_ANCHOR = b.comment("Automatic invasions only begin while a faction member is near the anchor.")
                .define("requirePlayerNearAnchor", true);
        AUTOMATIC_PLAYER_HOMES = b.comment("Automatically create siege targets from player respawn points. No command or admin setup is required.")
                .define("automaticPlayerHomes", true);
        FOLLOW_RESPAWN_POINT = b.comment("Move an automatically managed stronghold when its owner or Recruits faction leader changes respawn point.")
                .define("followRespawnPoint", true);
        MOBILIZE_RECRUITS = b.comment("Allow nearby soldiers from the defending Recruits faction to engage this mod's invaders. Their saved movement orders are not replaced.")
                .define("mobilizeRecruits", true);
        RECRUIT_MOBILIZATION_RADIUS = b.comment("Radius around the stronghold in which allied Recruits can join its defense.")
                .defineInRange("recruitMobilizationRadius", 128, 32, 384);
        CAPTURE_RADIUS = b.comment("Radius around the stronghold that the invaders must occupy to win the siege.")
                .defineInRange("captureRadius", 18, 6, 64);
        CAPTURE_TIME_SECONDS = b.comment("Continuous contested seconds required for illagers to capture the stronghold.")
                .defineInRange("captureTimeSeconds", 120, 30, 600);
        CAPTURE_DECAY_PER_SECOND = b.comment("Seconds of occupation progress removed each second after defenders regain control.")
                .defineInRange("captureDecayPerSecond", 2, 1, 10);
        RAIDER_ADVANCE_SPEED = b.comment("Navigation speed used when invasion forces advance toward the stronghold.")
                .defineInRange("raiderAdvanceSpeed", 1.05, 0.5, 1.5);
        ALLOW_WORLD_SPAWN_FALLBACK = b.comment("Use the overworld spawn when a player has no bed or respawn anchor. Disabled by default to avoid attacking public spawn.")
                .define("allowWorldSpawnFallback", false);
        STAGED_SQUADS = b.comment("Deploy each wave as several marching squads instead of creating the entire wave in one server tick.")
                .define("stagedSquads", true);
        SQUAD_SIZE = b.comment("Maximum illagers deployed in one squad when stagedSquads is enabled.")
                .defineInRange("squadSize", 4, 1, 20);
        SQUAD_INTERVAL_SECONDS = b.comment("Delay between squads belonging to the same wave.")
                .defineInRange("squadIntervalSeconds", 6, 1, 60);
        RECRUITS_PER_EXTRA_ENEMY = b.comment("Nearby allied Recruits required to add one scaling enemy. Set zero to disable Recruit-based scaling.")
                .defineInRange("recruitsPerExtraEnemy", 3, 0, 20);
        MAX_RECRUIT_SCALING_ENEMIES = b.comment("Maximum additional enemies per wave created from the defending Recruit army's strength.")
                .defineInRange("maximumRecruitScalingEnemies", 8, 0, 40);
        ENABLE_COMMANDER = b.comment("Create a named elite commander during the final assault.")
                .define("enableCommander", true);
        COMMANDER_HEALTH_MULTIPLIER = b.comment("Maximum-health multiplier applied to the final invasion commander.")
                .defineInRange("commanderHealthMultiplier", 2.0, 1.0, 5.0);
        SHOW_RAID_TITLES = b.comment("Show cinematic vanilla title overlays for major siege moments. No client-side addon is required.")
                .define("showRaidTitles", true);
        SHOW_ACTION_BAR_UPDATES = b.comment("Show concise reinforcement and occupation updates above the hotbar during a siege.")
                .define("showActionBarUpdates", true);
        SPAWN_ARRIVAL_EFFECTS = b.comment("Create a brief smoke effect when an assault squad enters the battlefield.")
                .define("spawnArrivalEffects", true);
        VICTORY_EMERALDS_BASE = b.comment("Guaranteed emeralds awarded to each online faction member after an eligible victory.")
                .defineInRange("victoryEmeraldsBase", 16, 0, 512);
        VICTORY_EMERALDS_PER_WAVE = b.comment("Additional guaranteed emeralds per completed wave for each online faction member.")
                .defineInRange("victoryEmeraldsPerWave", 4, 0, 64);
        COMMANDER_EMERALD_BONUS = b.comment("Additional guaranteed emeralds when the faction defeats the siege commander.")
                .defineInRange("commanderEmeraldBonus", 12, 0, 256);
        MANUAL_RAIDS_GRANT_REWARDS = b.comment("Allow raids started with /factionraids start or the dashboard test button to grant rewards. Disabled to prevent farming by default.")
                .define("manualRaidsGrantRewards", false);
        b.pop();

        b.comment("Optional companion-mod integrations. These safely do nothing when the named mod is absent.")
                .push("compatibility");
        ENABLE_WORKERS_COMPAT = b.comment("Recognize Villager Workers as faction civilians during sieges.")
                .define("enableVillagerWorkers", true);
        PROTECT_WORKERS = b.comment("Prevent Faction Raids invaders from damaging Villager Workers. Workers keep their own flee and take-cover AI.")
                .define("protectVillagerWorkers", true);
        ENABLE_SMALLSHIPS_COMPAT = b.comment("Recognize crewed Small Ships as faction naval assets.")
                .define("enableSmallShips", true);
        ENABLE_SIEGEWEAPONS_COMPAT = b.comment("Recognize crewed Siege Weapons as faction defensive assets.")
                .define("enableSiegeWeapons", true);
        COMPAT_ASSET_RADIUS = b.comment("Radius around a stronghold used to detect Workers, crewed Small Ships and crewed Siege Weapons.")
                .defineInRange("assetDetectionRadius", 128, 32, 384);
        CREWED_ASSETS_PER_EXTRA_ENEMY = b.comment("Crewed Small Ships or Siege Weapons required to add one enemy to each wave. Set zero to disable equipment-based scaling.")
                .defineInRange("crewedAssetsPerExtraEnemy", 2, 0, 20);
        MAX_ASSET_SCALING_ENEMIES = b.comment("Maximum additional enemies per wave created from detected crewed equipment.")
                .defineInRange("maximumAssetScalingEnemies", 4, 0, 20);
        b.pop();
        SPEC = b.build();
    }

    private RaidConfig() {}
}
