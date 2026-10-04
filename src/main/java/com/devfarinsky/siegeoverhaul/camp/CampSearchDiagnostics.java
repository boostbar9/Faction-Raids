package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.stream.Collectors;

/** Bounded observations, never candidate positions, claim identities or arbitrary error text. */
public final class CampSearchDiagnostics {
    private static final int MAX_COUNT = 10_000;
    private static final int MAX_REASONS = 3;

    public enum Reason {
        CLAIM_SETUP("camp claiming was unavailable"),
        CLAIM_SAFETY("claim or world-border safety checks failed"),
        UNLOADED("terrain was not loaded in time"),
        SURFACE("surface suitability checks failed"),
        NAVAL_SPACE("insufficient clearance from naval staging"),
        RELIEF("terrain exceeded safe grading limits"),
        FLUID("water or other fluid exceeded safe earthworks limits"),
        PROTECTED_BLOCKS("blocks or structures could not be safely changed"),
        BUDGET("earthworks exceeded the block budget"),
        NO_LAND_EXIT("no safe dry land exit was found"),
        CLAIM_CREATE("a native camp claim could not be registered"),
        TERRAIN_APPLY("earthworks could not be safely applied");

        private final String text;
        Reason(String text) { this.text = text; }
    }

    private final EnumMap<Reason, Integer> counts = new EnumMap<>(Reason.class);
    private boolean exhausted;

    public void record(Reason reason) { record(reason, 1); }

    public void record(Reason reason, int count) {
        if (count <= 0 || exhausted) return;
        counts.put(reason, (int) Math.min(MAX_COUNT, (long) counts.getOrDefault(reason, 0) + count));
    }

    public void recordTerrain(CampTerrain.Rejection reason, int count) {
        record(switch (reason) {
            case UNLOADED -> Reason.UNLOADED;
            case BORDER, CLAIM -> Reason.CLAIM_SAFETY;
            case RELIEF, EDGE, HEIGHT_LIMIT -> Reason.RELIEF;
            case FLUID -> Reason.FLUID;
            case BLOCK_ENTITY, SOIL, CLEARANCE -> Reason.PROTECTED_BLOCKS;
            case BUDGET -> Reason.BUDGET;
            case NO_LAND_EXIT -> Reason.NO_LAND_EXIT;
        }, count);
    }

    public void finish() { exhausted = true; }
    public boolean exhausted() { return exhausted; }

    public String summary() {
        String reasons = counts.entrySet().stream()
                .sorted(Comparator.<java.util.Map.Entry<Reason, Integer>>comparingInt(entry -> entry.getValue())
                        .reversed().thenComparing(java.util.Map.Entry::getKey))
                .limit(MAX_REASONS).map(entry -> entry.getKey().text).collect(Collectors.joining("; "));
        return "No safe camp site found within the search limits. "
                + (reasons.isEmpty() ? "No detailed blocker was recorded." : "Observed blockers: " + reasons + ".");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Exhausted", exhausted);
        counts.forEach((reason, count) -> tag.putInt(reason.name(), count));
        return tag;
    }

    public void load(CompoundTag tag) {
        counts.clear();
        exhausted = tag.getBoolean("Exhausted");
        for (Reason reason : Reason.values()) {
            if (!tag.contains(reason.name(), Tag.TAG_INT)) continue;
            int count = Math.min(MAX_COUNT, tag.getInt(reason.name()));
            if (count > 0) counts.put(reason, count);
        }
    }
}
