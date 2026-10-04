# Fresh native-client QA defaults

Before launching an opt-in client fixture, run this from a fresh isolated checkout
with Python 3.11+ (the native workflows use Ubuntu 24.04's Python):

```sh
python3 -B -m unittest discover -s scripts/tests -p 'test_prepare_native_client_defaults.py'
python3 scripts/prepare-native-client-defaults.py --mode baseline --smallships-version 2.0.0-b1.4
```

Use the mode matching the subsequent Gradle invocation:

| Helper mode / `nativeQaMode` | Only permitted game directory |
| --- | --- |
| `baseline` (Gradle default) | `build/native-qa/client` |
| `perimeter-completion` | `build/native-perimeter-qa/client` |
| `territory-perimeter` | `build/native-territory-qa/client` |
| `camp-spawn` | `build/native-camp-qa/client` |
| `staged-perimeter` | `build/native-staged-qa/client` |
| `staged-unload` | `build/native-unload-qa/client` |
| `staged-handoff` | `build/native-handoff-qa/client` |

Run the helper **before** creating `options.txt`, camp-specific fixture config, or
starting Minecraft. It creates only two Small Ships TOMLs and a provenance marker
inside that game's directory. The workflows copy the marker into their evidence
directory as `smallships-default-config-provenance.json` before normal setup.
It never prepares dedicated-server QA, a production instance, or a user game.
The `staged-unload` allowlist entry supports the separately implemented bounded
unload diagnostic. That fixture owns its labeled `RecruitsChunkLoading=false`
setup; this helper never reads or writes a Recruits configuration. The defaults
marker describes only the two Small Ships files, not other fixture settings.

The game directory must not already exist. One narrow, read-only retry is allowed:
the directory must contain exactly this helper's unchanged marker and two unchanged
configs, with no additional files or directories. Existing worlds, logs, options,
unknown/missing configs, changed bytes, symlinks, an unmarked directory (even empty),
and unsupported versions/modes are refused without altering them. There is no
arbitrary destination argument, `--force`, deletion, repair, or reset mode. After an
interrupted setup or a client launch, use another fresh isolated checkout; preserve
the old directory and its evidence. Do not run concurrently with a client or another
preparation process.

## Exact defaults and provenance

The fixtures in `scripts/fixtures/smallships-2.0.0-b1.4/` are byte-for-byte native
output from Small Ships Forge 1.20.1 **2.0.0-b1.4**, official CurseForge project
450659 / [file 5566900](https://www.curseforge.com/minecraft/mc-mods/small-ships/files/5566900),
with Minecraft 1.20.1 and Forge 47.4.16. The helper checks the native QA Gradle
coordinate is still `curse.maven:small-ships-450659:5566900` and checks hard-pinned
SHA-256 values for both fixtures before creating anything:

- `smallships-client.toml`: `bcb7919d3911085ed068e902f46de9971bf152a7b09376bc06e29e6d3d4fdf35`
- `smallships-common.toml`: `2c2914337fc96d44520ac1758afed504616cd9b13a11a0992c3d3528c79a5c5b`

`provenance.json` records the source commit, console-log hash and line ranges,
loaded remapped development JAR hash (not original release bytes), and all declared
default strings from Forge's null-to-default correction messages. The tests compare
every TOML scalar/list leaf to those declarations: 7 client leaves including schema
2, and 49 common leaves including schema 5. Java list formatting and enum strings
are compared explicitly. The duplicate `minecraft:wither` blacklist entry is
retained exactly as generated. No gameplay value or upstream schema is changed.
The fixture-only Git attributes preserve the native bytes and terminal blank line
without line-ending conversion or trimming.

## Verified scope and limitation

A fresh staged client at `11e8c55433ba3e9838e1476ec172de788529a197` failed before
world creation in Small Ships `SmallShipsConfig.getSchematicVersion:474` during a
`ModConfigEvent` with a null config key. Its eventual TOMLs were byte-identical to
the previously successful native-generated defaults from
`afb855b4b095d77d67c64f9defa97a45a44632a6`. That earlier calibration commissioned
6,600 targets and built 512 before an intentional test-budget stop.

On 2026-10-04 at 08:46:08 UTC, a separate fresh checkout of the same `11e8c55` head,
seeded only with these unchanged defaults, passed startup and actual paid
commissioning: all four companions loaded, two native sections / 6,600 targets,
Treasury 2,000 to 1,936, active native queues and AI. The helper reproduces that
prelaunch configuration setup; the defaults were copied directly for that runtime
experiment before the helper existed. This is startup evidence, not a completed
long-run acceptance result or proof of an upstream Small Ships fix.

Normal Forge config loading/validation, companion checks, mixins, gameplay values,
native AI, supply budgets, world/restart assertions, and all evidence gates remain
enabled and unchanged. This QA-only setup is not packaged into the mod JAR and is
not a migration or recommendation for existing player configurations. A companion
upgrade requires fresh native-default provenance and review; do not just change the
version argument or hashes to make setup pass.
