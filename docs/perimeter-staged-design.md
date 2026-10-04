# Proposal: one complete perimeter, several native build areas

Status: design only. No stage scheduler, payment API, reservation delegation or compact native synchronization is implemented by this document. Keep the reviewed union/index/network-budget changes separate. The production architectural choice is staged native areas using the original Workers blueprint serializer.

## Contract

A commission is one reviewed complete same-faction perimeter, one hired builder and one flat 64-emerald Treasury fee. The global compiler runs once. Staging partitions that exact approved target and clearance map; it must never compile walls independently for each chunk. No internal border, gate, new palette, direct world placement, partial success, size-based fee or per-stage fee is introduced. The 64-emerald price applies only to new commissions; existing paid jobs and receipts remain unchanged. Blocks are supplied separately and claim-purchase costs are unchanged.

Current global planning bounds remain explicit: 4,096 owned chunks, 32,768 solid cells, 65,536 structural/headroom reservation cells, and the existing 256-block horizontal planning span. These are finite safety bounds, not a promise that any arbitrary territory fits. All terrain must be available for the initial exact review under the current surface-reading contract. Staging then allows later sections to unload without requiring the worker to load them. Extending initial review to terrain not currently loaded is a separate design; do not infer elevations or force-load it.

Each active native area must independently pass the unchanged actual `COMPOUND_TAG` serializer's 2,097,152-byte NBT accounting quota, native scan-volume bound, tracking envelope and existing permission/material protections. Only one stage is assigned to the builder at once. Storage, tools, native travel and work hours remain real.

## Partition, do not rebuild

1. Compile the entire union, global deck elevations, foundations, corners, targets and headroom exactly as now.
2. Form indivisible column atoms from each global column's solid cells and its clearance cells. An atom belongs to precisely one stage. This keeps an accepted column's foundations and walking headroom together.
3. Bucket atoms deterministically by chunk and fixed coordinate order. Greedily merge nearby buckets while a candidate stage passes its actual native serialization roundtrip, 256-block local span, bounded scan volume and reservation limit. Splitting a bucket is permitted only on whole-column boundaries, never by silently dropping cells.
4. If even one atom cannot pass, reject the entire unpaid commission. If any global plan cell is absent, duplicated or changed by partitioning, reject it.
5. Freeze the ordered stages in the reviewed fingerprint. Store stable stage IDs up front. Require the union of stage solids to equal the global solid map exactly, stage clearances to cover the global clearance exactly, and stage reservation sets to be disjoint with their union equal to the global reservation.

The partition algorithm's validation includes negative coordinates, holes, disconnected components, uneven foundations and shared chunk borders. Stage bounds may contain air, but that air is never implicitly reserved or authorized for mutation.

## Durable authoritative manifest

Proposed `PerimeterProjects` records live in the faction core's existing `RaidSavedData` compound, alongside the Treasury and payment receipt. Core relocation must preserve them. Keep large records server-only; UI packets carry a bounded summary and the existing bounded full review preview.

A versioned manifest contains:

- Commission UUID, format version and generation, owner UUID, reserved builder UUID, original core key/position, faction ID and exact reviewed sorted chunk union.
- Original material choice, exact global target map, original block states, original clearance states, global reservation and reviewed fingerprint.
- Frozen stage order, exact column/cell membership, native origin/bounds, stage target/clearance digest and deterministic area UUID for every stage.
- State, active stage index, verified stage receipts, overall completed-target count and last blocker. Counts are presentation derived from validated receipts, never authority.
- A bound receipt for the one fee, or an explicitly recorded creative/no-debit commission. Later stages cannot call the general payment path.

Use the existing native block-state recipe for each stage. Do not introduce a second compact-sync codec. Avoid persisting multiple copies of the whole recipe: stage membership references the bounded global map and builds one validated native blueprint at activation.

Malformed counts, duplicate IDs/cells, unknown versions, out-of-range stage indexes, mismatched digests or incomplete memberships fail closed before reconstruction. A vanished manifest, reservation ledger or payment receipt is not evidence that a fresh job may start.

## Payment and activation sequence

The existing `PaymentSource.consume` debits Treasury but has no operation receipt. Add a narrowly scoped `consumePerimeterOnce` only after reviewing this design. The debit, signed Treasury record and `(commission UUID, manifest hash, price)` receipt must be written to the same core compound and dirtied together. Repeating the same UUID/hash/price returns its existing result; a conflicting tuple is rejected. A normal stage activation never debits.

Initial commission order:

1. Validate complete preview and manifest, select an idle owned builder, register the global reservation and prepare the first native area unpaid.
2. Protect and obtain native assignment acceptance without permitting mutation.
3. Perform the one idempotent core-compound payment and mark the manifest paid in that same authoritative data object.
4. Activate the first area only after rechecking its exact project/stage/payment receipt.

Disk state spans entity chunks and the reservation ledger even if the manifest/payment share one SavedData file. Reconcile these explicitly on reload; Minecraft does not provide an atomic transaction across arbitrary entity and SavedData files. Missing or conflicting cross-file evidence pauses recovery. Never debit again or recreate a potentially existing stage because a marker is unloaded or a save is inconclusive.

## Global reservation with exact stage leases

`ConstructionEditLedger` currently indexes reservations and retirement by individual area UUID. Simply creating independent areas would release completed sections, allow overlapping work, and lose future-section protection.

Proposed change: reserve the entire manifest once under the commission UUID, then give an exact child-area lease for its active stage. A lease binds project generation, stage index, deterministic area UUID, owner/builder and exact stage cells/digest. It is not a general permission to use any subset of someone else's reservation.

