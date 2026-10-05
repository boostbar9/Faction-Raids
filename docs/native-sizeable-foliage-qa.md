# Actual Sizeable Foliage native QA

This is an explicit addon variant of [Native Building QA](native-building-qa.md).
Enable the `sizeable_foliage` boolean when manually dispatching **Native Building
QA**, or add the `native-sizeable-foliage-qa` label to a same-repository PR.
The existing `native-building-qa` label alone continues to run vanilla vegetation.

## Pinned official addon, QA only

- Minecraft 1.20.1, Forge, Sizeable Foliage **1.2.1**.
- Official [Modrinth version m2kFJSUs](https://modrinth.com/mod/sizeable-foliage/version/m2kFJSUs).
- Runtime coordinate `maven.modrinth:sizeable-foliage:m2kFJSUs`, remapped by ForgeGradle.
- Original release SHA-256 `19224fa993d92e605855261dc846d95239f05c3d4a74f1d199d025c918566e0a`.
- Original release SHA-512
  `2edb316ea862cd0e7c9e748a2ccc05c363590f6e05a67eeee765ba559345104637e3134abc049f164b95123797ed7beea679ddc4322fb1ab1fb26a4962f45b92`.

The original resolved Maven artifact must match both hashes. The actual loaded
remapped runtime artifact gets its own SHA-256 and is explicitly distinguished
from the original release. Neither vendor JAR is uploaded in evidence. No addon
classes, dependency declaration or QA classes enter the production JAR.

## Exact coverage and limits

The real initialized Forge registry must contain the expected
`com.craisinlord.sizeablefoliage.content.block.VeryShortGrassBlock`, directly
extending `BushBlock`. Its runtime state must have empty collision, no fluid,
block entity, crop inheritance, double-plant inheritance or state properties.
The class must not declare removal, neighbor or multipart-state overrides.
The original pinned classfile was separately inspected: its only behavior
methods are outline shape and the three bonemeal methods; bonemeal acts only
when requested and is not used by this fixture.

Before any commission, explicitly seeded short grass at a structural target and
an internal perimeter-clearance cell must survive the real production review.
Thirteen real registry obstructions are then reviewed individually: eight other
addon bush/fern/multipart states, wheat, oak sapling, chest, water and stone bricks.
Each must be rejected with its exact position and registry ID. Reviews preserve
Treasury, worker items, receipts and projects. Fixture terrain is restored.
These are synchronous production `prepare` checks, not rejected confirmation
packets or naturally grown full multipart plants.

The existing second-world fixture then substitutes actual addon short grass for
its one dandelion. Ordinary Survival plan-use packets must preserve it during
free preview and unpaid confirmation, then capture it in the paid native
`Before` receipt. The actual Workers break goal must remove it and write
`Cleared`; both receipts must survive real world close/reopen. Ordinary native
AI must place the planned cobblestone at that position, preserve its outside-plan
support, finish the bounded 83-block wall and conserve exact materials and
Treasury through the existing pause/resupply/restart checks. QA does not remove
this accepted plant, place completed structure blocks, accelerate AI, or bypass
production authority. The final baseline screenshot shows the actual finished
wall and builder; it does not establish a complete territory perimeter.

The ordinary baseline rendering, claim/authority, economy, finite-stock, reload
and screenshot assertions remain required. A passing Python parser test or Java
compile alone is not native gameplay evidence. Acceptance remains pending until
an actual linked workflow run passes and its screenshots are reviewed.

## Local command (fresh isolated checkout, Java 17 and display required)

First follow baseline fresh-client setup in `docs/native-building-qa.md`, including
the unchanged Small Ships defaults helper. Then run:

```sh
./gradlew --no-daemon --console=plain --max-workers=2 \
  -PnativeQa=true -PnativeQaSizeableFoliage=true runClient
python3 scripts/verify-native-sizeable-foliage.py build/native-qa/evidence/result.json
```

`GRADLE_USER_HOME` must name the cache used by that run for original-artifact
verification. The variant only supports the baseline native QA mode. Output is
`build/native-qa/evidence`, including `sizeable-foliage-evidence.json`; use a new
checkout/build directory for each run rather than reusing a prior QA world.
