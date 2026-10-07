# 4.53.0-alpha.2 experimental release

This candidate contains PR #286 gate-waypoint recovery and depleted-material queue recovery, based on published 4.53.0-alpha.1. It remains Alpha; stable main, 4.52.10 and updates.json are unchanged. Dead-builder replacement in draft PR #287 is excluded.

## Evidence and scope

- Recovery production source: `95dd161d371dce9bbc906af0e27d6b3e08a236f8`.
- Full regression Build: https://github.com/boostbar9/Faction-Raids/actions/runs/37569414853 ; downloaded XML verifies 1,995 tests / 293 suites with zero failures/errors/skips.
- Actual stepped/gated construction and world restarts: https://github.com/boostbar9/Faction-Raids/actions/runs/37569414849 ; artifact 11464486536, status passed, 3,831 completed blocks, one 64-emerald debit, finite material conservation, mid-stage restart at 871 and between-stage restart at 3,480.
- The native checkout merge tree equals the recovery branch tree. Only version and release documents change for this candidate. Final versioned source/build, dedicated startup with all required companion mods, exact reobfuscated JAR hash and release receipt must be pinned before publication.

## Focused release review

Review the narrow production delta from published alpha.1: guard-authorized PREPARE_PLACE_BLOCKS target reset, saved gate-authority fallback and consumed-waypoint handling. Native queue/material selection and movement remain upstream; no direct world writes, new fees, inventory copying, claim bypass or excavation are introduced. The native behavioral regressions exercise the pinned Workers goal and finite 64-item requests. The successful full native fixture addresses the two diagnosed stalls.

This review does not claim independent human review or general gameplay completion. A release-specific source review and all final checks must be recorded before invoking the existing pinned-artifact publisher. Do not rerun the already accepted alpha.1 upload or use its version/source/artifact/hash pins.

## Alpha limitations

The completed fixture is one 25-chunk stepped/gated layout, not every terrain or modpack. Deep excavation/CUT, authenticated packaged multiplayer, broad retry/cancellation scenarios and safe downgrade remain unproven. Use a backed-up world copy and install exactly this alpha version on server and clients with Recruits 1.15.2+, Workers 2 2.0.3+, Small Ships and Siege Weapons. Avoid stable promotion.
