## 4.2.1 beta — Claimed footholds before preparation

Scouts now inspect one bounded terrain neighborhood at a time, searching roughly 160–544 blocks from the core after the initial nearby search. Temporary chunk tickets keep the core and the chosen camp active while the defending faction is online. Only one remote candidate is loaded at a time, with Minecraft's surrounding chunk-load margin; tickets expire after five seconds without refresh and are removed at siege end. No synchronous terrain generation or permanent force-loaded claims. Ordinary tall grass and flowers no longer reject camp sites; water, crops, structures, steep terrain and other claims remain protected.

**No camp means no preparation countdown or assault.** A failed search stays in scouting and retries, including failed camps saved by 4.2.0. Once a native 25-chunk Recruits claim is confirmed, the camp is built inside it, supplied builders/engines initialize once and guards spawn. The full preparation countdown then begins. The map claim is named for the attacking faction (for example, Wilds Marauders War Camp), under the shared Raiders faction. Coordinates are announced only after successful registration. Recruits map fog-of-war settings still apply.

No user configuration change is required for normal claiming-enabled worlds. Logs and chat identify unavailable claim APIs/claiming settings rather than claiming a nonexistent camp is forming. Full native-mod gameplay still needs in-game testing; release remains beta.

## 4.2.0 beta — Faction uniforms and core conquest

Enemy soldiers and camp guards now wear faction uniforms. Chest/leg trims: Blackbay lapis blue, Hollowfang quartz white, Emberchant redstone red, Crownfall amethyst purple, Wilds emerald green. Helmet/boot trims mark rank: copper soldiers, iron guards, gold captains, diamond commanders. Commanders wear diamond armor; Wilds rank-and-file wear dyed leather; other uniforms use iron. Rank also changes the trim pattern. Cosmetic back banners show the faction sigil without replacing helmets or weapons. Existing tracked soldiers upgrade once; armor is never replenished automatically. Player-owned recruits keep their equipment.

Core sieges no longer have a perimeter capture timer. By default enemies must outnumber your survival/adventure players and faction recruits within **10 horizontal blocks and 3 vertical blocks** of the core for **120 seconds**. A larger numerical advantage does not accelerate conquest. Ties and empty rings pause progress; an opposing majority reverses it at one second per second. Mounted units, creative players and spectators do not contribute. Breaking physical gates remains a way to reach the core, not a separate conquest objective. Kill and breach bonuses cannot speed up core capture.

Capture transfers **the whole native Recruits claim containing the core**, including all its chunks, to the Raiders. It does not take unrelated claims. The surviving army occupies the core and stops receiving new waves. Your players or recruits must outnumber enemies there for **120 seconds** to recapture the core and return that same claim to your faction. Recapture can proceed with recruits while players are offline. No full five-wave loot reward is given for an early recapture. Occupation and recapture progress survive restarts and an administrator stopping the raid. Hiring and moving an occupied core are disabled. Normal terrain restoration happens when the siege ends after recapture (or an admin stops it).

Config: `coreCaptureRadius` (10), `captureTimeSeconds` (120), `coreRecaptureSeconds` (120). Existing custom `captureTimeSeconds` values are respected. Recruits' parallel whole-claim siege timer is suppressed for registered core claims; native land ownership, permissions, map updates and save data remain authoritative. Third-party claim-update cancellation and admin claims are respected. Legacy non-core raids retain their previous objectives.

**Beta:** automated regression tests and compilation are required before publishing; visual banner positioning and full native-mod gameplay still need an interactive Minecraft playtest. Install matching versions on server and clients.

# The Siege Overhaul

Enemy armies establish a foothold, build a fortified camp, assemble their troops, and attack your faction's **Siege Core**.

## Minecraft 1.20.1 Forge — version 4.1.1 beta

Build and automated regression checks validate this update. The new gameplay has not yet had an interactive Minecraft playtest.

