# The Siege Overhaul

**Minecraft 1.20.1 | Forge 47.x | Java 17 | Server + Client**

**Build a faction. Raise an army. Defend your Siege Core.**

Enemy factions claim a foothold near your territory, build a fortified war camp, and prepare an army to take your land. Hire recruits, recruit magical heroes, and hold the line when the assault begins.

> **Formerly Faction Raids.** Same project and author, with a new core-based siege system. **Beds and respawn anchors no longer establish new raid targets.**

---

## Your Land. Your Core. Your Army.

Create or join a [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) faction, claim land, and place a **Siege Core inside your faction's Overworld claim**. One active core per faction becomes the siege objective and your recruitment hub.

The core opens a responsive command center with Army, Loot, Treasury, Territory, Building, Civilians and Intel sections:

- **Army:** two recruit offers, one worker offer, and a featured hero, rotating every **15 minutes of server runtime**. Each offer can be purchased once per rotation across your faction. Catapult and ballista crews are delivered as deployment kits; their hover text and item tooltip show the selected engine's actual clear, solid, flat pad size and headroom requirement.
- **Loot:** spend emeralds on hidden equipment and supply rewards, or activate short personal blessings.
- **Treasury:** manage shared funds, members and recent activity.
- **Territory:** purchase permanent faction-wide upgrades.
- **Building:** review a complete automatic perimeter, choose among six placeable plans, and track nearby construction.
- **Civilians:** welcome residents, trade and grow faction Treasury income.
- **Intel:** browse unit reference cards, enemy lore, and field guidance.

The menu sizes itself from the player's Minecraft-scaled viewport, selects detailed or compact cards from the space actually available, and uniformly scales down for unusually small windows so controls and mouse hitboxes stay aligned. It also includes selected-tab highlighting, an emerald balance, offer refresh progress, affordability cues, and a **Leave Feedback** button that opens the mod's CurseForge comments page through Minecraft's normal link confirmation.

## Heroes With a Purpose

Twenty heroes are divided among the five Olympian hosts, with patron-themed names, equipment and distinct abilities. The Army panel shows the rotating hero offer, while Intel contains current unit descriptions. They use Recruits-based units with bounded hero abilities; player-chosen names and existing equipment are preserved.

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
5. **The army advances.** Infantry, ranged units, cavalry, officers, sappers, and siege engineers attack in waves. Units use reachable, role-based formations on the approach and release marching orders for combat or obstacles.
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

## Building System

Version 4.52.0 brings perimeter review, six existing placeable plans and nearby construction into **Building**, with cleaner dark panels and source-based plan thumbnails. New automatic perimeters follow the full claimed boundary in the existing five-wide wall style, with an oak walkway, parapets and a hollow enclosed body. Shared internal claim edges stay open. Review is free; commissioning costs **one flat 64 faction Treasury emeralds for the whole territory**, plus separately supplied blocks. Manual Wall Section and Wall Corner plans also gain hollow bodies with retained end caps; their prices and other variants are unchanged.

Already-paid jobs keep their saved geometry, supplies and payment. Old unpaid previews of changed geometry require fresh review. Cavities are protected clearance, never excavation targets. Builders only clear unchanged supported single-cell plants; player builds, containers, fluids and solid obstructions remain protected. Canceling leaves placed blocks intact and does not refund supplies or the commission.

Protected construction and post-load inventory verification for previously protected builders require the audited **Workers 2.0.3 + Recruits 1.15.2** runtime. Other companion versions pause those jobs and reload checks; ordinary unmarked legacy native workers retain their existing behavior. Install the same Siege Overhaul build on server and every client; 4.52.4 uses network protocol 20 to keep purchase prices and availability consistent. Older network versions cannot connect; saved worlds and accepted construction jobs remain compatible. Optional integrations remain optional, and these checks do not establish compatibility with every optional-mod combination or modpack.

Whole-territory projects use bounded native sections, one payment and durable progress. A complete plan must pass the initial loaded-terrain, permission, storage and size checks. Later unavailable sections pause; they are never silently omitted. Suitable paths and supplied native builder storage are still required.

Real-client camp checks passed ordinary terrain and a controlled shallow-water/forest fallback with protected content unchanged. Some terrain cannot safely support a camp; bounded search reports the reason rather than promising universal placement. This verifies establishment, not complete decorative construction or every modpack. Representative real section handoff, canceled-project reload and completed-manual-wall reload checks passed.

## Victory and Restoration

Eligible Siege Core campaigns deposit emeralds into the shared faction bank after every survived wave. Each five-wave chapter increases the per-wave payment by 50% of the starting amount. There is no five-wave ending: accept a majority retreat vote at a checkpoint or capture the enemy command core. Victory loot and XP remain available; bank wave payments replace personal emerald victory payouts for these campaigns. Manually started raids do not award rewards by default.

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

