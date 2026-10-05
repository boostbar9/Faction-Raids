# Recruits and Workers source compatibility review

## Commissioned builder arrival dispatch (pending validation)

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982` (2.0.3), `BuilderWorkGoal.tick` / `placeBlocks`, `WorkerPathNavigation.moveTo` and `WorkersAsyncPathfinder.processPath`. The native goal's movement check runs every tenth tick, but placement runs every fifth tick. `placeBlocks` can pop its first target and mutate it in the same call without checking distance. The commissioned wrapper previously corrected a route only after that dispatch.

The wrapper now checks the guard's exact next mutation cells after normal world/ownership/inventory admission and before dispatching the native tick. It waits for loaded safe footing outside reserved wall columns and the unchanged native horizontal work reach, without popping the queue, granting blocks, advancing work state or bypassing the native material operation. Standing occupancy admits only air or the existing audited single-cell plant allowlist with empty fluid and collision shape, so ordinary/approved addon grass does not become a new access obstruction; standing never authorizes clearing those extra cells. Storage and sleep remain native interruptions, including the 4.52.6 ground-recovery exclusions.

Workers returns budget-limited paths with `canReach=false` and a closest reached endpoint. The previous commissioned approach rejected every such path, even when the endpoint made safe surface progress. A processed multi-node partial path may now nominate a loaded, dry, collision-free, unreserved endpoint strictly closer to the same work target. The wrapper reissues native coordinate movement to that waypoint, preserving the native async callbacks; it does not treat that waypoint as permission to work. Stale, timed-out, unloaded, hazardous, nonprogressing and self-clearance recovery partial paths remain refused. Complete path probes still require their actual end node, not the upstream first-candidate label.

Regression source exercises the real pinned goal's fifth-tick dispatch with mocked terrain/entities, then requires zero block/item/queue mutation before arrival. It also exercises the real bounded native pathfinder and mocked synchronous/asynchronous route lifecycles. A supported four/eight-block-foundation fixture explicitly retains the existing native vertical behavior; this patch does not claim to solve vertical access, scaffolding or terrain grading. These tests are not live game evidence. Local execution is blocked before Gradle startup by the unavailable `services.gradle.org` download; exact-commit GitHub Build and focused native building/handoff validation are required before release. Cardinal entrances and bounded player earthworks are separate staged planner work, not included in this patch.

## Commissioned builder ground recovery respects native interruptions

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982` (2.0.3), `BuilderWorkGoal.canUse`, `AbstractWorkerEntity.shouldWork` / `needsToSleep` / `needsToGetToChest`, `WorkerGoHomeGoal.start`, and `AbstractChestGoal.moveToPosition`. Native construction requires `shouldWork()` with neither a sleep nor a chest need. Home and storage activity retain `currentBuildArea` and use the same follow state 6 as construction, so a saved job plus that state does not prove the builder is currently available to work. Previously the recovery timer could mistake six seconds resting under a roof for an underground construction stall and move the worker onto the roof.

The ground-recovery boundary now independently requires those native scheduling methods, excludes sleeping and active item use, and clears its own stall observations while an interruption is present. Missing or throwing scheduling APIs disable recovery. It does not call the native goal's area-cleanup predicate, change owner commands, end sleep/eating, rewrite supply requests, or alter inventories, paid-job links, native placement or destination selection. After an observed interruption ends, recovery needs a fresh six-second stationary observation window.

Regressions exercise the pinned companion's actual `BuilderWorkGoal.canUse`, inherited `shouldWork`, home start and storage movement methods with mocked entities, plus the recovery timer and exclusion boundary. They cover sleep, storage, ownership/commands, active item use, unavailable APIs and resumed genuine ground recovery. These are isolated companion-method and mocked-terrain tests, not a live Minecraft sleep/resupply playtest.

