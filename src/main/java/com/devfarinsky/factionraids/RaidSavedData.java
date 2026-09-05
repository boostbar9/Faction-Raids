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
    public static final int DATA_VERSION = 9;
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
                         boolean internalRoster, boolean automaticHome,
                         Map<String, DefensePoint> defensePoints,
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
            tag.putBoolean("AutomaticHome", automaticHome);
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
                    tag.getBoolean("InternalRoster"), tag.getBoolean("AutomaticHome"),
                    points, tag.getLong("NextRaid"));
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
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster, automaticHome,
                    defensePoints, time);
        }

        public Anchor withIdentity(String newTeamKey, String newDisplay) {
            return new Anchor(newTeamKey, newDisplay, ownerUuid, members, internalRoster, automaticHome,
                    defensePoints, nextRaidGameTime);
        }

        public Anchor withOwner(UUID owner) {
            Set<UUID> updated = new LinkedHashSet<>(members);
            updated.add(owner);
            return new Anchor(teamKey, teamDisplay, owner, updated, internalRoster, automaticHome,
                    defensePoints, nextRaidGameTime);
        }

        public Anchor withRoster(Set<UUID> updatedMembers, boolean managed) {
            Set<UUID> updated = new LinkedHashSet<>(updatedMembers);
            if (!UNKNOWN_OWNER.equals(ownerUuid)) updated.add(ownerUuid);
            return new Anchor(teamKey, teamDisplay, ownerUuid, updated, managed, automaticHome,
                    defensePoints, nextRaidGameTime);
        }

        public Anchor withPoint(DefensePoint point) {
            Map<String, DefensePoint> updated = new LinkedHashMap<>(defensePoints);
            updated.put(point.name(), point);
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster, automaticHome,
                    updated, nextRaidGameTime);
        }

        public Anchor withoutPoint(String name) {
            Map<String, DefensePoint> updated = new LinkedHashMap<>(defensePoints);
            updated.remove(name);
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster, automaticHome,
                    updated, nextRaidGameTime);
        }

        public Anchor withAutomaticHome(boolean automatic) {
            return new Anchor(teamKey, teamDisplay, ownerUuid, members, internalRoster, automatic,
                    defensePoints, nextRaidGameTime);
        }
    }

    public static final class RaidState {
        public final String teamKey;
        public String defensePointName;
        public int wave;
        public int ticksToNextWave;
        public int abandonedTicks;
        public int waveStartingCount;
        public int plannedWaveSize;
        public int pendingWaveSpawns;
        public int ticksToNextSquad;
        public int squadsSpawned;
        public int captureTicks;
        public int breachTicks;
        public boolean breached;
        public int lastBreachWarningBand;
        public BlockPos campPos;
        public boolean campBuildAttempted;
        /** Water-surface staging point when this raid has an amphibious component. Null otherwise. */
        public BlockPos navalStagingPos;
        /** Landing beach the naval convoy steers toward. Null when no naval staging. */
        public BlockPos navalBeachPos;
        public final Map<Long, String> campBlocks = new LinkedHashMap<>();
        /** UUID -> SiegeEngineType.name() for engines currently on the field. */
        public final Map<UUID, String> siegeEngines = new LinkedHashMap<>();
        /** How many sappers this raid has already dispatched (capped by config). */
        public int sappersDispatched;
        public final Map<Long, CompoundTag> breachedBlocks = new LinkedHashMap<>();
        public final Map<Long, Integer> blockBreachProgress = new HashMap<>();
        public BlockPos currentBreachBlock;
        public int currentBreachRequired;
        public double approachAngle;
        public int lastCaptureWarningBand;
        public UUID commanderUuid;
        public boolean commanderDefeated;
        public long startedGameTime;
        public int totalSpawned;
        public int totalDefeated;
        public int totalEscaped;
        public boolean rewardEligible = true;
        public int lastWarningSecond = Integer.MAX_VALUE;
        public final Set<UUID> raiders = new HashSet<>();
        public final Map<UUID, Integer> missingTicks = new HashMap<>();
        public int reconcileTicks;
        public boolean performancePauseAnnounced;
        public boolean offlinePauseAnnounced;
        /**
         * Themed narrative for this raid (who is attacking, why, and pre-rendered
         * flavor strings). Null on raids loaded from pre-2.7 saves or when the
         * narrative system is disabled in config — callers must fall back cleanly.
         */
        public com.devfarinsky.factionraids.narrative.RaidNarrative narrative;

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
            tag.putInt("PlannedWaveSize", plannedWaveSize);
            tag.putInt("PendingWaveSpawns", pendingWaveSpawns);
            tag.putInt("NextSquad", ticksToNextSquad);
            tag.putInt("SquadsSpawned", squadsSpawned);
            tag.putInt("CaptureTicks", captureTicks);
            tag.putInt("BreachTicks", breachTicks);
            tag.putBoolean("Breached", breached);
            tag.putInt("BreachWarningBand", lastBreachWarningBand);
            if (campPos != null) tag.putLong("CampPosition", campPos.asLong());
            tag.putBoolean("CampBuildAttempted", campBuildAttempted);
            if (navalStagingPos != null) tag.putLong("NavalStagingPos", navalStagingPos.asLong());
            if (navalBeachPos != null) tag.putLong("NavalBeachPos", navalBeachPos.asLong());
            tag.putInt("SappersDispatched", sappersDispatched);
            ListTag siegeList = new ListTag();
            siegeEngines.forEach((uuid, typeName) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("UUID", uuid);
                entry.putString("Type", typeName);
                siegeList.add(entry);
            });
            tag.put("SiegeEngines", siegeList);
            ListTag camp = new ListTag();
            campBlocks.forEach((position, block) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Position", position);
                entry.putString("Block", block);
                camp.add(entry);
            });
            tag.put("CampBlocks", camp);
            ListTag breached = new ListTag();
            breachedBlocks.forEach((position, blockState) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Position", position);
                entry.put("State", blockState.copy());
                breached.add(entry);
            });
            tag.put("BreachedBlocks", breached);
            ListTag breachProgress = new ListTag();
            blockBreachProgress.forEach((position, progress) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Position", position);
                entry.putInt("Progress", progress);
                breachProgress.add(entry);
            });
            tag.put("BlockBreachProgress", breachProgress);
            if (currentBreachBlock != null) tag.putLong("CurrentBreachBlock", currentBreachBlock.asLong());
            tag.putInt("CurrentBreachRequired", currentBreachRequired);
            tag.putDouble("ApproachAngle", approachAngle);
            tag.putInt("CaptureWarningBand", lastCaptureWarningBand);
            if (commanderUuid != null) tag.putUUID("Commander", commanderUuid);
            tag.putBoolean("CommanderDefeated", commanderDefeated);
            tag.putLong("StartedGameTime", startedGameTime);
            tag.putInt("TotalSpawned", totalSpawned);
            tag.putInt("TotalDefeated", totalDefeated);
            tag.putInt("TotalEscaped", totalEscaped);
            tag.putBoolean("RewardEligible", rewardEligible);
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
            if (narrative != null) tag.put("Narrative", narrative.save());
            return tag;
        }

        public static RaidState load(CompoundTag tag) {
            String point = tag.getString("DefensePoint");
            if (point.isBlank()) point = HOME_POINT;
            RaidState state = new RaidState(tag.getString("Team"), point, tag.getInt("NextWave"));
            state.wave = tag.getInt("Wave");
            state.abandonedTicks = tag.getInt("Abandoned");
            state.waveStartingCount = tag.getInt("WaveStartingCount");
            state.plannedWaveSize = tag.contains("PlannedWaveSize", Tag.TAG_INT) ?
                    tag.getInt("PlannedWaveSize") : state.waveStartingCount;
            state.pendingWaveSpawns = tag.getInt("PendingWaveSpawns");
            state.ticksToNextSquad = tag.getInt("NextSquad");
            state.squadsSpawned = tag.getInt("SquadsSpawned");
            state.captureTicks = tag.getInt("CaptureTicks");
            state.breachTicks = tag.getInt("BreachTicks");
            state.breached = tag.contains("Breached", Tag.TAG_BYTE) ?
                    tag.getBoolean("Breached") : state.wave > 0;
            state.lastBreachWarningBand = tag.getInt("BreachWarningBand");
            state.campPos = tag.contains("CampPosition", Tag.TAG_LONG) ?
                    BlockPos.of(tag.getLong("CampPosition")) : null;
            state.campBuildAttempted = tag.getBoolean("CampBuildAttempted");
            state.navalStagingPos = tag.contains("NavalStagingPos", Tag.TAG_LONG) ?
                    BlockPos.of(tag.getLong("NavalStagingPos")) : null;
            state.navalBeachPos = tag.contains("NavalBeachPos", Tag.TAG_LONG) ?
                    BlockPos.of(tag.getLong("NavalBeachPos")) : null;
            state.sappersDispatched = tag.getInt("SappersDispatched");
            ListTag siegeList = tag.getList("SiegeEngines", Tag.TAG_COMPOUND);
            for (int i = 0; i < siegeList.size(); i++) {
                CompoundTag entry = siegeList.getCompound(i);
                if (entry.hasUUID("UUID")) {
                    state.siegeEngines.put(entry.getUUID("UUID"), entry.getString("Type"));
                }
            }
            ListTag camp = tag.getList("CampBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < camp.size(); i++) {
                CompoundTag entry = camp.getCompound(i);
                state.campBlocks.put(entry.getLong("Position"), entry.getString("Block"));
            }
            ListTag breached = tag.getList("BreachedBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < breached.size(); i++) {
                CompoundTag entry = breached.getCompound(i);
                state.breachedBlocks.put(entry.getLong("Position"), entry.getCompound("State").copy());
            }
            ListTag breachProgress = tag.getList("BlockBreachProgress", Tag.TAG_COMPOUND);
            for (int i = 0; i < breachProgress.size(); i++) {
                CompoundTag entry = breachProgress.getCompound(i);
                state.blockBreachProgress.put(entry.getLong("Position"), entry.getInt("Progress"));
            }
            state.currentBreachBlock = tag.contains("CurrentBreachBlock", Tag.TAG_LONG) ?
                    BlockPos.of(tag.getLong("CurrentBreachBlock")) : null;
            state.currentBreachRequired = tag.getInt("CurrentBreachRequired");
            state.approachAngle = tag.contains("ApproachAngle", Tag.TAG_DOUBLE) ?
                    tag.getDouble("ApproachAngle") : 0.0D;
            state.lastCaptureWarningBand = tag.getInt("CaptureWarningBand");
            state.commanderUuid = tag.hasUUID("Commander") ? tag.getUUID("Commander") : null;
            state.commanderDefeated = tag.getBoolean("CommanderDefeated");
            state.startedGameTime = tag.getLong("StartedGameTime");
            state.totalSpawned = tag.getInt("TotalSpawned");
            state.totalDefeated = tag.getInt("TotalDefeated");
            state.totalEscaped = tag.getInt("TotalEscaped");
            state.rewardEligible = !tag.contains("RewardEligible", Tag.TAG_BYTE) ||
                    tag.getBoolean("RewardEligible");
            ListTag ids = tag.getList("Raiders", Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) {
                try {
                    state.raiders.add(UUID.fromString(ids.getString(i)));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!tag.contains("TotalSpawned", Tag.TAG_INT)) {
                // A 2.1 raid cannot reconstruct earlier casualties, but counting
                // every currently tracked attacker keeps upgraded summaries sane.
                state.totalSpawned = state.raiders.size();
            }
            if (tag.contains("Narrative", Tag.TAG_COMPOUND)) {
                state.narrative = com.devfarinsky.factionraids.narrative.RaidNarrative.load(tag.getCompound("Narrative"));
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
