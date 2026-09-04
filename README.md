# Faction Raids 1.1.0

Faction Raids is a server-authoritative Forge 1.20.1 addon for kingdom and faction modpacks.
It creates illager invasions whose objective is the defending faction's online players, not
Minecraft villagers or beds.

## Compatibility

- Minecraft 1.20.1
- Forge 47.4.16 (47.x)
- Java 17
- No required library mods
- Compatible by design with Villager Recruits, Villager Workers and custom faction mods that
  place faction members on the same vanilla scoreboard team
- Does not add blocks, items, dimensions, biomes or world-generation content

Install `factionraids-1.1.0.jar` in the host's `mods` folder. Version 1.1 is server-authoritative
and can accept clients without the mod; installing the same JAR on every client is still fine.
Back up the world before changing any mod list.

## First-time setup

1. Start the world after installing the mod.
2. Verify both faction owners/members are on the same Minecraft scoreboard team. The custom
   Village Recruits faction system normally handles this already.
3. Stand at the center of the territory you want to defend.
4. Run `/factionraids anchor set`.
5. Run `/factionraids start` for an immediate test.

Only one anchor and one active invasion are allowed per scoreboard team. A player without a
scoreboard team receives a private solo anchor.

## Commands

| Command | Permission | Purpose |
|---|---:|---|
| `/factionraids anchor set` | Anchor owner/operator | Set or move your faction's invasion anchor |
| `/factionraids anchor claim` | Faction member | Claim management of an anchor created by version 1.0 |
| `/factionraids anchor remove` | Anchor owner/operator | Remove your faction's anchor while no raid is active |
| `/factionraids start` | Anchor owner/operator | Start a test invasion while standing near the anchor |
| `/factionraids status` | Everyone | Show the anchor, cooldown or current wave |
| `/factionraids stop` | Operator level 2 | Safely stop your faction's active invasion |
| `/factionraids admin list` | Operator level 2 | List every anchor and active invasion |
| `/factionraids admin stop <team>` | Operator level 2 | Stop an orphaned or remote invasion |
| `/factionraids admin remove <team>` | Operator level 2 | Remove an inactive orphaned anchor |

## Rules

- Mod-spawned raiders continually prioritize online members of the targeted faction.
- Villagers and iron golems cannot be damaged by these raiders while `protectVillagers=true`.
- Player death alone never loses the invasion. Players can respawn and return.
- Defeat happens only if no living faction member remains within the defense radius for the
  configured abandonment time (five minutes by default).
- Victory happens after every configured wave is eliminated.
- Automatic raids start only when a faction member is online and near the anchor.
- Active raids pause completely while every faction member is offline; logout never causes defeat.
- Temporarily unloaded mobs remain tracked and tagged mobs are recovered after restarts.
- Vexes inherit the invasion tag and are counted, targeted and cleaned up with their summoner.
- Unsafe terrain causes a delayed spawn retry instead of an empty wave or free victory.
- Hard per-invasion and global mob caps prevent unlimited spawning.
- New waves pause automatically when approximate server TPS falls below the configured threshold.
- The final three enemies glow briefly to prevent a wave stalling on a hidden mob.
- Each online faction member receives configurable experience after a real victory.

## Configuration

Forge creates `config/factionraids-common.toml` after the first launch. Default settings are
tuned for Devin and Godric's two-player world: five waves, 10 base enemies plus three for the
second online faction member, 45 seconds between waves, 30 maximum active raiders, 35 maximum
globally, one concurrent invasion, and a 60–90 minute automatic cooldown.

When upgrading from 1.0, existing anchors remain valid. Their previous owner is unknown, so
stand in the same faction and run `/factionraids anchor claim` once before attempting to move
the anchor or manually start an invasion.

## Interaction with existing faction sieges

This mod does not alter another mod's internal settlement defeat checks or suppress its chat
messages. If the custom Village Recruits addon continues to announce that a faction “has been
lost,” disable that addon's old autonomous siege/defeat system before relying exclusively on
Faction Raids. Do not run two large automatic raid systems simultaneously on a performance-
sensitive integrated server.

## Removal safety

The mod stores anchors and active-invasion state in its own SavedData entry and adds no world
blocks. Finish or stop active invasions first, then remove the JAR. Existing terrain and builds
remain intact.
