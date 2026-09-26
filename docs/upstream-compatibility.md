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
