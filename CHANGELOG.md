Warning: truncated output (original token count: 30345)
Total output lines: 1153

## 4.51.3
- The final 32 camp-search attempts now sample distinct ground between existing search rings rather than revisit already rejected chunks. The same 200-site cap and maximum search distance remain.

## 4.51.2
- Fallback camp planning measures ground beneath supported, canopy-covered tree trunks and clears qualifying timber and natural leaves within the work area, saving removed blocks for cleanup.
- Shallow water up to three blocks deep can be filled with dirt over validated soil. Camps still require a dry approach, bounded earthworks, protected claims and no lava or containers.
- Stop the legacy paver from reworking an already validated and restored-tracked camp floor and shoulders.

## 4.51.1
- Camp earthworks now smooth the approach shoulder against real surrounding ground instead of rejecting every uneven step outside the camp. The camp floor remains level, with dirt support and walkable transitions.
- Camp search logs now identify terrain rejection reasons, including water, protected blocks, excessive relief, edge transitions and work budgets.

## 4.51.0
- Establish a settlement with two named civilians per faction. Recruit more in the Civilians tab for 16 Treasury emeralds.
- Civilians use vanilla professions, trades, beds and breeding, with faction territory boundaries and saved ownership. Supply beds, food and matching workstations. Population is capped at 64 per faction.
- Each living registered civilian contributes one emerald per full in-game day to the Treasury. Tax clocks and starter grants survive reloads and core relocation; occupation or a missing owned core pauses taxes.
- Hire previews and purchases share server-saved equipment, including armor trims and compatible gear. Ordinary recruits and civilians use simple first names.

## 4.50.13
- Camp leveling can cut natural soil mounds and fill hollows up to six blocks deep with solid dirt. Every changed block is tracked for cleanup.
- Existing terrain-change budget, gentle boundary transitions, water, structure and claim protections remain enforced.

## 4.50.12 — Better earthwork elevations
- Camp fallback searches choose a balanced ground height across the full site instead of forcing the camp to match a mound or hollow under the scout.
- The existing three-block cut/fill limit, complete terrain validation, claim protection and restoration budget still apply. Water, structures and steep terrain remain grounds for rejection.

## 4.50.11 — Construction panel polish
- Reopening Live construction fetches a fresh snapshot immediately instead of showing the previous result for up to two seconds.
- Long construction labels and status text no longer cut an emoji or other supplementary Unicode character in half when sent to clients.

## 4.50.10 — Live construction and modular walls
- Defenses now includes an in-hub live construction list with progress, builder activity, current supply requests and hover details for marker/builder locations. Lists refresh every two seconds while viewed and cover up to twelve of your loaded nearby commissioned jobs.
- Added Wall Section, Wall Corner and Wall Stairs plans, costing 90 / 90 / 100 Treasury emeralds when commissioned. Matching raised walkways snap to a five-block world grid; rotate pieces to line up their open ends at the same ground height.
- Two plan pages keep all six designs accessible on small screens. Existing structures, chat reports, job ownership, native Workers materials/work hours and shovel projections are preserved.
- Placement previews and final checks still protect existing blocks, claims, entities and unloaded terrain. Client and server must update together (network protocol 17).

## 4.50.9 — Clearer Command Center tabs
- Army cards give names more room and show hire costs directly on their buttons, including compact screens.
- Loot rows separate titles, reveal progress and purchase controls at small GUI sizes; rewards remain hidden until unsealing finishes.
- Territory cards show their upgrade descriptions. Defenses show footprints, commission costs and construction-report scope on roomy screens.
- Treasury distinguishes online and offline members, keeps roster headings clear of scroll counts, and handles large activity totals safely.
- Intel adds search and Clear controls across Units, Enemy Lore and How to Play, with Ctrl+F focus and corrected banking, territory and native builder guidance.
- Purchases, plans, prices and server authority are unchanged.

