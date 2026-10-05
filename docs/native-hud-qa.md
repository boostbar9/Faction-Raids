# Opt-in native HUD QA

This bounded gate runs a real Minecraft 1.20.1 / Forge 47.4.16 client and a fresh
integrated world. Actual production screens, fonts, block models, companion entity
renderers, native Workers inspection widgets and Minecraft framebuffer captures
are used. The unshipped harness is `src/nativeQa/.../NativeHudQa.java`. A normal
Build pass does not establish this gate ran, and this gate does not replace JUnit,
review or gameplay acceptance.

## Run and isolation

Add `native-hud-qa` to a same-repository pull request, or dispatch **Native HUD QA**
when its workflow exists on the default branch. The job has read-only contents
permission, no secrets, disabled checkout credential persistence and read-only
Gradle cache access. It never publishes, merges, changes credentials or alters a
player's installation. All game/config/world files are isolated under
`build/native-hud-qa/client`; existing worlds and result receipts are refused.

From a fresh checkout, with Java 17 and a display:

```sh
python3 -B -m unittest discover -s scripts/tests -p 'test_prepare_native_client_defaults.py'
python3 -B -m unittest discover -s scripts/tests -p 'test_native_hud_qa.py'
python3 scripts/prepare-native-client-defaults.py --mode hud --smallships-version 2.0.0-b1.4
./gradlew --no-daemon --console=plain --max-workers=2 -PnativeQa=true -PnativeQaMode=hud prepareNativeQaClient
./gradlew --offline --no-daemon --console=plain --max-workers=2 -PnativeQa=true -PnativeQaMode=hud runClient
python3 scripts/verify-native-hud.py
```

For the full workflow contract, create `build/native-hud-qa/evidence` and write
`git rev-parse HEAD` to its `source-commit.txt` before launching. The helper must
run before creating options or other game-directory files. See the exact defaults
and refusal rules in [native-client-defaults-qa.md](native-client-defaults-qa.md).

The workflow warms the compile/launch runtime online before its offline native
run. It uses Ubuntu Xvfb at 1600×1000 and Mesa llvmpipe without fake GL versions or
replacement renderers. Each resize changes only this disposable client window;
the harness waits for its real window and framebuffer dimensions. Preparation is
capped at 20 minutes, the process at ten minutes, the harness at eight minutes,
and individual resize/capture waits at ten/fifteen seconds. The job is capped at
35 minutes. No dedicated-server EULA, account login or external listener is used.

## Exact companions

This mode shares the existing `nativeQaCompanions` pins without changing them:

- Workers 2.0.3: `curse.maven:workers-567450:8351157`
- Recruits 1.15.2: `curse.maven:recruits-523860:8339846`
- Small Ships 2.0.0-b1.4: `curse.maven:small-ships-450659:5566900`
- Siege Weapons 0.2.5: `curse.maven:siegeweapons-1259343:7906096`

The receipt records all loaded versions and SHA-256 of the actual **ForgeGradle
remapped development JARs**, not original CurseForge bytes. Native mixins remain
enabled with the existing supported SRG-to-named refmap remapping. No vendor JAR,
world, account or authentication file is uploaded.

## Evidence matrix

- All seven Core pages, all three Building sections, all three Intel sections,
  and all six selectable native-block-model plans at 960×720 GUI scales 2 and 3,
  and 1440×960 GUI scales 2 and 1. At compact scale 3, catalogue paging must reveal
  each actual plan, and its visible hitbox must select the expected blueprint.
- The four Army portraits must all retain real native living entities in the
  renderer cache. An item fallback in any populated slot fails the gate. Screens
  retain real vanilla equipment sprites alongside the actual 3D characters.
- Labeled sample Hired, unavailable, insufficient and affordable hire states;
  loading/empty construction reports; empty Treasury; civilian capacity; active
  territory upgrades and retained unavailable ownership; both unavailable upgrade
  buttons remain disabled and are excluded from the active count at every scale;
  first-click local loot confirmation and its navigation
  cancellation. The second, paid loot click is never performed.
- Civilian roster at every viewport: labeled loaded native farmer/librarian
  appearances, explicitly unavailable residents without invented models, keyboard
  paging, selection across read-only sync and the complete focusable Care tooltip.
  Compact loading and empty reports are captured separately. These are client
  display fixtures, not proof of server villagers, native AI or tax collection.
- Possible-loot gallery at all four viewports: native enchanted/trimmed epic and
  rare ItemStacks, bounded keyboard paging through later items, Royal tier-floor
  controls and Back without a purchase. Gallery examples never inspect a sealed
  reward. Review native tooltips, models and text clipping in the captures.
- Intel search focus via its visible hitbox, real OS E typing without closing the
  inventory, no-result and Clear states, wheel scrolling, per-section scroll
  memory, native OS Ctrl+Tab/Ctrl+Shift+Tab, Escape, header Close, repeated reopening,
  and a real window/GUI-scale resize preserving the selected page and query.
- The three reachable Codex pages, guide tip navigation and keyboard body handling,
  focus retention across a read-only snapshot update, Journal paging and Close.
- Main settings and Hero visuals at scales 2 and 3; empty filters, scrollable rows
  and Done/back. No option value is changed. The existing save-on-close path can
  save unchanged defaults only inside this disposable instance.
- Native inspection at scales 1, 2 and 3 with the real Workers
  `StructurePreviewWidget` and native materials control. A fresh actual native
  builder is AI-paused and a separate 15-block protected marker is initialized
  solely for rendering. This is an **uncommissioned fixture**, not a paid job or
  construction result. Cancel opens its normal confirmation; only No and Close
  are used. No projection/commission/cancel action is sent.

Every capture records actual viewport dimensions, active/focused visible widgets,
widget bounds and center-hitbox checks. Visible control clipping, overlapping
hitboxes, hidden/disabled keyboard focus, blank/missing images, missing native
portraits, missing native inspection preview or incomplete steps fail the gate.
The verifier requires the exact named image matrix and corresponding receipts.

## What the result means

Core menu and Codex data are deliberately labeled **QA SAMPLE**. They are display
fixtures, not claims about real server offers, Treasury, factions, native jobs,
commission receipts or synchronized production reports. Read-only construction
subscriptions and Codex sync retain their normal handlers; their empty isolated
server state is not presented as the sample's source. No mutable gameplay guard
is bypassed and no completed structure blocks are written.

Mouse navigation is dispatched through the actual screen/widget hitboxes; it is
not an OS mouse-routing test. E, Ctrl+Tab, Ctrl+Shift+Tab and Escape use Java's
standard `Robot` keyboard against the focused Xvfb window, so the game's ordinary
GLFW modifier checks run. Read-only reflection records selected screen state and
native portrait cache identity; it never writes private production state.

Review the PNGs as well as the receipt. Nonblank frames and valid hitboxes cannot
prove visual quality, readable paragraph wrapping or adequate model framing.
Physical GPUs, every shader/resource pack, localization, screen readers, real paid
flows, authority, rollback, native construction work and dedicated-server sessions
remain separate acceptance requirements. No successful run is claimed until a
linked workflow for the exact source has passed and its screenshots are reviewed.
