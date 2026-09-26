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
- Replace mismatched hero effects with patron signatures: Poseidon's sea surges, Ares' burning battle fury, Hephaestus' forge sparks, Athena's aegis magic and Artemis' moonlight. Newly summoned wolves are named Moon Hounds.
- Offensive hero spells now wait for a living, visible enemy. Area spells no longer consume their cooldown when no eligible enemy is in reach.
- Keep existing damage, effect durations, cooldowns, camp benefits and saved identities. Reduce particle counts for the tidal nova, battle fury and hound effects.

# 4.47.13 beta
- Fix the solid core blocking its own capture sight checks. Walls and roofs still prevent capture through cover.
- Keep troops assigned to another siege out of a core's capture count. Native soldiers of the actual occupying faction still contest recovery.
- Abandoned capture progress now drains at one second per second. Occupied ties still pause, and larger armies do not shorten the hold timer.
- Reset recapture progress when the observed occupying faction changes, and reject enemy-core victory if its camp claim has been removed, replaced or transferred.

# 4.47.12 beta
- Fix commissioned wall blueprints shifting sideways when an outer edge is already built or protected. Existing solid blocks are no longer queued for demolition, and unloaded wall columns are left alone.
- Grade camp-building entrances into walkable, three-wide steps; reject blocked interiors and sites without a safe connection to the courtyard.
- Keep later camp buildings and supply barrels out of saved entrances and core courtyards. New perimeter gates record their actual ground height for troop waypoints.
- Let attackers choose reachable floor beside a Siege Core during their final approach, using their actual collision size. Existing routes and protected-block breaching rules remain in place.

# 4.47.11 beta

- Fix Provisioning's interest bonus being skipped by automatic Treasury settlement and wave-clear payouts. All settlement paths now apply the same effective rate, whether the Command Center is open or closed.
- Show the effective interest rate in the Treasury and label its interval as one in-game day instead of 24 hours. The existing interest cap, fractional carry, and saved payout timing still apply.

# 4.47.10 beta

- Remove stale enemy camp territory claims left behind by older or interrupted sieges. These invisible Recruits reservations could prevent players from claiming otherwise-empty land; active camps, occupied Siege Core territory and camps captured by players remain intact.
- Run camp-claim maintenance once per interval and limit full native-claim discovery to every five minutes, avoiding repeated registry scans on the server tick.

# 4.47.9 beta

- Detach a failed Workers 2 wall job before resetting the builder's movement state. If detachment is rejected, the live job and working state now remain together instead of stalling.

# 4.47.8 beta

- Preserve a failed Workers 2 wall build area when the builder still references it, instead of discarding the entity and leaving a broken job pointer.
- Report reflective detachment failures accurately while still allowing an incompatible movement-state reset to complete the safe half of cleanup.

# 4.47.7 beta

- Roll back a partially assigned Workers 2 build area when a wall commission fails, so the builder does not remain stuck in working mode on a discarded job.
- Restore the builder's previous native job when the reflective Workers 2 handoff is incompatible, and never clear an unrelated job during cleanup.
- Run movement-state and area cleanup independently so an incompatible optional API cannot block the rest of the rollback.
- Treat a paid, started wall commission as successful even if later confirmation text or logging fails, preserving both the work and the player's Treasury purchase.

# 4.47.6 beta

- Check a wall area's current owner before reconnecting its saved job, including when the builder loads later. Transferred areas cannot reserve a former owner's builder.
- If ownership is temporarily unavailable, keep the job pending and retry when it can be verified.
- Remove confirmed ownership transfers from the former owner's pending-job scans.

# 4.47.5 beta

- Keep a wall builder's saved job reserved while chunks load, so a second wall area cannot replace it before work starts.
- New perimeter commissions also wait for saved wall jobs to finish instead of replacing them during recovery.
- Respect transferred workers and build areas. Recovery no longer assigns them back to a former owner, and waits when ownership cannot be read.
- Keep enemy camp workers out of player wall recovery and remove unloaded or finished jobs from pending scans. Unfinished areas register again when their chunks load.
- Includes the 4.47.4 wall-job reload and legacy migration fixes.

# 4.47.4 beta

- Recover paid Fortify Perimeter jobs when their Workers 2 builder loads after the wall build area. The correct owner's builder reconnects automatically or after finishing another job; other workers and active jobs are left alone.
- Migrate older wall areas using positive player-owned Workers 2 evidence instead of raid timing. Active sieges no longer cause legacy player jobs to be discarded, while raider-team areas remain enemy assets and follow normal cleanup.
- Limit reload checks to real Workers 2 builders and preserve existing saves, wall prices, Treasury rules, required and optional dependencies, claims, and player-supplied inventories.

# 4.47.3 beta

