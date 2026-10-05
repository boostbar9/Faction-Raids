# Terrain-following perimeters and bounded local earthworks: staged design

Status: pure level-region selection, an exact unhooked local-earthworks manifest/codec, and a versioned progress journal. Terrain-following stepped wall geometry is the approved next default; it is not live yet. This branch does not enable excavation, alter current reviews, change accepted jobs or raise the existing support limit. The required UI proposal was delivered before implementation. Runtime integration needs the gates below.

## Approved default: follow the terrain

The intended new-plan default is terrain-following stepped wall sections, short foundations over dips, and small level gate pads. Cutting/filling is a bounded local fallback for gates, transitions and dips, not a mandatory component-wide plane. The existing level-region selector and exact operation/progress contracts remain reusable for those local regions. They do not implement stepped wall geometry or establish its seams, footing, wall-walk continuity, native worker access, four cardinal gate joins or absence of holes.

A terrain-following compiler needs separate geometry and admission proof before fresh reviewed commissioning. Existing accepted/built plans retain their recorded geometry and quote; there is no automatic regeneration or demolition. The updated grounded UI proposal must precede UI implementation. The current classes remain unhooked while those gates are developed.

## Current cause and retained contracts

`PerimeterBlueprint` chooses the maximum sampled surface Y per edge-connected claim component and fills lower columns up to that plane. Its eight-block support bound is intentional. A relief greater than eight therefore fails before a native job or payment; increasing that limit would hide access/capacity problems rather than level the land.

Saved `PerimeterProject` v1 manifests, stages, payments and the protected native snapshot are immutable. They cannot be regenerated under a new grade policy on load. Existing `AcceptedConstructionReservation` describes unchanged occupancy, not authorization to mine. The protected area denies the native broad `FREE_AREA` mode. Existing soil/plant predicates used by enemy camps must not inherit new player permissions.

The builder-arrival work in PR #274 addresses premature horizontal dispatch and native partial approaches. It deliberately preserves the companion's current vertical semantics. A strict physical vertical-reach policy requires a separately proved work-access plan; it cannot be added to a paid eight-depth plan as an isolated check.

## First planning stage

`PerimeterGradePlane` accepts already sampled, bounded surface columns for one already-bounded level region. It never reads the world. It proposes one level plane and exact per-column cut/fill depths and counts. It does not classify natural terrain or grant mutation authority.

Provisional conservative policy for new reviewed plans:

- Preserve mode: no cuts, at most the existing eight fill blocks per column
- Reviewed cut/fill mode: at most four cut blocks and eight fill blocks per column, at most 4,096 cuts and 32,768 fill cells, and at most 20,480 columns
- Remain within world coordinates, build height, the wall's six-block clearance and all existing total-plan/native-serialization budgets
- Prefer the highest feasible plane, minimizing removed terrain; no removal is proposed when the old support-only plane is feasible
- Reject the complete proposal when relief, counts or metadata are unsafe; do not return a partial buildable section
- Keep disconnected components independent and ordering deterministic

These are staged local-earthworks bounds, not a new gameplay setting or a claim that every allowed relief has a usable worker route. The four-cut bound does not increase the existing fill bound. Terrain-following steps are now the intended wall default, but still require proof of continuous wall-walk joins and worker access; do not join mismatched deck levels with an inaccessible vertical wall.

## Second stage: exact proposal contract (implemented, unhooked)

`PerimeterEarthworksManifest` and its strict `EarthworksVersion: 1` codec persist one proposed level region, separately from accepted `PerimeterProject` v1 saves. There is no live compiler, acceptance/payment, world write, migration or native authorization caller. This deliberately narrow format records:

- Full original block states/properties, durable edit-revision observations, explicit work versus protected/gate-floor/gate-headroom/dependency roles
- Ordered single-cell cut, fill and build steps, including a cut followed by a build at the same cell; contiguous top-down cuts and bottom-up fills meet the component plane
- At most four cuts/eight fills per column, 4,096 cuts, 32,768 combined fill/build placements, 20,480 work columns and 65,536 total observations
- Immutable project/owner/builder/generation, world, faction/claims/layout/policy identity, plane/build bounds and recorded 64-emerald quote
- Whole-proposal and exact ordered-stage digests; construction demand remains separate from possible mining drops
- Explicit soil/stone/wood/modded family, unknown versus recorded-worldgen origin, and version/source-pinned adapter descriptors for removal proposals

