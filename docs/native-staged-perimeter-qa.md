# Full 5×5 native staged-perimeter acceptance

This is optional full-completion stress coverage, available by manual workflow dispatch when a specific risk justifies it. Focused representative gameplay and targeted regressions are the normal release approach.

This opt-in fixture verifies the production whole-territory project path in a fresh, isolated integrated Forge world. Its source being present does not establish a gameplay pass. A successful exact-head run, strict evidence contract, and inspection of its raw framebuffers are required.

First run `python3 scripts/prepare-native-client-defaults.py --mode staged-perimeter --smallships-version 2.0.0-b1.4` in a fresh isolated checkout, before other game-directory setup. This installs only unchanged native Small Ships defaults; see [startup provenance and strict refusal rules](native-client-defaults-qa.md).

Run `./gradlew --no-daemon --console=plain --max-workers=2 -PnativeQa=true -PnativeQaMode=staged-perimeter runClient` with Java 17. The separate `Native Staged 5x5 Perimeter QA` workflow runs under Xvfb/software OpenGL only when manually dispatched for a justified full-completion check. It uses read-only repository permissions, no secrets or publishing, and uploads evidence without worlds or companion JARs.

The only game directory is `build/native-staged-qa/client`; an existing `saves/siege-native-staged-perimeter` is rejected. All four pinned companions must actually load. Evidence identifies Minecraft/Forge and each loaded companion version and SHA-256. These are ForgeGradle-remapped development JAR hashes before runtime mixins, not original release-file hashes. The additional audit disassembles the exact already-loaded companion JARs and uploads text only.

## Fixture and actual authority path

- A vanilla custom superflat generator supplies stable stone ground at Y64. No broad terrain rewrite is needed. The fixture initially loads a bounded 7×7 chunk apron; ordinary player simulation distance 6 owns subsequent loading. The fixture adds no extra forced-chunk tickets; native companion default chunk-loading remains enabled.
- Public native faction/claim APIs create a single 25-chunk 5×5 claim, chunks X8–12/Z0–4. Actual CoreItem placement initializes the central core. Unrelated starter NPCs are parked; the tested builder is separate.
- The independent distance-to-unowned-column oracle covers the global five-wide outer ring of the 80×80 territory: 1,500 columns, 2,400 cobblestone and 1,500 oak planks, exactly 3,900 targets. Internal chunk seams remain open. The production quote must match this complete oracle and accept its natural native stage count, without imposing extra partition pressure.
- Four real, separately owned native storage areas scan four real single chests. Each starts with exactly 600 cobblestone and 375 oak, occupying 16 of 27 slots. The builder starts with a finite 64 bread and ordinary native tools; its initial construction stock is empty. No stock is replenished, no worker is teleported, and no build/tick speed changes are made. Initial NoAI is cleared only after the first paid acceptance.
- A real non-op Survival player opens the real core through a use packet, navigates visible Building → Auto perimeter → Review in world controls, and receives the production free plan. Actual plan-use packets must consume one plan and commission the complete manifest for one 64-emerald Treasury debit. The initial 2,000 fixture Treasury becomes 1,936; the existing exact read-only passive-income observer separately accounts for normal later interest/taxes.
- Every section must join with its predetermined native area UUID and reuse the original physical shovel site. The whole reservation, manifest, stage partition, single payment, and stage receipts are checked as work progresses. Native goals, storage extraction and pathfinding perform construction.

## Real restart boundaries and completion

The client fully closes and reopens the same world during genuine partial progress in the first section. When the natural production layout has multiple sections, a second close/reopen is required in `WAITING_FOR_NEXT_STAGE` after an actual verified/retired section; a single-section layout reports that boundary as inapplicable. A temporary spectator setting on the owner exercises the production permission pause and makes each shutdown boundary stable; the builder itself is not frozen or redirected. The fixture requires zero accepted-cell writes during that pause and no next section while permission is absent. This is explicitly a real permission-unavailable case, not a claim of testing a disconnected owner.

