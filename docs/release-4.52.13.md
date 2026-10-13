# 4.52.13 clearer building and faction setup feedback release record

Status: merged and accepted by CurseForge on 2026-10-13 at 12:04:34 UTC as file **9156272**. Publicly verified on 2026-10-13 at 12:07 UTC through the public CurseForge file API (status 4, approved) as a Release-channel Forge/1.20.1 file with the expected filename, 2,037,725-byte size and four required/seven optional file relationships, listed first in the project file list. A public CDN download of the file matched the tested SHA-256 byte for byte. No duplicate upload should be attempted.

- [Fixes and regressions, PR #300](https://github.com/boostbar9/Faction-Raids/pull/300)
- [Accepted CurseForge file](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9156272)
- [File-specific dependencies](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9156272/dependencies)
- [Successful exact-artifact publisher](https://github.com/boostbar9/Faction-Raids/actions/runs/38394612675), attempt 1

## Player-visible scope

When a manual defense plan is refused for claim reasons, the plan review names the chunk the plan reaches (chunk coordinates and block ranges) and says whether that chunk is not part of the claim holding the Siege Core, belongs to another faction, or is a different claim of the player's own faction, replacing the single sentence "The whole structure must be inside your core's claim and allow building". Server spawn protection and the world border are reported separately for the exact block. A claim mod that failed to answer (FTB Chunks, Open Parties and Claims, or a Villager Recruits lookup that failed mid-session) is reported as "A claim mod failed to answer, so land ownership is unknown and building is paused. An operator can run /siegeoverhaul compat and check the server log." instead of as a rival claim, unclaimed land, lost territory or a missing dependency, and `/siegeoverhaul compat` shows `Recruits: ERROR (placement blocked)` in that state. Automatic perimeters receive the same structure and continue to use the complete faction territory. `/siegeoverhaul help` adds a line explaining that factions and claims are managed in Villager Recruits through its faction screen (U by default) and claim map (M by default) and that there is no `/faction` command. Placement rules, prices, claim requirements and dependency requirements are unchanged. Back up worlds and install matching 4.52.13 versions on server and clients.

## Exact source and artifact

- Reviewed PR head: `4d099e47f5e8f755f1dc672c38bb8dd82cf51c89`.
- Merged main: `c961438f1954f4883c2b51d0cc65fe52081ef618`.
- Identical reviewed/merged/native source tree: `cec22f58ca062a9883b8e4f43a0c3f3c71f483fa`.
- [Final PR Build](https://github.com/boostbar9/Faction-Raids/actions/runs/38392536486), attempt 1; JAR artifact `11810531306`, XML artifact `11811116038`.
- [Merged-main Build](https://github.com/boostbar9/Faction-Raids/actions/runs/38393768507), attempt 1; JAR artifact `11811342440`, XML artifact `11810633199`.
- Tested and accepted file: `siegeoverhaul-4.52.13.jar`, **2,037,725 bytes**.
- Tested and accepted JAR SHA-256: `73f6edb47eb06618acb06976c6ece6872ca4b4b3a5d0d6c05a54721a67a94a41`.
- Both Builds: **1,582 tests across 259 suites**, zero failures, errors or skips; DefenseStructuresTest has 26 cases, PerimeterConstructionTest 15 and ClaimBridgeFailureTest 10.
- Suite-name/count manifest SHA-256: `4822ba058aadd8e28b1235761d2effdd2e20ad3a2ad81ffc0f9c34c239c5bfaf`.
- Automated review attestation SHA-256: `8a5d2be837dd8a7574369d70d782154828232f69a4c0e4b3a98d85482d98d68a`. This records the Copilot and Codex review loop plus an AI-assisted final verification, not human approval.

The publisher validated exact source/run/artifact identities, unresolved review threads, SHA-pinned review evidence, both regression XML manifests, dependency metadata and JAR contents before upload. Reviewed and merged JAR entries match except the allowed build manifest difference. Only the successful merged-main JAR was uploaded; no release rebuild occurred.

## Review loop

Copilot and Codex reviewed every pushed revision. Copilot and Codex both found that permitted chunks were never cached because `computeIfAbsent` ignored null results, Codex found that a broken FTB Chunks or Open Parties provider was shown as a foreign claim, and both found that the README and CHANGELOG applied the manual-plan rule to automatic perimeters (fixed in `6ea5a4a`). Codex then found that a provider could fail during the lookup itself and that a same-faction sibling claim was told to claim land it already owned (fixed in `bbec2e0`), that a Recruits lookup failing mid-session was not counted as a broken provider (fixed in `750700e`), and that the Recruits check was unreachable before the dependency gate and inside claim resolution while `/siegeoverhaul compat` showed Recruits as off (fixed in `4d099e4`). Each fix carries a named regression; the final Codex review of `4d099e4` reported no new findings, the Codex security review found no issues, and every thread is resolved.

## Regression and native coverage

[Final Native Building Server QA](https://github.com/boostbar9/Faction-Raids/actions/runs/38392536636), attempt 1, artifact `11811165992`, passed one GameTest comprising **nine contracts**. Source receipt `d47bd75dbd9655e33a6ae0f8075878614441db19` has the exact reviewed source tree. Runtime versions: Minecraft 1.20.1, Forge 47.4.16, Siege Overhaul 4.52.13, Workers 2.0.3, Recruits 1.15.2, Small Ships 2.0.0-b1.4 and Siege Weapons 0.2.5. All eight direct builder-recovery destination checks passed.

[Final Native Hollow Representative Stage Handoff QA](https://github.com/boostbar9/Faction-Raids/actions/runs/38392536396), attempt 1, artifact `11810481453`, passed on the same source receipt and tree. A real client with the same companion versions completed the first 95-target section natively in about 97 seconds, native placement began in the next section, mid-stage, between-stage and canceled restarts were verified, cancellation cleanup passed, materials were exactly 352 cobblestone and 220 oak planks, and the 64-emerald fee was the only ledger delta. The publisher pinned and re-verified this run.

These runs use a QA-only partition of at most 96 targets, a direct server commission path, one native builder and no authenticated connected players. They do not establish whole-plan completion, dedicated-client networking or arbitrary modpacks, and they do not exercise the new rejection texts, which are covered by unit regressions with mocked levels and claim bridges.

## Publication receipt

Publisher commit `eda9197` is on the separate `publish/curseforge-v4.52.13` branch and must not be merged into main. All publisher steps passed once, including duplicate-upload protection and final main/JAR recheck.

- Project: `1364352`; accepted file: `9156272`.
- Receipt artifact: `11811585078`, `curseforge-4.52.13-receipts-38394612675-1`.
- Receipt ZIP SHA-256: `f17f638078e31a1c01fa8920f7e4731016b4cca3cd8b6f5dd8b72644656f6e02`.
- Receipt includes exact source, run, artifact, checksum, test totals, review attestation and bounded native evidence.

The immutable accepted receipt deliberately records public availability as not yet verified. A later public check at 12:07 UTC used the public CurseForge website file API (the file page itself is behind a browser challenge for automation) and verified the exact file metadata and the 11 file-specific relationships; the project-level Main File flag was not readable through that API and is not claimed. Separately, `https://mediafilez.forgecdn.net/files/9156/272/siegeoverhaul-4.52.13.jar` downloaded 2,037,725 bytes with SHA-256 `73f6edb47eb06618acb06976c6ece6872ca4b4b3a5d0d6c05a54721a67a94a41`, identical to the tested and accepted upload, so the public bytes are verified for this release.

Required file relationships: recruits, workers, small-ships and siegeweapons. Optional: ftb-chunks-forge, open-parties-and-claims, corpse, epic-knights-armor-and-weapons, ewewukeks-musket-mod, curios and epic-knights-addon.