- Restrict the expanded masonry-and-plank breaching fallback to real Siege Core assaults. Legacy named defense points and synthetic claim targets no longer gain the stronger whitelist merely because their perimeter phase has been breached.
- Preserve ordinary door, fence and siege-breach behavior, the 24-block claimed-core limit, tracked restoration, existing saves, prices and dependency requirements.

# 4.47.2 beta

- Modernize all five Command Center tabs around one quieter navy-and-aged-gold visual system: flat tab rails, slim purpose-colored card accents, clearer hover states, compact text badges and consistent two-line page headers.
- Keep Army offers centered on real equipped Recruits and Workers previews while adding readable availability and hero-rarity states without restoring the removed mystery glyphs.
- Rebuild Loot as an Olympian reliquary view with sealed/opening/revealed states, blessing-duration badges and an explicit no-preview protocol. Correct its copy to state that Command Center purchases debit the faction Treasury, not personal emeralds.
- Rebuild Treasury around three glanceable metrics for balance, next-wave reward and daily interest, plus a cleaner member roster and palette-matched transaction graph.
- Use Territory's former empty area for kingdom-readiness progress, permanent-decree status and Workers 2 perimeter requirements. Add clear fortification tooltips without changing prices or job behavior.
- Present Intel entries as scrollable dossier cards with modern sub-navigation while preserving the complete unit, Olympian-host and field-playbook content.
- Reduce HUD frame cost by replacing the full-window tiled backdrop and oversized ornamental corners with a gradient surface and lightweight architectural lines. Preserve responsive scaling, live models, hidden loot, save/network formats and server-authoritative purchases.

# 4.47.1 beta

- Restore real, continuously visible Recruits and Workers entity previews to all four Army offer cards. Models wear their preview loadouts and follow the cursor again; the flat procedural avatars now appear nowhere in the normal Command Center path.
- Correct preview entity selection for the complete twenty-hero Olympian roster. Later heroes now map through their actual Recruit class instead of `role - 10`, and previews reuse the production equipment paths, including supported Epic Knights and Musket equipment when installed.
- Remove the unclear equipment glyph rows, large tab-corner emblems and decorative icons from tabs, status, upgrades, blessings, roster and action buttons. Keep real Minecraft emeralds and revealed reward items where an item sprite has literal meaning.
- Reduce Command Center frame work while preserving four live models: cache fitted and wrapped text, remove continuous motes and additive glow passes, remove three obsolete hidden map widgets, and keep control-state updates at game-tick frequency.
- Tighten Army card text flow and use readable headings across Loot, Treasury, Territory and Intel at detailed and compact GUI sizes.
- Preserve Minecraft 1.20.1 Forge / Java 17, saves, gameplay balance, loot-box prices, required and optional dependency metadata and server-side purchase validation.

# 4.47.0 beta

- Let stranded invasion crews disembark onto the prevalidated landing beach even when their vessel stops outside its old local landing search, while retaining collision, fluid, world-border and loaded-chunk safety checks.
- Preserve player-commissioned Workers 2 fortification jobs across chunk and server reloads. Wall build areas now use a durable player-builder link instead of the enemy-camp cleanup tag, including migration for still-active legacy jobs.
- Improve the final approach to claimed Siege Cores: raiders can operate ordinary doors, and breachers may slowly open a restored passage through a narrow whitelist of common masonry or planks within 24 blocks of the core. Containers, valuables, obsidian, machinery, foreign claims and the restoration cap remain protected.
- Place new enemy pavilions wholly inside the palisade with a paved, validated three-wide approach. Preserve openings for older saved camps whose pavilion doorway intersects the later wall.
- Make the Sanctuary of Demeter, Forge of Hephaestus and Strategion of Athena visibly active with bounded thematic particles while retaining their guard-healing, guard-damage and wave-coordination effects.
- Reduce Command Center frame cost by rendering at most one live third-party entity preview while hovered and using the custom static portraits for the other cards. Button labels and state now update once per game tick instead of allocating again every rendered frame.
- Preserve Minecraft 1.20.1 Forge / Java 17, saves, required and optional dependency metadata, loot-box prices, player structures, claims and existing restoration safeguards.

# 4.46.0 beta