## Free building review and native site admission (4.52.6)

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, `BuildArea.scanBreakArea` and `BuilderWorkGoal`: native breaking scans existing non-air targets, while sealed commissioned areas continue to prohibit unrestricted `FREE_AREA`. The existing single-cell vanilla-plant allowlist, paired-plant refusal, supported target materials, immutable reservation and neighbor-update protection remain unchanged.

Whole-perimeter review previously checked the footprint but omitted the final guard's two-ring neighboring-block check. It now checks every still-pending target during the free review and reports the same blocker before a native section is created. Already-matching targets do not acquire a new mutation requirement. Manual and perimeter footprint/headroom reviews share the guard's original replacement predicate, including paired-plant and block-entity refusal; manual permissions are checked at every reserved height. This prevents a ready-looking review from admitting clearance that the native snapshot cannot accept, without expanding excavation or touching a saved paid plan.

Targeted unit regressions cover outside sand, unloaded neighbor chunks, paired clearance, safe single-cell plants, height-specific permission refusal and already-matching perimeter targets. The opt-in native client fixture adds explicitly seeded pre-commission obstacles using the real registries, faction claim and production prepare APIs, checks exact block/coordinate feedback, and verifies unchanged Treasury, worker inventory/receipts and absence of new project records. It restores only those fixture seeds before the existing native 83-block wall/supply/reload/cancel scenario. Those fixture checks are not evidence of this user's exact terrain or a complete large perimeter walkthrough.

## Protected builder hand provenance after terminal cleanup

Rechecked the actual pinned Recruits 1.15.2 and Workers 2.0.3 runtime bytecode and Workers source at `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`. Recruits `AbstractInventoryEntity.readAdditionalSaveData` first invokes its superclass load and reconstructs the native inventory, then separately calls `ItemStack.of(HandItems)` for slots 5/4. Its public `setItemInHand(MAIN_HAND, stack)` binds the live hand and slot 5 to the same supplied object. Workers `AbstractWorkerEntity.switchMainHandItem` moves the old live-hand object into a selected cargo slot and invokes that native setter. These are the existing audited APIs; the patch adds no private inventory writes or optional-companion assumptions.

The short native handoff fixture on source `12eb6fa6b095e7b8603c779142edbafcdcd05747` proved a terminal load boundary missed by the earlier active-job guard. Immediately before cancellation save, slot 5 and the live hand shared one 57-cobblestone object. After reopening the canceled compact-terminal job, they were distinct equal-valued 57-cobblestone objects, while all other observed inventory/world quantities remained unchanged. The independent-object conservation oracle therefore counted 409 against 352 supplied cobblestone. This proves a split mirror; it does **not** prove that a later native callback already materialized extra cargo. The failed world is not repaired or used as a source of guessed quantities.

`ConstructionEditLedger` now retains a separate strict `HandLifecycles` list, bounded to 4,096 immutable per-builder records. Each binds the actual builder UUID, original commissioning owner, original area UUID, opaque receipt UUID and ledger generation. A matching selector stays on the builder after child/project cleanup. Original owner is provenance only, so later native ownership transfers do not invalidate a value-only alias check. The record never grants work, payment, storage or spatial reservation authority; terminal cleanup still removes those independent authorities normally. Existing records are reused across subsequent jobs and never evicted to make room. Capacity/identity checks occur before new protected handoff state is written; new builders pause before payment at the bound.

The entity selector and world record must agree exactly. Missing halves, wrong generations, foreign builder IDs, malformed fields and ambiguous inventory values pause without recreating receipts or choosing stock amounts. Registration follows authenticated source proof and the unchanged full item/count/NBT/ForgeCaps equality and native-callback verification. Normal old protected jobs can migrate only while their exact loaded snapshot/reservation or verified project/terminal authority still proves builder, original owner and ledger identity. Unmarked legacy workers and already-cleaned old workers without proof remain untouched. Ordinary cancellation of an older manual job with an unloaded, unproven builder waits for that original builder, retaining the marker and reservation. Forced/admin removal can destroy older proof outside that flow; a remaining old worker selector then stays paused rather than fabricating replacement provenance. Death/corpse paths do not mint hand receipts or repair inventories.

