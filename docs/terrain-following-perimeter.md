# Terrain-following perimeter: local profile checkpoint

## Status and scope

`PerimeterSteppedProfile` is an isolated pure height-feasibility proposal. It has no production
callers, block blueprint, serializer, world reads, native execution, UI changes or payment path.
Every proposal reports `executable() == false`. A feasible height assignment is not a claim that
Minecraft collision, a native builder route, claim topology or an excavation operation is safe.

Released version-one jobs retain their exact saved geometry, hashes, payment and receipt identity.
Do not reload/recompile them through this planner. The gated version-two envelope is an unshipped
draft: adapting that envelope is preferable to maintaining a permanent compatibility branch for
an unshipped flat-gate format. Its final geometry/state contract still needs an explicit version
and fresh quote. No existing execution/payment/protection barrier is removed by this checkpoint.

## Source feasibility findings

The current `PerimeterBlueprint` uses a single base/deck level per claim component, with a
five-wide footprint, two body skins, an oak deck three blocks above base, and a three-wide walk.
The draft gate contract additionally validates one level per component. Neither is an executable
container for varying-height geometry without a deliberate new contract.

`DefenseBlueprint` already describes full-block Wall Stairs, but those are not slab or stair-block
states and are not evidence for a new half-step transition. The pinned Workers source review in
`docs/upstream-compatibility.md` identifies Workers 2.0.3 commit
`29d26e1df6475fc8d043dc5d455f67b2fd1e9982`, native queues, material requests and level-by-level
placement. It does not certify new slab collision, worker traversal or transient build safety.

A possible one-block level change could use an explicitly placed half-height walking row and
raise the side rails alongside it. Merely raising neighboring full-block deck columns produces a
jump-only ledge; merely adding a half-step without changing the low rail can leave a walkable edge
out of the parapet. Both skins, the three lanes, rail height, two-block entity headroom, foundations
and transient native work access must be verified together. This profile does not choose a slab,
stair block, orientation, target state or physical transition implementation.

## Mathematical input and output

Input consists of explicitly ordered closed loops, identified by component and loop ID. The
caller must derive those loops from the complete fresh claim topology: one outer loop per true
edge-connected component, additional hole loops as needed. This class does not infer outer space
from a missing neighbor or silently turn a hole edge into a gate.

Each band has:

- A stable ID, a short length of 3–8 blocks, and straight/corner/gate kind
- An exact five-wide rectangular wall footprint; corners are complete 5-by-5 squares
- A verified first-air surface-height sample for every footprint column
- For gates, complete immediately adjacent 3-by-3 inside/outside pad samples, with one shared
  tangent alignment and a north/east/south/west facing
- An explicit indication of whether its outgoing straight-to-straight seam can potentially host
  a later verified transition

Each closed-loop seam supplies exactly five adjacent lane pairs, each crossing one block in one
cardinal direction. The lane rows must be contiguous and belong to the respective wall bands.
All level and stepped seams are retained in the result, not just the changed joins. This metadata
is necessary for the later physical compiler but does not replace that compiler's checks of
claim boundaries, correct skins, continuous walk cells, corners and natural terrain.

Mutable sample hints permit only the configured local cut/fill depth. Untouched samples pin the
band exactly to the observed height; outside gate pads must remain untouched at this checkpoint.
They never acquire outside-claim grading rights. Shared untouched pad columns must have the same
role, component and height. Mutable overlap requires an explicitly merged landing group and is
rejected rather than assigned conflicting work.

The result contains immutable per-band footprints/target levels, a deduplicated list of exact
column target levels, all seam endpoints/levels and explicit pending one-block transition
requirements. These are the local-region inputs to a separate exact grading manifest. Surface
heights and `gradingAllowed` hints are not before-state snapshots, claim permission, native
mining support or material receipts.

## Selection rules

The dynamic program solves each complete closed loop, including the closing seam. Its objective
is minimum summed cut/fill volume, then the fewest pending elevation transitions. Stable band IDs
choose the loop start; sorted component/loop/sample order and stable predecessor ties make labels
deterministic under input iteration changes and loop rotation.

- Adjacent eligible straight bands differ by at most one block
- Corner or gate joins remain level; transitions do not intrude into those protected groups
- Each component requires four distinct cardinal gate bands on its outer loop by default
- Conflicting pinned gate/corner levels and an impossible closing seam reject the whole proposal
- No component-maximum fallback, omitted band, partial loop or silent long-range grade is produced
- Every selected column remains within its own explicit cut/fill interval
- Aggregate cut and fill budgets reject the result atomically

The objective is geometric grading volume, not Minecraft item cost: physical targets and actual
mining/fill receipts must determine the eventual material bill. If the minimum-grading result
exceeds a separate cut or fill cap, this bounded checkpoint requests another route; it does not
perform an unbounded multi-budget search or claim that every possible alternative is impossible.

## Explicit complexity bounds

Defaults allow at most 4,096 bands, 20,480 supplied samples, four cut blocks and eight fill blocks
per mutable column, 8,192 aggregate cut cells and 8,192 aggregate fill cells. First-air levels are
bounded to the configured build interval. Every band's height domain has at most 13 candidates.

For a loop, the solver tries at most 13 starting heights and at most three predecessor heights
for each candidate at every band. A shared hard cap of 4,000,000 transition evaluations applies
across all loops, including closing edges. Exceeding it returns no partial geometry. Domain costs
require at most 13 passes over the bounded samples. Saved parent indices are bounded by bands
multiplied by 13; the algorithm does not retain an exponential path tree. Outputs are capped by
the input band/sample bounds.

## Validation and remaining blockers

JUnit regressions are ready for CI: local terrain versus component-maximum supports, level gate
pins, conflicting pads, closing cliffs, complete seams/footprints, disconnected components and
inner loops, no outside-pad grading, cardinal coverage, deterministic iteration/rotation,
immutable output, work/cut/fill budgets, and comparison against an independent brute-force oracle.

The cloud has a Java 21 runtime with the `jdk.compiler` module, but no Java 17 toolchain,
`javac` executable or cached Gradle distribution. The wrapper download from services.gradle.org
is unavailable. As a supplemental check only, the pure class and synthetic fixture were compiled
with the Java 21 compiler module using Java 17 source/target syntax, then exercised on Java 21.
A smoke runner checked the representative profile, conflicting pins, closing seam, independent
loops, and 128 brute-force comparisons (65 feasible, 63 correctly rejected). This is not the
Java 17 JUnit/Forge Build, Minecraft collision testing or native Workers QA.

Remote publication of the preceding gate checkpoint is blocked by an unrelated read-only task
scope in tool review. No alternative publication route is used. Required next gates are an
independent source review, exact-head Java 17 Build, and focused actual collision/Workers tests.
Source-level block/transition compilation may proceed locally behind the existing disabled live
acceptance boundary, but no transition can be called proven or shipped until those checks pass.

The native matrix must include both ascent and descent on all three walk lanes, corner/gate
landings, adequate headroom and side rails, continuous skins, bounded foundations, actual builder
arrival/work access, incomplete transition construction, storage/sleep interruptions, unloads,
changed footing/fluids, retries/reloads and unchanged materials/payment accounting. Preview
only reviewed cuts/fills and selected gate pads; preserve completed player blocks throughout.