- Turn every fifth wave into a clearly telegraphed patron-specific signature assault. Poseidon commits a Tidal Onslaught, Ares a Bronze Spearhead, Hephaestus a Forge Engine Advance, Athena an Aegis Phalanx, and Artemis a Moonlit Hunt, each with its own roster, title, sound cue and counterplay warning.
- Give newly established Olympian camps distinct six-unit garrisons and defensive leashes: Tidewatch Sentries, Bronze Gateguards, Forgeward Sentries, Aegis Sentinels and Silver Hunt Wardens. New core keeps also use patron-specific pillar silhouettes; existing cores and guards keep their saved positions while guards adopt the matching title and leash.
- Let coastal war camps actually deploy their configured naval contingent instead of redirecting would-be ship crews back through the land gate. Poseidon's checkpoint assault commits at least 75% of eligible non-cavalry troops to ships when a safe staging route exists.
- Keep the Treasury interest countdown at `Due now` after its deadline until settlement advances the saved payout anchor, rather than wrapping to another future-day countdown.
- Preserve the exact configured order of repeated wave roles, keep Artemis' Hunter's Mark command to its promised five seconds, and normalize both 4.44 boolean and 4.45 integer hero-identity markers into a save-compatible boolean plus a separate schema field.
- Preserve wave/entity caps, ordinary reward balance, existing raids and saves, faction IDs, restoration limits, dependency requirements and player structures. Signature assaults replace normal wave slots instead of adding unbounded enemies.

# 4.45.0 beta

- Give every Olympian war host a real battlefield doctrine: Poseidon's broad tidal line, Ares' melee-heavy spearhead, Hephaestus' supplied siege column, Athena's captain-led shield square, and Artemis' ranged skirmish screen. Terrain and specialist safety can still override formation orders when needed.
- Give each Strategos a distinct, five-second signature command at half health: Tidal Advance, War Cry, Forge Ward, Aegis Order, or Hunter's Mark. Effects are bounded, visible and non-destructive; they do not place water, spread fire, or alter terrain.
- Bind enemy champions to the patron leading their invasion. The twenty existing hero roles are divided into four champions per host while preserving role IDs, rarity, prices, stats and mechanics. Untouched 4.44 names migrate; player-renamed heroes remain unchanged.
- Give each host a recognizable command keep and progressive camp skyline using patron-specific palettes: prismarine waves, blackstone battlements, copper forge chimneys, a quartz acropolis, or a moss-and-birch hunting shrine.
- Update Intel lore with each host's doctrine, camp silhouette, Strategos ability and counterplay. Existing faction IDs, active raids, saves, claims, diplomacy records, restoration tracking and dependency requirements remain compatible.

# 4.44.0 beta

- Recast the five enemy factions as Olympian war hosts: Poseidon's Tide, the Warhost of Ares, the Forgeguard of Hephaestus, the Aegis Order of Athena, and the Silver Hunt of Artemis. Their banners, map colors, uniforms, lore, commander titles, and visible territory names now share that identity.
- Retheme all twenty recruitable and enemy heroes with Greek names, Olympian patrons, divine equipment names, and matching ability descriptions while preserving every role, rarity, price, stat, and combat mechanic.
- Migrate untouched legacy hero names automatically. Heroes renamed by a player keep their custom names, and existing faction IDs, claims, diplomacy records, trophy data, active raids, and configuration allowlists remain compatible.
- Give new enemy core sanctuaries and progressive camp pavilions a marble, quartz-column, and colored-terracotta visual language without changing their footprints, finite construction budgets, or restoration behavior.
- Keep Minecraft 1.20.1 Forge, Java 17, the required dependency contract, save format, and loot-box prices unchanged.

# 4.43.3 beta

- Keep occupied Siege Cores recoverable when another Recruits faction takes control of the same native claim instead of leaving the occupation permanently stuck.
- Count the current claim holder's nearby players and Recruits soldiers as opposition during recovery, while unrelated factions remain neutral to the capture meter.

# 4.43.2 beta

- Normalize saved `team:<id>` keys before calling Villager Recruits' diplomacy manager, so active-siege hostility is applied to the real faction instead of an unknown prefixed identity.
- Ignore solo-player fallback keys at the native diplomacy boundary while preserving the existing safe no-op behavior when the Recruits API is unavailable.

# 4.43.1 beta

- Keep enemy command-core courtyards protected even when War Gate planning fails, and reject overlapping Workers 2 construction before gate or road setup can mutate the camp.
- Keep every alternative five-wide command keep inside the camp palisade and cache queued columns plus claim checks during site searches, avoiding repeated work when terrain blocks placement.
- Reassert Villager Recruits hostility in both directions during an active siege. A diplomacy change to neutral or ally can no longer disable native enemy targeting mid-battle; the relationship still resets when the siege ends.
- Preserve existing cores, capture progress, faction claims, loot-box prices, dependency requirements, and save compatibility.

# 4.34.0

