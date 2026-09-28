# Door navigation and Olympian casting — 4.49.9

## Recruits door failure

Reviewed Recruits 1.15.2 source commit `cff03e085d65653406a8b6ddcdd0ebff615c3e48`:

- `RecruitPathNavigation` extends `AsyncGroundPathNavigation`, which extends `AsyncPathNavigation` / `PathNavigation`, not vanilla `GroundPathNavigation`.
- Its node evaluators already support doors, including the async evaluator factory.
- Our previous vanilla-ground installation skipped these recruits entirely.
- Native `RecruitsDoorInteractGoal.canUse` checks `GoalUtils.hasGroundPathNavigation` before its own custom navigator check. This vanilla gate rejects the custom hierarchy.

The original Siege `RaiderDoors` goal works from the common path/node API and explicit supported navigator hierarchy. It inspects at most four nearby nodes (plus their upper blocks), checks every five game ticks, and opens intact wooden doors within 2.5 blocks without waiting for collision. It does not scan buildings, destroy blocks, force iron doors, change paths, or take MOVE/LOOK ownership. Foreign claims remain protected. Existing native door-close goals are replaced only for tagged active siege troops at AI installation; friendly recruits are unchanged. Opened entrances remain open so trailing soldiers can pass.

Regression fixtures model the distinct native navigator hierarchy rather than inheriting vanilla ground navigation. They are API-shape tests, not execution of the Recruits binary.

## Iron's Spells 'n Spellbooks research

Reference: [repository](https://github.com/iron431/irons-spells-n-spellbooks), inspected `1.20.1-legacy` at `b928995887aa362467fa999a9c9fb86419f00f2d`. This is a source reference, not a claim that this moving branch corresponds to a particular published binary.

Files reviewed:

- `api/spells/SpellAnimations.java`: distinct charge, continuous and release animation identities.
- `entity/spells/lightning_lance/LightningLanceRenderer.java`: dedicated geometry, directional transforms and timed texture frames.
- `entity/spells/wall_of_fire/WallOfFireRenderer.java`: layered world-space geometry and independent visual timing.
- `LICENSE.md`: all rights reserved, including restrictions on redistributing assets.

We use the general presentation approach: a recognizable charge silhouette, a clear release, role-specific poses and local effects driven by server cast events. All new shape math and animation poses are original. No Iron's code, assets, textures, sounds or dependencies are bundled.

The first pass covers the existing storm (22), fire (27) and tide (29) casts. Storm emits jagged spokes and an upright bolt; fire emits eight rising flame ribbons; tide emits twin curling wave crests. Charge crests use bolt, flame and trident silhouettes. The native model/armor/held-item parts remain shared. Combat timing, damage, cooldowns and faction rules are unchanged; this is a presentation improvement, not a new spell catalog.

Visual work is bounded to at most 96 line segments per cast, 64 tracked casts, the existing distance culling and 128-particle per-tick budget. Reduced flashes lower opacity and omit release particles. Geometry is deterministic instead of frame-random flicker. Sound volume, animation toggle and particle detail remain client-controlled.

Tests cover finite/bounded/distinct geometry, invalid and expired events, role-specific poses and shared model parts. A Forge build cannot establish visual quality in a live client; no interactive Minecraft playtest is claimed.