The first post-load living boundary verifies the hand-only receipt even after completion/cancellation or dimension transfer. A terminal worker with active item-use state pauses without a setter, amount change, `stopUsingItem`, or review flag solely for normal use; letting its unguarded legacy callbacks run cannot be proven safe. The diagnostic does not promise that a canceled living tick can finish eating. Existing guarded active-job item-use handling is retained.

Regression coverage includes manual completion/cancel reloads, staged retirement and terminal-compaction models, authenticated old snapshot/terminal migration, normal ownership transfer, capacity, malformed/one-sided receipts, active use, unsupported runtime, persistent unequal-pair review, and unchanged hand callback/capability safeguards. These are isolated model/mocked integration tests, not a replacement for the same strict native cancellation-reopen fixture or native completed-manual-job reopen evidence.

## Commissioned wall-builder arrival follow-up (4.51.22)

A late review found that the first scoring pass could still let a nearby roof beat farther same-elevation footing, and that collision-free hazards in the worker's body cells were not rejected. The search now completes its entire player-elevation pass before reading any surface heightmaps. Its support, feet and head checks share the hired-unit danger set: water, lava, magma, campfires, cactus, fire, soul fire, berry bushes, wither roses, powder snow and portal blocks. Regression coverage blocks the anchor cell while leaving farther indoor floor and a closer roof available, then verifies hazards in both occupied cells. No blueprint, material, inventory or native Workers job state changes.

## Commissioned wall-builder arrival (4.51.21)

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, `entities/ai/BuilderWorkGoal.java`. The native goal keeps the assigned build area and owns subsequent pathing, supply requests and placement; this compatibility change only chooses a safe starting point before the existing direct handoff when the hired builder is more than 24 blocks away. It checks at most 81 already-loaded columns, prefers clear footing at the player's elevation over a roof heightmap, then considers nearby surfaces within eight vertical blocks. Fluid, leaves, hazards, world-border crossings, collisions and occupied bodies are rejected. No terrain, inventory, blueprint or native job state is changed by the search. Regression fixtures are mocked integration checks, not an interactive companion-mod playtest.


## Projection camera bounds (4.51.20)

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, `client/render/WorkerAreaRenderer.java` and `entities/workarea/AbstractWorkAreaEntity.java`. The native projection already maps blueprint cells to fixed world positions. However, its entity renderer inherits the shovel marker's small culling box, so an enabled projection can disappear when that corner leaves the camera. The client now reads native `getAlwaysShowProjection()`, `canPlayerSee(Player)` and `getArea()` and tests the full native world-box envelope against the current frustum. Only while that envelope is visible does it temporarily bypass the marker-only frustum test. Original flags are restored immediately after native entity rendering, before the next frame, and on world changes. No entity position, collision/picking box, blueprint, worker state or native projection preference changes.

Verified the Forge 1.20.1 `RenderLevelStageEvent` and `LevelRenderer` patch: `AFTER_SOLID_BLOCKS` precedes entity rendering and `AFTER_ENTITIES` follows it. Official Minecraft 1.20.1 client bytecode (`client.jar` SHA-1 `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838`, mappings SHA-1 `6c48521eed01fe2e8ecdadbd5ae348415f3c47da`) confirms `EntityRenderer.shouldRender` applies entity distance limits before `Entity.noCulling`, preserving native distance/tracking behavior. Disabled previews stay disabled, including the automatic-preview size limit of 1,024 cells. Larger jobs can still enable the native projection manually.

Regression fixtures cover whole-plan camera intersection, repeated-frame/restoration lifecycle, original flags, native visibility permission, disabled projection and missing/throwing compatibility APIs. Source/bytecode checks and these tests are not a rendered Minecraft session or companion-binary playtest.

