# Update notifications

Forge checks `updates.json` on the repository's main branch asynchronously at game startup.
After five seconds in a world, the client reads that cached result once a second until
an update is available. A notice appears only once per Minecraft launch, including
across reconnects. Offline, disabled, failed, current, and development builds do not
produce warning messages. Nothing is automatically installed.

The download link uses Minecraft's normal external-link handling. The notice reminds
multiplayer players to coordinate matching client and server versions.

## Release checklist

After CurseForge accepts a release and its download is publicly available, update
`updates.json` on main: add its changelog under `1.20.1` and set `1.20.1-latest` to
the published version. Do not advertise a PR, CI-only build, or pending upload.
Set `1.20.1-recommended` only for a publicly verified Release-channel version that
has been selected as recommended. Other published release channels may advance
`1.20.1-latest` after public verification; this does not require future versions to
use the Release channel. Do not bump the feed during builds.

The current publicly verified recommended Release is **4.52.9**.
The [4.52.9 release record](release-4.52.9.md) identifies its exact source, artifact,
Release channel and public-verification evidence. The historical
[4.52.7 record](release-4.52.7.md) and [4.52.8 record](release-4.52.8.md) preserve
the preceding grass-compatibility and hub/capture releases. These records
distinguish verified accepted artifacts from unverified separate public-download
byte hashes.

Players must install a version containing this feature before chat notifications
can appear; older installed JARs cannot be retroactively changed. Forge's global
version-check setting controls the network check and therefore these notices too.

Manual verification: point a development build at a test feed with a higher latest
version, join a world, wait five seconds, open chat and click View updates. Check
link confirmation, reconnect without another notice, then test an unreachable feed
and an equal/lower version. Run the same JAR on a dedicated server to confirm the
client subscriber is not loaded there.
