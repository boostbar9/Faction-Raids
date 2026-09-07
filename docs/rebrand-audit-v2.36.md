# Rebrand persistence audit (v2.36 pre-work → v3.0 rename)

Every string key on a persistence surface that will change under the
`factionraids` → `siegeoverhaul` rename. The v2.36 migration reader
copies legacy → new on first launch after upgrade; the v3.0 code
writes only to the new keys.

## SavedData (world-level `data/*.dat`)

| # | Key | Owner | Migration |
|---|---|---|---|
| 1 | `factionraids_data` | `RaidSavedData.DATA_NAME` | Copy-rename file to `siegeoverhaul_data.dat` on first v3.0 launch. |

Only one SavedData file. `ScoutManager.save/load` round-trips through
this same file (nested tags), so it migrates for free.

## Entity persistent data (per-entity NBT PDC)

| # | Key | Owner | Notes |
|---|---|---|---|
| 1 | `FactionRaidsTeam` | RaidEvents raider drop attribution | Read-only from RaidEvents.java:264-area logic |
| 2 | `FactionRaidsRole` | Raider role tag | Read + write across raid systems |
| 3 | `FactionRaids_Role` | Legacy variant of #2 (pre-2.10) | Already dual-read for old saves |
| 4 | `FactionRaidsScout` | Scout mission tag | Set by ScoutManager |
| 5 | `FactionRaidsSiegeTeam` | Siege engine team attribution | |
| 6 | `FactionRaidsSapperCharge` | Sapper explosive marker | |
| 7 | `FactionRaidsGuidebookGiven` | Player PDC — first-join guidebook drop | |
| 8 | `FactionRaidsAssetFaction` | Banner/shield item asset attribution | |
| 9 | `FactionRaidsAssetOwner` | Banner/shield item owner attribution | |

Migration approach: on first raid tick after v3.0 launch, for every
entity in the raid's `state.trackedRaiders` (bounded set), copy any
legacy tag onto the new-name equivalent. Cheap — set typically <200
entities per raid, one-time per entity via a `SiegeOverhaul_Migrated`
marker.

## Scoreboard teams

| # | Key | Owner |
|---|---|---|
| 1 | `fraid_role_<role>` prefix | `RaiderLabels.TEAM_PREFIX` |

Migration: on ServerStartedEvent, for every scoreboard team beginning
with `fraid_role_`, copy members to `sieov_role_<role>` and delete the
legacy team.

## Recruits integration keys (external — do NOT rewrite)

| # | Key | Owner |
|---|---|---|
| 1 | `factionraids_raiders` (Recruits faction id) | `RecruitsBridge.RAIDERS_FACTION_ID` |

This lives in Recruits' faction manager. We CANNOT safely rewrite
Recruits' persistence. Plan: keep the string `factionraids_raiders`
as the Recruits-facing faction id even under the rename. It's an
internal identifier, invisible to players. Rename would strand every
existing faction relationship.

## Network channel

| # | Key | Owner |
|---|---|---|
| 1 | `factionraids:main` | `RaidNetwork` (derived from `MOD_ID`) |

Auto-migrates when `MOD_ID` changes. Wire format protocol bump handles
mismatched builds refusing to connect. Users with old-name clients
will simply be told to update.

## Config file

| # | Path | Owner |
|---|---|---|
| 1 | `<world>/serverconfig/factionraids-common.toml` OR `<config>/factionraids-common.toml` | Forge default |

Migration: on config load, detect legacy file next to expected new
file. If new-name doesn't exist AND legacy does, copy legacy → new
name before Forge reads it. Alternative: pass explicit filename
`siegeoverhaul-common.toml` to `registerConfig`, and detect+copy in
`FMLCommonSetupEvent`.

## Command name

| # | Key | Owner |
|---|---|---|
| 1 | `/factionraids` | `RaidCommands` |

Migration: in v3.0, register `/siegeoverhaul` as primary and
`/factionraids` as an alias (deprecated) for at least one 3.x version.
Not a save surface — no data migration needed, just player muscle memory.

## Asset resource path

| # | Path | Owner |
|---|---|---|
| 1 | `assets/factionraids/textures/**` | Resource pack layout |

Migration: renaming the asset directory changes every `ResourceLocation`
built with `MOD_ID`. Auto-migrates when `MOD_ID` changes. Any hardcoded
paths in JSON models must be swept.

## v2.36 pre-work scope (this release)

- Add `RebrandMigration.migrateOnServerStart(server)` that:
  - detects legacy SavedData `.dat` and copy-renames it
  - detects legacy config file and copy-renames it
  - migrates scoreboard team memberships
  - logs migration outcome at INFO
- Add one-shot deprecation log on first ServerStartedEvent per launch
- Register with dual-key precedence: if new-name file exists, use it;
  otherwise read legacy and write new on next save
- No name/id changes yet — this is READ-ONLY pre-work that gives v3.0
  something to migrate FROM cleanly

## v3.0.0 scope (next release)

- `MOD_ID` change in `gradle.properties`
- Root package `git mv` + import sweep
- PDC tag rename in every writer
- Scoreboard team prefix change
- Command rename with legacy alias
- `mods.toml` display name change
- README + CurseForge/Modrinth listing copy sweep
- Migration reader flips to "primary is new, legacy fallback for one version"
