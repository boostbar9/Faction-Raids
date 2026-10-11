# Representative native hollow stage handoff QA

This opt-in client mode bounds the staged lifecycle check to ten minutes. It is deliberately a representative lifecycle test, not evidence that a full perimeter completed or that production naturally makes sections this small.

The isolated world is `siege-native-staged-handoff` under `build/native-handoff-qa/client`. Its fixture retains the existing one-claim terrain, core, chest and native builder setup: the full 572-target perimeter, exactly 352 cobblestone and 220 oak planks, and one builder. The geometry oracle is pinned to the approved new-wall profile in `acbc66025e09152db75ab419b34fad81a3dd290e`; the patch base is `1c589dab1801699280ca1bdd5aa48540895c531e`. Integrate that production hollow change before running. The previously archived solid QA bundle remains separate and unchanged.

The independent oracle computes distance to the four edges of the actual 16×16 claim, without reading production columns: the five-wide ring has 220 columns, comprising 60 outer-skin plus 28 inner-skin columns and 132 middle columns. Four cobblestone levels on each of the 88 skin columns give 352 cobblestone; every ring column carries an oak deck block, giving 220 oak and 572 targets. The middle columns retain 132×3 = 396 AIR cavity cells below the deck. Walkway and top headroom contribute another 352 protected AIR cells, retaining the full 1,320-cell reservation. These are this fresh fixture's exact finite-stock totals, not generic controller constants.

## What the scenario actually does

1. Ask `PerimeterConstruction.prepare` for the complete production quote. Preserve the exact plan, territory and before/clearance states. Compare both full targets and full clearance against the independent skin/deck/cavity/headroom oracle.
2. Partition that whole plan losslessly with `PerimeterStageLayout.partition`. A QA-only validator additionally caps each section at 96 targets. Every candidate still passes the unchanged `BlueprintNetworkBudget.problem(TerritoryFortification.blueprint(...))` native serialization check. `PerimeterReviewFingerprint` binds this synthetic partition.
3. Call the public `NativePerimeterProjects.start` directly on the real server thread. Record this as a direct server commission, not as the normal review-menu or plan-use packet path. Assert one 64-emerald noncreative Treasury charge with the existing passive-credit-aware `NativeQaTreasury` observer.
4. Let original native goals collect the finite chest materials and place real targets. Require every cavity and walkway/headroom cell to remain exact AIR during material-conservation checks; decode each active native reservation and verify its exact clearance and no cavity mutation targets. Observe at least 40 consecutive ordinary server ticks with closed native storage/cleanup state, no active item use and at least 32 carried cobblestone, after partial first-section placement. Pause only the owner's permission using ordinary Spectator mode, save, close the world and reopen it. Compare the full authority/journal/Treasury, ledger generation, native recipe, geometry, pending cells, chest slots and loaded material inventory before any native AI resumes. Restore Survival and require new native placement.
5. Observe actual first-section completion, its receipt and retired journal entry, then the production controller's next-section admission at the original physical shovel site. Require real native placement in section two. If the ordinary waiting boundary can be observed cleanly, save/close/reopen between sections too; otherwise report that coverage as absent.
6. Use the actual client core interaction, then real `Screen.keyPressed` Tab/Enter traversal for Building → Construction → Cancel entire perimeter. Exercise Escape on the first confirmation, verify the same server menu and live paid project remain for at least 20 ordinary server ticks, then reopen cancellation and activate Yes with Tab/Enter. Traversal requires observed focus movement, is bounded to 128 Tab handlers per control and observes actual runtime modifiers without changing key state or setting focus. Vanilla ConfirmScreen handles Escape by invoking its callback with false; vanilla Tab traversal reads runtime Shift state, so either actual traversal direction is accepted. This verifies screen handlers, not physical OS keyboard input. Require the authenticated packet to produce a compact CANCELED receipt, removed markers/reservations and detached builder. Save, close and reopen again; prove that receipt, cleanup, partial geometry and conserved remaining materials survive. Never call the whole plan complete.

The QA does not place target blocks, refill materials, teleport the tested builder, force chunk tickets, change native speeds, manually call native goals, or repair/drain cleanup journals. Initial terrain/setup writes and the initial worker AI hold are fixture preparation only. Permission pauses change only the owner; the worker remains under the real runtime. A native lifecycle block fails with diagnostics instead of being repaired by QA.

## Running

Integrate the separately reviewed Small Ships defaults helper before using this mode. From a fresh checkout, run the helper before any task or command creates the client directory:

```sh
python3 scripts/prepare-native-client-defaults.py --mode staged-handoff --smallships-version 2.0.0-b1.4
```

Then run `prepareNativeQaClient` with `-PnativeQa=true -PnativeQaMode=staged-handoff` under a separate 30-minute process cap. This task depends on `nativeQaClasses prepareRunClient` and then resolves the exact ForgeGradle run task's Minecraft/runtime artifact collections and source-set runtime classpaths without launching Minecraft. `prepareRunClient` alone does not fetch/remap runtime-only companions such as Small Ships and Siege Weapons. Launch `--offline runClient` with the same properties and Gradle cache under a hard ten-minute process-tree cap. The workflow uses GNU timeout with KILL at 600 seconds for the owned process group, with no earlier TERM that could let its wrapper exit before descendant cleanup; the client independently stops at 600 seconds. Compilation and dependency preparation do not consume the client process budget. There is no automatic rerun, reused world, or timeout extension.

