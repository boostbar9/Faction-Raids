package com.devfarinsky.factionraids.raid;

import com.devfarinsky.factionraids.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns the map of per-team {@link ServerBossEvent} instances used to display invasion
 * progress bars.
 *
 * <p>Extracted from {@code RaidEvents.BOSS_BARS} so upcoming features (multiple stacked
 * bars, per-defense-point bars, cross-mod bar theming) have a single collaborator to
 * mock or extend, and so a future refactor can hold state on a per-server basis instead
 * of one static map.
 */
public final class RaidBossBars {
    private RaidBossBars() {}

    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();

    /** Fetch or create the bar for a team, applying the standard color/overlay. */
    public static ServerBossEvent getOrCreate(String teamKey, Component title) {
        return getOrCreate(teamKey, title, ModConstants.BOSS_BAR_COLOR, ModConstants.BOSS_BAR_OVERLAY);
    }

    /** Fetch or create the bar for a team with a specific color and overlay style. */
    public static ServerBossEvent getOrCreate(String teamKey, Component title,
                                              BossEvent.BossBarColor color,
                                              BossEvent.BossBarOverlay overlay) {
        return BARS.computeIfAbsent(teamKey, key -> {
            ServerBossEvent bar = new ServerBossEvent(title, color, overlay);
            bar.setDarkenScreen(false);
            bar.setPlayBossMusic(false);
            bar.setCreateWorldFog(false);
            return bar;
        });
    }

    /** Returns the current bar for a team without creating one. */
    public static ServerBossEvent get(String teamKey) {
        return BARS.get(teamKey);
    }

    /** Remove and return the bar for a team (does not call {@link ServerBossEvent#removeAllPlayers()}). */
    public static ServerBossEvent remove(String teamKey) {
        return BARS.remove(teamKey);
    }

    /** Apply an action to every registered bar. Useful for shutdown clean-up. */
    public static void forEach(Consumer<ServerBossEvent> action) {
        BARS.values().forEach(action);
    }

    /** Clear all bars from tracking. Callers are responsible for removing viewers first. */
    public static void clear() {
        BARS.clear();
    }

    /** Drop every viewer, then clear the map. Safe to call on server shutdown. */
    public static void shutdown() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }
}
