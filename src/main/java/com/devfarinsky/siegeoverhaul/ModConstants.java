package com.devfarinsky.siegeoverhaul;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

/**
 * Central home for cross-cutting constants used across The Siege Overhaul.
 *
 * <p>v3.0.0 rename: the mod was formerly published as 'Faction Raids' under
 * mod id {@code factionraids}. Entity persistent-data tags on this class
 * deliberately keep their legacy {@code FactionRaids*} on-disk names to
 * avoid per-entity migration cost. Rename happens only at surfaces where
 * it must (mod id, package, SavedData filename, config file, asset
 * directory) -- those are handled by
 * {@link com.devfarinsky.siegeoverhaul.rebrand.RebrandMigration} on
 * server about-to-start.
 *
 * <p>Concentrating tick math, persistent-data tag names, chat styling, and boss-bar
 * colors here keeps behavior consistent as new features are added and prevents the
 * "magic number" drift that grew in {@code RaidEvents} across versions 1.0 through 2.7.
 */
public final class ModConstants {
    private ModConstants() {}

    /** Minecraft server tick rate. Use this instead of hard-coding {@code 20}. */
    public static final int TICKS_PER_SECOND = 20;

    /** Ticks between periodic {@code RaidEvents} bookkeeping passes. */
    public static final int TICK_INTERVAL = TICKS_PER_SECOND;

    /**
     * Persistent-data NBT keys attached to invasion entities.
     * Wire names are historical; a code-side rename would break save compat
     * for zero player-visible benefit.
     */
    public static final class Tags {
        private Tags() {}
        // v3.0.0 note: on-disk tag names deliberately keep the
        // legacy 'FactionRaids' prefix. These are internal keys never
        // shown to players; renaming would require a per-entity
        // migration pass on every loaded chunk with no reliable trigger
        // for unloaded entities. Not worth the risk. Codepaths that
        // want a clean canonical name can go through
        // {@link com.devfarinsky.siegeoverhaul.raid.RaidTags} which
        // hides the wire name behind a stable API surface.
        public static final String RAID_TEAM = "FactionRaidsTeam";
        public static final String RAID_ROLE = "FactionRaidsRole";
        /**
         * v2.26.0 marker for pre-raid scouts. Scouts carry this tag AND the
         * RAID_TEAM tag so friendly-fire logic still works, but the marker
         * excludes them from raid bookkeeping (they are not counted as
         * wave spawns and their deaths do not credit raid-effort).
         */
        public static final String CAMP_WORKER_TEAM = "SiegeOverhaulCampWorkerTeam";
        public static final String CAMP_JOBS = "CampConstructionJobs";
        public static final String CAMP_CREW = "CampConstructionCrew";
        public static final String CAMP_USES_WORKERS = "CampUsesWorkers";
        public static final String CAMP_BUILD_TICKS = "CampBuildTicks";
        public static final String SCOUT = "FactionRaidsScout";
    }

    /** Legacy mod id, used only by the migration reader. */
    public static final String LEGACY_MOD_ID = "factionraids";

    /** Standard boss-bar color for an in-progress invasion. */
    public static final BossEvent.BossBarColor BOSS_BAR_COLOR = BossEvent.BossBarColor.RED;

    /** Standard boss-bar overlay style. */
    public static final BossEvent.BossBarOverlay BOSS_BAR_OVERLAY = BossEvent.BossBarOverlay.PROGRESS;

    /**
     * Chat message prefix reused by dashboard command responses (status,
     * territory list, help). Runtime raid announcements route through
     * {@link com.devfarinsky.siegeoverhaul.chat.ChatStyle} and do NOT use
     * this prefix.
     *
     * <p>v2.31.0 Chat Presentation Overhaul: the bracket prefix
     * {@code [Faction Raids]} was replaced with a faint diamond glyph so
     * command output visually matches raid announcements without shouting
     * the mod name on every line.</p>
     */
    public static final Component MESSAGE_PREFIX = Component.literal("\u25c6 ")
            .withStyle(ChatFormatting.DARK_GRAY);

    /** Convert seconds to server ticks. */
    public static int secondsToTicks(int seconds) {
        return seconds * TICKS_PER_SECOND;
    }

    /** Convert seconds to server ticks (long variant to avoid overflow on large values). */
    public static long secondsToTicks(long seconds) {
        return seconds * TICKS_PER_SECOND;
    }

    /** Convert ticks to whole seconds, rounding down. */
    public static int ticksToSeconds(int ticks) {
        return ticks / TICKS_PER_SECOND;
    }
}