- Overhauls the Siege Overhaul creative tab. The generic Recruits spawn eggs are gone, replaced with dedicated spawn eggs for every hireable unit in the mod: the four recruit tiers (Recruit, Shieldman, Archer, Crossbowman) and every one of the twenty heroes across all five rarities.
- Each egg spawns the unit in the exact same gear it would arrive in through a Siege Core hire. Recruits get their trimmed iron and chainmail armor, matching weapon, and a random first name. Heroes get their tier-appropriate gear from HeroTraits, an XP level 10 head start, a 60-hp health floor, +4 attack, bread and arrows where appropriate, and the hero flag so downstream systems recognize them.
- Each egg has its own texture, colored to match the unit's tier. Recruits are palette-coded by role (gray, blue, green, brown). Heroes are colored by rarity: white Common, green Uncommon, blue Rare, purple Epic, gold Legendary. A weapon glyph on the egg tells you at a glance what the unit fights with, or a sparkle for mage heroes.
- Egg names show the full hero name in the tooltip in the vanilla rarity color, so a stack of Solmyra the Radiant eggs reads in gold like a legendary should.
- No hidden dependency: if Villager Recruits isn't installed, the egg politely tells you rather than spawning nothing.

# 4.33.1

- Redraws all four loot box textures in a proper vanilla item style. The originals were too polished and read like mobile-game gacha art next to real Minecraft items. New textures are 32x32 with a flat head-on view, a hard black outline, and a limited palette per tier so they sit next to a vanilla chest item without looking out of place.
- Common gets iron bands on oak. Uncommon adds bronze bands and a green rarity halo. Rare has silver bands and a sapphire gem on the lid with a blue halo. Epic has gold bands on darker wood and an amethyst gem with a purple halo.

# 4.33.0

- Loot boxes are now real items you get for surviving a wave, instead of automatic emerald deposits alone. Every online defender receives one loot box at the end of every cleared wave, and higher waves shift the odds toward better boxes.
- Four rarity tiers with their own textures: Common (iron-banded oak), Uncommon (bronze), Rare (silver with a sapphire), and Epic (gold with amethyst). The item name is color-coded by tier, and the tooltip tells you what you're holding.
- Right-click a box to open it. A chest-open sound plays, a firework-star pop appears above your head, and the contents drop into your inventory (overflow drops at your feet so a full inventory never eats it).
- Contents scale to the box. Common boxes carry iron, arrows, and basic building materials. Uncommon adds gold and a few emeralds. Rare pulls diamonds, ender pearls, and experience bottles. Epic can drop netherite, enchanted golden apples, and totems of undying.
- Rough distribution: wave 1 clears are almost all Common with a small shot at anything better, by wave 10 you're seeing Rare boxes regularly and Epics start showing up, and past wave 20 the mix is dominated by Rare and Epic. A lucky wave-1 clear can still pop an Epic; it just isn't the usual outcome.
- Loot boxes stack up to 16 in a single slot so they don't clog inventory when you're running a long Endless push. They're also listed in the creative Tools tab.

# 4.32.0

- Adds a proper Siege Overhaul tab to the vanilla advancement screen. Open your advancements (default L key) and there is now a full tree tracking your first kill, your first held wave, your first won raid, your first commander down, your first faction met, and every milestone that comes after that.
- Tracks flawless defense two different ways. Win a raid without a single wall block being breached and you get Perimeter Intact. Win a raid without a single defender dying and you get Untouchable, which is the hidden challenge tier and pays real experience.
- Tracks the five raiding factions as a set. Meet any faction for the first time and you get Faction Scholar. Meet all five and you get Know Thy Enemy, which is a full goal on the tree.
- Tracks commanders the same way. Kill any commander and you get Commander Down. Kill a commander from all five factions and you get Regicide, which is hidden until you've done at least one, and drops a fat experience payout when the fifth one falls.
- Tracks Endless siege progression. Voting to continue past wave 5 unlocks Endless. Reaching wave 10 in Endless gives you Double Digits. Reaching wave 25 in the same Endless push is the hidden long-war challenge.
- Tracks career wins as Warlord, which unlocks after your tenth won raid. Win counter persists across relogs.
- No new HUD and no new menu. Everything routes through the vanilla advancement screen so it reads exactly like a base-game achievement tree, just with siege-specific milestones inside it.

# 4.31.0

- Wave clears play a quiet experience-ping so the beat lands with sound, not just chat. Defeats now close on a low wither-death shout that fades like a war horn dying. Victories still get the raid horn they've always had, so the whole raid arc reads audibly whether or not chat is on screen.
- Pending-spoils lookup on login no longer swallows failures silently. If a saved-data problem ever stops a returning player from being told about their unclaimed rewards, it now shows up in the server log so admins can catch it instead of the reward just seeming to vanish.

# 4.30.0

