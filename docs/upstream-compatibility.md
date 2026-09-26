# Recruits and Workers source compatibility review

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
