package com.devfarinsky.factionraids;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

/**
 * Central home for cross-cutting constants used across Faction Raids.
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
     * Access these through {@link com.devfarinsky.factionraids.raid.RaidTags} rather than raw NBT calls.
     */
    public static final class Tags {
        private Tags() {}
        public static final String RAID_TEAM = "FactionRaidsTeam";
        public static final String RAID_ROLE = "FactionRaidsRole";
        /**
         * v2.26.0 marker for pre-raid scouts. Scouts carry this tag AND the
         * RAID_TEAM tag so friendly-fire logic still works, but the marker
         * excludes them from raid bookkeeping (they are not counted as
         * wave spawns and their deaths do not credit raid-effort).
         */
        public static final String SCOUT = "FactionRaidsScout";
    }

    /** Standard boss-bar color for an in-progress invasion. */
    public static final BossEvent.BossBarColor BOSS_BAR_COLOR = BossEvent.BossBarColor.RED;

    /** Standard boss-bar overlay style. */
    public static final BossEvent.BossBarOverlay BOSS_BAR_OVERLAY = BossEvent.BossBarOverlay.PROGRESS;

    /** Chat message prefix reused by every player-facing announcement. */
    public static final Component MESSAGE_PREFIX = Component.literal("[Faction Raids] ")
            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);

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
