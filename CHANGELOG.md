# 4.20.1 beta

- Nightcaller's temporary Shadow Wolves now expire at their saved deadline instead of accumulating permanently. Cleanup also handles already-saved summons and wolves with disabled AI.
- Summoned wolves no longer masquerade as hired recruit heroes. Ordinary wolves and player pets without the summon deadline are untouched.
- No changes to prices, hero abilities, required companions or save format. Automated regression coverage; no interactive gameplay playtesting.

# 4.11.7 beta

- New ordinary core recruits receive simple first names such as Bobby and Fernan.
- Core-hired workers receive one-time job supplies: farmers get a diamond hoe, water bucket and wheat seeds; lumberjacks get a diamond axe and saplings; miners get diamond mining tools, torches and cobblestone; builders get diamond tools and starter blocks; cooks get fuel and raw food. All receive eight bread; couriers retain cargo space.
- Native equipment slots and existing items are preserved. Kits do not refill after reload; full inventories reject preparation before charging. Work areas, routes, recipes and project-specific building materials still require normal Workers setup.

# 4.11.6 beta

- Commanders and breachers can engage defenders after reaching the existing objective area instead of repeatedly clearing their combat targets. Their approach remains focused on the core when the existing ignore-defenders option is enabled.
- Preserve target range, ally/dead-target checks, other troop roles, difficulty, rewards and dependencies. Clarify the configuration description.

# 4.11.5 beta

- Dismounted siege engineers cancel their siege-issued vehicle travel command before returning on foot, preventing native walking orders from repeatedly overriding the return route.
- Restore the original firing setting and preserve unrelated movement orders. Failed cancellation retains ownership for a later retry; no vehicle reset or teleport is performed.

# 4.11.4 beta

- Isolated marching soldiers and lone squad survivors can regroup with nearby troops of the same role instead of keeping a permanent one-person formation.
- Preserve intact squad membership, the six-unit squad limit, proximity checks and combat/engineer exclusions. No difficulty, reward or dependency changes.

# 4.11.3 beta

- Endless siege boss bars show the absolute wave and its retreat checkpoint instead of impossible totals such as wave 10/5.
- Active retreat votes show accepted retreat ballots, the strict-majority target, continue ballots and remaining seconds. Displaying the tally never changes votes or the deadline.
- Preserve difficulty, rewards, active-enemy limits, legacy finite sieges and the core-occupation progress bar.

# 4.11.2 beta

- Later waves can reuse unoccupied siege-owned artillery after its previous crew is confirmed killed. Replacement crews deploy at the War Gate and walk to their equipment, without teleporting or increasing the fleet limit. Player-mounted equipment is excluded from reuse and cleanup.
- Assault paths now recover after ten seconds without approaching their objective, while retaining short obstacle detours and combat behavior.
- Camp builders try eight bounded upgrade sites when a preferred site is obstructed, preserving player blocks, camp claims and external claim exclusions.
- Reopening the core or reconnecting restores uncast active retreat-vote links. Bank transactions report the actual transferred amount and balance; practice sieges show zero upcoming bank rewards. Loot-box contents stay hidden.

# 4.11.1 beta

- Credit a cleared wave before checking enemy-core victory, so simultaneous last-enemy defeat and core capture cannot lose the wave reward.
- Preserve once-only payouts across checkpoint votes, countdowns and reloads; preparation, remaining reinforcements and occupied player cores cannot award a cleared wave.

# 4.11.0 beta

- Siege Core campaigns continue beyond five waves. Every fifth cleared wave offers a sixty-second, one-ballot-per-member retreat vote; strict majority accepts retreat, otherwise the siege continues. Old vote buttons cannot affect later checkpoints.
- Every survived, reward-eligible wave deposits emeralds once into the faction bank. Payouts increase each five-wave chapter. Enemy counts grow through staged reinforcements under existing active limits; health and damage gradually escalate.
- Capture the enemy command core at its War Gate by outnumbering its defenders for the existing recapture duration to win early.
- Persistent faction bank supports member deposits and leader withdrawals. Default daily interest is 1% per real 24-hour day, including up to 365 days of bounded catch-up; configurable, with fractional interest retained and a 1-billion-emerald ledger limit.
- Core services now use three compact tabs: Army & Heroes, Loot & Buffs, Bank & Faction. Added five-minute Speed I, Strength I and Resistance I blessings for 16/24/32 personal emeralds. Existing effects are preserved. Loot boxes retain 16/48/96 prices and mystery reveals.
- Rebuilt the physical core as a glowing crystal command pedestal with restrained magical particles.
- Attack formations now operate throughout the approach, rather than only inside defender claims. Existing combat, ladder and collision releases remain.
- Known new enemy siege corpses convert their contents into normal dropped loot after two minutes; fully empty corpses are removed after one minute. Nonempty player and unclassified legacy corpses are preserved. Cleanup is bounded and configurable; failed drop creation retains the corpse and rolls back new drops.
- Saves and mandatory companion dependencies preserved. New network protocol requires matching client/server versions. No interactive Minecraft playtesting.

