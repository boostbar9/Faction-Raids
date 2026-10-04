# Native camp establishment acceptance

This opt-in fixture runs the real Forge client and integrated server with the four pinned companion mods. Enable the `native-camp-qa` label on a same-repository pull request, or dispatch `Native Camp Spawn QA`. The runtime is isolated under `build/native-camp-qa/client`; it refuses existing fixture worlds. It needs no account login, release credentials, dedicated-server EULA flag or player save. This directory's fresh `config/siegeoverhaul-common.toml` uses the supported `[playerRaids] allowedRaiderFactions = ["wilds_marauders"]` setting. No real user configuration is touched. Selecting the existing Artemis doctrine makes native scout and absent-assassin coverage deterministic while preserving the real command/scouting pipeline and its timers.

For local setup, first run `python3 scripts/prepare-native-client-defaults.py --mode camp-spawn --smallships-version 2.0.0-b1.4` in a fresh isolated checkout, before creating the camp-specific config or options. The workflow does this automatically. See [unchanged Small Ships defaults and strict isolation](native-client-defaults-qa.md).

The entry point is the actual non-op Survival client's `/siegeoverhaul start` command. Normal production raid ticks then perform scouting, native claim selection, camp earthworks, enemy core establishment and native crew creation. The harness never calls a private camp helper, changes the raid's timers/state, forces a selected site, or accelerates game ticks.

## Scenarios and fixture boundaries

1. **Natural flat ground.** A vanilla custom superflat world supplies grass over 60 layers of dirt. Public Recruits faction/claim setup and actual `CoreItem.place` establish the defender's core. Production must establish an enemy camp through the natural search path.
2. **Controlled shallow water and forest.** A separate vanilla custom superflat world supplies three water layers over natural dirt everywhere, preventing a later scout from escaping to unrelated dry flat terrain. A small defender island and neighboring protected claim are fixture setup. After observing the first actual loaded remote scout, fewer than 300 block writes provide a three-wide dry landing/causeway, rooted oak logs/leaves and protected unclaimed storage/stonework. Production must exhaust its natural search, enter its genuine bounded terraforming fallback and safely select a camp. No raid state is changed to produce that transition.

3. **Controlled rocky rise with shallow water and forest.** A third fresh world retains scenario 2's water, supported trees, dry landing and protected builds. Four additional setup writes create a one-block exposed stone rise at the observed scout, inside every possible local camp core. Production must raise the camp plane by exactly one block, establish the same native claim/core/crew and keep that stone unchanged and absent from the restoration ledger. No selected site, raid timer, fallback flag or native behavior is forced. The fixture remains below 300 setup block writes. This is a mixed-terrain regression, not a naturally generated mountain-world claim.

All actors are fresh Survival players with commands disabled and no operator permission. Faction creation, claims, defender core, protected treasures and controlled terrain are labeled fixture setup. Only unrelated defender starter NPCs are parked during setup; the subsequently spawned enemy workers and guards retain their real AI. Day/weather changes and natural mob spawning are disabled for reproducibility.

Crew identity is checked after 60 ordinary server ticks following establishment. Production `beginRaid` creates workers before adding the raid to SavedData; its normal 20-tick `RaiderFactions.sync` pass then resolves their specific hostile scoreboard faction. The harness retains exact faction, native type, crew-tag and active-AI assertions, and records the initial and settled identities. Guards keep the shared raider leader UUID. `NativeCampConstruction.start` intentionally assigns workers a separate random job owner, saved in `raid.nativeCamp.Owner`; QA requires that same owner on each worker, the native build/storage areas and the real supply barrel, together with the camp identity tags. Each worker must remain owned and unlistening, both unchanged native `canWorkHere(worker)` checks must pass, and storage must include its native `BUILDERS` type. The owner must differ from the fixture player and shared guard leader. A real d3aba9f run confirmed native faction synchronization after 60 ordinary ticks and exposed the old QA assumption that workers also kept the guard owner; that assumption was incorrect. It never calls faction synchronization or edits the crew to make these checks pass.

Every registered requested guard slot must produce its exact native role in these controlled scenes. All scenarios require five native guards, including two actual recovered scouts, and an explicitly unavailable `recruits:assassin` slot. An absent companion role receives no substitute; Forge's default pig must never be instantiated for that missing key. A run with no scouts cannot satisfy the compatibility acceptance.

The Recruits 1.15.2 scout compatibility boundary resumes only the known failed spawn tail: the existing native navigator's public `setCanOpenDoors(true)`, then the normal final `initSpawn` call. Native superclass initialization runs exactly once. Recovery requires exact native classes/version and the observed direct cast boundary; the vanilla override is identified by its full method signature for development and reobfuscated names. Unknown failures remain failures, and the guard is discarded before registration. The actual camp evidence records which scouts used this path. Source support is Recruits commit `cff03e085d65653406a8b6ddcdd0ebff615c3e48`; source/binary equivalence and the actual recovery must be checked using the next loaded-JAR bytecode audit and native run.

The assertions require the real 25-chunk native enemy claim with correct owner and all chunk indexes, the actual enemy core block, campfire/barrel/banner, and loaded living native builder/guard entities with the correct crew identities. Every block in the two original claimed regions and exact protected chest NBT must remain unchanged, and no protected cell may enter the camp restoration ledger. Hostile terrain must use the fallback path and record actual replaced water, oak logs and oak leaves in the original-state restoration ledger; a natural success there is a failure of coverage.

## Evidence

`result.json` records loaded Minecraft/Forge/companion versions, SHA-256 of the loaded ForgeGradle remapped development JARs, native API identities, OpenGL renderer, timed scout samples and each scenario's assertions. These are development artifact identities, not original release JAR hashes. Logs retain concrete production rejection/pause reasons. No vendor JAR or world is uploaded.

The read-only audit disassembles 18 classes from those same already-loaded JARs, including ScoutEntity, BowmanEntity, RecruitPathNavigation, AsyncGroundPathNavigation and the companion entity registry. It uploads text and class-list/hash receipts only. This is bytecode before runtime mixin transformation, not a universal mod-compatibility claim.

The raw framebuffer manifest is:

- `01-established-flat-camp.png`
- `02-established-shallow-water-forest-camp.png`
- `03-established-rocky-water-forest-camp.png`

The observer moves only after Survival command, claim/core and crew assertions pass. Images show an established camp, not a claim that every decorative block or raid wave has completed. Failures retain `failure-native-camp.png` and diagnostics. Review actual pixels separately from the machine assertions.

Each natural/fallback scouting pass remains governed by the production candidate and time limits. The harness allows six minutes for the flat case, eleven for each hostile case, and 33 minutes overall after client startup. The workflow's 37-minute process and 43-minute job bounds include Forge setup and evidence collection.

## Evidence limits

This covers three controlled integrated-server camp establishment cases. It does not prove arbitrary mountainous/deep-ocean/protected terrain can support a camp, multiplayer client connections, other mod packs or graphics drivers, full decorative camp completion, wave combat, or occupation. A production bounded rejection must remain a rejection with its visible reason. Compilation, source review and JUnit tests alone do not satisfy this acceptance.
