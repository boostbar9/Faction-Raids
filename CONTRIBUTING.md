# Developing Siege Overhaul

## Environment

Use Java 17 and the checked-in Gradle wrapper. Minecraft, Forge, mappings and the mod version are pinned in gradle.properties; ForgeGradle and test dependencies live in build.gradle. Do not upgrade the Minecraft/Forge target as part of an unrelated fix.

On Linux/macOS, run from the repository root:

```sh
java -version
./gradlew --no-daemon --console=plain testClasses
./gradlew --no-daemon --console=plain clean build
```

On Windows use .\gradlew.bat instead of ./gradlew. testClasses prepares the development dependencies and compiles tests; it does not execute them. clean build executes the JUnit suite and produces the reobfuscated release JAR. There is no separate configured lint task.

The first Forge setup downloads tooling, mappings and Minecraft artifacts and can take longer than cached builds. GitHub's Copilot setup workflow installs Temurin 17 and warms these dependencies. It uses read-only repository permissions and no release secrets. If setup fails, read the failing Actions step before changing configuration. For blocked downloads, identify the actual host and ask the maintainer to permit the required host in Copilot settings; do not disable the firewall.

Focused example:

```sh
./gradlew --no-daemon test --tests 'com.devfarinsky.siegeoverhaul.core.EnemyHeroesTest'
```

Tests: src/test/java; shared bootstrap: MinecraftTestSupport. XML: build/test-results/test. HTML: build/reports/tests/test/index.html. CI uploads regression-test-results and Faction-Raids-Forge-1.20.1 artifacts.

Interactive testing needs a separate Minecraft 1.20.1 Forge instance with this JAR and all four required companion mods on server and client. Build-time reflection does not install those mods into the development run directory. runClient/runServer are configured in Gradle but are not a complete companion-mod setup by themselves. Never claim playtesting from a successful compilation or mocked test.

## Code map

All Java packages below are under src/main/java/com/devfarinsky/siegeoverhaul.

| Area | Entry points |
| --- | --- |
| Siege lifecycle and persistence | RaidEvents.java, RaidSavedData.java, RaidConfig.java |
| Wave selection and heroes | waves/WaveComposer.java, core/EnemyHeroes.java, core/CoreHiring.java, core/CoreOffers.java, core/HeroTraits.java |
| Camp construction and defense | camp/; CampBuilder, NativeCampConstruction, CampDevelopment, CampUpgradeLayout, CampGuards |
| Native mod integrations | RecruitsBridge.java, OptionalCompatBridge.java, compat/ |
| Recruitment, bank, purchases and inventory | core/ |
| GUI, responsive layout, configuration UI | client/ |
| Combat movement and siege equipment | raid/, formations/, siege/, naval/ |
| Resources and dependency declarations | src/main/resources; META-INF/mods.toml |

Read the relevant implementation and neighboring tests before editing. RaidEvents is large: prefer focused helpers over broad restructuring. Keep Minecraft client-only classes out of dedicated-server paths.

For Recruits/Workers bridge changes, compare the matching upstream source, including the owning manager, argument/return types and lifecycle. See [the pinned compatibility review](docs/upstream-compatibility.md). Record the source commit used; source review and independent API fixtures are not live companion-mod playtesting.

## Copilot workflow

Repository instructions live in .github/copilot-instructions.md; AGENTS.md points other coding agents to the same guidance. GitHub's cloud agent setup is .github/workflows/copilot-setup-steps.yml.

For a new task, create an issue using the Development task template and assign it to Copilot if your account/repository has cloud-agent access. Give it the desired player-visible result, reproduction steps or acceptance criteria, and explicit scope. Review its PR and Build results before merging. Account entitlement, repository agent enablement and branch protection are GitHub settings; committed files do not turn these on.

Continue from the latest main, not from an older conversation's release number. Capture decisions and remaining work in PR descriptions/issues so the next developer does not need private chat history. Keep changes bounded: one feature or coherent fix per PR.

## Release handoff

For gameplay releases, update mod_version and CHANGELOG.md, then verify the final PR head and successful merged-main Build. Upload the exact tested reobfuscated JAR. Check META-INF/mods.toml, version, required/optional dependencies and regression XML; record its SHA-256 and source/build IDs.

The maintainer's release workflow includes CurseForge (project 1364352). A coding agent must still respect its actual permissions and merge/publish policies. If it cannot perform release steps, leave the tested artifact and a concrete handoff rather than claiming publication.

Recent verified-artifact publishing workflows are on publish/curseforge-v<version> branches. For example, publish/curseforge-v4.28.12 downloads the successful main Build artifacts and validates source tree, tests, metadata and JAR before upload. Treat that as a historical example: replace all version, PR, commit, tree and run IDs for a new release, and derive test counts from actual results.

The older main-branch publish.yml rebuilds with tests excluded; do not use it as evidence that an upload contains the tested bytes. release.yml is tag-triggered and also rebuilds. Creating release tags is therefore a publishing action, not routine task setup.

Required CurseForge dependencies: recruits, workers, small-ships, siegeweapons. Optional: corpse, epic-knights-armor-and-weapons, ewewukeks-musket-mod. Release credentials belong only in trusted release workflows; never copy them into issues, source, Copilot setup or logs.

Avoid duplicate uploads after ambiguous failures: check the run logs and upload receipt first. Report accepted upload and public moderation status separately. Documentation and development-tooling changes do not need a version bump or CurseForge JAR.

## Useful references

- [Copilot repository instructions](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions)
- [Copilot development environment](https://docs.github.com/copilot/how-tos/use-copilot-agents/coding-agent/customize-the-agent-environment)
