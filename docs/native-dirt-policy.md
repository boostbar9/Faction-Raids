# Read-only native dirt policy (unregistered candidate)

This candidate adds five isolated helpers in `nativecompat`. It does **not** change
`EarthworksExecution`, its blanket CUT refusal, any callback, loot/event/gamerule,
job/manifest, tool behavior, production registration, QA source set, build, or release.
No approved runtime profile is shipped. An observed matching census is not approval.

## API and trust boundary

- `NativeDirtPolicy.Epoch`: a live-world-only resource generation. No disk codec.
  Call `beginReload()` at **beginning** of every resource reload, including failed
  reloads. Invalidation is synchronized and may occur on a reload thread. Complete
  that exact generation only on the server thread after the successful future or
  initial `ServerStarted`, using `NativeDirtCensus.completeReload(generation)`.
  It binds actual resource-manager, frozen dirt table and exact Forge GLM manager
  object identities. Ordinary player `OnDatapackSyncEvent` is not a completion signal.
  Never complete merely because the manager exists: its initial empty map precedes
  resource application. Failed/out-of-order reloads must stay invalidated.
- `NativeDirtCensus.bindRuntime(target)`: explicit off-pulse startup/commissioning
  binding after successful resource completion; hashes startup inputs once. It
  produces no CUT approval. Work inspection refuses an unbound helper or newly
  observed module/file identity before reading artifact bytes. No automatic binding
  occurs on a native work pulse. Every completed resource generation needs an
  explicit off-pulse bind (including class-origin warmup) before work inspection;
  the immutable module/artifact bytes are still reused. A changed startup module
  or file identity needs a new explicit helper lifecycle.
- `NativeDirtCensus.inspect(target)`: immutable resource, loaded-code-origin,
  inherited listener and local section evidence; an explicit current-world refusal;
  and an opaque live identity. Invoke on the owning server thread immediately before
  **each** native pulse, including partial mining, and again after the callback
  fence as required by the existing adapter. Inspection never authorizes dispatch.
- `NativeDirtPolicy.evaluate(observation, reviewedProfile, previousIdentity)`: compares the exact closed profile, live reload generation,
  actual object/listener identities and independent runtime proof. Pass the prior
  pulse identity even across settled partial mining. The pure comparison is
  package-private; exported observations are QA/reporting evidence, not stored
  permission. Target/time are bound to their live identity; later-tick reuse refuses. Reopening the world needs a
  fresh live epoch/profile check, never a restored approval boolean.

`Profile` is trusted reviewed code/configuration, not a user/world input. There is
no method that blesses a census or automatically creates a profile. It contains
an explicit `NativeDirtRuntime.Catalog` captured and independently reviewed from
the actual packaged runtime. There is no caller-supplied Boolean, opaque proof
supplier, or expected digest copied back as actual evidence.
The practical default is a **built-in reviewed profile factory**, populated only
from independently reviewed native census results. It embeds exact third-party
artifacts/modules, launch/transformation metadata and explicit callback shapes.
The containing reviewed Siege build is the first-party trust anchor: its actual
ModList ModFile, GAME Module, resolved reference and ClassLoader must match, and
only the explicit Class identities listed by `trustedFirstPartyClasses()` receive
first-party treatment. No package-prefix or claimed-module-name allowlist is used.

`Catalog.reviewedShape()` substitutes a first-party sentinel only for Siege's own
artifact/module content hash. Code comparisons likewise substitute it only for the
explicitly listed first-party callback/helper identities. Provider declarations,
version/mode, launch/transform audit and callback shapes remain exact; all third-
party content remains exact. The real local first-party file/module/class digests
and object references are still captured and retained in the live binding, so
later drift cannot reuse a prior pulse identity. This is ordinary trust in the
reviewed first-party policy code, **not self-attestation**. It avoids a circular
embedded self-hash and does not require a user-managed per-install sidecar.

A release/QA sidecar can still hold the reviewed data while developing a profile,
but is optional. The built-in factory can construct `Profile` from the reviewed
resource/listener data and `reviewedShape()` constants after actual native review.
No approved default data is invented or shipped by this candidate; actual census
and representative native acceptance still precede activation. Current observations
must never be auto-promoted to approval or loaded as approval from world NBT.