## Player wall surface approaches (4.51.14)

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, `entities/ai/BuilderWorkGoal.java`. `moveToPosition` uses horizontal reach (20 for the area, 40 for block work) and otherwise navigates to the exact target Y. A reachable cave path can therefore approach a buried marker, while a worker directly underneath can pass the reach check. The commissioned-job wrapper now checks the completed path's endpoint against loaded, dry, collision-free surface footing before retaining it; horizontal proximity alone does not suppress correction from underground. Pending native async paths remain untouched until processed, safe native paths are retained, and cached standing destinations are revalidated. This does not grant excavation permission or change blueprint targets, material fetching, inventory, ownership or native build execution. Source/API regression coverage is not interactive companion-mod playtesting.

## Starter Core Guard (4.50.3)

Rechecked Recruits `cff03e085d65653406a8b6ddcdd0ebff615c3e48` (1.15.2) on 2026-09-28. `AbstractRecruitEntity.hire(Player, RecruitsGroup, boolean)` posts the cancellable hired event, enforces the native player/faction unit limit, assigns owner and team, increments the player's unit count, resets the payment timer, and calls `setFollowState(2)` / `setAggroState(0)`. The boolean controls dialogue, not cost bypass. Follow state 2 stores the recruit's current position; `RecruitHoldPosGoal` returns it to that position. The guard uses this existing hiring path with native dialogue disabled and a dedicated confirmation, rather than rewriting ownership, counts or AI. Regular commands remain available.

The one-time guard skips only Siege Core's initial Treasury payment. Its normal configured hire cost remains on the entity, and native payment/food/upkeep logic is unchanged. Gear occupies native inventory slots 0-5; eight bread go in slot 6. No hero attributes, immortality or resupply loop is added. Spawn search stays bounded, loaded, dry, collision-free and inside the faction's claim. Delivery is deferred to the core's next block tick so a reverted placement cannot grant a recruit; opening a valid core retries failures. A separate saved faction-grant ledger survives relocation, missing cores and entity death.

Regression tests cover grant persistence, retries, reentrant native callbacks, eligibility, finite equipment, free-grant dispatch and deferred placement. They do not execute companion binaries or constitute an interactive Minecraft playtest.

Reviewed 2026-09-26 against Siege Overhaul 4.47.16, with the diplomacy correction in 4.47.17.

## Pinned upstream references

