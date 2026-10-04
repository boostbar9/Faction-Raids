# Native Building server-only QA

This opt-in suite uses Forge's supported `runGameTestServer` mode on the physical
`DEDICATED_SERVER` distribution. It loads the actual pinned Workers 2.0.3,
Recruits 1.15.2, Small Ships, and Siege Weapons artifacts. It is independent of
`src/nativeQa` and never packages either QA source set into the production JAR.

## Run

Use a fresh checkout/build directory, Java 17, and the checked-in wrapper:

```sh
python3 scripts/generate-native-server-template.py --check
./gradlew --no-daemon --console=plain --max-workers=2 -PnativeServerQa=true runGameTestServer
```

The separate **Native Building Server QA** workflow supports manual dispatch or
the `native-building-server-qa` label on a same-repository PR. Its permissions are
read-only, credentials are not persisted, and execution is bounded to 20 minutes
inside a 30-minute job. The ordinary Build and native client workflow remain
separate checks.

The run refuses an existing fixture world, EULA file, or completed result. It uses
only `build/native-server-qa/server/worlds/siege-native-server-fixture`. It does
not create `eula.txt`, change authentication, configure a public server, or request
credentials. Do not substitute `runServer` or edit legal/authentication settings
if this supported GameTest route encounters a prompt; stop and report it.

## What it verifies

One required GameTest checks these seven groups and writes its actual execution
count, physical/logical side, server class, loaded versions, remapped runtime JAR
hashes, assertions, and limitations to `build/native-server-qa/evidence/result.json`:

1. Dedicated distribution, GameTestServer thread, real companion capability
   checks, production class signatures after server stripping, and zero players.
2. Actual registered protected/native builder factories, native noncreative queue
   initialization, marker/blueprint origin separation, and unpaid job denial.
3. Real Workers packet mutation entry points: blueprint/creative entity payload,
   owner/team, delete, position overloads, clearing, and unverified completion.
4. Native entity NBT round trip, defensive nested input/output copies, denied
   restart, valid queue reconstruction, and malformed sealed-save rejection.
5. Reconstruction refuses unloaded blueprint chunks without loading them.
6. Real loaded-world headroom changes invalidate accepted clearance; actual
   world SavedData is flushed to disk and read back with exact reservation,
   edit history, and generation checks; explicit retirement survives NBT reload.
7. Direct handler calls with explicitly labeled Forge FakePlayer actors reject
   outsiders, distant owners, and spectators, while nearby owner show/hide/cancel
   preserves stock/ownership and retires the exact loaded native job reference.

The workflow checks every expected group and the in-test completion marker in
addition to the standard GameTest process exit status. A missing test or missing
report cannot pass. Required failures propagate through Gradle and the shell's
`pipefail`; there is no custom successful exit or swallowed exception. Only
reports and logs are uploaded, never companion JARs or generated worlds.

## Exact limitations

Physical dedicated distribution in Forge GameTestServer with real companion
mods. FakePlayer actors test direct handler authorization only; real multiplayer
connection/authentication remains unverified. Fixture initialization is not
production commissioning, native AI construction, rendering, or a full server
restart. The entity round trip and actual SavedData disk flush/read are not a
server-process restart. Client QA covers separate gameplay/rendering scenarios.

## Supported Forge references

- [Forge GameTest documentation](https://docs.minecraftforge.net/en/1.20.x/misc/gametest/)
  describes the server CI configuration and required-failure exit status.
- [Forge 1.20.1 GameTest entry point](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/gametest/GameTestMain.java)
  selects the normal supported mode.
- [Forge 1.20.1 Main patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/server/Main.java.patch)
  constructs GameTestServer through the server-loading path.
- [Forge 1.20.1 Eula patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/server/Eula.java.patch)
  explicitly recognizes GameTest CI; this suite does not manufacture an acceptance file.
- [Forge 1.20.1 GameTestServer lifecycle patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/gametest/framework/GameTestServer.java.patch)
  runs the normal server lifecycle hooks.

The 113-byte empty NBT structure is generated entirely from the checked-in Python
source. It contains scene dimensions and an air palette, with no copied vendor
assets, entities, blocks, or existing-world data.