The `Native Hollow Representative Stage Handoff QA` workflow supports manual dispatch and the `native-handoff-qa` PR label. It prepares pinned unchanged Small Ships 2.0.0-b1.4 defaults before client creation and uses the existing four real companion mods. Evidence is in `build/native-handoff-qa/evidence`; worlds and vendor JARs are never uploaded.

## Reading the result

`result.json` explicitly identifies the synthetic partition and direct server commission. It records all 572 targets through the full authority at commission and handoff, exact stage IDs/digests, actual native area joins and physical marker positions, restart snapshots, cancellation receipt, stock totals, passive Treasury accounting and four raw framebuffers. `verify-native-staged-handoff.py` verifies these alongside the exact loaded native bytecode audit. A passed result requires the real partial first-stage restart and canceled restart; it separately reports `betweenStageRestartVerified`.

This mode does not prove full-plan completion, production partition sizing, normal commission-menu/use-packet behavior, actual chunk unload, dedicated-client networking, other palettes/terrain, distant storage or arbitrary graphics configurations. Existing accepted solid records are neither recompiled nor reinterpreted by this QA; their unchanged saved production geometry remains authoritative. This fresh-world mode does not claim legacy-record compatibility testing. Keep those coverage claims separate. Source compilation or a passing evidence-parser syntax check is not live Minecraft acceptance.

## Separate shared-crew acceptance option

`-PnativeSharedCrewQa=true` is a default-off option for `staged-handoff` only. It is
mutually exclusive with `-PnativeBuilderReplacementQa=true`: replacement remains
its original single-builder/new-hire scenario. For same-repository pull requests,
use the `native-shared-crew-qa` label with the exact base
`release/4.53.0-alpha.3`. The workflow retains read-only repository permissions and
uses no additional secrets. Manual dispatch offers a separate `shared_crew`
boolean, default false. Both compilation preparation and the offline client launch
receive the same option; the ten-minute client and nine-minute construction caps
remain unchanged.

This is an explicitly synthetic, representative **legacy hollow fixture**, not a
new gated-plan geometry test. It retains the original independent 572-target
oracle and uses the same lossless QA-only 192-target section cap as replacement
acceptance. It creates exactly two native builders owned by the real survival
owner, plus **one** original chest containing 352 cobblestone and 220 oak planks.
The second builder receives tools and food only. Both fixture AI holds end at the
paid direct-server commission; production discovers and enlists the helper.
Nothing adds construction stock, sets target blocks, invokes native goals,
accelerates AI, teleports a working builder, or manufactures completion receipts.

The required evidence, all stored in the existing `result.json`, is:

- Exact coordinator/helper/owner/project/area identities and the unchanged paid
  manifest; one 64-emerald commission using the existing Treasury observer.
- Distinct real placements by both builders on that shared project. A read-only
  Forge `LivingTickEvent` observation starts before a tested builder's actual AI
  and ends before the next living entity's tick or at ordinary server END. Each
  credited target must change to the exact accepted block, consume the same
  actor's matching material, and appear in the production guard's `Completed`
  receipt. Events include actor, area, stage, game time, packed and readable
  target positions, material, per-actor stock and finite-chest stock. Final unique
  event targets must equal all retained world targets, with no duplicate credit.
- Native finite-chest supply withdrawals by both builders, with matching chest
  and per-builder stock deltas. Conservation includes both actors and loose items.
- Actual partial-stage interruption through the owner's ordinary Spectator
  permission pause, save/close/reopen, and exact pre-AI loaded construction cargo
  for **both** builders. Both must resume actual distinct placement afterward.
- Actual lethal damage to the original coordinator only after its construction
  cargo is empty and native inventory cleanup is closed. The already-enlisted
  helper survives; no third builder is spawned. Production must promote it and
  resume real placement within one minute, preserving the original paid header,
  manifest and materials without another commission.
- The existing native first-section completion, second-section placement,
  authenticated menu cancellation and canceled reload. Between-section reload
  remains separately reported when its clean ordinary boundary is observed.
- Exact checked-out source commit/tree, loaded companion hashes, the unchanged
  native-bytecode audit, and original framebuffer/source evidence files. The
  verifier requires the recorded source identities to match those evidence files.

`verify-native-staged-handoff.py` requires these additional contracts when
`SHARED_CREW_QA=true`. Its independent coordinate/material oracle rejects targets
outside the fixture's approved hollow skin/deck, duplicate credits, material
mismatches, a missing contributor, unmatched withdrawals, lost cargo, a missing
post-reload contributor, changed source identity, a new replacement hire, or an
extra commission. Python contract tests use explicitly synthetic records to test
acceptance and rejection; they do not constitute native gameplay evidence.

Sleep is deliberately **not exercised**: this world stays at noon, and the test
does not force a native sleep state. The permission-pause/reload is a shared
interruption test, not evidence of individual dismissal, sleep, unloaded-chunk
succession or every helper-rejoin path. The original limitations still apply,
including no whole-perimeter completion, normal commission packet, production
partition-sizing, gated-geometry, distant-storage or dedicated-client-networking
claim. A checked-in harness or passing Python tests are not an actual client
pass; the exact integrated source must compile and produce verified runtime
artifacts before shared-crew acceptance can be claimed.
