# 4.52.6 — More reliable builder reviews and work breaks

**Published as a CurseForge Release for Minecraft 1.20.1 / Forge. Exact file metadata, player notes and file-specific dependencies were verified publicly. Public download bytes and their SHA-256 were not independently verified; the limitation is recorded below.**

## What changes

Siege Overhaul's ground recovery no longer moves a commissioned builder while native Workers behavior is sleeping, handling storage or using an item. Native work schedules and supply movement stay in charge, and the recovery stuck timer restarts after the break. This is a targeted recovery correction; it does not make workers build while sleeping or obtain materials automatically.

Free manual and automatic-perimeter reviews now use the same supported small-plant clearance and reserved-headroom rules as native acceptance. The complete perimeter's placement neighbors are checked before creating a native section. Paired plants that need manual clearance report their block and coordinates, while supported single-cell plants still work normally. Manual plans also check building permissions throughout their reserved headroom. Existing structures, protected blocks, claims and inventories remain protected.

Existing paid jobs, supplied materials and construction fees are preserved. Manual commissions remain **8 emeralds** for walls or corners, **12** for stairs or archer barricades, **32** for watchtowers and **48** for gatehouses; the whole-perimeter fee remains **64**. Builders, supplies and normal work time remain separate requirements. This release does not change protocol **20**, saved payment/reward semantics or existing server configuration.

Back up worlds and update the server and every client together. Minecraft **1.20.1**, Forge **47.x**, Java **17**, Villager Recruits **1.15.2 or newer**, Villager Workers 2 **2.0.3 or newer**, Small Ships and Siege Weapons remain required. **Small Ships is still mandatory.**

The separately reported **AllTheLeaks 1.1.3 / Small Ships stable 2.0.0** crash is an external compatibility issue and is **not fixed by 4.52.6**. The original builder problem in the reported player world has not been reproduced or retried, so its cause and remediation remain unverified. The evidence below establishes the bounded fixes and checks described here, not a diagnosis of that world.

## Source and regression verification

