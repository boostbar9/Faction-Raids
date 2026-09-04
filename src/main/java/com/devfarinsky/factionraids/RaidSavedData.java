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
    public static final int DATA_VERSION = 3;
    public static final UUID UNKNOWN_OWNER = new UUID(0L, 0L);
    public static final String HOME_POINT = "home";
    public final Map<String, Anchor> anchors = new HashMap<>();
    public final Map<String, RaidState> raids = new HashMap<>();

    public static RaidSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RaidSavedData::load, RaidSavedData::new, DATA_NAME);
    }

    public static RaidSavedData load(CompoundTag root) {
        RaidSavedData data = new RaidSavedData();
        ListTag anchorsTag = root.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchorsTag.size(); i++) {
            Anchor anchor = Anchor.load(anchorsTag.getCompound(i));
            data.anchors.put(anchor.teamKey, anchor);
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
        anchors.values().forEach(anchor -> anchorsTag.add(anchor.save()));
        root.put("Anchors", anchorsTag);
        ListTag raidsTag = new ListTag();
        raids.values().forEach(raid -> raidsTag.add(raid.save()));
        root.put("Raids", raidsTag);
        return root;
    }

    public record DefensePoint(String name, ResourceLocation dimension, BlockPos pos) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Dimension", dimension.toString());
            tag.putLong("Position", pos.asLong());
            return tag;
        }

        public static DefensePoint load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null) dimension = Level.OVERWORLD.location();
            String name = tag.getString("Name");
            if (name.isBlank()) name = HOME_POINT;
            return new DefensePoint(name, dimension, BlockPos.of(tag.getLong("Position")));
        }
    }

    public record Anchor(String teamKey, String teamDisplay, UUID ownerUuid, Set<UUID> members,
                         boolean internalRoster, Map<String, DefensePoint> defensePoints,
                         long nextRaidGameTime) {
        public Anchor {
            members = new LinkedHashSet<>(members);
            defensePoints = new LinkedHashMap<>(defensePoints);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Team", teamKey);
            tag.putString("Display", teamDisplay);
            tag.putUUID("Owner", ownerUuid);
            tag.putBoolean("InternalRoster", internalRoster);
            tag.putLong("NextRaid", nextRaidGameTime);

            ListTag memberTags = new ListTag();
            members.forEach(id -> memberTags.add(StringTag.valueOf(id.toString())));
            tag.put("Members", memberTags);

            ListTag pointTags = new ListTag();
            defensePoints.values().forEach(point -> pointTags.add(point.save()));
            tag.put("DefensePoints", pointTags);

            // Keep legacy location fields so older NBT inspection tools remain useful.
            DefensePoint primary = primaryPoint();
            tag.putString("Dimension", primary.dimension().toString());
            tag.putLong("Position", primary.pos().asLong());
            return tag;
        }

        public static Anchor load(CompoundTag tag) {
            UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : UNKNOWN_OWNER;
            Set<UUID> members = new LinkedHashSet<>();
            ListTag memberTags = tag.getList("Members", Tag.TAG_STRING);
            for (int i = 0; i < memberTags.size(); i++) {
                try {
                    members.add(UUID.fromString(memberTags.getString(i)));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!UNKNOWN_OWNER.equals(owner)) members.add(owner);

            Map<String, DefensePoint> points = new LinkedHashMap<>();
            ListTag pointTags = tag.getList("DefensePoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < pointTags.size(); i++) {
                DefensePoint point = DefensePoint.load(pointTags.getCompound(i));
                points.put(point.name(), point);
            }
            if (points.isEmpty()) {
                ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
                if (dimension == null) dimension = Level.OVERWORLD.location();
                points.put(HOME_POINT, new DefensePoint(HOME_POINT, dimension,
                        BlockPos.of(tag.getLong("Position"))));
            }

            return new Anchor(tag.getString("Team"), tag.getString("Display"), owner, members,
                    tag.getBoolean("InternalRoster"), points, tag.getLong("NextRaid"));
        }

        public DefensePoint primaryPoint() {
            DefensePoint home = defensePoints.get(HOME_POINT);
            return home != null ? home : defensePoints.values().iterator().next();
        }

        public DefensePoint point(String name) {
            DefensePoint point = defensePoints.get(name);
            return point != null ? point : primaryPoint();
        }

        public Anchor withNextRaid(long time) {
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster, defensePoints, time);
        }

        public Anchor withIdentity(String newTeamKey, String newDisplay) {
            return new Anchor(newTeamKey, newDisplay, ownerUuid, members, internalRoster,
                    defensePoints, nextRaidGameTime);
        }

        public Anchor withOwner(UUID owner) {
            Set<UUID> updated = new LinkedHashSet<>(members);
            updated.add(owner);
            return new Anchor(teamKey, teamDisplay, owner, updated, internalRoster,
                    defensePoints, nextRaidGameTime);
        }

        public Anchor withRoster(Set<UUID> updatedMembers, boolean managed) {
            Set<UUID> updated = new LinkedHashSet<>(updatedMembers);
            if (!UNKNOWN_OWNER.equals(ownerUuid)) updated.add(ownerUuid);
            return new Anchor(teamKey, teamDisplay, ownerUuid, updated, managed,
                    defensePoints, nextRaidGameTime);
        }

        public Anchor withPoint(DefensePoint point) {
            Map<String, DefensePoint> updated = new LinkedHashMap<>(defensePoints);
            updated.put(point.name(), point);
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster,
                    updated, nextRaidGameTime);
        }

        public Anchor withoutPoint(String name) {
            Map<String, DefensePoint> updated = new LinkedHashMap<>(defensePoints);
            updated.remove(name);
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster,
                    updated, nextRaidGameTime);
        }
    }

    public static final class RaidState {
        public final String teamKey;
        public String defensePointName;
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

        public RaidState(String teamKey, String defensePointName, int warningTicks) {
            this.teamKey = teamKey;
            this.defensePointName = defensePointName;
            this.ticksToNextWave = warningTicks;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Team", teamKey);
            tag.putString("DefensePoint", defensePointName);
            tag.putInt("Wave", wave);
            tag.putInt("NextWave", ticksToNextWave);
            tag.putInt("Abandoned", abandonedTicks);
            tag.putInt("WaveStartingCount", waveStartingCount);
            ListTag ids = new ListTag();
            raiders.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
            tag.put("Raiders", ids);
            ListTag missing = new ListTag();
            missingTicks.forEach((id, ticks) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Id", id);
                entry.putInt("Ticks", ticks);
                missing.add(entry);
            });
            tag.put("MissingEntities", missing);
            return tag;
        }

        public static RaidState load(CompoundTag tag) {
            String point = tag.getString("DefensePoint");
            if (point.isBlank()) point = HOME_POINT;
            RaidState state = new RaidState(tag.getString("Team"), point, tag.getInt("NextWave"));
            state.wave = tag.getInt("Wave");
            state.abandonedTicks = tag.getInt("Abandoned");
            state.waveStartingCount = tag.getInt("WaveStartingCount");
            ListTag ids = tag.getList("Raiders", Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) {
                try {
                    state.raiders.add(UUID.fromString(ids.getString(i)));
                } catch (IllegalArgumentException ignored) {}
            }
            ListTag missing = tag.getList("MissingEntities", Tag.TAG_COMPOUND);
            for (int i = 0; i < missing.size(); i++) {
                CompoundTag entry = missing.getCompound(i);
                if (entry.hasUUID("Id")) state.missingTicks.put(entry.getUUID("Id"), entry.getInt("Ticks"));
            }
            return state;
        }
    }
}
