# Siege Overhaul development

This is The Siege Overhaul (formerly Faction Raids), a Minecraft 1.20.1 Forge 47.x mod using Java 17. Read CONTRIBUTING.md for environment setup, architecture and release procedure. Current source and gradle.properties are authoritative; chat history and old changelog entries can describe obsolete behavior.

## Build and verification
- Use the checked-in Gradle wrapper, never a globally installed Gradle. Linux: ./gradlew --no-daemon --console=plain clean build. Windows: .\gradlew.bat clean build.
- For focused regression work: ./gradlew --no-daemon test --tests 'com.devfarinsky.siegeoverhaul.core.EnemyHeroesTest'. Finish gameplay changes with the full build.
- Tests use JUnit 5 and Mockito. Minecraft-dependent tests extend MinecraftTestSupport. Reports are in build/test-results/test and build/reports/tests/test.
- Production output is build/libs/siegeoverhaul-<mod_version>.jar, reobfuscated by the build. Passing unit tests does not prove interactive Minecraft behavior.
- Keep required runtime mods: Villager Recruits 1.15.2+, Villager Workers 2 2.0.3+, Small Ships and Siege Weapons. They are integrated through compatibility bridges and are not automatically installed for runClient/runServer.
- Do not disable tests, dependency checks, the agent firewall or security checks to get a green result. Report blocked access precisely.

## Gameplay invariants
- Siege targets come from Siege Cores in faction Overworld claims; do not restore bed/respawn-anchor auto-targeting.
- Currency, purchases, inventory delivery, ownership and combat decisions are server-authoritative. Account for all 36 player inventory slots and synchronize open menus.
- Keep faction Treasury amounts distinct from personal emeralds. Preserve signed, aggregated faction notifications.
- Keep UI rendering and hitboxes aligned across Minecraft GUI scales and window sizes. Do not expose hidden loot rewards before the reveal.
- Use native Recruits/Workers/Ships/Siege Weapons behavior through existing bridges. Enemy units remain unhireable; abilities must respect faction identity.
- Preserve finite spawn/material budgets, loaded-chunk and collision checks, claim protection, and restoration that respects later player edits.
- Preserve NBT keys and backwards-compatible defaults. Save random decisions that must survive retries/reloads. Temporary summons need saved expiry and cleanup.

## Working and handoff
- Start from current main on a focused branch; inspect relevant source/tests before editing. Avoid unrelated rewrites and new dependencies unless needed.
- Add meaningful regressions for behavioral fixes; documentation-only changes need no synthetic tests.
- Use PRs and existing Build checks. Do not merge or publish from a Copilot session if its permissions or repository policies disallow it.
- For gameplay releases, update mod_version and CHANGELOG.md. The maintainer expects CurseForge delivery as part of finished releases; follow CONTRIBUTING.md and existing authorization. Documentation/tooling-only changes do not need a new mod JAR.
- Report changed behavior, checks actually run, untested behavior, PR/build links and release status. Never equate accepted CurseForge upload with confirmed public availability.