# 4.10.6 beta

- Siege captains and commanders now relinquish native patrol regroup/hold/retreat orders to the siege assault controller, including after reload. Camp guards and player-owned leaders are excluded.
- Siege engineers prioritize driving into their established firing position before native firing/reloading. Arrival accounts for native vehicle stopping tolerance, clears latched steering, and restores the previous ranged setting; dismounted operators regain their firing setting.

- Optional Epic Knights armor outfits for new core hires, heroes, and siege faction uniforms. Coordinated dye colors preserve identities; missing or disabled outfit pieces fall back to vanilla.
- Optional ewewukek Musket Mod: one-third of newly hired ordinary crossbowmen receive a personal musket and 32 cartridges when the native Recruits musket API passes compatibility checks.
- Existing equipment, player armor, worker jobs, hiring and loot-box prices remain unchanged. No new mandatory dependencies.

# 4.10.5 beta

- Ordinary soldiers hired at Siege Cores now receive randomized personal names and named role-appropriate weapons.
- New core hires arrive in iron and chainmail armor with coordinated randomized trim colors, eight bread, and thirty-two arrows for ranged roles. Shieldmen receive shields.
- Existing soldiers, worker jobs, named heroes, hiring prices, ownership and unit limits are unchanged. Equipment is assigned once and uses the native inventory.

# 4.10.4 beta

- Fail closed when an installed optional claim provider throws: claim-aware placement no longer proceeds after silently disabling protection.
- Keep failed-provider protection active on subsequent placement checks and show the failure in claim diagnostics. Repair the provider and restart the server to clear the failure.
- No save format or mandatory dependency changes.

## 4.10.3 beta

- Add optional henkelmax Corpse compatibility: camp earthworks and gate assembly reject overlapping recovery bodies; native builders pause and resume the same blueprint jobs when bodies are removed.
- Protect fallback camp placement, native cell preparation and supply-barrel sites from overlapping corpses. No corpse inventory, owner, decay or NPC death behavior is changed.
- Corpse remains optional. Minecraft 1.20.1 Forge / Java 17 and the four required companion mods are unchanged.

## 4.10.2 beta

- Reject failed War Gate installation candidates and try other sites, restoring camp and fortification job queues after each rejected placement.
- Mark successful gate assembly and its terrain-restoration ledger for saving immediately, including callers that cannot start native worker jobs afterward.
- Reject malformed saved gate coordinates and invalid or missing block IDs safely; guard gate readiness, status, road preparation, repair and cleanup against invalid coordinate entries.
- Gate cleanup no longer restores old terrain over a later player/mod replacement. Original soil still restores after the gate is removed.
- Preserve current loot prices, odds, companions and save format. No interactive Minecraft playtest.

## 4.10.1 beta

- Keep loot-box purchase chat generic so it no longer reveals the prize before the mystery animation. Rewards are still delivered immediately and safely if the menu closes; prices and odds are unchanged.
- Resolve siege-controller APIs before mounting engineers. Verify both native controller and passenger attachment before activating control; failed native attachment dismounts the crew so deployment can retry rather than leaving a stranded passenger.
- Add regression coverage for all twelve loot-box outcomes and engineer attachment success, missing APIs/controllers, exceptions, mismatched vehicles, refused mounts and retries. No interactive Minecraft playtest.

## 4.8.0 beta

- Enemy builders receive a one-time veteran kit: Protection III / Unbreaking III diamond armor, diamond tools with Efficiency III, four golden apples and food. At least 60 health, +20% movement speed and knockback resistance; existing damage is preserved and equipment is never replenished each tick. Undelivered supplies remain stored until backpack space opens.
- Siege builders work through the night using native Workers building and storage jobs. Nearby village bells no longer stop this enemy crew; fleeing still takes priority. Morale recovers gradually while assigned to an active camp job. Player workers are unaffected.
- War Gates require a three-wide stone-brick road to the camp with clear headroom, shallow terrain cuts/fills and gradual steps. Planning rejects roads through water, player structures or steep drops. Builders receive the finite road materials; road completion is part of gate readiness. New camp walls leave the road entrance open.
- Saved gates attempt a road retrofit after the current job finishes. Road and terrain snapshots survive saves and restore on siege cleanup. The gate approach is protected from player block edits while active.

## 4.7.0 beta