`NativeDirtRuntime` is the concrete production binding. Each inspection reads the
actual ModList IDs/versions and hashes its owning original files; enumerates all
BOOT/SERVICE/PLUGIN/GAME resolved modules and hashes complete non-JDK module
resource contents through public ModuleReader; captures the public ModLauncher
active service/plugin census; refuses pending mixin configurations; and reads
actual public transformation-audit activities for every observed implementation,
callback owner, actual ASM handler (including `$1`), and generated delegate class.
The dispatch names come from actual validated handler/delegate Class identities,
not name parsing or trust in a claimed string. Generated delegates need no invented
class-resource hash; their known factory/module inputs and actual transformation
audit are bound explicitly. It never invokes a transformer, plugin, modifier or listener
as a probe. It does not access closed ModLauncher/Mixin private modules or open them.
Service names alone confer no trust: exact containing module contents and all
provider declarations are pinned alongside the actual metadata/audit trail.

Two explicitly distinct positive profiles can be reviewed: (1) ordinary packaged
Forge 1.20.1/47.4.16 dedicated server or integrated client, and (2) the actual frozen
ForgeGradle development/native-QA layout. The read-only binding derives the mode
from FMLLoader and prefixes the launch identity with `production:` or `development:`.
Original production files and remapped development files receive distinct artifact
kinds. For a development mod whose primary input is a classes/resources directory,
it hashes the **entire resolved GAME SecureJar module**, including all combined
roots, not just the first directory. Complete resolved-module content, actual loaded
class/resource origins and relevant transformation metadata must all match that
reviewed development profile. There is no Boolean bypass and a development match
is not a packaged release validation. Missing combined-module identity refuses.

Each profile pins the exact required companion files, tested Siege contents, QA
fixture contents where applicable, Java runtime, loaded modules/services and
relevant transformations. Unknown additions or changes refuse. Mixin's internal
Config package is not exported: the provider neither opens it nor invokes reflected
getters. Any pending/unvisited mixin configuration refuses; already applied mixins
are bound by the pinned complete startup inputs and actual relevant-class audit
trail. No startup agent or mixin hot swap is supported for gameplay admission.

Reasonable trust assumptions are explicit: ordinary Java/FML/ModLauncher metadata,
immutable loaded module contents after startup, no out-of-band dynamic attachment,
no malicious in-process replacement of private metadata, and no in-place library
rewrites during the session. Original mod file size/mtime/file-key drift refuses;
module content hashes are cached per live census-helper lifetime because resolved
modules are startup inputs. Class resource/origin hashes are provenance, **not**
post-transform executable byte hashes. The actual transformation audit trail and
pinned complete module/mod inputs establish gameplay compatibility under these
normal trusted-server assumptions, not malicious-owner/JVM attestation.

Runtime IO is bounded: 32 mods, 256 module entries, 65,536 resource names per
module, 32 MiB per entry, 256 MiB per artifact/module and 1 GiB total initial reads.
Freeze development output directories for the lifetime of the native fixture; no
recompilation/hot replacement during a run is supported. Subsequent observations
use cached immutable module content hashes, recheck mod
file attributes and recapture mod/module/service/mixin/audit metadata. `NativeDirtCensus.runtimeMetrics()` reports cumulative bytes read, artifacts/modules
hashed, and cache-hit counters for QA. A 30-work-tick regression proves unchanged
byte count after initial artifact/module capture; another proves all 27 world
registry entries are re-read on each observation and a newly present registry
refuses. The explicit off-pulse initial
capture can be expensive and should run at successful load/reload admission, not
in a busy native pulse. Read failures/overruns refuse. No runtime profile is
shipped approved before independent actual census and representative native tests.

The policy covers only drop configuration and bounded `BLOCK_DESTROY` listeners.
It grants no natural provenance, stone/log clearance, claim/ownership permission,
worker/shovel approval, navigation/escape approval, inventory admission, or success
receipt. Existing exact-removal, loaded-neighbor, normal tool, drop/XP accounting,
edit, worker authority and contradiction fences remain independently mandatory.

## Bounded observations

- Only Overworld; exact vanilla dirt/default state with no existing or pending
  block entity; valid
  target build height; entire horizontal event radius within world border.
  Target state comes from the already-loaded exact chunk. Existing block-entity
  map membership and pending NBT use pinned direct-map reads, never the generic
  getBlockEntity path that can remove/promote pending NBT or create an entity.
