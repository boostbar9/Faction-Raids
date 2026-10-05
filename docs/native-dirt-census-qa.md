# Read-only development dirt census QA

This is a separately opted-in observer of the existing native one-FILL fixture.
It collects evidence only. It supplies no reviewed `Profile`, performs no mining
callback, and leaves production CUT refusal unchanged. A successful observation
still reports `PROFILE_UNREVIEWED`. It cannot establish packaged production
compatibility or native CUT acceptance.

## Opt-in and unchanged first slice

The existing `Native One-FILL Earthworks QA` workflow retains its ordinary
one-FILL verifier and all existing fixture assertions. Ordinary pull-request runs keep the
census **off**. A same-repository PR may explicitly opt in with the
`native-dirt-census-qa` label, or a manual run may explicitly set
`dirt_census: true`; the additional
strict verifier then runs independently. Its dependency-free failure-boundary
regressions require Java 17+ `java` and `javac` on PATH, as in the workflow. The Gradle equivalent is
`-PnativeEarthworksQa=true -PnativeDirtCensusQa=true runClient` in that already
supported isolated graphical workflow. The census switch defaults to false and
has no effect outside the native-earthworks source set.

The only existing fixture change is an appended observer call at the end of
`NativeEarthworksQa.verifyFinish`. The real completed target is `(141,64,9)`.
All original native storage/custody/receipt and payment checks run first, after
at least 40 ordinary ticks at `STAGE_VERIFIED`. No new synthetic dirt target,
world edit, item construction, entity creation, callback invocation, or tick
acceleration is used for this census. The original fixture and its independent
Python verifier are pinned byte-for-byte by source-contract regressions.

## Lifecycle, evidence, and refusal

A QA-only `ServerStartedEvent` handler initializes a live epoch and completes
that generation through the reviewed provider's `completeReload` method. This
uses the actual successful initial server-resource lifecycle, before admission or
work. If any subsequent `AddReloadListenerEvent` begins, the epoch is invalidated
before resource listeners run. This narrow fixture never re-completes a later
reload and never treats player datapack sync as successful completion.

After the original one-FILL checks, the observer captures bounded state, calls
`bindRuntime` explicitly off-pulse, captures state again, and performs two fresh
same-thread `inspect` calls with another state capture after each. The provider's
read counters prove that those fresh inspections reuse startup artifact/module
bytes while still rechecking actual runtime metadata and all 27 registry entries.
Each exported Census includes exact dispatcher coordinates and closed registry
kinds/statuses, frozen loaded-loot check, active GLM count and resource layers,
actual remapped development artifacts, complete module content and provider
identities, loaded class origins, inherited listeners, sanitized transformation
context digests, and the explicit first-party anchor.

There is no call to construct a reviewed Profile from an observation. The pure
comparison is exercised with a null profile; complete read-only evidence must
therefore return `PROFILE_UNREVIEWED`. Earlier provider refusals remain unchanged
in the binding/census diagnostics. No module openings, dynamic attachment,
additional agents, credential changes, security changes, reflective field writes,
or disabled listeners/modifiers are allowed to obtain a positive capture.

The additional section DTO does not change registry admission. Inspection still
stops at its first refusal; a refused partial census cannot be presented as a
complete 27-entry census. Y coordinates are never clipped to terrain build
height. Mock-world regressions exercise the actual lower and upper out-of-height
keys. The one-FILL target's native window is at normal build height; this run does
not claim native out-of-height dynamic-listener acceptance.

## Bounded no-effects evidence

The server thread cannot advance its own ordinary tick while this synchronous
observation runs. All four snapshots retain private exact equality and export
only bounded state summaries/digests:

- All 35,937 block states in target plus/minus 16, using nine `getChunkNow` chunks
- Existing registry-map entries (up to 128 per chunk), native listener/add/remove
  identity lists (up to 256 each), processing state, and all map sizes
- Existing and pending block-entity map state, without the generic block-entity
  getter or registry-creation getter
