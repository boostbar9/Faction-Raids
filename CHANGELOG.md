Warning: truncated output (original token count: 36570)
Total output lines: 1354

## 4.53.0-alpha.2 - Perimeter travel and material recovery

- Keep the exact blocking block and coordinates when stepped ground review refuses a site.
- Fix native builders reusing a gate approach pad after reaching it, including fallback when no standing site or complete path is found. Existing claim, collision, loaded-chunk and reservation checks remain in force.
- Fix depleted-material batches keeping the previous block target. Workers now selects from its freshly prepared queue and makes its normal finite supply requests.
- A real 25-chunk stepped/gated fixture completed all 3,831 blocks from finite chest supplies, with one 64-emerald commission and actual mid-section and between-section world restarts. This is focused evidence on the tested layout, not a guarantee for every terrain or modpack.
- Still experimental: deep excavation/CUT is deferred; broader terrain, multiplayer, retry/cancellation and downgrade coverage remains limited. Replacement builders and simultaneous crews are separate development work and are not included in this alpha.
- Back up your world and test on a copy. Match 4.53.0-alpha.2 on the server and every client, with Recruits 1.15.2+, Workers 2 2.0.3+, Small Ships and Siege Weapons. Stable 4.52.10 and its update feed remain unchanged.

## 4.53.0-alpha.1 - Stepped perimeter testing Alpha

- Experimental terrain-following perimeter plans add stepped walls, four cardinal gates on small level pads, and short dirt fills over dips. One paid plan prepares the ground and then builds using real chest supplies.
- Include saved gate-authority routing for native Workers 2 builders, while retaining existing walls and paid plans. Deep excavation and CUT remain deferred.
- Keep the current Command Center and sealed-loot changes from 4.52.10.
- Known limitations: builders can still stall at corners or gates, and complete native dirt-fill-to-wall construction, travel, retry/reload and cancellation acceptance is not finished. This Alpha is for manual testing, not a stable construction-completion release.
- Back up your world and test on a copy. Install exactly 4.53.0-alpha.1 on the server and every client, with the required Recruits, Workers 2, Small Ships and Siege Weapons dependencies. Do not assume worlds saved by this Alpha can be safely downgraded.

## 4.52.10 - Keep Olympian loot sealed

- Remove the possible-item browser and reward models from the Loot tab. Each chest now keeps its exact equipment pool mysterious until the purchased box is opened.
- Give the chest purchase controls the full card width and replace the preview showcase with a compact Fates-themed rarity and price reminder.
- Preserve the existing 16/48/96 emerald prices, rarity floors, random rolls, reveal animation, creative catalog and saved items. No loot probabilities or rewards changed.
- Add unit and native-client regression contracts that reject a restored possible-item browser at compact and roomy GUI scales.

## 4.52.9 — Builders reach their work before building

- Make commissioned builders approach the next planned block before native placement or mining can start. This fixes a native tick-order gap where a newly selected distant target could be worked on before its movement check.
- Let a safe, closer native partial-path endpoint serve as the next walking waypoint when a complete route is not found in one search. Builders keep using their normal pathfinder and working range.
- Keep collision-free vanilla plants and reviewed Sizeable Foliage 1.2.1 short grass usable as standing space without clearing extra terrain.
- Preserve accepted plans, materials, payment, native vertical reach, sleep and supply trips. Gate entrances and terrain grading are being developed separately.

## 4.52.8 — Saved plans, capture boundaries and richer hub pages

- Keep unpaid building-plan selections until you use or cancel them. Returning to a saved site no longer loses its preview after two minutes; current terrain, permissions, builders and costs are still checked before payment.
- Show the enemy core's capture boundary before you enter it, with progress, counted allies/enemies and clear range, height or sight-blocking feedback. Capture radius, timing and balance are unchanged.
- Browse real possible Olympian equipment from each chest's eligible loot pool, with native item previews, rarity tabs and complete tooltips. Sealed rewards stay hidden until opened, and chest odds now describe their existing rarity floors accurately.
- Review civilian names, professions, native portraits, remembered beds/workstations and tax status in a paged roster. Unloaded residents are clearly marked; the 16-emerald action recruits a resident, with housing still built separately.
- Update the server and every client together for network protocol 22. Existing worlds, accepted building jobs, claims, materials, Treasury balances and loot rolls remain compatible.

## 4.52.7 — Build through Sizeable Foliage short grass