## 4.50.8 — Defense previews and construction reports
- Defense plans now preview before charging: use ground to select the near-center anchor, sneak-use to rotate, use the same anchor again to confirm, or use in air to cancel. Previews expire after two minutes and are bound to the owner and dimension.
- Held plans show a transparent block outline, red terrain obstructions, a yellow confirmation anchor, exact material counts and the Treasury price. Server checks refresh once per second and run again on confirmation.
- The Defenses tab offers a Construction report, also available through `/siegeoverhaul builds`. It lists progress, native material requests, work/rest/order status, and builder/marker coordinates for your loaded commissioned jobs within 128 blocks.
- Reports and previews do not provide materials, move workers or change native work schedules. Existing plans, jobs and prices are preserved.

## 4.50.7 — Player-built defenses
- The core's new Defenses tab offers free Archer Barricade, Watchtower and Gatehouse plans. Use a plan on level ground to choose the near-center anchor; the structure extends in your facing direction.
- Commissions cost 120 / 300 / 450 Treasury emeralds only after an owned idle builder accepts the job. Players supply cobblestone and oak planks through their own Workers storage area with Builders enabled.
- Barricades provide a firing step; watchtowers have an open deck and broad steps; gatehouses have a raised deck over an open three-block passage. Station defenders with normal recruit commands.
- Whole-site checks protect claims, existing blocks, fluids, entities, world borders and build heights. Reuses native Workers blueprints, supply trips, work hours and persistent player-job recovery; failed placements keep the plan.

## 4.50.6 — Capture beacon
- Enemy core captures show a 96-block beacon beam to nearby defending faction members. Its color smoothly follows progress: red at 0%, orange at 25%, yellow at 50%, lime at 75%, green at 100%.
- Ties keep the current color; lost progress reverses the colors. Completion stays green briefly, then expires. Interrupted effects expire within three seconds, and world changes clear them.
- Uses vanilla beacon geometry and texture with no terrain changes or beacon gameplay effects. Updates once per second, renders within 192 blocks, and has a bounded client cache.
- Added a client Capture beacon toggle. Network protocol updated; server and clients must use matching versions.

## 4.50.5 — Safe arrivals for hired units
- Core Guards, recruits, heroes and workers avoid damaging floors, fire, freezing powder snow, portals and fluid-filled spawn spaces.
- Placement checks the unit's full body, footing, loaded chunks and world border. Starter Core Guards also remain fully inside their faction's claim.
- A stale pending guard record no longer blocks delivery for another valid core record at the same location. Failed placements preserve the free guard grant and do not charge for a hire.

## 4.50.4 — More useful items
- Aimed spellbook projectiles pass through friendly troops, players and other ineligible entities, while still stopping at blocks and damaging only siege enemies.
- Forge Embers prioritize damaged gear in the other hand, then the most worn equipped armor piece. Repair remains 25% of maximum durability, with the cap raised from 80 to 400 points. Failed repairs do not consume a charge.
- Owl Seals no longer attempt to refresh permanent enemy outlines.
- Removed Tidehook fishing rods, Harvest hoes and Deep Breath underwater draughts from new loot boxes and the creative catalog. Poseidon's higher-tier combat tridents remain available. Reward stack counts and box prices are unchanged.
- New provisions, ammunition and potions use their ordinary actions without the weaker alternate sneak-use blessings. Existing saved blessed supplies and retired gear remain usable; no registry entries or saved items are deleted.

## 4.50.3 — Starter Core Guard
- A faction's first usable Siege Core grants one free native hired shieldman named Core Guard, with an iron sword, shield, basic armor and eight bread. Native ownership, unit limits, hold-position behavior and normal commands remain in control.
- The lifetime faction grant survives saves, core relocation and the guard's death. No replacement guards or recurring supplies are generated.
- Placement delivery waits until the next block tick. Blocked spawns, rejected hiring and unavailable placers preserve the grant; open the owned core to retry. Existing factions can receive their one guard by opening their core after updating.
- The starter guard does not debit personal funds or the Treasury. Later hires and native ongoing upkeep retain their normal rules.

