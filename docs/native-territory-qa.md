# Native L-territory acceptance

This is optional full-completion stress coverage, available by manual workflow dispatch when a specific risk justifies it. Focused representative gameplay and targeted regressions are the normal release approach.

The opt-in `Native Territory Perimeter QA` workflow runs one fresh integrated-server world with two real same-faction Recruits claim records forming a three-chunk L. Production free review and an actual plan-use packet must commission the exact 1,404-block perimeter for one 64-emerald payment. The native builder uses two finite chests containing exactly 864 cobblestone and 540 oak planks. No refill, forced movement, accelerated ticks or replacement goal is permitted.

For local setup, first run `python3 scripts/prepare-native-client-defaults.py --mode territory-perimeter --smallships-version 2.0.0-b1.4` in a fresh isolated checkout, before other game-directory setup. The workflow does this automatically. The separate one-chunk calibration uses `--mode perimeter-completion`. See [unchanged Small Ships defaults and strict isolation](native-client-defaults-qa.md).

The complete paid project is partitioned into one or more actual native sections. Every section must retain its predetermined area UUID and the original shovel site; there may be at most one live section at a time. The global manifest, exact L geometry and full structural/headroom reservation remain authoritative between sections. The fixture follows the returned layout and does not require either one area or an artificial minimum of two sections.

The run closes and reopens its actual world during genuine partial progress within an active section, checks native chest/cargo and complete paid project/native-recipe persistence, expands the faction's territory through a detached native claim update, and requires a 100-tick scope pause with a real permitted Survival owner. The complete project and creation journal must match their stopped snapshot at the pre-tick `ServerStartedEvent` boundary. Restoring the original claim union must rebuild only the active section's remaining native queue and resume without payment; the separately recorded global remaining set still covers every later section.

Final acceptance requires a durable `COMPLETE` full project or compact terminal covering all verified sections, actual joins for every exact native area, final world states, unchanged non-plan cells, both native claim indexes, every material count and the real completed framebuffer. Marker disappearance alone cannot pass. This L run verifies construction completion; retained full records may still represent deferred cleanup. Until the actual run passes, source adaptation or a partial run is not completed-territory evidence. The distinct 5×5 mode additionally tests a between-section world restart and records compact-terminal cleanup checks.

## Exact payment and passive-income accounting

The initial Survival transaction must still move Treasury from 2,000 to 1,936 and record exactly one negative ledger entry of 64 emeralds. Longer construction legitimately crosses the production bank's 24,000-tick interest period: at the default 100 basis points, 1,936 earns 19 emeralds with a 3,600 fractional remainder. Starter civilians can separately generate recorded taxes.

`NativeQaTreasury` observes existing NBT without writing to the bank or changing any rate, timer or configuration. Each observation validates the exact balance, unchanged rate, daily clock step, fractional remainder, bounded `CivilianTaxesTotal` change and the single negative ledger entry. When tax and interest listeners run between observations, both possible orders are checked against the exact balance **and** remainder. Extra debits, unexplained credits, malformed clocks and rate changes fail the run. No tolerance or balance reset is used.

At the real `ServerStartedEvent` load boundary, the exact saved balance, interest clock/remainder, tax counter and transaction ledger must match the final stopped snapshot before game ticks resume. Later reload checks add only passive credits that the observer has independently verified since that stop. The result records every passive credit with its before/after counters, sample observations, and the final equation:

`2,000 - 64 + verified interest + recorded civilian taxes = final Treasury`

The helper is used by the long L and 5×5 fixtures. Ten named pure arithmetic/validation contracts run before gameplay, covering unchanged input NBT, repeated interest/remainders, both listener orders, tax-only credit and rejection of unexplained gains/debits, changed remainders/clocks, duplicate fees, excess taxes and changed rates. These helper contracts are identified separately from real Forge gameplay assertions.

## Diagnosis

Failure evidence includes native state/target, storage/cargo, exact mutation candidates, registered and live builder dimensions, entity UUIDs/bounds and intersected cells. Self-clearance telemetry records the real guard-triggering AABB and target; the production recovery uses ordinary safe navigation while every mutation collision check remains active. Screenshots remain raw framebuffers. No companion JAR or world save is uploaded.