- Let builders clear Sizeable Foliage 1.2.1’s Very Short Grass from approved wall and perimeter cells, instead of refusing the site as occupied. Other addon versions keep manual clearance until their behavior is checked.
- Keep the free terrain review, manual wall checks and protected native builder on the same single-cell plant rules. Paired plants and dangerous vegetation still need separate clearance.
- Preserve existing builds, claims, containers, fluids, supplied materials and paid jobs. Sizeable Foliage remains optional; no additional mod is required.

## 4.52.6 — More reliable builder reviews and work breaks

- Stop ground recovery from moving commissioned builders while they are sleeping, getting supplies or using an item. Native Workers work schedules stay in charge, and the stuck timer restarts after a break.
- Check the complete perimeter's placement neighbors during the free review, so nearby protected blocks are reported before a native section is created.
- Make manual and perimeter reviews use the same small-plant and headroom rules as native acceptance. Paired plants that need manual clearance now report their block and coordinates in the review; approved single-cell plants still work normally.
- Check manual-plan building permissions throughout reserved headroom. Existing structures, claims, inventory, paid jobs, supplied materials and construction fees are preserved.

## 4.52.5 — Give camp scouts safer ways to recover

- Try the existing safe soil-grading plan during the initial camp search, instead of discarding workable mounds with an older, stricter surface check.
- Let the rough-ground fallback preserve existing steep ground outside the camp when a flat camp, a walkable inner margin and a three-wide exit can still be made safely. Unrelated outer slopes no longer need to be flattened; protected blocks, claims, water limits and finite earthworks budgets remain enforced.
- If nearby natural and earthworks searches fail, scouts regroup for one minute and make one bounded wider search, roughly 608–992 blocks from the defending core. Search progress and regrouping time survive world reloads, and preparation stays paused until a camp is found or all searches finish.
- Keep attacks without a fortified camp as the final fallback. Scouts explain when they are regrouping, searching farther away or giving up; existing active camp-less raids are not restarted.
- Adds targeted terrain, persistence and real native lifecycle checks. Deep oceans, protected land or other unsafe terrain can still prevent a camp; this does not promise a site in every world.

## 4.52.4 — Clearer emerald costs and earned rewards

- Update the server and every client together. Network protocol 20 rejects older clients so the displayed prices and upgrade availability match the server. Saved worlds and accepted construction jobs remain compatible.
- Reduce new manual construction commissions to 8 emeralds for a wall section or corner, 12 for stairs or an archer barricade, 32 for a watchtower and 48 for a gatehouse. The whole-perimeter fee stays 64. Builders, supplied materials and normal work time remain separate requirements.
- Require a fresh review for older unpaid plan quotes. Accepted jobs keep their saved geometry, materials and payment, and existing server configuration is not rewritten.
- Stop selling Fortified Walls and Watchtower territory upgrades while their advertised effects are unavailable. Previous ownership stays saved; these entries are clearly unavailable and no longer counted as active benefits. Provisioning and Iron Levy keep their current prices and behavior.
- Award eligible cleared-wave loot before checkpoint retreat decisions, with a saved once-only receipt and retained random outcome. Practice raids with rewards disabled no longer award wave boxes or bonus barrel emeralds; barrel bonuses require a defending player.
- Existing raids without a trustworthy loot receipt do not backfill their current wave’s box, avoiding duplicate rewards after an update; later waves use the new receipt.
- Keep all other hiring, hero, civilian, loot, blessing, siege-crew and Treasury interest settings unchanged. This is a targeted consistency pass, not a claim that every economy style has been play-balanced.

## 4.52.3 — Clearer command screens and feedback

- Keep live unit models and 3D building plans visible at more GUI sizes. Small Building screens page through the plans instead of replacing their previews with material icons.
- Give every Command Center page clearer headings, useful unavailable states and consistent navigation. Restore readable close controls and keep the Army refresh timer visible on compact screens.
- Make the Codex guide and older Journal entries fully reachable, with keyboard focus that stays in the right place when tabs or live reports change.
- Fit Settings to smaller screens, keep unfinished edits when resizing or returning from Hero visuals, and prevent offscreen rows from receiving input.
- Bring construction inspection and held-plan guidance into the same dark style while retaining the native previews and controls.
- Distinguish camp searching, terrain waiting and assaults. Avoid false capture, payment or item-delivery confirmations, and keep unchanged stalled-search messages from repeating in chat.

## 4.52.2 — Actionable building checks

