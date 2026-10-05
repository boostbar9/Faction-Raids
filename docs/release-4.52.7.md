# 4.52.7 — Build through Sizeable Foliage short grass

**Published as a CurseForge Release for Minecraft 1.20.1 / Forge. The official file page and dependency relationships were separately verified on 2026-10-05.**

## What changes

Builders can clear Sizeable Foliage **1.2.1**'s `sizeable_foliage:very_short_grass` from approved wall and perimeter cells as native work progresses. Free terrain reviews, manual wall checks and the protected native builder use the same single-cell plant rule. The adapter checks the exact addon version, registry ID, runtime class and safe one-cell structure; it is not a general permission to clear modded vegetation. Other addon versions continue to require manual clearance.

Paired or larger addon plants, crops, containers, fluids and existing structures remain protected. Claims, saved jobs, paid commissions, supplied materials and protocol **20** are unchanged. Sizeable Foliage remains optional, with **no new required mod**. Minecraft **1.20.1**, Forge **47.x**, Java **17**, Villager Recruits **1.15.2 or newer**, Villager Workers 2 **2.0.3 or newer**, Small Ships and Siege Weapons remain required. Back up worlds and update the server and every client together.

This release does **not** implement terrain leveling beyond the existing eight-support-block limit or broad natural-obstacle clearing. It does not resolve every rejected site or establish that the original reported player world now builds successfully. The separately reported AllTheLeaks **1.1.3** / Small Ships stable **2.0.0** crash is also outside this fix.

See the [compatibility audit](sizeable-foliage-compatibility.md) and [actual-addon native QA](native-sizeable-foliage-qa.md) for the deliberately narrow acceptance boundary.

## Source and regression verification