- [Villager Recruits source](https://github.com/talhanation/recruits/tree/cff03e085d65653406a8b6ddcdd0ebff615c3e48): commit `cff03e085d65653406a8b6ddcdd0ebff615c3e48`. `build.gradle` and `META-INF/mods.toml` declare **1.15.2**, Minecraft **1.20.1**.
- [Villager Workers source](https://github.com/talhanation/workers/tree/29d26e1df6475fc8d043dc5d455f67b2fd1e9982): commit `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`. The same files declare **2.0.3**, Minecraft **1.20.1**.

Use build/resource metadata rather than the upstream `gradle.properties` example versions. These are source snapshots, not a verified source-to-CurseForge-binary correspondence. Both mods' metadata says All rights reserved; this review references API behavior and does not vendor their implementation.

## Contracts checked

| Integration | Upstream contract | Siege Overhaul result |
| --- | --- | --- |
| Diplomacy manager | `FactionEvents` has separate `recruitsFactionManager` and `recruitsDiplomacyManager` fields. Only the latter owns `getRelation` and `setRelation`. | **Fixed in 4.47.17.** We previously invoked diplomacy methods on the faction manager, causing caught reflective failures. Cache the correctly typed field and read its current value for each operation. |
| Diplomacy direction and IDs | `setRelation` stores one directed relation using raw faction string IDs; states are NEUTRAL, ALLY, ENEMY. | Existing two-direction updates and `team:` normalization match. Native event cancellation and persistence remain upstream-controlled. |
| Server lifecycle | `FactionEvents.onServerStarting` replaces both managers. | Cache the field, not the manager instance. Regression covers replacement and a temporarily null manager. |
| Worker ownership | Recruits exposes `setOwnerUUID(Optional<UUID>)`, but `getOwnerUUID()` returns nullable UUID. Work areas compare worker owner UUID with their player UUID, or require enabled team access. | Our setter signature and readers support this. Enemy jobs use a finite job owner and team access disabled; player jobs retain player ownership. |
| Builder work states | Workers `shouldWork()` requires owned state and follow state 0 or 6. Recruits state 3 preserves a specified hold position; state 2 replaces it with the current position. | Our work/park and guard-position values match. Player builders keep native AI; the camp-only wrapper changes the enemy night shift. |
| Storage permission | `StorageArea.StorageType.BUILDERS` has index 2 and uses a bit mask. | Our `1 << 2` storage mask and enum-name inspection match. |
| Blueprint origin | `getOriginPos()` uses `getOnPos()`. `setStartBuild(false)` maps cells using facing, clockwise direction and `width - 1 - x`. | Our raised area entity origin, SOUTH facing and relative coordinates match; existing regression round-trips negative coordinates. |
| Native material consumption | `BuildBlockParse.parseBlock(Block, Level)` can return a recipe ingredient; native placement consumes one parsed item per cell. | Our camp supplier uses that parser and counts each cell, including non-block ingredients. The one-argument fallback remains for compatibility. |
| Build clearance | `setFreeArea(true)` enables broad volume clearing; native break scans otherwise compare the planned cells. | Our blueprint handoff sets it false. Existing ownership, obstruction, corpse and restoration guards remain necessary. |
| Claims and identity | Claim manager exposes chunk-based lookups; claim center is `ChunkPos`; faction manager owns `addTeam`, display-name, banner and color persistence. | The inspected claims/identity bridge signatures and expected return types match. |
| Native siege events | Although stored under an `events` source directory, `SiegeEvent` declares package `com.talhanation.recruits`; Start/Tick inherit claim and level getters. | Our reflective event class names and getters match. |

## Regression and verification limits

`RaiderDiplomacyApiTest` supplies independent API fixtures with distinct manager types, then exercises the public bridge: two-way hostility, asymmetric relationship repair, neutral reset, server replacement, missing manager and solo fallback identities. It does not compile or execute upstream implementation classes.

Source review checks the contract; it does not certify every feature or replace a live Forge server/client test with the actual companion JARs. Terrain-dependent navigation, concurrent builders and other mods' event listeners still require runtime evidence. No interactive Minecraft playtest was performed for this review.

For future bridge changes, inspect the matching upstream source and record its commit, method owner, parameter/return types, lifecycle and side effects. A permissive mock with the desired method names is insufficient: keep separate upstream manager types in tests so the wrong receiver cannot pass unnoticed. Preserve the current required dependency contract.

## Small Ships and Siege Weapons review (4.47.18)

References inspected on 2026-09-26:

- Small Ships `v2-1.20_beta`, commit `08aed675409d9bbd93c352a4082971ba88c29366`: metadata declares Minecraft 1.20.1 / 2.0.0-b1.4.
- Small Ships `1.20.1`, commit `842e309875b1e074d8b4a944407ca92ad36ebe18`: newer source with multipart hulls and seat assignments. Do not assume this source matches a published JAR or that its registry AABB covers all hull/mast parts.
- Siege Weapons `0748a61fc0f6d4c4eede5ab0ac6e72055838e0dc`: build version 0.2.5 / Minecraft 1.20.1. Source-to-release byte correspondence has not been established.
- Recruits controller source is the 1.15.2 snapshot pinned above.

Verified corrections in 4.47.18:

- Naval boarding no longer uses `startRiding(vessel, true)`, which bypasses native passenger admission. The normal overload enforces Small Ships locks, configured exclusions and capacity. Already-mounted mobs are not taken from another vehicle. Actual vanilla fallback boats receive at most two crew even when Small Ships is installed.
- The spawn search now checks all columns of its square footprint, including diagonal obstructions, the world border, build height and nearby entities. This fixes the previous cross-shaped sampling and avoids placing new vessels on existing ones. It does not certify the full multipart collision envelope of unreleased ship models.
- Rejected boarding and failed vessel spawns use checked landing positions instead of blindly teleporting to the saved beach block. Existing passengers are not ejected by that fallback.
- Native Recruits engineer repair requires both iron nuggets and plank-tagged items. New friendly and enemy operator inventories receive 16 of each once, alongside their existing ammunition/food; no tick-based refill or existing-inventory mutation is introduced.

Contracts that match: catapult/ballista registry IDs, bolt and cluster-shot item IDs, the public engineer controller fields, `ISiegeController.tryMount(Entity)`, `getSiegeEntity()`, native movement-order setters and squared arrival tolerances. Automatic raids deliberately convert non-ranged engine choices to ballistas because no ram/tower controller is wired; changing that requires implementing their own assault behavior, not merely allowing them to spawn.

Follow-up work, not claimed fixed by this release:

1. Replace direct convoy velocity/yaw writes with a verified native sailing handoff. Small Ships calculates velocity from its internal speed and sail state; direct velocity can conflict with this. Compare behavior with the actual supported companion JARs before publishing that change. Older Recruits assumes its captain is passenger zero; newer Small Ships uses assigned helm seats, so blindly attaching the captain controller is insufficient.
2. Verify full hull/mast placement for the selected ship version, including multipart bounds and safe spacing. The repaired square search is a minimum footprint, not a guarantee for arbitrary future ships.
3. Dedicated captains, cannon provisioning, naval target selection and rams/towers are integration extensions. They need finite budgets, ownership checks, friendly-fire/claim protection and runtime validation before activation.

Tests cover admission flags, refusal/existing-passenger handling, diagonal terrain and ceiling rejection, nearby entities, borders/heights, checked fallback landing and finite non-destructive repair supplies. They do not execute companion implementations or constitute interactive playtesting.

## Commissioned wall projections (4.49.0)

Workers commit `29d26e1df6475fc8d043dc5d455f67b2fd1e9982` renders the shovel independently, but only renders BuildArea structure NBT when focused, showBox is true, or getAlwaysShowProjection() is true. Territory commissions previously never set the latter. Small new wall jobs now call the native setAlwaysShowProjection(true); jobs over 1,024 blocks keep manual projection because the native renderer parses/renders the full blueprint each frame. Missing presentation APIs do not fail the build job. The commission reports the actual native marker coordinates and explains closing the HUD and approaching it. Blueprint origins and all world build positions remain unchanged. This is an in-world job projection, not a pre-purchase HUD preview. Native entity tracking/frustum and ownership visibility rules still apply.

## Player wall access and diplomacy notices (4.49.7)

Rechecked the same pinned Workers 2.0.3 and Recruits 1.15.2 source commits above. Workers BuilderWorkGoal MOVE_TO_WORK_AREA navigates to getOnPos() and tests horizontal squared distance <20. Perimeter origins use global minY, which can be underground at the origin corner on sloped claims. The commissioned-job wrapper delegates native lifecycle and, only for failed routes on the same owned saved job, finds reachable standing space inside that horizontal threshold. Blueprint cells, inventory, supply fetching and day/night schedule are unchanged. Restored jobs install the same wrapper.

Recruits stores each direction separately, but notifyPlayersInTeam sends to both factions. Use the existing five-argument setRelation overload: one notice per two-way transition, silent active-siege repairs, no writes for unchanged directions. Native events, cancellation and persistence remain in Recruits.

Regression fixtures cover buried marker access, failed/unloaded/unsafe paths, bounded search, native scheduling delegation, duplicate notifications and silent repairs. These are not companion-binary integration tests or interactive gameplay testing.

## Commissioned builder lifecycle (4.50.1)

Rechecked Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982` and Recruits `cff03e085d65653406a8b6ddcdd0ebff615c3e48` on 2026-09-28.

- `BuilderWorkGoal.start()` selects a work area again after sleep or supply collection. `SELECT_WORK_AREA` searches for other eligible areas even when `currentBuildArea` is already set. For a live, owned, explicitly linked commission accepted by native `canWorkHere`, `WallBuilderAccess` now performs the native selection initialization and proceeds to `MOVE_TO_WORK_AREA` without searching for a replacement. This includes resetting the package-private completion latch, area activity/time and stale block target. Unlinked, reassigned, transferred, removed and completed jobs keep native selection.
- Workers' `WorkersGroundPathNavigation` extends Recruits' `AsyncPathNavigation`. `AsyncPath.canReach()` returns false until `isProcessed()` is true. Surface approach searches now retain pending paths, inspect readiness on the server tick and validate the endpoint before asking native `moveTo` for its movement path. The multi-target result is a reachability probe: installing it after processing would miss the native target/reach-range callback. Integer endpoint coordinates avoid upstream negative-coordinate truncation. Results expire after 100 ticks; stopping/changing jobs or targets discards them. Search cadence remains bounded, and active native paths are left alone.
- Native material requests, storage visits, placement, completion, sleep and owner-command scheduling stay delegated. No new block placer, inventory supplier or free materials were added. Existing terrain planning, saved job IDs and prices are unchanged.

Before adding other commissioned structures, reuse the native `BuildArea` handoff and ownership/persistence contract. Cover asymmetric blueprint coordinates, multi-block materials, reachable approaches, storage interruption/resumption and completion followed by a second job. Do not generalize the wall planner's natural-ground/column assumptions to arbitrary structures without corresponding checks.

The regression fixtures reproduce the inspected selection/restart and deferred-path contracts independently; they do not execute companion JARs. This source review does not establish source-to-release byte correspondence or certify terrain navigation in a running Minecraft world.

### Staged perimeter approach: multi-target endpoint contract (4.52.0)

The short hollow-perimeter native handoff run completed its first 95-block section, saved/reopened between sections, and then stalled with all 44 targets in the next section still pending. The builder remained at approximately `(129.94,65,3.92)` with supplies, no protection/cleanup blocker, and an unreachable navigation target `(128,70,3)` on the previous section. The fixed visible marker remained `(137,65,6)`.

Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, `WorkersAsyncPathfinder.processPath`, marks a multi-target search successful when it reaches **any** candidate, but reconstructs that path using the **first** candidate as its target label. Its end node is the reached candidate. This was also verified with `javap -c -p` against the exact remapped Workers 2.0.3 development artifact used by the failed run: `workers-567450-8351157_mapped_official_1.20.1.jar`, SHA-256 `1d819c18d0f1fba8e26bdfa0b53add1776ec7b429ce9f46f79d8409dd88b5d7c`. At successful-branch bytecode offsets 592–613, it loads the reached node, then `List.get(0).getValue()`, then calls `reconstructPath(..., true)`. This identifies the development-runtime bytes; it is not a production release JAR hash.

`WallBuilderAccess` now validates the processed, reachable probe's actual end node against its allowed standing sites before passing integer coordinates to native `moveTo`. An absent or noncandidate endpoint is rejected even when the target label is valid. Deferred probes additionally recheck that the endpoint is still loaded, safe, unreserved and inside the existing reach bound. The heightmap/cave protections, native goal lifecycle, async wait/expiry/cancellation, fixed marker, claims and placement guards remain unchanged. No teleport, block clearing, queue completion or stage-layout change is used.

The targeted regression executes the pinned companion's actual path reconstruction over a bounded mocked node graph, then checks immediate and deferred handling of the mismatched roof label and ground endpoint. It also covers absent/out-of-range/wrong-height endpoints and deferred hazard, unload and reservation changes. This is companion-algorithm integration with mocked terrain, not a substitute for repeating the native gameplay handoff.

### 4.50.7: player defense structures

Archer Barricade, Watchtower and Gatehouse plans reuse the player BuildArea handoff and `PlayerFortificationJobs` persistence. Rechecked Workers commit `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, `entities/workarea/BuildArea.java` and `entities/ai/BuilderWorkGoal.java`: mirrored local X, noncreative `setStartBuild`, level-by-level queues and native storage/material handling are retained. Plans use cobblestone and oak-plank full blocks only, including broad stepped approaches; there are no multipart blocks, custom worker inventories, creative placement or enemy work shifts. Whole-site validation is separate from the terrain-following perimeter planner. Plan placement requires an already hired, exactly owned idle builder.

Regression coverage checks asymmetric rotation/world-coordinate recovery, open access routes, dry flat claimed sites, occupied cells, failed handoff/payment and plan consumption. Existing player-job recovery and bridge fixtures cover reloads, material handoff and completion followed by another job. A live companion-mod session still needs to confirm the three blueprints finish, storage interruption/resumption, troop stationing and appearance at GUI scales; mocked/source checks are not playtesting.

### 4.50.8: preview and read-only construction reports

Rechecked the same pinned Workers commit, including `BuildArea`, `BuilderWorkGoal`, `AbstractWorkerEntity` and `world/NeededItem`. Native remaining work is the sum of `stackToPlace` and `stackToPlaceMultiBlock`; `getRequiredMaterials()` counts the full blueprint, so it must not be presented as the remaining supply deficit. Reports use positive-count required `neededItems` requests and their native match keys, and distinguish sleep/night, ownership commands and work assignment. An unreadable API reports unknown progress, never false completion. Queues, inventories, worker orders and pathfinding are not modified by inspection. New commissions save their original queued count and label; older jobs fall back to their blueprint cell count.

Plan previews use owner/dimension/time-bound item NBT and vanilla inventory synchronization. Confirmation repeats the same server validation used by the preview, then uses the existing paid native handoff. Client outlines are presentation only. No custom client-authoritative placement packet was added. Interactive Minecraft rendering and completion/resupply tests remain unperformed because this environment has no configured companion-mod client instance.

## Civilian settlements and offer equipment (4.51.0)

Core civilians are tagged vanilla `Villager` entities. They retain the vanilla brain, trades, workstations, beds and food/bed-gated breeding. Assigned professions receive one experience point to avoid the novice profession-reset behavior; matching workstations are still necessary for restocking. Non-owned villagers are untouched. Registered civilians use their faction's scoreboard team without consuming Recruits' hired-unit quota. Portal travel is blocked; claim-boundary recovery uses validated previously safe ground, and out-of-claim POI memories release their reservations. If the previous safe location is obstructed or the faction loses that claim, a bounded scan checks safe loaded ground near the current core. If no site exists, a saved waiting state pauses AI and taxes until recovery is possible; no unsafe teleport is forced.

The saved per-faction civilian ledger is independent of core location and holds two lifetime starter grants and individual game-tick tax clocks. Population is capped at 64. Taxes go to the Treasury at one emerald per complete 24,000 game ticks per registered living villager, including unloaded residents; entity death/discard removes membership. No real-time offline accrual. An occupied/missing owned core suspends tax clocks. Core removal is explicitly saved, preventing unloading the old core chunk from re-enabling taxation. Vanilla beds and food are not supplied automatically.

Recruits 1.15.2 source at `cff03e085d65653406a8b6ddcdd0ebff615c3e48` was rechecked: `AbstractRecruitEntity.hire` assigns ownership/team and does not replace the custom name or kit. Server-generated offer inventories and names are persisted for each rotation and used for both delivery and read-only menu equipment slots. The client uses those synchronized slots instead of independently randomizing gear. Hero stats remain on the existing hire path. Armor rendering continues through the native Recruits renderer.

Verification: regression coverage for tax timing/persistence/removal/caps, starter retries, portal blocking, safe boundary return and saved equipment round-trips. This does not constitute an interactive companion-mod playthrough; breeding, job-site restocking and visual armor rendering should not be claimed playtested from these checks.
