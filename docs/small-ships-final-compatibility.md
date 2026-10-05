# Small Ships final 2.0.0 compatibility

Status: reproduction and bounded bridge design; gameplay compatibility is not yet established.

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