- Fix engineer-only waves when the War Gate has no buildable site: search all four sides, prioritize the gate over camp upgrades, retain its ticking chunks, keep artillery off its pad, delay the assault/support until infantry can deploy, report missing gate blocks, and withdraw without rewards after three active minutes of blocked deployment.

- Fix builders stalling on flowers and connected fence/wall/stair states. Clear only small plants within camp jobs, snapshot both tall-plant halves, and preserve native finite supplies and solid-obstruction pauses.

- Fix enemy hiring: native aggro API no longer disables ownership initialization; reject enemy interaction and native hire events, including guards/workers and saved units.
- Four hero identities with distinct armor trims, named enchanted weapons and role abilities: Vanguard speed burst, Bulwark protection rally, Ranger evasive speed, Arbalist slowing shot. Abilities have 20–25 second cooldowns. Existing heroes gain role abilities; new hires receive the new equipment.
- Commanders carry a Siegebreaker axe and faction shield. Their three-second wall strike breaks one common building block in melee range, requires sight and defender-owned land, respects mob griefing/restoration caps, and interrupts on damage or displacement. Ten-second recovery after every attempt. One Last Stand rally at half health buffs up to six nearby troops for five seconds.
- Camp guards receive at least 50 health, +2 melee damage and knockback resistance once; existing health percentage is preserved. No respawning or equipment refills.
- Enemy shields carry faction colors and sigils, preserving durability and enchantments; archers retain their ranged loadouts.
- Reduce survival/building bag to 256 stone bricks, 32 stairs, 32 slabs, 64 planks, 16 panes, two chests, 32 food total, 32 torches and 16 coal. Faction setup funding and iron gear remain. Previously unpacked or partially opened supplies are preserved.

## 4.6.0 beta

- Builders construct a protected War Gate: blackstone landing pad, obsidian arch, crying-obsidian accents, amethyst pylons and a rotating portal effect. Land reinforcements use checked pad positions instead of camp roofs. Two guards defend the gate; it removes itself and restores its footprint when the siege ends.

- First login now grants two distinct starter bags instead of loose core/book gifts. Existing players receive the bags once on their next login as an upgrade grant.
- Faction bag: Codex, Siege Core, loom, two banners, three dye colors, and configured hiring currency covering faction creation, a first claim, two shieldmen and two archers plus a buffer (192 emeralds with native defaults).
- Survival/building bag: iron tools and armor, shield, food, bed, water bucket, workstations, torches, 1,024 stone bricks, 128 stairs, 128 slabs, timber, glass and storage. Unpacking fills available inventory space; remaining supplies stay in the bag, including in Creative mode.
- Ladders scan from locally blocked troops, check reachable approaches and defender claim ownership, respond every five seconds and support walls up to twelve blocks. Added a short physical crest movement to help troops step off the top rung. Ladder limits survive reloads.
- Breachers skip climbing/mounted units, target forward foot/head-height obstacles instead of digging below themselves, and prioritize headroom above a partially opened breach. All existing restoration and claim protection checks remain.

## 4.5.0 beta

- Expanded progressive waves to all twelve native Recruits combat types, including recruits, scouts, horsemen, nomads and assassin leaders. Civilian messengers and nobles remain outside combat waves. Very small/custom sieges may not have room for every type.
- Fixed reserved-wave indexing and rebuilt composition after reload. Tiny waves cannot over-allocate specialist slots.
- Role-based local formations: shield/infantry lines, loose ranged spacing, mobile wedges and compact support; narrow approaches use columns. Reachability, claim boundaries and ground clearance are checked before native walking orders. Combat, ladders and the core ring release formations.
- Native cavalry mounts advance with the raid and riders dismount near the core or at obstacles. Unclaimed raid mounts are cleaned up at siege end, including later chunk reloads.
- Starting Codex now focuses on Core, How to play and Journal. Removed obsolete perimeter/gate progress and crowded stats from its overview; updated the short guide for core capture, recapture and hero hiring. Panel fits scaled screen dimensions.

## 4.4.0 beta

- Separate native enemy factions give camp and captured claims distinct map colors. Core capture renames territory; recapture restores its original name.
- Dedicated camp guards use aggressive native hold orders at gate and rear flank posts; their assignments survive reloads.
- Builders receive three sequential, finite native blueprint extensions after initial fortifications: timber shelters with distinct roofs. Jobs wait for safe, clear terrain and living builders.
- New Hire a Hero tab: one level-10 featured recruit with diamond armor, 60 minimum health, and a melee damage bonus. Vanguard/Bulwark/Ranger/Arbalist offer odds: 40/30/20/10 percent. Price is 12 times native cost, minimum 256 configured currency.
- Hero stock shares the 15-minute faction rotation; exact offers and prices are visible before hiring. Existing two recruit and one worker slots are retained.
- Refreshed responsive cards, gradients and a brief reveal highlight. Network protocol 9 requires matching server/client versions.
- Native Workers blueprint reference: https://www.curseforge.com/minecraft/mc-mods/workers

