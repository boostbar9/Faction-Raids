# Faction Raids — Architecture Notes

Living guide for contributors. Update it whenever a package layout, cross-cutting
helper, or major class boundary changes.

## Package layout

```
com.devfarinsky.factionraids
├── FactionRaids.java          — @Mod entry point (Forge lifecycle only)
├── FactionLogger.java         — Single SLF4J logger for the mod
├── ModConstants.java          — Cross-cutting constants (ticks, NBT tags, boss-bar defaults)
├── RaidConfig.java            — ForgeConfigSpec definitions
├── RaidEvents.java            — Forge event handlers + siege state machine (see split plan below)
├── RaidSavedData.java         — SavedData: Anchors, DefensePoints, RaidState (persistence root)
├── RaidNetwork.java           — Client/server packet channel
├── RaidDashboardMenu.java     — Server-side dashboard menu
├── OptionalCompatBridge.java  — Reflection-free companion-mod detection
├── RecruitsBridge.java        — Villager Recruits integration
│
├── client/                    — Client-only rendering
│   ├── ClientDashboardOpener.java
│   └── SiegeCommandScreen.java
│
├── command/                   — Brigadier command tree + player-command helpers
│   ├── RaidCommands.java      — /factionraids tree (calls RaidEvents.*Cmd delegators)
│   └── PlayerCommand.java     — try/catch(getPlayerOrException) helper
│
└── raid/                      — Siege-state helpers extracted from RaidEvents
    ├── RaidTags.java          — Encapsulates NBT team/role tags on entities
    └── RaidBossBars.java      — Owns the per-team ServerBossEvent registry
```

## Guiding conventions

1. **No new magic numbers.** Tick math goes through `ModConstants.secondsToTicks` /
   `ticksToSeconds`. Anything time-based referencing `20` is suspect.
2. **No new persistent-data string literals.** Add the key to `ModConstants.Tags`
   and access it via `raid.RaidTags`.
3. **No new empty `catch (Exception ignored)` blocks.** Route the throwable through
   `FactionLogger.debugCommandFailure(context, t)` (or `FactionLogger.LOG` directly).
4. **No new command handlers inside `RaidCommands`.** Add a private handler on
   `RaidEvents` (or, ideally, in a new class under `command/handlers/`), then expose
   it as a `public static int fooCmd(...)` delegator on `RaidEvents` and reference
   that delegator from `RaidCommands`.
5. **Player-only commands use `PlayerCommand.run(source, notPlayerMsg, player -> …)`.**
   Do not repeat the `try/getPlayerOrException/catch` pattern.
6. **Boss-bar state lives in `raid.RaidBossBars`.** Do not reintroduce a private map
   inside another class.

## RaidEvents split plan (incremental, do not do in one commit)

`RaidEvents.java` remains large by necessity — it still owns the siege state machine
and Forge event bindings. The intended long-term split, in priority order:

1. **`command/handlers/AnchorHandlers.java`** — `setAnchor`, `removeAnchor`,
   `claimLegacyAnchor`, `enableAutomaticHome`, `refreshAutomaticHome`. Each handler
   should adopt `PlayerCommand.run` when moved.
2. **`command/handlers/TerritoryHandlers.java`** — `addDefensePoint`,
   `removeDefensePoint`, `listDefensePoints`.
3. **`command/handlers/MemberHandlers.java`** — `addMember`, `removeMember`,
   `listMembers`.
4. **`command/handlers/RaidControlHandlers.java`** — `startOwnRaid`, `stopOwnRaid`,
   `status`, `debug`, `help`, admin variants.
5. **`raid/SiegeLifecycle.java`** — `beginRaid`, `processRaid`, `finishRaid`,
   `queueWave`, `spawnNextSquad`, `updateBossBar`, `updateCaptureProgress`.
6. **`raid/BreachSystem.java`** — the physical gate-breach subsystem
   (`processPhysicalBreaching`, `breachAndRemember`, `restoreBreachedBlocks`,
   block-state (de)serialization).
7. **`raid/WarCamp.java`** — `buildWarCamp`, `findWarCampPosition`, `placeCampBlock`,
   `cleanupWarCamp`.
8. **`raid/AttackerFactory.java`** — `createAttackerForWave`, `createVanillaAttacker`,
   `assignSiegeRole`, `markCommanderDefeated`.
9. **`raid/Announcements.java`** — `announce`, `showTitle`, `sendActionBar`,
   plus title/subtitle formatting helpers.

Every extraction should be its own commit with **no behavior changes** — pure
move-and-delegate, verified by running one manual siege end-to-end before merging.

## Data / persistence

`RaidSavedData` is the single serialization root. `DATA_VERSION` must be bumped
whenever an on-disk field is added, renamed, or repurposed, and the `load` methods
must remain tolerant of old NBT (see the `HOME_POINT` fallback in `Anchor.load`
and the `Breached` / `WaveStartingCount` fallbacks in `RaidState.load` for the
established pattern).

## Configuration

`RaidConfig` is a flat constant table today. When it exceeds ~100 entries or a new
feature area is added, split it into nested classes (`RaidConfig.Siege`,
`RaidConfig.Breach`, `RaidConfig.Rewards`, `RaidConfig.Compat`) rather than adding
another top-level `public static final ForgeConfigSpec.*Value` alongside dozens of
unrelated ones.
