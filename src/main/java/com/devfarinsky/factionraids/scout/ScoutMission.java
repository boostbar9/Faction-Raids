package com.devfarinsky.factionraids.scout;

import com.devfarinsky.factionraids.narrative.RaidNarrative;
import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * v2.26.0 pre-raid scouting mission.
 *
 * <p>One mission per anchor per cooldown, created at a random point in the
 * middle third of the cooldown window. Holds the pre-selected narrative
 * (so scouts can drop an intel letter that names the attacker who will
 * actually arrive), the set of live scout UUIDs (so we can clean them up
 * when the raid begins or the anchor is deleted), and a scheduled game
 * time (so the tick loop knows when to spawn).
 *
 * <p>Persisted on {@link com.devfarinsky.factionraids.RaidSavedData} so a
 * server restart mid-cooldown doesn't re-roll the mission or forget the
 * scouts already on the field.
 */
public final class ScoutMission {

    /** Anchor teamKey this mission belongs to. Matches map key on RaidSavedData. */
    public final String teamKey;

    /** Game time at which scouts should spawn. */
    public long spawnGameTime;

    /**
     * True once scouts have been spawned. Prevents re-spawning after a
     * mid-cooldown restart when spawnGameTime has already passed.
     */
    public boolean spawned;

    /**
     * Game time at which the mission expires. Living scouts flee back and
     * despawn; the mission entry is cleared. Prevents scouts from lingering
     * indefinitely if defenders never engage them.
     */
    public long expireGameTime;

    /**
     * Pre-selected narrative for the raid that will follow this scouting
     * mission. Written into the intel letter so defenders learn who is
     * actually coming, not a randomly-rolled fake. Reused verbatim by the
     * raid itself when {@code beginRaid} runs so the promise is kept.
     */
    public RaidNarrative previewedNarrative;

    /** UUIDs of live scout entities. Cleared when scouts die or despawn. */
    public final Set<UUID> scoutUuids = new HashSet<>();

    public ScoutMission(String teamKey, long spawnGameTime, long expireGameTime,
                        RaidNarrative previewedNarrative) {
        this.teamKey = teamKey;
        this.spawnGameTime = spawnGameTime;
        this.expireGameTime = expireGameTime;
        this.previewedNarrative = previewedNarrative;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Team", teamKey);
        tag.putLong("SpawnAt", spawnGameTime);
        tag.putLong("ExpireAt", expireGameTime);
        tag.putBoolean("Spawned", spawned);
        if (previewedNarrative != null) tag.put("Narrative", previewedNarrative.save());
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        scoutUuids.forEach(u -> list.add(net.minecraft.nbt.StringTag.valueOf(u.toString())));
        tag.put("Scouts", list);
        return tag;
    }

    public static ScoutMission load(CompoundTag tag) {
        RaidNarrative narrative = tag.contains("Narrative") ?
                RaidNarrative.load(tag.getCompound("Narrative")) : null;
        ScoutMission m = new ScoutMission(tag.getString("Team"),
                tag.getLong("SpawnAt"), tag.getLong("ExpireAt"), narrative);
        m.spawned = tag.getBoolean("Spawned");
        net.minecraft.nbt.ListTag list = tag.getList("Scouts", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                m.scoutUuids.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // Corrupt UUID in old save; skip. Scout entity will be
                // orphaned but harmless — it despawns on its own timer.
            }
        }
        return m;
    }
}