**Required on the server and every client:** [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) 1.15.2+, [Villager Workers 2](https://www.curseforge.com/minecraft/mc-mods/workers) 2.0.3+, [Small Ships](https://www.curseforge.com/minecraft/mc-mods/small-ships), and [Siege Weapons](https://www.curseforge.com/minecraft/mc-mods/siege-weapons). Forge 47.x and Java 17. Match Siege Overhaul versions on all clients and the server.

## Banner placement fix

The old physical breach markers near the defender's objective have been removed. Banners are restricted to the camp; the objective uses particles. Failed camp banner placement cannot alter an existing banner's pattern. Markers already recorded by an active older siege remain covered by its normal end-of-siege cleanup.

## Native factions, camp claims and guards

Use **Your faction** and **Claim map** in the Codex footer to open Villager Recruits' actual faction screen and world map. Sneak-right-click a Siege Core with an empty hand to open the native faction screen. Enemy faction stories are under **Enemy lore**.

New enemy camps register real, non-admin Recruits claims with the native initial 5×5 chunk footprint and a three-chunk buffer from existing claims. Claims appear on the native map and use its ownership and capture system. NPC expeditions register through the claim manager; they do not charge a player's currency. Camps permit interaction and block breaking so sabotage remains possible. Existing claims are never overwritten. Uncaptured camp claims are removed after the siege; claims captured by players are preserved.

Camps begin with up to four native guards: two shieldmen, an archer and a regular recruit. They hold camp separately from the assault waves, respect enemy caps, receive food/ammunition, and do not respawn after defeat. Existing active camps gain their garrison once after upgrading.

Native builders now **pause and resume** if a player or replacement block obstructs the blueprint. Their work orders and finite supplies remain intact. Native jobs no longer disappear at the old construction timeout. Logs report obstruction, resumption, completion and lack of progress. Nighttime and unloaded camps still pause work; the original preparation schedule can also leave a completed crew waiting for fortification.

Claim spacing may put camps farther away (the search extends to roughly 256 blocks). Only loaded terrain is searched; no chunks are force-loaded. If claiming is disabled, the native maximum claim size is below 25, or no eligible site exists, the siege announces that it has no camp. Existing 4.0 camps are not retroactively claimed over land that may now belong to a player.

## Your Siege Core

1. Join or create a Villager Recruits faction and claim land using Recruits.
2. Place your free first-login Siege Core inside that faction's **Overworld claim**. One active core per faction. A replacement can be crafted with eight iron ingots around an emerald block.
3. Right-click the core to hire soldiers. Defend the core when the enemy arrives.

The core is the exact occupation objective. Attackers win by holding its inner ring; defenders reverse occupation by clearing or matching them. This is an occupation objective, not a block-health system. Cores resist explosions and cannot be moved during a siege. Losing the claim or forcibly removing the core ends that siege without rewards.

The core offers **three shared faction hires**, refreshing every **15 minutes of server runtime**. Each slot independently rolls 80% regular recruit, 10% shieldman, or 10% archer; duplicates are possible. Each offer can be purchased once before refresh. Recruits' configured hiring costs, currency, ownership and unit limits apply. Breaking/replacing a core or restarting the server does not reroll stock. Stock refreshes while the server runs, not while it is shut down.

## A longer siege

New sieges have **12 minutes of preparation** by default, configurable with `siegePreparationMinutes`:

- **Establishment — 4 minutes:** builders construct the camp structures using private work areas and supplied materials.
- **Fortification — 4 minutes:** builders receive a second blueprint for the timber palisade. The gate remains open toward your core.
- **Muster — 4 minutes:** the first wave gathers at camp in a native Recruits formation before marching. Defenders can engage them early.

The preparation clock pauses when the faction is offline if offline pausing is enabled, and when the camp chunk is unloaded. Builders still follow Workers' daytime work rules and configured construction limits, so interrupted construction can leave an unfinished camp. No safe camp site means a siege without camp structures or equipment; a message explains this.

Later waves retain staged reinforcements and naval landings. Supplied native operators use ballistae and catapults after preparation. Automatic ram/tower selections use ballistae because Recruits does not provide native AI operators for those vehicles. Raider catapults retain projectile attacks but do not cause untracked terrain explosions.

Completed builders remain at camp until raid cleanup. Camp jobs, fortification plans, army preparation, core locations and recruit offers survive saves. Raid damage and camp blocks use the restoration ledger.

## Upgrading from 3.x

Finish active sieges before updating when practical. Existing active raids keep their saved objective and do not restart with the longer preparation. **New raids require a Siege Core; beds and respawn anchors no longer establish raid targets.** Existing players receive a core on their next login. Old home locations remain saved but cannot start a coreless raid.

Install all four required companion mods and the same Siege Overhaul version on server and clients. This version adds a custom core block: remove cores before uninstalling if you want to avoid missing blocks. Formerly Faction Raids; never install its old JAR alongside Siege Overhaul. Legacy `/factionraids` remains an alias.

## Custom rewards and support

Override `data/siegeoverhaul/loot_tables/gameplay/invasion_victory.json` with a datapack to change victory loot.

[Source and issues](https://github.com/boostbar9/Faction-Raids) · GPL-3.0-only · Author: boostbar9

Built on Villager Recruits, Villager Workers, Small Ships and Siege Weapons by Talhanation.