## 4.50.2 — Larger battles, gentler opening enemies
- Preserve full army sizes, defender/equipment scaling, staged squads and population caps.
- Newly spawned invading NPCs in waves 1-4 have 40%, 30%, 20% and 10% less health and deal the same percentage less attributed damage, including melee and arrows. Wave five onward retains existing strength and scaling; reductions never repeat at later endless-siege chapters.
- Enemy heroes cannot join the first three waves. Existing spawned mobs and already queued wave budgets are preserved.
- New `gentleOpeningWaves` server setting defaults to true, including on existing configurations. Disable it to restore the previous opening balance for future spawns and hero planning.

## 4.50.1 — Commissioned builder jobs
- Hired wall builders keep their assigned blueprint when starting work or returning from a supply trip, instead of switching to another nearby construction site.
- Wall approach paths now wait for Workers' asynchronous pathfinder to finish. Stale, blocked and timed-out results are discarded safely.
- Workers still controls materials, construction, sleep and owner commands. Wall prices and saved blueprints are unchanged.

## 4.50.0 — Olympian armory and aimed spellbooks
- Replaced bulky weapon cuboids with original pixel skins using vanilla handheld, bow and crossbow models and draw stages.
- Creative spellbooks now fire aimed lightning, fireballs and ice bolts; holding Undertow channels a water stream. Projectiles stop at blocks, spare players/allies and never damage terrain.
- Restored hero names after native spawning and repaired untouched generic names on saved heroes while preserving player renames.
- Aligned new starter camp entrances with the sanctuary approach and later perimeter; allowed safe single-file access through existing offset gates before the outer avenue is built; positioned two sentries beside the saved enemy core.
- Switched new faction standards, shields and map identities to vanilla banner compositions.
- Removed Forge Stock, building blocks, ladders, scaffolding and tribute materials from new loot rolls and the creative catalog. Existing saved items remain usable; box prices stay 16/48/96 emeralds.
- Reserved separate space for decree descriptions and purchase status.

## 4.49.11 — Camp construction safety
- Native camp construction waits when a guard or visiting living entity occupies an unfinished blueprint cell. Active construction crew keeps its native movement to avoid self-blocking.
- Removed the obsolete future-rebrand startup announcement and stopped identifying the current config as a legacy file.
- No changes to combat, loot prices, dependencies or saved items.

## 4.49.10 — Held items and readable tooltips

- All 128 Olympian weapon models use third-person transforms anchored to their actual grips, with matching left/right hands and stable bow/crossbow draw states.
- Relic, spellbook, spawn-egg and supply descriptions separate activation, effect and cooldown information.
- Long Siege item descriptions wrap to the GUI width while retaining colors, enchantments and attribute information.
- New Olympian loot lore uses upright text. Item powers, prices, saves and dependency requirements are unchanged.

## 4.49.9 — Doors and Olympian casting

- Siege raiders can open nearby wooden doors along their path with Recruits' asynchronous navigation. Opened doors stay open for following troops; iron doors still need breaching.
- Door checks are bounded, server-owned and respect foreign claims. Friendly recruits retain their own door behavior.
- Storm, fire and tide casts have original branching bolts, flame plumes and curling wave geometry, distinct arm poses and release sounds.
- Existing spell timing, damage, cooldowns, friendly-fire rules, reduced-flash settings and particle limits remain intact.

## 4.49.8
- Stuck raiders reject unsafe or unreachable random fallback destinations and try the wider route before giving up.
- Cached detours are discarded when their landing becomes blocked, flooded or unloaded.
- Tempest, Inferno and Undertow spellbooks now have distinct lightning, fire and water artwork from Hungry22's Weekly Dot pack, with the original author notice and source credit included.

## 4.49.7
- Commissioned wall builders can route to reachable ground beside a buried blueprint marker or unreachable work cell. Native materials, work hours and owner commands remain in control.
- Stuck recovery no longer teleports raiders into the core approach area or onto much higher roofs.
- Reachable floor beside a core is used even before the breach phase completes, rather than routing to the solid core block.
- Raiders no longer treat thin roofs as cave terrain or abandon a reachable indoor route for cave recovery.
- Diplomacy sends one notification for a two-way change; active-siege repairs are silent and unchanged relations are not rewritten.

