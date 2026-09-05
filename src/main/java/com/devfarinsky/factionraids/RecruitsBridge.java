package com.devfarinsky.factionraids;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrow compatibility boundary for Villager Recruits 1.15.2+.
 *
 * Faction Raids has a hard loader dependency on Recruits, but this bridge keeps
 * the compiled jar independent of Recruits' obfuscation mapping and limits the
 * integration to its long-lived public faction/ownership methods.
 */
public final class RecruitsBridge {
    private static final String RECRUITS_NAMESPACE = "recruits";
    private static final String FACTION_EVENTS = "com.talhanation.recruits.FactionEvents";
    private static final String ABSTRACT_RECRUIT = "com.talhanation.recruits.entities.AbstractRecruitEntity";

    private static Class<?> recruitClass;
    private static Field factionManagerField;
    private static Method getFactionByStringId;
    private static Method getFactionLeaderUuid;
    private static Method getOwnerUuid;
    private static Method setCombatState;
    private static Method addTeamMethod;
    private static Method setOwnerUuidMethod;
    private static Method setIsOwnedMethod;
    private static boolean factionReflectionAttempted;
    private static boolean ownerReflectionAttempted;

    /**
     * Public string-id for the shared "Raiders" faction. Every raider spawned
     * across every raid, regardless of narrative, joins this faction so:
     *   1. Raiders never friendly-fire each other (Recruits' friendly-fire flag
     *      is off by default for the team we register).
     *   2. Players can't hire them — they're owned by RAIDERS_LEADER_UUID,
     *      which no real player ever has.
     */
    public static final String RAIDERS_FACTION_ID = "factionraids_raiders";
    public static final String RAIDERS_FACTION_DISPLAY = "Raiders";

    /**
     * Sentinel UUID used as the raider faction's "leader" and every raider's
     * owner. Deterministic (version-4-shaped constant) so it survives across
     * server restarts and world moves without needing NBT persistence of its
     * own. Real player UUIDs are cryptographically random — collision is
     * astronomically unlikely.
     */
    public static final UUID RAIDERS_LEADER_UUID =
            UUID.fromString("fac10000-4a1d-4e75-8a1d-000000000001");

