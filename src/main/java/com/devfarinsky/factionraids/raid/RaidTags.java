package com.devfarinsky.factionraids.raid;

import com.devfarinsky.factionraids.ModConstants;
import net.minecraft.world.entity.Entity;

/**
 * Encapsulated read/write access to the {@code FactionRaidsTeam} / {@code FactionRaidsRole}
 * NBT tags that mark raid-owned entities.
 *
 * <p>Before this class, {@code RaidEvents} accessed {@code entity.getPersistentData()} directly
 * in ~15 places with hand-written {@code getString}/{@code putString}/{@code contains} calls.
 * Every future addition (a new tag, prefixing, migration) would have to hunt all those
 * call sites. Route all access through here.
 */
public final class RaidTags {
    private RaidTags() {}

    /** Whether the entity has been claimed by any Faction Raids invasion. */
    public static boolean isRaider(Entity entity) {
        return entity != null && entity.getPersistentData().contains(ModConstants.Tags.RAID_TEAM);
    }

    /** Team key stored on the entity, or an empty string when absent. */
    public static String teamKey(Entity entity) {
        return entity == null ? "" : entity.getPersistentData().getString(ModConstants.Tags.RAID_TEAM);
    }

    /** Role tag stored on the entity, or an empty string when absent. */
    public static String role(Entity entity) {
        return entity == null ? "" : entity.getPersistentData().getString(ModConstants.Tags.RAID_ROLE);
    }

    /** Tag an entity as a raider belonging to the given team. */
    public static void markTeam(Entity entity, String teamKey) {
        if (entity == null || teamKey == null) return;
        entity.getPersistentData().putString(ModConstants.Tags.RAID_TEAM, teamKey);
    }

    /** Tag an entity's raid role (e.g. commander, breacher). */
    public static void markRole(Entity entity, String role) {
        if (entity == null || role == null) return;
        entity.getPersistentData().putString(ModConstants.Tags.RAID_ROLE, role);
    }

    /** Copy raid tags from {@code source} onto {@code target}. Used for summoned minions. */
    public static void copyFrom(Entity source, Entity target) {
        if (!isRaider(source) || target == null) return;
        markTeam(target, teamKey(source));
        String role = role(source);
        if (!role.isEmpty()) markRole(target, role);
    }

    /** True when {@code a} and {@code b} belong to the same raid team. */
    public static boolean sameTeam(Entity a, Entity b) {
        if (!isRaider(a) || !isRaider(b)) return false;
        return teamKey(a).equals(teamKey(b));
    }
}