# 4.49.6 — Clearer camp courtyards

- New camps leave the central sanctuary clear instead of placing the campfire in the core's preferred spot.
- Starter pavilions sit farther back and apart; supplies, the banner and forge occupy the side courtyards in every camp orientation.
- Core placement requires an unobstructed ring around the sanctuary and keeps that space clear of later construction.
- Existing camp positions and capture progress are preserved.

# 4.49.5 — Blessed supplies and Curios

- Optional Curios 5.14.1+ support: wear Forge Ember, Owl Seal or Sun Laurel in the charm slot for a passive defense or health bonus. Equipping never spends charges; no new required dependency.
- Blessed supplies now carry usable powers, including Rampart absorption, Forge resistance, Moon Sight, Deep Breath and safe-descent blessings. Sneak-use in air to activate; tooltips show the exact cost, effects and durations. Ordinary use, crafting and placement stay available.
- All supply blessings share a ten-second cooldown. Ineffective uses spend nothing; stronger existing effects stay intact. The power stays with the item through saving and inventory transfers.
- Reward-eligible siege raiders killed by the defending faction have a 2.5% chance to drop a sealed box (2% Common, 0.5% Uncommon). Creative kills, untracked spawns, canceled drops and disabled mob loot do not award boxes. Wave rewards remain separate.
- Loot-box prices remain 16/48/96 emeralds. Update the server and every client together.

# 4.49.4 — Olympian utility relics

- Added three real consumable relics with separate item IDs, 3D models and activation effects: Hephaestus' Forge Ember repairs equipment in the other hand; Athena's Owl Seal outlines nearby siege enemies; Apollo's Sun Laurel removes five combat afflictions while preserving beneficial effects.
- Every sealed box includes one relic stack alongside its enchanted equipment and provisions. Rare/Epic boxes supply two uses; lower tiers supply one. Total reward stacks and box prices (16/48/96 emeralds) stay the same.
- Added stone bricks, scaffolding, ladders, fire-resistance and water-breathing supplies. Extra steak is replaced by building stone; the guaranteed food reward remains.
- Infinity Sunbows receive ordinary arrows whenever the ammunition category rolls. All new rewards are in the Creative catalog.
- Relics are server-authoritative, have 10-second cooldowns, and are spent only when their action succeeds. No terrain edits, PvP targeting or new dependencies.

# 4.49.3 — Olympian weapon presentation

- Refined Olympian blades with bright cutting edges, slimmer grips and distinct deity guards; caster staves now have eight different head designs.
- Cleaner metal, inlay and grip materials replace noisy full-face building textures across all 128 weapon model states.
- Inventory models fit within their slots, including long staves and spears. Bow and crossbow draw states share the same framing so icons do not jump while loading.
- Held-item alignment, draw timing, hero casting, enchantments and weapon stats are unchanged.

## 4.49.2 — Weapon animation timing

- Olympian bow and crossbow skins follow Minecraft's draw-stage timing, including Quick Charge. Crossbows no longer look fully drawn before their loading animation reaches its final stage.
- Includes the Command Center navigation and balance improvements from 4.49.1.

## 4.49.1 — Command Center navigation

- Shared Treasury funds and your personal purse stay visible on every page. Select the Treasury balance to manage funds.
- Narrow windows show readable tabs with previous/next controls instead of squeezing every label. Ctrl+Tab, Ctrl+Shift+Tab and scrolling over the tab bar switch pages.
- Page headings have separate title and description lines. Intel sections support keyboard focus and retain their individual scroll positions.
- Treasury and Intel scroll only under the pointer. Intel skips painting off-screen archive cards.

## 4.49.0 — Olympian creative catalog