- Perimeter reviews distinguish unloaded boundary terrain, fluids, protected ground and occupied build space, reporting the exact rejected block and full coordinates when available. Existing surface and structure protections remain unchanged.
- Manual defense previews check the same nearby-block safety rule used during native builder admission, so a reactive or protected neighbor is identified before commissioning. This does not expand the native construction allowlist.
- Failed manual commissions retain their known native safety refusal and commissioning stage in the player message and log. Unknown exceptions stay in the log; no payment, rollback, claim, inventory or placement rules change.
- Adds focused regressions for negative-coordinate footing, clear grass with flowers, protected surfaces, unloaded terrain, preview/admission disagreement and unpaid handoff diagnostics. These diagnostics do not claim that every reported building failure has been resolved.

## 4.52.1 — Safe camp support on rough ground

- Fallback camp scouting can raise its proposed ground plane over exposed raw-rock bumps inside the camp core instead of rejecting a median plane that would require quarrying. The rock remains untouched; all existing claim, structure, exit, relief, height and finite earthworks limits still apply.
- Adds focused rough-ground regressions and a third opt-in native camp scenario combining an exposed stone rise, shallow water, trees and protected content. This is bounded representative coverage, not a guarantee that every world has a safe camp site.

## 4.52.0 — Building and camp reliability

- Consolidates automatic perimeter, all six existing plans and construction progress in one Building section. Adds restrained dark styling, source-based plan thumbnails, readable costs and responsive layouts while preserving Minecraft's font and the other command sections.
- Reduces new automatic perimeter commissions to one flat 64-emerald Treasury fee for the whole territory. Supplied blocks stay separate; existing paid jobs and claim-purchase costs are unchanged.
- Plans the full claimed boundary with the existing five-wide wall-template style and retained material palettes. Shared internal claim edges stay open. Free review and deliberate confirmation precede payment; unsafe, unloaded or oversized complete plans are rejected rather than silently truncated.
- New automatic perimeters and manual Wall Section/Wall Corner plans retain their outer faces, oak deck and parapets while leaving the enclosed body hollow. Cavities remain protected clearance and are never excavated. Already-paid jobs keep their saved solid geometry, supplies and payment; changed unpaid previews require fresh review. A flat 5×5 cobblestone/oak example falls from 6,600 to 3,900 blocks; terrain foundations change actual requirements.
- Uses one durable paid project with bounded native Workers sections. Whole-site reservations, exact geometry, stage progress and the original accessible shovel site persist across section changes. Unavailable ownership, terrain or recovery evidence pauses work without charging again.
- Keeps the native Workers shovel renderer, blueprint transforms, builder and storage behavior through a protected native BuildArea extension. Only unchanged, explicitly supported natural plants may be cleared; solid obstructions and protected inventories/builds remain protected.
- Adds authenticated whole-project cancellation, including the original owner's nearby marker after a faction change, and exact destroyed-builder cleanup. Placed blocks remain; paid commissions and consumed supplies are not refunded.
- Persists interrupted inventory-cleanup obligations and prevents replay of partial native callbacks. Normal shutdown drains only already-admitted protected callbacks when their sources remain safely available; interrupted or unavailable-source cases stay paused for review rather than guessing at item repairs.
- Fixes commissioned approach probes that reached a safe native path endpoint but reported another candidate as their target. Immediate and asynchronous paths now validate the reached endpoint before native movement, avoiding the reproduced previous-wall roof stall.
- Retains bounded, authenticated hand-only provenance after protected job completion or cancellation. Reloads bind only equal native main-hand/inventory mirrors with unchanged item counts, full NBT and capabilities; this grants no construction/storage authority. Missing old proof, inconsistent saves or unsafe active-use state pause for review instead of choosing amounts.
- Improves bounded camp-search diagnostics and exact native guard lookup. Missing roles no longer resolve to default registry entities; the narrowly matched Recruits 1.15.2 scout spawn cast failure resumes only its original initialization tail. Real-client checks passed ordinary-terrain and controlled shallow-water/forest establishment, including native ownership/work access and unchanged protected content. This does not guarantee arbitrary terrain or complete decorative camp construction.
- Local and final PR verification passed: 1,375 regression tests across 236 suites with zero failures, errors or skips; representative real hollow building, section handoff, cancellation/completion reloads, exact material accounting, keyboard/compact HUD checks, camp establishment and eight dedicated-side contracts. The handoff uses a synthetic maximum-96-target partition with 98 blocks retained; configured unload covers an accepted solid manifest with RecruitsChunkLoading=false. Complete large-build walkthroughs are optional stress tests; authenticated multiplayer, separately installed production-JAR gameplay and every modpack combination are not claimed. Native protocol 19 requires matching client/server builds. See the [release notes](docs/release-4.52.0.md) for exact remote evidence and coverage limits.