- Exact state and Gaussian caches for the level RNG, level thread-safe RNG, and
  worker RNG, read through pinned descriptors without advancing any RNG
- Every finite chest slot, worker slot, all 41 native player inventory slots,
  and both worker hands, including item identity, count and complete bounded NBT
- Up to 128 local entity identities/positions/motion/ticks; loose item stacks and
  ages, plus XP entity/value counts in the same local radius
- All four native work queues (up to 4,096 entries each), request count, complete
  earthworks ledger/receipt NBT and complete bounded worker persistent NBT
- Scheduled block/fluid tick **counts** only, explicitly not a global queue audit

Private identity wrappers compare reference identity without calling arbitrary
listener/entity/item `equals`, `hashCode` or `toString` methods. Per-tag binary
NBT serialization is capped at 256 KiB before copying. Raw RNG seeds, private NBT,
listener objects, Observation/Identity internals, JVM arguments and environment
values are never serialized. The export is limited to two Census DTOs plus
explicit safe maps, at most 100,000 JSON nodes, depth 16, 4,096 characters per
string and 2 MB total. Resource pack identifiers are restricted to bounded
identifier syntax. The provider already rejects private URI components and
hashes arbitrary transformation labels instead of exporting them.

These are bounded local no-effects observations, not a complete global-world or
malicious-plugin attestation. Any missing snapshot, reflection denial, changed
state, partial runtime census or unsupported loaded input fails the separate
strict gate. A QA-only non-throwing boundary covers the entire optional capture,
including preflight, export validation/serialization, UTF-8 byte budget and
CREATE_NEW output IO. It returns only a bounded capture receipt to the original
one-FILL result. Successful sidecars are bound by their exact SHA-256; refused
writes carry no digest and never overwrite an existing file. The separate verifier
requires that receipt and digest to match the sidecar, so missing, stale or changed
files cannot pass. Exception messages are never copied into the receipt. The
original one-FILL result is kept separate so an observation refusal does not
rewrite its genuine native outcome.

## Independent review and two clean launches

Before any census launch, independently review the exact frozen source/fixture
and verifier. Publish through the established PR275 writer only after that gate.
Keep the already reviewed dormant provider checkpoint and one-FILL checkpoint
recorded in the publication manifest. PR276 geometry is outside this change.

Where manual dispatch is unavailable, the reviewed same-repository PR label route
can start the first census job. Only after that job is terminal may the writer
remove and re-add the specific census label to request the second fresh job.
Check the recorded source commit/tree for both; a changed merge base/source is
not a reproducibility pair. The ordinary one-FILL label does not enable census,
and fork PRs cannot enable either native job through this route.

Run two distinct fresh native client jobs on the same source commit/tree, with
no rebuilt/hot-replaced runtime contents during either process. Each launch must
pass both the original one-FILL verifier and the census verifier:

```sh
python3 scripts/verify-native-earthworks.py EVIDENCE
python3 scripts/verify-native-dirt-census.py EVIDENCE
python3 scripts/verify-native-dirt-census.py FIRST_EVIDENCE SECOND_EVIDENCE
```

The workflow writes a bounded `ci-run.json` containing only the public GitHub
run ID and run-attempt number. The two-launch comparison requires distinct
run/attempt pairs, matching source commit/tree, and different actual launch start
times and builder identities. A copied artifact cannot serve as both launches. It retains real first-party
content hashes and listener/transform ordering. Only installation-specific class
source URIs and live observation coordinates/timestamps are excluded from the
portable profile comparison. All third-party and first-party content digests,
module/launch metadata, explicit callback shapes and sanitized audit digests
remain exact. This comparison creates no Profile and gives no dispatch approval.

No native census or production CUT acceptance is implied by source contracts,
mock-world tests, local compilation or two matching development profiles. Review
the actual resulting evidence and all remaining gates in `native-dirt-policy.md`
separately before proposing gameplay activation.
