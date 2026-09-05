package com.devfarinsky.factionraids;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Locale;
import java.util.Map;

/**
 * v2.15.0 "Clear Intent" - raider role name tags + role-colored outlines.
 *
 * <p>Two visibility layers:
 * <ul>
 *   <li><b>Name tag</b> - via {@link Mob#setCustomName} / {@link Mob#setCustomNameVisible}.
 *       Applied at spawn by {@link #applyRole}. Visibility mode (OFF /
 *       PROXIMITY / ALWAYS) is enforced per tick by {@link #tick}.</li>
 *   <li><b>Glowing outline</b> - vanilla {@link MobEffects#GLOWING}
 *       colored by team scoreboard membership. Registered once per server
 *       via {@link #ensureTeams(Scoreboard)}. Renewed each tick while the
 *       raider is visible so the effect never wears off; removed when it
 *       falls out of the proximity window.</li>
 * </ul>
 *
 * <p>All logic no-ops when the raider's persistent data has no role tag
 * (unrelated mobs are never touched). Applying tags to already-spawned
 * raiders is safe - {@link #applyRole} is idempotent.
 */
public final class RaiderLabels {

    /** Persistent-data key matching RaidEvents.RAID_ROLE_TAG. */
    public static final String RAID_ROLE_TAG = "FactionRaids_Role";

    /** Team-name prefix for the scoreboard teams that color raider outlines. */
    private static final String TEAM_PREFIX = "fraid_role_";

    /**
     * Role -> (display name, team color). Order here defines the roles
     * this system knows about; roles not in the map render as an
     * uncolored "Raider" tag.
     */
    private static final Map<String, RoleStyle> ROLES = Map.of(
            "commander",  new RoleStyle("Siege Commander", ChatFormatting.DARK_RED),
            "breacher",   new RoleStyle("Breacher",         ChatFormatting.RED),
            "warcaster",  new RoleStyle("Warcaster",        ChatFormatting.DARK_PURPLE),
            "captain",    new RoleStyle("Captain",          ChatFormatting.GOLD),
            "marksman",   new RoleStyle("Marksman",         ChatFormatting.YELLOW)
    );

    private RaiderLabels() {}

    /**
     * Apply the role's name tag and (if enabled) glow team membership to
     * a freshly-spawned raider. Safe to call more than once.
     *
     * <p>Called from {@link RaidEvents#assignSiegeRole} right after the
     * role tag is stamped on the raider's persistent data.
     */
    public static void applyRole(Mob raider, String role) {
        if (role == null || role.isEmpty()) return;
        RoleStyle style = ROLES.get(role);
        if (style == null) return;

        RaidConfig.LabelMode mode = RaidConfig.RAIDER_LABEL_MODE.get();

        // Name tag: set once. Visibility is toggled per-tick, but the tag
        // has to exist before we can toggle its visibility.
        if (mode != RaidConfig.LabelMode.OFF) {
            raider.setCustomName(Component.literal(style.displayName)
                    .withStyle(style.color));
            // ALWAYS mode makes the tag visible immediately; PROXIMITY
            // will flip it in tick() based on nearest player distance.
            raider.setCustomNameVisible(mode == RaidConfig.LabelMode.ALWAYS);
        }

        // Team membership: join the raider to its role team so its glow
        // outline (applied per-tick) inherits the team color. We do this
        // even in PROXIMITY mode - team membership is cheap and lets the
        // effect become visible the moment the raider enters range.
        if (RaidConfig.RAIDER_GLOW.get() && raider.level() instanceof ServerLevel serverLevel) {
            Scoreboard scoreboard = serverLevel.getScoreboard();
            ensureTeams(scoreboard);
            PlayerTeam team = scoreboard.getPlayerTeam(TEAM_PREFIX + role);
            if (team != null) {
                scoreboard.addPlayerToTeam(raider.getStringUUID(), team);
            }
        }
    }

