# 4.52.1 — Safe camp support on rough ground

## What changes

Fallback camp scouting can raise its proposed ground plane over exposed natural rock inside the camp core, avoiding a proposal that would require cutting that rock. The rock stays untouched. The complete terrain planner still enforces claims, protected structures, supported exits, height, relief and finite earthworks limits. Ordinary natural scouting is unchanged; some worlds or locations still cannot support a safe camp.

Back up worlds and install the same build on the server and every client. Minecraft 1.20.1, Forge 47.x, Java 17 and all four required companion mods remain required. The existing Building features and protected-job compatibility requirements in [4.52.0](release-4.52.0.md) are unchanged.

## Verification and coverage

[PR #257](https://github.com/boostbar9/Faction-Raids/pull/257) was reviewed at head `675de28e3f97137a5955368913ad1885a54f68d0`. The PR CI checkout `5f2320faa6b585d8ba333ed8a6ffec22d22276d2` and merged-main commit `a520a9f9fcb8545e659969e906253135193706a6` share exact source tree `e8949fc768ec7c317ca0f5205b3d19ae99866b70`.

- [Merged-main Build 37215158716](https://github.com/boostbar9/Faction-Raids/actions/runs/37215158716), attempt 1, passed **1,379 tests in 236 suites**, with zero failures, errors or skips. The four new regressions cover a one-block rock bump, uneven mixed rock, protection/relief boundaries and an over-budget raised fill.
- [Native Camp Spawn QA 37213533266](https://github.com/boostbar9/Faction-Raids/actions/runs/37213533266) passed three controlled integrated-server worlds: natural flat ground, shallow water/forest, and rocky shallow water/forest. A real non-op Survival client used the normal start command; production scouting, timers and enemy AI remained active.
- Every scene established a native 25-chunk claim, core, two workers and five exact-role guards, with native work access and 60 ordinary ticks observed after establishment. The rocky case raised the camp plane by **one block**, retained its exposed stone, and preserved **6,147 protected cells and three chests**, including exact chest NBT. Fresh in-game captures of all three scenes were inspected.
- Runtime pins were Forge 47.4.16, Workers 2.0.3, Recruits 1.15.2, Small Ships 2.0.0-b1.4 and Siege Weapons 0.2.5. Native evidence used the development runtime; separately installed production-JAR gameplay is not claimed.

This verifies representative camp establishment, not complete decorative construction, combat, arbitrary generated terrain/modpacks or authenticated external multiplayer. Successful siege-engine placement was not demonstrated; both hostile scenes reported no clear engine slot. Audio/accessibility playback was not tested in the headless environment. Live full-search exhaustion, world-border/foreign-claim providers and unload/reload during scouting remain outside this gameplay coverage.

## Exact artifact and publication

- Reobfuscated artifact: `siegeoverhaul-4.52.1.jar`, **1,953,014 bytes**, from merged-main Build artifact **11307579912**. Regression XML artifact: **11308296588**.
- SHA-256: `bba5c0c3fe00555a47bd31e11d0f66e4664e5f182396bcdf8c9328e058e24719`.
- Independent artifact review verified version/dependencies, all regression XML and production-only contents, with no QA/vendor classes or nested JARs. PR and merged-main JARs contain the same decompressed entries, differing only in the manifest `Implementation-Timestamp`.
- [Verified-artifact publisher 37215964310](https://github.com/boostbar9/Faction-Raids/actions/runs/37215964310), attempt 1 at commit `2ea29df4bbc8f5900600f2a92c90af8a527cde47`, completed successfully using these exact tested bytes.
- CurseForge project: **1364352**; channel: **Release** (`releaseType: release`); accepted file ID: **9060392**; [file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9060392).
- Upload accepted at **2026-10-04 16:14:10 UTC**. Public availability was verified separately at **2026-10-04 16:40:25 UTC**.

## Public verification

- The [exact file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9060392) was publicly visible at **2026-10-04 16:40:25 UTC**, with version 4.52.1, filename `siegeoverhaul-4.52.1.jar`, Release channel and Forge 1.20.1. The earlier 16:14 UTC HTTP 404 was superseded by this observation.
- At **2026-10-04 16:41:16 UTC**, the [all-files listing](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/all) showed **204 files** and exactly one matching 4.52.1 row, newest and identified as Main File and Recent File.
- At **2026-10-04 16:41:16 UTC**, the [file-specific dependencies](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9060392/dependencies) matched all four required projects (`recruits`, `workers`, `small-ships`, `siegeweapons`) and six optional projects (`ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios`).
- At **2026-10-04 17:06:03 UTC**, both the 4.52.1 and 4.52.0 file-specific pages also listed `epic-knights-addon` as optional, bringing the current relationships to four required and seven optional projects. The maintainer confirmed this addition is intentional; future publishers must preserve it. This relationship is not evidence of an independently tested integration.
- The official [download page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/download/9060392) was visible at **2026-10-04 16:42:10 UTC** and displayed the [direct download link](https://www.curseforge.com/api/v1/mods/1364352/files/9060392/download).

**Public downloaded-byte verification unavailable:** the cloud-browser media download timed out, and its subsequent attempt to use the displayed direct link failed with `Invalid InterceptionId`. No file bytes were returned; the public JAR byte count and SHA-256 were not independently verified. No bot or permission block was shown. The artifact digest above identifies the tested merged-main JAR accepted by the publisher, not a separately verified public download.

## Player-facing patch notes

The public CurseForge notes for both 4.52.1 and 4.52.0 are now short and player-focused. Developer test evidence stays in the repository release records.

- The exact 4.52.1 copy was verified on [file 9060392](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9060392) at **2026-10-04 16:55:49 UTC** after [metadata run 37218434026](https://github.com/boostbar9/Faction-Raids/actions/runs/37218434026). Copy SHA-256: `91b139ce98829403601ea771083b685f6990fc09a0118bd3dcf51f8765823ac1`.
- The exact 4.52.0 copy was verified on [file 9059365](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9059365) at **2026-10-04 17:03:29 UTC** after [metadata run 37218913798](https://github.com/boostbar9/Faction-Raids/actions/runs/37218913798). Copy SHA-256: `b2c1c409e680b395e48027d722b0a7c70f571caca5909610502483a84516322d`.
- Each existing file received one metadata request containing only `fileID`, `changelog` and `changelogType`. Both API responses were ambiguous HTTP errors; the second reported HTTP 500. The visible final text establishes the result. Neither request was repeated, and no JAR was uploaded or replaced for these edits.