## 4.3.2 — Recruits siege engineer spawn compatibility

- Fix the Recruits 1.15.2 engineer initialization ClassCastException confirmed in the supplied 4.3.0 log. Its native initializer still casts RecruitPathNavigation to GroundPathNavigation.
- Supply a temporary compatible navigator only during initialization, run the complete native initializer once, then restore native asynchronous navigation before spawning. Applies to mounted operators and infantry siege engineers. Unrelated initialization failures still discard the mob safely.
- Includes all 4.3.1 ladder traversal, claim-only formations and per-wave supplied artillery changes. 4.3.1 was held from CurseForge while this log-confirmed issue was fixed.

## 4.3.1 — Wall climbing, faster approach and wave artillery

- Raiders travel normally outside the defending faction's native Recruits claims. Formation orders only apply inside that territory, and release for combat, obstacles, climbing and the final capture approach.
- Remove rigid camp muster formations; troops gather near camp using normal walking orders.
- Assign raiders to tracked siege ladders: walk to the foot, climb physical rungs, then step onto a clear wall top. No teleporting. Limit each column to three assigned soldiers at a time.
- Climbing owns movement so objective redirects, formations, parkour and straggler retries do not interrupt it. Broken, blocked or timed-out routes return control to normal AI.
- Each assault wave requests a supplied ranged engine and native Siege Engineer, replacing the old 30% chance. Reserve an operator slot, retry blocked deployments, preserve completed wave support across saves, and retain faction/global entity caps.
- Mounted engineers receive native advance destinations and temporary chunk-loading tickets so they can drive out of camp. Defeated crews are not replaced within the same wave.
- Reject over-height ladder columns and blocked wall-top exits. Existing intact siege ladders are rediscovered from saved block records.

## 4.3.0 — Core command desk

- Replace the chest hiring screen with a responsive command desk: three offer cards on wide screens and compact rows at larger GUI scales. Shows role, price, rarity, sold state and refresh countdown.
- Reserve two slots for recruits and one for Villager Workers 2. Each slot rolls independently every 15 minutes using shared, persistent faction stock.
- Recruit weights: Recruit 50%, Shieldman 25%, Archer 20%, Crossbowman 5%.
- Worker weights: Farmer 25%, Lumberjack 25%, Miner 20%, Builder 15%, Cook 10%, Courier 5%.
- Hire workers through native ownership, faction, hiring events and unit limits. Use configured native villager-trade prices and Recruits currency.
- Preserve existing military offers, purchased slots and refresh time when migrating older saves. Reject stale, duplicate and out-of-range purchase requests on the server.
- Network protocol 8: install 4.3.0 on server and all clients.

## 4.2.1 beta

- Fix camps failing outside loaded terrain: bounded asynchronous scouting, temporary core/camp chunk tickets and wider candidate rings.
- Do not advance preparation or spawn assault waves until a camp exists; recover previously failed 4.2.0 camps automatically.
- Accept ordinary tall grass and flowers during terrain validation while preserving crops, water and structures.
- Initialize deferred builders, engines and guards after native claim registration; persist search/crew state and avoid duplicate crews on existing camps.
- Name camp claims for their attacking faction and replace misleading startup announcements with actual scouting/claim status.

## 4.2.0 beta

- Add faction armor palettes, rank trim colors/patterns and torso-mounted cosmetic faction banners for enemy soldiers and guards. Preserve weapons and native equipment inventories.
- Replace the core raid perimeter timer with a fixed-time numerical-majority contest around the core. Exclude creative/spectator players, passengers, and combatants on distant floors.
- Transfer the core's entire native Recruits claim on conquest; preserve surviving occupiers, stop further waves and support timed player/recruit recapture, including offline recruits.
- Persist occupation independently of an active raid, retain recapture rights across restarts/admin stops, disable occupied-core hiring/movement, and prevent competing native conquest timers.
- Show core capture/recapture status in the HUD and Codex. Keep physical gate destruction and terrain restoration.
- Add regressions for timer/presence rules, native transfer rejection, save/reload and faction/rank equipment.

## 4.1.1 beta — Keep banners off player bases

- Remove physical forward marker banners and wool plinths beside the defender's objective; the surface-height lookup could place them on a player's roof.
- Restrict faction banner placement to the actual camp footprint.
- Do not mutate an existing banner block entity when placement fails.
- Includes all 4.1.0 builder, native claim, faction-interface and starting guard changes.

