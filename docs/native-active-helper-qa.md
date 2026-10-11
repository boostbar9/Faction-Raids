# Native active-helper lifecycle QA

This is a separate opt-in workstream. Existing shared handoff/death/cancellation
acceptance and its source-specific evidence remain separate. The local implementation adds a separately opted-in same-stage helper transit and
coordinator-first disk scenario. No native pass is claimed until its exact hosted
source, reviewed API gate, loaded-provider parity and runtime oracle all pass.
Whole-site eviction, stage-skipped return and cancellation while absent remain
explicitly uncovered.

## Prepare-only capture

The `Native Active Helper API Evidence (Prepare Only)` workflow accepts the
`native-active-helper-api-qa` opt-in label only on same-repository PR #295 into
`release/4.53.0-alpha.3` (opened, reopened, synchronized or labeled). This lets the
prerequisite run before its workflow exists on the default branch. Manual
dispatch remains available only once the workflow is on the default branch,
as required by [GitHub's manual-run contract](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
Neither route uses `pull_request_target`, write permissions or stored credentials.
The workflow compiles the opted-in shared native source set and prepares the same
Minecraft artifacts, copied runtime artifact collections and source-set runtime
classpaths used by `runClient`. A separate task exports those **already resolved**
files, their SHA-256 values, canonical supplying paths, available resolved
coordinates, Minecraft/Forge/mapping versions and toolchain information. It does
not search a cache for a plausible filename or choose among duplicate classes.

```
./gradlew --no-daemon --console=plain --max-workers=2 \
  -PnativeQa=true -PnativeQaMode=staged-handoff -PnativeSharedCrewQa=true \
  -PnativeActiveHelperApiQa=true prepareNativeActiveHelperApiEvidence
python3 scripts/capture-native-helper-api.py \
  build/native-active-helper-api/evidence/resolved-runtime.json \
  build/native-active-helper-api/evidence/api-capture
```

Use a fresh checkout/build directory and the existing fresh-client defaults
helper, as the workflow does. No world is created, game launched, helper moved,
ticket added, or gameplay permission changed. With this property enabled,
`runClient` deliberately fails before launching Minecraft. Baseline workflows
do not enable the property and are unchanged.

The collector inspects the exact supplying JAR for each required vanilla,
Forge and worker-inheritance class, plus its nested classes. The bounded set
covers native entity reconstruction and removal reasons, `ITeleporter` and
`PortalInfo`, Forge forced-ticket bookkeeping/reinstatement, server-level and
chunk-manager ticking, entity-section load/unload callbacks, and the actual
Workers/Recruits superclass chain. It records supplying JAR manifests/hashes,
class-byte hashes, signatures and complete `javap -c -p -s -l` disassemblies with
hashes. Missing classes, ambiguous suppliers, changed artifacts, changed pins,
multi-release variants, changed manifest bytes, partial disassembly or overwritten
evidence fail capture. Only text and JSON
are uploaded; vendor JARs, class binaries and worlds are excluded.

The result is explicitly `captured-unreviewed` with `gameplayAuthorized=false`.
A successful capture is **not** a semantic audit pass. These are prepared
pre-mixin JAR bytes, not observed transformed classes. Version fields are declared
by the prepared build; resolved coordinates and supplying manifests are retained
for independent identity review. Those fields alone do not authenticate the
actual Forge supplier as 47.4.16.

## Reviewed gate before a lifecycle fixture launches

Independent review inspected the captured exact Forge 47.4.16 control flow,
including destination insertion before source removal, cancellation/null
behavior, one native reposition callback with portal creation disabled,
`CHANGED_DIMENSION` versus finalized `UNLOADED_TO_CHUNK`, and the exact
`ForgeChunkManager.forceChunk` UUID-owner overload's persistent add/remove and
ticking/reinstatement semantics. The reviewed signatures and class/disassembly pins are enforced by the separate
fail-closed prelaunch gate described below. Runtime supplying artifacts must match
that gate before the transfer stimulus is used.

Separately opted-in fixtures may then exercise supported dimension transit and
whole-site disk eviction. The implemented increment below covers only the first
same-stage and coordinator-first disk scenario. The lifecycle fixture must keep one exact
custody record per worker UUID, check uniqueness after transfer settles rather
than inside join callbacks, account for every persistent holding-dimension
ticket and prove zero remaining tickets before absence/save, capture pre-AI
disk joins without world queries, preserve owner Survival/coordinator progress,
and observe same-stage, historical-stage and cleanup-only cancellation returns.
No direct saved-membership editing, replacement actor creation, forced native
goal ticks, target placement, inventory repair, time extension or build-site
tickets are allowed. Each scenario retains the 600-second process cap; split
complex scenarios rather than weakening the required evidence.

Pure Python capture tests use deliberately fake class payloads and a fake
disassembler. They prove boundary rejection and output accounting only, not
Forge semantics, compilation or native gameplay.


## Separately opted-in first gameplay increment

`Native Active Helper Same-Stage Disk QA` runs only with its own
`native-active-helper-qa` label on same-repository PR #295 into
`release/4.53.0-alpha.3`. This initial gameplay workflow has no manual-dispatch
route. It has read-only repository permissions, no publisher or credentials,
and a fresh `build/native-helper-qa/client` world. The existing representative
shared death/handoff acceptance runs independently and is excluded only when
`nativeActiveHelperQa=true` selects this different driver.

Preparation uses both `nativeActiveHelperQa=true` and the prepare-only
`nativeActiveHelperApiQa=true`. After preparation, `verify-native-helper-api.py`
creates a new fail-closed gate from the independently reviewed supplier/class/
method contract. The game invocation omits the prepare-only property; its Gradle
`doFirst` revalidates that exact receipt read-only. Raw unreviewed capture cannot
launch this driver. At client startup, before world creation, the driver checks
actual loaded class providers, raw resource hashes and the full worker superclass
chain against the gate. This is provider/raw-resource parity, not inspection of
transformed method bodies. Unsupported provider URL shapes fail closed.

The prepared Forge 47.4.16, Minecraft 1.20.1, official mappings, Workers 2.0.3 and
Recruits 1.15.2 bytes are cryptographically pinned under
`scripts/native-api/helper-lifecycle-1.20.1-47.4.16.json`. BowmanEntity and
RecruitSimpleContainer are newly recorded raw class hashes under the same already
reviewed Recruits JAR pin; the older supplemental audit never claimed raw hashes.
The hash-bound independent review text accompanies that contract.

The first bounded scenario uses the same paid 572-target hollow plan, finite 352
cobblestone/220 oak and synthetic 139-target first section. It requires both
original workers to withdraw from the finite chest and place native blocks
before any absence. It then observes:

1. A naturally closed helper at ordinary END: canonical BeforeItemSlot=-1,
   empty native requests, closed hand/storage/cleanup state, no sleep/use/combat/
   liquid/riding/leash, and five grounded observations with zero fall distance.
   The predicate is diagnostic and bounded; no request clearing or forced goal
   transition makes a window happen.
2. One native dimension transfer to a synthetic sealed breathable stone holding
   room in the existing Nether, at feet (8,160,8), prepared before commission.
   Its chunk is held only by the exact audited Forge UUID-owner ticking tuple.
   The clear return landing at (137,65,9) is baselined before commission. Neither
   site intersects protected targets or reservations.
3. Before/raw HIGHEST/production LOWEST/settled cargo and receipt comparison,
   non-destructive CHANGED_DIMENSION source removal, one exact registered live
   destination UUID after the call and again at a later ordinary END. Source
   equipment cleared by native removal is never counted again. Production alone
   handles hand rebind and saved membership. The Overworld controller must
   suspend the absent member while its coordinator makes new native progress.
4. Same-stage native return and genuine same-helper placement. A second transfer
   removes the holding ticket, waits for unavailable holding chunk/lookup plus
   finalized UNLOADED_TO_CHUNK, and records exact offline cargo. Missing lookup
   alone is insufficient. The eviction wait is at most 1800 ticks; finalization
   after first unavailable observation is at most 200 ticks.
5. A real close/reopen with the owner saved in non-op Survival. The coordinator
   must actually disk-join first and place again while the helper remains
   disk-unloaded and all QA tickets are zero. Only then may the exact holding
   ticket load the helper. Its later `loadedFromDisk=true` join must match the
   finalized offline cargo. A separate non-disk native return must rebind and
   place again through the real production path.
6. Authenticated real core-menu cancellation with both workers loaded, exact
   terminal/cleanup identity, retained unfinished work and 100 stable END ticks.
   This does not cover cancellation while absent.

All transfers use the one reviewed reposition callback with `false`; there is no
manual entity reconstruction, membership edit or target/inventory repair. The
fresh world alone has `RecruitsChunkLoading=false` installed through
`defaultconfigs/recruits-server.toml` before creation; inherited chunk-loader
state must already be empty. The fixture never rewrites an existing server
configuration. Owner Survival and the original coordinator stay ordinary; only
the actual close/reopen disconnect interrupts the owner.

Every persistent ticket add has a recorded absent preflight and pending intent
before the native call. Even a partial throwing add owns a bounded cleanup
obligation. Cleanup removes only that known tuple and preserves the current
unrelated census. Any original-census divergence still fails permanently after
safe owned removal. Zero tickets are required before disk absence, save and
finish. The disposable world must never be reused after a failed operation.

Every accepted placement is bracketed around one real native LivingTick and
requires its exact material debit and production completion receipt. Per-worker
contributions, original chest withdrawals, full cross-dimension/offline stock,
claim identity, immutable project/payment, Treasury credits, holding geometry and
same-claim auxiliary isolation are checked without invoking production
continuation or reconciliation methods. The exact observed authenticated pending load-only main-hand mirror is counted
once only while raw slot/hand values match and no third alias exists. Every cargo
slot is independently counted; an unequal or unobserved split fails. Production
must clear pending, restore object identity and record a rebind for each nonempty
split. QA never invokes that repair. Full inventory NBT strings are comparison
evidence; the verifier uses explicit counted material/debit fields for stock
arithmetic. Original source quantities are never restored or compensated.

The whole game process has a fixed 600-second cap. A natural eligibility window,
full entity-ready destination, genuine eviction or first-stage continuation that
cannot be observed in this budget is a failure, not permission to lengthen it.
If this combined small scenario proves infeasible, split the scenario under
review rather than relaxing the required production evidence.

Local checks are source-17 parsing and Python oracle/source-contract tests. They
do not compile Forge types or prove engine behavior. The fresh hosted build,
loaded parity, exact native run, strict verifier and independent evidence review
remain required. The baseline report-correction screenshot and prior shared
acceptance remain source-specific separate evidence.

## Separate Case C: cancellation while the helper is disk-absent

The independently selected `cancel-while-absent` case uses the same fresh-world
572 targets, 139-target first section, finite 352 cobblestone/220 oak and single
64-emerald fee. Its read-only, same-repository alpha PR route is the
`native-absent-helper-cancel-qa` label on PR 295. The workflow adds
`-PnativeActiveHelperCase=cancel-while-absent` during preparation and launch and
uses `scripts/verify-native-absent-helper-cancel.py`. With no case property the
accepted `same-stage-disk` scenario and its separate oracle remain the default.
Unknown cases and a case selected without the active-helper opt-in fail closed.
This new case requires its own hosted compile, native run and artifact review;
implementation or synthetic test success does not establish gameplay acceptance.

After both original workers withdraw and place naturally, the helper transfers
once to the reviewed holding room. The existing ticket stays held through unique
registered custody at the following ordinary END, then is removed and verified
zero. Missing lookup alone does not qualify: the actor must finalize
`UNLOADED_TO_CHUNK`, the holding chunk must be unavailable, all dimensions must
lack the helper UUID and its exact finalized inventory/receipts are frozen.
Only then do the 100 absent ticks and new coordinator-placement observation begin.
The owner remains ordinary non-op Survival and uses the real authenticated core
menu to cancel the exact paid project. There is no server restart in this case.

The first server-authoritative CANCELED or compact-terminal observation freezes
all 572 target block states and individual source/coordinator/offline-helper
material custody. Native worker bracketing continues after the active project and
marker disappear. No later withdrawal, placement, claim or authority is accepted,
even when the total stock would still balance. Earlier legitimate work retains
its exact recorded marker completion receipt. The compact receipt must preserve
project, owner, manifest, generation, payment and full stage summary while the
reservations, markers and stage journal are gone. The absent helper must remain
known, suspended, unresolved and historically granted only A0, without a fresh
admission, rejoin or destruction receipt.

After 100 stable canceled offline ticks, the same exact ticket tuple loads the
helper from disk. Its raw HIGHEST values must match the finalized actor snapshot;
production LOWEST may add only the existing hand-refresh-pending flag. The same
read-only terminal proof is used for transfer eligibility and the narrowly
observed pending-load hand mirror census. It validates the original helper and
current owner, paid terminal and ledger identity, original immutable A0 hand
receipt in actor and ledger, exact historical cleanup scope and member state.
The stale-unresolved state requires every original association channel. The
resolved state requires all work selectors, native associations, requests and
cleanup operations cleared by ordinary production. No arbitrary equal stack or
unobserved split becomes exempt from accounting. A real recorded load pair,
unchanged values, no third-slot alias and subsequent canonical identity/rebind
remain mandatory. Same-tick hand restoration and terminal cleanup are valid;
the fixture does not force an artificial intermediate state.

Cleanup can occur in the holding dimension because production authenticates the
Overworld ledger there. The fully detached original UUID then returns through the
supported native transfer, again with exact raw/LOWEST/synchronous/later-END
custody. The existing ticket is released after that later-END proof. Both actors
must stay loaded and detached for 100 further ordinary ticks with unchanged
terminal receipt, target states and individual finite stock. The oracle requires
exactly two transfers, two balanced ticket cycles and three helper admissions
(non-disk outbound, disk holding join, non-disk return), with no coordinator disk
join. Failure paths preserve failure and remove only the owned pending tuple.

All established caps remain: 600 seconds total, 1200 ordinary-phase ticks, 1800
eviction ticks, 200 removal-finalization ticks and five naturally grounded END
observations before transfer. No coordinator pause, forced goal/placement,
request clearing, stock compensation, membership edit or postcommission world
setup is permitted. Later-stage return, whole-site eviction, actual sleep,
post-DONE succession and complete-perimeter construction remain separate gates.
The real cancellation-request screenshot does not by itself prove visible
cancel-control rendering or the server-authoritative transition.
