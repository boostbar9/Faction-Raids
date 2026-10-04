# Current hollow-wall native QA fixtures

These expectations apply only to fresh current-build plans. Previously accepted solid
jobs retain their saved solid targets, material costs, manifest and payment. Existing
fixed renderer/authority fixtures are not regenerated. The 6,600-entry malformed
network-budget fixture is deliberately independent of current wall geometry.

## Independent flat expectations

| Fixture | Previous solid stone + oak = targets | Current stone + oak = targets | Protected body AIR cells |
| --- | ---: | ---: | ---: |
| One 16×16 claim | 748 + 220 = 968 | 352 + 220 = 572 | 396 |
| Three-chunk L | 1,836 + 540 = 2,376 | 864 + 540 = 1,404 | 972 |
| 5×5 claim, including translated unload | 5,100 + 1,500 = 6,600 | 2,400 + 1,500 = 3,900 | 2,700 |
| Capped manual WALL / CORNER | 85 + 25 = 110 | 58 + 25 = 83 | 27 |

`NativeHollowWallOracle` calculates the flat ring from owned chunk coordinates and
Chebyshev distance to unowned columns, independently of production `Column` data.
It checks exact material target positions and the complete six-high reserved AIR
set, including convex and concave corners. Layers one and five retain three body
skin cells plus their parapet; all five layers retain the oak deck. The original
L/5×5 geometric oracles and open-internal-seam checks remain independent gates.

One-claim stock occupies ten slots. Each L chest contains 432 cobblestone and 270
oak planks in twelve slots. Each 5×5/unload chest contains 600 cobblestone and 375
oak planks in sixteen slots. Exact native cargo, equipment-mirror, owner, loose-item
and chest accounting is unchanged; there are no extra supplies or refills in these
perimeter modes. The existing manual resupply scenario retains measured finite
resupplies and its exact conservation checks.

Natural production stage counts are accepted without artificial partition pressure.
The optional long 5×5 suite always requires its partial first-section restart. It
requires the additional waiting-boundary restart only when the production layout
actually has multiple sections, and records applicability separately. The dedicated
representative handoff mode owns its explicitly synthetic 96-target partition.

## Representative manual hollow regression

The baseline real-client WALL is explicitly checked against an independent 83-target
capped SOUTH-facing oracle. At payment its actual native recipe must contain only
solid targets, and every one of the 27 body cells must appear as AIR in the saved
clearance reservation and in the live ledger. Its existing dandelion target is a
corner skin cell, so native clearing/support-persistence coverage stays meaningful.

The scenario first verifies the original top-headroom obstruction pause. It restores
that cell, inserts a raw environmental obstruction into a body cavity alone, and
requires a separate pause without structure mutation, obstruction removal or extra
payment. The cavity obstruction survives a real save/close/reopen and restored owner
permission; only restoring the original cavity AIR lets native work continue. This
is a labeled environmental fixture, not a simulated player-edit event.

Completion requires all 83 actual native-placed targets, the central nine oak deck
cells above all 27 unchanged cavity AIR cells, nine unchanged stone footing columns,
and the original material/payment checks. The receipt verifier requires these new
facts. No QA step writes finished wall blocks, speeds up ticks or invokes native
worker goals manually.

## Evidence boundaries

Cached compilation, pure oracle execution and YAML/Python parsing establish source
compatibility and fixture consistency only. A hollow gameplay pass requires a new
exact-source native run. Earlier solid-profile completion, handoff or configured
unload receipts remain labeled historical evidence and cannot satisfy that gate.
Long whole-territory completion remains optional risk-based stress coverage.
