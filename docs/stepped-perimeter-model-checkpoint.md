# Stepped perimeter two-phase model checkpoint

## Scope and limits

This is an isolated, new-only pure Java model. It does not connect to a world, native Workers,
Treasury, the legacy perimeter project/codec, Earthworks receipts, or a store. It never authorizes
work, debits currency, acknowledges a dirty save, proves receipt provenance, or proves persistence.
`executionSupported()` remains false in both new classes. There is no production activation path.
No legacy version, hash, saved-data key, or whole-XZ-column stage behavior changes.

`PerimeterSteppedAssembly` binds exactly two nonempty phases, FILL followed by STRUCTURE,
under one parent quote. The first phase admits any bounded nonempty set of AIR-to-DIRT edits;
it is not restricted to one fill cell. Empty fill is explicitly refused. The model never invents
an empty child, a retirement receipt, or a legacy fallback. Structure admits AIR-to-full-block
placements and exact already-matching full-block KEEP targets. Cuts, replacement edits, stateful
wall targets, and arbitrary region graphs are outside this slice.

The assembly records the exact owner, builder, core, faction, dimension, claims, project generation,
ledger identity and generation, world height, reviewed fingerprint, and single 64-emerald quote.
Each target and read-only clearance/support observation includes its exact original block state
and properties plus observed edit revision. Target ownership must be inside the claims. Read-only
approach observations may be outside, remain reserved, and confer no mutation permission.

Two phase memberships are disjoint; shared read-only observations belong to both reservations.
Structure depends on phase zero. Phase native-area IDs are deterministic and distinct. The planned
material bill counts PLACE targets only and is checked against the supplied bill. These counts are
not native parser demand or inventory evidence. The real parser must independently agree before
any future controller can activate a child.

The SHA-256 canonical stream uses typed fields, collection lengths, sorted keys, and length-framed
UTF-16 code units. Unpaired surrogates do not collapse through replacement encoding. It includes
all recorded identity, geometry identity, target/original/edit observations, phase membership,
dependency, reservation, and bill values. Area IDs and phase digests are deterministically derived
from the complete assembly digest and phase index. Limits cover targets, claims, observations,
aggregate reservation, state-property strings, total state code units, and native envelope volume.

## Compiler handoff

The independently compiled `PerimeterSteppedGeometry.Draft` supplies `geometryVersion()`,
`digest()`, immutable target/phase/component values, clearance cells, and read-only footing cells.
Its canonical geometry digest also commits to gates, approaches, seam metadata, and column levels.
A future bridge maps compiler `Pos` to assembly `Position`, maps its four full-block enums to
property-free namespaced states, and supplies exact world state/edit-revision observations.
Assembly intentionally has no source dependency on the moving compiler implementation.

The assembly constructor validates its own input shape but cannot establish that supplied maps
really came from that compiler or that the compiler digest matches them. The future bridge must
compare the exact compiler output, bind world-review evidence, recheck claims/protection, and
refuse mismatches. A feasible geometry proposal is not execution authority or proof of safe
collision shapes. Non-level physical transitions remain a separate compiler/native verification gate.

## Receipt/state model

`PerimeterSteppedProject` starts unpaid with no receipts. It accepts only externally supplied
payment or creative-exemption, activation, whole-phase verification, native retirement, and final
observation receipts. Each is bound through the exact assembly/project/ledger identity and, for
phase receipts, phase digest and area. Event IDs cannot be reused across different events.

The state sequence is:

1. PREPARED_UNPAID
2. PREPARED_PAID after recording the sole parent payment/exemption assertion
3. RUNNING with an externally supplied activation receipt for FILL
4. PHASE_VERIFIED with the actual exact verification receipt; no phase advancement yet
5. WAITING_FOR_NEXT_PHASE only after recording its separate native-retirement receipt
6. RUNNING, then PHASE_VERIFIED for STRUCTURE
7. VERIFYING_COMPLETE only after the second actual retirement
8. COMPLETE only after a separate externally supplied final-observation digest bound to both ordered retirement IDs

The immutable snapshot validates the exact ordered verification/retirement prefixes, active lease,
payment, state, phase, and minimum revision. A paid boolean or counters without receipts cannot
restore a later state. Identical receipt retries with a current check are no-ops; stale checks and
conflicting receipts fail closed. A revision cannot overflow.

UNCERTAIN is sticky and preserves the prior state, original reason, and actual receipts. Cancellation
while uncertain is refused so it cannot erase this fence. No recovery-unblock or uncertain-cancel-intent
path is included. CANCELED also preserves the active lease, receipts, and complete reservation; it is
not evidence that a worker detached, a native area retired, or cleanup finished. Neither snapshots
nor receipt-shaped values authenticate external facts. `restore` only validates consistency; it
is not a codec or a durability/provenance check.

## Verification

The focused suite uses actual Corretto Java 17 and Jupiter 5.10.2 through the official JUnit
Platform console standalone 1.10.2. It compiles only the two new models and their matching tests,
with `javac -Xlint:all`. The checkpoint has 56 passing tests and zero failed/skipped tests.
No vendor stubs, fake Minecraft types, Gradle task, Forge compilation, full regression build,
live native placement, native supply transfer, persistence/save-reload, or player travel was run.

To rerun using existing official tool installations:

```sh
mkdir -p build/stepped-model-focused/classes
"$JAVA_HOME/bin/javac" -Xlint:all -cp "$JUNIT_CONSOLE" \
  -d build/stepped-model-focused/classes \
  src/main/java/com/devfarinsky/siegeoverhaul/core/PerimeterStepped{Assembly,Project}.java \
  src/test/java/com/devfarinsky/siegeoverhaul/core/PerimeterStepped{ModelFixtures,AssemblyTest,ProjectTest}.java
"$JAVA_HOME/bin/java" -jar "$JUNIT_CONSOLE" execute \
  --class-path build/stepped-model-focused/classes \
  --select-class com.devfarinsky.siegeoverhaul.core.PerimeterSteppedAssemblyTest \
  --select-class com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProjectTest \
  --reports-dir build/stepped-model-focused/reports --details summary
```

The normal Forge regression build, independent review, exact native parser material census,
server-owned atomic payment/persistence handling, native callback verification, and focused live
safety/claims/reload/cancellation/travel checks remain separate requirements before release.
