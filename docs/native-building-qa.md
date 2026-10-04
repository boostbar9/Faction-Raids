# Opt-in native Building QA

This is a **real Minecraft 1.20.1 / Forge 47.4.16 client and integrated-server
fixture**, using all four required companion mods, with software OpenGL rendering.
It is separate from the normal Build/JUnit check. A green ordinary Build does not
prove this test ran. A green fixture does not prove the untested gameplay matrix.
The Java harness lives only in `src/nativeQa`; it is not packaged in release JARs.

## Exact dependencies

The QA invocation resolves these official CurseForge file coordinates through the
same Curse Maven repository already used for the native API compile dependencies:

| Mod | Public release file | Coordinate |
| --- | --- | --- |
| Workers 2.0.3 | [8351157](https://www.curseforge.com/minecraft/mc-mods/workers/files/8351157) | `curse.maven:workers-567450:8351157` |
| Recruits 1.15.2 | [8339846](https://www.curseforge.com/minecraft/mc-mods/recruits/files/8339846) | `curse.maven:recruits-523860:8339846` |
| Small Ships Forge 1.20.1 2.0.0-b1.4 | [5566900](https://www.curseforge.com/minecraft/mc-mods/small-ships/files/5566900) | `curse.maven:small-ships-450659:5566900` |
| Siege Weapons 1.20.1 0.2.5 | [7906096](https://www.curseforge.com/minecraft/mc-mods/siegeweapons/files/7906096) | `curse.maven:siegeweapons-1259343:7906096` |

`result.json` records actual loaded Minecraft, Forge and companion versions;
actual native class identities; and SHA-256 hashes of the **loaded, ForgeGradle
remapped development JARs**. Those hashes are deliberately not described as hashes
of original CurseForge release JARs. Neither original nor remapped vendor JARs are
uploaded as workflow artifacts. No API substitutes or test mocks are on this run's
class path. Companion release mixin refmaps are translated from SRG to the named
userdev runtime with Mixin's supported `remapRefMap`/`refMapRemappingFile` settings
and ForgeGradle's generated `build/createSrgToMcp/output.srg`. Native mixins and
injection failure checks remain enabled.

## Trigger and isolation

- Add the `native-building-qa` label to a same-repository pull request, or dispatch
  the workflow explicitly once it is available on the default branch. Removing
  the label stops future PR-triggered jobs, not a currently running job.
- The workflow uses Java 17, the checked-in Gradle wrapper and `-PnativeQa=true`.
  It does not run another `clean build` or publish anything.
- All game files are isolated under `build/native-qa/client`. The harness verifies
  that exact active directory before creating `saves/siege-native-qa-fixture`.
  A pre-existing fixture world is a hard failure; nothing is deleted or migrated.
- A newly created integrated server uses a real development client player, without
  a login, external network listener, player-world input, authentication-setting
  change or copied credentials.
- The workflow has read-only repository permissions, disables checkout credential
  persistence and Gradle cache writes, and has a 30-minute job timeout plus a
  20-minute process timeout. The harness has its own eight-minute scenario timeout.
- Mesa's actual software driver is used. There is no fake GL-version override,
  disabled renderer, image mock or disabled security check.

For an already isolated development checkout with display access:

```sh
./gradlew --no-daemon --console=plain --max-workers=2 -PnativeQa=true runClient
```

For Linux without a physical display, the workflow installs Xvfb/Mesa from Ubuntu
and runs the same command under `xvfb-run`. Its generated `options.txt` dismisses
Minecraft's first-run accessibility onboarding and sets modest rendering limits
only inside the disposable QA instance.

## First gate: actual coverage

The deterministic fixture uses a real native Workers builder with AI explicitly
paused and a new sealed `ProtectedBuildArea`. It initializes a 15-block blueprint
directly, with the shovel marker physically separate from the blueprint origin.
This is **fixture setup, not a successful paid player commissioning flow**.

Assertions and thirteen real framebuffer screenshots cover:

1. All four required companions load, and the protected type resolves to Workers'
   actual `WorkerAreaRenderer`.
2. Server/client agreement on owner, marker coordinates, accepted blueprint and
   immutable native world origin.
3. Ordinary Minecraft ray picking, as read by Recruits' real `ClientEvent`, hits
   the exposed shovel. The harness does not assign `Minecraft.hitResult`.
4. A real client interaction sends the native C2S/S2C sequence and opens the
   protected/native inspection screen. It is captured at GUI scales 2 and 3.
5. Always-visible versus focus-only projection screenshots, plus a view whose
   marker is behind the camera. The temporary culling override is observed after
   the real entity pass and must have been restored.
6. A real solid obstruction prevents native shovel ray picking. A screenshot
   captures the occluded scene.
7. Real server-side NBT round trip; calls to native creative placement, clearing,
   rotation, movement and completion setters cannot alter the sealed fixture or
   place blocks in its world.
8. Normal client disconnect closes/saves the whole integrated server. Opening the
   same fixture again must preserve the native builder, sealed contract, marker,
   native origin and ray picking, with a screenshot.
9. The actual `CoreHireScreen`/client `CoreHireMenu`, with real Minecraft fonts,
   sprites and widgets. Visible navigation controls are clicked through actual
   screen hitboxes for Auto perimeter, Place structure and Construction. The last
   view is also captured at GUI scale 3 (240px logical height). Menu faction/report
   rows explicitly say QA/sample; they are fixture values, not server claim data.
   No review, free-plan request or purchase button is activated.
10. A separate native projection uses the production `PerimeterBlueprint` planner
    and `TerritoryFortification` NBT converter for a single-chunk input shape. The
    shape is not installed as a live claim. A stable spectator fixture camera
    captures the real native perimeter, leaving the original marker assertions intact.
11. `ProtectedNativeEntityContracts` moves the real entity lifecycle assertions
    out of plain JUnit into the initialized Forge world: native packet entry
    points, all movement overloads, defensive NBT, malformed saves, queue
    reconstruction, unloaded-origin safety and unpaid/paid deletion boundaries.
    Each successful contract is separately listed in `result.json`.

Every PNG is captured from Minecraft's actual render target, must have nontrivial
pixels, and must exist with the exact expected name. A missing result, missing
companion/hash, blank framebuffer, assertion failure or timeout fails the gate.
Screenshots remain subject to visual review; nonblank pixels alone do not establish
that a projection is correctly positioned or aesthetically usable.

The evidence artifact contains `result.json`, thirteen PNGs, source commit, JVM/GL
identity, console/game logs and crash reports if present. It contains no world
save, vendor JAR or authentication file.

## Separate release acceptance still needed

The first gate deliberately reports these as **not covered**, even when it passes:

- Server-fed Building hub data, free-plan review, repeat confirm, payment failure,
  exact single charge and rollback. Test these through normal player interactions.
  Actual Building panel rendering/navigation is covered by the client-menu fixture;
  that fixture does not establish server authorization or report accuracy.
- Actual native builder goals, finite inventory, material resupply, native work
  hours, competing builders and completion. The renderer fixture builder has AI
  paused and does not establish any of these.
- A real Recruits faction/claim/core fixture, with owner online/offline, dimension
  changes, ownership/claim loss or expansion, occupied/destroyed core and recovery.
- Player edits (including same-state edits), late solid/vegetation/neighbor changes,
  unloaded marker and build chunks, server restart and missing/corrupt ledger.
- 1,024/1,025-cell projection settings, all horizontal facings/negative coordinates,
  all frustum angles, resource packs/shaders and realistic GPU-driver behavior.
- Timings at the allowed native scan-work bound and reentrant companion callbacks.
- A separately launched dedicated server and its actual client connection.

A second bounded gameplay harness should use the real connected player, Recruits'
public faction/claim manager API, actual Siege Core and production commissioning
entry points. Each test gets its own freshly created fixture world or bounded
reset region; setup may create test stock/claims, but test assertions must not
replace native goals or guard decisions. Record before/after world cells, Treasury
and inventories, and fail if mutations occur during pauses. Use separate named
results for each case; never relabel fixture initialization as end-to-end play.
For server-only native entity/ledger cases, Forge's supported GameTestServer can
supply an additional gate. It cannot replace client pixels or real-player packets.

## Verified references

- [Forge 1.20.x development runs](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/)
- [ForgeGradle 6 source sets and run configuration](https://docs.minecraftforge.net/en/fg-6.x/configuration/runs/)
- [Forge game-test behavior and exit status](https://docs.minecraftforge.net/en/1.20.x/misc/gametest/)
- [Forge 1.20.1 render/tick events](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/event/TickEvent.java)
- [Mesa software-rendering settings](https://docs.mesa3d.org/envvars.html)
- [Workers renderer, reviewed commit 29d26e1](https://github.com/talhanation/workers/blob/29d26e1df6475fc8d043dc5d455f67b2fd1e9982/src/main/java/com/talhanation/workers/client/render/WorkerAreaRenderer.java)

Implementation signatures for `WorldOpenFlows.createFreshLevel`,
`Minecraft.createWorldOpenFlows`, `Screenshot.takeScreenshot`, and world reload
were additionally checked against the official 1.20.1 Mojang mappings and client
class signatures. Actual compatibility is established only by the CI run.

## CI bring-up record

- [Run 37157686364](https://github.com/boostbar9/Faction-Raids/actions/runs/37157686364)
  compiled production and all QA sources on Java 17 and initialized actual
  llvmpipe OpenGL 4.5. It failed before world creation because Recruits' mixin
  refmap still named an SRG method in the named development runtime. No scenario
  assertion or screenshot passed in that run. The opt-in remapping configuration
  addresses that loader setup error and requires a fresh run.

- [Run 37158057809](https://github.com/boostbar9/Faction-Raids/actions/runs/37158057809)
  passed all first-gate assertions and produced thirteen real screenshots using
  Workers 2.0.3, Recruits 1.15.2, Small Ships 2.0.0-b1.4 and Siege Weapons 0.2.5.
  Artifact SHA-256: `395531862e14d3c0a6e716b8a6b5f0c7c25c49c20a5d4ae86e2606b5c0922847`.
  Pixel review confirmed native marker/projection/reload and the Building views.
  It also exposed clipped native inspection controls and an overlapping status
  line at GUI scale 3; these remain a visual acceptance issue despite the green
  automation. Construction captures had hover tooltips over the fixture rows;
  the harness now moves its actual cursor away before capturing. The harmless
  invalid simulation-distance setting was corrected from 4 to Minecraft's minimum
  5. Headless narrator/audio-device warnings mean audio has not been verified.

## Second gate: production gameplay (acceptance not yet passed)

After the first thirteen views, the same client creates a separate fresh
`Siege-native-gameplay` scenario (`siege-native-gameplay` on disk) with cheats off.
The actor must be a real non-op Survival player; the harness asserts those facts.
Fixture setup uses the public native Recruits faction/claim path and an actual
core placement, native builder, storage area and chest. The initial construction
stock is finite (eight cobblestone and eight oak planks), with tools/food supplied
separately. The test does not replace builder goals or any construction guard.

The initial bounded gameplay sequence covers:

- Free production perimeter review, with a held-plan framebuffer capture.
- Before its first commission, the idle native builder holds its existing finite
  pickaxe and undergoes native NBT save/load. Equal-valued but distinct hand and
  inventory-slot-5 objects must be observed. Production acceptance, without a QA
  repair call, must bind them exactly once while preserving every inventory value.
- Actual client plan-use confirmation, an exact 900-emerald Treasury debit, consumed
  plan, paid protected marker and a second real framebuffer capture.
- Authenticated explicit projection visibility for the large perimeter; real
  cancellation must detach the builder/retire the reservation and not refund.
  AI is paused for this transaction-only perimeter; it is not built to completion.
- Manual-wall free preview, rejected insufficient-Treasury confirmation, preserved
  item/balance/reservations, then real paid confirmation for exactly 90 emeralds.
- Repeated post-confirmation use cannot duplicate a paid job. Native AI is enabled
  for the manual wall and must place blocks using the real finite chest stock,
  stop with an actual native material request, and resume after measured resupply.
- Real claim loss and owner permission loss must produce native pause reasons and
  zero further accepted-cell changes over observed intervals, with no extra debit.
- A raw fixture solid inserted into reserved, non-structural headroom must pause
  the live native job without a single further structural mutation or removal of
  that obstruction. The obstruction is carried through the real world reload;
  queues must remain unready even after owner permission returns. Only restoring
  the original clearance allows normal resumption without another charge. This
  is an environmental regression, not an actual player BlockEvent/history test.
- Full client/server world close/reopen mid-job preserves the exact paused cells,
  paid state, reserved job and ledger identity. Native work resumes and completes
  the exact 110-block wall; chest + native builder stock + placed blocks must equal
  the measured supplied quantities for each construction material.
- The protected builder's main hand must share the native inventory slot-5 object
  after reload. The guarded rebind count must increase exactly once, with no pending
  or review flag and no change to any item/tag/count, through final completion.

Every server stage is capped at 2,400 ticks and reports current native pause,
follow-state, material-request count and placed-block count on timeout. The whole
second sequence also has the client harness's eight-minute deadline. The result
must include a passed gameplay section and all fifteen image names to pass CI.
This implementation has not passed until a linked run proves it.

The remaining actual-player-edit, competing-builder and exact
1,024/1,025-cell rendering cases remain explicitly listed as uncovered until those
additional bounded cases actually run. The claim/owner cases above are a defined
subset, not a claim to have tested every ownership/core mutation.

A separately connected dedicated server, dedicated-only distribution/classloading,
audio devices, every shader/resource pack and every GPU are evidence limits, not
an implied promise of exhaustive coverage. The integrated instance exercises the
same production server-thread handlers with the real network sender. No dedicated
server EULA acceptance, credentials or authentication changes are automated here.

- [Run 37160108311](https://github.com/boostbar9/Faction-Raids/actions/runs/37160108311)
  passed the responsive inspection controls and produced all fifteen images,
  including actual free perimeter review and an actually paid native marker.
  Production Survival payments, perimeter cancel, manual commissioning, finite
  stock/resupply, claim/owner pauses and mid-job restart passed. The manual wall
  reached its complete geometry, but strict material conservation failed with
  168 cobblestone supplied versus 198 accounted. This is a real unresolved
  acceptance failure; the assertion remains unchanged. Native slot/hand identity
  and serialized stock snapshots were added to distinguish a native reload stock
  defect from an accounting error.

- [Run 37161200249](https://github.com/boostbar9/Faction-Raids/actions/runs/37161200249)
  reproduced the same strict conservation failure and isolated its cause in the
  actual pinned native runtime. Immediately before save, inventory slot 5 and the
  main hand were the same 30-cobblestone object. After reload, both still held 30
  but were different objects. Each next placement reduced the inventory stack
  while the live hand stayed at 30. A later native material switch moved that
  stale 30-stack into cargo. Only native inventory, chest and placed blocks were
  counted, so this was not double-counting the displayed hand. The run's fifteen
  screenshots also verify the centered compact native view, unobstructed HUD
  catalogue and compiled-terrain production review capture. Its artifact SHA-256
  is `a0d33b9a940bac89abe56d998123fcfc148e686195472d101f957b2aa1425e70`.
  The focused main-hand rebind and new headroom/reload regression require their
  own successful run; the strict material assertion has not been relaxed.

- [Run 37163228609](https://github.com/boostbar9/Faction-Raids/actions/runs/37163228609)
  passed initial idle-builder handoff binding, exact once-only mid-job binding,
  unchanged builder inventory, and the live headroom obstruction/reload regression.
  Hand/slot identity remained shared and the old extra 30-stack did not recur.
  Strict conservation still failed, now at 168 supplied versus 200 accounted:
  the real chest changed from zero cobblestone before save to 32 after reload,
  while ten placed blocks and 30 builder-held cobblestone were unchanged. This
  exposes a separate native container-persistence defect; it is not an accepted
  completion. Workers' native withdrawal splits the live chest stack without a
  container dirty notification. Exact chest slot/NBT snapshots, chunk-dirty
  evidence and immediate post-reload stock equality are now required, without a
  QA dirty-mark workaround. Artifact SHA-256:
  `4ef06fcd6c247c3ae12b937f2e351271a62d17a6e9e77608750f7452f7bf7035`.
