# Copilot auto-publish workflow

This repository now includes an automatic CurseForge publish workflow for Copilot update branches:

- Workflow file: `.github/workflows/publish-copilot-updates.yml`
- Trigger: pushes to `copilot/**`
- Trigger: successful completion of **Running Copilot cloud agent** on `copilot/**`
- Default channel: `beta`

## What it does

On each qualifying push, the workflow:

1. Builds the mod (`./gradlew clean build -x test`)
2. Verifies `CURSEFORGE_TOKEN` exists
3. Selects a publishable jar from `build/libs` (skips `-sources`/`-javadoc`)
4. Validates artifact size to catch malformed outputs
5. Publishes to CurseForge project `1364352`
6. Writes a run summary with artifact path, version, channel, and run link

## Reliability and safety guardrails

- **Concurrency lock per ref**: prevents overlapping publishes from the same branch.
- **Deterministic beta versioning**: `mod_version-copilot.<run_number>+<short_sha>`.
- **Hard-fail checks**: token missing, no jar found, or suspiciously tiny jar all fail the run immediately.
- **Skip switch**: include `[skip publish]` in a commit message to bypass auto-publish for that push.
- **Manual fallback**: `workflow_dispatch` supports publishing a specific ref and selecting `beta` or `release`.

## How to use

- Normal path: merge/push Copilot updates to a `copilot/**` branch and let automation publish.
- Preferred path: once the Copilot run succeeds, the follow-up `workflow_run` trigger publishes the exact `head_sha`.
- Skip once: add `[skip publish]` to commit message.
- Manual publish: run **Auto-publish Copilot updates to CurseForge (beta)** from Actions and provide a ref if needed.
