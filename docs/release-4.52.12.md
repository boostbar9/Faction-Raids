# 4.52.12 builder supply and section handoff release record

Status: merged and accepted by CurseForge on 2026-10-13 at 07:03:41 UTC as file **9154421**. Publicly verified on 2026-10-13 at 07:10 UTC through the public CurseForge file API (status 4, approved) as a Release-channel Forge/1.20.1 file with the expected display name, filename, 2,035,639-byte size and four required/seven optional file relationships, listed first in the project file list. A public CDN download of the file matched the tested SHA-256 byte for byte. No duplicate upload should be attempted.

- [Fixes and regressions, PR #298](https://github.com/boostbar9/Faction-Raids/pull/298)
- [Accepted CurseForge file](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9154421)
- [File-specific dependencies](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9154421/dependencies)
- [Successful exact-artifact publisher](https://github.com/boostbar9/Faction-Raids/actions/runs/38360304461), attempt 1

## Player-visible scope

A commissioned builder no longer keeps its previous block target after a material runs out; when Workers 2 rebuilds its placement queue from the remaining supplies, the builder takes the next block from that fresh queue. Before a paid next perimeter section is admitted, the assigned builder walks out of that section's footprint and off the reused shovel site to checked safe ground nearby using ordinary navigation, with no teleporting, digging or plan changes; construction shows "Waiting: assigned builder is walking out of the next section" while this happens. Finite chest supplies, native supply requests, payment, accepted plans, claims and dependency requirements are unchanged. Replacement builders, stepped walls and gates remain experimental and are not included. Back up worlds and install matching 4.52.12 versions on server and clients.

## Exact source and artifact

- Reviewed PR head: `771af9eef9c37866479a59750ceb28fab9779f48`.
- Merged main: `59f3c87dc293f4cbb2672824bdd454d22e66d57f`.
- Identical reviewed/merged/native source tree: `b461460b71738154d1e4f63f3cfd73c630ba0b29`.
- [Final PR Build](https://github.com/boostbar9/Faction-Raids/actions/runs/38357457027), attempt 1; JAR artifact `11793296785`, XML artifact `11794080590`.
- [Merged-main Build](https://github.com/boostbar9/Faction-Raids/actions/runs/38359303546), attempt 1; JAR artifact `11794322894`, XML artifact `11794058962`.
- Tested and accepted file: `siegeoverhaul-4.52.12.jar`, **2,035,639 bytes**.
- Tested and accepted JAR SHA-256: `70f09a58d842fa9898fb28a12157269e92185d00e91e64833fc6d1d049915e83`.
- Both Builds: **1,573 tests across 259 suites**, zero failures, errors or skips; WallBuilderAccessTest has 37 cases and BuilderArrivalDispatchTest 13.
- Suite-name/count manifest SHA-256: `22d93af1565f7f45e95f1108a23528a7a8afaba5f21c63aa1a113708c19f26ad`.
- Automated review attestation SHA-256: `0fb4b3a639d624e999e4b5add4af828860dec6ae41b5e29a988764a843125913`. This records the Copilot and Codex review loop plus an AI-assisted final verification, not human approval.

The publisher validated exact source/run/artifact identities, unresolved review threads, SHA-pinned review evidence, both regression XML manifests, dependency metadata and JAR contents before upload. Reviewed and merged JAR entries match except the allowed build manifest difference. Only the successful merged-main JAR was uploaded; no release rebuild occurred.

## Review loop

Copilot and Codex reviewed every pushed revision. Copilot found that the clearance destination could be the marker itself (fixed in `47a7bbf`). Codex found that a builder already inside the marker box was never moved and that a partial path could be retried forever (fixed in `981e194`), then that asynchronous probes were discarded each tick and that an indoor shovel site only offered roof positions (fixed in `771af9e`). Each fix carries a named regression; the final Codex review of `771af9e` reported no new findings and every thread is resolved.

## Regression and native coverage

The two new BuilderArrivalDispatchTest cases were confirmed red on main before the production change and green after it.

[Final Native Building Server QA](https://github.com/boostbar9/Faction-Raids/actions/runs/38357457018), attempt 2, artifact `11793144380`, passed one GameTest comprising **nine contracts**. Attempt 1 failed before any Siege Overhaul code ran because the Forge GameTest server crashed inside Small Ships 2.0.0-b1.4 `SmallShipsConfig.getSchematicVersion` during its own config event; it produced no result and is not counted as evidence. Source receipt `4779ae7c7a68033109a1aa808dab09a941b04572` has the exact reviewed source tree. Runtime versions: Minecraft 1.20.1, Forge 47.4.16, Workers 2.0.3, Recruits 1.15.2, Small Ships 2.0.0-b1.4 and Siege Weapons 0.2.5. All eight direct builder-recovery destination checks passed.

[Final Native Hollow Representative Stage Handoff QA](https://github.com/boostbar9/Faction-Raids/actions/runs/38357457045), attempt 1, artifact `11793591764`, passed on the same source receipt and tree. A real client with the same companion versions completed the first 95-target section natively in about 94 seconds, the controller handed off the next paid section at the original shovel site and native placement began in it, mid-stage, between-stage and canceled restarts were verified, cancellation cleanup passed, materials were exactly 352 cobblestone and 220 oak planks, and the 64-emerald fee was the only ledger delta. The publisher pinned and re-verified this run.

These runs use a QA-only partition of at most 96 targets, a direct server commission path, one native builder and no authenticated connected players. They do not establish whole-plan completion, dedicated-client networking or arbitrary modpacks, and no scenario placed the builder inside its next footprint on purpose, so the clearance walk itself is covered by unit regressions rather than native evidence.

## Publication receipt

Publisher commit `28c2ee8` is on the separate `publish/curseforge-v4.52.12` branch and must not be merged into main. All publisher steps passed once, including duplicate-upload protection and final main/JAR recheck.

- Project: `1364352`; accepted file: `9154421`.
- Receipt artifact: `11794551490`, `curseforge-4.52.12-receipts-38360304461-1`.
- Receipt ZIP SHA-256: `ae738561f4266196717ea2589b3d49130d29b15f2dd081f3ca72df5bd49a622a`.
- Receipt includes exact source, run, artifact, checksum, test totals, review attestation and bounded native evidence.

The immutable accepted receipt deliberately records public availability as not yet verified. A later public check at 07:10 UTC used the public CurseForge website file API (the file page itself is behind a browser challenge for automation) and verified the exact file metadata and the 11 file-specific relationships; the project-level Main File flag was not readable through that API and is not claimed. Separately, `https://mediafilez.forgecdn.net/files/9154/421/siegeoverhaul-4.52.12.jar` downloaded 2,035,639 bytes with SHA-256 `70f09a58d842fa9898fb28a12157269e92185d00e91e64833fc6d1d049915e83`, identical to the tested and accepted upload, so the public bytes are verified for this release.

Required file relationships: recruits, workers, small-ships and siegeweapons. Optional: ftb-chunks-forge, open-parties-and-claims, corpse, epic-knights-armor-and-weapons, ewewukeks-musket-mod, curios and epic-knights-addon.
