# 4.52.8 — Saved plans, capture boundaries and richer hub pages

**Published as a CurseForge Release for Minecraft 1.20.1 / Forge. The official file page, notes and dependency relationships were separately verified on 2026-10-05.**

## Player changes and compatibility

- Unpaid manual and perimeter plan selections no longer expire after two minutes. Saved selections survive leaving their site, another dimension, logout and reload. Returning to a plan still requires current ownership, dimension, terrain, builder, permissions and cost checks. A changed quote needs a fresh visible review and another deliberate confirmation; persistent selection is not persistent server approval. Existing accepted jobs and payment records are untouched.
- Enemy cores show the server's capture boundary before entry, plus progress, eligible ally/enemy counts and range, height or sight-blocking feedback. Terrain projection, normal depth occlusion, freshness and dimension clearing are bounded. The default horizontal radius remains six blocks, with feet within two vertical blocks of the core's center; server settings remain authoritative. Capture balance is unchanged.
- Loot pages preview real possible Olympian ItemStacks from each chest's eligible pool, with rarity tabs, names, paging and complete native tooltips. Sealed rewards stay hidden until opened. Odds labels now describe existing rarity floors; random selection and prices do not change.
- Civilians show a paged read-only roster of actual owned native villagers: name, profession, portrait, remembered bed/workstation and tax state. Unavailable details do not assert a cause or invent a portrait. The existing 16-emerald action is correctly described as recruitment. Housing, food stocks and native AI remain separate from this report.

Minecraft 1.20.1, Forge 47.x and Java 17 remain the target. **Update the server and every client together for protocol 22.** Worlds, claims, accepted building jobs, supplies and Treasury remain compatible. The Sizeable Foliage 1.2.1 short-grass fix is preserved. Back up worlds before updating.

## Exact source, reviews and builds

