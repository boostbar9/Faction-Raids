package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;

/** Faction-wide hero unlocks from real siege victories, saved on the core. */
public final class HeroProgress {
    private static final String VICTORIES = "HeroVictories";
    private HeroProgress() {}

    public static boolean migrate(CompoundTag core, RaidSavedData.WarJournal journal) {
        if (core.contains(VICTORIES, 3)) return false;
        int wins = journal == null ? 0 : (int) journal.entries.stream()
                .filter(e -> "victory".equals(e.outcome())).count();
        core.putInt(VICTORIES, Math.min(6, wins));
        return true;
    }

    public static void victory(CompoundTag core) {
        if (core != null) core.putInt(VICTORIES, Math.min(6, Math.max(0, core.getInt(VICTORIES)) + 1));
    }

    public static int tier(CompoundTag core) {
        int wins = Math.max(0, core.getInt(VICTORIES));
        return wins >= 6 ? 4 : wins >= 4 ? 3 : wins >= 2 ? 2 : wins >= 1 ? 1 : 0;
    }
}