    public static boolean isRecruitSoldier(Entity entity) {
        if (!(entity instanceof Mob)) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null || !RECRUITS_NAMESPACE.equals(id.getNamespace())) return false;
        String path = id.getPath();
        return !path.contains("noble") && !path.contains("messenger");
    }

    public static boolean belongsTo(Entity entity, String factionKey, Iterable<UUID> fallbackMembers) {
        if (!isRecruitSoldier(entity)) return false;
        if (factionKey.startsWith("team:") && entity.getTeam() != null) {
            return factionKey.substring(5).equals(entity.getTeam().getName());
        }
        Optional<UUID> owner = ownerUuid(entity);
        if (owner.isEmpty()) return false;
        for (UUID member : fallbackMembers) if (member.equals(owner.get())) return true;
        return false;
    }

    public static Optional<UUID> factionLeader(ServerPlayer player) {
        if (player.getTeam() == null) return Optional.of(player.getUUID());
        initializeFactionReflection();
        if (factionManagerField == null || getFactionByStringId == null || getFactionLeaderUuid == null) {
            return Optional.empty();
        }
        try {
            Object manager = factionManagerField.get(null);
            Object faction = getFactionByStringId.invoke(manager, player.getTeam().getName());
            if (faction == null) return Optional.empty();
            Object id = getFactionLeaderUuid.invoke(faction);
            return id instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> ownerUuid(Entity entity) {
        initializeOwnerReflection();
        if (recruitClass == null || getOwnerUuid == null || !recruitClass.isInstance(entity)) {
            return Optional.empty();
        }
        try {
            Object id = getOwnerUuid.invoke(entity);
            return id instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static String diagnosticStatus() {
        initializeFactionReflection();
        initializeOwnerReflection();
        return "faction API " + (factionManagerField != null ? "ready" : "fallback") +
                ", ownership API " + (getOwnerUuid != null ? "ready" : "fallback");
    }

    /** Put an unowned Recruit into its built-in raid combat state. */
    public static boolean configureHostileRaidRecruit(Mob recruit) {
        initializeOwnerReflection();
        if (recruitClass == null || setCombatState == null || !recruitClass.isInstance(recruit)) return false;
        try {
            // Recruits state 2 is its native RAID mode. Combined with faction
            // membership below, raiders can fight players and faction soldiers
            // while remaining un-hireable and never friendly-firing each other.
            setCombatState.invoke(recruit, 2);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Register the shared "Raiders" faction with the Recruits faction manager
     * and the vanilla scoreboard. Idempotent — safe to call every server
     * start; returns quickly when the faction already exists.
     */
    public static boolean ensureRaidersFaction(MinecraftServer server) {
        if (server == null) return false;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return false;

        // Step 1: vanilla scoreboard team. Recruits looks this up by string-id.
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam scoreboardTeam = scoreboard.getPlayerTeam(RAIDERS_FACTION_ID);
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.addPlayerTeam(RAIDERS_FACTION_ID);
            scoreboardTeam.setDisplayName(net.minecraft.network.chat.Component.literal(RAIDERS_FACTION_DISPLAY));
            scoreboardTeam.setColor(ChatFormatting.DARK_RED);
            // Explicit off: raiders should never damage each other in melee.
            scoreboardTeam.setAllowFriendlyFire(false);
            scoreboardTeam.setSeeFriendlyInvisibles(true);
        }

        // Step 2: Recruits faction manager entry. Reflection because we don't
        // compile-depend on Recruits classes.
        initializeFactionReflection();
        if (factionManagerField == null || getFactionByStringId == null || addTeamMethod == null) {
            // Recruits API unavailable: scoreboard team alone still gives us
            // team membership for friendly-fire and "same-team" checks.
            return scoreboardTeam != null;
        }
        try {
            Object manager = factionManagerField.get(null);
            Object existing = getFactionByStringId.invoke(manager, RAIDERS_FACTION_ID);
            if (existing != null) return true;

            // Build a minimal, valid banner NBT so Recruits' "blank banner"
            // check passes when clients open faction lists.
            CompoundTag bannerNbt = new CompoundTag();
            bannerNbt.putString("id", "minecraft:black_banner");
            bannerNbt.putInt("Count", 1);

            addTeamMethod.invoke(manager,
                    RAIDERS_FACTION_ID,
                    RAIDERS_FACTION_DISPLAY,
                    RAIDERS_LEADER_UUID,
                    "Raider Warlord",
                    bannerNbt,
                    (byte) 0,
                    ChatFormatting.DARK_RED);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Attach a raider mob to the shared Raiders faction:
     *   - Adds it to the raiders scoreboard team (Recruits treats scoreboard
     *     team as ground truth for "same faction").
     *   - Sets its owner-UUID to the raider-leader sentinel + isOwned=true so
     *     {@code AbstractRecruitEntity#canBeHired} short-circuits and the
     *     hire GUI never opens on right-click.
     * Safe to call on any Mob; silently no-ops for non-raiders.
     */
    public static boolean assignToRaidersFaction(Mob recruit) {
        if (recruit == null || !recruit.isAlive()) return false;
        if (!(recruit.level() instanceof ServerLevel level)) return false;
        boolean success = false;

        // Scoreboard team assignment — works whether or not Recruits is loaded.
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam raidersTeam = scoreboard.getPlayerTeam(RAIDERS_FACTION_ID);
        if (raidersTeam != null) {
            scoreboard.addPlayerToTeam(recruit.getStringUUID(), raidersTeam);
            success = true;
        }

        // Owner assignment — blocks the hire path (isOwned && ownerUUID != you).
        initializeOwnerReflection();
        if (recruitClass != null && recruitClass.isInstance(recruit) &&
                setOwnerUuidMethod != null && setIsOwnedMethod != null) {
            try {
                setOwnerUuidMethod.invoke(recruit, Optional.of(RAIDERS_LEADER_UUID));
                setIsOwnedMethod.invoke(recruit, true);
                success = true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall through — scoreboard team alone still helps.
            }
        }
        return success;
    }

    private static synchronized void initializeFactionReflection() {
        if (factionReflectionAttempted) return;
        factionReflectionAttempted = true;
        try {
            Class<?> factionEvents = Class.forName(FACTION_EVENTS);
            factionManagerField = factionEvents.getField("recruitsFactionManager");
            Class<?> managerClass = factionManagerField.getType();
            getFactionByStringId = managerClass.getMethod("getFactionByStringID", String.class);
            Class<?> factionClass = getFactionByStringId.getReturnType();
            getFactionLeaderUuid = factionClass.getMethod("getTeamLeaderUUID");
            addTeamMethod = managerClass.getMethod("addTeam",
                    String.class, String.class, UUID.class, String.class,
                    CompoundTag.class, byte.class, ChatFormatting.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            factionManagerField = null;
            getFactionByStringId = null;
            getFactionLeaderUuid = null;
            addTeamMethod = null;
        }
    }

    private static synchronized void initializeOwnerReflection() {
        if (ownerReflectionAttempted) return;
        ownerReflectionAttempted = true;
        try {
            recruitClass = Class.forName(ABSTRACT_RECRUIT);
            getOwnerUuid = recruitClass.getMethod("getOwnerUUID");
            setCombatState = recruitClass.getMethod("setState", int.class);
            setOwnerUuidMethod = recruitClass.getMethod("setOwnerUUID", Optional.class);
            setIsOwnedMethod = recruitClass.getMethod("setIsOwned", boolean.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            recruitClass = null;
            getOwnerUuid = null;
            setCombatState = null;
            setOwnerUuidMethod = null;
            setIsOwnedMethod = null;
        }
    }

    private RecruitsBridge() {}
}