[PR #273](https://github.com/boostbar9/Faction-Raids/pull/273) was reviewed at head `282324228dc29b4360edcdd7295d7a44aacde842` and merged as main `0976763349ac3bb9d5a3d8ed2647545f2ad95c67`. The native/PR checkout is `d16052ae084067b43c81fff2dc9536af45492462`. All three resolve to tree `eab9db0df613d5778dd09487fee31c1fe397fcbd`.

- Independent source COMMENT review **5410122866**, final-head Copilot review **5410146969** and [agent-recorded evidence comment 5988463890](https://github.com/boostbar9/Faction-Raids/pull/273#issuecomment-5988463890) are bound to that head. No blocking findings remain; both earlier clarity comments were fixed and resolved. Agent evidence is not represented as a personal maintainer approval.
- [Final-head Build 37265282593](https://github.com/boostbar9/Faction-Raids/actions/runs/37265282593), attempt 1: JAR artifact **11326162816**, regression artifact **11326675860**.
- [Merged-main Build 37266371483](https://github.com/boostbar9/Faction-Raids/actions/runs/37266371483), attempt 1: JAR artifact **11326252918**, regression artifact **11326387588**.
- Both runs passed **1,548 testcase occurrences across 258 XML suites**, with zero failures, errors or skips. Full testcase identities, outcomes and multiplicities match. Suite-manifest SHA-256: `9b4e963760047fcb5af66238b3d403a4353bc1b18fe3947b9ada4bcadd1ae949`.
- All **969 JAR entries**, including **533 production class files**, were reconciled. All nonmanifest payload bytes match; only the declared `Implementation-Timestamp` differs, with ZIP metadata differences recorded separately. Whole archives are not claimed byte-identical.
- PR JAR SHA-256: `47371087da0eac4ff51c86bad8aa5822a1d9c01564062ec6b245c6cceedba825`. It is PR evidence only; publication uses the tested merged-main artifact below.
- All 95 Python source/receipt regressions pass. Java 17, production-only contents, SRG reobfuscation, version, protocol and dependency metadata were verified from actual build artifacts.

## Fresh representative native coverage

All runs below passed on attempt 1 and the exact reviewed tree; their complete evidence manifests and strict validators were replayed.

- [Native HUD 37265282546](https://github.com/boostbar9/Faction-Raids/actions/runs/37265282546), artifact **11325883534**: all **154** actual framebuffers and bounded input/layout receipts. This preserves the original 106 cases and adds real item gallery, civilian page and capture visuals. Fresh affected and representative pixels were inspected, including all four unavailable-civilian scales, long Loot tooltips, wall/height/low-ceiling exclusions, boss bars and expiry. Capture's automated matrix is 960×720 scale 3 and 1440×960 scale 1; additional scale-2 manual checks are not asserted as automated capture coverage.
- [Native Building with actual Sizeable Foliage 37265282555](https://github.com/boostbar9/Faction-Raids/actions/runs/37265282555), artifact **11326013467**: retained the genuine native 83-block manual wall, exact 8-emerald debit / 992 balance, protected admission, cancellation, materials and reload/economy receipts. Original addon checksums and loaded version/class are verified; Very Short Grass survives free/unpaid review and is cleared only through accepted native work, with excluded plants/blocks still protected.
- The same Building run additionally compares real production starter-villager identities/details to the server report, receives the report through production watch/S2C, checks leave/return and normal menu close/reopen, and proves unchanged resident ledger, inventory, Treasury and collected taxes. No recruitment action occurs. Both actual live/reopened roster captures were inspected.
- [Native Building Server 37265282582](https://github.com/boostbar9/Faction-Raids/actions/runs/37265282582), artifact **11326511902**: all eight physical dedicated Forge GameTest checks pass.
- [Native Camp Spawn 37265282600](https://github.com/boostbar9/Faction-Raids/actions/runs/37265282600), artifact **11326611602**: flat, controlled shallow-water/forest and controlled rocky-water/forest establishment passes. All three original PNGs were reviewed.

These are bounded acceptance tests. HUD sample values and capture counts are visibly seeded; they do not prove a fought raid or live capture victory. The live civilian fixture reuses already AI-paused starter villagers and does not measure breeding, bed acquisition or tax accrual. Physical-server GameTest uses a FakePlayer with no authenticated clients. Camp establishment does not prove full decorative/wall completion. No arbitrary terrain, addon, shader, production-performance or crash-atomic cross-save guarantee is claimed.

The native runtime remains pinned to Minecraft 1.20.1, Forge 47.4.16, Recruits 1.15.2, Workers 2.0.3, Small Ships 2.0.0-b1.4 and Siege Weapons 0.2.5. Addon gameplay is the bounded integrated Building fixture, using ForgeGradle-remapped development bytes with the official Sizeable Foliage 1.2.1 original separately checksum-verified.

## Historical evidence retained

[Lifecycle 37249819787](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819787), [representative stage handoff 37249819858](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819858) and [staged unload 37249819831](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819831) retain their exact **4.52.6** source/tree/run/artifact/manifest pins and unchanged validators. Their relevant construction contracts remain unchanged and were rechecked. They are historical regression evidence, not fresh 4.52.8 gameplay. See the [4.52.6 record](release-4.52.6.md) for the setup and limits.

## Exact tested merged-main JAR

- Filename: `siegeoverhaul-4.52.8.jar`
- Size: **2,037,855 bytes**
- SHA-256: `d5eedf5ec85c55d4f83002324a5a7a6f88ecf2f95c2c51627c569e21aa9d9c60`
- Source/main Build/artifacts are identified above. No rebuilding is permitted in the release publisher.

## Publication and separate public verification

[Publisher run 37267561286](https://github.com/boostbar9/Faction-Raids/actions/runs/37267561286), attempt **1**, completed successfully at commit `5aee7314ce3d81a24c228dba3aefb9bf8dbc2d56` on the separate `publish/curseforge-v4.52.8` branch. Every step passed, including exact tested-JAR/regression/native replay, duplicate-upload protection, immediate pre-upload source/main/review revalidation, upload and receipt retention. The branch adds only the reviewed one-use workflow and is not merged into main.

- Executed workflow SHA-256: `c6999b0193049a8381de4e8d6a3118ea0acc4fefbe36361d224acc8b66501579`.
- Final pin-file SHA-256: `1babb111756e70bba02cfd1dcbba6113a1ecdab57f9ba2a73675bfbbc34cd75c`.
- Independent exact-pin review verified all 1,649 frozen files, aggregate `f641d66d0fb3cb73041a8ada8054f7c9f0029b8bddd731ea7619b02daf97961b`, all 172 offline tests and 856 deliberate mutation cases, and a complete exact-evidence replay. The reviewer found no blockers.
- Accepted receipt `curseforge-4.52.8-receipt.json` SHA-256: `7721e38cfb13e69e41d43b533664b298054dcc5cc057dea7395e667bbac62fd3`.
- Receipt ZIP artifact **11326598484**, SHA-256 `a586c6169d944eaa5b8b88b77942453dfcab75e8ae722be12d352972e75df97f`.
- CurseForge project **1364352**, Release channel, accepted file **9066362**: [official file URL](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9066362).

The upload receipt binds the file to the tested main JAR and all exact evidence, and intentionally records `accepted_upload` / `public_availability: not_yet_verified` at upload time. The official public page still showed **404 at 2026-10-05 05:28 UTC**. Accepted upload alone does not establish public availability. The separately delivered Library JAR was checked against the same SHA-256. No duplicate upload was performed.

The [official file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9066362) was publicly visible at **2026-10-05 05:32:46 UTC**, showing **The Siege Overhaul v4.52.8 - Saved plans, capture boundaries and richer hub pages**, exact filename `siegeoverhaul-4.52.8.jar`, Release channel, Minecraft 1.20.1 and Forge. The approved notes matched in full. It was shown as the project Main File and latest Recent File at that observation. All four required and seven optional [file-specific relationships](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9066362/dependencies) below were verified at **05:33:25 UTC**.

The supported public media download and ordinary Download click each timed out; subsequent browser observation hit a protocol-policy block. No public bytes were obtained and the block was not bypassed. The public page's rounded 1.9 MB size does not establish exact bytes or hash. The accepted CI artifact and Library-delivered JAR match the tested main SHA-256; independent public CDN-byte equivalence is not claimed.

This metadata closeout does not alter the production JAR, gameplay/version source, publish another CurseForge file or create a release tag. Metadata-PR CI may build and retain artifacts.

Required CurseForge relationships remain `recruits`, `workers`, `small-ships` and `siegeweapons`. Optional relationships remain `ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios` and `epic-knights-addon`. The JAR retains its six optional declarations: `ftbchunks`, `openpartiesandclaims`, `corpse`, `magistuarmory`, `musketmod` and `curios`. `epic-knights-addon` is an existing approved CurseForge relationship, not a new JAR dependency or separately tested integration. Sizeable Foliage remains optional.
