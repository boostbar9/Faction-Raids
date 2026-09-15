package com.devfarinsky.siegeoverhaul.client;

/**
 * Tier-specific loot crate glyphs.
 *
 * <p>The three Command Center crates used to share one generic chest icon, so
 * nothing on screen told a player which crate was the cheap one and which was
 * the endgame one. Each crate now has its own silhouette and metal:
 *
 * <ul>
 *   <li>{@link CommandIcon#SUPPLY_CRATE} — a roped canvas field crate.</li>
 *   <li>{@link CommandIcon#ARMORY_CRATE} — an iron-banded armory chest.</li>
 *   <li>{@link CommandIcon#ROYAL_CRATE} — a crowned gold reliquary.</li>
 * </ul>
 */
public final class LootIcons {
    private LootIcons() {}

    /** Glyph for loot box {@code index} (0 field, 1 armory, 2 royal). */
    public static CommandIcon tier(int index) {
        return switch (index) {
            case 0 -> CommandIcon.SUPPLY_CRATE;
            case 1 -> CommandIcon.ARMORY_CRATE;
            case 2 -> CommandIcon.ROYAL_CRATE;
            default -> CommandIcon.CHEST;
        };
    }
}