    /**
     * Per-tick visibility maintenance for one raider. Toggles the name
     * tag and refreshes the glowing effect based on the configured mode
     * and the nearest player distance.
     *
     * <p>Called from the main raid loop for every tracked raider (already
     * iterating them for other reasons, so this adds no scan cost).
     */
    public static void tick(Mob raider) {
        String role = raider.getPersistentData().getString(RAID_ROLE_TAG);
        if (role.isEmpty()) return;
        if (!ROLES.containsKey(role)) return;

        RaidConfig.LabelMode mode = RaidConfig.RAIDER_LABEL_MODE.get();
        boolean glowEnabled = RaidConfig.RAIDER_GLOW.get();

        if (mode == RaidConfig.LabelMode.OFF && !glowEnabled) {
            // Both systems off - make sure we're not leaving stale state.
            if (raider.isCustomNameVisible()) raider.setCustomNameVisible(false);
            if (raider.hasEffect(MobEffects.GLOWING)) raider.removeEffect(MobEffects.GLOWING);
            return;
        }

        boolean visible = switch (mode) {
            case OFF       -> false;
            case ALWAYS    -> true;
            case PROXIMITY -> {
                int r = RaidConfig.RAIDER_LABEL_RADIUS.get();
                yield hasPlayerWithin(raider, r);
            }
        };

        // Name tag toggle. Avoid write when unchanged (setCustomNameVisible
        // marks the entity dirty for network sync).
        if (mode != RaidConfig.LabelMode.OFF) {
            if (raider.isCustomNameVisible() != visible) {
                raider.setCustomNameVisible(visible);
            }
        } else if (raider.isCustomNameVisible()) {
            raider.setCustomNameVisible(false);
        }

        // Glow renewal / removal. GLOWING has no duration flicker if we
        // re-apply it every ~40 ticks with a fresh 60-tick timer.
        if (glowEnabled) {
            boolean glowVisible = mode == RaidConfig.LabelMode.ALWAYS || visible;
            if (glowVisible) {
                MobEffectInstance existing = raider.getEffect(MobEffects.GLOWING);
                if (existing == null || existing.getDuration() < 40) {
                    raider.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING, 60, 0, false, false, false));
                }
            } else if (raider.hasEffect(MobEffects.GLOWING)) {
                raider.removeEffect(MobEffects.GLOWING);
            }
        } else if (raider.hasEffect(MobEffects.GLOWING)) {
            raider.removeEffect(MobEffects.GLOWING);
        }
    }

    /**
     * Register one scoreboard team per role if not already present. Teams
     * carry the role's color and are used by the vanilla glow renderer to
     * tint the outline - a defender doesn't need any client-side asset,
     * this works on stock clients.
     */
    public static void ensureTeams(Scoreboard scoreboard) {
        for (Map.Entry<String, RoleStyle> entry : ROLES.entrySet()) {
            String name = TEAM_PREFIX + entry.getKey();
            PlayerTeam existing = scoreboard.getPlayerTeam(name);
            if (existing == null) {
                PlayerTeam team = scoreboard.addPlayerTeam(name);
                team.setColor(entry.getValue().color);
                team.setSeeFriendlyInvisibles(false);
                team.setAllowFriendlyFire(true);
                team.setCollisionRule(Team.CollisionRule.ALWAYS);
                team.setNameTagVisibility(Team.Visibility.ALWAYS);
            }
        }
    }

    /**
     * Server-startup hook: register teams once so raiders spawned into a
     * long-running server can join them without a per-spawn registry check.
     */
    public static void onServerStarted(MinecraftServer server) {
        ensureTeams(server.getScoreboard());
    }

    // ----- helpers -----

    private static boolean hasPlayerWithin(Mob raider, int radius) {
        double rSq = (double) radius * radius;
        if (!(raider.level() instanceof ServerLevel level)) return false;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (player.distanceToSqr(raider) <= rSq) return true;
        }
        return false;
    }

    private record RoleStyle(String displayName, ChatFormatting color) {}
}