## 4.1.0 beta — Native camp claims and starting guards

- Preserve native builder jobs during temporary player/block obstructions and resume when clear. Replace native construction timeout cancellation with progress diagnostics.
- Register new camps through Recruits' native claim manager with map synchronization, claim events, persistence, ownership and capture. Never overwrite existing claims; clean only leased Raider-owned claims, preserving captured player land.
- Search a wider loaded area to respect the native initial claim footprint and spacing.
- Add a finite starting garrison of two shieldmen, one archer and one recruit, subject to existing caps. Guards hold camp and never replenish after defeat.
- Open Recruits' actual faction menu and claim map from the Codex. Sneak-right-click the core for native faction management. Rename the lore tab so it is not confused with faction management.
- Correct the raid team prefix when looking up native claims and receiving native siege events.

## 4.0.0 — Siege Core and extended preparation

- Require a player-placed core in the faction's native Recruits Overworld claim for new sieges; beds no longer set targets. Existing active raids retain their saved target.
- Add a free first-login core, recovery recipe, occupation objective and protected placement during active sieges.
- Add three shared hire offers per faction: 80% recruit, 10% shieldman, 10% archer per slot, rotating every 15 server-runtime minutes. Use native prices, currency, hiring events and unit limits.
- Persist stock independently of core placement so replacements and server restarts do not reroll or restock it.
- Add a configurable 12-minute establishment, fortification and army muster period. Use a separate supplied Workers blueprint for the palisade; hold the first assault wave at camp before release.
- Include the builder visibility, supplied native siege operators and all-four-required dependency changes below.
- Fix siege operator retry timing for server ticks that do not align with multiples of 100.

# Changelog

All notable changes to Faction Raids are documented here.

## 3.7.0 - 2026-09-08

- Require Recruits, Workers 2, Small Ships and Siege Weapons in Forge metadata and CurseForge release relations.
- Keep builders at camp after construction finishes; retain the crew if native setup falls back to scripted building.
- Expand safe camp searches and announce camp coordinates, crew/equipment counts, or the absence of a safe site.
- Deploy ranged engines outside the palisade in collision-checked slots. Legacy unmanned ram/tower choices use ballistas with native operators.
- Initialize hostile siege operators, provide ammunition and food, mount native controllers after the warning period, and avoid replacing killed operators.
- Preserve unloaded engine cleanup identities and prevent raider catapult shots from causing untracked terrain explosions.

## 3.6.1 - 2026-09-07

- Clear stale formation markers for vanilla auxiliary enemies after reload, so their direct movement is never suppressed by an unavailable Recruits formation API.
- Includes the supplied native Workers camp construction and movement fixes from 3.6.0.

## 3.6.0 - 2026-09-07

- Workers 2 enemy builders receive native build areas, private supply storage stocked once from the blueprint's actual material list, tools, and food. Construction pauses at night without using its timeout budget.
- Native camp work records the full restoration footprint before construction. Interrupted jobs cannot mine player replacements; areas and crew are retired together. Ownership, supplies and pending jobs survive saves without replenishing stock.
- Formation marching no longer competes with direct objective navigation. Soldiers leave formation to fight or capture, and boat passengers are excluded from walking orders.
- Straggler recovery retries walking instead of teleporting to the army centroid. Sideways movement around obstacles counts as progress; only prolonged stationary stalls are retired.
- Reviewed the supplied 3.5.2 session log: normal exit, no crash exception. Config default-generation warnings do not indicate a failed launch.

## 3.5.2 - 2026-09-07

- Raider boats detect twenty seconds of actual stalled approach progress, including boats wedged in water. Fixed the old timer running once per second as if it were once per tick.
- Landing checks account for ship width, nested Small Ships seats, dry solid ground, collision space, world borders and loaded chunks. Spread landed raiders across safe positions; keep them aboard if no safe nearby landing exists.
- Disembarked Recruits have native mounting orders cleared and ground navigation directed toward the active siege objective. Players and unrelated passengers are not ejected.
- Persist convoy team/beach tags on vessels and rebuild tracking from loaded raiders after restart or chunk reload. Recover older untagged boats without assuming ownership or deleting them afterward.

## 3.5.1 - 2026-09-07

- Enemy effort bonuses now accelerate breach/occupation only while attackers outnumber defenders inside the active objective. Clearing or matching their presence correctly reverses pressure, even with queued bonuses.
- Show live objective attacker/defender counts and Capturing/Recovering/Held/Secure status in the boss bar, periodic action bar and Codex overview.
- Banner sabotage now removes the current wave without granting kill credit. Retreated IDs persist across saves so unloaded raiders cannot return or be counted again by reconciliation. Reconciling also excludes pre-raid scouts.
- Opening reconnaissance reports name the marked defensive point and explain how to hold it. Camp sabotage guidance explains the campfire, banner and supply-barrel effects; messages clarify that later waves still attack.
- Canceled block-break events no longer activate camp sabotage. Camp handling runs after normal protection handlers.

