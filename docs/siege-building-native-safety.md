# Siege Building native integration audit

This implementation was compared with public Workers commit
[`29d26e1df6475fc8d043dc5d455f67b2fd1e9982`](https://github.com/talhanation/workers/tree/29d26e1df6475fc8d043dc5d455f67b2fd1e9982).
It is source-reviewed compatibility code, not evidence of companion-mod playtesting.
The supported runtime dependency declaration still permits multiple Workers versions;
no dependency version, compile-only dependency, or renderer registration was changed.
The actual installed Workers/Recruits JARs must be identified and tested before release.

## Release blocker: direct native placement controls

**New protected commissions are disabled.** `availabilityProblem()` returns the
verified blocker and `protect(...)` fails before activation. The native
`MessageUpdateBuildArea` packet calls `setStartBuild(isCreative)` directly; the
creative branch writes every block and can spawn entities outside the builder goal.
The inspected handler does not validate sender ownership or server-side creative
permission. A goal wrapper and a later tick cannot stop those writes. Hiding a
client button, canceling an interaction, or inspecting ordinary break/place events
would not provide a trustworthy server boundary. No such workaround was added.

A possible follow-up is a reviewed native `BuildArea` subtype with server-side
virtual mutation guards, using Workers' actual renderer and a coordinated exact
compile dependency, or an upstream hook covering every native write path. That
larger change has not been made. The goal guard described below is draft scaffolding,
not an enabled or complete claim of construction safety.

## Native projection and remaining shovel limitation

`WorkersProjectionView` now mirrors all native visibility gates: `canPlayerSee`, then
`showBox || nativeFocus == area || getAlwaysShowProjection()`. Focus comes from the
same `ClientEvent.getEntityByLooking()` used by Workers. This includes large plans
whose always-show setting is false; it does not silently turn that setting on.
The existing short-lived frustum override still restores `noCulling` after the
native entity pass, uses native world bounds, and changes no blueprint or entity
position. It neither removes ordinary block depth testing nor makes the shovel
visible through terrain.

The physical shovel issue remains unresolved. In the inspected
[`WorkerAreaRenderer`](https://github.com/talhanation/workers/blob/29d26e1df6475fc8d043dc5d455f67b2fd1e9982/src/main/java/com/talhanation/workers/client/render/WorkerAreaRenderer.java),
shovel rendering is inline in public `render`, with a private final `ItemRenderer`.
The projection helper is private. There is no public shovel-offset extension point.
A visual-only offset would leave the entity's native picking target behind.
`AbstractWorkAreaEntity.getOriginPos()` and `createArea()` both derive their origin
from `getOnPos()`. Raising the entity changes the world blueprint, and compensating
with negative relative Y moves cells outside the native scan envelope. A ring's
bounding corners are themselves wall cells; a horizontal relocation is not a
general solution. No fake marker, copied renderer, pose interception, origin
rebasing, or alteration of existing saved jobs was added.

## Supported construction guard boundary

Upstream `BuilderWorkGoal` exposes no cancellable per-cell mutation callback.
`AbstractWorkerEntity.mineBlock` ultimately calls `destroyBlock`, and its shears
branch calls `setBlock` directly. `placeBlocks` uses direct world writes and can
mine a late obstacle; multipart placement has another direct-write branch.
Forge player break/place events therefore cannot be used as the sole worker
mutation guard.

The new guard wraps the existing native goal through the existing
`WallBuilderAccess` delegation boundary. It does not replace native construction,
storage, inventory requests, work hours, mining timing, or block placement.
For the inspected source, all construction writes occur synchronously inside that
`tick`; normal full-block placement has at most one direct primary target in a
tick. Full native state/queue validation occurs before dispatch, after the wrapper's
own approach/state transitions. World, entity, owner, core and claim checks run on
the actual next mutation target. The audited native mutation cadence is every five
ticks (mining every ten); nonmutation ticks retain native look/availability updates.
Any incompatible API, unsupported state, changed geometry, escaped queue target or
unsupported multipart queue pauses the job. This source-specific guard still needs
real-version and other-mod callback testing; it is not a claim that arbitrary
future Workers implementations or reentrant third-party mutations are safe.

Mutation branches:

- `FREE_AREA` and its preparation are blocked, regardless of the original flag.
  The flag stays false; an owner enabling it pauses the protected job.
- `BREAK_BLOCKS` checks the current native target. A null target only selects a
  future position in the audited source. A stale mining target that became a
  completed wall is blocked, even though native mining itself ignores desired state.
- `PLACE_BLOCKS` checks `blockPos`, or the exact top of its native `Stack` when no
  target is selected. The same check covers late-obstacle mining. The accepted
  single-cell vegetation must still exactly match its original state.
- Both paired-block writes and secondary-only placement are excluded: the approved
  templates support only full cobblestone, stone brick, oak plank and dirt blocks,
  and any nonempty secondary queue fails closed.
- `DONE` cannot spawn recorded entities because entity-bearing structures are
  rejected. Every accepted block is rechecked before allowing native completion.

## Registration, payment and persistence

New commission callers must run:

1. Native `startBlueprint` with `FREE_AREA=false` and noncreative placement.
2. `NativeConstructionGuard.protect(owner, builder, area)`. Abort if it returns false.
3. Existing ownership/assignment/payment transaction.
4. `NativeConstructionGuard.activate(area)` only after successful payment.

A protected job starts unpaid and cannot mutate while payment/rollback is pending.
Retries and returning online never charge again or consume materials in the guard.
`beforeWorkerTick` installs the wrapper for any other builder that discovers a
protected area. Only inability to install protection cancels a living tick. Normal
blocked jobs pause their construction goal while preserving physics, food and sleep.
`status(area)` reports the real bounded pause reason for the construction report.

The accepted exact native NBT, origin, facing, dimensions, world states, owner,
reserved builder, core key/location, and completed/cleared cell receipts are saved
on the area. Geometry is copied and treated as immutable. Reload reconstructs native
queues through `setStartBuild(false)` only when the saved contract and edit ledger
match; it does not replace an edited blueprint. Missing/corrupt history fails closed.
Existing unguarded jobs are not migrated or rewritten.

The durable edit ledger indexes only protected cells, including while the marker
chunk is unloaded. Accepted block place/break events mark those sites as edited,
even if a block is changed back to the same state. Such a site cannot silently
resume or rebuild a later player edit. Completed-block receipts also prevent repair
after unobserved removal. The index is capped at 64 jobs and 262,144 total cells;
a job is removed on actual entity destruction, not ordinary chunk unload.

## Deliberately conservative limits

- Arbitrary dirt, stone, logs and other legacy solids are not classified as natural
  or clearable. A material whitelist cannot establish provenance.
- Only original, unchanged, single-cell vanilla vegetation from the existing
  commissioned-site policy can be cleared. This is an accepted site snapshot, not
  proof that a plant was naturally generated. Newly introduced plants are blocked.
  Paired plants, wither roses, fluids, hazards, inventories and block entities are
  blocked. Unknown modded vegetation is blocked.
- New protected jobs pause while their owner is offline, in another dimension,
  unable to build, or no longer has the original faction/core permissions. They
  resume without repayment when a transient permission/occupancy issue clears.
  Explicit player edits require a new reviewed plan rather than automatic repair.
- Native `scanBreakArea` reads the entire envelope. Its inclusive scan dimensions
  are `width * depth * (height + 1)`. Each scan cell performs a linear lookup in
  native placement stacks, making the conservative work estimate that volume
  times planned block count. Loaded chunks are checked across the envelope before
  native scanning. No replacement scan or unproven performance claim was added.
- A 65,536-cell guard ceiling is an absolute storage bound, not a performance
  endorsement. Server preflight must apply its stricter budgets and scan-work cap.

## Validation still required

Focused regressions cover all horizontal facings and negative coordinates, NBT
round trip and immutable copies, negative-Y/out-of-envelope rejection, multipart
and entity rejection, native queues and target selection, original vegetation and
late changes, paid gating, missing/corrupt ledger handling, reload persistence and
ledger budgets. Java 17 syntax parsing and `git diff --check` were run locally.
The Gradle distribution download is unavailable in this executor, so these tests
and full Forge compilation/build must run in the selected CI environment.

Release acceptance still needs a real Forge client/server with the actual required
companions: shovel ray picking, native camera focus, all frustum angles, 1,024/1,025
cell settings, shader/resource-pack behavior, offline/online, unload/reload,
resupply, same-state player edits, new obstructions, ownership/claim/core changes,
competing builders, callback interference, rollback/payment failure, and profiling
at the permitted scan-work bound.
