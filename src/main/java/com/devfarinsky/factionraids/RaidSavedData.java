package com.devfarinsky.factionraids;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public final class RaidSavedData extends SavedData {
    public static final String DATA_NAME = "factionraids_data";
    public static final int DATA_VERSION = 2;
    public static final UUID UNKNOWN_OWNER = new UUID(0L, 0L);
    public final Map<String, Anchor> anchors = new HashMap<>();
    public final Map<String, RaidState> raids = new HashMap<>();

    public static RaidSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RaidSavedData::load, RaidSavedData::new, DATA_NAME);
    }

    public static RaidSavedData load(CompoundTag root) {
        RaidSavedData data = new RaidSavedData();
        ListTag anchorsTag = root.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchorsTag.size(); i++) {
            CompoundTag t = anchorsTag.getCompound(i);
            Anchor a = Anchor.load(t);
            data.anchors.put(a.teamKey, a);
        }
        ListTag raidsTag = root.getList("Raids", Tag.TAG_COMPOUND);
        for (int i = 0; i < raidsTag.size(); i++) {
            RaidState raid = RaidState.load(raidsTag.getCompound(i));
            data.raids.put(raid.teamKey, raid);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putInt("DataVersion", DATA_VERSION);
        ListTag anchorsTag = new ListTag();
        anchors.values().forEach(a -> anchorsTag.add(a.save()));
        root.put("Anchors", anchorsTag);
        ListTag raidsTag = new ListTag();
        raids.values().forEach(r -> raidsTag.add(r.save()));
        root.put("Raids", raidsTag);
        return root;
    }

    public record Anchor(String teamKey, String teamDisplay, UUID ownerUuid, ResourceLocation dimension,
                         BlockPos pos, long nextRaidGameTime) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Team", teamKey);
            t.putString("Display", teamDisplay);
            t.putUUID("Owner", ownerUuid);
            t.putString("Dimension", dimension.toString());
            t.putLong("Position", pos.asLong());
            t.putLong("NextRaid", nextRaidGameTime);
            return t;
        }

        public static Anchor load(CompoundTag t) {
            ResourceLocation dimension = ResourceLocation.tryParse(t.getString("Dimension"));
            if (dimension == null) dimension = Level.OVERWORLD.location();
            UUID owner = t.hasUUID("Owner") ? t.getUUID("Owner") : UNKNOWN_OWNER;
            return new Anchor(t.getString("Team"), t.getString("Display"), owner, dimension,
                    BlockPos.of(t.getLong("Position")), t.getLong("NextRaid"));
        }

        public Anchor withNextRaid(long time) {
            return new Anchor(teamKey, teamDisplay, ownerUuid, dimension, pos, time);
        }

        public Anchor withIdentity(String newTeamKey, String newDisplay) {
            return new Anchor(newTeamKey, newDisplay, ownerUuid, dimension, pos, nextRaidGameTime);
        }

        public Anchor withOwner(UUID owner) {
            return new Anchor(teamKey, teamDisplay, owner, dimension, pos, nextRaidGameTime);
        }
    }

    public static final class RaidState {
        public final String teamKey;
        public int wave;
        public int ticksToNextWave;
        public int abandonedTicks;
        public int waveStartingCount;
        public int lastWarningSecond = Integer.MAX_VALUE;
        public final Set<UUID> raiders = new HashSet<>();
        public final Map<UUID, Integer> missingTicks = new HashMap<>();
        public int reconcileTicks;
        public boolean performancePauseAnnounced;
        public boolean offlinePauseAnnounced;

        public RaidState(String teamKey, int warningTicks) {
            this.teamKey = teamKey;
            this.ticksToNextWave = warningTicks;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Team", teamKey);
            t.putInt("Wave", wave);
            t.putInt("NextWave", ticksToNextWave);
            t.putInt("Abandoned", abandonedTicks);
            t.putInt("WaveStartingCount", waveStartingCount);
            ListTag ids = new ListTag();
            raiders.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
            t.put("Raiders", ids);
            ListTag missing = new ListTag();
            missingTicks.forEach((id, ticks) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Id", id);
                entry.putInt("Ticks", ticks);
                missing.add(entry);
            });
            t.put("MissingEntities", missing);
            return t;
        }

        public static RaidState load(CompoundTag t) {
            RaidState state = new RaidState(t.getString("Team"), t.getInt("NextWave"));
            state.wave = t.getInt("Wave");
            state.abandonedTicks = t.getInt("Abandoned");
            state.waveStartingCount = t.getInt("WaveStartingCount");
            ListTag ids = t.getList("Raiders", Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) {
                try { state.raiders.add(UUID.fromString(ids.getString(i))); } catch (IllegalArgumentException ignored) {}
            }
            ListTag missing = t.getList("MissingEntities", Tag.TAG_COMPOUND);
            for (int i = 0; i < missing.size(); i++) {
                CompoundTag entry = missing.getCompound(i);
                if (entry.hasUUID("Id")) state.missingTicks.put(entry.getUUID("Id"), entry.getInt("Ticks"));
            }
            return state;
        }
    }
}
