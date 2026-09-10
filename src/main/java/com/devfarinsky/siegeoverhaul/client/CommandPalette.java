package com.devfarinsky.siegeoverhaul.client;

/**
 * Central palette for the command center HUD.
 *
 * <p>The palette is medieval-fantasy but modern-sleek: deep midnight-navy
 * panels with warm aged-gold framing and cool arcane accents. Every screen
 * and widget in the HUD reads from these constants so revisions stay coherent.
 */
public final class CommandPalette {
    private CommandPalette() {}

    // Shadow layers behind the whole window.
    public static final int SHADOW_OUTER = 0x66000000;
    public static final int SHADOW_INNER = 0xaa000000;

    // Outer bevel (aged gold framing).
    public static final int BEVEL_LIGHT = 0xffe3b968;
    public static final int BEVEL_DARK  = 0xff6a4a1f;

    // Main window body (midnight navy gradient).
    public static final int PANEL_TOP    = 0xff1a1f2e;
    public static final int PANEL_BOTTOM = 0xff0d101a;

    // Header banner (deeper navy).
    public static final int HEADER_TOP    = 0xff141826;
    public static final int HEADER_BOTTOM = 0xff0a0d18;

    // Interior card panels (slate blue).
    public static final int CARD_TOP    = 0xff222839;
    public static final int CARD_BOTTOM = 0xff141826;
    public static final int CARD_BORDER = 0xff3d4863;

    public static final int CARD_TOP_DIM    = 0xff181c28;
    public static final int CARD_BOTTOM_DIM = 0xff0d101a;
    public static final int CARD_BORDER_DIM = 0xff242938;

    // Currency / status chips.
    public static final int CHIP_BORDER = 0xff3d4863;
    public static final int CHIP_FILL   = 0xff0d101a;

    // Hairlines and dividers.
    public static final int HAIRLINE = 0xffb08a52;
    public static final int DIVIDER  = 0xff2a3145;

    // Corner rivets.
    public static final int RIVET_LIGHT = 0xffe3b968;
    public static final int RIVET_DARK  = 0xff1e1610;

    // Text.
    public static final int TEXT       = 0xfff2ecdb;
    public static final int TEXT_MUTED = 0xff9aa3b8;
    public static final int TEXT_DIM   = 0xff626a80;

    // Accents (gameplay-coded).
    public static final int ACCENT_GOLD    = 0xfff2c96b;
    public static final int ACCENT_EMERALD = 0xff58e089;
    public static final int ACCENT_ARCANE  = 0xffb89aff;
    public static final int ACCENT_TEAL    = 0xff81e8da;
    public static final int ACCENT_BLOOD   = 0xffe06060;
    public static final int ACCENT_STEEL   = 0xff9ac9ff;

    /** Rare/tier tinting for loot chips. */
    public static int tier(int tier) {
        return switch (tier) {
            case 0 -> 0xffc7c1b5;   // Common
            case 1 -> ACCENT_EMERALD; // Uncommon
            case 2 -> ACCENT_STEEL;   // Rare
            case 3 -> ACCENT_ARCANE;  // Epic
            default -> ACCENT_GOLD;   // Legendary
        };
    }
}
