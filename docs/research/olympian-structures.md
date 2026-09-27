# Olympian camp structure research

Reviewed 2026-09-27. These are candidates and design references, not bundled assets.

| Candidate | Published details | Decision |
| --- | --- | --- |
| [Mark2304: Greek Settlement](https://www.minecraft-schematics.com/schematic/17503/) | 29 x 29 x 13, interiors, Java 1.17.1+, Litematic; matching houses and temple | Strong style reference for a coordinated camp. Listing does not state redistribution rights; obtain author permission before bundling or adapting the actual files. |
| [Mark2304: Small Greek Temple](https://www.minecraft-schematics.com/schematic/17638/) | Interiors, Java 1.17.1+, Litematic | Candidate for a shrine after permission and footprint inspection. Compatibility is a listing claim, not a verified 1.20.1 import. |
| [BuildSchem: Iconic Parthenon Temple](https://www.schemcraft.com/schematics/iconic-parthenon-temple) | 86 x 48 x 72, 77,622 blocks; listing says CC BY-NC 4.0 | Too large for starter camps. Do not bundle under the present research decision. |
| [BuildSchem: Ultimate Greek Temple Survival Base](https://www.schemcraft.com/schematics/ultimate-greek-temple-survival-base) | 128 x 72 x 128, 156,940 blocks; listing says CC BY 4.0 | More permissive stated terms, but far outside camp size and build budgets. File provenance and visual quality have not been verified. |

## Recommended direction

Create a small coordinated kit: shrine/core court, barracks, forge, supply pavilion, gate and wall segments. Give each deity a recognizable silhouette as well as materials; keep the current faction mapping authoritative. Starter camps should establish that identity immediately, with later stages adding substantial architecture rather than scattered props.

Use broad entrances, three-block-wide routes, open gathering space and shallow supported foundations. Each building needs an explicit role, entrance direction, full interior clearance volume and construction order. Reserve approaches before placing scenery. Test all four rotations, slopes, queued obstructions and save/reload. Do not infer navigability from a screenshot.

## Asset pipeline proposal (not implemented)

Use schematic files as authoring inputs, then convert vetted structures offline to a bounded internal template format. WorldEdit's [7.2 clipboard documentation](https://worldedit.enginehub.org/en/7.2.20/usage/clipboard/) documents modern Sponge `.schem`, legacy `.schematic`, origins and rotations. Litematic inputs require their own conversion step; they are not interchangeable extensions.

Before accepting an asset: record original author, URL, license/permission, file hash, modifications and credit; validate all block states against 1.20.1; strip entities, command blocks and supplied container inventories; define allowed block-entity data deliberately. Retain block orientations when rotating. Feed placement through the existing budgets, Workers jobs, claim checks and restoration ledger, not a direct whole-schematic paste. No additional player dependency is needed for an offline conversion approach.

For 4.47.20 the compact starter pavilion geometry is original. No external schematic was downloaded, copied or bundled. The proposed import pipeline and larger modular kit remain follow-up work, not released functionality.
