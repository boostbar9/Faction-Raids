# 4.52.5 — Safer enemy camp recovery

## What changes

Enemy camp scouting now tries the existing safe soil-grading plan during the initial search. The rough-ground fallback can preserve steep ground outside the camp when the camp itself, its walkable inner margin and a three-wide exit can be established safely. Claim and block protections, water limits and finite earthworks budgets still apply; this does not permit arbitrary terrain clearing.

After nearby searches fail, scouts regroup for one minute of active game time before making one bounded wider search, roughly **608–992 blocks** from the defending Siege Core. Search progress and the remaining wait survive world reloads. Preparation and assault waves stay paused while camp placement is unresolved. If no safe site is found after the bounded searches, the existing camp-less raid fallback remains available and preparation starts then.

**New raids and still-active camp searches get the stronger recovery. Already-abandoned camp searches keep their current camp-less fallback; updating does not restart those raids.** Deep oceans, protected land and other unsafe terrain can still prevent a camp. This is a targeted reliability improvement, not a guarantee of a fortified camp in every world.

Back up worlds and update the server and every client together. Network protocol **20** remains in use and rejects older-protocol clients. Minecraft **1.20.1**, Forge **47.x**, Java **17** and all four required companion mods remain required, including Recruits **1.15.2 or newer** and Villager Workers 2 **2.0.3 or newer**.

## Source and regression verification

