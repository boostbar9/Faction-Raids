# Faction Raids 2.1.0

Faction Raids is a server-authoritative Forge 1.20.1 addon that turns player homes into siege
objectives. Illager armies establish an approach front, deploy in marching squads, advance on a
stronghold, fight the defending players and their Villager Recruits, and attempt to occupy the
heart of the base under the command of a final-wave siege commander.

No administrator setup is required. A player's bed or respawn anchor becomes a valid stronghold
automatically. Players in the same Villager Recruits faction share one raid schedule, while any
online faction member's home can be selected as the next target.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- [Villager Recruits 1.15.2 or newer](https://www.curseforge.com/minecraft/mc-mods/recruits/files/8339846)

Villager Recruits is a hard dependency and must be installed wherever it is normally required.
Faction Raids does not redistribute any Villager Recruits code or assets.

Faction Raids adds no blocks, ores, biomes, dimensions or structures. It can be installed in an
existing world after making a normal backup. Never keep two Faction Raids versions in `mods`.

## Zero-setup gameplay

1. Install Faction Raids and Villager Recruits.
2. Sleep in a bed or charge and use a respawn anchor near the base you want to defend.
3. Join or create a Villager Recruits faction if playing as a group.
4. Continue playing. The mod registers and updates the stronghold automatically.

By default, a player without a personal respawn point is not registered. This prevents a public
world spawn from accidentally becoming a faction stronghold. Servers that intentionally want that
behavior can enable `allowWorldSpawnFallback`. Changing the faction leader's spawn updates the
faction's idle stronghold, and an automatic invasion may target the respawn point of any online
faction member. A selected location is locked for the duration of that siege so sleeping elsewhere
cannot move an active battlefield.

## How a siege works

- Scouts provide advance warning and reveal the direction of the enemy war camp.
- Every wave enters from one coherent front instead of appearing randomly around the base.
- Waves deploy as smaller squads at configurable intervals, reducing spawn spikes and creating a
  visible stream of reinforcements from the war camp.
- The vanguard is followed by mixed assault troops, breach companies, war casters and a command
  assault containing elite illagers and a ravager.
- Breachers move aggressively, squad captains are tougher, marksmen and war casters retain their
  specialized vanilla combat behavior, and the final assault contains a named elite commander.
- Killing the commander removes 30 seconds of accumulated occupation pressure.
- Invaders path toward the stronghold when they do not have a reachable defender to fight.
- Nearby soldiers belonging to the defending Villager Recruits faction acquire invasion targets.
- Invaders also recognize those Recruits as defenders, producing an actual army-versus-army fight.
- The wave budget accounts for the nearby defending Recruit army, but remains inside the per-raid
  and global entity caps.
- Recruits' permanent follow, hold and group orders are not replaced by Faction Raids.
- The illagers win only after outnumbering defenders inside the inner capture ring long enough.
- Retaking the ring rapidly removes occupation progress.
- Player death never causes an instant defeat, and leaving the outer defense radius does not cause
  defeat unless the optional legacy abandonment timer is enabled.
- If every faction member logs out, the siege and its loaded enemies freeze until someone returns.

The boss bar reports the current assault phase, remaining invaders and stronghold occupation.
Occupation warnings are sent at 25%, 50% and 75%.

## Villager Recruits integration

Villager Recruits factions are authoritative. Joining or leaving a Recruits faction automatically
changes which Faction Raids siege schedule and stronghold the player belongs to; no duplicate
member list has to be maintained. The Recruits faction leader is the default stronghold owner.

Allied Recruit soldiers within `recruitMobilizationRadius` can defend. Nobles and messengers are
excluded from automatic mobilization. The integration uses the Recruits faction team and public
recruit ownership information, so solo-owned soldiers are also recognized when possible.

Faction Raids does not start or resolve Villager Recruits' separate claim-conquest system. This
prevents an NPC claim from being transferred merely because a PvE invasion occurred.

