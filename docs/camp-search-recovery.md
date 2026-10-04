# Bounded camp search recovery (4.52.5)

## What was failing

The reported order, natural search followed by earthworks followed by an attack without a base, was the explicit camp-less fallback. That fallback remains supported. This change improves the safe placement attempts before it rather than preventing raids indefinitely.

A supplied 4.52.1 session log contained 152 failed expanded site searches: 1,306 individual offsets rejected by `EDGE`, 817 by `NO_LAND_EXIT`, 359 by `RELIEF`, 284 by `CLEARANCE`, 143 by `FLUID` and 61 by `SOIL`; 830 further offsets failed the preliminary surface check. No claim-registration or terrain-application failures were recorded in those searches. One search timed out with only 143 of its 200 candidate neighborhoods selected. These observations identify the important rejection classes; they do not prove every rejected site was usable or reproduce the original world.

Two reproducible false-negative paths were found in source:

1. Natural scouting rejected height differences over three blocks before reaching the existing six-block safe cut/fill planner. When full grading and restoration are enabled, the balanced height proposal and full planner are now used. The strict old surface test remains for configurations without that planner.
2. The expanded grading solver demanded one-block slopes against every immutable outer edge. A four-block rock spike at the outside survey boundary could therefore reject an already flat 19×19 core with clear exits. A second, bounded solver now preserves existing remote steepness without increasing it, while retaining a one-block walkable inner collar and a continuous three-wide exit. The ordinary solver and ordinary terrain mode remain unchanged.

## Safety and finite work

- Native Recruits claims remain Overworld-only, with the same 25-chunk footprint and neighboring-claim buffer. Existing foreign-claim, border, height and loaded-chunk checks still apply.
- No new rock excavation, protected-block replacement, deep-water paving, unsupported foundation or relaxed block budget is introduced. Every resulting height still goes through the complete original block-by-block planner and its atomic application/restoration checks.
- The alternative edge solver runs only after an expanded `EDGE` failure. It checks at most four exits, each with three priority-queue transforms over at most 1,085 surveyed cells. Core and immutable boundary heights are fixed. A one-block collar around the camp and every edge touching the chosen three-wide exit retain a one-block maximum step; elsewhere the original step may be retained but never increased.
- Existing natural and optional earthworks scouting remain capped at 200 candidate neighborhoods and four minutes of active time each. A one-minute active-time regroup now precedes one additional recovery pass, with the same per-pass caps. It interleaves roughly 608–992-block radii so slow chunk loading cannot consume the entire pass on its nearest ring.
- At most one search neighborhood is ticketed. Claim-only skips remain capped at eight per call; local candidate counts, ticket lifetime and per-candidate load deadline do not increase. Regrouping consumes no candidate, terrain reads or pass time.
- The recovery flag, cooldown, candidate, pass progress and diagnostics survive reloads. Existing offline pause behavior remains in force. Older already-abandoned raids keep their preparation/waves rather than restarting scouting. The administrator's explicit `skipscout` override is retained.
- With earthworks enabled, the additional bounded recovery permits at most thirteen minutes of active remote search/regrouping in total; actual elapsed wall time depends on server tick rate. Camps-disabled and impossible-terrain paths still support the original camp-less assault.

## Verification contract

- Full `clean build`: all normal JUnit regressions, including production site selection on safe soil mounds, protected container/claim refusals, conservative edge geometry, persistence, candidate limits and terminal fallback.
- Existing `Native Camp Spawn QA`: all three natural/shallow-water/rocky native establishment cases remain required.
- New `Native Camp Lifecycle QA`: real non-op command, ordinary production scouting, actual save/close/reopen during regrouping, final camp-less fallback on deep ocean, and a supported recovery-only island beyond the old range. The fresh fixture uses the supported three-minute preparation setting to observe actual first waves without altering raid timers. See the lifecycle QA document for exact evidence and scene boundaries.
- Local syntax parsing and Python contract tests are supplementary only. This cloud workspace cannot download Gradle and has no Java 17 runtime, so Java compilation, JUnit and native acceptance must run on exact source commits in GitHub Actions before merge/release.

The test evidence is representative, not a claim that arbitrary mountains, oceans, structures, mod packs or protected worlds always contain a valid camp.
