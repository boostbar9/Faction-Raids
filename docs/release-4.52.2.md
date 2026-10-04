# 4.52.2 — Actionable building checks

## What changes

Manual defense previews now apply the same nearby-block safety check used by protected native builder admission. Reactive or protected neighbors are identified before commissioning instead of allowing a preview that the native guard will later refuse. This corrects a verified preview/admission disagreement without expanding the native construction allowlist.

Automatic perimeter reviews distinguish unloaded terrain, fluids, protected ground and occupied build space, with the rejected block and full coordinates when available. Failed manual commissions preserve the known safety refusal and commissioning stage in the player message and server log. Unknown exception details remain in the server log. Payment, rollback, claims, inventories and placement rules are unchanged; a refused initial start keeps the plan and takes no commission payment.

**The originally reported site-specific building blocker remains unreproduced, and its cause is unknown.** These fixes improve consistency and diagnosis; they do not establish that the original site or every failed build is now usable. Existing protections may still correctly refuse an unsafe site.

Back up worlds and install the same build on the server and every client. Minecraft 1.20.1, Forge 47.x, Java 17 and all four required companion mods remain required, including Recruits 1.15.2 or newer and Villager Workers 2.0.3 or newer. The Building features and protected-job compatibility requirements in [4.52.0](release-4.52.0.md) and camp changes in [4.52.1](release-4.52.1.md) remain in place.

## Source and regression verification

