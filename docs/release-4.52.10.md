# 4.52.10 sealed-loot release record

The native HUD gate counts all three sealed chest cards only after their visible text is drawn. Compact layouts no longer pass sealed-state verification through an empty reserve band.

Status: merged, accepted by CurseForge and publicly verified on 2026-10-05 at 18:36 UTC as file **9071131**. The live public page shows version 4.52.10, `siegeoverhaul-4.52.10.jar`, Release status, Forge/1.20.1 support and Main File status. Its file-specific relationships contain the expected four required and seven optional projects. The exact tested/accepted JAR checksum is recorded below; an independent public-CDN byte checksum remains unverified and is not claimed.

- [Sealed-loot change, PR #279](https://github.com/boostbar9/Faction-Raids/pull/279)
- [Final native-HUD evidence correction, PR #280](https://github.com/boostbar9/Faction-Raids/pull/280)
- [Public CurseForge file](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9071131)
- [Public file relationships](https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files/9071131/dependencies)
- [Successful exact-artifact publisher](https://github.com/boostbar9/Faction-Raids/actions/runs/37343639664), attempt 1

## Player-visible scope

The Command Center no longer lists or renders possible equipment from unopened loot boxes. Common, Noble and Royal cards still show their established rarity promises and 16/48/96-emerald prices, but the exact relic remains with the Fates until the owned box is opened. Purchase confirmation and the existing reveal sequence are unchanged.

This is a presentation-only correction. It does not change loot pools, rarity floors, random selection, enemy drops, Treasury payments, saved items, the creative catalog or network protocol 22. Server and clients still use matching mod versions.

## Exact source and artifact

- Reviewed final PR head: `0be0574331b7c7e3a2f1dadffda367b617ebf275`.
- Merged source: `f259763b6e7c8ee4a927316e6f0336640fc8797c`.
- Identical reviewed/merged tree: `8fa911b8276501efc1d0c73c527177f8777ca00a`.
- [Reviewed PR Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37341714978), attempt 1: JAR artifact `11358272891`, XML artifact `11359036453`.
- [Merged-main Build](https://github.com/boostbar9/Faction-Raids/actions/runs/37342855421), attempt 1: JAR artifact `11358664417`, XML artifact `11359865124`.
- Tested and accepted release file: `siegeoverhaul-4.52.10.jar`.
- Tested and accepted JAR SHA-256: `2c5a50d042a880282da1b50c0fbaa794e0afd74509c12991938274994e60db58`.
- XML: **1,557 tests across 258 suites**, zero failures, errors or skips.
- Suite manifest SHA-256: `55c320fdc3c296e57fcf9877e9983763f49acc4d2762bda99fddc44beea8a607`.

## Completed verification and limits

The full Java 17 Forge PR and merged-main Builds passed. [Final-head review](https://github.com/boostbar9/Faction-Raids/pull/280#issuecomment-5998868755) found no blocking findings for the exact reviewed head above.

[Native HUD QA](https://github.com/boostbar9/Faction-Raids/actions/runs/37341890400), attempt 1, passed with artifact `11359330490`: **142 framebuffers** across the established compact and roomy viewports. All four Loot matrix views require three actually rendered sealed chest cards, zero pre-purchase reward stacks and zero possible-item controls. PR #280 corrected the late PR #279 finding that an empty compact reserve band could otherwise report successful rendering.

The native HUD fixture is a labeled client-menu sample. It does not purchase a box, spend Treasury funds, validate random distribution through statistical play or constitute an interactive Minecraft playthrough. Existing loot-selection unit tests remain the evidence for pool and probability behavior.

## Publication receipt and public verification

The successful publisher ran from commit `71aa8261055f6efda14d6ef8035881adf847cf13` on `publish/curseforge-v4.52.10`. Its accepted-upload receipt was recorded on 2026-10-05 at 16:48:40 UTC for CurseForge project `1364352`, file `9071131`, with the exact source, build, HUD and JAR identities above.

- Receipt artifact: `11359701347`, `curseforge-4.52.10-receipts-37343639664-1`.
- Receipt ZIP SHA-256: `7cb9188c5cfd8c1f5cffd2e59f30d30902ff06b30e12fb65bac25144ea43633f`.

The immutable receipt records accepted upload with public availability not yet verified. The later 18:36 UTC public browser check separately verified the file metadata and its file-specific Related Projects page. Required projects: recruits, workers, small-ships and siegeweapons. Optional projects: ftb-chunks-forge, open-parties-and-claims, corpse, epic-knights-armor-and-weapons, ewewukeks-musket-mod, curios and epic-knights-addon.

This record closes the documentation and update-feed handoff for the already published artifact. No additional upload, publisher run, release tag or public download was needed for this closeout; independent public-CDN checksum verification remains outstanding.
