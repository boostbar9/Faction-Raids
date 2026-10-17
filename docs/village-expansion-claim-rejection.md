# Village Expansion claim rejection investigation

Status: reproduced in an isolated execution of the published claim checker on 2026-09-27. No production patch or gameplay fix is included. NAD42's installed addons and exact versions remain unconfirmed.

## Artifact and references

- Siege Overhaul reviewed: `99d6bec3944f33a2414b6c7af74e33a69887cf79`, version 4.47.26.
- Village Expansion CurseForge file [8792855](https://www.curseforge.com/minecraft/mc-mods/villager-recruits-village-expansion/files/8792855). The page calls it 1.0.7.20; its filename and embedded version are `village_recruits-1.0.7.93TC-205.jar` / `1.0.7.93TC-205`.
- Published JAR SHA-256: `9f34d9fed68f1f07bd83040759bece65240b31e37b0d4cea08286b1605137437`.
- The author's [GitHub TC-205 artifact](https://github.com/magarellivito09-boop/Village-Recruits-Issues/releases/tag/1.0.7.93TC-205) is different bytes; do not substitute it in this reproduction.
- Similar upstream report: [Village-Recruits-Issues #4](https://github.com/magarellivito09-boop/Village-Recruits-Issues/issues/4).
- Local tracking: [Siege Overhaul #198](https://github.com/boostbar9/Faction-Raids/issues/198).

## Failure path

Inspection of the published classes identifies these steps:

1. `PlayerClaimRules.onClaimCreated` handles a new native Recruits claim at HIGHEST event priority. It excludes known addon villages, then checks the distance from every claimed chunk's edges to registered village centers and potential tower locations.
2. `VillageSiteSpacing.potentialTowerChunksInRadius` enumerates random-spread placement candidates. It does not establish whether the candidate generated a tower, a vanilla village, or no structure. The default structure set mixes the tower and five vanilla village types.
3. `RemoteTowerActivation` probes candidates. When a loaded candidate has no spawner, it remembers the chunk in `PROBED` and removes its priority request. This does not add the chunk to `generatedChunks`.
4. The claim checker only excludes candidates in `generatedChunks`. An already-probed empty candidate can therefore still become the rejecting site. Asking it to queue again does nothing because the queue method rejects previously probed candidates.
5. The same empty candidate continues rejecting later attempts. This explains how land with no visible village can remain blocked. It does not establish the state of NAD42's world.

The checker uses a fixed 600-block edge distance. Changing Siege Overhaul's camp spacing or orphan-lease cleanup will not correct this separate candidate classification.

## Repeatable check

Supply the published JAR separately; it is not redistributed in this repository:

```sh
python3 tools/reproduce_village_claim_reservation.py /path/to/village_recruits-1.0.7.93TC-205.jar
```

The script verifies the complete artifact SHA-256, extracts only the original compiled `PlayerClaimRules` class into a temporary directory, and executes its private reservation query against isolated Java API fixtures. It uses the JDK compiler module and requires Java 17. No decompiled upstream implementation is copied into the repository.

Observed result:

- A 5-by-5 claim with no village centers and a previously probed empty candidate at block X=168, Z=8 is rejected repeatedly.
- The empty candidate is not queued again, and no tower is recorded as generated.
- No candidate or a candidate outside the 600-block range permits the claim.
- A registered village at the nearby site still rejects it.

These fixtures model the published queue/probe contract; they are not a live Forge server, world-generation test, or end-to-end payment test. No interactive Minecraft playtest was performed.

## Safe correction requirements

A correction belongs before the addon's claim-cancellation/refund branch:

- Track confirmed-empty probes separately from generic `PROBED`: that set also contains attempted or active tower activations, so skipping all of it would be unsafe.
- Exclude only confirmed-empty candidates. Preserve registered villages, real tower spawners, active placement jobs, and genuine pending reservations.
- Define invalidation for server/world reload, changed generation settings, and a later tower appearing in a previously empty location. Never alter the addon's `generatedChunks` merely to trick the check.
- Keep uncertain/unloaded sites conservative and any verification work bounded; do not synchronously load hundreds of chunks when the player clicks Claim.
- Preserve other mods' event cancellations and native claim ownership/buffer checks.
- Test payment exactly once. The current addon cancels and refunds before notifying the player. Uncanceling the event afterward can accept a refunded claim; an exception or broad listener removal can also affect unrelated protections.
- Gate any Siege Overhaul compatibility interception to a verified addon API/bytecode contract and safely leave unsupported versions unchanged.

No broad event uncancellation, global village-protection disable, or world-data deletion is a suitable repair.

## Remaining evidence and work

Obtain NAD42's complete warning (including nearest-site coordinates), `latest.log` or full mod list, exact server/client mod versions, and selected claim coordinates. Reproduce their installed combination in a disposable world. Implement the negative-site correction upstream, or a narrowly scoped optional compatibility hook with actual-addon lifecycle and payment coverage, before claiming or publishing a fix. The current Siege Overhaul release remains unchanged.
