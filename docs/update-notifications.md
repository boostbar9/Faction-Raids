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
Only add `1.20.1-recommended` for a stable release; the current release channel is beta.
The initial entry is the confirmed 4.21.2 upload; advance it to the latest publicly
verified release when shipping this feature. Do not bump the feed during builds.

Players must install a version containing this feature before chat notifications
can appear; older installed JARs cannot be retroactively changed. Forge's global
version-check setting controls the network check and therefore these notices too.

Manual verification: point a development build at a test feed with a higher latest
version, join a world, wait five seconds, open chat and click View updates. Check
link confirmation, reconnect without another notice, then test an unreachable feed
and an equal/lower version. Run the same JAR on a dedicated server to confirm the
client subscriber is not loaded there.