[Share Feedback](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/comments) | [Source Code](https://github.com/boostbar9/Faction-Raids) | [Report an Issue](https://github.com/boostbar9/Faction-Raids/issues) | [GPL-3.0-only](https://spdx.org/licenses/GPL-3.0-only.html)

**Author:** boostbar9

Built with **Villager Recruits, Villager Workers 2, Small Ships, and Siege Weapons** by **Talhanation**.

## Optional Corpse Compatibility

With henkelmax's Corpse mod installed, camp earthworks and War Gate assembly reject changes that overlap a recovery body. Native camp builders pause when a corpse overlaps pending blueprint cells and resume after it is removed, retaining their jobs and supplies. Fallback camp placement and native supply-barrel placement also avoid corpse space.

Corpse remains optional. Cleanup checks at most 32 loaded bodies per second. Empty bodies are removed after 60 seconds; positively identified new siege NPC bodies convert their full inventory into ordinary dropped loot after 120 seconds. Dropped loot then follows normal pickup and despawn rules. Nonempty player bodies and older/unidentified bodies are preserved. Both cleanup timers are configurable; zero disables that cleanup. Native Recruits creates NPC corpses. Automated regression coverage is provided; combined in-game playtesting remains outstanding.

## Optional Epic Knights and Musket Mod

With **Epic Knights: Shields, Armor and Weapons** (`magistuarmory`) installed, new core-hired soldiers and heroes use coordinated medieval armor outfits. Enemy siege uniforms also use faction-specific styles and colors, retaining rank trims and faction banners. Missing/disabled outfit pieces retain the vanilla outfit. Existing equipment and player armor are not automatically replaced. Workers retain their work equipment.

With **ewewukek's Musket Mod** (`musketmod`) installed and its native Recruits combat API available, one-third of new ordinary crossbowman hires receive a named musket and 32 cartridges. They use Recruits' existing musket aiming, reload and ammunition rules. If the expected API or items are absent, the hire keeps its crossbow and arrows. Named ranged heroes retain their specialized weapons.

Both integrations are optional on Minecraft 1.20.1 Forge. Automated tests and upstream API inspection do not establish every combined-mod or modpack configuration.


## Historical command center (4.11.0)

This historical entry describes the earlier three-tab layout. The current sections and costs are described above. The Siege Core introduced a glowing crystal model and these service tabs:

- **Army & Heroes:** ordinary hires, workers, and the rotating hero offer together.
- **Loot & Buffs:** mystery boxes remain 16/48/96 emeralds. Personal five-minute blessings offer Speed I (16), Strength I (24), or Resistance I (32), without replacing an existing effect.
- **Bank & Faction:** shared balance, online/offline faction roster, upcoming wave rewards, and deposit/withdraw controls. Members may deposit; the faction leader may withdraw. Inventory space and bank capacity are checked server-side.

The bank earns **1% interest per real 24-hour day** by default, configurable from 0–10%. Fractional interest carries forward; full-day catch-up is bounded to 365 days. The ledger, completed-wave payments, and votes survive saves and core relocation.

Core sieges continue through progressively stronger five-wave chapters. Reinforcements are staged under existing active-entity and performance caps. Every fifth cleared wave offers a 60-second clickable retreat vote to the faction's eligible online members. Each member gets one ballot; a strict majority accepts retreat. A tie or no majority continues the siege. Capturing the enemy core on its War Gate pad also ends the invasion: outnumber the defenders in the capture radius for the configured recapture duration. Camp restoration still runs afterward.

Bank withdrawals, buffs, and voting are validated on the server. Matching server/client versions are required. The original 4.11.0 entry had automated-only validation. Current release coverage and its limits are recorded in the [4.52.0 release notes](https://github.com/boostbar9/Faction-Raids/blob/main/docs/release-4.52.0.md) and native QA reports.


### Long-campaign reliability (4.11.2)

Later waves can reuse a siege-owned engine when its previous crew is confirmed killed and the vehicle is unoccupied. Replacement engineers deploy at the War Gate and walk to their assigned equipment; vehicles are never teleported. The fleet and population limits still apply. Mounting captured artillery protects it from siege reuse and end-of-siege cleanup. Unknown crew deaths from older saves are preserved rather than guessed.

Assault navigation retries prolonged circling while allowing short detours. Builders try alternative claim-checked upgrade sites if the preferred site is obstructed, without replacing player blocks. Guards retain their stationed aggressive defense. Bank actions report the actual transfer and balance. Eligible members can recover an uncast retreat ballot after reconnecting or reopening the core; the original deadline and one-vote limit remain. Practice sieges show zero upcoming bank payment. Loot-box contents stay hidden.
