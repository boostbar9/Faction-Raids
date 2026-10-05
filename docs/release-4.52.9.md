# 4.52.9 builder-arrival release record

Status: merged and accepted by CurseForge on 2026-10-05 as file **9066545**. Public file availability, the public dependency listing and separately downloaded public bytes are not yet verified. Keep the recommended public update feed on the newest independently verified public release until those checks finish.

- [PR #274](https://github.com/boostbar9/Faction-Raids/pull/274)
- [Accepted CurseForge file](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9066545)
- [Successful exact-artifact publisher](https://github.com/boostbar9/Faction-Raids/actions/runs/37270609849), attempt 1

## Player-visible scope

Commissioned builders wait until they are within Workers' existing horizontal working range and on verified loaded, safe footing before dispatching the next native block operation. This covers the first target still on the native queue and an already selected target. Waiting does not pop the work queue, spend supplies or place blocks directly. Native pathfinding can use a safe, closer endpoint from a budget-limited approach probe, then continue approaching.

Standing occupancy accepts air or the existing audited one-cell, empty-fluid, empty-collision plant rule, including the supported Sizeable Foliage short grass. It does not clear plants outside the accepted mutation plan. Paired plants, hazards and unsupported addon identities remain refused.

The patch preserves native vertical reach, accepted four/eight-block foundations, sleep/storage scheduling, claims and inventory safeguards. Physical vertical work access, scaffolding, cardinal gates and terrain-following walls remain separate staged work and are not included in this release.

## Exact source and artifact

- Reviewed final PR head: `35df97b25a3044a9f98d7ff95dd3f255e76a3026`.
- Merged source: `d33a3e01e89b6a6dcc080240a122048bbcfcfdb6`.
- Identical reviewed/merged tree: `2dfc4bc3ec9e06098738548fbeb038982b37b26d`.
- Immediate release baseline: 4.52.8, source `0976763349ac3bb9d5a3d8ed2647545f2ad95c67`.
- [Reviewed PR Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37266652954), attempt 1: JAR artifact `11326329638`, XML artifact `11327100434`.
- [Merged-main Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37268518578), attempt 1: JAR artifact `11326584816`, XML artifact `11327725395`.
- Released file: `siegeoverhaul-4.52.9.jar`, **2,038,840 bytes**.
- Released JAR SHA-256: `0db1135dba27bafd43c5b0a675324461bae37d55d754db8175db9125dd435cf2`.
- XML: **1,561 tests across 259 suites**, zero failures, errors or skips.
- Canonical suite digest: `76ef70835166a221670bb5cdd5127197d59b3f2acc888b6bafe23e5760647f3b`.
- Canonical testcase manifest: `9bbdfabf859bb85e9aa548be808dad7c886c578f6419c72c059500d19af9939e`.

All 969 PR/main JAR entries, including 533 production classes, were reconciled. Class/resource bytes and all passing testcase identities/multiplicities match; only the declared manifest implementation timestamp and ZIP metadata differ. Java 17, protocol 22, MATCH_VERSION, SRG reobfuscation, version and required/optional runtime dependency declarations were verified. No vendor or QA classes were included in the production JAR.

## Final gameplay evidence

The native final-head runs identify PR merge `45c0edf70ae7760648a96314021ab55eba8a9997`, with the identical source tree above.

| Gate | Successful run | Artifact | Observed coverage |
| --- | --- | --- | --- |
| Building + actual Sizeable Foliage | [37266652967](https://github.com/boostbar9/Faction-Raids/actions/runs/37266652967) | `11327155670` | Native 83-block manual wall; finite stock, resupply, one eight-emerald debit, claim/occupancy pauses, native plant clearing and reload conservation; actual addon registry standing-site checks |
| Representative stage handoff | [37266652957](https://github.com/boostbar9/Faction-Raids/actions/runs/37266652957) | `11326821917` | 95 first-section targets plus one next-section native placement, all 96 retained; one 64-emerald debit; mid-stage, between-stage and cancellation restarts |
| Configured unload/resume/cancel | [37266652927](https://github.com/boostbar9/Faction-Raids/actions/runs/37266652927) | `11326906443` | All 49 configured chunks unavailable, exact inventory across unload/reload, resumed one-to-two placements and cancellation preservation |
| Physical dedicated Server QA | [37266652876](https://github.com/boostbar9/Faction-Raids/actions/runs/37266652876) | `11327140313` | Dedicated Forge GameTest and native runtime compatibility using FakePlayer |

Downloaded artifact checksums, native receipt validators and selected completed-work screenshots were verified. Handoff uses a QA-only maximum 96-target partition, not full-perimeter completion. Unload evidence observes **100 unloaded ticks within a 600-second total scenario budget**, not ten minutes continuously unloaded. Dedicated QA has zero authenticated clients and does not establish real multiplayer playtesting.

Actual pinned Workers 2.0.3 dispatch and pathfinding tests reproduce first-target premature placement and exercise bounded partial-path endpoint handling. They do not establish arbitrary long-world journeys, steep-terrain access or all modpack combinations. Actual Sizeable Foliage standing evidence tests the production read-only predicate, not traversal through every addon plant.

HUD and camp-spawn evidence inherit the exact accepted 4.52.8 artifacts only after relevant source/provenance comparison and unchanged behavior-path review: HUD run `37265282546`/artifact `11325883534`, camp run `37265282600`/artifact `11326611602`. Camp lifecycle remains explicitly historical 4.52.6 evidence, run `37249819787`/artifact `11320914488`. These are not fresh 4.52.9 gameplay runs. Independent review confirmed the commissioned-builder wrapper is not used by the camp construction path.

## Reviews and publishing safeguards

Independent source review caught and resolved an incompatible new vertical cap and an air-only standing predicate before approval. Final Copilot review `5410280364` covers the exact final PR head and reports no findings. Independent final-source review is recorded in [PR comment 5988737208](https://github.com/boostbar9/Faction-Raids/pull/274#issuecomment-5988737208). There were no unresolved review threads at publication.

The independently reviewed one-use publisher downloads the exact merged-main tested artifact without rebuilding. It checks all 55 immutable pins and repeats live main/source/review/run/artifact verification immediately before upload. Independent publisher review verified 1,698 frozen files, 171 offline tests, 856 rejected adversarial stage mutations and the complete exact-evidence replay.

- Approved workflow SHA-256: `0c0fa621010a84ad6ba767b431909f4bc6bc1d05f65a59b8147485184db7f7e0`.
- Executed publisher commit: `ac5aab5deb85f09694d648f539205763b48f2390`, on `publish/curseforge-v4.52.9`.
- Canonical repository workflow: `.github/workflows/publish-curseforge-v4.52.9.yml`; it is not merged into main.
- Accepted-upload receipt artifact: `11328088305`, ZIP SHA-256 `77da7524fcea1c6437c5e52f1a1f3fbe653099b37c8fcab059c603da5db23ed7`.

The first publisher run, [37270240771](https://github.com/boostbar9/Faction-Raids/actions/runs/37270240771), used the local `-pinned.yml` filename as its repository path. The duplicate-upload guard expected the canonical filename and stopped with HTTP 404 before any artifact download or upload. Its CurseForge step was conclusively skipped. Independent review approved a path-only correction with identical workflow bytes; the successful canonical run then passed the unchanged guard and every live validation. There was one accepted upload and no ambiguous upload retry, guard override, release tag or rebuild.

The upload was configured with all four required and seven optional CurseForge relationships. Because upstream publishing can omit a definitively rejected relation, the actual public relationship list remains a separate closeout check. Public metadata and public-download verification must not be inferred from this accepted-upload receipt.
