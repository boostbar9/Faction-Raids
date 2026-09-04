# Faction Raids 1.2.0

Faction Raids is a server-authoritative Forge 1.20.1 addon for kingdom and faction modpacks.
It creates illager invasions whose objective is the defending faction's players and territory,
not Minecraft villagers, recruits or beds.

## Compatibility

- Minecraft 1.20.1
- Forge 47.4.16 (47.x)
- Java 17
- No required library mods
- Compatible by design with Villager Recruits, Villager Workers and custom faction mods
- Does not add blocks, dimensions, biomes or world-generation content
- Safe to install in an existing world after making a backup

Install `factionraids-1.2.0.jar` in the host's `mods` folder. The mod is server-authoritative,
so clients may connect without it. Installing the same JAR on every client is also supported.
Never leave an older Faction Raids JAR in the folder beside the new version.

## Recommended setup for Devin and Godric

1. Install the mod and start the world.
2. Stand at the center of Black Beards Company's main base.
3. Run `/factionraids anchor set`.
4. Make sure Godric is online, then run `/factionraids member add Godric`.
5. Confirm both names with `/factionraids member list`.
6. Run `/factionraids start home` for a controlled test.

The internal roster is authoritative once enabled. After that, temporary scoreboard-team
changes by another faction mod cannot split Devin and Godric into separate raid factions.

## Territory points

Every faction has a permanent `home` point created or moved with:

```mcfunction
/factionraids anchor set
```

The owner can register additional named targets while standing at them:

```mcfunction
/factionraids territory add harbor
/factionraids territory add castle
/factionraids territory list
/factionraids territory remove harbor
```

Automatic invasions randomly choose from eligible points near an online faction member.
The default maximum is four total points, including `home`. Point names may contain lowercase
letters, numbers, underscores and hyphens.

## Commands

| Command | Permission | Purpose |
|---|---:|---|
| `/factionraids anchor set` | Owner/operator | Create or move the permanent home point |
| `/factionraids anchor claim` | Faction member | Claim ownership of an unowned 1.0 anchor |
| `/factionraids anchor remove` | Owner/operator | Remove the faction and all its defense points |
| `/factionraids territory add <name>` | Owner/operator | Add or move a named defense point |
| `/factionraids territory remove <name>` | Owner/operator | Remove a named point other than home |
| `/factionraids territory list` | Member | List every registered target |
| `/factionraids member add <online-player>` | Owner/operator | Add a player and enable the internal roster |
| `/factionraids member remove <online-player>` | Owner/operator | Remove a non-owner member |
| `/factionraids member list` | Member | Show saved and online members |
| `/factionraids start [point]` | Owner/operator | Start a test at a named or nearby point |
| `/factionraids status` | Member | Show cooldown or active-wave status |
| `/factionraids debug` | Member | Explain faction, roster, TPS and raid state |
| `/factionraids stop` | Operator level 2 | Stop the caller's invasion safely |
| `/factionraids admin list` | Operator level 2 | List every faction and invasion |
| `/factionraids admin stop <team>` | Operator level 2 | Stop an orphaned or remote invasion |
| `/factionraids admin repair <team>` | Operator level 2 | Reconcile tracked enemies for an active invasion |
| `/factionraids admin remove <team>` | Operator level 2 | Remove an inactive orphaned faction anchor |

## Invasion rules

- Mod-spawned enemies continually prioritize online players on the targeted roster.
- Villagers and iron golems cannot be damaged by these enemies when `protectVillagers=true`.
- Player death never causes immediate defeat; players may respawn and return.
- Defeat occurs only when no living roster member remains within the defense radius for the
  configured abandonment time.
- Automatic invasions begin only at a point near an online member by default.
- The invasion timer and loaded enemies freeze while the entire roster is offline.
- Unloaded enemies remain tracked, and tagged mobs are recovered after restarts.
- Orphaned tagged enemies are removed when their chunks load after an invasion ends.
- Vexes summoned by invasion evokers are tracked and cleaned up.
- Unsafe terrain triggers a delayed retry instead of an empty wave.
- Per-invasion, global-mob, concurrent-invasion and TPS limits protect integrated-server performance.
- The final three enemies glow so a wave cannot stall on a hidden mob.
- Late waves include witches, evokers, illusioners and a final-wave ravager.

## Victory rewards

Each online faction member receives 250 experience points plus items from:

```text
factionraids:gameplay/invasion_victory
```

The bundled table awards emeralds, arrows and a chance at a golden apple or crossbow. A datapack
can replace:

```text
data/factionraids/loot_tables/gameplay/invasion_victory.json
```

This allows a modpack to award spell scrolls, artifacts or collectible items without making those
mods hard dependencies. Rewards can be disabled or redirected in `factionraids-common.toml`.

## Configuration

Forge creates `config/factionraids-common.toml`. Defaults remain tuned for a two-player,
performance-sensitive integrated server:

- Five escalating waves
- 10 base enemies plus three for the second online member
- 45 seconds between waves
- 30 active enemies per invasion and 35 globally
- One simultaneous invasion
- 60–90 minutes between automatic invasions
- Four defense points and 16 roster members maximum
- New waves delayed below approximately 18 TPS

Existing configuration files receive new 1.2 keys automatically.

## Updating from 1.0 or 1.1

Existing anchors, cooldowns and active raids migrate automatically. A former single anchor becomes
the `home` defense point. Version 1.0 anchors without recorded ownership must be claimed once:

```mcfunction
/factionraids anchor claim
```

Version 1.1 factions continue using their scoreboard membership until the owner runs
`/factionraids member add <player>`. That command safely seeds the internal roster from the
currently online scoreboard team before adding the selected player.

## Interaction with Village Recruits

Faction Raids does not alter another mod's internal settlement defeat checks or suppress its chat
messages. If a Village Recruits addon announces that Black Beards Company “has been lost,” that
message comes from the addon's separate siege/defeat system. Disable that old autonomous defeat
system if Faction Raids is intended to be the only authority for player-territory invasions.

## Removal safety

The mod adds no world blocks. Stop active invasions before removing it so every loaded invasion
mob can be cleaned up. Existing terrain and builds remain intact.