- Raider factions actually declare on your faction now. When a raid starts the attacking clan is marked ENEMY inside Villager Recruits' diplomacy manager, and marked back to NEUTRAL when the raid ends. That makes Recruits' own target selectors, HUD tints, and the vanilla diplomatic-status toast light up correctly instead of treating them as generic neutrals.
- Treasury deposit and withdrawal notices no longer stomp the raid HUD. The action-bar copy of every purchase or bounty landed on the same row as the current phase and stronghold status, which flickered during a fight; it is chat-only now, so the raid HUD stays put.
- Warlord's Codex attacker marker uses a real iron sword icon instead of the crossed-swords Unicode character. Font packs without that glyph were rendering a tofu box next to the attacking faction; the new marker reads on every default and modded font.

# 4.29.1 beta

- Keep the enemy command core in its raised keep, preferring a clear camp center and trying interior courtyard sites when the center is blocked.
- Require a dry, supported footprint and open headroom; avoid roads, queued buildings, foreign claims and unloaded terrain. Reserve the keep against later obstructions.
- Preserve existing active cores and capture progress.

# 4.29.0

- Marching enemy armies show up on the Recruits map. Press M and you can watch a column close on your territory the same way you watch your own recruits move. Soldiers and their siege engines get their own icons and slide across the map as the army advances.
- Siege engines that fall behind now catch up. Catapults and ballistas are tracked by the loaded corridor alongside the infantry, so an engine no longer freezes in an unloaded chunk while the soldiers walk on. An engine that gets wedged sweeps its waypoint wider, shortens its steps, and after a few failed attempts is set down a few blocks further along its march line onto solid ground.
- Artillery no longer parks inside the camp wall. Engine slots stay clear of the wall line and the ground the camp still has to build on, which was the main reason builders were found grinding against a catapult.
- Camp builders can no longer be stuck on one project forever. A cell a builder cannot fill is left out of the plan, and a job that has made no progress for the configured time is abandoned so the camp moves on. In practice that is what kept the perimeter wall and its corner towers from ever being started. The gap before the next wall section or tower starts is now a minute by default and configurable.
- Hero prices are down to a straightforward ladder. A Common hero costs 50 emeralds and each rarity above it adds another 50, up to 250 for a Legendary, instead of being scaled off recruit prices. Both the starting price and the step per rarity are configurable.
- Raiders can actually climb their siege ladders now. A climber holds itself lined up with the rung column, a raider that loses its grip grabs the ladder again rather than dropping to the ground, climbers get the extra lift they need to step onto the walkway, and a ladder that really cannot be climbed is abandoned after a few seconds so the raider goes back to the assault instead of grinding against the wall.
- War chests are completely rebuilt. Each of the three chests pulls from its own long, hand-picked table with dozens of possible outcomes, so opening the same chest twice rarely gives the same reward. Common rolls hand out supplies and materials, uncommon rolls hand out iron gear and potions, rare rolls hand out trimmed diamond pieces and modest weapons, and epic rolls hand out named trophies with sensible vanilla enchantments. No auto-Mending, no Fire Aspect stacking, no chest-only custom enchants; the gear is meant to feel earned, not to trivialize the rest of the game.
- Every chest purchase now draws from the faction Treasury only. The war-key system and the three custom enchantments introduced in an earlier beta have been removed; if you have unused war keys from a beta build they will disappear on load without affecting anything else.
- Command Center Loot page updated to match. The old key counter is replaced with an odds hint, so the panel actually reflects how the chest rolls.

# 4.28.19 beta

- All Command Center purchases use faction Treasury emeralds only. Deposit emeralds before buying units, siege kits, walls, upgrades, loot boxes or blessings; personal inventory is never used as a fallback.
- Rename the Bank tab to Treasury and align purchase availability and help text with the shared balance.
- Preserve saved balances, deposits, withdrawals and creative-mode purchases.

# 4.28.19a beta

- Enemy armies now carry their own loaded ground with them. A column that leaves a distant camp keeps ticking the whole way to your territory instead of freezing in unloaded chunks and never arriving, and an army caught mid-march by a server restart is pulled back into the world instead of being stranded. The corridor is capped and its chunk holds expire on their own, so nothing stays force-loaded after a siege.
- The Siege Core contest is much tighter. The ring is now a low cylinder around the core rather than a wide bubble, the default radius drops from 10 blocks to 6, and combatants must have a clear view of the core, so troops standing on the roof above it or outside the wall no longer count toward capture or recapture. Both the radius and the height band are configurable.
- Camp guards no longer arrive as full veterans. The garrison starts close to its normal Recruits stat line on the opening wave and earns its extra health, damage and knockback resistance as the siege reaches its later waves. Existing guards keep the stats they already had, and a new setting scales or disables the bonus entirely.
- Hero hiring is priced by rarity again. A single flat minimum meant every rarity below Legendary cost the same 256 emeralds on ordinary recruit prices; each rarity now has its own minimum, so Commons stay affordable and Legendaries cost what they should.

