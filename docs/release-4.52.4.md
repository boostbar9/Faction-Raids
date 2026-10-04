# 4.52.4 — Clearer emerald costs and earned rewards

## What changes

New manual construction commissions cost **8 emeralds** for walls or corners, **12** for stairs or archer barricades, **32** for watchtowers and **48** for gatehouses. The whole-perimeter fee remains **64**. Fees use the faction Treasury; builders, supplied materials and normal work time remain separate. Older unpaid quotes require a fresh review. Accepted jobs retain their saved geometry, materials and payment; existing server configuration is not rewritten.

Fortified Walls and Watchtower territory upgrades are unavailable while their advertised effects are missing. Previous ownership stays saved and is displayed separately from active benefits. Provisioning, Iron Levy and other hiring, loot, blessing, siege-crew and Treasury interest settings remain unchanged. This is a targeted consistency pass, not comprehensive economy play-balancing.

Eligible cleared-wave rewards are awarded before checkpoint retreat decisions, with a saved once-only receipt and retained random outcome. Reward-disabled practice raids no longer grant wave boxes or bonus barrel emeralds; barrel bonuses require a current defending player. Older raids without trustworthy reward history do not backfill the current wave's box; later waves use the new receipt.

Back up worlds and update the server and every client together. **Network protocol 20 rejects older clients.** Existing worlds and accepted building jobs remain compatible. Minecraft **1.20.1**, Forge **47.x**, Java **17** and all four required companion mods remain required, including Recruits **1.15.2 or newer** and Villager Workers 2 **2.0.3 or newer**.

## Source and regression verification

[PR #262](https://github.com/boostbar9/Faction-Raids/pull/262) was reviewed at head `aa8dd4909ac038f72ec19187e8e9ff1a60b4c27b`. Its native QA checkout `04b89be2d5eb632b93aa501940f72f5bb9c4a077` and merged-main commit `ef2a8a6ebdb32eafdb3032e90bca945e0b3f525e` share exact source tree `16098c164f37bd1c3975ad0beecee6c515f7594f`.

- [Merged-main Build 37239961129](https://github.com/boostbar9/Faction-Raids/actions/runs/37239961129), attempt 1, passed **1,472 tests in 248 suites**, with zero failures, errors or skips.
- The complete canonical suite-name/count manifest has SHA-256 `63b8a6aa1ef342f75edf6a48fbe0221f51d133d9996c19bd259453634e074c5a`.
- Final-head [PR Build 37238813352](https://github.com/boostbar9/Faction-Raids/actions/runs/37238813352), attempt 1, passed the same test/suite counts. Its JAR artifact **11315599946** and regression artifact **11315679785** were reviewed but excluded from publication; the merged-main artifacts below were used.

## Representative native coverage

The final-source native gates passed and their exact artifact manifests were replayed by the verified-artifact publisher:

- [Native HUD QA 37238813430](https://github.com/boostbar9/Faction-Raids/actions/runs/37238813430), attempt 1, artifact **11316418939**: actual client-menu framebuffer/input evidence for the updated prices and upgrade availability.
- [Native Building QA 37238813327](https://github.com/boostbar9/Faction-Raids/actions/runs/37238813327), attempt 1, artifact **11317070116**: representative native construction and controlled cleared-wave economy cases through production `processRaid`.
- [Native Building Server QA 37238813349](https://github.com/boostbar9/Faction-Raids/actions/runs/37238813349), attempt 2, artifact **11316740856**: dedicated Forge GameTest/FakePlayer checks. Attempt 2 is the successful evidence pinned by the publisher.

**Coverage limits:** HUD fixtures do not prove server payments. Controlled cleared-wave states did not include fought raid combat. Construction coverage is bounded and does not establish full-territory completion or arbitrary terrain/modpack behavior. Dedicated tests used FakePlayer with zero authenticated clients; real multiplayer remains unverified. The native runs do not establish separately installed production-JAR gameplay. Saved reward receipts cover conservative replay/save-load idempotence, not crash-atomic transactions across separate save files.

## Exact artifact and accepted upload

- Reobfuscated artifact: `siegeoverhaul-4.52.4.jar`, from merged-main Build artifact **11317720449**. Regression XML artifact: **11316718526**.
- Tested uploaded JAR SHA-256: `943396763f4c51ef147945c33d9056fce6fd9565dab2768466e19790ff0adc77`.
- Exact source, protocol 20, Java 17, version, dependency declarations, reobfuscation evidence and complete regression XML were verified before upload. Finalized publisher preparation passed **160 offline publisher tests** and independent final review.
- Reviewed workflow SHA-256: `be82484eb519a92b45aabd28fae06643b2be6b8b808f0d358cd4eb32f3c16d6e`. [Verified-artifact publisher 37240611982](https://github.com/boostbar9/Faction-Raids/actions/runs/37240611982), attempt 1 at commit `5d80db31ab428641925bb2ffec72cfda332800aa`, completed successfully using these exact tested main bytes.
- CurseForge project **1364352**, **Release** channel (`releaseType: release`), accepted file **9063876**. Upload acceptance was recorded at **2026-10-04 22:36 UTC**; public availability was verified separately below. No duplicate upload was needed.

## Public verification and dependencies

At **2026-10-04 22:47:22 UTC**, the [exact public file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9063876) showed `siegeoverhaul-4.52.4.jar`, Release channel, Forge and Minecraft 1.20.1. Its visible player-facing notes matched the reviewed publisher copy below. Java 17 was verified in the tested uploaded artifact, not separately displayed by the public page.

The public file-specific relationships were checked in the live browser and matched four required projects: `recruits`, `workers`, `small-ships` and `siegeweapons`; and seven optional projects: `ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios` and `epic-knights-addon`. The last is an approved CurseForge relationship, not a new JAR dependency or evidence of an independently tested integration. The JAR retains six optional declarations: `ftbchunks`, `openpartiesandclaims`, `corpse`, `magistuarmory`, `musketmod` and `curios`.

**Public downloaded-byte verification unavailable:** the official Download route timed out and reached a Chrome internal error page; the supported browser download helper also timed out. No public JAR bytes were retrieved, so the public download's byte count and SHA-256 were **not independently verified**. The digest above identifies the tested merged-main JAR accepted by the publisher, not a separately verified public download. No browser-policy bypass or upload retry was used. Public metadata, notes and dependency relationships were verified separately from this download limitation.

## Player-facing patch notes

The following copy was supplied to the publisher and verified on the public file page:

Back up your world, then update the server and all clients together. This release uses protocol 20, so older clients cannot join. Existing worlds and accepted building jobs remain compatible.

- New manual commissions cost 8 emeralds for walls or corners, 12 for stairs or barricades, 32 for watchtowers and 48 for gatehouses. The whole-perimeter fee stays 64. Fees come from the faction Treasury; builders, materials and work time are still separate. Older unpaid plans need a fresh review.
- Fortified Walls and Watchtower territory upgrades are unavailable while their advertised effects are missing. Previous ownership stays saved and is shown separately from active benefits. Provisioning and Iron Levy are unchanged.
- Eligible wave rewards arrive before checkpoint retreat decisions and keep a saved once-only result. Reward-disabled practice raids no longer give wave boxes or bonus barrel emeralds. Barrel bonuses require a current defending player.
- Older ongoing raids with an ambiguous reward history do not backfill the current wave’s box; later waves use the new receipt.