## 4.51.22 — Wall-builder arrival follow-up

- Same-elevation footing is now fully prioritized before any roof or terrain-surface fallback, even when the closest player-level cell is occupied.
- Fire, soul fire, wither roses, powder snow, portals and the complete hired-unit hazard set are rejected in both body cells as well as beneath the builder.
- This release supersedes 4.51.21 and addresses its late review findings without changing wall plans, supplies, inventories or Workers 2 construction behavior.

## 4.51.21 — Safe wall-builder arrival

- A distant commissioned wall builder now chooses loaded, dry, collision-free footing near the player instead of teleporting blindly to the heightmap at the player's coordinates.
- The search prefers the player's elevation, avoiding roofs above indoor Siege Cores, and refuses water, hazards, occupied space and unloaded columns. If no safe arrival exists, the builder stays put rather than being embedded or dropped.
- Safe relocation clears old movement and fall state. Wall blueprints, Workers supplies, inventories, ownership and native construction remain unchanged.

## 4.51.20 — Stable wall projections and camp search

- Enabled Workers wall projections use their full fixed footprint for camera visibility, so turning away from the shovel marker no longer hides an otherwise visible projection. Native placement, distance limits, ownership and the existing projection toggle stay unchanged.
- Difficult-site scouting accepts untouched natural rock as foundation support, including shallow pond bottoms and exits. It still cannot excavate rock or use crafted foundations.
- All 25 expanded scout survey positions now stay within the guaranteed loaded three-by-three chunk neighborhood.

## 4.51.19 — Clear camp entrance canopies

- Difficult-site entrance preparation now notices confirmed trees beside the route and clears their overhanging natural canopy instead of stopping at the ground-only heightmap result.
- The bounded route survey still requires loaded terrain, preserves protected columns, and refuses persistent, waterlogged or unsupported foliage.

## 4.51.18 — Packet validation and snapshots

- Reject negative dashboard collection counts and counts that cannot fit in the received payload before allocating entries.
- Treasury ledger packets now copy their input and output arrays so queued snapshots and their equality/hash values remain stable.
- Army map faction names truncate without splitting supplementary Unicode characters.
- Packet layout and protocol version remain unchanged; valid existing payloads still decode.

## 4.51.17 — Prepared camp foundations and exits

- Difficult-site fallback clears confirmed natural trees and canopy in the camp and entrance, with saved originals for cleanup.
- Grading gets a wider six-block shoulder, up to twelve blocks of dirt cut/fill per column and a finite 4,096-block mutation budget. Ordinary scouting keeps its smaller limits.
- Camps can fill shallow water onto solid sediment and prepare a three-wide dirt causeway up to eight blocks beyond the shoulder, ending on dry ground. Deep water, lava, blocked exits and protected builds remain excluded.
- Camp height selection and the search prefilter now agree with the stronger fallback. Preparation remains fully validated before any edits; failed placements roll back.

## 4.51.16 — Wall approaches and camp slopes

- Commissioned perimeter builders approach the nearest remaining wall section instead of an arbitrary blueprint corner. Workers still handles supplies, preparation and construction; a safe surface position is required before work starts.
- Builders move aside when standing in a queued wall column before handing construction back to Workers.
- Camp grading now checks the amount of dirt actually cut or filled. Natural outer slopes no longer count as oversized earthworks, and the proposed height respects the untouched boundary.
- Existing claims, protected blocks, water limits, restoration and work budgets remain enforced.

## 4.51.15 — Starting civilian arrivals

- If the core cannot safely place both starting civilians, it retries every five seconds while loaded. The placer must still be nearby and able to use the core; no terrain or chunks are forced open.
- Automatic retries are quiet. Opening the core still explains blocked space or a full population, and resumes retries for older pending grants.
- Completed lifetime grants clear their pending owner and stop retrying. Reloading, moving the core, or losing residents never grants replacement starters.

## 4.51.14
- Commissioned wall builders no longer keep a reachable path whose endpoint is underground or unsafe; they try validated surface standing space instead.
- Being horizontally close to a buried job no longer suppresses surface access recovery, and cached work positions are rechecked before reuse. Native supplies, construction and ownership remain unchanged.

## 4.51.13
- Coastal camp earthworks now try alternate dry entrances before rejecting a site whose preferred gate faces water.
- Successful site selection saves the dry entrance for the gate, approach road and perimeter; failed earthworks leave no saved entrance. Existing camps retain their established gate orientation.