The descriptor and origin fields are evidence to be validated later, not proof generated by this model. In particular, `UNKNOWN` remains unknown and `requiresSpecificRemovalReview()` is a warning, never consent. The format refuses known player edits, block-entity/fluid mutation and non-air placement originals. It cannot declare callback-dependent mutations; attempted extra codec fields fail closed. Trees, paired plants and modded callbacks with outside-cell effects must stay blocked until separately audited bounded dependency contracts exist. A dependency observation is read-only, not permission for callbacks to change it.

Edit revisions are hashed and preserved, but the server edit-source integration, genuine natural-origin evidence, manufactured-structure admission, adapter registry, exact-removal review and fresh server revalidation are not implemented. The contract does not assert a native-serializer capacity check, access route, safe escape, claim ownership, staged payment/progress receipt or completed work. Those are subsequent gates. No existing `PerimeterProject`, stage layout, native reservation or construction ledger source changed in this stage. Gate persistence remains owned by the coordinated cardinal-entrance branch.

Focused tests cover exact snapshots/property round trips, immutable/deterministic records, staged cut-before-build, gate observation exclusion, edit revisions, truthful unknown provenance, material counts, caps/order, malformed/tampered metadata and unchanged paid v1 saves.

## Third stage: reusable progress journal (implemented, unhooked)

`PerimeterEarthworksJournal` is a separate immutable `EarthworksJournalVersion: 1` contract bound to the exact manifest and an external ledger generation, admission receipt and whole-project payment receipt. It does not charge anything, authenticate those external receipts or authorize native work. Its transitions are:

1. READY to PENDING: retain one exact next-step intent before exposing work to the native controller
2. PENDING to observed receipt: require exact before/after states, unchanged observed edit revisions, and an external native tool/hand/inventory/drop-accounting receipt; cuts consume zero construction items and a fill/build consumes exactly one
3. At the end of a stage, retain STAGE_VERIFIED until an independently authenticated native-detachment receipt is supplied; only then move to the next stage
4. After every step and stage receipt, retain VERIFYING until a fresh whole-world/accounting reconciliation receipt is supplied
5. Cancellation retains every prior receipt, the immutable payment reference and any unresolved pending intent; it never restores blocks, refunds stock, generates drops or revives work

Exact repeat acknowledgments are idempotent under the current progress token; stale/foreign tokens, conflicting receipts, repeated placements, stage skips and rewritten progress fail closed. Save/load is strict and bounded, including exact prefix/stage digests and terminal metadata. Immutable linked receipt prefixes avoid copying all earlier cells on each ordinary acknowledgment.

This is bookkeeping only. An accounting digest is a reference to future authenticated evidence, not proof fabricated by the contract. World state alone cannot establish who caused a change. Reloading PENDING preserves ambiguity rather than replaying a callback or guessing completion. Cancellation with a pending intent is not a completed accounting reconciliation or permission to discard the underlying audit records; the future cross-file controller must resolve that ambiguity without further work or item issuance. The current contract deliberately cannot revive a canceled journal. Native callbacks, storage, retrieval, claims, access, receipt authentication and cross-file recovery remain unwired.

Twelve focused tests cover every serialized transition, pending restart, exact retry/conflict, cross-ledger identity, same-state edits, construction consumption, cancellation at partial boundaries, final reconciliation, malformed/tampered receipts and legacy-format separation.

## Exact mutation and occupancy manifest

Before live wiring, add a versioned contract with disjoint or explicitly ordered roles:

1. Original full block states, including properties, for every observed mutation and protected occupancy cell
2. Exact native cut cells, exact post-cut state, and adapter/version identity for any nontrivial removal behavior
3. Exact fill/build targets and material counts, separate from potential mining drops
4. Read-only gate inside/outside approach floor/body/headroom cells; an occupancy reservation never grants a write
5. Declared ordering/dependencies, component plane, work access and safe escape requirements
6. Owner, builder, faction/claim, world, project generation, fee, quote, layout and policy/version identity

Bind the whole contract into the review fingerprint and durable project/stage digests. Decode old v1 manifests under their existing meaning; reject unknown/corrupt new metadata without guessing. Every stage membership and cut/fill receipt must be lossless across save/reload, stage handoff, cancellation and ownership changes. Do not encode AIR as a native placement item or silently drop clearance-only columns to satisfy the old atom model.