- The Siege Overhaul creative tab includes every registered mod item, all loot-box tiers, named Olympian rewards, hero equipment and faction banners.
- The Olympian Arts reference book explains all twenty hero powers and the distinction between hero abilities and held equipment.
- Added Creative-only Tempest, Inferno and Undertow spellbooks with a one-second cast, saved cooldowns and existing casting effects. Only visible siege enemies are affected; no terrain damage.
- Commissioned walls enable the native Workers 2 projection for plans up to 1,024 blocks and report the shovel marker coordinates. Larger plans retain manual projection to avoid rendering the entire wall every frame.
- Spawn eggs check placement, finish outfitting before insertion, and preserve the egg on failed or cancelled spawns. Spawned units retain native Recruits hiring and commands.

# 4.48.1 beta

- Olympian crossbows now show a rocket when loaded with fireworks and a bolt when loaded with arrows.
- Charging no longer shows a loaded bolt before the weapon is ready.
- Crossbow strings remain attached to the bow tips throughout the draw.
- Ammo, enchantments, prices and combat behavior are unchanged.

# 4.48.0 beta

- Tempest, Inferno and Undertow now have a visible one-second casting wind-up, a release pose and expanding tidal or fire rings. A cast stops if its target dies, disappears or becomes friendly.
- Added client settings for casting poses, particle density, effect distance, sound volume and reduced flashes.
- Hero weapons and tagged Olympian loot now use original 3D warblade, spear, staff, bow, crossbow and tool designs. Existing item stats, enchantments and saved items are preserved; ordinary equipment keeps its normal appearance.
- Bow and crossbow skins show their draw stages. Vanilla tridents, shields and armor keep their existing renderers.
- Server and clients must update together. Minecraft 1.20.1 Forge, Java 17 and companion requirements are unchanged.

# 4.47.27 beta

- Camp scouting now has a four-minute active-time limit for each search pass. Slow or unavailable terrain can no longer keep preparation paused for hours; after the natural and enabled earthworks searches fail, preparation begins for the existing camp-less assault.
- The last selected camp site is checked before the candidate limit ends a pass. Searches never select beyond their 200-site budget, and their time limit survives saving and reloading.
- Site safety, protected claims, reversible terrain work and the full preparation period still apply.

# 4.47.26 beta

- Enemy bridge builders stop construction when the army captures your Siege Core, freeing them to hold the occupied territory. Unfinished crossings retain their saved progress, material costs and deadlines.

# 4.47.25 beta

- Marching troops gain a temporary 20% base movement-speed bonus while unengaged and more than 64 blocks from the objective, including troops following native formation orders. Combat, occupation, passengers and working crews use their normal pace.
- Ordinary infantry can build checked temporary crossings even far from the target. Dismounted melee recruits, archers and flankers share the same finite bridge job system.
- Crossing searches run every five seconds and rotate through the army instead of repeatedly checking only the first troops. Saved material/attempt limits, safe shores, excluded claims and tracked cleanup still apply; only one crossing job runs per raid.

# 4.47.24 beta

- Enemy camps attempt checked sanctuary placement immediately when established, before the crew spawns. Players can capture the enemy core during preparation to stop the assault, using the same occupation and claim checks. Unsafe sites still wait for safe placement.

- Player perimeter walls follow actual surface height instead of clamping to the core and queuing work inside hills. Water, unsupported ground and sections without clear nearby standing space inside the claim are skipped and reported. Existing commissions are not rewritten.

- Camp terrain checks accept dry sand, red sand, gravel and clay, so beaches are no longer rejected simply for their ground material. Water, lava, protected claims and structures remain excluded.
- Expanded local camp searches now check a balanced grid around the scout position. All candidates keep their terrain footprint inside the loaded search neighborhood instead of drifting far in one direction.
- Gentle beach earthworks use the existing bounded, reversible terrain plan and preserve original blocks across saves.

# 4.47.23 beta

- Enemy camp builders spawn on checked surface ground instead of nearby underground air pockets.
- Fallback builders choose reachable work positions with their native navigation. Partial paths no longer redirect them toward blocked pockets.
- Fallback construction waits for the builder to reach safe footing before placing nearby blocks, preventing work from starting while the builder is still inside a planned wall.
- Standing checks reject hazardous ground, blocked headroom and pending construction through the worker's body, while preserving finite jobs and supplies.

# 4.47.22 beta

