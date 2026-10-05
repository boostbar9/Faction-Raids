# Cardinal perimeter gates: staged implementation

## Status

This checkpoint includes the read-only geometry compiler, `PerimeterGateLayout`, plus opt-in
version-two project persistence for exact gate observations. The current commissioning/preview
path still creates version-one projects. There is **no new live gate commissioning path**.
Version-two records explicitly fail closed before native marker creation, worker authorization,
advancement, completion receipt issuance and Treasury debit while live gate verification is
incomplete. It does not change any current paid or completed perimeter. No release/version bump
belongs to this checkpoint. Gates are not a shipped gameplay feature yet.

The intended Building presentation retains the existing Auto perimeter / Place structure /
Construction navigation, material choices, one-time Treasury fee and in-world review. The
review must eventually show the actual four entrances per connected component and all
approach observations before payment. Do not advertise usable gates in the UI before the
remaining persistence, live validation and native QA stages pass.

## Geometry contract

- Components use four-neighbor chunk connectivity. Diagonal contacts remain independent.
- Every component receives exactly one north-, east-, south- and west-facing entrance, or the
  entire result is blocked. There is no partially usable result.
- Flood-fill unclaimed chunks from outside the complete bounded territory to distinguish
  outer space from enclosed holes. Shared claim edges and hole edges are never exits.
  A disconnected island enclosed inside another component's hole cannot satisfy the
  contract. Separate components can also jointly enclose a hole and make one component's
  cardinal exit impossible. Fail clearly rather than silently use a hole boundary.
- Preserve the complete five-wide corner footprints intact. Each
  three-wide gate center is at least four cells along a run from either corner center.
- These are usable open gate passages, matching the existing manual Gatehouse semantics;
  no moving door or portcullis is introduced.
- The opening is three wide, five deep through the wall, and three high immediately below
  the existing oak deck. Omit only 18 new skin targets per gate. All 15 passage columns
  retain their deck targets. The deck, upper parapets, foundation targets and walk are unchanged.
- The inside and outside approach each begin immediately past the corresponding skin and
  extend three blocks, with three lanes and three-high headroom. Each gate describes
  45 wall-passage cells, 54 approach-air cells and 18 supporting approach-floor cells.
  Overlapping approaches within a component are deduplicated in the global result.
- An approach cannot overlap any other wall footprint or enter another claimed component.
- Prefer the cardinal-most exterior edge, then the lateral component midpoint, then stable
  X/Z ordering. A blocked preferred location tries the remaining complete candidates.
- Validation checks every lane, not just the center. The feet height is the component's
  wall base. An elevated wall over low ground is not proof of a usable approach.
- The caller must verify dry, safe footing at exactly passage Y minus one and a collision-free,
  fluid-free three-block passage. Existing planned foundations may support the in-wall
  passage; approaches require an already usable floor. No unreviewed ramps, grading,
  tree clearing, plant clearing, or outside-claim placement is implied.
- All callbacks are read-only; unavailable terrain or exceptions block the candidate.
  Results are deterministic for the same immutable inputs and observations. Checks are
  cached by exact feet position and role, with a 16,384-check hard budget. Run centerline work
  has an independent 16,384-cell budget; overlapping/repeated runs reject before terrain reads.
  Candidate geometry expands lazily instead of retaining every possible passage. Claim extent,
  runs, columns, targets and aggregate reservations retain finite existing manifest caps.

`wallOpenings` means omitted targets in a newly reviewed wall, **not a list of blocks to break**.
The class deliberately does not return an activatable modified `PerimeterBlueprint.Plan`.
A caller must not consume its wall changes while discarding its approach reservations.

## Why persistence is a separate gate

The current version-one saved format cannot represent an external approach safely:

1. `PerimeterStageLayout.atoms()` accepts clearance only in existing target-bearing wall columns.
2. `PerimeterProject.validatePlan()` requires every target/clearance reservation cell to lie in
   the original reviewed territory.
3. `NativeConstructionPolicy.problem()` requires native reservation cells to belong to the
   current faction's claim.

Do not remove these checks or put approach cells into native construction to make gates fit.

### Versioned project-level observations

`PerimeterGateContract` now implements the immutable data contract and strict codec described
below. `PerimeterProject.prepareWithGates` is explicitly opt-in; ordinary `prepare` still creates
version one. The complete contract, including its original-wall digest, is bound into the v2
manifest hash. The global project reservation includes the observations and existing ledger
subset/edited-cell behavior protects them without a native reservation change. The store's
aggregate budgets include observations before decoding their states. Native execution and new
payment remain blocked for v2 at this stage.

Keep in-wall passage cells in the normal wall-column clearance; the unchanged deck remains a
native target in each column. Add **separate immutable project-level read-only observations**
for the approaches. Never add them to native stage reservations or `AcceptedConstructionPlan.cells`.