[PR #266](https://github.com/boostbar9/Faction-Raids/pull/266) was verified at final head `547dafd3e898282df67f1134f41cf9a6a57e3a0a` and merged as main commit `978643da95e773170789ee02bf956c74f81d99f7`. The base was `19b6e8a5d5f66d289c4a724ee1ac1db342ffb4f3`; the actual PR integration checkout was `0bc7715ecc72dbbf2bd01810fc247592a9658d22`. Final head, integration checkout and merged main have the identical source tree `a3e44c7478612b7f8b4de9112c664005a4bda78e`.

- [Final-head PR Build 37249819910](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819910), attempt 1: JAR artifact **11319929875**, regression artifact **11320761847**.
- [Merged-main Build 37252017691](https://github.com/boostbar9/Faction-Raids/actions/runs/37252017691), attempt 1: JAR artifact **11321366361**, regression artifact **11321446147**. Job **111581425691** records the exact main checkout, ordinary `./gradlew clean build`, executed `reobfJar` and success.
- Both builds passed **1,503 actual testcase occurrences in 250 XML suites**, with zero failures, errors or skipped tests. All suite counters agree with testcase elements. Complete testcase identities and their exact multiplicities match between PR and main; timing, runtime output and runner metadata are not treated as identity.
- All **946 JAR entry names** match. All **945 nonmanifest entries** match byte-for-byte. The only changed entry is `META-INF/MANIFEST.MF`, and its only content change is `Implementation-Timestamp`; ZIP timestamps also differ. The complete PR and main JAR archives are not byte-identical.
- The PR JAR is **1,981,647 bytes**, SHA-256 `270aece070f78c4d3ef4757c246e381b83847531a4eed7a270dd0b2a9bb0ea4f`. It is retained as PR evidence and excluded from publication. Only the tested merged-main JAR below was accepted for this release.

## Representative native coverage

All eight final PR workflows succeeded on attempt 1: Build plus the following seven native gates. Every native artifact records integration checkout `0bc7715ecc72dbbf2bd01810fc247592a9658d22` and the identical final source tree. All nine PR artifact ZIPs and both main artifact ZIPs were checked against their GitHub byte sizes and SHA-256 digests. Checked-in workflow/script validators were replayed on frozen exact-head inputs; Building's economy validator was also replayed.

- [Native Building QA 37249819808](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819808), artifact **11320057727**: all six strict admission-review receipts passed: protected perimeter neighbor rejection, perimeter and manual paired-plant rejection, exact inventory/Treasury preservation, no project/worker receipt change, and restored fixture terrain. Actual native completion and restart of an **83-block manual wall**, **27 preserved cavity-air cells**, exact supply accounting and bounded economy receipts also passed. The new obstacle cases are free-review checks, not new full-perimeter completion tests.
- [Native Building Server QA 37249819830](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819830), artifact **11320262178**: one physical dedicated Forge GameTest passed eight production API, claim/inventory authority, native and disk/NBT checks. It uses FakePlayer direct calls with zero authenticated clients, not an authenticated multiplayer session or full dedicated-process restart.
- [Native HUD QA 37249819909](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819909), artifact **11320801742**: **106** actual framebuffer PNGs and **269/269** bounded interaction steps validated. These are explicitly labeled client-menu samples; they do not establish payments or worker AI.
- [Native Camp Spawn QA 37249819875](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819875), artifact **11320224572**: natural-flat, controlled shallow-water/forest and controlled rocky-water/forest establishment passed, including native job ownership, storage and protected-terrain contracts.
- [Native Camp Lifecycle QA 37249819787](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819787), artifact **11320914488**: both no-safe-land reload/fallback and recovery-only-island scenarios passed with **1,200 observed ordinary cooldown ticks** and unchanged production search/recovery timers. Save/close/reopen and saved search authority were verified in the no-safe-land scenario. A recovery first-wave muster does not establish completed preparation or combat.
- [Native Hollow Representative Stage Handoff QA 37249819858](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819858), artifact **11320885967**: genuine native placement through a deliberately synthetic **maximum-96-target stage partition**. The first stage had **95 targets**, native placement in the next stage was observed, and **98 blocks** remained at cancellation. Mid-stage, between-stage and canceled restart checks passed. Whole-plan completion was not attempted.
- [Native Staged Unload QA 37249819831](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819831), artifact **11320885883**: with `RecruitsChunkLoading=false`, all **49 chunks** were unavailable for **100 ticks**; native inventory reload/conservation passed, placement resumed from **3 to 4 blocks**, and authenticated cancellation/safe cleanup passed. This retains the fixture's accepted solid manifest. Full completion and remote unloaded-menu rendering are not claimed.

Independent visual review inspected **12** actual final-head PNGs: three Building, two HUD, one Handoff, one Unload, three Camp Spawn and two Lifecycle frames. Sampled frames rendered coherently and nonblank. The Unload frame does not contain a legible pause menu; runtime receipts establish pause/resume. The Building free-review frame was captured after fixture-terrain restoration and does not show each rejection. Hidden cavity counts, authority and lifecycle transitions come from receipts, not static screenshots.

**Coverage limits:** The native runtime was pinned to Minecraft **1.20.1**, Forge **47.4.16**, Workers **2.0.3**, Recruits **1.15.2**, Small Ships **2.0.0-b1.4** and Siege Weapons **0.2.5**. It does not cover Small Ships stable **2.0.0**, every modpack, arbitrary terrain or the reported player world. Controlled terrain, client-menu fixtures and synthetic stage partitions are explicitly bounded. The evidence does not establish full-territory completion, indefinite offline progress, authenticated dedicated multiplayer, separately installed production-JAR gameplay, complete raid combat or crash-atomic transactions across separate save files. Economy receipts use controlled cleared-wave cases rather than fought raids. Save/reload checkpoints are gameplay evidence; source-tree and API snapshots identify reviewed inputs and are not additional gameplay passes.

## Exact tested and accepted merged-main artifact

- Reobfuscated filename: `siegeoverhaul-4.52.6.jar`.
- Size: **1,981,648 bytes**.
- SHA-256: `c01058700466c3892cf9890ce7386063add2b53f511832c935aa9c6a0f4955a3`.
- Merged-main JAR ZIP artifact **11321366361**: SHA-256 `c6cbbc5d71e836faf21aa231109f332d76434c1b1927f28701cc024d800c6f1b`.
- Merged-main regression ZIP artifact **11321446147**: SHA-256 `9dc648bea9763c3ebf3549e4d92152530bce2c1e214ae9f072302b9a3ee126ef`.
- Version, Java 17/reobfuscation evidence, protocol 20 and complete required/optional dependency declarations were verified. The payload reconciliation above ties these main bytes to the tested final PR source. This metadata closeout does not publish another CurseForge release or replace the accepted production JAR; normal CI may build and retain artifacts.

## Publisher and accepted upload

[Publisher run 37253976928](https://github.com/boostbar9/Faction-Raids/actions/runs/37253976928), attempt **1**, completed successfully at publisher commit `de8fee0420719e287fd54e9dec7a4227a1230d1b` on the separate `publish/curseforge-v4.52.6` branch. Job **111587095104** passed every step, including exact-byte/source/regression/native validation, duplicate-upload protection, publication and accepted-receipt recording. This branch is not a gameplay-source change and is not merged into main.

- Executed publisher workflow SHA-256: `6ee4beae4c2825c7d9c461b72fe89623c3d6976b940e09374306abb968686623`; Git blob: `b6e8d09995b36a645e1211d766fc0bbb3b52c00c`. Its bytes match the final independently reviewed frozen workflow.
- Accepted receipt: `curseforge-4.52.6-receipt.json` recorded in the publisher job log; retained receipt SHA-256 `513ecb483e32da6560039224dd87e48609670a1e875d3a59addbef9249989508`.
- CurseForge project **1364352**; accepted channel **Release**; file **9065413**.
- The upload-success log is timestamped **2026-10-05 02:05:54 UTC**. The receipt ties file 9065413 to the exact tested merged-main JAR SHA-256 `c01058700466c3892cf9890ce7386063add2b53f511832c935aa9c6a0f4955a3`, source tree, Build/artifact IDs, all 1,503 tests and all seven native gates.
- Accepted file URL: [4.52.6 file 9065413](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9065413). An accepted URL does not itself prove that the file is publicly available.

The receipt intentionally reports `accepted_upload` and `public_availability: not_yet_verified`; it describes acceptance at upload time. The official file page returned 404 during the initial **2026-10-05 02:08 UTC** check, then became publicly visible at **02:11:30 UTC**. The separate public checks below establish the later public state. No duplicate upload was performed.

## Separate public verification and dependencies

Live official-page observations recorded at **2026-10-05 02:13:45 UTC** confirmed [file 9065413](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9065413) as `siegeoverhaul-4.52.6.jar`, **Release**, **Minecraft 1.20.1**, **Forge**, with the expected title. It was visible as the main/latest file. The exact three-paragraph player notes match the final reviewed publisher copy below.

The [file-specific dependency page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9065413/dependencies) confirmed all four required and all seven optional relationships listed below, including `epic-knights-addon`.

**Public byte-verification limitation:** The visible [official Download link](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/download/9065413) opened the download page and five-second countdown. The supported browser download event timed out after 20 seconds, and the next state was rejected by the browser URL policy because its resulting protocol was outside http/https. No local public JAR bytes were retrieved; no bypass or repeated download was attempted. The public byte count and public-download SHA-256 remain unverified. The tested/accepted main-JAR digest above is **not an independently verified public-download hash**.

Required CurseForge projects must remain `recruits`, `workers`, `small-ships` and `siegeweapons`. Optional projects must remain `ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios` and `epic-knights-addon`. The last is an approved CurseForge relationship, not a new JAR dependency or an independently tested integration. The JAR retains six optional declarations: `ftbchunks`, `openpartiesandclaims`, `corpse`, `magistuarmory`, `musketmod` and `curios`.

The update feed and current-release guide select **4.52.6** as latest and recommended after the separate accepted/public metadata, notes and relationship checks. This metadata closeout preserves the public-byte retrieval limitation. This metadata closeout does not publish another CurseForge release or replace the accepted production JAR; normal CI may build and retain artifacts.

## Player-facing publisher copy — publicly verified

The following exact copy was submitted by the final reviewed publisher and matched the live official file notes. The wording describes recovery without implying that native supply movement stops.

Builder recovery no longer interrupts sleep, supply runs or item use, and its stuck timer restarts when work resumes.

Free building reviews catch nearby protected blocks and paired plants before you commit. Manual and perimeter reviews now follow the same plant-clearance and headroom rules as native construction, with clearer block locations when something needs moving.

Existing paid jobs, supplied materials and construction fees are preserved. Back up your world and update the server and all clients together.
