# Faction Raids 2.7.0

Faction Raids is a synchronized Forge 1.20.1 addon that turns player homes into siege
objectives. Hostile armies establish an approach front, deploy in marching squads, advance on a
stronghold, fight the defending players and their Villager Recruits, and attempt to occupy the
heart of the base under the command of a final-wave siege commander.

Version 2.2 added a complete presentation pass: cinematic siege titles, reinforcement and
occupation action-bar updates, smoke at enemy arrival points, responsive boss-bar colors,
cleaner command output and persistent after-action battle summaries.

Version 2.3 adds a faction command dashboard and a guaranteed emerald treasury reward designed
for Villager Recruits economies. Open it with `/factionraids` to see the stronghold, current siege,
nearby allied Recruits, occupation pressure, cooldown and expected victory spoils in one place.

Version 2.4 adds optional, zero-hard-link integrations for Villager Workers, Small Ships and Siege
Weapons. Workers are recognized as protected faction civilians, while crewed ships and siege
engines appear as war assets and can add a small, capped amount of enemy scaling.

Version 2.5 deepens the siege itself. New invasions must establish control of an outer perimeter
before occupation of the stronghold heart can begin. Raiders rally at the approach-side breach
point, defenders can push breach pressure back, and the boss bar, dashboard, titles and alerts all
track the current siege stage. Ships and siege engines also remember the faction that last crewed
them, so briefly dismounting no longer makes a legitimate war asset disappear from detection.

Version 2.6 makes the invading army use Villager Recruits' own soldier classes by default, including
shieldmen, bowmen, crossbowmen, captains, assassins and siege engineers. Every siege now attempts to
raise a real temporary war camp on the approach side. Red-banner perimeter markers identify the exact
breach objective, and breach pressure rises only while attackers outnumber defenders at that point.

Version 2.7 replaces the chest inventory with a purpose-built live tactical interface. It also adds
physical gate breaching: assault troops visibly crack and temporarily remove doors, trapdoors, fence
gates, fences and iron bars that block their advance. Exact block states are persisted and restored
when the siege ends. The war camp now includes a full field-command pavilion and palisade line.

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
Faction Raids 2.7 must also be installed on every joining client because its tactical screen and
network synchronization are client-visible. Clients and the server must use the same version.

These companion mods are optional and are detected automatically:

- [Villager Workers](https://github.com/talhanation/workers)
- [Small Ships](https://github.com/talhanation/smallships)
- [Siege Weapons](https://github.com/talhanation/siegeweapons)

Faction Raids adds no registered blocks, ores, biomes, dimensions or world-generated structures. It
does assemble a small temporary camp from vanilla blocks during a siege and removes unchanged camp
blocks afterward by default. It can be installed in an existing world after making a normal backup.
Never keep two Faction Raids versions in `mods`.

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

- Scouts provide advance warning and reveal the direction and coordinates of a physically built enemy war camp.
- Invaders use hostile Villager Recruits soldiers by default, preserving their melee, ranged, shield,
  morale and specialist combat behavior. A config switch retains the older vanilla-illager roster.
- Every wave enters from one coherent front instead of appearing randomly around the base.
- Waves deploy as smaller squads at configurable intervals, reducing spawn spikes and creating a
  visible stream of reinforcements from the war camp.
- The vanguard is followed by mixed Recruit assault troops, shieldmen, marksmen, captains, assassins,
  siege engineers and a final command assault supported by selected vanilla raid specialists.
- Breachers move aggressively, squad captains are tougher, marksmen and war casters retain their
  specialized vanilla combat behavior, and the final assault contains a named elite commander.
- Killing the commander removes 30 seconds of accumulated breach or occupation pressure.
- Major moments use vanilla title overlays, sounds and faction-only chat announcements by default.
- Assault squads arrive through a brief smoke effect and reinforcement updates appear above the hotbar.
- Invaders path toward the stronghold when they do not have a reachable defender to fight.
- Breachers standing beside a gate, door, trapdoor, fence or iron bar visibly damage it over time.
  Several breachers work faster, while other blocks and machines are never selected.
- Every block removed by the siege is stored with its exact properties and automatically rebuilt when
  the battle ends. A player replacement is never overwritten.
- Before the inner objective can be occupied, attackers must outnumber defenders at the visibly
  marked approach-side breach point long enough to establish a breach.
- Until the breach opens, unengaged raiders rally on the approach side of that perimeter instead of
  immediately piling onto the stronghold center.
- Defenders can reverse breach pressure by regaining control of the outer line. Once breached, the
  fight falls back to the smaller, slower inner occupation objective.
- Nearby soldiers belonging to the defending Villager Recruits faction acquire invasion targets.
- Invaders also recognize those Recruits as defenders, producing an actual army-versus-army fight.
- The wave budget accounts for the nearby defending Recruit army, but remains inside the per-raid
  and global entity caps.
- Recruits' permanent follow, hold and group orders are not replaced by Faction Raids.
- Villager Workers keep their native work, take-cover and flee behavior; invasion mobs cannot hurt
  them while worker protection is enabled.
- Crewed Small Ships and Siege Weapons belonging to faction members are recognized without taking
  over their movement, sailing, ammunition or firing controls.
- The invasion wins only after outnumbering defenders inside the inner capture ring long enough.
- Retaking the ring rapidly removes occupation progress.
- Player death never causes an instant defeat, and leaving the outer defense radius does not cause
  defeat unless the optional legacy abandonment timer is enabled.
- If every faction member logs out, the siege and its loaded enemies freeze until someone returns.

The boss bar reports the current assault phase, remaining invaders and stronghold occupation. Its
color changes as the threat develops. Occupation warnings are sent at 25%, 50% and 75%, and the
finished siege reports deployed enemies, confirmed defeats, lost contacts and elapsed time.

## Villager Recruits integration

Villager Recruits factions are authoritative. Joining or leaving a Recruits faction automatically
changes which Faction Raids siege schedule and stronghold the player belongs to; no duplicate
member list has to be maintained. The Recruits faction leader is the default stronghold owner.

Allied Recruit soldiers within `recruitMobilizationRadius` can defend. Nobles and messengers are
excluded from automatic mobilization. The integration uses the Recruits faction team and public
recruit ownership information, so solo-owned soldiers are also recognized when possible.

Faction Raids does not start or resolve Villager Recruits' separate claim-conquest system. This
prevents an NPC claim from being transferred merely because a PvE invasion occurred.

## Optional companion-mod integration

The compatibility bridge uses entity registry namespaces plus vanilla faction teams, vehicle
passengers and public ownership information. It never imports optional-mod classes, so removing an
optional companion mod cannot make Faction Raids fail to load.

- **Villager Workers:** nearby allied Workers are displayed as civilians in the command dashboard.
  Invaders spawned by Faction Raids clear Workers as attack targets and cannot damage them. Workers
  do not count as soldiers or artificially hold the capture ring.
- **Small Ships:** boarding a ship records the crew member's current faction. It remains a recognized
  naval asset after the crew dismounts and can be captured by a different faction boarding it.
  Faction Raids does not steer or damage ships directly.
- **Siege Weapons:** boarding a siege engine records or transfers its faction in the same way. Its
  own mod remains responsible for aiming, ammunition, projectiles and damage.
- **Balanced scaling:** by default, every two recognized ships or siege engines add one invader per wave,
  capped at four extra invaders. This stays inside the existing per-raid and global entity caps.

All integrations, Worker protection, asset detection radius and equipment scaling can be changed in
the `compatibility` section of `factionraids-common.toml`. The scans occur when a wave is planned or
while a player has the live dashboard open, never every server tick.

## Player commands

| Command | Purpose |
|---|---|
| `/factionraids` | Open the faction command dashboard |
| `/factionraids menu` | Open the faction command dashboard explicitly |
| `/factionraids status` | Show the stronghold, cooldown or active siege occupation |
| `/factionraids debug` | Show faction identity, TPS and tracked siege state |
| `/factionraids help` | Show the essential player command guide |
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

## Faction command dashboard

The dashboard is a dedicated responsive tactical screen with a dark campaign-table layout, live
two-second synchronization, custom status cards, progress bars and controls. It does not use a chest
inventory or require a separate GUI library. It shows:

- Faction and stronghold identity
- Automatic-siege cooldown or active wave
- Deployed enemies, queued reinforcements and confirmed defeats
- Current stronghold occupation pressure
- Current outer-perimeter breach pressure before occupation begins
- The physical gate currently under attack and its destruction progress
- How many removed defenses are queued for automatic reconstruction
- Nearby allied Villager Recruits
- Nearby protected Villager Workers
- Crewed Small Ships and Siege Weapons
- Guaranteed emerald and campaign-loot rewards

The lower controls refresh the automatic home, begin a practice siege or print command help.

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
- 36-block outer perimeter requiring 45 seconds of attacker control before the inner objective opens
- Wooden gates and doors requiring about 10 seconds of focused breach work
- Iron doors and bars requiring about 24 seconds of focused breach work
- A 64-block safety cap on temporary, automatically restored siege damage
- Two seconds of breach pressure removed per second after defenders regain the perimeter
- Two seconds of occupation removed per second after defenders regain control
- 128-block Recruit mobilization radius
- Squads of four arriving six seconds apart
- Up to eight strength-scaling enemies based on one extra enemy per three nearby Recruits
- Optional companion assets detected within 128 blocks
- Up to four additional enemies based on one extra enemy per two crewed ships or siege engines
- A final-wave commander with double normal maximum health
- Cinematic titles, action-bar alerts and squad-arrival smoke enabled
- No world-spawn fallback until explicitly enabled
- Legacy distance-only abandonment defeat disabled

These are conservative integrated-server defaults. Dedicated-server packs can raise the global and
concurrent limits gradually after profiling.

## Rewards and datapacks

Every online faction member receives guaranteed emeralds, the configured experience award and a
roll from the campaign loot table after an eligible successful defense. With default settings, a
five-wave victory with the commander defeated awards each online member:

- 16 base emeralds
- 20 emeralds for completing five waves
- 12 emeralds for defeating the commander
- 250 experience points
- Additional items from the campaign loot table

That is **48 guaranteed emeralds per online faction member**, plus the existing bonus loot roll.
The base, per-wave and commander amounts are independently configurable.

Automatic scheduled sieges are reward eligible. Sieges started manually with `/factionraids start`
or the dashboard test button do not grant rewards by default, preventing unlimited farming. Public
servers can deliberately enable `manualRaidsGrantRewards` if they use another cooldown system.

The additional campaign loot comes from:

```text
factionraids:gameplay/invasion_victory
```

Datapacks can replace
`data/factionraids/loot_tables/gameplay/invasion_victory.json` to award modded equipment,
currencies, spell scrolls or collectibles without adding more hard dependencies.

## Updating from 1.x through 2.3

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

Active 2.1 sieges also migrate safely. Currently tracked attackers seed the new deployment count;
earlier casualties cannot be reconstructed, while all existing enemies, waves, occupation pressure
and commander state remain intact.

Existing 2.2 sieges remain reward eligible when first loaded in 2.3, preserving the outcome players
were already fighting toward. Newly started practice sieges follow `manualRaidsGrantRewards`.

Version 2.4 changes no saved-world schema. Existing strongholds, active sieges and reward eligibility
continue unchanged. The optional integrations activate automatically when their mods are installed.

Version 2.5 adds migration-safe breach state. A 2.4 raid that has already deployed a wave is treated
as already breached when first loaded, so updating cannot move an ongoing battle backward. New
invasions use the full perimeter phase. Vehicle faction markers are stored on the optional-mod entity
and require no world conversion; board an existing ship or siege engine once to register it.

Version 2.6 active raids receive a one-time camp construction attempt after updating to 2.7. Gate
restoration data is introduced empty, so an update never claims or rewrites pre-existing air blocks.
The new dashboard requires 2.7 on the host/server and every client.

## Building from source

The build pins ForgeGradle 6.0.54 and declares the Forge and Maven Central repositories explicitly.
With Java 17 installed, run:

```text
./gradlew build
```

The reobfuscated release JAR is written to `build/libs`.

## Removal

Stop active sieges before removing the mod so loaded invasion mobs can be cleaned up and breached
defenses can be restored. Because the mod adds no world-generation content or custom blocks, terrain
and player builds remain intact after a properly completed or stopped siege.

## License and project

Faction Raids is MIT licensed. Source and issue tracking:
[boostbar9/Faction-Raids](https://github.com/boostbar9/Faction-Raids).

Villager Recruits is a separate project by TalhaNation and retains its own license.
