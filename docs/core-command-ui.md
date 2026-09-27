# Core Command Center

The shell uses `CoreCommandPage` for page identities, labels, titles and descriptions. These enum ordinals are client navigation only, never purchase IDs or packet fields. Adding a page requires a descriptor, its body renderer and its controls/visibility rules. Existing server action IDs must remain stable.

`CoreTabStrip` owns overflow geometry and navigation. Tabs keep a minimum width; when the viewport cannot fit all pages, previous/next buttons expose a window around the selected page. Ctrl+Tab and reverse cycling reach every page. Wheel navigation is limited to the strip. Keep hidden controls out of keyboard focus when changing pages. Do not add speculative future tabs before they have functionality.

Both money balances remain visible. The Treasury is a real, narrated button and opens the funds page. The purse is informational. All purchases remain server-authoritative and use the existing funding rules.

Page headings reserve separate 9px text lines with a gap. The shared body geometry, real armored entity portraits, sealed loot presentation, and action IDs remain intact. Intel section selectors are ordinary accessible widgets; each section remembers its scroll position while the menu is open. Culling avoids painting archive cards outside the body; wrapping measurements remain bounded in the screen cache.

Validation covers 1–40 pages across compact, ordinary, ultrawide and scaled tiny windows, selection reachability, header bounds and unchanged content bands. Interactive rendering, narration and modded GUI scales still require a Minecraft client to inspect; automated geometry tests are not a gameplay playtest.
