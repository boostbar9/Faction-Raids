# The Siege Overhaul

Enemy armies establish a foothold, build a fortified camp, assemble their troops, and attack your faction's **Siege Core**.

## Minecraft 1.20.1 Forge — version 4.0.0 beta

Build and automated regression checks validate this update. The new gameplay has not yet had an interactive Minecraft playtest.

**Required on the server and every client:** [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) 1.15.2+, [Villager Workers 2](https://www.curseforge.com/minecraft/mc-mods/workers) 2.0.3+, [Small Ships](https://www.curseforge.com/minecraft/mc-mods/small-ships), and [Siege Weapons](https://www.curseforge.com/minecraft/mc-mods/siege-weapons). Forge 47.x and Java 17. Match Siege Overhaul versions on all clients and the server.

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