- Nine `getChunkNow` lookups, at most 27 existing section-map entries for target
  plus/minus 16. Y keys are **not clipped to block build height**: the real dispatcher
  visits those keys and an out-of-height dynamic entity listener must still refuse.
  No chunk-loading getter and no `getListenerRegistry` cache creation is used.
- Exact native idle registry, empty main/add/remove lists; absent/NOOP is accepted.
  Unknown registry types and any nearby listener, including sculk/warden/allay,
  refuse without calling listener positions, filters or callbacks.
- Enabled `doTileDrops`, neither Forge snapshot flag; effective builtin dirt bytes
  exactly 376 bytes and SHA-256 `9222e5df0ffbb258af7ad3c42a563b432d46bdaf398bf505eccd1df89db24d25`.
- Exact frozen native loaded loot graph, including native `BlockItem` dirt, one
  `LootItem`, one constant roll, zero bonus rolls, and only `survives_explosion`.
  The native destroy context has EMPTY tool and no explosion radius. No number
  provider, condition, function, serializer or random-loot method is invoked.
- Exact private Forge manager field/descriptor; public active modifier collection
  must be empty. Up to 16 layered resource inputs of 8 KiB each. This deliberately
  conservative first policy rejects any nonempty layer, even if later replaced
  away, and strictly parses only `replace` boolean plus empty `entries` array. The sole
  additional accepted form is the exact built-in Forge 47.4.16 254-byte empty
  resource, SHA-256 `ed72002040acf4aa51ce8d92dc9591bbf423f9be9860022e36060eaabb0ca4f3`,
  which also contains a documentary `comment` string. It is recognized by complete
  bytes/hash and built-in provenance, not by broadly accepting comment/unknown keys.
  Modified comments, extra/duplicate keys and nonempty entries remain refused.
  Its original pack/built-in/length/hash ResourceProof is retained without normalization.
- Complete inherited arrays for LootTableLoad, EntityJoinLevel, EntityConstructing,
  AttachCapabilities (including generic registrations), NeighborNotify and
  VanillaGameEvent: max 256 entries each; max 512 owners and 128 registrations per
  owner. Only exact EventPriority markers are ignored. Unknown/consumer/named forms
  refuse. Exact ASM shapes must resolve to the actual factory-cache `Method` and
  registered target by identity, never listener `toString` or generated-name parsing.
  The pinned EventListenerHelper may populate Forge metadata via its known no-arg
  event constructors; it does not post an event or invoke any callback.
- Origin reads are capped at 128 classes, 1 MiB each, cached only within a live
  generation. Full graph/manager/layer/listener/section observations repeat each
  inspection. Unknown mappings/private descriptors/access or unstable census refuse.

## Exact authority inspected

Minecraft 1.20.1 official client SHA-1 `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838`
and official mappings SHA-1 `6c48521eed01fe2e8ecdadbd5ae348415f3c47da`.

| Original release | SHA-256 |
| --- | --- |
| Workers 2.0.3, Curse file 8351157 | `c7c59a7a87bf51ec5ffbadfe5340f854f92397591a52ab5af1df8bffd71d941e` |
| Recruits 1.15.2, file 8339846 | `4b53c1b752e886ba10985aad2df30d668971810230eb77867e07428a21d1af7f` |
| Small Ships 2.0.0-b1.4, file 5566900 | `f79a4dcab3e1c8e4d14467cbfa655f4a16e0f0d0901e8f9bde9e3f5e36735eff` |
| Siege Weapons 0.2.5, file 7906096 | `777359069e04159c0fedf19b1df56ff1b943d07bdcc09698b098a0ae2962b2ed` |
| Forge 47.4.16 universal | `45c5c111c2bce893369e01f67efa4a042b488f29e3910f8872fe71c848e0c1ee` |
| Forge 47.4.16 sources | `f83e705df5e25dc244e06abb185efd86f8889ceca285b2a38f5006257a72dbde` |
| EventBus 6.2.32 binary | `9feab3a39a540e3b4ce4437eb9b989d0e8d2a78484edbb43410025aa2cc49fc8` |
| EventBus 6.2.32 sources | `0df120267bd76f1d2734865c938bf3d8f9c0d51cbb1fd8d6810dcaf62b82462f` |

