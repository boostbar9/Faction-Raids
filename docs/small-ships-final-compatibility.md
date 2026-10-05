# Small Ships final 2.0.0 compatibility

Status: exact-runtime reproduction and an unshipped, bounded turn-adapter prototype; gameplay compatibility is not yet established.

## Exact baseline

- Minecraft 1.20.1; native probe Forge 47.4.10 (user target); ordinary Build remains at the repository pin.
- [Small Ships final 2.0.0 official file 9061578](https://www.curseforge.com/minecraft/mc-mods/small-ships/files/9061578), SHA-256 `fa9dbc2332b79d16c97075b19c3e6a23d6b3e3cd8455ab1419f2666d85407add`.
- [Villager Recruits 1.15.2 official file 8339846](https://www.curseforge.com/minecraft/mc-mods/recruits/files/8339846), SHA-256 `4b53c1b752e886ba10985aad2df30d668971810230eb77867e07428a21d1af7f`.
- Source comparison: [Small Ships 1.20.1 commit 13b7447f18a26a1313aebcd1ce121760337811f5](https://github.com/talhanation/smallships/tree/13b7447f18a26a1313aebcd1ce121760337811f5).
- Legacy comparator: Small Ships `2.0.0-b1.4`, official file 5566900. Existing native building suites retain this pin and their captured defaults.

## Confirmed static blockers

Recruits' version comparator reports final `2.0.0` below its minimum `2.0.0-b1.4`. Its native captain check assumes passenger index zero; final Small Ships assigns an explicit DRIVER seat and ordinary passengers can board first. Final Ship's control flags require a controlling passenger, while its controlling-passenger getter recognizes the player in that seat. Recruits has no Small Ships mixin to reconcile these changes. Its rotation path directly changes yaw, whereas final ships use collision-constrained turning. Enabling the compatibility flag alone is therefore not a fix.

Siege's current convoy uses direct delta movement/yaw, and its fixed two-air-block clearance is not the final ship's multipart hull/mast envelope. The existing registry IDs `smallships:cog` and `smallships:brigg` remain valid.

## Reproducible native probe

The new, opt-in `Native Small Ships Compatibility Probe` runs the final and legacy artifacts separately. It uses real Cog/Brigg entities and Recruits captains with both boarding orders, waits for actual native ticks, preserves dockyard refusal, and records native seat/driver/API evidence plus an entity-and-passenger NBT round trip. It never edits the companion JARs or flips compatibility flags. Its result is `probe-completed`, with compatibility explicitly `unverified`; a successful probe is not an assertion that naval gameplay is fixed.

Commands (fresh checkout/build per invocation):

```
./gradlew --no-daemon --console=plain -Pforge_version=47.4.10 -PnativeShipsQa=true -PnativeShipsRelease=final runGameTestServer
./gradlew --no-daemon --console=plain -Pforge_version=47.4.10 -PnativeShipsQa=true -PnativeShipsRelease=legacy runGameTestServer
```

Production output excludes this source set. CI uploads results, actual runtime versions, original/remapped artifact hashes, bytecode and logs, never companion JARs or worlds. The GameTest server does not create an EULA acceptance file.

## Minimal bridge sequence

1. Establish exact-runtime observations before changing compatibility gates.
2. Keep the legacy branch behavior separate from final's seat/driver capabilities. Honor native boarding refusal, occupancy, sinking, docking and locking.
3. Replace Siege-owned convoy movement with a tested native-compatible control path, without rewriting player ships or bypassing multipart collisions. Use the real hull/mast envelope for spawning.
4. Reconcile captain seat order and controls only with a tested public bridge or bounded addon integration. Do not patch vendor JARs or claim that a boolean enables support.
5. Verify actual client and server behavior, safe landing, legacy/mod-absence helper fallback, ownership boundaries and real reload recovery before release.

Dockyards, new hulls, ammunition and expanded fleets are outside this first compatibility task. The unrelated ATL 1.1.3 obsolete ChunkMap hook is not repaired or bypassed here. No arbitrary-modpack compatibility claim is made.

## QA-only turn adapter prototype

The final-adapter matrix entry tests an unshipped, exact-version-gated runtime addon. It recognizes the native DRIVER occupant and feeds short-lived left/right requests into native `controlShip`; it never writes yaw, rotation speed, velocity, or the shared controlling-passenger result. Native collision/wind/sail handling remains the physics owner. Receipts expire after the current/previous-tick window and reject helm loss, dockyard work, locks, leash and sinking. Passenger captains cannot use stale wrappers to write sails. The hooks, config and helper classes are confined to `src/nativeShipsQa` and are absent from the production JAR.

This prototype deliberately leaves Recruits' compatibility flag unchanged. Production activation also requires resolving the independent repair/damage mapping path and verifying the actual client, protected ownership, native waypoint convergence, unload/reload and production packaging. A passing prototype contract is not a completed gameplay fix.

## Verified observations and remaining checks

The original exact-version dedicated run [37273363433](https://github.com/boostbar9/Faction-Raids/actions/runs/37273363433) and ordinary Build [37273363635](https://github.com/boostbar9/Faction-Raids/actions/runs/37273363635) succeeded at `bb0560a6`. Final 2.0.0 returned `recruitsSmallShipsCompatible=false`; both hulls assigned captains to DRIVER even after crew-first boarding, but Recruits recognized them only when passenger index zero. Both hulls returned no controlling passenger, and the left getter suppressed a direct native input request. Native dockyard refusal and entity/passenger NBT round trips passed.

The strengthened final-only baseline also passed at `b0286265` in [37274024340](https://github.com/boostbar9/Faction-Raids/actions/runs/37274024340), including each actor's tick counter and an obstacle intersecting translated real mast bounds. The legacy case exposed a fixture entity-ticking gap; the follow-on fixture now contains the full initialized scene inside an explicit 48×24×48 GameTest structure. The first adapter iteration failed on duplicate test resources before execution; source-set paths were corrected instead of hiding duplicate handling.

The client fixture separately verifies actual native passenger/seat synchronization, takes real framebuffer screenshots, disconnects/saves, reopens the same disposable world, and checks original ship and crew UUIDs and helm assignment again. It lets final 2.0.0 generate its own default config and never uses the beta seed helper. At `803e1838`, [all four native jobs](https://github.com/boostbar9/Faction-Raids/actions/runs/37275172561) and [ordinary Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37275172636) passed. The QA-only adapter produced native opposite turns of approximately ±16.36° for Cog and ±12.43° for Brigg without direct yaw mutation, and passed its expiration/helm/dockyard checks. Both 1280×720 client frames were inspected; unchanged final ships and their crew/helm assignments survived real integrated-server disk save/reopen. The client job does not enable the adapter, so adapter client synchronization and actual player takeover remain unverified.

## Bounded production ownership safeguard

Recovery previously retained a lost raid crew aboard an unowned ship but then still applied convoy steering to that ship. `NavalConvoy.mayControl` now requires the convoy's own disposable/team tags and refuses control while any player or unrelated living passenger is aboard, including nested seats. Recovery and collision-checked landing remain available for the raid crew. Native final/legacy/client fixtures call actual recovery and tick methods and require that unowned ship yaw/velocity remain unchanged; unit tests cover nested passengers and team mismatches. This safeguard does not activate the captain adapter or certify final-2.0.0 convoy navigation.

Review of the successful client run also found five transient “Received passengers for unknown entity” warnings after reconnect, followed by successful final UUID/helm checks. The pre-reload image retains a welcome-chat overlay; the post-reload image is unobstructed. These fixtures establish eventual synchronization and persistence, not warning-free join synchronization or polished player UI.

Absence checks are helper-level JUnit tests with an uninitialized or missing Small Ships API. The real mod declaration still makes Small Ships mandatory, so these are not claims of no-Small-Ships game startup. Actual final/legacy native fixtures separately request the vanilla-vessel fallback and verify the production factory returns a real vanilla Boat.

A subsequent exact-head adapter rerun at `1197965` failed before GameTest execution in final Small Ships `getSchematicVersion`/`updateConfig`, because its fresh config data lacked `schematicVersion`. The unchanged final, legacy and client jobs succeeded on that head. This intermittent native first-start failure is retained as a blocker/limitation; the workflow now preserves untouched fresh QA config files for diagnosis. No production config is reset or overwritten.

Fresh QA now uses exact hash-checked native-generated final defaults captured from the successful config initialization in run37276889470. Dedicated and integrated-world server defaults independently matched byte for byte. The helper refuses existing directories, symlinks, changed defaults and changed artifact pins, and places server defaults in Forge's defaultconfigs folder without creating a world. This is a test-fixture initialization precaution, not a repair of the intermittent upstream first-start defect, and it never resets player configs. Five helper regressions cover the safeguards.

The genuine waypoint extension exposed another final-seat integration issue: the native processed path contained one unreachable node at Y=-53, while water was Y=-55 and the valid helm captain stood at Y=-52.77. Neither attack targets nor block obstacle rays explained the refusal. Exact Recruits bytecode seeds its swim path using captain Y, despite taking X/Z from the vessel. A new QA-only named-runtime redirect tests using the eligible final ship's waterline for this single start-height read; it preserves native node validity/collision/pathfinding and does not move the captain or ship. Production mapping and packaging are still deliberately unproven.