[PR #264](https://github.com/boostbar9/Faction-Raids/pull/264) was reviewed at final head `ff4a0fff75d7f8c4255cd135560e0353a47e24ef` and merged. Its native QA checkout `37d4aac6754e19e72c2a69886e0da8c057ce5463` and merged-main commit `6eb119764108d62163cf708f54c2c8e2de250cc5` share exact source tree `7976281f3a7e8122154f9deeefe6532a96f428c4`.

- [Merged-main Build 37246027780](https://github.com/boostbar9/Faction-Raids/actions/runs/37246027780), attempt 1, passed **1,492 tests in 250 suites**, with zero failures, errors or skips.
- The complete canonical suite-name/count manifest has SHA-256 `f75569d7bbe9cc61ee95b8c642973f1ea43dd9af030a6775e1b5725efd1780b7`.
- Final-head [PR Build 37244006834](https://github.com/boostbar9/Faction-Raids/actions/runs/37244006834), attempt 1, passed the same counts. Its JAR artifact **11317959185** and regression artifact **11318257661** were reviewed but excluded from publication; the merged-main artifacts below were used.
- Independent PR/main reconciliation found identical payload bytes for all **510 classes** and all other JAR entries except the generated manifest's `Implementation-Timestamp`. ZIP timestamps also differ. All **250 XML reports** retain identical suite counts, testcase identities with their exact multiplicity, and passed outcomes; runner timing, host and diagnostic-output differences are recorded separately. The PR and main archives are not claimed to be byte-identical.

## Representative native coverage

All five final-source native gates passed on attempt 1. Their exact artifact manifests were validated by the verified-artifact publisher:

- [Native HUD QA 37244006875](https://github.com/boostbar9/Faction-Raids/actions/runs/37244006875), artifact **11318411499**: current-source client-menu framebuffers and input checks, retaining the existing HUD gate.
- [Native Building QA 37244006767](https://github.com/boostbar9/Faction-Raids/actions/runs/37244006767), artifact **11318856493**: representative native construction, ownership/payment/persistence checks and controlled cleared-wave economy cases through production `processRaid`.
- [Native Building Server QA 37244006816](https://github.com/boostbar9/Faction-Raids/actions/runs/37244006816), artifact **11318306676**: dedicated Forge GameTest/FakePlayer authority, persistence and native checks.
- [Native Camp Spawn QA 37244006885](https://github.com/boostbar9/Faction-Raids/actions/runs/37244006885), artifact **11318901127**: flat, shallow-water/forest and rocky-water/forest establishment, with native claim/core/workers/guards, protected content and terrain-restoration records. The rocky case raised its plane one block while preserving the stone.
- [Native Camp Lifecycle QA 37244006864](https://github.com/boostbar9/Faction-Raids/actions/runs/37244006864), artifact **11318449964**: two controlled deep-water worlds exercised genuine bounded failed searches, **1,200 observed ordinary cooldown ticks**, real disconnect/server close/world reopen, persisted search authority, wider-only safe-land recovery and the allowed impossible-terrain camp-less fallback. Neither case spawned a wave while scouting remained unresolved.

In the recovery case, an actual native camp existed before first-wave muster; that muster occurred with **1,180 of 3,600 preparation ticks remaining**, as permitted by existing final-third preparation behavior. It does not prove that preparation or assault had completed. In the impossible-terrain case, the first wave followed the explicit fallback by **3,620 ticks**, with zero preparation remaining. Direct independent visual review included all five camp/lifecycle framebuffers and a seven-frame HUD/building sample; complete receipt validation covered the full retained evidence.

**Coverage limits:** These are controlled native fixtures with pinned companion versions and terrain, not arbitrary generated worlds or modpacks. Lifecycle used the supported **three-minute preparation option**, not the default 12-minute timing. The wider-only island and boundary stone were deliberate fixture setup. First-wave spawn does not establish complete combat, occupation or decorative camp construction. HUD fixtures do not prove server payments; economy cases did not fight raid combat. Construction coverage is bounded and does not establish full-territory completion. Dedicated tests used FakePlayer with zero authenticated clients; real multiplayer remains unverified. The native runs do not establish separately installed production-JAR gameplay. Saved receipts and reload checks establish conservative replay/save-load behavior, not crash-atomic transactions across separate save files.

## Exact artifact and accepted upload

- Reobfuscated artifact: `siegeoverhaul-4.52.5.jar`, **1,980,914 bytes**, from merged-main Build artifact **11319097336**. Regression XML artifact: **11319032630**.
- Tested uploaded JAR SHA-256: `e18b29dbe3f0eb6c4bf68be3914c4121c08c05172865d731f99d1c7d0261b37c`.
- Exact source, protocol 20, Java 17, version, required/optional dependency declarations, reobfuscation evidence and complete regression XML were verified before upload. Finalized publisher preparation passed **239 offline publisher checks**, plus **six PR/main comparison guard tests**, and independent final review. Those guard checks are not additional gameplay tests.
- Reviewed workflow SHA-256: `9cf0963d23aa0c42695c2970d305250c94e9a33cdf476aebd0b9af4cc1c932c9`. [Verified-artifact publisher 37246924034](https://github.com/boostbar9/Faction-Raids/actions/runs/37246924034), attempt 1 at commit `26fab1da635e5c42c14cb527d12732950d5a6390`, completed successfully using these exact tested main bytes.
- CurseForge project **1364352**, **Release** channel (`releaseType: release`), accepted file **9064675**. Upload acceptance was recorded at **2026-10-05 00:17:40 UTC**. Accepted upload and public availability are separate checks; no duplicate upload was needed.

## Public verification and dependencies

At **2026-10-05 00:35:01 UTC**, the [exact public file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9064675) showed `siegeoverhaul-4.52.5.jar`, Release channel, Forge and Minecraft 1.20.1. Its visible player-facing notes matched the reviewed publisher copy below. Java 17 was verified in the tested uploaded artifact, not separately displayed by the public page.

At **2026-10-05 00:35:25 UTC**, the [public file-specific relationships](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9064675/dependencies) were checked in the live browser and matched four required projects: `recruits`, `workers`, `small-ships` and `siegeweapons`; and seven optional projects: `ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios` and `epic-knights-addon`. The last is an approved CurseForge relationship, not a new JAR dependency or evidence of an independently tested integration. The JAR retains six optional declarations: `ftbchunks`, `openpartiesandclaims`, `corpse`, `magistuarmory`, `musketmod` and `curios`.

**Public downloaded-byte verification unavailable:** the official Download route produced no browser download event after 60 seconds and reached a Chrome internal error page. The supported browser download helper also timed out after 60 seconds without returning a file path. No public JAR bytes were retrieved, so the public download's byte count and SHA-256 were **not independently verified**. The digest above identifies the tested merged-main JAR accepted by the publisher, not a separately verified public download. No browser-policy bypass or upload retry was used. Public metadata, notes and dependency relationships were verified separately from this download limitation.

## Player-facing patch notes

The following copy was supplied to the publisher and verified on the public file page:

Enemy camps now look more carefully for safe ground, including suitable sites beside rough terrain.

If the first searches fail, raiders wait one minute before making one wider search. That wait survives a world reload, and assault waves stay paused while camp placement is unresolved.

Terrain that cannot safely support a camp still uses the existing camp-less raid fallback after those bounded searches.

Back up your world before updating. Install the same version on the server and all clients, with Villager Recruits, Villager Workers 2, Small Ships and Siege Weapons.
