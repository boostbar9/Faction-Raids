package com.devfarinsky.factionraids.narrative;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;

/**
 * The realized narrative for one raid: which faction is attacking, under
 * what pretext, and pre-rendered flavor strings.
 *
 * <p>Chosen once at raid start and persisted on
 * {@link com.devfarinsky.factionraids.RaidSavedData.RaidState}. Re-render is
 * cheap but stability across a server restart makes debugging and player
 * experience nicer — the story doesn't change mid-siege.
 *
 * <p>Fields are nullable-friendly because a raid loaded from a pre-narrative
 * save just carries {@code null} everywhere and the caller falls back to the
 * legacy announcement text.
 */
public final class RaidNarrative {

    public final String factionId;
    public final String factionName;
    public final String factionEpithet;
    public final ChatFormatting accent;
    public final String casusBelliId;
    public final String opening;
    public final String chant;
    public final String victoryTaunt;

    public RaidNarrative(String factionId, String factionName, String factionEpithet,
                         ChatFormatting accent, String casusBelliId,
                         String opening, String chant, String victoryTaunt) {
        this.factionId = factionId;
        this.factionName = factionName;
        this.factionEpithet = factionEpithet;
        this.accent = accent == null ? ChatFormatting.GOLD : accent;
        this.casusBelliId = casusBelliId;
        this.opening = opening;
        this.chant = chant;
        this.victoryTaunt = victoryTaunt;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("FactionId", nullSafe(factionId));
        tag.putString("FactionName", nullSafe(factionName));
        tag.putString("FactionEpithet", nullSafe(factionEpithet));
        tag.putString("Accent", accent.getName());
        tag.putString("CasusBelliId", nullSafe(casusBelliId));
        tag.putString("Opening", nullSafe(opening));
        tag.putString("Chant", nullSafe(chant));
        tag.putString("VictoryTaunt", nullSafe(victoryTaunt));
        return tag;
    }

    public static RaidNarrative load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        ChatFormatting accent = ChatFormatting.getByName(tag.getString("Accent"));
        if (accent == null) accent = ChatFormatting.GOLD;
        return new RaidNarrative(
                emptyToNull(tag.getString("FactionId")),
                emptyToNull(tag.getString("FactionName")),
                emptyToNull(tag.getString("FactionEpithet")),
                accent,
                emptyToNull(tag.getString("CasusBelliId")),
                emptyToNull(tag.getString("Opening")),
                emptyToNull(tag.getString("Chant")),
                emptyToNull(tag.getString("VictoryTaunt")));
    }

    private static String nullSafe(String value) { return value == null ? "" : value; }
    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
}
