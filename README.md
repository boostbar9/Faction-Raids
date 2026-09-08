# The Siege Overhaul

**Minecraft 1.20.1 | Forge 47.x | Java 17 | Server + Client**

**Build a faction. Raise an army. Defend your Siege Core.**

Enemy factions claim a foothold near your territory, build a fortified war camp, and prepare an army to take your land. Hire recruits, recruit magical heroes, and hold the line when the assault begins.

> **Formerly Faction Raids.** Same project and author, with a new core-based siege system. **Beds and respawn anchors no longer establish new raid targets.**

---

## Your Land. Your Core. Your Army.

Create or join a [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) faction, claim land, and place a **Siege Core inside your faction's Overworld claim**. One active core per faction becomes the siege objective and your recruitment hub.

The core opens a responsive menu with three tabs:

- **Army:** two recruit offers and one worker offer, rotating every **15 minutes of server runtime**. Each offer can be purchased once per rotation across your faction.
- **Heroes:** a featured hero with enhanced equipment and a signature magical skill.
- **Loot boxes:** spend emeralds on equipment and supplies. Sealed boxes hide their contents until a short animated reveal. Prices and rarity odds are visible before purchase.

The menu includes selected-tab highlighting, an emerald balance, offer refresh progress, and compact layouts for smaller screens or larger GUI scales.

## Heroes With a Purpose

| Hero | Signature skill |
| --- | --- |
| **Kael Bloodthorn** | Every third melee hit against siege enemies heals him. At full health, it grants a short shield instead. |
| **Branna Dawnwarden** | Grants an emergency shield to one nearby wounded ally belonging to her owner. |
| **Sylva Stormbow** | Every fourth arrow hit can chain magical damage to two additional nearby siege enemies. |
| **Orin Frostbinder** | Bolts briefly slow up to three nearby siege enemies. |

Hero magic uses bounded effects and cooldowns while retaining the recruits' normal combat and movement. Existing hired heroes gain their new role abilities without receiving replacement equipment.

## Emerald Loot Boxes

| Box | Price | Category |
| --- | ---: | --- |
| **Field Supplies** | 16 emeralds | Mystery supplies |
| **Veteran Armory** | 48 emeralds | Mystery enchanted equipment |
| **Royal Treasury** | 96 emeralds | Premium mystery rewards |

Each box awards **one randomly selected reward stack**. Rarity odds are **Common 50% / Uncommon 30% / Rare 15% / Epic 5%**. Confirm a purchase to open the seal, watch the short reel-style animation, and reveal your reward. Purchases use emeralds and require enough inventory space for any outcome. Rewards are delivered by the server even if you close the menu before the animation ends.

## How a Siege Unfolds

1. **An enemy foothold.** An attacking faction searches for a suitable camp site and registers a real Villager Recruits claim. Enemy factions have distinct map colors, banners, and equipment identities.
2. **Time to prepare.** New sieges allow **12 minutes of preparation by default**, split between establishment, fortification, and army muster. Defenders can prepare their troops or disrupt the enemy camp.
3. **Builders get to work.** Armored Workers 2 builders use native building jobs and supplied materials. Camp guards defend the site while construction and later upgrades progress.
4. **The War Gate opens.** A protected reinforcement gate provides a designated ground-level arrival point. The gate and its graded access road arrive fully assembled when the camp is established, leaving builders free to work on the camp itself. The gate is removed during siege cleanup.
5. **The army advances.** Infantry, ranged units, cavalry, officers, sappers, and siege engineers attack in waves. Units travel normally outside your territory and use local role-based formations inside it.
6. **Defenses are tested.** Raiders use breaching, ladders, alternate approaches, and supported siege equipment. Commanders can perform a visible, interruptible strike against certain building blocks.
7. **The core is contested.** Numerical superiority around the core drives capture. Losing the core transfers its territory to the enemy faction. Bring yourself and your recruits back to outnumber the occupiers and reclaim it.

**Ties pause capture progress. The opposing side's numerical superiority reverses it.** Capture distances and timings are configurable.

Siege engines use supplied native crews. Deployment depends on available space, working routes, configuration, and entity caps.

## Start With Two Supply Bags

Your first-join starter package occupies **two inventory slots**:

- **Faction supplies:** the Warlord's Codex, a Siege Core, loom, banners, dyes, and emerald funding toward faction creation, claiming land, and hiring starting troops.
- **Survival supplies:** starting tools, armor, food, and a modest building kit, including **256 stone bricks**.

Open each bag when ready. If your inventory fills, undelivered contents remain in the bag.

### Quick Start

1. Install The Siege Overhaul and **all four required companion mods** below.
2. Open your starter bags.
3. Create or join a Villager Recruits faction and claim land.
4. Place your Siege Core inside your faction's **Overworld claim**.
5. Hire troops, assign your workers their normal work areas, and prepare your defenses.

Use the **Warlord's Codex** for concise siege guidance, status, and access to the native faction and claim interfaces.

## Victory and Restoration

With default settings, an eligible five-wave victory with the commander defeated awards **48 emeralds per online faction member**, plus a customizable victory loot roll. Manually started raids do not award victory rewards by default.

Siege Overhaul records supported siege damage and temporary construction for restoration. **Cleanup settings control restoration**, and later player replacements are respected. Restoration covers tracked changes; it is not a universal rollback for unrelated mods or player activity.

## Requirements

| Component | Required version |
| --- | --- |
| Minecraft | **1.20.1** |
| [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) | **47.x** |
| Java | **17** |
| [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) | **1.15.2+** |
| [Villager Workers 2](https://www.curseforge.com/minecraft/mc-mods/workers) | **2.0.3+** |
| [Small Ships](https://www.curseforge.com/minecraft/mc-mods/small-ships) | Compatible **1.20.1 Forge** build |
| [Siege Weapons](https://www.curseforge.com/minecraft/mc-mods/siegeweapons) | Compatible **1.20.1 Forge** build |

**All four companion mods are required. Install matching Siege Overhaul versions on the server and every client.** No additional GUI mod is needed.

## Server and Upgrade Notes

- Configurable enemy caps, preparation time, capture rules, rewards, and performance controls.
- Waves can delay when server performance falls below the configured TPS floor.
- Temporary chunk tickets keep active siege areas ticking; they are not permanent world-wide chunk loaders.
- Enemy camp construction, siege equipment, and pathfinding require suitable terrain and space. These systems continue to receive beta improvements.
- The mod now includes a **custom Siege Core block and items**. The old "no custom blocks" description no longer applies.
- Remove the old `factionraids-*.jar` when installing `siegeoverhaul-*.jar`. Do not run both.
- `/factionraids` remains a legacy command alias. New sieges use claimed Siege Cores; old bed locations do not replace this setup.

### Custom Victory Loot

Override this file in a datapack to change the victory loot roll:

```text
data/siegeoverhaul/loot_tables/gameplay/invasion_victory.json
```

This override changes **victory loot**, not the core menu's equipment-box reward pools.

---

## Links and Credits

[Source Code](https://github.com/boostbar9/Faction-Raids) | [Report an Issue](https://github.com/boostbar9/Faction-Raids/issues) | [GPL-3.0-only](https://spdx.org/licenses/GPL-3.0-only.html)

**Author:** boostbar9

Built with **Villager Recruits, Villager Workers 2, Small Ships, and Siege Weapons** by **Talhanation**.