The next stage must save:

- A geometry/manifest version, stable component IDs, cardinal facings, boundary centers and
  passage elevation; exact selected gates must survive retry/reload without recompilation.
- Exact approach-air and floor positions with original complete block states.
- Original floor observations below zero-foundation passage columns if those are not already
  protected by the existing plan's target contract.
- Deterministic ordering, immutable copies, strict coordinate/count/bounds limits, disjoint
  roles and rejection of unknown, duplicate, missing or overlapping data.
- New review, manifest and stage-contract hashes that bind all gate descriptors/observations.

The global project reservation and `ConstructionEditLedger` must include observation cells.
`ConstructionProjectLeases` already permits stage reservations to be a proper subset of that
global reservation. This protects approach edits without giving native builders permission to
mutate those cells. Air and solid-floor observations are distinct roles; do not treat a solid
floor as native air clearance. Reservation conflicts must include both roles.

### Backward compatibility

Version one must retain its exact save shape, hash prefixes, stage UUIDs, receipt identities,
payment and original geometry. Add explicit v1/v2 codecs and a per-instance format version;
new records use v2 and old records save back as v1. Never recompile old records on load.
Do not reset paid jobs, change accepted costs, auto-demolish a completed skin or silently
upgrade an existing blueprint. Any future upgrade would require a separate immutable reviewed
plan and its own explicit supported execution contract.

### Live validation points

Add complete approach checks to preparation, the reviewed fingerprint, final acceptance before
payment, active native authorization, stage handoff and final verification. Check loaded chunks
before every state read; use exact state, collision, fluid, block-entity, world-border,
authorization and reservation checks. Resolve outside-claim permission as a read-only access
observation, not construction authority. Changes, unloads or same-state ledger edits pause the
project and require the appropriate fresh review; they never trigger clearing or grading.

The full gate passage must be genuinely traversable under the new contract. The existing
native clearance helper preserves some single-cell plants; do not mistake that for literal
empty headroom or silently broaden its mutation predicate. A pre-existing wall skin at a new
gate passage must block review instead of being turned into an excavation target.

## Remaining reviewable stages

1. Geometry compiler and synthetic geometry tests, with no live commissioning callers (implemented).
2. Opt-in versioned immutable gate/observation persistence, hashes, ledger and compatibility
   tests, with explicit execution/payment barriers (implemented in this checkpoint).
3. Exact live passage/approach validation, review rendering, commissioning integration and
   explicit gates/cost/material UI, with no terrain mutation expansion.
4. Focused actual Workers native QA covering passage construction, access, protection,
   reload/payment, stage handoff, changed observations, cancellation and final verification.
5. Reviewed final Build, coordinated version/release notes and verified artifact publication.

Earthworks is a separate proposal. A future cut/fill planner may consume these exact reserved
cells, but it cannot mutate them until its own snapshots, permissions, native mining/placement,
material accounting and reviewed plan contract are proven.

## Validation

`PerimeterGateLayoutTest` covers exact N/E/S/W geometry, complete approaches, deck/corner/support
preservation, alternate safe candidates, modeled water/cliff/obstacle/unload failures, independent
component heights, concavity, holes, enclosed islands, disconnected/diagonal claims, shuffled
inputs, negative translation, immutable results, callback budgets and all 511 nonempty 3x3
claim topologies. The clear validator in geometry tests is synthetic, not proof of actual terrain
or native construction. Existing `PerimeterBlueprint` tests must continue passing unchanged.

Persistence regressions also cover a fixed v1 golden manifest/hash/stage UUID, exact paid/running
save shape, v2 role round-trip, unchanged native stage membership, mixed-format stores,
same-state observed-cell edit invalidation after ledger reload, injected lease-cell rejection and
native marker/payment refusal before any worker interaction or debit. These are contract/model
tests, not proof of native building or gameplay.

Required before production wiring/release:

- v1 paid/running save-load-save and receipt identity preservation
- v2 tamper, missing/oversize/overlap and changed gate identity rejection
- floor/headroom obstacles, fluids, unsafe surfaces and unloaded chunks reject or pause
- same-state approach edits remain detectable after save/reload
- native injected mutations of any observation cell remain rejected
- complete observations survive stage handoff/cancellation and final verification
- native builders construct the intended new openings without demolition or material loss
- actual player/unit travel through all selected entrances, with deck walking preserved
- full regression Build on the exact final source plus the affected focused native QA

Local validation at this checkpoint can be limited by the cloud environment. The checked-in
Gradle wrapper needs Gradle 8.8 from services.gradle.org and the Java 17 toolchain. Record
actual GitHub Build results in the PR; a source review or compilation is not Minecraft playtesting.
