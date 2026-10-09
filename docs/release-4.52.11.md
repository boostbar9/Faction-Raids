# 4.52.11 safer builder recovery release record

Status: merged and accepted by CurseForge on 2026-10-09 at 01:06:45 UTC as file **9103821**. Publicly verified on 2026-10-09 at 01:14 UTC as the Main File, Release channel, Forge/1.20.1, with the expected filename, player notes and four required/seven optional file relationships. No duplicate upload should be attempted.

- [Fix and regressions, PR #292](https://github.com/boostbar9/Faction-Raids/pull/292)
- [Accepted CurseForge file](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9103821)
- [File-specific dependencies](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9103821/dependencies)
- [Successful exact-artifact publisher](https://github.com/boostbar9/Faction-Raids/actions/runs/37868131572), attempt 1

## Player-visible scope

Emergency builder recovery rejects wither roses in every occupied destination body cell. Harmless flowers and tall vegetation remain eligible when the existing safety checks pass. Native work and sleep schedules, supplies, claims, accepted jobs, payments, inventory and world-edit restrictions are unchanged. This release does not include the experimental replacement-builder/perimeter changes. Back up worlds and install matching 4.52.11 versions on server and clients.

## Exact source and artifact

- Reviewed PR head: `751dcab465e4d6775c447defb46d16689f4955e2`.
- Merged main: `8ac69877fa3de87d2122ead0ec9f0d24b260ed18`.
- Identical reviewed/merged/native source tree: `74ac2a3908dee0142c305111a52dc791bd64407e`.
- [Final PR Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37867008068), attempt 1; JAR artifact `11589070430`, XML artifact `11588482233`.
- [Merged-main Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37867400698), attempt 1; JAR artifact `11588404357`, XML artifact `11588658721`.
- Tested and accepted file: `siegeoverhaul-4.52.11.jar`, **2,031,973 bytes**.
- Tested and accepted JAR SHA-256: `950e33f620452d5a9801b75cde4de46ae3ace40bcdb8515916fec1082c226dda`.
- Both Builds: **1,560 tests across 258 suites**, zero failures, errors or skips; all 12 BuilderGroundRecovery tests pass.
- Suite-name/count manifest SHA-256: `c3ec70762ecdf7ba3c79b919173cecc179abb6e3591ce24b70b72288b94169cf`.
- Independent automated review attestation SHA-256: `84f5a1f295bd47a2f1a99ec89e70ca3b129bce4eff48ef1fde2c2a1293763605`. This is separate AI review, not human approval.

The publisher validated exact source/run/artifact identities, unresolved review threads, SHA-pinned review evidence, both regression XML manifests, dependency metadata and JAR contents before upload. Reviewed and merged JAR entries match except the allowed build manifest difference. Only the successful merged-main JAR was uploaded; no release rebuild occurred.

## Regression and native coverage

The initial red-before-green Build `37865857366` executed the same 1,560-test suite and failed exactly the two intended wither-rose cases. Harmless vegetation controls passed before the production guard.

[Final Native Building Server QA](https://github.com/boostbar9/Faction-Raids/actions/runs/37867007996), attempt 1, artifact `11588054510`, passed one GameTest comprising **nine contracts**. Source receipt `a6e2cdb9c8ce5a3c6f143cbc05f9360d5b6d5429` has the exact reviewed source tree. Runtime versions: Minecraft 1.20.1, Forge 47.4.16, Workers 2.0.3, Recruits 1.15.2, Small Ships 2.0.0-b1.4 and Siege Weapons 0.2.5.

Eight direct recovery checks passed: air, all feet-level roses, nearest feet-level rose, synthetic upper-body rose, poppy, tall grass, sunflower and unchanged worker position. The fixture verifies retained real block states and plant survival after explicitly test-only paired-plant setup. Earlier failed native attempts `37866279564` and `37866720644` revealed fixture support/paired-plant setup errors; neither is counted as passing evidence.

These are direct destination queries with a real Workers entity and vanilla states in a loaded Forge GameTest world on the physical dedicated-server distribution. They do not establish timed stuck teleport, native AI construction, production commissioning, rendering, full server restart, arbitrary modpacks or authenticated multiplayer. The upper-body rose is a labeled synthetic body-cell fixture. Existing server authority/payment/cancellation contracts remain separately covered; injected isolated profile-cache cases do not prove a real multiplayer server profile cache.

## Publication receipt

Publisher commit `960f64ca00b6080ab5dfde85d31d6c37d78cfcea` is on the separate `publish/curseforge-v4.52.11` branch and must not be merged into main. All publisher steps passed once, including duplicate-upload protection and final main/JAR recheck.

- Project: `1364352`; accepted file: `9103821`.
- Receipt artifact: `11589510503`, `curseforge-4.52.11-receipts-37868131572-1`.
- Receipt ZIP SHA-256: `17e97299fa965e9c2ea61ce82df2ef881f1f339629c68ff7f25b82850111267e`.
- Receipt includes exact source, run, artifact, checksum, test totals, review attestation and bounded native evidence.

The immutable accepted receipt deliberately records public availability as not yet verified. A later 01:14 UTC public browser check separately verified the exact file metadata, Main File status and file-specific relationships. An attempted official public download encountered the cloud browser URL-protocol security restriction; that path was stopped. Independent public-CDN byte checksum verification remains unverified and is not claimed. The checksum above identifies the exact tested and accepted upload.

Required file relationships: recruits, workers, small-ships and siegeweapons. Optional: ftb-chunks-forge, open-parties-and-claims, corpse, epic-knights-armor-and-weapons, ewewukeks-musket-mod, curios and epic-knights-addon.
