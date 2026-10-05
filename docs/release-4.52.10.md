# 4.52.10 sealed-loot release record

The native HUD gate counts all three sealed chest cards only after their visible text is drawn. Compact layouts no longer pass sealed-state verification through an empty reserve band.

Status: release candidate. Final source, build, artifact checksum, review and CurseForge receipt are recorded after the reviewed head is merged and the exact merged-main artifact is accepted.

## Player-visible scope

The Command Center no longer lists or renders possible equipment from unopened loot boxes. Common, Noble and Royal cards still show their established rarity promises and 16/48/96-emerald prices, but the exact relic remains with the Fates until the owned box is opened. Purchase confirmation and the existing reveal sequence are unchanged.

This is a presentation-only correction. It does not change loot pools, rarity floors, random selection, enemy drops, Treasury payments, saved items, the creative catalog or network protocol 22. Server and clients still use matching mod versions.

## Verification plan and limits

- Full Java 17 Forge build and regression XML.
- Native HUD QA at the established compact and roomy viewports, including an explicit absence check for possible-item controls.
- Review of final source and merged-main artifact identity before publication.

The native HUD fixture is a labeled client-menu sample. It does not purchase a box, spend Treasury funds, validate random distribution through statistical play or constitute an interactive Minecraft playthrough. Existing loot-selection unit tests remain the evidence for pool and probability behavior.
