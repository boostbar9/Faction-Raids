package com.devfarinsky.factionraids.formations;

/**
 * Formation styles Faction-Raids can command Recruits raiders into.
 *
 * <p>Backed by {@code com.talhanation.recruits.util.FormationUtils}. Only
 * formations whose Recruits implementation has a raw-vector overload (no
 * ServerPlayer needed for facing) are exposed here — the mod ships with
 * more shapes, but they all need a real player to derive yaw. Those will
 * become available in a later PR once we build our own facing helper.
 */
public enum Formation {
    /** No formation guidance — raiders advance individually. */
    NONE,
    /** Single line perpendicular to the advance vector. Good for early waves. */
    LINE,
    /** Filled square, three rows deep. Good for mid+ waves and command assault. */
    SQUARE
}