## 3.5.0 - 2026-09-07

- War camps gently level soil across their interior, with cuts and fills capped at three blocks and a three-block transition into surrounding terrain. Adjacent surface columns must remain within one block of height so camp exits do not end at a cliff.
- Validate the entire site before earthworks. Reject water, non-soil foundations, containers, obstructed building space, excluded claims, unloaded chunks and excessive excavation. Try another site when unsuitable.
- Snapshot every cut and fill in the existing saved camp ledger. Restore ground before vegetation during cleanup; natural dirt/grass changes remain restorable, while different player replacement blocks are preserved.
- New `levelCampTerrain` setting defaults to true. Leveling is disabled when camp cleanup is disabled. Existing camps are not retroactively leveled.

## 3.4.0 - 2026-09-07

- Villager Workers 2 builders now assemble the actual war camp's towers, forge and tents a few blocks at a time. Builders walk to nearby work positions and swing while building; missing, dead or fleeing workers cannot build remotely.
- Camp workers belong to the Raiders faction, not the defending player. They stay out of player job areas and storage routines and do not count toward assault waves.
- Camp construction jobs, elapsed time and worker IDs now survive server restarts. Unloaded crew are cleaned up when they return after a siege ends.
- Every construction placement uses the siege's existing block snapshot and restoration system. Occupied blocks and spaces containing living entities are skipped.
- Removed the broken parallel blueprint/lumber-area spawn path. Native tree cutting is disabled because it bypassed terrain restoration; the old lumberjack cap remains readable for config compatibility.
- Camps still construct automatically without Workers or with compatibility disabled. Stalled decoration jobs expire without delaying assault waves. Fixed the old seconds-versus-ticks construction delay by replacing it with a bounded per-pass block budget.
- Updated Workers 2 ownership calls and reject incompatible NPC initialization before registration.

## 3.3.2 - 2026-09-07

- Prevent a failed enemy spawn initializer from crashing the server tick. In particular,
  Recruits 1.15.2 siege engineers can cast their custom navigator to an incompatible
  vanilla class. Discard failed mobs before registration and initialize a fresh pillager
  at the same position, preserving wave progress without retaining partial entities.
- Fix ballista controller attachment using the shared Recruits siege-controller API.
- Snapshot a sapper's entire breach before changing any blocks. Preserve both halves of
  doors, including doors at the blast boundary, and respect the restoration cap atomically.
- Preserve the first terrain snapshot when a temporary camp block is placed repeatedly;
  restore the terrain beneath camp blocks that defenders already destroyed.
- Apply camp-construction timeouts in game ticks instead of counting each one-second
  lifecycle pass as a single tick (previously a 180-second limit lasted about an hour).
- Check the siege dimension before applying camp sabotage effects, and process breaks
  after protection handlers have had an opportunity to cancel them.
- Discard raiders that remain stuck after rescue so entity reconciliation cannot add
  them back into the wave. Count them as escaped and clear their tracking records.
  Keep raiders holding the current breach/capture objective, fighting visible defenders,
  or riding vehicles out of straggler rescue.
- Add regression coverage for spawn failure isolation, door snapshots, camp timeouts,
  repeated camp placements, and straggler retirement; run the checks in the Java 17 build.

## Unreleased — Refactor: Scalability Foundation

No gameplay changes. Pure internal refactor to make future features easier and safer to add.

- Added `ModConstants` for tick math, NBT tag keys, boss-bar defaults and the shared chat prefix.
- Added `FactionLogger` (single SLF4J logger) and routed swallowed command failures through it.
- Added `raid/RaidTags` to encapsulate the `FactionRaidsTeam` / `FactionRaidsRole` persistent-data access
  that was previously duplicated in ~15 call sites.
- Added `raid/RaidBossBars` to own the per-team `ServerBossEvent` registry.
- Added `command/RaidCommands` and moved the entire `/factionraids` Brigadier tree out of `RaidEvents`.
- Added `command/PlayerCommand.run(...)` to remove the repeated player-resolution try/catch that
  appeared 16 times in `RaidEvents`.
- Added `ARCHITECTURE.md` documenting the new package layout and the ordered plan for splitting
  `RaidEvents` further without behavior changes.

## 2.7.0 - 2026-09-04

