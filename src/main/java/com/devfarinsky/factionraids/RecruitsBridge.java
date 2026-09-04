package com.devfarinsky.factionraids;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
    private static boolean reflectionAttempted;

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
        initializeReflection();
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
        initializeReflection();
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

    private static synchronized void initializeReflection() {
        if (reflectionAttempted) return;
        reflectionAttempted = true;
        try {
            Class<?> factionEvents = Class.forName(FACTION_EVENTS);
            factionManagerField = factionEvents.getField("recruitsFactionManager");
            Class<?> managerClass = factionManagerField.getType();
            getFactionByStringId = managerClass.getMethod("getFactionByStringID", String.class);
            Class<?> factionClass = getFactionByStringId.getReturnType();
            getFactionLeaderUuid = factionClass.getMethod("getTeamLeaderUUID");

            recruitClass = Class.forName(ABSTRACT_RECRUIT);
            getOwnerUuid = recruitClass.getMethod("getOwnerUUID");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            factionManagerField = null;
            getFactionByStringId = null;
            getFactionLeaderUuid = null;
            recruitClass = null;
            getOwnerUuid = null;
        }
    }

    private RecruitsBridge() {}
}