[PR #258](https://github.com/boostbar9/Faction-Raids/pull/258) was reviewed at head `0cb2127b185b2e572da907189b550ae0485570bf`. The final native QA checkout `54a3deb52da590ec722daf9bbcc5d43ebe6f0530` and merged-main commit `df646ea323c5ebb894702e765edc82d0ddeec03a` share exact source tree `b555e29e457fc09ae34435be795f47c7c2e75f1d`.

- [Merged-main Build 37220833830](https://github.com/boostbar9/Faction-Raids/actions/runs/37220833830), attempt 1, passed **1,389 tests in 237 suites**, with zero failures, errors or skips.
- Focused regressions cover negative-coordinate footing, clear grass with flowers, protected surfaces, unloaded terrain, preview/admission disagreement and unpaid handoff diagnostics.
- The complete canonical suite-name/count manifest has SHA-256 `1585fd2ad95cbcec9a6c53ec2e0ad48305effdead2697359b7252425c9528018`.
- The PR Build was [37220416880](https://github.com/boostbar9/Faction-Raids/actions/runs/37220416880), with JAR artifact **11310252641** and regression artifact **11309603952**. Those PR artifacts were excluded from publication; the exact merged-main artifacts below were used.

## Representative native coverage

[Native QA 37220416850](https://github.com/boostbar9/Faction-Raids/actions/runs/37220416850), artifact **11309188891**, passed at the matching final source tree. Its integrated-world gameplay phase used a real non-op Survival player, real Recruits claims, production plan-item packets and native Workers AI. Terrain, faction, core and stock setup were fixture-only; the builder was not teleported or given accelerated AI.

- A real manual job consumed one plan, charged exactly **90 Treasury emeralds**, and completed **83 blocks** through native AI after finite-stock exhaustion, resupply and a world restart. It retained **27 hollow AIR cells** and **nine untouched footing columns**, with exact material conservation.
- Free and insufficient-funds reviews preserved the plan, funds and reservations. Repeated confirmation did not duplicate the payment or job. Native clearing removed only the accepted single-cell dandelion while preserving its support and acceptance receipt through reload.
- Claim removal, owner permission loss, obstructed reserved headroom and an obstructed body cavity paused native mutations. The cavity obstruction survived reload and kept native queues unready; restoring the original fixture clearance resumed work without another payment.
- A real automatic perimeter commissioned once for **64 Treasury emeralds**. A same-state Survival player edit paused construction without accepted-cell writes or material/payment changes, and authenticated cancellation retired all global/stage reservations and builder associations without a refund. This does not claim full automatic-perimeter completion.
- Protected hand rebinding preserved exact inventory values before AI; mid-job and completed-job reloads retained paid state, placed blocks and cavity AIR. Completed construction links and reservations remained retired.
- Native rendering/inspection, protected mutation denial, marker save/load and compact Building/inspection controls also passed the enumerated fixture checks. Runtime pins were Forge **47.4.16**, Workers **2.0.3**, Recruits **1.15.2**, Small Ships **2.0.0-b1.4** and Siege Weapons **0.2.5**.

The native run used ForgeGradle-remapped development runtime JARs. Separately installed production-JAR gameplay is not claimed. Coverage does not establish the original reported site's cause, arbitrary terrain/modpacks, full large-perimeter completion, a dedicated network server or authenticated external multiplayer. Other limits include menu-issued Review/Take-plan commands beyond the read-only live Core HUD checks, the full claim/core/owner mutation matrix, an offline owner with a separately connected second player, competing active builders, malicious-client fuzzing, the exact 1,024/1,025-cell rendering boundary, scan-work performance and shader/resource-pack/GPU combinations.

## Exact artifact and accepted upload

- Reobfuscated artifact: `siegeoverhaul-4.52.2.jar`, **1,957,880 bytes**, from merged-main Build artifact **11310435152**. Regression XML artifact: **11309897719**.
- SHA-256: `23e7478a71d657604cf65123679359528a8ca5d66b50567f9ecfec34da3c1ec3`.
- Independent finalized-publisher review replayed exact main-artifact verification, including version, Java 17, dependencies, production-only class scope and all regression XML. The review reran **74 offline publisher tests** and found no blocking defect in the exact pinned workflow.
- Reviewed workflow SHA-256: `99dabe16812cd712ace5bae85798a58cad2ccab91d85618c9767f1b76d4b867a`. [Verified-artifact publisher 37221560347](https://github.com/boostbar9/Faction-Raids/actions/runs/37221560347), attempt 1 at commit `75671ba3ff6dff2651de459b75dbab99871aff80`, completed successfully using these exact tested main bytes.
- CurseForge project: **1364352**; channel: **Release** (`releaseType: release`); accepted file ID: **9061269**; [exact file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9061269).
- The upload was accepted at **2026-10-04 17:43:04 UTC**. Public availability was separately verified at **2026-10-04 17:56:30 UTC**. No duplicate upload was needed.

## Dependency relationships

The reviewed publisher preserves four required CurseForge projects: `recruits`, `workers`, `small-ships` and `siegeweapons`.

It also preserves seven optional CurseForge projects: `ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios` and `epic-knights-addon`. The last is an intentionally approved CurseForge relationship; it is not a new JAR dependency or evidence of an independently tested integration.

The release JAR retains its six optional declarations: `ftbchunks`, `openpartiesandclaims`, `corpse`, `magistuarmory`, `musketmod` and `curios`. All four required and seven optional public file-specific relationships were separately verified as described below.

## Public verification

- The [exact file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9061269) was publicly visible at **2026-10-04 17:56:30 UTC**. The earlier HTTP 404 observations at **2026-10-04 17:43:49 UTC** and **2026-10-04 17:49:55 UTC** were superseded by this observation.
- At **2026-10-04 17:56:52 UTC**, the file page showed filename `siegeoverhaul-4.52.2.jar`, Release channel and Forge 1.20.1, and the visible player-facing notes matched the approved copy below. Copy SHA-256: `d49bdcdfcf20ff4ee0831373bf21923334bd8b2ce0a9c4689fc325b0b51b1c40`.
- At **2026-10-04 17:57:18 UTC**, the [all-files listing](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/all) showed **205 files** and exactly one matching 4.52.2 row, newest in the listing.
- At **2026-10-04 17:57:18 UTC**, the [file-specific dependencies](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9061269/dependencies) matched all four required and seven optional projects listed above, including `epic-knights-addon` as optional.

**Public downloaded-byte verification unavailable:** at **2026-10-04 17:57:18 UTC**, the official [download page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/download/9061269) countdown led to a Chrome internal error page with the tab title `mediafilez.forgecdn.net`. The cloud browser's security policy prevented inspection of the non-HTTP(S) internal page. No downloaded file bytes were returned, and the actual underlying CDN error was not observed. The public JAR byte count and SHA-256 were not independently verified. The digest above identifies the tested merged-main JAR accepted by the publisher, not a separately verified public download. This download limitation does not negate the verified public file page, notes, listing and relationships.

## Player-facing patch notes

The following short copy was supplied to the publisher and verified on the public file page at **2026-10-04 17:56:52 UTC**. Test counts and developer evidence belong in this record.

Building previews now point out nearby blocks that prevent a builder from starting. Perimeter messages identify blocked coordinates and unloaded, wet or occupied ground. When a start is refused, the message includes the known reason. Existing building protections remain in place, and an unsuccessful initial start does not charge the commission fee.

Back up your world, then update the server and all clients together. Use Recruits 1.15.2 or newer and Villager Workers 2 version 2.0.3 or newer.
