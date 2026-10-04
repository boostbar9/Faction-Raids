# Configured real-client staged chunk-unload acceptance

This QA-only scenario starts a real paid staged perimeter, observes native placements,
lets the owner's ordinary departure unload it, verifies the unavailable server report,
returns to the same native entities, proves placement resumes, and cancels normally.
The outcome is **unloaded pause/resume/cancel**, never full completion.

This is the explicitly configured case **RecruitsChunkLoading=false** in a fresh,
isolated QA world. The pinned Recruits default is true, and its inherited native worker
chunk loader otherwise keeps the worker's chunk loaded. This result does not establish
unloading under the default configuration. The harness writes only its own
`build/native-unload-qa/client/defaultconfigs/recruits-server.toml` before fresh-world
creation, refuses conflicting existing fixture configuration, and asserts the actual
loaded native BooleanValue is false before any fixture NPC or worker is spawned.
It does not change any other game directory, existing world or active acceptance run.

Run with Java 17 and all four pinned companion mods through the opt-in source set:

```sh
./gradlew --no-daemon --console=plain --max-workers=2 -PnativeQa=true -PnativeQaMode=staged-unload runClient
```

The new `Native Staged Unload QA (Chunk Loading Off)` workflow is manually dispatched or enabled by the
same-repository PR label `native-staged-unload-qa`. Repository permissions are read-only;
there are no publishing steps, secrets, vendor JAR uploads or uploaded worlds. Do not
start this process alongside another native acceptance client on the same host.

The only allowed game directory is `build/native-unload-qa/client`. An existing
`saves/siege-native-staged-unload` is rejected. The scenario has a hard 600-second
wall-clock cap from its first client tick, including creation, departure, loading,
permission pause, resumed work and cancellation. A separate preparation step allows at most 15 minutes for `prepareNativeQaClient`
without launching Minecraft. It depends on `nativeQaClasses prepareRunClient`, then resolves the exact ForgeGradle run task's
Minecraft/runtime artifact collections and source-set runtime classpaths. This fetches/remaps runtime-only companions such as
Small Ships and Siege Weapons, which `prepareRunClient` alone does not resolve. The subsequent `--offline runClient`
uses the same native QA mode and Gradle cache. Its
process is independently terminated at 10 minutes, with a 10-second TERM-to-KILL grace;
this includes Gradle launch overhead and can therefore stop before the internal cap.
A hung client does not receive the preparation budget. Failure to obtain a
closed lifecycle in time is a failed acceptance, not permission to change native speed,
reset goals, inject materials or clear recovery evidence.

## Fixture and exact boundaries

- `NativeStagedUnloadFixture` copies the full staged 5×5 geometry translated +2048
  blocks on X: territory chunks 136–140 × 0–4; fixture apron 135–141 × −1–5.
  It keeps 3900 initially empty target cells, 2400 cobblestone, 1500 oak planks,
  four single chests and original Workers goals. Natural production partitioning
  may yield one or more sections; this unload scenario never forces a stage count. The translation keeps ordinary
  vanilla spawn chunks out of the unload experiment. Native chunk loading is disabled
  through the fresh fixture's supported server configuration; no ticket APIs or spawn
  changes are used. The bounded initial setup reads/generates the apron before review.
- The real cheats-off non-op Survival owner receives a production free review plan.
  Actual client item use admits the full staged manifest and charges exactly one
  64-emerald fee. This mode does not claim to test the initial review menu controls.
- After actual native placement, every ordinary server END tick must observe at
  least 32 carried cobble, empty neededItems, no active item use, null
  `ProtectedStorageAccess.runningProblem`, an empty cleanup journal, and the original
  `BuilderWorkGoal` in PLACE_BLOCKS. All conditions must remain true for 40 consecutive
  ticks and be rechecked immediately before departure. This is read-only observation.
- Only the owner teleports 2048 blocks farther east. Survival stays active. The builder
  gets no movement, speed, inventory or goal changes. Departure may legitimately allow
  further placements before chunk unload; their count is recorded after reload and
  is not falsely required to freeze at the teleport instant.