[PR #268](https://github.com/boostbar9/Faction-Raids/pull/268) was verified at final head `5ad06b41ecf550cc574c0c169026f43b58e9506b` and merged as main commit `09568e49aa5498ef934e933df3cfaaabb8285da1`. The actual PR/native integration checkout was `559a6b200f0b46eab504b22d9d2c240774a74193`. Final head, integration checkout and merged main have the identical source tree `b31842363c093833a3fd50a132c0cbd328f36904`.

- [Final-head PR Build 37259692657](https://github.com/boostbar9/Faction-Raids/actions/runs/37259692657), attempt **1**: JAR artifact **11324505012**, regression artifact **11324325315**.
- [Merged-main Build 37260568029](https://github.com/boostbar9/Faction-Raids/actions/runs/37260568029), attempt **1**: JAR artifact **11325015412**, regression artifact **11325005374**. The ordinary full Build and reobfuscation checks passed.
- Both builds passed **1,507 actual testcase occurrences across 251 XML suites**, with zero failures, errors or skipped tests. Complete testcase identities, passed outcomes and duplicate-label multiplicities match. Runtime output, timings and runner metadata are not treated as test identity.
- All **946 JAR entries**, including all **510 production class files**, were reconciled. All nonmanifest entry bytes match. The only payload difference is the declared `Implementation-Timestamp` in `META-INF/MANIFEST.MF`; ZIP timestamps also differ. The complete PR and main archives are not byte-identical.
- The PR JAR SHA-256 is `8793f96b615ecb970395cf02bf3ddfda48dbed6e41ddd8e3874c90689346a707`. It is retained only as PR evidence. Publication uses the exact merged-main JAR below.
- Final-head Copilot review **5409763117** and independent review comment **5987509693** were pinned by body hash; all gameplay PR review threads were resolved before publication.

## Fresh representative native coverage

The following three final-head workflows passed on attempt **1** with the exact integration checkout and reviewed source tree above. Their frozen artifacts and production validators were replayed before publication.

- [Native Building QA with actual Sizeable Foliage 37259692607](https://github.com/boostbar9/Faction-Raids/actions/runs/37259692607), artifact **11324620197**: the real loaded **1.2.1** addon class/version and original vendor checksums were checked. Very Short Grass survived free review and unpaid confirmation, was recorded at paid acceptance, was cleared by native construction, and was replaced with the expected wall block. Its supporting block outside the mutation plan was preserved; accepted/cleared receipts survived reload and exact material conservation passed. The bounded wall fixture also completed its **83-block manual wall**, preserved **27 cavity-air cells**, and retained the exact **8-emerald** debit and **992-emerald** balance.
- The addon fixture separately rejected **13** actual registry states, including large/multipart addon vegetation, wheat, a sapling, a chest, water and stone bricks, while preserving plants, Treasury, exact inventory and receipts. These are synchronous production preparation checks on explicitly placed fixture terrain, not rejected confirmation packets or naturally grown multipart layouts.
- [Native Building Server QA 37259692528](https://github.com/boostbar9/Faction-Raids/actions/runs/37259692528), artifact **11324505038**: one physical dedicated Forge GameTest passed **eight** native API, claim/inventory authority and disk/NBT checks. FakePlayer direct calls with zero authenticated clients do not establish authenticated multiplayer or a full dedicated-process restart. This run did not load Sizeable Foliage.
- [Native Camp Spawn QA 37259692531](https://github.com/boostbar9/Faction-Raids/actions/runs/37259692531), artifact **11324530433**: flat, controlled shallow-water/forest and controlled rocky-water/forest establishment passed, covering the changed shared vegetation classifier and existing native ownership/storage/protected-terrain contracts. This is three representative scenes, not arbitrary terrain or complete camp construction.

The native runtime was pinned to Minecraft **1.20.1**, Forge **47.4.16**, Workers **2.0.3**, Recruits **1.15.2**, Small Ships **2.0.0-b1.4** and Siege Weapons **0.2.5**. Actual addon gameplay coverage is confined to the bounded integrated Building fixture. Original addon release checksum verification identifies the downloaded vendor bytes; the native fixture uses ForgeGradle's remapped development runtime, not a separately installed production-JAR gameplay session.

## Historical coverage retained from 4.52.6

Unchanged-area evidence remains explicitly historical: [HUD 37249819909](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819909), [camp lifecycle 37249819787](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819787), [representative stage handoff 37249819858](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819858) and [staged unload 37249819831](https://github.com/boostbar9/Faction-Raids/actions/runs/37249819831). These retain their original source, artifact and validator hashes; they are **not fresh 4.52.7 gameplay passes**. Their detailed setup and limits remain in the [4.52.6 release record](release-4.52.6.md).

No full-territory completion, indefinite offline progress, arbitrary modpack/addon-version compatibility, authenticated dedicated addon multiplayer, complete raid combat or crash-atomic transactions across separate save files is claimed. Economy receipts use controlled cleared-wave states. Short synthetic stage partitions and client-menu fixtures remain labeled; source and API snapshots identify reviewed inputs rather than additional gameplay passes.

## Exact tested and accepted merged-main artifact

- Reobfuscated filename: `siegeoverhaul-4.52.7.jar`.
- Size: **1,983,675 bytes**.
- SHA-256: `0ffbb3b68446c0fbe6d183a20171ea338b2e0964b3116f101c629777b560b4ec`.
- JAR artifact **11325015412** and regression artifact **11325005374**, from the successful merged-main Build above.
- Canonical regression-suite manifest SHA-256: `7351eae84d410c927b6dd1c19b2b5a0b1dc0992906ced6484e1911eb0a36f303`.
- Exact version, production-only Java 17 classes, reobfuscation, protocol **20** and complete required/optional dependency declarations passed verification.

This metadata closeout does not replace the accepted production JAR, publish another CurseForge file, change gameplay/version source or create a release tag. Normal metadata-PR CI may build and retain artifacts.

## Publisher and accepted upload

[Publisher run 37261507461](https://github.com/boostbar9/Faction-Raids/actions/runs/37261507461), attempt **1**, completed successfully at publisher commit `eb4b9e31c9b163567db2336d73628d03f8089972` on the separate `publish/curseforge-v4.52.7` branch. Job **111609531116** passed every step, including exact source/artifact/regression/native checks, duplicate-upload protection and accepted-receipt recording. The publisher branch is not merged into main.

- Executed independently reviewed workflow SHA-256: `531a705272f84d923ebd666d6e082962c4f28fddc8fb4275412e3a2218b011e4`.
- Accepted receipt: `curseforge-4.52.7-receipt.json`, SHA-256 `de6f325b3f782f753ac25005384679159b0d3892532b9f577acf8e9a523154ed`.
- Receipt ZIP artifact **11325141602**, SHA-256 `3a73cf543e18625e38362009e538197e2fc5bce2ecfe524d625e0bacfe2e542e`.
- CurseForge project **1364352**, channel **Release**, file **9065976**: [accepted file URL](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9065976).

The receipt binds the file to the exact tested main JAR hash, source tree, Build/artifact IDs, all **1,507** regressions and the scoped native evidence above. It intentionally records `accepted_upload` and `public_availability: not_yet_verified` at upload time. The official page returned **404 at 2026-10-05 04:00 UTC**. Accepted upload alone does not establish later public availability. No duplicate upload was performed.

## Separate public verification and dependencies

The [official file page](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9065976) was publicly visible at **2026-10-05 04:33:48 UTC**, showing **The Siege Overhaul v4.52.7 - Sizeable Foliage short grass compatibility**, filename `siegeoverhaul-4.52.7.jar`, Release channel, Minecraft 1.20.1 and Forge. The approved player-facing notes matched. At that observation it also appeared as the project's main/latest file. The four required and seven optional relationships below were verified at **04:34:47 UTC**.

The exact accepted CI artifact and separately delivered Library artifact were SHA-256 verified against the tested main JAR. A separate public-download byte hash was **not verified**: the supported browser download timed out and subsequently rejected its URL protocol, leaving no downloaded file. No workaround or duplicate upload was attempted. Public page identity and relationships are established; independent public CDN-byte equivalence is not claimed.

Required CurseForge relationships remain `recruits`, `workers`, `small-ships` and `siegeweapons`. Optional relationships remain `ftb-chunks-forge`, `open-parties-and-claims`, `corpse`, `epic-knights-armor-and-weapons`, `ewewukeks-musket-mod`, `curios` and `epic-knights-addon`. The JAR retains its six optional declarations: `ftbchunks`, `openpartiesandclaims`, `corpse`, `magistuarmory`, `musketmod` and `curios`. `epic-knights-addon` is an approved CurseForge relationship, not a new JAR dependency or a separately tested integration. Sizeable Foliage is not added as a required dependency.

## Player-facing publisher copy

Very short grass from Sizeable Foliage 1.2.1 no longer blocks protected building plans. Builders can clear it as work progresses, while larger plants, crops, containers and water stay protected. Other addon versions will ask you to clear the grass manually.

Update Siege Overhaul on both your client and server, and back up your world before updating.
