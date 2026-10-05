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
`bindRuntimeCensus` explicitly off-pulse, captures state again, and performs two fresh
same-thread `inspect` calls with another state capture after each. A separate new
census helper then performs a second full off-pulse binding between its own
before/after world snapshots. It re-reads bytes with separate zero-start counters;
exact private Census, module-reference identity, effective lookup catalog, content
digests, and captured backing metadata must match the original binding. This is
a same-launch frozen-input seal, not a cached bind or mutable-input validation. Each read
window attempts its post-state exactly once even if the read throws; those
attempts happen before metadata export. Explicit booleans record which post-states
were actually captured, and incomplete evidence cannot claim a successful census. The provider's
read counters prove that those fresh inspections reuse startup artifact/module
bytes while still rechecking actual runtime metadata and all 27 registry entries.
The actual partial binding Census is retained even if commissioning refuses;
its original resource/graph/GLM observations are never substituted with a later
unbound inspection. Each exported Census includes exact dispatcher coordinates and closed registry
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

## Pinned SecureJar API and frozen inputs

SecureJar 2.1.10's `JarModuleReader.list()` returns null. Null is never accepted
as an empty module. The fallback requires the exact reference/reader classes and
pins seven implementation class-resource hashes to the official
`cpw.mods:securejarhandler:2.1.10` JAR (SHA-256
`7ec9207f47bc8847a16566b9a3b4ced8d073c49592897eb30ce2aaf643fdfcab`).
It resolves the existing union root from `ModuleReference.location()`; it neither
mounts a substitute filesystem nor uses `getPrimaryPath()`.

The pinned union provider forwards a caller filter to backing directory streams
before filtering, deduplication and eager allocation. The census uses that public
filter to collect bounded raw candidates, returning false to avoid the eager
result set. It traverses raw directories, including a directory hidden by a
non-prefix filter whose descendant remains visible. Actual `reader.find/open`
selects overlay and multi-release resources. Physical names, logical aliases,
absent lookups, selected relative paths and selected bytes enter the digest.
Unusual multi-release version spellings refuse rather than assume standard JAR
selection: this pinned implementation's lookup semantics differ at the current
Java version boundary.

Default and JDK ZIP backing paths are inspected using their real NOFOLLOW
attributes, including ancestors and the archive container. Union NOFOLLOW and
`toRealPath()` are not used to claim symlink safety. Symlinks, special files,
unknown/nested providers, ambiguous paths and exceeded bounds refuse. Each scan
allows at most 65,536 raw entries and logical names, 128 path components,
4,096 code units per name/path, and 16 Mi code units of accumulated raw names.
Metadata/topology/lookup snapshots must agree before and after each module read;
retained runtime input metadata is capped at 262,144 entries. Content limits stay
32 MiB per entry, 256 MiB per module/artifact and 1 GiB per fresh helper, so the
independent sealing helper has its own explicitly separate 1 GiB bound.

These checks retain the existing ordinary trusted-launcher, frozen-module,
frozen-development-output contract. Hashing the known Jar/provider class resources
does not inspect or independently prove the reference's private provider delegate.
No malicious custom SecureJar delegate or adversarial metadata-preservation claim
is made. Cache keys use actual reference identity; cache hits do **not** revalidate
same-reference mutable directory/container topology. Such freshness remains an
open gate before a reusable production profile or CUT activation. No rebuild,
hot replacement or concurrent runtime-input edit is permitted during either QA
process. Two independent byte-reading seals and their private metadata agreement
are bounded evidence for this frozen fixture, not cryptographic attestation.

Before world capture, eleven small real-SecureJar self-tests run on fresh QA temp
inputs in the existing opted-in Forge context. No module or class from those
inputs is activated. They cover combined roots and an unchanged fresh seal,
overlay winners and masked backing edits, hidden-directory descendants,
version-only and boundary MR lookup, unusual versions, pre-allocation bounds,
backslash ambiguity, entry/root symlinks, removed roots, and ZIP/directory merging.
Their fixed case/status receipt is required separately by the census verifier.
They are synthetic filesystem evidence, never part of the world no-effects proof.
No JUnit module opens or launch/security flags are added for them.

## Bounded no-effects evidence

The server thread cannot advance its own ordinary tick while this synchronous
observation runs. All six snapshots retain private exact equality and export
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
values are never serialized. The export is limited to four Census DTOs (binding, two fresh inspections, and independent input seal) and
explicit safe maps, at most 100,000 JSON nodes, depth 16, 4,096 characters per
string and 2 MB total. Resource pack names are represented only by SHA-256 over a four-byte
big-endian code-unit count followed by the original Java UTF-16 code units,
including unpaired surrogates. Names are bounded at 4,096 code units before
framing. The reporting-only Census copy marks this encoding explicitly and
preserves each resource's original content hash/byte count/built-in flag.
The original exact Census/Observation stays private and is used for policy
comparison; encoded reporting data is never evaluated or promoted to a Profile. The provider already rejects private URI components and
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
files cannot pass. Exception messages are never copied into the receipt. Fixed phase codes identify
preflight, binding, each inspection, post-state and reporting stages in diagnostic
sidecars; the outer receipt also distinguishes capture, payload bounds and output
write failures. A successfully written refusal sidecar can carry its digest, but
its refusal status remains independent of genuine one-FILL success. The
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
