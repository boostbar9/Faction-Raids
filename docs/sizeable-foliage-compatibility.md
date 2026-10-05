# Sizeable Foliage single-cell grass compatibility

## Report and bounded fix

A player perimeter review repeatedly stopped at `sizeable_foliage:very_short_grass` at `-96, 77, -705`. This was the occupied build-space check, not a failed natural-ground foundation check. The previous camp plant classifier did not recognize that block, and the native construction guard independently admitted only single-cell vanilla plants.

`CampVegetation.singleCellPlant` is now the shared one-cell admission policy for player wall surface checks and the native construction guard. Existing camp plant recognition also includes the same reviewed optional grass. Only the exact registry ID and implementation class below are added. No addon classes are linked, loaded reflectively or registered; installations without Sizeable Foliage remain supported without a new dependency.

- Registry: `sizeable_foliage:very_short_grass`
- Implementation: `com.craisinlord.sizeablefoliage.content.block.VeryShortGrassBlock`
- Required runtime structure: a `BushBlock`, not a crop, flower or paired plant; no state properties, block entity, fluid or collision; replaceable and instant-break

The class-name check deliberately fails closed if a future addon release replaces the audited implementation. A matching namespace, material tag, `BushBlock` superclass or replaceable flag alone is insufficient. Both the registry/implementation identity and the structural checks must pass.

The old wall-surface shortcut also now rejects paired plants and wither roses, matching final native admission rather than offering a surface that the protected handoff would subsequently refuse. Existing camp handling of vanilla paired plants is retained.

## Official source and binary audit

Reviewed the author's [public source repository](https://github.com/craisinlord/sizeable-foliage) at commit `a952589640918c9998938e83bf43c54ba3f8e5be`:

- [VeryShortGrassBlock](https://github.com/craisinlord/sizeable-foliage/blob/a952589640918c9998938e83bf43c54ba3f8e5be/common-1.20.1/src/main/java/com/craisinlord/sizeablefoliage/content/block/VeryShortGrassBlock.java) extends `BushBlock`. Its overrides provide the outline and bonemeal behavior only; it defines no multipart properties or removal/neighbor callbacks. Bonemeal replaces that single cell with vanilla grass.
- [ModBlocks](https://github.com/craisinlord/sizeable-foliage/blob/a952589640918c9998938e83bf43c54ba3f8e5be/common-1.20.1/src/main/java/com/craisinlord/sizeablefoliage/registry/ModBlocks.java) constructs it from `BlockBehaviour.Properties.copy(Blocks.GRASS)`.
- [ForgeModBlocks](https://github.com/craisinlord/sizeable-foliage/blob/a952589640918c9998938e83bf43c54ba3f8e5be/forge-1.20.1/src/main/java/com/craisinlord/sizeablefoliage/forge/registry/ForgeModBlocks.java) binds that factory to `very_short_grass`.
- [VeryTallGrassBlock](https://github.com/craisinlord/sizeable-foliage/blob/a952589640918c9998938e83bf43c54ba3f8e5be/common-1.20.1/src/main/java/com/craisinlord/sizeablefoliage/content/block/VeryTallGrassBlock.java) has custom lower/middle/upper parts and an `onRemove` that clears other cells. It is explicitly outside this fix. The larger bushes, fern walls and other addon plants are likewise not inferred safe from their names or tags.

The published Forge 1.20.1 version is separately pinned because the current source tree is not proof of the published binary:

- Official [Modrinth release 1.2.1](https://modrinth.com/mod/sizeable-foliage/version/m2kFJSUs)
- Filename: `sizeable_foliage-forge-1.20.1-1.2.1.jar`
- SHA-256: `19224fa993d92e605855261dc846d95239f05c3d4a74f1d199d025c918566e0a`
- Published SHA-512: `2edb316ea862cd0e7c9e748a2ccc05c363590f6e05a67eeee765ba559345104637e3134abc049f164b95123797ed7beea679ddc4322fb1ab1fb26a4962f45b92`

The downloaded official JAR matches that published SHA-512. Classfile inspection independently confirms the exact `BushBlock` subclass, shape/bonemeal-only overrides, and absence of its own multipart/state-defining, `onRemove`, `playerDestroy` or `neighborChanged` implementation.

## Preserved boundaries and verification

The compatibility predicate does not authorize excavation, tree felling, crops, structures, containers, hazards, fluids or arbitrary modded blocks. It does not expand the reserved footprint, claim permissions, payment authority or a saved paid plan. Native work still requires the exact original snapshotted state; a replaced plant or completed-cell edit pauses rather than becoming new work. The already-audited claim, loaded-chunk, border, entity, neighbor-update and ledger checks remain in force.

The unit regressions exercise the exact registry/class pair, excluded addon IDs, changed runtime properties, vanilla hazards/paired plants, and addon-absent behavior. Vanilla grass is explicitly only a structural test replica. Real-addon review/clearing/native placement requires the separate opt-in native QA fixture; a unit pass or binary audit alone is not live gameplay evidence.

This small compatibility repair is separate from broader natural-terrain clearing and leveling, which needs its own bounded plan, preview, material handling and provenance policy.
