# 4.53.0-alpha.3 experimental release

Combines the published alpha.2 fixes with PR #287 replacement builders. A confirmed dead worker can be replaced by an eligible idle owned builder within 64 blocks of the original core, with the owner online and unchanged paid authority. One assigned builder per project; missing/unloaded workers do not permit replacement. Blueprint, payment, progress and finite supply accounting are preserved. Native walking clears next-section targets before admission.

Previous replacement source 27949d615257df1574540feee770bee3fd38e15e passed Build 37584299068 (2006 tests / 294 suites) and native lifecycle 37584298892. Actual death at 119 placements, replacement at 120, partial reload at 120, first 139-target section completion, between-section reload at 139, cancellation/reload at 142; one 64-emerald commission. This is a representative legacy 572-target plan with explicit QA-only <=192 sections, not whole-plan completion or gated replacement proof. No native AI acceleration or synthetic completed blocks.

Final versioned source, regression build, dedicated GameTest startup with companion mods, native safety/reload/cancellation and replacement lifecycle must pass and be pinned before uploading the exact tested reobfuscated JAR through a fresh one-use publisher. Preserve alpha.2 accepted receipt; never rerun it. Stable main/4.52.10 and update feed remain unchanged.

Limits: deep CUT, simultaneous crews, general terrain/modpacks, owner-absence gameplay, authenticated packaged dedicated multiplayer and downgrade remain unproven. Back up worlds and install exact alpha.3 everywhere with all four required companions. Dedicated startup and unit tests are separate from actual integrated-client construction/reload evidence.