## Player commands

| Command | Purpose |
|---|---|
| `/factionraids status` | Show the stronghold, cooldown or active siege occupation |
| `/factionraids debug` | Show faction identity, TPS and tracked siege state |
| `/factionraids start` | Start a controlled test at the caller's respawn point |
| `/factionraids home automatic` | Convert an older/manual home to automatic respawn behavior |
| `/factionraids home refresh` | Immediately refresh an automatic stronghold |
| `/factionraids territory list` | List the home and any optional manual targets |

Manual anchors and named territories remain available for servers that want castles, towns or
event arenas in addition to automatic player homes:

```mcfunction
/factionraids anchor set
/factionraids territory add castle
/factionraids territory remove castle
```

The older `/factionraids member` commands remain for legacy solo parties. Recruits factions do not
need them because their scoreboard membership is authoritative.

## Administrator commands

| Command | Purpose |
|---|---|
| `/factionraids stop` | Stop the caller's invasion safely |
| `/factionraids admin list` | List registered strongholds and active sieges |
| `/factionraids admin stop <faction>` | Stop an orphaned or remote siege |
| `/factionraids admin repair <faction>` | Reconcile an active siege's tracked enemies |
| `/factionraids admin remove <faction>` | Remove an inactive saved stronghold |

Operators never have to create homes or territories for ordinary players.

## Default balance and performance limits

Forge creates `config/factionraids-common.toml`. Important defaults are:

- Five escalating waves
- 10 base enemies per wave, plus three per additional online defender
- 45 seconds between waves
- One invasion at a time
- 30 active enemies per invasion and 35 globally
- New waves delayed below approximately 18 TPS
- 60–90 minutes between automatic sieges per faction
- 18-block capture ring requiring 120 seconds of enemy control
- Two seconds of occupation removed per second after defenders regain control
- 128-block Recruit mobilization radius
- Squads of four arriving six seconds apart
- Up to eight strength-scaling enemies based on one extra enemy per three nearby Recruits
- A final-wave commander with double normal maximum health
- No world-spawn fallback until explicitly enabled
- Legacy distance-only abandonment defeat disabled

These are conservative integrated-server defaults. Dedicated-server packs can raise the global and
concurrent limits gradually after profiling.

## Rewards and datapacks

Every online faction member receives the configured experience award and rolls this loot table
after a successful defense:

```text
factionraids:gameplay/invasion_victory
```

Datapacks can replace
`data/factionraids/loot_tables/gameplay/invasion_victory.json` to award modded equipment,
currencies, spell scrolls or collectibles without adding more hard dependencies.

## Updating from 1.x or 2.0

Saved anchors, cooldowns and active raids migrate automatically. Existing manually created homes
remain manual so an update cannot unexpectedly move a live server's established castle. Run this
once as the faction leader to opt that old home into the new behavior:

```mcfunction
/factionraids home automatic
```

Version 1.0 anchors without ownership can still be recovered with
`/factionraids anchor claim`. Back up the world before every mod update.

Active 2.0 sieges migrate safely. Their current wave continues normally; staged deployment begins
with the following wave. Existing automatic strongholds at world spawn remain saved, but new
players without beds are not registered unless `allowWorldSpawnFallback` is enabled.

## Building from source

The build pins ForgeGradle 6.0.54 and declares the Forge and Maven Central repositories explicitly.
With Java 17 installed, run:

```text
./gradlew build
```

The reobfuscated release JAR is written to `build/libs`.

## Removal

Stop active sieges before removing the mod so loaded invasion mobs can be cleaned up. Because the
mod adds no world-generation content or custom blocks, terrain and player builds remain intact.

## License and project

Faction Raids is MIT licensed. Source and issue tracking:
[boostbar9/Faction-Raids](https://github.com/boostbar9/Faction-Raids).

Villager Recruits is a separate project by TalhaNation and retains its own license.
