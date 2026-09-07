package com.devfarinsky.factionraids.raid;

import com.devfarinsky.factionraids.RaidConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * v2.34.0 &mdash; per-team Siege Commander boss bar. Small vanilla
 * {@link ServerBossEvent} pinned to the Commander mob, visible only to
 * defenders within a configurable radius, that fills with the Commander's
 * current HP and colors by pressure (yellow &rarr; red at low HP).
 *
 * <p>Separate from the raid-wide bar in {@link RaidBossBars} so both can
 * coexist &mdash; the raid bar carries phase/wave state, this one carries
 * "how close am I to killing the boss".
 *
 * <p>Lifecycle is driven from {@code RaidEvents}:
 * <ul>
 *   <li>{@link #onCommanderSpawn(String, Mob)} &mdash; called once when the
 *       Commander is picked out of a wave.</li>
 *   <li>{@link #tick(String, Mob)} &mdash; called once per raid tick from
 *       the raider iteration loop for the tagged Commander mob. Updates HP,
 *       color, and viewer set.</li>
 *   <li>{@link #onCommanderDefeated(String)} &mdash; called from
 *       {@code markCommanderDefeated} to tear the bar down cleanly.</li>
 *   <li>{@link #remove(String)} &mdash; called from raid teardown for the
 *       "raid ended without the Commander dying" case.</li>
 *   <li>{@link #shutdown()} &mdash; called from the mod shutdown hook.</li>
 * </ul>
 */
public final class CommanderBossBar {

    private CommanderBossBar() {}

    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();

    /** Percent at or below which the bar swaps YELLOW &rarr; RED. */
    private static final float LOW_HP_THRESHOLD = 0.35F;

    /**
     * Create (or refresh) the boss bar for a Commander that just spawned.
     * Safe to call twice &mdash; if a bar already exists for this team the
     * title is refreshed and the progress reset to 1.0.
     */
    public static void onCommanderSpawn(String teamKey, Mob commander) {
        if (teamKey == null || teamKey.isEmpty() || commander == null) return;
        if (!RaidConfig.COMMANDER_BOSSBAR_ENABLED.get()) return;
        Component title = buildTitle(commander);
        ServerBossEvent bar = BARS.computeIfAbsent(teamKey, key -> {
            ServerBossEvent b = new ServerBossEvent(title,
                    BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            b.setDarkenScreen(false);
            b.setPlayBossMusic(false);
            b.setCreateWorldFog(false);
            return b;
        });
        bar.setName(title);
        bar.setColor(BossEvent.BossBarColor.YELLOW);
        bar.setProgress(1.0F);
    }

    /**
     * Per-tick refresh. Called once per raider iteration for the tagged
     * Commander mob. Updates HP fill, color threshold, and prunes/adds
     * viewers based on the configured radius.
     *
     * <p>No-op when the feature is disabled or no bar exists for this team.
     */
    public static void tick(String teamKey, Mob commander) {
        if (teamKey == null || teamKey.isEmpty() || commander == null) return;
        ServerBossEvent bar = BARS.get(teamKey);
        if (bar == null) return;
        if (!RaidConfig.COMMANDER_BOSSBAR_ENABLED.get()) {
            remove(teamKey);
            return;
        }
        if (!(commander.level() instanceof ServerLevel level)) return;

        float maxHp = commander.getMaxHealth();
        float hp = commander.getHealth();
        float progress = maxHp <= 0F ? 0F : Math.max(0F, Math.min(1F, hp / maxHp));
        if (Math.abs(bar.getProgress() - progress) > 0.001F) {
            bar.setProgress(progress);
        }
        BossEvent.BossBarColor desired = progress <= LOW_HP_THRESHOLD
                ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW;
        if (bar.getColor() != desired) bar.setColor(desired);

        // Viewer reconciliation: everyone within radius sees the bar,
        // anyone else is removed. Cheap because radius is small and the
        // player list is short in Minecraft servers.
        int radius = RaidConfig.COMMANDER_BOSSBAR_RADIUS.get();
        double rSq = (double) radius * radius;
        java.util.Set<java.util.UUID> current = new java.util.HashSet<>();
        for (ServerPlayer p : bar.getPlayers()) current.add(p.getUUID());

        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            boolean inRange = p.distanceToSqr(commander) <= rSq;
            if (inRange && !current.contains(p.getUUID())) {
                bar.addPlayer(p);
            } else if (!inRange && current.contains(p.getUUID())) {
                bar.removePlayer(p);
                current.remove(p.getUUID());
            }
        }
        // Any UUID left in `current` that isn't in the player list this tick
        // (dimension change, disconnect) also gets pruned. Iterate a copy so
        // we can mutate the bar's viewer set.
        for (ServerPlayer p : new java.util.ArrayList<>(bar.getPlayers())) {
            if (p.level() != level || p.hasDisconnected()) {
                bar.removePlayer(p);
            }
        }
    }

    /**
     * Commander was defeated. Tear down cleanly &mdash; no lingering bar,
     * no orphaned viewers.
     */
    public static void onCommanderDefeated(String teamKey) {
        remove(teamKey);
    }

    /**
     * Force-remove the bar for a team (raid ended, feature disabled at
     * runtime, etc.). Safe to call when no bar exists.
     */
    public static void remove(String teamKey) {
        ServerBossEvent bar = BARS.remove(teamKey);
        if (bar != null) bar.removeAllPlayers();
    }

    /** Drop every viewer, then clear the map. Safe to call on server shutdown. */
    public static void shutdown() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    /** Apply an action to every registered bar. Useful for debugging. */
    public static void forEach(Consumer<ServerBossEvent> action) {
        BARS.values().forEach(action);
    }

    // ---- helpers ----

    private static Component buildTitle(LivingEntity commander) {
        // Prefer the Commander's already-styled name tag if RaiderLabels
        // set one, so faction-specific renames (future work) flow through
        // for free. Fall back to the plain literal.
        Component tag = commander.getCustomName();
        if (tag != null) return tag;
        return Component.literal("Siege Commander").withStyle(ChatFormatting.DARK_RED);
    }
}
