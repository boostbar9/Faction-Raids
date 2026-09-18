package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/**
 * v4.30.0 - Bridges into Villager Recruits' RecruitsDiplomacyManager so raider
 * factions we register are actually marked ENEMY toward the player faction (and
 * reset to NEUTRAL when the raid ends). Without this hook the raid teams exist
 * on paper but Recruits' own targeting, HUD, and toast systems treat them as
 * generic neutrals, which caused several small "friendly-looking enemy"
 * papercuts in the field.
 *
 * <p>All Recruits access is through reflection to keep the compile clean when
 * that mod isn't on the classpath in a dev sandbox. Failures log once and no-op
 * so nothing here can ever crash a live raid.
 */
public final class RaiderDiplomacy {
    private RaiderDiplomacy() {}

    /** Recruits' DiplomacyStatus enum values (byte-backed 0/1/2 in the source). */
    public static final String NEUTRAL = "NEUTRAL";
    public static final String ALLY    = "ALLY";
    public static final String ENEMY   = "ENEMY";

    /** Cached once so we don't re-resolve reflection on every raid. */
    private static volatile boolean initialized;
    private static volatile boolean available;
    private static Class<?> managerClass;
    private static Class<?> statusClass;
    private static Method setRelation;
    private static Method getRelation;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            managerClass = Class.forName("com.talhanation.recruits.world.RecruitsDiplomacyManager");
            statusClass  = Class.forName("com.talhanation.recruits.world.RecruitsDiplomacyManager$DiplomacyStatus");
            // (String, String, DiplomacyStatus, ServerLevel) - the notify-players
            // toast is desirable so pick the short overload when it exists.
            setRelation = managerClass.getMethod("setRelation",
                    String.class, String.class, statusClass, ServerLevel.class);
            getRelation = managerClass.getMethod("getRelation", String.class, String.class);
            available = true;
        } catch (ReflectiveOperationException ex) {
            FactionLogger.LOG.info("Recruits diplomacy API not present; raider factions will stay NEUTRAL: {}",
                    ex.getMessage());
            available = false;
        }
    }

    /**
     * Set the diplomatic relation between a player faction and one of our
     * raider factions. Both directions are set so vanilla Recruits doesn't get
     * confused about who is hostile to whom. Safe to call with a raider team
     * that hasn't been ensured yet; Recruits treats unknown teams as no-ops.
     */
    public static void setRelation(MinecraftServer server, String playerTeam, String raiderTeam, String statusName) {
        String playerFaction = factionId(playerTeam);
        String raiderFaction = factionId(raiderTeam);
        if (server == null || playerFaction == null || raiderFaction == null || statusName == null) return;
        init();
        if (!available) return;
        ServerLevel level = server.overworld();
        if (level == null) return;
        try {
            Object manager = Class.forName("com.talhanation.recruits.FactionEvents")
                    .getField("recruitsFactionManager").get(null);
            if (manager == null) return;
            Object status = Enum.valueOf(statusClass.asSubclass(Enum.class), statusName);
            setRelation.invoke(manager, playerFaction, raiderFaction, status, level);
            setRelation.invoke(manager, raiderFaction, playerFaction, status, level);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.debug("Diplomacy set {} <-> {} failed: {}", playerFaction, raiderFaction, ex.getMessage());
        }
    }

    /** Convenience: mark a raid as active - both sides ENEMY. */
    public static void markEnemy(MinecraftServer server, String playerTeam, String raiderFactionKey) {
        setRelation(server, playerTeam, RaiderFactions.id(raiderFactionKey), ENEMY);
    }

    /**
     * Reassert the two-way hostile relation while a siege is active. Players
     * may use Recruits' diplomacy UI, but changing a synthetic raider faction
     * to neutral or ally cannot disable native combat targeting mid-siege.
     * Reflection failures remain fail-safe no-ops.
     */
    public static void maintainEnemy(MinecraftServer server, String playerTeam, String raiderFactionKey) {
        if (raiderFactionKey == null || raiderFactionKey.isBlank()) return;
        String raiderTeam = RaiderFactions.id(raiderFactionKey);
        String forward = currentRelation(playerTeam, raiderTeam);
        String reverse = currentRelation(raiderTeam, playerTeam);
        if (needsEnemyRepair(forward, reverse)) setRelation(server, playerTeam, raiderTeam, ENEMY);
    }

    static boolean needsEnemyRepair(String forward, String reverse) {
        return forward != null && reverse != null && (!ENEMY.equals(forward) || !ENEMY.equals(reverse));
    }

    /** Convenience: raid ended - reset to NEUTRAL so hostile targeting stops. */
    public static void resetRelation(MinecraftServer server, String playerTeam, String raiderFactionKey) {
        setRelation(server, playerTeam, RaiderFactions.id(raiderFactionKey), NEUTRAL);
    }

    /** Read current relation for diagnostics; returns null if Recruits is absent. */
    public static String currentRelation(String teamA, String teamB) {
        String factionA = factionId(teamA);
        String factionB = factionId(teamB);
        if (factionA == null || factionB == null) return null;
        init();
        if (!available) return null;
        try {
            Object manager = Class.forName("com.talhanation.recruits.FactionEvents")
                    .getField("recruitsFactionManager").get(null);
            if (manager == null) return null;
            Object result = getRelation.invoke(manager, factionA, factionB);
            return result == null ? null : ((Enum<?>) result).name();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Siege Overhaul persists scoreboard identities as {@code team:<id>}, but
     * Recruits' faction and diplomacy managers are keyed by the raw string id.
     * Player fallback anchors have no native Recruits faction and must not be
     * handed to that API.
     */
    static String factionId(String key) {
        if (key == null || key.isBlank() || key.startsWith("player:")) return null;
        if (!key.startsWith("team:")) return key;
        String id = key.substring(5);
        return id.isBlank() ? null : id;
    }
}
