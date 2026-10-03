# Siege Building: native integration and safety boundary

## Audited source and runtime fence

The native control/worker audit used public Workers commit
[`29d26e1df6475fc8d043dc5d455f67b2fd1e9982`](https://github.com/talhanation/workers/tree/29d26e1df6475fc8d043dc5d455f67b2fd1e9982).
The new protected path requires exactly Workers **2.0.3** and Recruits **1.15.2**,
plus the audited virtual methods, native queue fields and goal-state shape. This
is a non-toggleable guard. Other versions pause new and loaded protected jobs;
legacy native jobs keep their existing behavior and dependency metadata.

Compile/test references are the official releases:

- [Workers 2.0.3, file 8351157](https://www.curseforge.com/minecraft/mc-mods/workers/files/8351157)
- [Recruits 1.15.2, file 8339846](https://www.curseforge.com/minecraft/mc-mods/recruits/files/8339846)

Version/API checks do not establish an arbitrary repack's binary identity. The
native QA report records actual loaded versions and SHA-256 hashes of ForgeGradle's
remapped runtime JARs. Those are distinct from original CurseForge release bytes.
Source-to-binary behavior, real rendering and the intended modpack still need the
native runtime checks; reflection fixtures alone are not companion playtesting.

## New native subtype, unchanged native rendering

`ProtectedBuildArea` extends the real Workers `BuildArea`. It has a new Siege entity
registration, so existing `workers:buildarea` entities and saved jobs are not
converted. The registered renderer is the actual `WorkerAreaRenderer`, with no
copied renderer, replacement shovel, beacon or custom blueprint drawing pipeline.

The new type has a synchronized, persisted native origin separate from its physical
entity position. Both `getOriginPos()` and `createArea()/getArea()` use that origin.
Native relative block coordinates, mirrored X, facing, width/depth/height and the
full scan envelope stay unchanged. The physical shovel and its real native pick
box occupy the same independently selected location. No negative-Y rebasing is
used. Native rendering still computes world cells through its own code.

The factory searches at most 49 nearby columns in two passes, preferring the owner's
current floor before a small terrain-height fallback. The entire marker box must
be loaded, in the permitted claim, clear, dry, safe, outside all accepted blocks,
and within a current unobstructed three-block inspection ray. The short ray's
loaded envelope is checked before vanilla clipping, including diagonal chunk
crossings; the completed plan must not block that ray. Failure rejects the handoff
instead of placing an inaccessible marker or moving the approved structure.

Tracking is explicitly 16 chunks / 256 blocks. The subtype's distance test covers
the accepted native envelope with a 32-block viewing margin, bounded at 256 blocks;
a factory request outside that bound is rejected. This does not force chunk loads
or override Minecraft's server/client view-distance caps. A remote marker that is
not tracked because its chunk is outside those caps cannot render a projection.
Opposite-end visibility must be checked with an adequate loaded/view-distance setup.

`WorkersProjectionView` mirrors native `canPlayerSee`, then
`showBox || nativeFocus == area || getAlwaysShowProjection()`. Focus is read from
the same Recruits focus method as Workers. This covers large focus-only jobs without
silently enabling always-show. The temporary frustum override restores `noCulling`
after the entity pass. Native distance/depth behavior and actual ownership visibility
remain authoritative.

## Why public native controls need a server boundary

The audited `MessageUpdateBuildArea` packet calls `setStartBuild(isCreative)`
directly, and that creative branch writes all blocks and can spawn entities without
a builder goal. Its handler does not establish trusted sender ownership/creative
permission. Generic native update packets also move or delete an area without an
ownership check. A goal wrapper, disabled client button, later tick, or ordinary
player break/place event cannot protect these paths.

The new subtype therefore defaults to denying native creative/restart calls,
structure/size/facing/owner/team/free-area changes, raw deletion and public movement
entry points. Every relevant `moveTo` overload is overridden: vanilla `moveTo` calls
final `setPosRaw` before `setPos`, so overriding only `setPos` is insufficient.
Native NBT getters and setters use defensive copies. A narrow trusted initialization
and load scope restores only the new type's own validated contract. Missing or
malformed new-type data remains closed; it is never treated as an editable legacy job.

The protected screen extends the native inspection screen. Geometry/ownership
controls are disabled as a UX supplement, and native blueprint/material inspection
remains available. The original Delete position is replaced by an authenticated
Siege action with explicit confirmation. Projection controls use the same authenticated
route. The common handler verifies the actual packet sender owns this exact nearby
marker. Canceling keeps placed blocks and existing payment/material semantics;
there are no refunds, restored blocks, or global queue actions.

A loaded transferred builder loses only an exact old area reference and matching
old Siege association. Owner, inventory, movement orders and different assignments
are untouched. An unavailable/dead worker is not accessed. Cancellation cleanup on
later load uses explicit bounded retirement receipts, described below. The existing
native creative-admin crouch-break permission path remains an authorized cleanup path.

## Native worker dispatch guard

Workers' mining, late-obstacle removal and placement call world mutation methods
directly, including a second multipart-write path. The guard wraps the existing
`BuilderWorkGoal` through `WallBuilderAccess`; it does not replace storage, supplies,
work hours, mining timing or placement. In the audited body, construction writes
are synchronous within `tick`, every five ticks (mining every ten).

The accepted world NBT, exact transform, owner, designated builder, core context,
original per-cell states and completed/cleared receipts are immutable/persisted.
Before dispatch, after the wrapper's approach transitions, the guard validates
native queue/state shape and the exact next mutation target. It rechecks live
owner/core/claim permissions, blocks, inventories, fluids, occupancy, world limits,
loaded terrain and edited-site history. Unknown APIs or unsupported states pause.

- `FREE_AREA` is always false; both its preparation and mutation states are blocked.
- `BREAK_BLOCKS` checks the existing target; a null target only selects future work
  in the audited implementation. Stale mining targets that became finished wall
  blocks are blocked even though native mining itself ignores the desired state.
- `PLACE_BLOCKS` checks its current target or exact top native stack entry, covering
  late-obstacle mining as well as placement.
- The approved templates use only full cobblestone, stone-brick, oak-plank and dirt
  cells. Multipart/secondary queues and entity-bearing structures are rejected.
- `DONE` requires all accepted cells to match before native completion is allowed.

A successful protected assignment transactionally resets stale transient goal
queues/targets from previous jobs. It does not erase the area's actual native work
queues or loosen mutation checks. `canWorkHere` reserves discovery to the paid,
designated builder, preventing unrelated builders from getting stuck on this job.

## Terrain and player-edit policy

Arbitrary dirt, stone, logs and other legacy solids are not classified as natural
or clearable. A material whitelist cannot prove provenance. Only original unchanged
single-cell vanilla vegetation allowed by the accepted site policy may be removed;
that snapshot is not a claim of natural origin. Newly added plants, paired plants,
wither roses, fluids, hazards, inventories and block entities are blocked.

Neighbor updates can affect cells outside the plan, for example breaking adjacent
cactus. Before mutation, a Manhattan-radius-two envelope is checked loaded before
any block or signal read. A closed list permits ordinary inert vanilla building
blocks/soil and simple plants; fluids, gravity blocks, redstone/reactive blocks,
block entities, powered sites and unknown modded neighbors are rejected. Radius
two also covers vanilla conductor signal lookups. This conservative policy does
not promise safety against arbitrary unreviewed third-party callback/coremod changes.

Players, creatures, vehicles and hanging entities occupying a target block pause
work. Dropped items and experience do not: native vegetation drops must not stall
construction before the next target. Later player placement/break events persist
an edited-site receipt, even when a state is changed back. Completed-block receipts
also prevent silently rebuilding later removals. Edited sites require an explicit
cancel and new reviewed plan; transient occupancy/offline/permission blockers resume
without repayment. Protected jobs pause while the owner is offline or unavailable
rather than guessing offline privileges.

## Payment, reservations and reload

Callers create the protected type, add it, initialize the accepted native queues,
and call `protect(...)` before assignment/payment. Protection begins unpaid.
`activate(area)` is called only after payment succeeds. Failed handoff/rollback
cannot build for free. Unpaid rollback uses its narrow cleanup method; raw native
Delete stays denied.

The durable index contains actual planned cells, not a hollow ring's whole AABB.
Reservations survive marker unloads. The combined active-job/pending-retirement
budget is 64 entries, and active indexed cells are capped at 262,144. Registration
reserves the slot needed for later cancellation; existing jobs can always transition
within that bound. Canceled receipts are acknowledged after exact worker cleanup.
Observed worker destruction records that no future receipt cleanup is needed.

An unknown UUID, absent site, recreated ledger or partial restore is never itself
retirement evidence. Automatic deletion of an unregistered failed handoff does not
create a retirement entry. Retirement is registered-only, explicit and idempotent.
Before replacing a worker receipt, protection must acknowledge its explicit old
retirement; active or unknown history blocks the new commission. New assignments
are never cleared by an older cancellation.

Native queue reconstruction reads world cells, so it is deferred until all required
chunks are already loaded. Readiness stays false and progress unknown meanwhile;
retry happens at the assignment/worker boundary, before approach caches can be
retained. No reload path uses creative placement or replaces an edited blueprint.

The native scan's inclusive volume is `width * depth * (height + 1)`, with a linear
placement-stack lookup at each cell. Server preflight separately bounds that volume
times planned block count; no replacement scan or unproven performance claim is made.

## Verification status and release gate

The actual pinned artifacts have compiled in Forge CI. The first full test run
exposed test-environment issues subsequently addressed: Forge event discovery was
separated from the plain guard, and real entity lifecycle contracts moved from
plain JUnit (which lacks Forge's fluid registry) to the actual native QA runtime.
Those cases were not replaced by fake registry mocks or claimed as live checks.

JUnit covers geometry, immutable serialization, exact version fencing, queue targets,
clearance/neighborhood policies, edit/retirement history, tracking bounds and narrow
cleanup. `ProtectedNativeEntityContracts` runs real subtype construction, native
creative/update packets, every movement overload, nested NBT aliases, queue restart,
round-trip/malformed saves, unpaid/wrong-builder discovery and unloaded-cell rejection
inside the fresh isolated native QA world. The native client fixture separately
records framebuffer, native raycast, interaction, GUI-scale, culling and save/reload
checks, plus actual loaded artifacts and explicit exclusions.

No production release is justified by compilation, syntax parsing, mock tests or
an unreviewed screenshot alone. End-to-end commissioning/payment/resupply, owner and
claim changes, cancellation/death/transfer/replacement matrices, far-end tracking,
scan-bound profiling, dedicated-server operation, and the intended modpack still
require their applicable successful checks and visual review.