- From departure until the full apron is loaded on return, the harness never calls
  getBlockState, getBlockEntity or getChunk at the site. It uses only `hasChunk`, loaded
  entity lookups, native entity inventory observations and saved authority. All 49
  apron chunks must become unavailable within 90 ordinary game seconds, and the
  original builder and marker must disappear. Forge's EntityLeaveLevelEvent means
  tracking ended; it can fire with a null removal reason before native entity storage
  runs. The harness retains that exact worker object read-only and immediately captures
  the tracking-end inventory. It then waits at most 200 ordinary ticks for the object's
  actual UNLOADED_TO_CHUNK reason while all unavailable-site read restrictions remain.
  A null reason is never accepted as unload. The confirmed-unload inventory and cleanup
  values must exactly match tracking-end values; any other removal reason fails.
  In the pinned runtime, native entity NBT serialization precedes setting this reason.
  This establishes that serialization was invoked, not a completed filesystem flush;
  the later genuine disk-loaded rejoin and exact value comparison establish persistence.
- For at least 100 ordinary ticks after confirmed unload, all chunks and entities must
  remain unavailable. The exact immutable full project, active stage, payment,
  creation journal and reservation remain unchanged. At most one controller settling update may change revision by exactly +1 and set
  the exact expected builder-load blocker, within 40 ticks of confirmed unload. Once
  settled, the entire saved project and journal must remain exact for a full 100 ticks;
  a later blocker or revision change fails rather than resetting the baseline.
  `ConstructionReport.snapshot` must report the original builder load blocker and
  unknown progress. This is server report evidence; no remote core menu is rendered.
- Return uses spectator solely as the existing permission pause. Both original entity
  IDs must rejoin from disk. Separate tracking-end, confirmed-unload and join snapshots
  compare every native cargo slot and both hands across serialization/load, with the
  join snapshot captured before production wrapping. The early tracking callback is
  not treated as the final serialized state without the intervening equality proof.
  The immutable recipe, owner/context and reservation fields must stay exact. Mutable
  Completed/Cleared arrays are checked separately: every returned completed entry must
  be unique, belong to the accepted active stage, match its exact returned target state,
  and retain every departure receipt. This initially empty fixture must have no cleared
  entries. Legitimate additional departure placements may add completed receipts.
  Every previously placed target stays intact, every target is either its original
  empty state or exact accepted material, all non-plan cells remain unchanged, and
  world + four chests + native cargo/hands + player + loose items conserve each exact
  finite material total. Loaded geometry stays fixed for another 40 ordinary ticks.
- Any retained cleanup journal after genuine unload fails closed. The result retains
  the raw cleanup and inventory evidence for REVIEW. QA never drains callbacks,
  clears CLEANUP/REVIEW, repairs mirrors or rewrites inventory to manufacture a pass.
- Survival is restored; at least one additional original native placement must happen
  without another fee or plan. A real local core-use packet opens an authenticated
  menu, and the normal client CANCEL packet cancels the whole project. Success needs
  a durable compact CANCELED receipt, retired reservations, absent marker, detached
  original builder, empty cleanup journal, exact stock conservation and 40 more ticks
  with no placements. Ordinary production cancellation owns all cleanup.

## Evidence and remaining limits

`build/native-unload-qa/evidence/result.json` records the gate, exact native unload/load
inventory values and tracking-to-final-unload timing, controller report, stage/payment identity, departure-placement delta,
resumed placement, stock/Treasury accounting and compact cancellation receipt. The
single unedited framebuffer is `01-returned-permission-pause.png`, captured after the
owner returns to the partial construction. Companion version/SHA-256 receipts identify
the actual remapped development runtime; the existing bytecode audit includes the
loaded native classes without uploading vendor binaries.

Syntax, YAML and independent geometry checks are preparation evidence only. They do
not prove Forge integration or gameplay. Run this scenario only after source review.
The physical dedicated-server GameTest's existing unloaded-blueprint probe tests
reconstruction of an already-unavailable area; its NO_SERVICES owner environment does
not honestly exercise a live staged owner's scheduler and is not a substitute here.
This configured mode does not prove default-enabled native chunk unloading, full completion, later-stage scheduling, a remote menu,
dedicated-client networking, disconnected-owner handling, varied terrain, other
palettes or recovery of an intentionally open native inventory lifecycle.

Earlier solid-profile configured-unload receipts remain valid only as labeled lifecycle evidence for their recorded source and 6,600-target geometry. They are not a hollow-profile acceptance result.
