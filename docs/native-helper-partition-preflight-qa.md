# Native cap-64 helper partition preflight

This separately opted-in check starts the real Java 17 / Minecraft 1.20.1 / Forge
47.4.16 client and runs only at the title screen. It creates no world, integrated
server, worker, marker, project, ticket, inventory or payment. It is a prerequisite
for the separately reviewed later-stage helper scenario, not Case L gameplay.
The accepted same-stage/disk and canceled-while-absent modes are unchanged.

## Exact scope

The synthetic in-memory input is the existing one-claim flat plan: claim `(8,0)`,
base Y 65, 220 columns, 572 targets (352 cobblestone and 220 oak planks), and 748
clearance cells. Required material totals are observed, not provisioned. The
production 64-emerald fee is read and checked, never charged.

The driver executes production `PerimeterBlueprint.create`,
`TerritoryFortification.blueprint` and `PerimeterStageLayout.partition`. Every
one of the 46 proposed sections first passes the unchanged
`BlueprintNetworkBudget.problem` native COMPOUND_TAG write/read equality and
unread-byte check. A native rejection is fatal; it cannot select a different
partition. Only then does the fixed QA-only cap of 64 targets apply.

All 16 final sections, their ordered columns, target/material maps, clearance,
bounds, counts, raw digests, and every validator call must equal the immutable
reviewed resource:

- `src/nativeQa/resources/native-helper-partition/cap64-candidate-partition.json`
- SHA-256 `64d4deeadebea0808cf38cbca69ebf72b68b3e2ed089958e3a41d1a37a15a115`
- Layout digest `1f25c389bc6c477295a33c74a73648f5aed71f64ab16d3da1dde5425b1837fe8`
- First section target counts: 30, 35, 30

The resource was deterministically regenerated after a local workspace rollback.
The old unpublished document bytes were unavailable; this is a new artifact with
fresh review provenance, not a claimed restoration of the old document hash.
Production validation and the independent oracle verify the lossless,
nonoverlapping 1,320-cell reservation union. These are raw layout digests, not
paid project contract hashes or authority receipts.

## Isolation and evidence

`.github/workflows/native-helper-partition-preflight-qa.yml` runs only for the
same-repository alpha PR 295 with label `native-helper-partition-preflight-qa`.
It has read-only repository permission, no release credentials, and no manual
route that bypasses the opt-in restriction.

Use the fresh defaults helper with `--mode helper-partition-preflight` before any
other game-directory setup. The isolated directory is
`build/native-helper-partition-qa/client`. Unknown selectors and combined
helper-gameplay flags fail closed. Preparation with `nativeActiveHelperApiQa`
still prohibits client launch. The subsequent title-screen invocation rechecks
the unchanged reviewed API gate before launching, then requires actual loaded
provider/raw-resource parity.

The actual production COMPOUND_TAG instance must retain its identity throughout
the sequence and have the expected serializer interface, concrete implementation
and defining loader. Resolved public instance write/read methods, their owners,
return types and bridge/synthetic flags are recorded without guessing an anonymous
class name. Erased and CompoundTag-compatible covariant read returns are allowed.
The implementation, interface, method owners and NBT/buffer resources are matched
to unique bounded class entries in that same exact Forge supplier JAR. This
attests providers and raw class resources; it does not inspect transformed
runtime method bodies.

The fixture and outer client command retain the 600-second ceiling. An absent or
canonical empty saves container is permitted after client startup; any entry,
symlink or non-directory fails the no-world check. The result records source/tree
identities, pin and API-gate identity, loaded parity, serializer resources, every
native roundtrip, complete partition and explicit world/server/worker/commission/
payment/work absence. The strict oracle replays the API gate, checks independent
geometry and pins, and rejects missing or contradictory evidence. Text evidence
and logs are uploaded; JARs, worlds and configuration are excluded.

## Validation limits

Local executed tests compile the actual production partition and QA decision
boundary with explicitly labeled coordinate/plan stubs using source/target 17.
Executed reflection tests cover inherited/covariant/bridge positives and wrong
type, loader, method and instance negatives. Tests of the unchanged budget wrapper
use labeled codec/buffer stubs to reject NBT mismatch, trailing bytes and encoder
errors; those tests do not execute the native serializer.

The separately reviewed hosted preflight must execute successfully before any
Case L gameplay launch. Even a passing preflight establishes no later-stage timing
window, native completion, disk absence, A2 return, historical cleanup, resupply
or cancellation. Case L gameplay, ordinary whole-site eviction, sleep, post-DONE
behavior and full perimeter completion remain separate gates.
