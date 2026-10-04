# Native camp establishment acceptance

This opt-in fixture runs the real Forge client and integrated server with the four pinned companion mods. Enable the `native-camp-qa` label on a same-repository pull request, or dispatch `Native Camp Spawn QA`. The runtime is isolated under `build/native-camp-qa/client`; it refuses existing fixture worlds. It needs no account login, release credentials, dedicated-server EULA flag or player save.

The entry point is the actual non-op Survival client's `/siegeoverhaul start` command. Normal production raid ticks then perform scouting, native claim selection, camp earthworks, enemy core establishment and native crew creation. The harness never calls a private camp helper, changes the raid's timers/state, forces a selected site, or accelerates game ticks.

## Scenarios and fixture boundaries

1. **Natural flat ground.** A vanilla custom superflat world supplies grass over 60 layers of dirt. Public Recruits faction/claim setup and actual `CoreItem.place` establish the defender's core. Production must establish an enemy camp through the natural search path.
2. **Controlled shallow water and forest.** A separate vanilla custom superflat world supplies three water layers over natural dirt everywhere, preventing a later scout from escaping to unrelated dry flat terrain. A small defender island and neighboring protected claim are fixture setup. After observing the first actual loaded remote scout, fewer than 300 block writes provide a three-wide dry landing/causeway, rooted oak logs/leaves and protected unclaimed storage/stonework. Production must exhaust its natural search, enter its genuine bounded terraforming fallback and safely select a camp. No raid state is changed to produce that transition.

Both actors are fresh Survival players with commands disabled and no operator permission. Faction creation, claims, defender core, protected treasures and controlled terrain are labeled fixture setup. Only unrelated defender starter NPCs are parked during setup; the subsequently spawned enemy workers and guards retain their real AI. Day/weather changes and natural mob spawning are disabled for reproducibility.

The assertions require the real 25-chunk native enemy claim with correct owner and all chunk indexes, the actual enemy core block, campfire/barrel/banner, and loaded living native builder/guard entities with the correct crew identities. Every block in the two original claimed regions and exact protected chest NBT must remain unchanged, and no protected cell may enter the camp restoration ledger. Hostile terrain must use the fallback path and record actual replaced water, oak logs and oak leaves in the original-state restoration ledger; a natural success there is a failure of coverage.

## Evidence

`result.json` records loaded Minecraft/Forge/companion versions, SHA-256 of the loaded ForgeGradle remapped development JARs, native API identities, OpenGL renderer, timed scout samples and each scenario's assertions. These are development artifact identities, not original release JAR hashes. Logs retain concrete production rejection/pause reasons. No vendor JAR or world is uploaded.

The raw framebuffer manifest is:

- `01-established-flat-camp.png`
- `02-established-shallow-water-forest-camp.png`

The observer moves only after Survival command, claim/core and crew assertions pass. Images show an established camp, not a claim that every decorative block or raid wave has completed. Failures retain `failure-native-camp.png` and diagnostics. Review actual pixels separately from the machine assertions.

Each natural/fallback scouting pass remains governed by the production candidate and time limits. The harness allows six minutes for the flat case, eleven for the hostile case, and 22 minutes overall after client startup. The workflow's 26-minute process and 32-minute job bounds include Forge setup and evidence collection.

## Evidence limits

This covers two controlled integrated-server camp establishment cases. It does not prove arbitrary mountainous/deep-ocean/protected terrain can support a camp, multiplayer client connections, other mod packs or graphics drivers, full decorative camp completion, wave combat, or occupation. A production bounded rejection must remain a rejection with its visible reason. Compilation, source review and JUnit tests alone do not satisfy this acceptance.