## 4.51.12
- Wall commissions can raise shallow perimeter depressions to accessible interior work height, using dirt foundations supplied and constructed through Workers 2.
- Foundation planning stays within eight blocks of natural ground and preserves buildings, fluids and deep gaps. The preview and material list include the dirt needed.

## 4.51.11
- Scout missions now retry temporary placement failures instead of being silently consumed when their scheduled chunks are unloaded or no safe route endpoint is available.
- Retries preserve the promised attacking faction, intel and bounty state, and remain bounded by the mission's original expiration time.

## 4.51.10
- Scout parties now choose loaded, dry, walkable spawn and lookout ground, rejecting water, trees, hazards, isolated pillars and unsafe world boundaries.
- Every scout receives a separately measured surface position, preventing party members from spawning inside slopes or above holes when the terrain height changes across the group.
- Lookouts stay on the party's incoming side of the territory instead of selecting an unrelated high roof or mountain across the defended base.

## 4.51.9
- Scouts keep their lookout, retreat route and observation progress after chunk reload or server restart.
- Unloaded scouts are recalled when they return after their mission expires, scouting is disabled, their core is removed or the raid begins.
- Scouts from older saves without route data retire safely instead of returning with pillager combat AI. Intel and bounty records remain intact.

## 4.51.8
- Prevent a narration crash when opening an unnamed Villager Workers build area: its screen receives a Construction Area title. Existing named areas keep their title.

## 4.51.7
- Expired scouting missions retain their promised attacker identity and paid bounty until the next raid. This prevents repeated scout parties in one cooldown and keeps recovered intel accurate after saving and reloading.
- Expired missions no longer spawn a party after a restart. Disabling scouting recalls loaded scouts; starting a raid also recalls any loaded scouts before consuming their mission.

## 4.51.6
- Civilian taxes now appear as identifiable Treasury activity, with a saved last payout and lifetime total. Online faction members receive one aggregated chat notice when taxes are deposited.
- The House of Civilians displays a villager preview and explains the 16-emerald Treasury housing fee, daily taxes, and assigned resident identity.

## 4.51.5
- Hero offers now unlock by eligible Siege Core victories: uncommon after one, rare after two, epic after four, legendary after six. Existing victory records seed the saved faction milestone; stale high-tier offers are safely rerolled before purchase.
- New common and uncommon heroes receive iron armor and melee weapons. Rare and epic heroes retain diamond; legendary heroes retain Netherite.
- Equipment issued by Siege Overhaul no longer drops when a tracked hero, core-hired soldier, or uniformed raider dies. Player-supplied replacement items still drop through Villager Recruits.

## 4.51.4
- Fallback camp earthworks can fill water up to six blocks deep over natural soil, allowing a camp floor to extend onto shallow water near a dry gate approach. Deep water, structures, claims and excessive earthwork remain excluded; water is recorded for cleanup.

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

- Every sealed loot box now guarantees a named, enchanted Olympian armory piece plus provisions. Twelve patron identities …11570 tokens truncated…b the same way any native Workers 2 job does, instead of forcing our raider night-shift goal onto them. The raider goal only fires when the worker has a raid team tag and an active enemy camp, so installing it on your builder actually disabled their AI. v4.23.1 uses a player-safe attachment that sets ownership and work state and leaves every native goal in place, so the builder walks to the buildarea, pulls blocks from your storagearea, and places them like any Workers 2 build.
- Removed the raider gear provisioning path from the player builder attachment. Your builder keeps whatever tools and armor you already gave it.
- The confirmation message now tells you exactly how many blocks of the chosen material to load into your storage area, so you can stock the right amount before the builder starts.

# 4.23.0 beta

- Fortify Perimeter now reuses the storage area you already built. Instead of dropping a new supply barrel next to the builder, the job discovers a Workers 2 storagearea inside your claim that belongs to you and hands it to the builder as the source of blocks. If no owned storagearea sits within 64 blocks of the builder, the commission is refused with instructions on where to place one.
- The buildarea for the wall is now created under your player UUID as well, so ownership matches your existing Workers 2 setup and the builder can access the storagearea without extra configuration.
- Removed the auto-placed barrel and its cleanup path. Nothing new is placed in the world when you commission Fortify Perimeter beyond the wall blocks themselves.
- Siege crew kits give a clear reason when a deployment is rejected instead of the generic "blocked" message. You now see the exact coordinate and block name of the cell that fails, which cell has fluid or missing headroom, and which of the anchor points has non-solid ground. Sturdy ground is only checked at the center and four corners now, so replaceable ground cover like grass and snow no longer forces a false rejection.