The gate planner is being staged separately. Its approaches can extend outside the current wall envelope. Admit them only after exact authority and loaded-state checks, and persist those same cells. An unclaimed outside path is not permission to alter someone else's claim; changed claims must pause the project.

## Natural blocks are not reliable provenance

A stone/log block state does not identify who placed it. Do not equate a `natural` tag, registry name, stone type or absence of a recent edit record with proof of world generation.

- Always refuse inventories, block entities, known player edits, manufactured structures, claims/permissions the owner cannot modify, fluids and unknown state/side-effect contracts
- Keep existing later-player-edit protections and neighbor-update checks active
- Where provenance is unknown, the initial implementation must preserve the block or require an explicit in-game review/authorization of that exact proposed removal with truthful unknown-origin wording; generic natural-clearing permission is not proof
- Start with independently audited bounded removal behavior. A broad modded-block/name/tag exemption is not acceptable
- Trees require special treatment: breaking logs can trigger leaf decay, support loss and drops outside the immediate cell. Paired or dependent modded plants can remove partners in callbacks. Include every authorized dependent change in a bounded adapter contract or refuse it
- Never transplant enemy camp restoration/clearing authority into player construction

A player can inspect specific highlighted cells in the free review. The UI must say what will actually be removed, including unknown-origin warnings; it must not label all stone/logs as naturally generated. The final server rebuilds the exact plan and requires a fresh review on any change before charging or mining.

## Native execution, access and accounting

Do not call broad `setFreeArea(true)` or directly set construction blocks in production. Extend the protected native scan boundary to offer only the immutable authorized cut cells to the existing native mining machinery, after reviewing the pinned companion's current queue, tool, drop and interruption behavior. A cut stage must not authorize a different target because a queue changed.

Revalidate loaded chunks, exact originals, claims, block entities, entities, neighbor dependencies, world border, access and reservations immediately before mutation. Break top-down only where dependency checks prove that ordering safe. Record progress from observed native changes, not optimistic queue removal. Missing tools or supplies should become ordinary native requests.

Keep fill materials, mined drops, hand/inventory aliases, ground items and consumed construction supplies separately accounted. Do not count expected mining drops as available stock before they exist. Reload, sleep, eating and storage trips retain the accepted plan and cannot duplicate cuts, drops, items or payment.

Physical vertical work access is a first-class gate for the later stricter-reach model. Merely allowing standing on completed foundation columns is insufficient: native layer order can create a six-block face before the worker decides to climb. A viable design must prove proactive ascent, native path continuity and a safe escape from each next mutation/body conflict, or reject the new site before payment. Do not teleport the worker or grant remote placement to make the fixture pass.

## Review UX

Keep the existing dark Building > Auto perimeter design, materials and 64-emerald one-time fee. Show separate build, clear and fill colors in the in-world plan; show gate direction and approach status, exact removal/fill/material totals and any unknown-origin blockers. Explicitly label a proposal that is not ready. Free review never mines, charges or assigns work. Confirmation binds the exact server-validated manifest; changed terrain requires refreshed review.

Existing accepted jobs are not silently upgraded or demolished. A future upgrade is a new reviewed operation over known existing geometry with its own exact authorization and accounting.

## Required acceptance before wiring/release

- Pure plane tests: flat/4/8/12/13 relief, negative coordinates, independent components, deterministic order, immutable results, height/column/count bounds and no partial result
- Manifest/codec tests: cut versus read-only clearance authority, exact target/original states, tampering, size, old-v1 fixtures, restart/handoff/cancel and same-state player edits
- Admission tests: natural/modded behavior adapters, unknown provenance, house/container/claims, water/cliffs/unsupported platforms, unavailable chunks and late neighbor/entity changes before payment and before mutation
- Real native fixture: finite cut/fill project, actual tool/material retrieval and drops, every authorized cell changed only once, no off-plan writes, pause/resume on sleep/storage/access changes, exact materials/payment, and complete reload/cancel accounting
- Gate interaction: N/E/S/W usable in both directions on irregular/disconnected claims, protected external approaches, no internal or hole edges and no corner cuts
- Render/input checks at compact and roomy scales, clear counts/colors, repeated confirm, back/close/cancel and stale review
- Exact final-head full Build, independent review, merged-main artifact verification and existing release publication/public-availability gates

Until those are satisfied, the selector and proposal contract stay unhooked and the user-visible feature remains in progress.
