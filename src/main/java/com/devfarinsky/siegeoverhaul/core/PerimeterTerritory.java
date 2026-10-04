package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/** Immutable territory reviewed for a perimeter. A resized union requires a fresh review, never automatic work. */
public final class PerimeterTerritory {
    public static final int MAX_CHUNKS = 4096;
    private static final String KEY = "SiegePerimeterTerritory", REQUIRED = "SiegePerimeterTerritoryRequired";
    private PerimeterTerritory() {}

    public static void remember(Entity area, RecruitsClaimsBridge.TerritorySnapshot territory) {
        if (territory == null || !territory.ready() || territory.chunks().size() > MAX_CHUNKS
                || area.getPersistentData().contains(KEY)) throw new IllegalArgumentException("Complete reviewed territory required");
        area.getPersistentData().put(KEY, encode(territory.factionStringId(), territory.chunks()));
        area.getPersistentData().putBoolean(REQUIRED, true);
    }

    static CompoundTag encode(String faction, Set<ChunkPos> chunks) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putString("Faction", faction);
        tag.putLongArray("Chunks", chunks.stream().mapToLong(ChunkPos::toLong).sorted().toArray());
        return tag;
    }

    static Set<ChunkPos> decode(CompoundTag tag, String faction) {
        if (tag.getInt("Version") != 1 || faction == null || !faction.equals(tag.getString("Faction"))
                || !tag.contains("Chunks", Tag.TAG_LONG_ARRAY)) return null;
        long[] encoded = tag.getLongArray("Chunks");
        if (encoded.length == 0 || encoded.length > MAX_CHUNKS) return null;
        Set<ChunkPos> chunks = new HashSet<>();
        for (long value : encoded) if (!chunks.add(new ChunkPos(value))) return null;
        return Set.copyOf(chunks);
    }

    /** Missing scope is a legacy job, whose original permissions remain unchanged. */
    public static boolean tracked(Entity area) { return area.getPersistentData().contains(KEY) || area.getPersistentData().getBoolean(REQUIRED); }

    public static String problem(ServerLevel level, Entity area, String faction) {
        if (!tracked(area)) return null;
        Set<ChunkPos> accepted = decode(area.getPersistentData().getCompound(KEY), faction);
        if (accepted == null) return "Paused: reviewed perimeter territory is unavailable; review a new plan";
        var current = RecruitsClaimsBridge.getFactionTerritory(level, faction, MAX_CHUNKS);
        if (!current.ready()) return "Paused: the entire faction territory cannot be verified";
        return accepted.equals(current.chunks()) ? null
                : "Paused: faction territory changed; cancel and review the complete perimeter again";
    }
}