At each final `ServerStoppingEvent`, evidence captures partial geometry, pending union, every chest's values, native cargo/equipment, native recipe when active, complete project/journal, ledger generation and Treasury counters. `ServerStartedEvent` must observe exactly preserved project/journal/Treasury before resumed ticks; the native entity-join boundary records loaded construction inventory before AI. Restoring Survival permission must let original native work resume with the same payment and exact material totals.

Completion requires a durable `COMPLETE` full project or compact terminal receipt tied to the original project, generation, manifest and payment. It also requires complete stage evidence, all 3,900 exact target states, unchanged non-plan cells, open internal seams, unchanged actual claim identity, and zero residual building stock. Marker disappearance alone cannot pass. No artificial cancellation or corruption is injected into this long run; those edge cases retain their separately labeled pure/source coverage until an actual dedicated scenario exists.

The result separately records physical-marker removal, builder detachment and reservation cleanup. If production has compacted to a terminal receipt, all three must be independently observed. A retained full `COMPLETE` record proves construction completion without claiming that deferred cleanup has finished. The final overhead camera keeps the observer over the original central chunk so it does not change horizontal loading coverage.

## Evidence and bounds

`build/native-staged-qa/evidence/result.json` contains the authority receipt, section layout, every observed native stage join, the applicable restart boundaries, progress/failure diagnostics, material totals, passive-income accounting and explicit exclusions. Screenshots are unedited real framebuffer captures:

1. `01-live-core-five-by-five-review.png`: real owner/faction/Treasury core menu, before the actual review click.
2. `02-free-five-by-five-perimeter-review.png`: the issued free plan in the world after ordinary chat fades.
3. `03-native-completed-staged-perimeter.png`: an aerial observer view taken only after complete authority, world and conservation assertions pass.

The earlier solid-geometry one-claim calibration was 709.24 seconds for 968 targets, but it underestimated this wider 5×5 layout. An actual afb855b run maintained 20.0 TPS and completed seven finite 64-cobble withdrawals in 80–90 seconds per supply cycle. Sustained 300/420-second windows yielded 0.78/0.762 blocks per second, projecting 141–145 minutes for 6,600 targets. That run was deliberately interrupted early and retained as partial evidence, not completion.

The fresh run therefore declares a 180-minute native-work cap and 195-minute total client cap in advance, with 198-minute process and 205-minute workflow bounds. The five-minute no-progress gate remains. Only QA time budgets change; no running timeout, native AI, speed, stock, stage layout or gameplay is modified.

The existing 64 bread remains finite and unchanged. Read-only disassembly of the actually loaded Recruits 1.15.2 development JAR confirms RecruitEatGoal updates working hunger once per 20 ticks, the working hunger decrement is 0.033333335, and ordinary bread restores 25.6 hunger (5 nutrition × 5 plus 0.6 saturation). At normal 20 TPS, 180 minutes accounts for about 360 hunger, or 15 bread when rounded up without crediting initial hunger; 195 minutes accounts for about 390 hunger, or 16 bread. This is the normal working-hunger budget, not a guarantee about unforeseen injury or other native behavior. Unexpected consumption remains observable and no refill is permitted. This local loaded remapped Recruits JAR has SHA-256 f39e27973d275d546908a79cc602fe02f20917500a5df68b1fdee676bafd601c; the calculation describes its bytecode before mixins, not original release bytes or arbitrary companion versions.

Java parsing and workflow syntax checks cannot establish native API compatibility or gameplay. Runtime gates remain real menu/plan delivery, staged admission and handoffs, the applicable exact save/reopen boundaries, stock/Treasury conservation, full `COMPLETE`, and visual review. Dedicated-client networking, uneven/disjoint/holed territories, other palettes, distant storage, arbitrary shader/GPU combinations, and actual cancellation/recovery-corruption scenarios are outside this fixture.

The historic solid timing observations above are not hollow-wall gameplay evidence. Current flat hollow geometry removes 2,700 hidden body blocks and reserves those cells as clearance; the deck, skin, rails and full-width footing remain covered by independent target/non-target oracles.
