# Complete-territory perimeter contract and native capacity

## Territory means the same-faction union

The reviewed footprint uses all claim records returned by Recruits' claim manager whose owner matches the core faction, including disconnected holdings. Adjacent records share a boundary only when the neighboring chunk is absent from the complete union. Holes retain their inward-facing perimeter. No claim record or distant section is silently omitted, and no plan is trimmed to storage range.

Source: [RecruitsClaimManager, pinned Recruits 1.15.2 source](https://github.com/talhanation/recruits/blob/cff03e085d65653406a8b6ddcdd0ebff615c3e48/src/main/java/com/talhanation/recruits/world/RecruitsClaimManager.java). `getAllClaims()` returns all `claimsById` values, whereas `getClaim(ChunkPos)` returns only the record covering one chunk.

A new perimeter stores a versioned immutable faction/chunk-set recipe before native protection, assignment or its single flat 64-emerald Treasury payment. The fee covers the whole territory, with no size-based or per-stage charge; blocks are supplied separately. Existing paid jobs and receipts are not repriced, and claim-purchase costs stay unchanged. Live work and reload require the same complete territory. Expansion or shrink pauses the existing job rather than building an obsolete internal edge, automatically expanding the quote or charging again. A restored identical union can resume, including a union repartitioned among claim records. Missing required or malformed recipes pause; unrelated legacy/manual jobs are not rewritten.

The read-only collector bounds registry records and inspected friendly chunk entries at 65,536 and unique territory chunks at 4,096. Any exceeded bound returns no partial union. Further whole-plan limits include 256 blocks on each horizontal axis, 32,768 solid preview cells, 65,536 reserved structural/headroom cells, and a 1,048,576-cell native scan envelope. These limits do not imply that a native network blueprint can contain 32,768 cells.

## Native scanning and storage

[Workers BuildArea, pinned 2.0.3 source](https://github.com/talhanation/workers/blob/29d26e1df6475fc8d043dc5d455f67b2fd1e9982/src/main/java/com/talhanation/workers/entities/workarea/BuildArea.java) calls virtual `getStateFromPos` for each bounding cell in `scanBreakArea`. Its lookup searches the current pending primary stack first, then the current pending multipart stack. The protected subclass keeps the original native scan and first-match order while making an immutable index of those pending stacks only for the synchronous scan. The index is discarded in `finally`. Normal work still reads native live queues; completed recipe cells are not reintroduced.

The guard checks the complete scan envelope, including hollow interiors and gaps, is already loaded. It does not force-load chunks. Structural mutation rights and reservations remain the accepted cells and headroom, not the empty courtyard.

[Workers AbstractChestGoal](https://github.com/talhanation/workers/blob/29d26e1df6475fc8d043dc5d455f67b2fd1e9982/src/main/java/com/talhanation/workers/entities/ai/AbstractChestGoal.java) discovers eligible storage markers in a 64-block AABB around the moving worker. A complete perimeter can therefore use multiple owned, Builders-enabled markers. Preflight requires an initial native-reachable supplier plus conservative horizontal-circle/vertical coverage for every section. Native material requests, finite supplies, pathfinding, tools and work hours remain responsible for actual progress. Coverage is not a promise that a blocked path or empty chest will work.

## The real native client synchronization ceiling

Workers stores the expanded per-block blueprint in `EntityDataSerializers.COMPOUND_TAG`. Minecraft 1.20.1's serializer uses `FriendlyByteBuf.readNbt()`, which creates `NbtAccounter(2_097_152)`. This is decoded NBT allocation accounting, not compressed network size. Audited official client JAR SHA-1: `0c3ec587af28e5a785c0b4a7b8a30f9a8f838`; official mappings SHA-1: `6c48521eed01fe2e8ecdadbd5ae348415f3c47da`.

Forge 1.20.1 also modifies that accounting:

- [CompoundTag patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/nbt/CompoundTag.java.patch): extra entry/object/type and UTF name costs.
- [NbtIo patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/nbt/NbtIo.java.patch): root type/name/object costs.
- [NbtAccounter patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/nbt/NbtAccounter.java.patch) and [StringTag patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/nbt/StringTag.java.patch): modified-UTF byte accounting.

For the current ASCII recipe, those source formulas give 416 root-accounted bytes plus 595 per cobblestone cell, 596 per stone-brick cell, 594 per oak-plank cell or 588 per dirt cell. Illustrative flat cobblestone/oak plans:

| Territory shape | Native targets | Source-calculated accounted bytes | Single native structure fits |
| --- | ---: | ---: | --- |
| One chunk | 968 | 576,156 | Yes |
| Three-chunk L, or 2×2 square | 2,376 | 1,413,596 | Yes |
| 2×3 rectangle | 3,080 | 1,832,316 | Yes |
| 3×3 square | 3,784 | 2,251,036 | No |
| 5×5 square | 6,600 | 3,925,916 | No |
| 16×16 square | 22,088 | 13,137,756 | No |

These are source-derived sizes, not completed gameplay tests. Production does not trust a hard-coded byte estimate: `BlueprintNetworkBudget` round-trips the complete recipe through the actual unchanged native serializer before payment or synchronization, including protected entity initialization/reload. It does not increase or bypass the decoder quota. Tests distinguish raw payload length from NBT-accounted size.

## Remaining larger-territory work

A faster native scan and multiple storage markers do not solve the single-structure NBT ceiling. Normal 5×5-and-larger territory needs a separately verified durable staged-native-job design (or another audited native synchronization representation). That design must preserve one full reviewed union, full-site reservations, one fee, exact phase receipts, restart-safe progress, live claim/permission protections and original native material consumption. It cannot declare completion after one stage, truncate a quote, charge per section or replace the worker with direct world writes.

Until that exists, oversized territory receives an explicit whole-plan capacity failure before payment. The existing one-chunk gameplay evidence does not prove multi-claim completion; the dedicated L-shaped multi-claim, finite-material, real-save/reopen acceptance scenario must pass separately.