# 4.28.18 beta

- Enemy troops leaving camp now walk out through the main gate instead of piling up against the inside of the wall. A unit still inside the perimeter heads for the gateway first, steps clear of it, and only then makes for its objective.
- Formation orders hold off while a squad is still inside the wall, so a formation waypoint can no longer drag troops back into the stonework they were trying to get around.
- Siege crews are handled the same way. An operator riding or walking with an engine is routed out through the gate before it resumes following its machine.
- Replacement crews for a damaged engine now muster just outside the main gate rather than in the middle of camp, so they are not spawned behind their own wall.
- The gateway threshold is paved level with the gate and carried a block past the wall on both sides, so nobody drops into a dip or catches a step on the way through.

# 4.28.17 beta

- Enemy camp builders now raise a defensive wall around the edge of the camp's own territory instead of only putting up a handful of outbuildings. The wall goes up one side at a time so a crew can finish a section before starting the next.
- The wall keeps a wide main gate on the side the War Gate road comes in on. The gateway stays permanently open under a raised beam, so the road, reinforcements and the camp's own builders are never walled in.
- A guard tower goes up at each corner of the camp's territory, with battlements at the top for archers.
- Two extra camp sentries now take station either side of the main gate once it exists, and fall back to the wall line if their post is blocked.
- Wall and tower sections skip any column sitting over a ravine, water or foreign claim rather than abandoning the whole section, and a section that cannot be started at all is retried a few times before the camp moves on.

# 4.28.16 beta

- Enemy troops no longer march off cliffs and ledges into a lethal fall. A raider that spots a killing drop in front of it stops, kills its forward momentum and steps back, and the assault orders leave it alone until it is back on safe ground.
- Drops onto lava count as lethal, drops into water do not, and a raider warped forward past broken terrain no longer takes fall damage from the warp.
- Enemy bridge builders actually show up now. The crew check was looking for troop roles that sieges never assign, so no wave could ever produce a builder. Ordinary line troops are eligible again, and the start of a crossing is written to the server log.
- Fortify Perimeter is far more reliable. The job now goes to the closest free builder of your own instead of whichever builder happened to be nearby, never to a busy, fleeing or enemy camp builder, and the commission fails with a refund-safe error instead of charging you when the builder will not take the blueprint.
- Fortify Perimeter only queues wall sections your storage area can actually supply and tells you how many sections were left out, so a wall on a large claim no longer stalls part-way through with no explanation.

# 4.28.15 beta

- Give blocked enemy assaults a dedicated bridge-builder role, using an existing soldier rather than unlimited extra reinforcements.
- Build temporary crossings progressively across water and dry gaps toward the defending territory, with a configurable span limit, saved construction budgets, and restoration tracking.
  - New configs default to 24-block spans (`maxBridgeSpan`, configurable up to 64), four attempts, and 96 planks per raid (`maxBridgeBlocksPerRaid`). Existing configured span limits are retained.
- Keep bridge workers out of competing formation, ladder, breach, and straggler orders while they work; require safe, loaded construction routes and respect unrelated claims and later player edits.
- Move enemy captains and patrol leaders into the assault leadership instead of rear support positioning, while preserving ranged and siege support behavior.
  - Prevent the native captain land-army controller from overriding siege orders with regroup/retreat commands; retain native individual combat and ship controls.
- Add regressions for bridge construction safety, persistence, work-order isolation, and captain movement.

# 4.28.14 beta

- Command Center polish pass:
  - Better number readability for prices/reward text and affordability messaging (thousands separators and clearer emerald wording).
  - Tooltip wrapping now uses the active panel width, improving readability and reducing overflow pressure on scaled/tiny windows.
  - Long unbroken words in wrapped card text are now clipped with ellipsis instead of bleeding outside card bounds.
  - Bank messaging now consistently refers to your **purse** (instead of mixed purse/pack wording).
- Added responsive layout regression tests for `CoreHireLayout` covering roomy, constrained, and tiny-window scaled-canvas behavior.
- Added hardened Copilot branch auto-publish workflow for CurseForge beta uploads with branch gating, concurrency lock, token preflight, artifact sanity checks, deterministic version metadata, and manual fallback.

# 4.28.13 beta

- Integrate Copilot HUD, onboarding, pathfinding and hero refinements onto current main, preserving the latest camp, inventory and enemy-hero systems.
- Stabilize the Command Center crest and clarify upgrade affordability. Align Territory cards, hover regions and purchase controls; prevent compact description/status overlap.
- Cache expensive fallback searches and add configurable final-approach urgency and pathing diagnostics. Rotate bounded breach scans through all sides and validate loaded, dry, in-border standing positions.
- Use the native engineer compatibility initializer for player siege crews.
- Give Bloodthorn absorption on a full-health melee hit, Wildsong a temporary attack-speed attribute bonus on confirmed kills, and Stonehand protection after a successful shield block.
- Preserve attributed, cooldown-limited Starweaver and Ashenheart damage. Keep Ashenheart's working melee flame burst and describe it accurately instead of requiring a nonexistent fireball attack.

