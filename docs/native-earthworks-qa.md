# Native one-FILL earthworks QA

This opt-in fixture is independent of stepped perimeter geometry. It exercises the internal production `EarthworksCommission.reviewLocal` / `acceptLocal` first slice with a real integrated-server survival player, the normal server player list/profile cache, and the exact pinned Workers 2.0.3 / Recruits 1.15.2 runtime. It does not substitute a FakePlayer or inject an inventory-authority cache.

The disposable world contains explicitly synthetic flat stone terrain, one native claim, one empty AIR→DIRT work cell, one spawned native builder and a finite chest containing two dirt items. One 64-emerald Treasury funding is synthetic setup. Faction setup uses native command-style managers; core placement uses the real item and its ordinary deferred startup tick. Unrelated starter NPCs are frozen before measured work. Before review, the unassigned worker is briefly released from its setup AI hold on ordinary ticks to establish real floor contact; no grounding flag is fabricated. Its actual tick interval and grounded body are recorded. The worker starts measured work grounded and within the first slice's safe work reach. The chest initially requires a short original Workers storage approach; it remains close enough that the worker can fill without an unimplemented return route.

Before measurement the fixture exercises unchanged-world pure review, changed support, an unclaimed write, a deliberately unloaded bounded snapshot, repeated acceptance and a different stale review. A disposable-world missing-core fault briefly removes and restores the exact core object to verify a paid retry cannot borrow missing payment authority. None of these rejected preparations may debit again, supply dirt or fill the target.

After the post-acceptance `setNoAi(false)` start, the fixture only observes server ticks. It never manually ticks an entity/goal, changes tick speed, teleports the builder, feeds the worker, refills the chest, directly places dirt or edits native requests. It retains the actual original `GetNeededItemsFromStorage` reference, observes its movement/storage states and the source transfer tick, checks the production source receipt, and reconciles chest → worker → world at every observed END tick. The real Workers placement callback must consume one item and leave a single accounted journal receipt. A 40-tick stable window and repeated paid accept check follow.

## Run

Start from a clean fresh QA directory:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_native_earthworks_qa.py' -v
python3 scripts/prepare-native-earthworks-client.py
./gradlew --no-daemon --console=plain -PnativeEarthworksQa=true prepareNativeEarthworksQaClient
./gradlew --no-daemon --offline --console=plain -PnativeEarthworksQa=true runClient
python3 scripts/audit-native-earthworks-bytecode.py build/native-earthworks-qa/evidence
python3 scripts/verify-native-earthworks.py build/native-earthworks-qa/evidence
```

The helper uses the existing exact Small Ships defaults and refuses existing worlds/configuration. `nativeEarthworksQa` is a separate source set, mutually exclusive with other opted-in clients, and is never packaged into the release JAR. The `Native One-FILL Earthworks QA` workflow is manual or same-repository PR opt-in via label `native-earthworks-qa`; it retains exact source/runtime digests, actual screenshots, finite accounting observations and bytecode evidence. Vendor JARs and worlds are not uploaded.

## Evidence limits

A successful receipt proves only one native FILL reaching `STAGE_VERIFIED`. It does not prove CUT/drop authority, multiple steps, autonomous grading approach/ascent, distant-storage return, marker retirement/COMPLETE, crash/save/restart recovery, normal player-facing review UI, or dedicated-client networking. Existing accepted v1 records are not migrated or used as grading authority. This fixture includes no PR276 stepped geometry payload.

Compilation and the Python verifier/contract tests are not live Minecraft proof. A native run is only passed after `result.json`, all custody transitions, native bytecode and screenshots pass the external verifier. Full regression Build and remaining gameplay/recovery gates remain separate release requirements.
