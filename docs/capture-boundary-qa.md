# Enemy-core capture boundary acceptance

The boundary is presentation only. The server still uses the existing majority,
full duration, eligible players/recruits, horizontal cylinder and line-of-sight
rules. Defaults remain radius 6 and feet within ±2 blocks of the core block center.
The client's geometry comes from the server snapshot, not local config values.

## Automated gates

Run with Java 17 and the checked-in wrapper:

```sh
./gradlew --no-daemon --console=plain test --tests '*Capture*Test' --tests '*EnemyCoreTest' --tests '*RaidNetworkCompatibilityTest'
./gradlew --no-daemon --console=plain clean build
```

The focused suite covers exact cylinder edges, status versus real majority
progress, pre-capture snapshots, personalized server exclusions, malformed packet
ranges, bounded sampling/cache, clear tombstones, expiry, disconnect/dimension
replacement, collision floors/slabs, missing intermediate chunks and roof/cliff
projection. These tests do not prove native rendering or live Recruits counts.
Protocol 21 requires both client and server to update together.

## Opt-in native visual gate (seeded capture extension to Native HUD QA)

Use an isolated fresh Forge 1.20.1 / 47.4.16 client/server fixture with the pinned
Recruits, Workers, Small Ships and Siege Weapons companions in CONTRIBUTING.md.
Do not alter a player's installation/world or use live payment/claim operations.
The unshipped `NativeCaptureBoundaryQa` extends Native HUD QA with 24 named
in-world captures at compact GUI scale 3 and roomy GUI scale 1. It seeds a small
plaza, pillars, a cliff, steps and low ceiling only in the fresh isolated fixture;
no raid or claim is created. The idle approach uses a third-person overview;
all inside/blocked/height/stale frames use and record first-person view, including
the actual aiming reticle and low-ceiling floor. The real server sender/S2C handler, native player
position/mode, actual core and production renderer are exercised with explicitly
seeded ally/enemy/progress counts. Every frame is visibly labeled. The verifier
rejects geometry/status/freshness mismatches, stale dimension markers, reticle
obstruction and truncated required status using actual native-font measurements. Four seeded native boss
bars also verify the real rendered objective band remains unobstructed.

Run the existing Native HUD QA workflow for the exact source and inspect its
`*-capture-*.png` artifacts plus receipts. This fixture does not prove live battle
counts, capture victory, all graphics configurations or production performance.
No runtime result is claimed until that workflow passes and frames are reviewed.

Record source SHA, settings, actual viewport/framebuffer sizes and screenshots at
960×720 GUI scales 2/3 and 1440×960 GUI scales 1/2. In a representative walled camp,
include a low plinth with four pillars, flanking roofs, steps, a slab and a cliff:

- Approach at 0% with no allied contestants: ground boundary and radius appear
  before capture starts; the long beacon need not appear yet.
- Walk across exact radius 6; cross the feet-height limits of core Y+0.5±2.
  Read outside distance, inside/awaiting-server, counted and wrong-height reasons.
- Stand inside the circle behind a solid wall, on a roof and behind a core pillar.
  Blocked players must not get “You count”. Ground strokes must depth-occlude,
  skip out-of-height roof tops and never bridge gaps/cliffs/unloaded terrain.
- Inspect the actual capture percentages, ally/enemy totals and advancing/tied/
  outnumbered/abandoned states. Test 0%, 50%, loss of progress, 99% and completion.
- Move a survival/adventure player, creative player, spectator and dead player
  through the region. Keep allied recruits capturing while the viewer is outside
  or excluded: faction progress must not imply that viewer is counted.
- Use nondefault server radius/vertical/sight settings (for example 9/4/false)
  with differing client-local config. Geometry and text must follow the server.
- Toggle the existing Capture beacon preference and F1; leave range, remove the
  core, unload boundary chunks, change dimension, disconnect and reconnect.
  No stale ring/HUD/beam should survive the configured three-second expiry or a
  world replacement. New sessions must not inherit the previous world's cache.
- Review legibility and overlap with normal HUD/boss bars and a held-plan review.
  No fill/line may be drawn through opaque walls. Review actual pixels as well
  as receipts; a successful screenshot capture alone is not visual acceptance.

## Separate server/gameplay gate

Exercise the real server counts with live required companion entities: eligible
faction players and soldiers, passengers, unrelated factions and another camp's
raid-tagged units. Verify radius/height/sight match snapshot participation, a tie
pauses, the enemy majority and abandonment reverse progress, and a capturing
majority must remain present to finish. Verify claim removal/transfer clears the
cosmetic target and unchanged normal capture handling ends the raid. Keep this
gate separate from injected visual fixtures and from the normal regression Build.

## Performance contract

Snapshots use the existing once-per-second capture tick and the same nearby
faction audience/range. Client cache is limited to 16 targets and expires at 60
ticks. Only the nearest core within radius+12 horizontal and vertical+8 gets a
terrain ring/HUD. Projection samples exactly 128 points at most every 10 ticks;
vertical scans are capped by the validated 1..16 tolerance. It does not request
chunks, use heightmaps, edit blocks or send gameplay actions. Per-player local
sight/status is refreshed once per client tick; rendering uses cached segments.
