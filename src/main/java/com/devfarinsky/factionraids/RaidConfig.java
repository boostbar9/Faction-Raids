package com.devfarinsky.factionraids;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

public final class RaidConfig {
    /** v2.15.0 raider label visibility mode. See RAIDER_LABEL_MODE. */
    public enum LabelMode { OFF, PROXIMITY, ALWAYS }

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
    public static final ForgeConfigSpec.IntValue AGGRO_RADIUS;
    public static final ForgeConfigSpec.IntValue OFF_AXIS_DRIFT_LIMIT;
    public static final ForgeConfigSpec.BooleanValue BREACHERS_IGNORE_DEFENDERS;
    // v2.23.0 Press-the-Attack:
    public static final ForgeConfigSpec.BooleanValue STUCK_DETECTION_ENABLED;
    public static final ForgeConfigSpec.IntValue STUCK_ESCALATION_SECONDS;
    public static final ForgeConfigSpec.DoubleValue INNER_AGGRO_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue FORCE_REPATH_WHEN_IDLE;
    // v2.24.0 vanilla-style cone-widening fallback:
    public static final ForgeConfigSpec.BooleanValue CONE_FALLBACK_ENABLED;
    public static final ForgeConfigSpec.IntValue CONE_FALLBACK_RADIUS;
    public static final ForgeConfigSpec.IntValue CONE_FALLBACK_VERTICAL;
    // v2.25.0 Parkour + Hazard Avoidance + Shout-to-Allies:
    public static final ForgeConfigSpec.BooleanValue PARKOUR_ENABLED;
    public static final ForgeConfigSpec.IntValue PARKOUR_MAX_FORWARD;
    public static final ForgeConfigSpec.BooleanValue AVOID_HAZARDS;
    public static final ForgeConfigSpec.BooleanValue SHOUT_TO_ALLIES;
    public static final ForgeConfigSpec.IntValue SHOUT_RADIUS;
    // v2.26.0 Pre-raid scouting phase:
    public static final ForgeConfigSpec.BooleanValue SCOUTING_ENABLED;
    public static final ForgeConfigSpec.IntValue SCOUT_PARTY_SIZE;
    public static final ForgeConfigSpec.IntValue SCOUT_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue SCOUT_OBSERVE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SCOUT_DROP_INTEL_LETTER;
    // v2.27.0 Recruits Claims integration:
    public static final ForgeConfigSpec.BooleanValue CLAIM_AWARE_ANCHORS;
    // v2.30.0 Recruits Bridge Sieges: subscribe to SiegeEvent.Start and
    // route it into our raid pipeline as an additional trigger source.
    public static final ForgeConfigSpec.BooleanValue BRIDGE_SIEGES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue USE_CLAIM_CENTER_AS_DEFENSE_POINT;
    public static final ForgeConfigSpec.BooleanValue RESPECT_DEFENDER_CLAIMS;
    // v2.15.0 "Clear Intent":
    public static final ForgeConfigSpec.EnumValue<LabelMode> RAIDER_LABEL_MODE;
    public static final ForgeConfigSpec.IntValue RAIDER_LABEL_RADIUS;
    public static final ForgeConfigSpec.BooleanValue RAIDER_GLOW;
    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue OBJECTIVE_BEACON;
    public static final ForgeConfigSpec.BooleanValue PHYSICAL_BREACHING;
    public static final ForgeConfigSpec.IntValue ABANDON_DEFEAT_MINUTES;
    public static final ForgeConfigSpec.IntValue MISSING_ENTITY_GRACE_SECONDS;
    public static final ForgeConfigSpec.IntValue SPAWN_RETRY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_TPS_TO_SPAWN;
    public static final ForgeConfigSpec.BooleanValue PAUSE_SPAWNING_BELOW_TPS;
    public static final ForgeConfigSpec.IntValue MINIMUM_TPS_SUSTAINED_TICKS;
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
    public static final ForgeConfigSpec.BooleanValue ENABLE_STRAGGLER_RESCUE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_EFFORT_BONUS;
    public static final ForgeConfigSpec.IntValue EFFORT_KILL_BONUS_SECONDS;
    public static final ForgeConfigSpec.IntValue EFFORT_BREACH_BONUS_SECONDS;
    public static final ForgeConfigSpec.IntValue EFFORT_MAX_BONUS_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BREACH_PHASE;
    public static final ForgeConfigSpec.IntValue BREACH_RADIUS;
    public static final ForgeConfigSpec.IntValue BREACH_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue BREACH_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.IntValue BREACH_OBJECTIVE_RADIUS;
    public static final ForgeConfigSpec.BooleanValue USE_RECRUIT_INVADERS;
    public static final ForgeConfigSpec.BooleanValue BUILD_WAR_CAMPS;
    public static final ForgeConfigSpec.BooleanValue CLEANUP_WAR_CAMPS;
    public static final ForgeConfigSpec.BooleanValue CAMP_DESTRUCTIBLE_STRUCTURES;
    public static final ForgeConfigSpec.IntValue CAMP_BONUS_LOOT_EMERALDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AMPHIBIOUS_RAIDS;
    public static final ForgeConfigSpec.IntValue NAVAL_STAGING_RADIUS;
    public static final ForgeConfigSpec.IntValue NAVAL_MIN_WATER_BODY;
    public static final ForgeConfigSpec.IntValue NAVAL_WAVE_SHARE_PERCENT;
    public static final ForgeConfigSpec.IntValue NAVAL_BOAT_SPEED;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BRIDGE_BUILDING;
    public static final ForgeConfigSpec.IntValue MAX_BRIDGE_SPAN;
    public static final ForgeConfigSpec.IntValue MAX_BRIDGES_PER_RAID;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SIEGE_ENGINES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FIRST_WAVE_ENGINES;
    public static final ForgeConfigSpec.IntValue LATER_WAVE_ENGINE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue CLEANUP_SURVIVING_ENGINES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SAPPER;
    public static final ForgeConfigSpec.IntValue SAPPER_MAX_PER_RAID;
    public static final ForgeConfigSpec.BooleanValue SAPPER_MODE_VANILLA_TNT;
    public static final ForgeConfigSpec.BooleanValue PREFER_SMALL_SHIPS;
    public static final ForgeConfigSpec.BooleanValue SMALL_SHIPS_PREFER_LARGE;
    public static final ForgeConfigSpec.IntValue SHIP_CREW_MAX;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CAMP_CONSTRUCTION;
    public static final ForgeConfigSpec.IntValue CAMP_BUILDER_MAX;
    public static final ForgeConfigSpec.IntValue CAMP_LUMBERJACK_MAX;
    public static final ForgeConfigSpec.IntValue CAMP_MAX_BUILD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_NARRATIVE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_RAIDER_FACTIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_CASUS_BELLI;
    public static final ForgeConfigSpec.BooleanValue NARRATIVE_IN_BOSS_BAR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WAVE_COMPOSITION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FORMATIONS;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_WAVE_FORMATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LADDER_BUILDING;
    public static final ForgeConfigSpec.IntValue MAX_LADDERS_PER_RAID;
    public static final ForgeConfigSpec.BooleanValue SPAWN_GUIDEBOOK_ON_JOIN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GATE_BREACHING;
    public static final ForgeConfigSpec.IntValue WOODEN_BREACH_SECONDS;
    public static final ForgeConfigSpec.IntValue REINFORCED_BREACH_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_RESTORABLE_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue RESTORE_BREACHED_BLOCKS;
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
        AGGRO_RADIUS = b.comment("Maximum distance at which a raider will lock onto a defender and break off from the objective. Smaller values keep raiders focused on the objective; larger values let them chase defenders across the base. 2.14.0 default 40 (down from the effective 160 in prior versions, which was the reason raiders ran into the base to chase far-away defenders).")
                .defineInRange("aggroRadius", 40, 8, 160);
        OFF_AXIS_DRIFT_LIMIT = b.comment("If a raider drifts this many blocks perpendicular to the invasion axis, drop its current defender target and re-lock onto the objective. Prevents raiders getting dragged sideways deep into the base by a fleeing defender.")
                .defineInRange("offAxisDriftLimit", 24, 8, 128);
        BREACHERS_IGNORE_DEFENDERS = b.comment("When true (default), breachers and the siege commander refuse to acquire defender targets and only path to the objective, so gate-breach progress is not interrupted by defender skirmishes. Melee raiders still engage defenders normally.")
                .define("breachersIgnoreDefenders", true);
        // v2.23.0 Press-the-Attack:
        STUCK_DETECTION_ENABLED = b.comment("When true (default), raiders that stop making progress toward the objective get escalating help: at 5s a forced re-path plus jump plus small speed burst; at 10s a widened aggro radius so they will chase any nearby defender; at 20s the nearest wall block between them and the objective is queued for physical breaching. Fixes the classic 'raiders standing around outside the wall' problem.")
                .define("stuckDetectionEnabled", true);
        STUCK_ESCALATION_SECONDS = b.comment("Seconds of no forward progress before a raider is treated as stuck and the first escalation fires. Second escalation triggers at 2x this, third at 4x. Default 5.")
                .defineInRange("stuckEscalationSeconds", 5, 2, 60);
        INNER_AGGRO_MULTIPLIER = b.comment("When a raider is within 1.5 x aggroRadius of the objective (i.e. right at the base), its effective aggro radius is multiplied by this. Fixes the case where raiders reach the objective, find no defender in their strict forward cone, and just stand there. Default 2.0.")
                .defineInRange("innerAggroMultiplier", 2.0, 1.0, 4.0);
        FORCE_REPATH_WHEN_IDLE = b.comment("When true (default), raiders that have no target get a fresh moveTo(objective) call every redirect tick (once per second) instead of only when the pathfinder reports done. Prevents a raider whose path failed against a wall from parking there forever.")
                .define("forceRepathWhenIdle", true);
        CONE_FALLBACK_ENABLED = b.comment("When true (default), an idle stuck raider whose direct path to the objective is failing will try a vanilla-style random reachable point in a narrow cone toward the objective, then widen to a 90-degree cone if that also fails. Matches Mojang's RaiderMoveThroughVillageGoal fallback. Only applies to raiders at stuck escalation level 1 or higher, so healthy raiders keep pushing straight at the objective.")
                .define("coneFallbackEnabled", true);
        CONE_FALLBACK_RADIUS = b.comment("Horizontal search radius (blocks) for the vanilla cone fallback. Vanilla uses 16 for the narrow attempt and 8 for the wide attempt; we use this value for the narrow attempt and half of it for the wide attempt.")
                .defineInRange("coneFallbackRadius", 16, 4, 48);
        CONE_FALLBACK_VERTICAL = b.comment("Vertical tolerance (blocks) for the vanilla cone fallback. Vanilla uses 7. Higher values let raiders find reachable points on higher terrain around the objective.")
                .defineInRange("coneFallbackVertical", 7, 1, 16);
        // v2.25.0 Parkour + Hazards + Shout:
        PARKOUR_ENABLED = b.comment("When true (default), raiders can leap 1-3 blocks forward when a solid obstacle blocks their path to the objective. Fixes raiders freezing at low walls, fences, and ledges without needing to break blocks or wait for stuck escalation. Adapted from Enhanced AI (LGPL-3.0) by Insane96.")
                .define("parkourEnabled", true);
        PARKOUR_MAX_FORWARD = b.comment("Maximum forward blocks a raider will probe when deciding whether to parkour-leap. Vanilla-safe range is 2-3; higher values let raiders leap over wider obstacles but can look unnatural.")
                .defineInRange("parkourMaxForward", 3, 1, 5);
        AVOID_HAZARDS = b.comment("When true (default), raiders actively avoid lava, fire, campfires, magma blocks, and their own sapper TNT when pathfinding. Eliminates the 'raider walks into lava' complaint without affecting balance. Uses the mob's built-in path-type malus system so it composes cleanly with vanilla navigation.")
                .define("avoidHazards", true);
        SHOUT_TO_ALLIES = b.comment("When true (default), a hurt raider alerts nearby raiders of the same faction within shoutRadius blocks, granting them the attacker as a target. Mirrors vanilla HurtByTargetGoal.setAlertOthers() but works across our full raider stack, not just at the objective. Ignores line-of-sight so defenders in cover can't hide from the whole wave.")
                .define("shoutToAllies", true);
        SHOUT_RADIUS = b.comment("Radius (blocks) within which a hurt raider alerts allied raiders when shoutToAllies is enabled. Vanilla pillager alert radius is 8; we default higher because raid combat is more spread out.")
                .defineInRange("shoutRadius", 16, 4, 64);
        // v2.26.0 Scouting phase:
        SCOUTING_ENABLED = b.comment("When true (default), a small scouting party of 1-3 raiders spawns during the middle third of each anchor's raid cooldown. Scouts walk to a lookout position near the anchor, observe briefly, then flee back and despawn. Killed scouts drop an intel letter (written book) naming the attacker faction and casus belli for the raid that will actually follow. Turns dead cooldown time into low-key anticipation without adding combat load.")
                .define("scoutingEnabled", true);
        SCOUT_PARTY_SIZE = b.comment("Maximum scouts per scouting party. Actual count is 1-N (rolled per mission). Small parties (1-2) feel like reconnaissance; larger parties (3+) feel like a strike group and undercut the observation fantasy.")
                .defineInRange("scoutPartySize", 2, 1, 3);
        SCOUT_SPAWN_DISTANCE = b.comment("Distance (blocks) from the defender anchor at which scouts spawn. Scouts pick a lookout position closer than this, walk to it, and observe. Too close and they arrive with the raid; too far and they never reach the lookout in time.")
                .defineInRange("scoutSpawnDistance", 100, 60, 200);
        SCOUT_OBSERVE_SECONDS = b.comment("Seconds a scout spends observing the anchor before fleeing home. Scouts also flee immediately if hurt or if a defender comes within 12 blocks during observation.")
                .defineInRange("scoutObserveSeconds", 45, 10, 180);
        SCOUT_DROP_INTEL_LETTER = b.comment("When true (default), a killed scout drops a written book naming the attacker faction, their epithet, opening line, and war chant. Rewards defenders who hunt down scouts with actionable pre-raid intel.")
                .define("scoutDropIntelLetter", true);
        // v2.27.0 Recruits Claims integration:
        CLAIM_AWARE_ANCHORS = b.comment("Master toggle for v2.27.0 Recruits Claims integration. When true (default) AND Recruits is installed, Faction Raids reads claim data at raid time to make defense-point selection and war-camp placement claim-aware. Set false to force the pre-2.27 behavior even with Recruits present.")
                .define("claimAwareAnchors", true);
        USE_CLAIM_CENTER_AS_DEFENSE_POINT = b.comment("When true (default), an anchor whose defense points sit inside a friendly Recruits claim will have that claim's center used as the raid's defense point (instead of the player-configured point). Keeps raids focused on what the player actually built and claimed, rather than the anchor block itself. Requires claimAwareAnchors.")
                .define("useClaimCenterAsDefensePoint", true);
        RESPECT_DEFENDER_CLAIMS = b.comment("When true (default), war-camp placement skips any candidate origin whose chunk is inside the defender's own Recruits claim, so raiders never build fortifications inside your walls. Falls back to the pre-2.27 behavior if no valid claim-external spot is found after 32 attempts. Requires claimAwareAnchors.")
                .define("respectDefenderClaims", true);
        BRIDGE_SIEGES_ENABLED = b.comment("v2.30.0: when true (default) AND Recruits is installed, subscribe to Recruits' SiegeEvent.Start. When another Recruits faction begins sieging a Recruits claim owned by a Faction Raids team, spawn a Faction Raids raid at the same location as the raiding army. The Recruits siege still runs its own timer/health system in parallel. Set false to keep raids purely on Faction Raids' own trigger schedule even with Recruits present.")
                .define("bridgeSiegesEnabled", true);
        // v2.15.0 Clear Intent:
        RAIDER_LABEL_MODE = b.comment("How raider role name tags (Breacher, Warcaster, Captain, Marksman, Siege Commander) are shown. OFF hides them entirely; PROXIMITY shows tags only to defenders within raiderLabelRadius blocks (default); ALWAYS shows all tags at all times (visually noisy on large raids).")
                .defineEnum("raiderLabelMode", LabelMode.PROXIMITY);
        RAIDER_LABEL_RADIUS = b.comment("When raiderLabelMode is PROXIMITY, tags become visible to defenders within this many blocks of the raider. Only used in PROXIMITY mode.")
                .defineInRange("raiderLabelRadius", 24, 8, 128);
        RAIDER_GLOW = b.comment("When true (default), raiders get a role-colored outline (via team scoreboard + glowing) that shows through walls. Uses the same visibility rule as raiderLabelMode.")
                .define("raiderGlow", true);
        HUD_ENABLED = b.comment("When true (default), defenders see a small top-center HUD widget during active raids showing current phase (Marching / Breaching / Occupying), objective name, distance to objective, and wave progress. Server-broadcast; each defender may still hide it client-side with F1.")
                .define("hudEnabled", true);
        OBJECTIVE_BEACON = b.comment("When true (default), a vertical particle column marks the raid objective block so defenders can see exactly where raiders are marching. Fades when the viewer is within 16 blocks of the objective.")
                .define("objectiveBeacon", true);
        PHYSICAL_BREACHING = b.comment("When true (default), the top-contributing breacher for each attacked block is commanded to path directly to that block, face it, and swing its main hand each time breach progress is added. Makes gate-breaking visually legible instead of the block breaking while raiders idle nearby. Disable to restore the pre-2.16.0 behavior where breach progress is credited purely by proximity.")
                .define("physicalBreaching", true);
        ABANDON_DEFEAT_MINUTES = b.comment("Optional legacy abandonment defeat timer. Zero disables it so defeat requires actual stronghold occupation.")
                .defineInRange("abandonDefeatMinutes", 0, 0, 30);
        MISSING_ENTITY_GRACE_SECONDS = b.comment("Keep temporarily unloaded raid mobs tracked for this long before treating them as missing. In 2.13.0 the timer only advances while the mob's chunk is unloaded — loaded chunks with a missing entity no longer count against grace.")
                .defineInRange("missingEntityGraceSeconds", 600, 20, 3600);
        SPAWN_RETRY_SECONDS = b.comment("Delay before retrying a wave that could not find safe spawn positions.")
                .defineInRange("spawnRetrySeconds", 20, 5, 120);
        MINIMUM_TPS_TO_SPAWN = b.comment("Do not create a new wave below this approximate server TPS when TPS protection is enabled. Lowered from 18.0 in 2.10.1 so healthy servers don't trigger on normal tick-time jitter.")
                .defineInRange("minimumTpsToSpawn", 15.0, 5.0, 20.0);
        PAUSE_SPAWNING_BELOW_TPS = b.comment("Delay new waves while server performance is below minimumTpsToSpawn. Default flipped to false in 2.10.1 — the MAX_ACTIVE_RAIDERS hard cap already prevents raid-caused overload, and this sensor produced too many false 'server is lagging' pauses on healthy servers. Server owners who genuinely see raid-related lag can turn this back on.")
                .define("pauseSpawningBelowTps", false);
        MINIMUM_TPS_SUSTAINED_TICKS = b.comment("Number of consecutive raid ticks (each ~1 second) TPS must stay below minimumTpsToSpawn before spawning pauses. Prevents brief tick-time spikes from triggering the pause. Only used when pauseSpawningBelowTps is on.")
                .defineInRange("minimumTpsSustainedTicks", 5, 1, 60);
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
        CAPTURE_TIME_SECONDS = b.comment("Continuous contested seconds required for invaders to capture the stronghold.")
                .defineInRange("captureTimeSeconds", 120, 30, 600);
        ENABLE_STRAGGLER_RESCUE = b.comment("Teleport raiders that stall on the way to the objective, and drop them from the wave count if they stall again. Prevents raids from grinding to a halt.")
                .define("enableStragglerRescue", true);
        ENABLE_EFFORT_BONUS = b.comment("Reward real raider effort (kills, block breaches) with bonus capture/breach progress on top of the presence baseline.")
                .define("enableEffortBonus", true);
        EFFORT_KILL_BONUS_SECONDS = b.comment("Bonus seconds of capture/breach progress awarded when a raider kills a defender.")
                .defineInRange("effortKillBonusSeconds", 5, 0, 60);
        EFFORT_BREACH_BONUS_SECONDS = b.comment("Bonus seconds of capture/breach progress awarded when a raider finishes breaking a wall/gate block.")
                .defineInRange("effortBreachBonusSeconds", 2, 0, 60);
        EFFORT_MAX_BONUS_SECONDS = b.comment("Maximum unspent effort-bonus reservoir (seconds). Prevents farmed events from cashing in all at once.")
                .defineInRange("effortMaxBonusSeconds", 30, 0, 300);
        CAPTURE_DECAY_PER_SECOND = b.comment("Seconds of occupation progress removed each second after defenders regain control.")
                .defineInRange("captureDecayPerSecond", 2, 1, 10);
        ENABLE_BREACH_PHASE = b.comment("Require attackers to establish control of an outer perimeter before stronghold occupation can begin.")
                .define("enableBreachPhase", true);
        BREACH_RADIUS = b.comment("Radius of the outer perimeter where attackers establish a breach. Keep this larger than captureRadius.")
                .defineInRange("breachRadius", 36, 12, 96);
        BREACH_TIME_SECONDS = b.comment("Continuous contested seconds required for attackers to breach the outer perimeter.")
                .defineInRange("breachTimeSeconds", 45, 15, 300);
        BREACH_DECAY_PER_SECOND = b.comment("Seconds of breach progress removed each second while defenders control the perimeter.")
                .defineInRange("breachDecayPerSecond", 2, 1, 10);
        BREACH_OBJECTIVE_RADIUS = b.comment("Radius around the marked approach-side breach point in which attackers and defenders contest progress.")
                .defineInRange("breachObjectiveRadius", 10, 4, 24);
        USE_RECRUIT_INVADERS = b.comment("Use Villager Recruits soldiers as the core enemy army, retaining a few vanilla special units.")
                .define("useVillagerRecruitsArmy", true);
        BUILD_WAR_CAMPS = b.comment("Build a small physical temporary war camp at the invasion staging point.")
                .define("buildTemporaryWarCamps", true);
        CAMP_DESTRUCTIBLE_STRUCTURES = b.comment("When true (default), destroying the war camp's campfire disables reinforcements, breaking the banner scatters the current wave, and breaking the supply barrel drops a stack of emeralds. Set false to keep the camp purely decorative.")
                .define("campDestructibleStructures", true);
        CAMP_BONUS_LOOT_EMERALDS = b.comment("Bonus emeralds dropped when the war camp supply barrel is destroyed by a defender.")
                .defineInRange("campBonusLootEmeralds", 3, 0, 64);
        CLEANUP_WAR_CAMPS = b.comment("Remove untouched temporary camp blocks when the invasion ends. Player-modified blocks are never removed.")
                .define("cleanupTemporaryWarCamps", true);
        ENABLE_AMPHIBIOUS_RAIDS = b.comment("Auto-detect water near the objective and stage part of each wave in boats when a large enough water body is found.")
                .define("enableAmphibiousRaids", true);
        NAVAL_STAGING_RADIUS = b.comment("How far from the objective to search for a naval staging point, in blocks.")
                .defineInRange("navalStagingSearchRadius", 64, 16, 256);
        NAVAL_MIN_WATER_BODY = b.comment("Minimum contiguous water blocks required to qualify as a naval staging point. Small puddles never trigger boat spawns.")
                .defineInRange("navalMinimumWaterBody", 80, 20, 1000);
        NAVAL_WAVE_SHARE_PERCENT = b.comment("Percentage of each wave that spawns in boats when a naval staging point is available. Remainder spawn on land as usual.")
                .defineInRange("navalWaveSharePercent", 40, 0, 100);
        NAVAL_BOAT_SPEED = b.comment("Steering speed for raider boats, as a percentage. 100 = one block per second in still water.")
                .defineInRange("navalBoatSpeed", 10, 1, 100);
        ENABLE_BRIDGE_BUILDING = b.comment("Let stalled raider groups drop a temporary planks bridge across narrow water spans they can't wade.")
                .define("enableBridgeBuilding", true);
        MAX_BRIDGE_SPAN = b.comment("Maximum water span (in blocks) the bridge builder will attempt to cross. Wider water is left for the naval convoy.")
                .defineInRange("maxBridgeSpan", 8, 2, 24);
        MAX_BRIDGES_PER_RAID = b.comment("Maximum bridge segments a single raid can build.")
                .defineInRange("maxBridgesPerRaid", 4, 0, 32);
        ENABLE_SIEGE_ENGINES = b.comment("Master toggle for siege engines (catapult, ballista, battering ram, siege tower). Requires the Siege Weapons mod to be installed; without it, only sappers spawn.")
                .define("enableSiegeEngines", true);
        FIRST_WAVE_ENGINES = b.comment("Engine types spawned prefab at the war camp when wave 1 kicks off. Valid values: CATAPULT, BALLISTA, BATTERING_RAM, SIEGE_TOWER.")
                .defineListAllowEmpty("firstWaveEngines", Arrays.asList("BATTERING_RAM"),
                        raw -> raw instanceof String s && (
                                s.equalsIgnoreCase("CATAPULT") || s.equalsIgnoreCase("BALLISTA")
                                        || s.equalsIgnoreCase("BATTERING_RAM") || s.equalsIgnoreCase("SIEGE_TOWER")));
        LATER_WAVE_ENGINE_CHANCE = b.comment("Percent chance per wave >= 2 that an additional siege engine gets assembled on-site.")
                .defineInRange("laterWaveEngineChancePercent", 30, 0, 100);
        CLEANUP_SURVIVING_ENGINES = b.comment("When a raid ends, discard any siege engines still standing on the field. Turn off to leave them as rubble/loot for defenders.")
                .define("cleanupSurvivingEngines", true);
        ENABLE_SAPPER = b.comment("Master toggle for demolition-charge sappers. Works without the Siege Weapons mod.")
                .define("enableSapper", true);
        SAPPER_MAX_PER_RAID = b.comment("Maximum number of sappers a single raid can dispatch.")
                .defineInRange("sapperMaxPerRaid", 3, 0, 32);
        SAPPER_MODE_VANILLA_TNT = b.comment("When true, sappers plant real primed TNT that damages any block per vanilla explosion rules. When false (default), they trigger a cosmetic blast that only removes doors, fences, trapdoors, and iron bars in a small radius.")
                .define("sapperUseVanillaTnt", false);
        PREFER_SMALL_SHIPS = b.comment("When true and the Small Ships mod is installed, raiders arrive in warships instead of vanilla boats. Falls back to vanilla boats when the mod is missing or the spawn fails.")
                .define("preferSmallShips", true);
        SMALL_SHIPS_PREFER_LARGE = b.comment("When true, prefer the largest available ship type (brigg) over the smaller cog. Ignored when Small Ships is not installed.")
                .define("smallShipsPreferLarge", true);
        SHIP_CREW_MAX = b.comment("Maximum raiders that mount a single Small Ships vessel. Vanilla boats always cap at 2 regardless of this value.")
                .defineInRange("shipCrewMax", 6, 1, 32);
        ENABLE_CAMP_CONSTRUCTION = b.comment("Spawn real Villager Workers lumberjacks and builders to chop trees and construct the raider camp before the assault begins. Requires the Villager Workers mod. Silently skipped when Workers is absent.")
                .define("enableWorkersCampConstruction", true);
        CAMP_BUILDER_MAX = b.comment("Maximum number of builder workers spawned per raider camp.")
                .defineInRange("campBuilderMax", 2, 1, 8);
        CAMP_LUMBERJACK_MAX = b.comment("Maximum number of lumberjack workers spawned per raider camp.")
                .defineInRange("campLumberjackMax", 2, 1, 8);
        CAMP_MAX_BUILD_SECONDS = b.comment("Safety cap on how long the camp construction phase may run before the raid advances anyway.")
                .defineInRange("campMaxBuildSeconds", 180, 30, 900);
        ENABLE_NARRATIVE = b.comment("Attach a themed raider faction and casus belli (reason for war) to every raid. When false, announcements use generic wording and no faction is stored.")
                .define("enableRaiderNarrative", true);
        ALLOWED_RAIDER_FACTIONS = b.comment("Which raider faction ids may be chosen. Leave empty to allow all built-ins. See RaiderFactionRegistry for ids.")
                .defineListAllowEmpty("allowedRaiderFactions", List.of(), o -> o instanceof String);
        ALLOWED_CASUS_BELLI = b.comment("Which casus belli ids may be chosen. Leave empty to allow all built-ins. See CasusBelliRegistry for ids.")
                .defineListAllowEmpty("allowedCasusBelli", List.of(), o -> o instanceof String);
        NARRATIVE_IN_BOSS_BAR = b.comment("Show the raider faction epithet on the boss bar. Disable to keep the generic \"Faction Invasion\" title.")
                .define("narrativeInBossBar", true);
        ENABLE_WAVE_COMPOSITION = b.comment("Use progressive wave composition: early waves lean shieldman/bowman, later waves add captains, engineers and assassins. Disable to fall back to the classic index-based picker.")
                .define("enableProgressiveWaveComposition", true);
        ENABLE_FORMATIONS = b.comment("Command Recruits raiders into formations (line, square) while advancing on the objective. Requires the Villager Recruits mod's FormationUtils to be present.")
                .define("enableRaiderFormations", true);
        ANNOUNCE_WAVE_FORMATION = b.comment("Include the wave's formation label (e.g. 'Shield line', 'Command assault') in the wave announcement.")
                .define("announceWaveFormation", true);
        ENABLE_LADDER_BUILDING = b.comment("Let stalled raider groups build temporary ladders on walls they can't path over. Ladders are removed with the camp cleanup and skip player-modified blocks.")
                .define("enableLadderBuilding", true);
        MAX_LADDERS_PER_RAID = b.comment("Maximum ladder columns a single raid can build. Each column is up to 7 blocks tall.")
                .defineInRange("maxLaddersPerRaid", 6, 0, 32);
        SPAWN_GUIDEBOOK_ON_JOIN = b.comment("Give each player a Faction Raids guidebook item on first login. Right-click the book to open the dashboard.")
                .define("spawnGuidebookOnJoin", true);
        ENABLE_GATE_BREACHING = b.comment("Allow tracked siege breachers to break doors, trapdoors, fence gates and fences blocking their advance.")
                .define("enableRestorableGateBreaching", true);
        WOODEN_BREACH_SECONDS = b.comment("Approximate focused breach time for wooden defenses. Multiple nearby breachers accelerate it.")
                .defineInRange("woodenBreachSeconds", 10, 3, 120);
        REINFORCED_BREACH_SECONDS = b.comment("Approximate focused breach time for iron doors and iron bars.")
                .defineInRange("reinforcedBreachSeconds", 24, 5, 240);
        MAX_RESTORABLE_BLOCKS = b.comment("Safety cap on blocks one siege may temporarily breach and later restore.")
                .defineInRange("maximumRestorableBlocks", 64, 4, 512);
        RESTORE_BREACHED_BLOCKS = b.comment("Restore siege-breached blocks when the raid ends, without overwriting player replacements.")
                .define("restoreBreachedBlocks", true);
        RAIDER_ADVANCE_SPEED = b.comment("Navigation speed used when invasion forces advance toward the stronghold.")
                .defineInRange("raiderAdvanceSpeed", 1.05, 0.5, 1.5);
        ALLOW_WORLD_SPAWN_FALLBACK = b.comment("Use the overworld spawn when a player has no bed or respawn anchor. Disabled by default to avoid attacking public spawn.")
                .define("allowWorldSpawnFallback", false);
        STAGED_SQUADS = b.comment("Deploy each wave as several marching squads instead of creating the entire wave in one server tick.")
                .define("stagedSquads", true);
        SQUAD_SIZE = b.comment("Maximum invaders deployed in one squad when stagedSquads is enabled.")
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
        ENABLE_SMALLSHIPS_COMPAT = b.comment("Recognize Small Ships crewed or previously registered by a faction member as naval assets.")
                .define("enableSmallShips", true);
        ENABLE_SIEGEWEAPONS_COMPAT = b.comment("Recognize Siege Weapons crewed or previously registered by a faction member as defensive assets.")
                .define("enableSiegeWeapons", true);
        COMPAT_ASSET_RADIUS = b.comment("Radius around a stronghold used to detect Workers and registered faction equipment.")
                .defineInRange("assetDetectionRadius", 128, 32, 384);
        CREWED_ASSETS_PER_EXTRA_ENEMY = b.comment("Recognized Small Ships or Siege Weapons required to add one enemy to each wave. Set zero to disable equipment-based scaling.")
                .defineInRange("crewedAssetsPerExtraEnemy", 2, 0, 20);
        MAX_ASSET_SCALING_ENEMIES = b.comment("Maximum additional enemies per wave created from recognized faction equipment.")
                .defineInRange("maximumAssetScalingEnemies", 4, 0, 20);
        b.pop();
        SPEC = b.build();
    }

    private RaidConfig() {}
}