# 4.22.1 beta

- Complete the Fortify Perimeter feature shipped in 4.22.0. The v4.22.0 tag was published with the new class file but without the Territory tab buttons, the menu action ids, or the version bump, so the feature was dead code. This release wires the three material buttons at the bottom of the Territory tab (Stone Bricks, Cobblestone, Oak Planks), routes menu ids 70 to 72 into TerritoryFortification.commission, and bumps the mod version.
- No new behavior beyond what 4.22.0 was intended to ship: 1200 emerald commission, 3-block walls along claim edges, 5-block corner pillars, materials pulled from a supply barrel the job drops next to your Villager Recruits builder.

# 4.22.0 beta

- New Territory job: Fortify Perimeter. Commissions a Villager Recruits Builder standing near your Siege Core to wall off the outer edge of your Recruits claim in stone bricks, cobblestone, or oak planks. Three material buttons live at the bottom of the Territory tab.
- Walls are 3 blocks tall along every chunk edge that borders unclaimed land, with 5-block corner pillars at exterior chunk corners.
- Costs 1,200 emeralds to commission (bank first, then inventory). The material itself comes from the storage barrel the job drops next to your builder. Fill the barrel with your chosen block and the builder walks the perimeter placing them; leave it empty or run it dry and the builder waits until you refill.
- Never overwrites existing solid blocks, so a wall that runs into your castle just skips those cells and continues on the other side.
- Requires both Villager Recruits and Workers 2. Uses the same buildarea, blueprint, and storagearea system the mod already uses for siege camps, so behavior is consistent with what a native Workers 2 job would do.

# 4.21.3 beta

- Raiders now reliably climb the temporary ladders they build against walls. Previously the goal frequently failed to attach because ground pathfinding cannot end a path on the ladder's air block, so raiders would stall a few blocks from the ladder. The goal now paths to the solid stand-on square adjacent to the ladder base and only ascends after touching a ladder anywhere in the column, not just the exact block coordinates.
- Widen the touching gate on the way up so slightly off-grid raiders keep climbing instead of dropping back to the ground.
- No changes to placement rules, ladder counts, save data or dependencies.

# 4.21.2 beta

- Fix the in-game configuration editor saving list settings as one string, which could reject the change or corrupt the setting type. String lists now use a readable comma-separated editor; blank input produces an empty list and the prior bracketed display remains accepted during upgrades.
- Increase list-field input capacity while preserving boolean, enum, string and numeric editing behavior. No gameplay balance, save data or dependency changes.
- Automated regression coverage; no interactive Minecraft playtesting.

# 4.21.1 beta

- Make the Siege Core HUD size itself from the actual Minecraft-scaled viewport. Wide, tall, short and narrow aspect ratios now select detailed or compact cards from their usable geometry, while unusually small modded/resizable windows uniformly scale the complete HUD and its mouse hitboxes instead of collapsing sections together.
- Let the command panel grow farther on larger resolutions, use adaptive tab/card/control gaps, ellipsize genuinely clipped labels, and replace the cramped compact Bank columns with a readable summary plus complete hover details.
- Show whether Army offers are affordable at a glance and explain exact emerald shortfalls in hover text. Creative players can use the siege deployment buttons without carrying emeralds.
- Require siege kits to be used on the top face of the center ground block, retain the item after every failed attempt, and add restrained purchase/deployment sound and particle feedback.
- Save format, prices, faction-bank payment order, dependencies and existing worlds remain unchanged.

# 4.21.0 beta

- Reworked the Siege Core command center into fixed header, tab, status, content, Army-action, and footer bands so controls no longer overlap at any supported Minecraft GUI scale.
- Added a true compact Army layout for large GUI scales: portrait, name, role, price, and Hire action remain readable while detailed kit and ability text moves to the existing hover tooltip.
- Catapult and ballista purchases now issue one-use deployment kits. Right-click the top of a clear flat 3x3 area to place the engine and its friendly Siege Engineer; failed placements keep the kit for another attempt.
- Added an always-visible Leave Feedback action that opens the Siege Overhaul CurseForge comments page through Minecraft's external-link confirmation screen.
- Added responsive compact layouts for Loot, Bank, Territory, tabs, status ribbon, and footer controls. Save data and existing worlds remain compatible; matching client/server versions are required.

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
