# Native active-helper lifecycle QA: exact-API prerequisite

This is a separate opt-in workstream. Existing shared handoff/death/cancellation
acceptance and its source-specific evidence remain separate. No active-helper
dimension-transit, coordinator-first disk reload, or whole-site two-worker
eviction pass is claimed by this tooling increment.

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

## Mandatory next gate before a lifecycle fixture launches

Independent review must inspect the captured exact Forge 47.4.16 control flow,
including destination insertion before source removal, cancellation/null
behavior, one native reposition callback with portal creation disabled,
`CHANGED_DIMENSION` versus finalized `UNLOADED_TO_CHUNK`, and the exact
`ForgeChunkManager.forceChunk` UUID-owner overload's persistent add/remove and
ticking/reinstatement semantics. Reviewed signatures and class/disassembly pins
must become a fail-closed prelaunch gate. Runtime supplying artifacts must match
that gate before the transfer stimulus is used.

Only then should separately opted-in fixtures implement supported dimension
transit and whole-site disk eviction. The lifecycle fixture must keep one exact
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