- Replaced the six-row chest dashboard with a dedicated client-rendered tactical command screen.
- Added a responsive campaign-table layout, custom cards and buttons, live strategic and gate-breach
  progress bars, army totals, war assets, rewards and reconstruction status.
- Added a versioned Forge network channel with server-authoritative dashboard actions and validation.
- Required matching Faction Raids versions on the server/host and every client for the custom UI.
- Added physical breaching for doors, trapdoors, fence gates, fences and iron bars near tracked
  invasion breachers; ordinary walls, storage, machines and unrelated blocks remain protected.
- Added visible cracking, impact sounds, particles and configurable breach times.
- Persisted exact registry names and block-state properties before any siege removal.
- Automatically restored siege-breached defenses after victory, defeat or administrative stop while
  preserving any player replacement placed during the battle.
- Added a configurable restoration safety cap and persisted in-progress breach work across restarts.
- Expanded the temporary camp into a field-command pavilion with a platform, canopy, rear wall,
  supplies, banners and a forward palisade.

## 2.6.0 - 2026-09-04

- Replaced the default generic assault roster with hostile Villager Recruits soldiers.
- Added Recruits shieldmen, bowmen, crossbowmen, captains, assassins, patrol leaders and siege
  engineers across escalating waves while retaining select vanilla raid specialists.
- Put invading Recruits into their native raid combat state and prevented same-invasion friendly fire.
- Added a configurable fallback switch for servers that prefer the previous vanilla-illager roster.
- Added real temporary war camps built from vanilla campfires, tents, supply blocks and banners.
- Spawned assault squads around their camp so reinforcements now visibly deploy from it.
- Added safe camp cleanup that removes only unchanged blocks placed by Faction Raids.
- Added a one-time migration attempt that gives already-active upgraded sieges a physical camp.
- Replaced abstract perimeter pressure with a compact, concrete approach-side breach objective.
- Added visible red-banner breach markers and required local numerical control for progress to rise.
- Added war-camp and marked-objective coordinates to `/factionraids status` and camp information to
  the command dashboard.

## 2.5.0 - 2026-09-04

- Added a configurable outer-perimeter breach phase before stronghold occupation can begin.
- Made unengaged invasion forces rally at the approach-side breach point until the perimeter opens.
- Added contested breach progress and configurable defender-driven breach decay.
- Added 25%, 50% and 75% breach warnings, action-bar pressure updates and a cinematic breach event.
- Added breach phase and pressure to `/factionraids status`, `/factionraids debug`, the boss bar and
  faction dashboard.
- Added migration-safe persisted breach state; already-deployed 2.4 raids continue as breached.
- Made Small Ships and Siege Weapons remember the faction of their last crew member after dismounting.
- Allowed boarding equipment to register it or organically transfer it to a different faction.
- Restricted Worker protection to Workers belonging to the faction actually being raided.
- Made the effective breach radius remain outside the capture ring even with an invalid config pair.
- Kept the siege non-destructive: no player blocks, claims or companion-mod controls are modified.

## 2.4.0 - 2026-09-04

- Added optional compatibility for Villager Workers, Small Ships and Siege Weapons.
- Protected Villager Workers from illagers spawned by Faction Raids while preserving native work,
  take-cover and flee AI.
- Added nearby allied Worker counts to the faction command dashboard.
- Recognized only crewed faction Small Ships and Siege Weapons as war assets, preventing abandoned
  equipment from inflating siege strength.
- Added a dedicated War Assets dashboard panel for crewed ships and siege engines.
- Added configurable, capped assault scaling based on crewed defensive equipment.
- Added optional-mod state to `/factionraids debug` diagnostics.
- Kept every integration class-link-free so Faction Raids remains safe when optional mods are absent.
- Limited compatibility scans to wave planning and dashboard refreshes to avoid continuous overhead.

## 2.3.0 - 2026-09-04

- Added a polished six-row faction command dashboard using Minecraft's lightweight vanilla interface.
- Made `/factionraids` and `/factionraids menu` open the dashboard without requiring an extra UI library.
- Added live stronghold, siege phase, reinforcement, occupation, Recruit-army and reward information.
- Added dashboard buttons for refreshing the automatic stronghold, starting a practice siege and opening help.
- Added guaranteed configurable emerald rewards for every online faction member after an eligible victory.
- Added configurable per-wave and commander-defeat emerald bonuses.
- Kept the existing datapack-controlled campaign loot as an additional victory reward.
- Marked automatic scheduled sieges as reward eligible and practice sieges as non-rewarding by default.
- Added an opt-in configuration switch for servers that deliberately want rewarded manual sieges.
- Persisted reward eligibility across restarts and active-siege migrations.

## 2.2.0 - 2026-09-04