# 4.28.12 beta

- Give enemy waves access to all 20 player heroes with the same rarity weights, native equipment, stats and signature abilities.
- Default to a 10% chance per wave from wave 2 onward, with at most one hero replacing an ordinary wave slot. Preserve commander, ravager and illusioner slots. Configure enemyHeroChancePercent (0 disables).
- Save hero selection across squad retries and reloads; announce successful hero arrivals and show an Enemy Hero nameplate.
- Restrict enemy hero abilities to the defending faction and support to their own invasion. Keep enemy heroes unhireable and shadow summons inside raid population and cleanup tracking.

# 4.28.11 beta

- Give future enemy camp upgrades distinct supply shelter, workshop and command shelter layouts, with reinforced lower walls, timber frames and screened openings.
- Face upgrade entrances toward the main camp, keep a three-block-wide central access route and support raised roof ridges with continuous eaves.
- Let camp sentries find safe posts up to two blocks above or below their assigned ground level, while avoiding queued fortifications, water and the world border.
- Keep existing finite builder supplies, construction limits, claim validation and restoration tracking. Already-built camp structures are not rebuilt.

# 4.28.10 beta

- Make faction Treasury notices easier to scan with a gold [Treasury] prefix, green deposits, red deductions, signed amounts, thousands separators and singular wording for one emerald.
- Combine same-tick transactions into one chat notice, keeping gains and deductions separate so spending remains visible.

# 4.28.9 beta

- Fix loot rewards, siege crew kits and emerald payments appearing stale or missing outside the hotbar while the Command Center is open. Synchronize changed player inventory slots independently of the Core's display menu, including items picked up while shopping.
- Preserve use of all 36 inventory slots and existing full-inventory delivery rules.

# 4.28.4 beta

- Show faction Treasury changes in chat and the action bar: green gains, red spending/withdrawals, signed amounts and a gold Treasury label. Only the amount actually credited or debited is shown, including the Treasury portion of mixed payments.
- Notify online members of the affected faction. Combine interest catch-up into one notice and keep failed/zero transactions silent.

# 4.28.3 beta

- Notify players in chat when Forge detects a newer Siege Overhaul release, with installed/available versions and a clickable CurseForge download link.
- Delay the notice until the world has loaded and show it once per game launch, including across reconnects. Failed or disabled checks stay silent and never block gameplay.
- Remind multiplayer players to update the server and clients together. Add the release update feed and publisher checklist.

# 4.28.1 beta

- Pay raider, commander, and scout bounties only when a member of the defending faction or one of its owned Recruits lands the kill. Environmental deaths and unrelated combatants still count toward normal siege progress but no longer create treasury emeralds.
- Keep scout bounties disabled while a non-rewarding manual raid is active, and carry the scouting payout into the ensuing raid so the configured per-raid cap covers scouts, troops, and commanders together instead of resetting at wave one.
- Correct Fortify Perimeter's storage validation to search from the commissioned builder, matching Workers 2's actual 64-block runtime lookup. A storage area reachable from the Core but not the builder can no longer be accepted and leave the job stalled.
- Stop wall-foundation scans at non-replaceable obstructions instead of queuing a disconnected section beneath them. Existing blocks remain protected and Workers 2 receives a continuous build path.
- Keep each wall segment and corner pillar at its configured height relative to its terrain-adjusted base. Sloped claim edges no longer create empty, buried, or incorrectly tall columns from a top fixed to the Core's Y level.
- Include the half-block vertical spawn offset in siege-vehicle clearance, so ceilings in the top intersected layer are reported before native collision rejects the deployment.
- Convert centered non-integral vehicle widths to the block columns they actually intersect instead of rejecting an unnecessarily large pad. Siege-yard help, purchase messages, and item tooltips now report the selected vehicle's actual pad size and required headroom.
- Added regression coverage for Workers storage masks, wall foundations, vehicle dimension conversion, fallback sizing, top-layer clearance, and player guidance.

# 4.28.0 beta

- Newly generated configs award 5 emeralds per completed wave instead of 4. Forge preserves values already stored in an existing server config, including the former default of 4; set `victoryEmeraldsPerWave` to 5 to adopt the new balance without overwriting intentional custom settings.
- Reduced major purchase prices: Fortify Perimeter 900 emeralds; Fortified Walls 700; Watchtower 500; Provisioning 900; Iron Levy 600; ballista crew 400; and catapult crew 480. Recruit, worker, hero, loot-box, temporary-buff, bounty, and interest values are unchanged.

