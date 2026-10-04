# Enemy-camp spawning: bounded rough-world coverage

The 4.52.5 recovery changes and their current verification contract are documented in [camp-search-recovery.md](camp-search-recovery.md). The 4.52.0/4.52.1 audit and evidence below are historical; the single-fallback-pass description is superseded by that bounded wider-recovery design.

Audit baseline: main `79609cb1499f87ab5c1f32d1d7316ce018e102c4`, tree `1d6ebcb1c85edcdd70943459e922117bf9a6eeec` (4.52.0). The next scoped change is the exposed-rock height proposal described below. This document distinguishes source constraints, unit fixtures and actual native gameplay evidence.

## Current production path and hard boundaries

- A new raid first checks up to 128 already-loaded local sites. If none works, ordinary raid ticks perform remote scouting while preparation is paused.
- Each remote pass has at most 200 chunk-centered candidates and four minutes of active scouting. Candidates cover directions at bounded distances around the defending core (roughly 160–544 blocks before chunk centering). At most eight claim-rejected candidates are skipped per call. A candidate waits up to 60 seconds for its neighborhood, within the pass deadline. The final already-loaded candidate retains its site check.
- Natural scouting checks nine local offsets. The optional fallback checks 25 offsets in the same ready three-by-three chunk neighborhood. One fallback pass is allowed; no unbounded retry or forced loading loop is added. Search position, step, timer, fallback and abandonment state persist. Offline factions pause under the existing setting.
- Camps require the Overworld, native Recruits claiming enabled, `MaxClaimChunks >= 25`, an available claim manager, an intact five-by-five claim footprint and the native foreign-claim buffer. Foreign claim integrations, the world border, naval separation and later revalidation remain authoritative.
- Ordinary earthworks use a 19×19 core, three-block graded edge, six-block change bound and 1,024-change budget. Fallback uses a six-block graded edge, twelve-block change bound and 4,096-change budget. These plans require terrain leveling and cleanup enabled; different configuration paths do not inherit new acceptance claims.
- Shallow water is limited to six blocks over supported ground. Fallback needs a supported three-wide exit with three consecutive dry rows, found within the existing eleven-row extension. A deep ocean or isolated unsupported platform remains a rejection.
- Only recognized supported natural trees may be cleared. Bare timber, persistent/waterlogged leaves, block entities, crafted blocks, lava, out-of-height terrain and unsafe or excessive work remain blockers. Raw rock can support the camp only while unchanged; no new rock excavation is authorized.
- Planning does not mutate terrain. Application rechecks proposed changes and living occupancy, records original block states, and rolls back a failed application. Existing restoration rules retain later player edits. No payment, supplies, building-job or ownership semantics change.
- If no safe legal site is found, scouting finishes with persisted bounded rejection reasons and the raid continues without a fortified camp after a full preparation period. It must never claim unconditional camp success or destroy protected land to obtain it.

## Evidence-backed gap and scoped fix

A site with an otherwise level natural floor and a one-block exposed stone bump has a safe supported raised plane. The previous fallback height selector used the median terrain height, proposing a cut through that stone. `CampTerrain.plan` correctly rejected the forbidden excavation, but no alternate height was considered at that local site.

The fallback height proposal now includes exposed, already-accepted raw-rock supports inside the flat camp core as lower bounds. It raises only a proposal that would otherwise have to cut those supports; the complete unchanged planner still decides whether the raised fill, graded boundary and exit are safe and affordable. Natural scouting is unchanged. Tall rises and oversized fill still reject. This does not promise to solve steep rock faces, arbitrary player-made terrain, deep ocean or wholly protected search areas.

Focused regressions cover one-block and mixed two-block exposed rock, unchanged supports and saved fill originals, protected claims/containers, excessive relief and over-budget raised fill. Existing direct-plan rock-excavation rejection tests remain intact.

## Representative adversarial test plan

| Risk | Reproducible case | Required outcome | Evidence status |
|---|---|---|---|
| Exposed rocky rise | Flat soil at Y64, stone at (1,64,0); then mixed stone/andesite up to Y65 | Fallback chooses Y65/Y66 respectively, fills only air, keeps all rock and records only its own changes | New JUnit regressions; run result must be recorded separately |
| Combined wet/wooded/rocky ground | Third isolated native world in `native-camp-qa`: three water layers, existing bounded landing/tree fixture, one exposed stone rise at actual scout | Actual non-op command and production scouting establish a raised camp, native claim/core/workers/five guards; all protected cells and chest NBT remain intact | New native fixture; not considered proven until exact-head run and image review pass |
| Forest and shallow shore | Existing two native cases plus dense/tall canopy, shoreline directions and six-deep water unit fixtures | Natural case stays natural; hostile case really enters fallback; saved water/log/leaf originals and safe dry exit | 4.52.0 live run 37208347913 passed two scenes; new commit needs rerun |
| Rugged soil, hollows and shoulder slopes | Existing mound/hollow, six/twelve-block relief and continuous shoulder fixtures | Allowed cut/fill stays bounded, supported and restorable; sharp excessive relief rejects | Existing unit coverage; arbitrary generated hills remain a gameplay coverage gap |
| Small footprint or only crafted terrain | Place chest, stone bricks or bare logs at every safe footprint; surround with excessive relief | No camp mutation or protected ledger entries; next bounded site or useful exhausted-search message | Unit rejection coverage; a real full-search rejection world is a future representative case |
| World border and claims | Border slices the 25-chunk claim footprint; defender/neighbor claims cover candidates; foreign claim at earthwork/exit | Skip unsafe site without loading/mutation beyond allowed bounds; do not reduce buffer | Source and unit constraints; dedicated live border/foreign-provider cases remain gaps |
| Chunk loading/reload | Candidate 200 pending/ready at deadline, never-loaded neighborhood, save/reload near pass deadline | Finite retries; final ready candidate checked once; tickets released; deadline and diagnostics persist | Existing scouting/loading unit fixtures; real unload/reload during scouting is a gap |
| Dimensions/claim setup | Non-Overworld or claiming disabled/manager unavailable | Clear setup failure, no forced native claim or camp | Source restrictions; not a request to enable other dimensions |
| Impossible world | Deep ocean, no dry exit, all land protected, or all feasible fills exceed budget | Explicit safe failure after bounded passes, with preparation restored; no infinite freeze | Existing planner/scouting unit coverage; controlled live exhausted-search case remains a gap |

Prioritize one real combined terrain success and one targeted rejection/retry boundary when touching their code. Do not replace risk-based testing with complete decorative camp or territory-build walkthroughs. Full normal regression build and relevant safety checks remain release requirements.

## Verification record

The 4.52.0 baseline had 1,375 tests across 236 suites and the two real camp scenes; those results are historical, not a pass for this patch. Local Python source-contract/default-preparation tests passed (27). Local Gradle execution could not start because the wrapper distribution at `services.gradle.org` is unreachable from this workspace; it did not compile or run JUnit. Exact-head remote Build, native camp run, independent review and actual screenshot inspection are required before merging/releasing the gameplay change.