- Added cinematic title and subtitle overlays for siege arrival, the command assault, victory and defeat.
- Added configurable action-bar updates for reinforcements, commander defeat and occupation milestones.
- Added configurable smoke arrival effects for staged assault squads.
- Added dynamic boss-bar colors for warning, active combat, critical occupation and offline pause states.
- Standardized active-siege announcements with a recognizable Faction Raids prefix.
- Added persistent deployed, defeated and lost-contact statistics across server restarts.
- Added immediate invasion-mob death tracking for player and Recruit kills.
- Added post-siege battle summaries with enemy totals and elapsed time.
- Reworked `/factionraids status` into a readable multi-line stronghold or battlefield report.
- Added `/factionraids help` with the essential player workflow.
- Preserved automatic migration for every 1.x, 2.0 and 2.1 world.

## 2.1.0 - 2026-09-04

- Added persistent staged-squad deployment for smoother, more organic waves.
- Added configurable squad size and interval without weakening global performance caps.
- Added Recruit-army strength scaling with a separate configurable ceiling.
- Added breacher, captain, marksman, war-caster and commander battlefield roles.
- Added a named final-wave siege commander with configurable maximum-health scaling.
- Made commander defeat remove 30 seconds of accumulated occupation pressure.
- Added queued reinforcements, squad counts and commander status to diagnostics and boss bars.
- Added separate faction and Recruit-ownership compatibility diagnostics so one API fallback cannot disable the other.
- Stopped automatically treating public world spawn as a stronghold for players without beds.
- Added an opt-in world-spawn fallback for servers that deliberately want it.
- Fixed later-wave countdown warnings not resetting after the first wave.
- Persisted all new deployment and commander state across server restarts.
- Pinned ForgeGradle and declared build repositories for reproducible public builds.

## 2.0.0 - 2026-09-04

- Made Villager Recruits 1.15.2+ a required dependency.
- Added zero-command stronghold registration from player respawn points.
- Added automatic Recruits-faction membership and leader detection.
- Allowed automatic invasions to target any online faction member's home.
- Added coherent siege fronts and directional war-camp warnings.
- Made invaders advance on the stronghold instead of waiting for players.
- Added automatic participation for nearby allied Recruit soldiers without replacing saved orders.
- Allowed invasion forces and Recruits to select each other as combat targets.
- Replaced distance-only defeat with a configurable, contested stronghold occupation system.
- Added occupation recovery, milestone warnings and boss-bar pressure reporting.
- Disabled legacy abandonment defeat by default while preserving it as an option.
- Preserved manual anchors and named territories for server events and legacy worlds.
- Added automatic migration for 1.x saved data and an opt-in command for legacy homes.

## 1.2.0 - 2026-09-04

- Added persistent internal faction rosters independent of scoreboard-team timing.
- Added owner-controlled member add, remove and list commands.
- Added up to four named defense points per faction, including the permanent home point.
- Added automatic and manual selection of named invasion targets.
- Added datapack-overridable victory loot alongside configurable experience rewards.
- Added illusioners to late-game wave composition.
- Added player-facing diagnostics for membership, TPS, targets and tracked enemies.
- Added administrator raid reconciliation.
- Fully froze loaded invasion mobs while every faction member is offline.
- Automatically removed orphaned tagged enemies when their chunks load after a raid ends.
- Added automatic migration of 1.0/1.1 anchors and active raids to the new data format.

## 1.1.0 - 2026-09-04

- Paused active invasions while all targeted faction members are offline.
- Added missing-entity grace periods and periodic tagged-mob reconciliation for chunk unloads
  and server restarts.
- Tagged and tracked vexes summoned by invasion evokers.
- Added stronger terrain, fluid, headroom and collision validation for spawn locations.
- Added safe retries when a wave cannot find a valid entrance.
- Added configurable global raider and concurrent-invasion caps.
- Added TPS-aware wave delays.
- Added anchor ownership and protected management commands.
- Added administrator list, remote stop and anchor removal recovery commands.
- Added automatic idle-anchor display-name/team refresh.
- Added glowing outlines for the final three enemies.
- Added configurable experience rewards for successful defense.
- Made the mod server-authoritative so unmodified Forge clients may connect.

## 1.0.0 - 2026-09-04

- Added per-faction territory anchors.
- Added automatic player-focused illager invasions.
- Added five configurable escalating waves.
- Added scoreboard-team faction membership support.
- Added persistent anchors, cooldowns and active raid state.
- Added boss-bar progress and faction-only announcements.
- Added villager and iron-golem protection from invasion mobs.
- Added abandonment-based defeat instead of villager-based defeat.
- Added hard per-invasion mob caps for integrated-server performance.
- Added administrator stop command and player test command.