- Regular manual/legacy `register/protect/matches/retire` behavior stays unchanged.
- A separate `NativeConstructionGuard.protectStage` consumes a validated manifest/lease proof, checks its exact area recipe and original global before-states, and does not register or charge an unrelated job.
- Stage removal retires that child lease only. The global reservation survives through later stages and final verification.
- Edits to any reserved global cell pause the project. Stage activation compares its cells/headroom against the original accepted state, adjusted only by the manifest's own proven completed native work; it never accepts a fresh unrelated replacement as its baseline.
- Canceled/completed stage IDs remain recognizable from the manifest, so a stale worker save cannot revive one. Bound project and stage tombstone retention; do not exhaust the ledger's existing 64-job limit by treating every old stage as an unrelated permanent job.

## Runtime transitions and recovery

Proposed states: `PREPARED_UNPAID`, `RUNNING`, `STAGE_VERIFIED`, `WAITING_FOR_NEXT_STAGE`, `VERIFYING_COMPLETE`, `COMPLETE`, `CANCELED`, and `RECOVERY_BLOCKED`. A pause reason accompanies the current state/index and never increments it.

Before native mutation, the guard verifies the project's generation, active stage lease, receipt, exact original territory union and live permissions. Expansion/shrink pauses the whole commission. It does not rewrite the quote or add an obsolete internal wall. Only the active stage's world-read envelope must be loaded for work; no native scan may touch an unloaded chunk.

Completion of a native stage is not completion of the perimeter:

1. The native guard proves every stage target equals its accepted block state and no native queue ended early.
2. Record an idempotent stage-verification receipt before allowing the old marker to retire.
3. Verify and detach only the builder's exact old native reference. Wait for safe inventory/material-goal quiescence; do not clear supplies, tools, ownership, normal schedules or unrelated orders.
4. Retire the child lease, advance once, and prepare the next deterministic stage only when its site, storage, permissions, tracking and loaded scan envelope pass.
5. If the next stage is unavailable, persist `WAITING_FOR_NEXT_STAGE` with the blocker and full reservation. No marker disappearance is treated as success.

Recovery cross-checks all manifest/area/builder/ledger receipts. An already verified old stage is retired without a second advance or debit. An active stage whose marker chunk is unloaded waits. An unknown or mismatched marker pauses. Lost ledger history remains fail-closed. Area destruction without a verified stage receipt pauses the project; chunk unload merely waits for the same marker.

The final stage moves to `VERIFYING_COMPLETE`. Declare `COMPLETE` only after the exact whole global target map has been verified and reservations/edit history agree. If final sections are not loaded, report awaiting final verification instead of silently declaring completion. This keeps the user-facing claim stronger than a count of stage receipts alone.

## Marker placement and travel need explicit integration

The current `ConstructionMarkerSite.find` searches around the online owner's position and requires a close visible safe marker. It cannot simply be reused to spawn an arbitrary remote stage. A stage-safe placement path must remain loaded, within the authorized claim and tracking envelope, collision-free, outside reserved wall/headroom cells, and compatible with the native worker's real route. No teleport or forced chunk ticket is permitted.

For the first implementation, an unavailable safe next-stage marker may pause and ask the owner to approach that section. Do not invent an automatic navigation or remote placement contract. Native movement to a successfully assigned stage remains Workers' job. Full-territory holes/disconnected holdings do not guarantee a traversable path; lack of a route is a visible blocker.

## Whole-project cancellation

The Construction panel and active marker cancel the commission as a whole. Authenticate the same owner and bind the explicit project ID/generation. Persist the canceled state, retire the global reservation with durable cleanup evidence, and detach only the exact active worker/area links. Unloaded workers receive a cancellation tombstone they must reconcile before any work resumes. Completed blocks remain; fee and consumed supplies are not refunded, matching current cancellation. Cancel/reload/retry must never spawn another stage.

## Required tests and acceptance

Pure/deterministic tests:

- Exact global partition equality, no overlaps/omissions, original shared borders absent, all palettes and foundations preserved.
- Same accepted manifest/hash under deterministic input ordering; changed territory/terrain refreshes free review.
- Every stage fits the actual native serializer and scan budget; no quota override.
- One payment under repeated confirmation, repeated activation, failed assignment and restarted stage transitions.
- Save/reload at each boundary: before assignment, after assignment/before debit, after debit/before activation, mid-stage, after stage verification/before removal, after old removal/before next assignment, final verification and cancellation.
- Unloaded markers, stale saved workers, duplicate area IDs, conflicting generation, lost/corrupt ledger, missing receipt and transferred worker fail safely.
- Global reservations survive all intermediate retirement and block competing work; one edit in an earlier/future stage pauses the project.

Real pinned-runtime acceptance:

- Preserve the existing one-chunk and two-record L scenarios as lower-scope regressions.
- Add at least a normal 5×5 perimeter (6,600 flat cobblestone/oak targets, necessarily multiple stages), finite real chest stock, one 64-emerald debit, native walking/material goals, an actual mid-stage save/reopen and an actual between-stage save/reopen.
- Observe exact total native placement and material conservation through the last stage; verify no interior shared-border wall, stage omissions, early success, free material or second fee.
- Exercise an unavailable future stage, global territory expansion/shrink, later-stage obstruction, cancel with an unloaded worker, and final verification.

## Coordination boundary

No shared-file implementation should begin until the parent coordinates ownership for `RaidSavedData`/core manifest and payment, `ConstructionEditLedger`, `NativeConstructionGuard`, `PlayerFortificationJobs`, marker lifecycle, native QA and the revised Construction UI. These are one architecture with coupled receipts, not independent ad hoc queues. Compact native sync remains out of scope.
