package com.devfarinsky.siegeoverhaul.client;

/**
 * Central palette for the command center HUD.
 *
 * <p>The palette is medieval-fantasy but modern-sleek: dark iron and aged
 * bronze framing over a deep parchment field, with cool arcane accents for
 * highlights. Every screen and widget in the HUD reads from these constants
 * so revisions stay coherent.
 */
public final class CommandPalette {
    private CommandPalette() {}

    // Shadow layers behind the whole window.
    public static final int SHADOW_OUTER = 0x66000000;
    public static final int SHADOW_INNER = 0xaa000000;

    // Outer bevel (aged bronze framing).
    public static final int BEVEL_LIGHT = 0xffb08a52;
    public static final int BEVEL_DARK  = 0xff3f2b18;

    // Main window body (deep parchment gradient).
    public static final int PANEL_TOP    = 0xff1f1a17;
    public static final int PANEL_BOTTOM = 0xff0f0b09;

    // Header banner (dark walnut).
    public static final int HEADER_TOP    = 0xff2a2018;
    public static final int HEADER_BOTTOM = 0xff17110c;

    // Interior card panels.
    public static final int CARD_TOP    = 0xff231d18;
    public static final int CARD_BOTTOM = 0xff130f0c;
    public static final int CARD_BORDER = 0xff4a3826;

    public static final int CARD_TOP_DIM    = 0xff191510;
    public static final int CARD_BOTTOM_DIM = 0xff0c0906;
    public static final int CARD_BORDER_DIM = 0xff2a2018;

    // Currency / status chips.
    public static final int CHIP_BORDER = 0xff3d2f20;
    public static final int CHIP_FILL   = 0xff0d0906;

    // Hairlines and dividers.
    public static final int HAIRLINE = 0xff6a4f2c;
    public static final int DIVIDER  = 0xff382a1c;

    // Corner rivets.
    public static final int RIVET_LIGHT = 0xffc59a5c;
    public static final int RIVET_DARK  = 0xff1e1610;

    // Text.
    public static final int TEXT       = 0xffefe4c8;
    public static final int TEXT_MUTED = 0xffa8967a;
    public static final int TEXT_DIM   = 0xff7a6a54;

    // Accents (gameplay-coded).
    public static final int ACCENT_GOLD    = 0xffe8c968;
    public static final int ACCENT_EMERALD = 0xff58e089;
    public static final int ACCENT_ARCANE  = 0xffb89aff;
    public static final int ACCENT_TEAL    = 0xff81e8da;
    public static final int ACCENT_BLOOD   = 0xffd9534f;
    public static final int ACCENT_STEEL   = 0xff9ac9ff;

    /** Rare/tier tinting for loot chips. */
    public static int tier(int tier) {
        return switch (tier) {
            case 0 -> 0xffc7c1b5;   // Common
            case 1 -> ACCENT_EMERALD; // Uncommon
            case 2 -> ACCENT_STEEL;   // Rare
            default -> ACCENT_ARCANE; // Epic
        };
    }
}