# 4.27.1 beta

- Toned down the combat bounties added in 4.27.0 after they filled the treasury too fast on longer raids. Per-raider bounty is now 0 by default so a 30-raider wave no longer stacks another 30 emeralds on top of the wave-clear reward. Turn raiderBountyEmeralds up if you want per-kill bounties back.
- Commander bounty lowered from 24 to 8 emeralds. Scout bounty lowered from 4 to 2.
- Added maxBountyEmeraldsPerRaid, a hard cap on the total bounty a single raid can deposit into the treasury (default 32). Wave-clear payouts are not counted against it. Set to 0 to remove the cap.

# 4.27.0 beta

- Combat now fills the faction treasury directly. Every raider your faction defeats deposits 1 emerald into the bank, the siege commander deposits 24 on top of the existing per-player bonus, and every enemy scout your faction kills before the raid deposits 4. These are on top of the guaranteed wave payouts, and they show up on the Bank tab activity graph the same way wave payouts do so you can watch the treasury fill in real time during a big fight.
- All three bounty amounts are individually configurable and can be set to 0 to disable. Manual raids started with /siegeoverhaul start still respect the manualRaidsGrantRewards toggle, so bounty farming is off by default.
- Rough scale at defaults: a 30-raider wave with a commander is worth about 54 emeralds in bounties on top of the normal wave reward, and a scouted raid adds another 4 to 8 for cleaning up scouts.

# 4.26.0 beta

- Fortify Perimeter now fills gaps under the wall. If a wall column runs over a pit, ravine, or ledge, the wall extends downward through the air until it hits solid ground (up to 8 blocks). This closes the hole with wall material and gives the Workers 2 builder ground to stand on for the next column, so a single pit no longer stalls the whole perimeter.
- Siege equipment kits now size the ground-clearance check to the actual vehicle. Catapults are 4 blocks wide, but the old check was a fixed 3 by 3, so the vehicle's corners fell outside the checked columns, the vanilla collision test found a block inside the bounding box, and the deployment failed with the misleading "Siege Weapons rejected the deployment spot" message. The kit now reads the entity's real width and height from Siege Weapons at commission time and inspects every column and vertical layer the vehicle will occupy, with a specific message about which block is in the way.
- Ballista deployments are unaffected (2 by 2 fits inside the previous 3 by 3 window) but they now get the same accurate error messages when something is in the way.

# 4.25.0 beta

- Audited how Workers 2 builders actually pull materials from a storagearea. Found one silent failure mode that ate Fortify Perimeter commissions if the player's storagearea wasn't configured right, plus a subtle mismatch between where we searched for the storagearea and where the builder searches at runtime.
- Fortify Perimeter now checks that your storagearea has Builders enabled in its GUI before it accepts your commission. Workers 2 gates every storagearea by job type, and if Builders isn't ticked the builder silently reports "No available storage found nearby" even though your storagearea is sitting right there. You now get a specific message telling you to open the storagearea and turn Builders on.
- The storagearea search now anchors on your Core instead of on the builder. Workers 2 searches for storageareas from the builder's current position (which moves), and the Core is the stable centre of your perimeter, so this matches where the builder will actually spend most of its time working the wall.
- If a storagearea in range doesn't have Builders enabled we still surface it (as a fallback candidate) instead of silently returning "not found", so you always get the actionable message.

# 4.24.0 beta

- Audited Fortify Perimeter against the actual Workers 2 source. Two more real problems came out of it and are fixed here.
- The buildarea was being wired up before it was spawned into the world. Workers 2 always expects the reverse order (place the area, then push the blueprint), and its own setStartBuild reads live block state at the target positions to compute what still needs to be placed. Fortify now spawns the buildarea first and then hands it the blueprint, matching the built-in flow.
- The buildarea was being tagged with the RAIDERS faction and team access off, which was harmless for the player-UUID access check but wrong on paper and could interact poorly with other systems that filter by faction. A new player-area path creates the buildarea with no team gating so it looks and behaves like any manually placed one.
- Also passes the player's actual game name through to the area's owner label so the tooltip reads as your player instead of "Siege camp".

# 4.23.1 beta

- Fortify Perimeter now attaches your builder to the job the same way any native Workers 2 job does, instead of forcing our raider night-shift goal onto them. The raider goal only fires when the worker has a raid team tag and an active enemy camp, so installing it on your builder actually disabled their AI. v4.23.1 uses a player-safe attachment that sets ownership and work state and leaves every native goal in place, so the builder walks to the buildarea, pulls blocks from your storagearea, and places them like any Workers 2 build.
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