Sources: [Mojang version metadata](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json),
[Forge exact sources](https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.16/forge-1.20.1-47.4.16-sources.jar),
[Forge exact universal](https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.16/forge-1.20.1-47.4.16-universal.jar),
[EventBus exact sources](https://maven.minecraftforge.net/net/minecraftforge/eventbus/6.2.32/eventbus-6.2.32-sources.jar).
Private Minecraft fields use SRG lookup through ObfuscationReflectionHelper, with
owner/descriptor checks. There is no production Mojang-field-name fallback.
EventBus additional field authority comes from exact `ASMEventHandler`,
`ModLauncherFactory.PENDING`, `CacheConcurrent.map`, and `ClassLoaderFactory`
source. Missing/remapped fields or unsupported cache implementation refuse.

Actual Workers `finalizeBlockBreak` invokes `Level.destroyBlock(pos,true,worker)`,
discards its boolean result, then damages the tool. Dirt drops precede `setBlock`
success. No player BreakEvent path is involved. A refused write can leave drops
and wear without removal; it must remain fenced, never retried/refunded by this
provider or the existing controller. This source/bytecode evidence is not playtesting.

## Separate actual-runtime fixture contract and remaining gates

Do not modify the already approved one-FILL native fixture for this candidate.
The later separately opted-in native QA fixture can use the concretely hashed
development combined-module profile; packaged acceptance remains a separate gate.
Use a separately opted-in fixture/source set after independent provider review:

1. Pin exact source/JAR/runtime hashes and distinguish original/remapped artifacts.
   Capture dedicated and integrated-client profiles separately. No shipped profile
   may use synthetic unit-test evidence or assume the two listener sets are equal.
2. At successful initial load, complete the live epoch. Record Census as bounded
   JSON using ordinary field access; record the concrete original-artifact/module/launch-transformation
   catalog and review its exact contents separately. No live effects probe.
   Compare world cells, items, XP, tool, registry map sizes and RNG state before/after
   observation; no constructor-created ItemStack/ItemEntity or condition invocation.
3. Independently audit every exact callback/cache mapping and loaded origin. A
   callback name/package alone cannot be reviewed as harmless. Install the approved
   profile only once the matching native cases pass; this file supplies no approval.
4. Representative positive case: exactly one dirt cell, ordinary iron shovel,
   ordinary native cadence. Observe one dirt drop, +1 wear, zero XP and only the
   authorized block changed. Capture actual native destroy/drop call-path evidence.
5. Before first pulse: doTileDrops false; each snapshot flag; unloaded radius chunk;
   changed vanilla resource; loaded-table mutation despite original raw bytes;
   active GLM; canceling EntityJoin and superclass EntityEvent listeners; custom
   item behavior; nearby sculk/other listener; processing/pending registry; missing
   mappings. Each must refuse without invoking callbacks or altering state.
6. Reload begin/failure/success/out-of-order completion, add/remove/replace callback,
   profile drift between partial pulses, and save/reopen: retain pending intent and
   pause; no replay, manufactured drop, guessed success, compensation or second wear.
7. Separate native failure fixture: a write rejected **after** resources spawn must
   leave the existing accounting contradiction fenced. Never disable a mod/event,
   change gamerules or clear listeners to obtain positive admission.
8. Finish full regression build and independent source review before any controller
   activation proposal. No external publication or native activation is authorized
   by this local provider commit. No broad excavation or long-soak claim is made.

Unit tests construct labeled native graph/registry objects, inject a test-only
mapped field resolver and use mock worlds for boundaries. They test fail-closed
contracts and read budgets; they are not an actual runtime census/native CUT gate.

## EventBus 6.2.33 delta

The warmed development cache contains 6.2.33 (SHA-256
`c9c9aab0979e08a0489fc497ba433276648d5192608e53535bad881dfe14d712`).
Exact binary disassembly was compared against the 6.2.32 audit. Relevant
EventBus/ASMEventHandler private field layouts remain unchanged.
ModLauncherFactory, ClassLoaderFactory, EventListenerHelper and CacheConcurrent
have identical disassembly. EventBus.addListener and ASMEventHandler.of replace
the final/cancelability optimization with InternalUtils.couldBeCancelled, which
also analyzes permitted sealed subclasses. The normal and optimized ASM callback
shapes remain the same and are both recognized. This supports a separately
reviewed 6.2.33 profile; it is not proof that native CI or a packaged installation
actually loads that version. The actual module contents/class origins and listener
census identify it. No dependency pin or security/module-opening change is made.

Reviewed profiles compare logical class/module identity and exact content hashes,
not installation paths. Complete runtime artifact/module/transform catalogs remain
required. Every relevant actual Class (including dispatch wrappers) must belong to the exact
loaded Module, resolved ModuleReference and ClassLoader captured by startup
binding. These object references and absolute origins are retained in the live identity:
a different local origin cannot silently reuse a prior pulse binding. The same
approved logical profile may initialize a fresh binding in an identical installation
at another path; different module names, bytes or transforms still refuse.

Repeat clean native launches to establish whether same-priority listener order and
launcher service metadata are stable before installing an order-sensitive profile.

Census export safety: no JVM arguments or environment dumps are retained. Argument
count/length are bounded before agent detection. Launcher records accept only the
pinned name/type/file schema and bounded identifier/filename values; unknown keys
or values refuse without echoing them. Code-source URIs accept only local file,
union or nested local-jar sources, and reject user-info/query/fragment components
without exporting the rejected URI. Secret-like metadata/argument regressions cover
these paths.

Transformation activity exports only actual owner/kind and ordered SHA-256 context
identities over length-framed UTF-16 code units (including unpaired surrogates). Arbitrary plugin audit labels and transformer labels are never exported
raw; order/contents still participate in exact profile matching. Total context IO
is capped at 256 KiB and 2,048 activities per observation, beyond per-class/string
bounds. The secret-like transformation-label regression verifies this channel too.

The raw bounded transformation context is retained only in the opaque in-process
pulse identity and compared exactly for local drift; it is not a field of Catalog
or Census. Export only the explicit Census DTO, never Observation/Identity, the
runtime reader, or their private fields. Thus sanitized evidence retains exact
profile digests while local revalidation still compares the actual raw context.

## Separately opted-in evidence observer

The approved dormant provider now exposes immutable section-coordinate/status,
loaded-loot check and active-modifier count fields on its Census DTO. This does
not change admission or install a profile. The default-off
[native development census observer](native-dirt-census-qa.md) appends read-only
evidence capture after the existing one-FILL fixture has completed its original
stable-window assertions. That separate observer requires independent source
review before native runs and two clean-launch evidence comparison afterward.
Production CUT refusal remains untouched.

## Native empty-layer correction provenance

The first read-only native attempt (run 37304902294, combined tree
`15aa6f034c2f94ffd49085b1182bc82e95a6ae6e`) preserved genuine one-FILL success
and unchanged bounded world/RNG/inventory/queue/receipt state, but its off-pulse
binding refused the strict resource-layer shape before any startup bytes were
hashed. That refused run is not a successful-profile capture or a reproducibility
pair member. Inspection of the already pinned official Forge universal JAR
`45c5c111c2bce893369e01f67efa4a042b488f29e3910f8872fe71c848e0c1ee`
found its built-in `data/forge/loot_modifiers/global_loot_modifiers.json` includes
that documentary comment alongside `replace:false` and `entries:[]`.
The exact bytes are preserved only as a test resource. This correction recognizes
only that immutable built-in empty-resource hash in addition to the original
strict two-key parser; it changes no active-modifier refusal, graph/listener
admission, runtime profile requirement or controller dispatch.

`bindRuntimeCensus` performs the same explicit off-pulse binding and returns its
safe Census DTO, including partial observed resource/graph/GLM/section evidence
on refusal. It never exports a live Identity. The original `bindRuntime` Check API
continues to delegate to that same operation. QA exports both the actual binding
Census and subsequent fresh Census values; no omitted diagnostic is replaced
with an inferred resource or an approved profile.

The optional QA exporter represents pack-name identities by bounded, length-framed
UTF-16 SHA-256 values in reporting-only ResourceProof copies, so unusual pack names
never leak raw plugin text. It retains the exact private provider Census for
evaluation. This reporting encoding does not change this provider's admission or
its native profile comparisons. QA fixed phase codes and actual post-state flags
separately distinguish read refusal, export failure and incomplete no-effects
observations; no missing state is assumed unchanged.