- New enemy core sites require a checked, three-wide walking route from the main camp avenue through the starter palisade to the sanctuary. Placement waits when no safe route is available.
- Three-wide stone steps lead onto the raised core platform. Saved route reservations keep later buildings, supply barrels and terrain work from blocking the approach.
- Existing cores and capture progress retain their saved positions and state.

# 4.47.21 beta

- Camp builders preserve the planned facing, slab height and other block properties through construction and world reloads. Both native Workers jobs and fallback construction use the same saved states.
- Starter pavilions gain sloped roofs and slab ridges in their host's stone palette, aligned with the courtyard entrance.
- Construction rejects malformed block states. Cleanup preserves later player edits that change a tracked block's orientation; old camp saves retain their existing restoration behavior.

# 4.47.20 beta

- Commissioned wall builders that remain trapped below the surface now recover to a checked nearby standing position. Recovery waits for sustained lack of movement and respects ownership, the active job, follow/hold commands, combat, fleeing and leashes. It never digs terrain or changes supplies.
- Starting camps replace the flat red tents with courtyard-facing Olympian pavilions: stone plinths, six columns, stepped roofs, lit entrances and host-specific materials.
- Pavilion sites validate the whole building, clear interiors, shallow foundations and three-wide approaches before queuing construction. Existing camps and paid wall jobs keep their saved state.

# 4.47.19 beta

- Every sealed loot box now guarantees a named, enchanted Olympian armory piece plus provisions. Twelve patron identities span weapons, armor, shields and tools, with stronger materials and enchantments at higher rarities.
- Rare and Epic boxes carry repairable relics such as Poseidon's Riptide trident, Zeus' returning thunderbolt and Hermes' fall-cushioning boots. Apollo's flaming Sunbow keeps Infinity instead of Mending. Armor has patron-colored trims; all abilities use vanilla enchantments.
- Added useful themed supply bundles, real healing/speed potions and snaring arrows with working Slowness. Extra supply categories cannot repeat within one box. Epic boxes also guarantee an enchanted golden apple.
- Opening a box names the armory prize and adds a short patron-colored particle/chime reveal. Contents stay hidden while sealed. Prices remain 16/48/96 emeralds and the existing rarity floors and wave-drop chances are unchanged.
- Fixed partial inventory insertion duplicating the original reward in overflow drops. Opening the last box frees its slot before delivery.

# 4.47.18 beta
- Respect native vessel boarding rules and keep vanilla fallback boats at two crew members.
- Check diagonal spawn-area corners, world borders and nearby entities before placing invasion vessels.
- Give newly supplied friendly and enemy siege engineers a finite repair kit for native repairs. Existing inventories are unchanged.

# 4.47.17 beta
- Fix native diplomacy updates calling Villager Recruits' faction manager instead of its separate diplomacy manager. Siege hostility, relationship repair and post-siege neutral resets now reach the correct API.
- Read the current diplomacy manager after server restarts and preserve safe handling while it is unavailable.
- Record the Recruits 1.15.2 / Workers 2.0.3 source compatibility review and add tests that distinguish their manager types.

# 4.47.16 beta
- Enemy builders announce the Olympian installation they are starting and the advantage it will provide, giving defenders a chance to disrupt construction.
- New camp installations grant their bonus only after the entire native build order finishes. An early centerpiece or abandoned shell no longer provides a finished building's advantage.
- Keep completed-building sabotage, existing saved installations and finite construction supplies intact.

# 4.47.15 beta
- Arrange camp upgrades around the actual main gate, keeping a five-block-wide approach clear of pavilions and new supply barrels.
- Add four interior corner sites when the main building wings are occupied, with space for the palisade and corner towers.
- Reject pavilion foundations without solid support. Existing buildings and saved camp layouts stay in place.

# 4.47.14 beta
- Give each Olympian host its own camp installation names and active-building effects, with clear messages describing the benefit lost when a building falls.
- Replace mismatched hero effects with patron signatures: Poseidon's sea surges, Ares' burning battle fury, Hephae…16345 tokens truncated…mounts are cleaned up at siege end, including later chunk reloads.
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
